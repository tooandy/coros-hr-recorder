package fun.aniss.coroshr;

/**
 * Noise rejection for GPS tracks. Uses monotonic timestamps and coordinates only;
 * no Android dependency, so historical GPS edge cases can be regression-tested.
 */
final class GpsDenoiser {
  static final class Fix {
    final long elapsedNanos, utcMillis;
    final double latitude, longitude;
    final float accuracyMeters, speedMps;
    final float cumulativeMeters;
    Fix(long elapsedNanos,long utcMillis,double latitude,double longitude,
        float accuracyMeters,float speedMps,float cumulativeMeters){
      this.elapsedNanos=elapsedNanos;
      this.utcMillis=utcMillis;
      this.latitude=latitude;
      this.longitude=longitude;
      this.accuracyMeters=accuracyMeters;
      this.speedMps=speedMps;
      this.cumulativeMeters=cumulativeMeters;
    }
  }
  static final class Result {
    final Fix fix; // null if discarded
    final String reason;
    Result(Fix fix,String reason){this.fix=fix;this.reason=reason;}
  }
  private static final double EARTH_RADIUS_M=6371008.8;
  private static final long MIN_INTERVAL_NS=750000000L;
  private static final long GAP_NS=20000000000L;
  private static final float MAX_ACCURACY_M=30f;
  private static final double MAX_RUN_SPEED_MPS=9.5;
  private Fix anchor;
  private float cumulative;
  private int consecutiveJumps;
  private double candidateLatitude,candidateLongitude;
  private long candidateElapsed;
  private boolean hasCandidate;

  float distanceMeters(){return cumulative;}
  void reset(){
    anchor=null;cumulative=0;consecutiveJumps=0;hasCandidate=false;
  }
  Result add(long utc,long elapsed,double latitude,double longitude,
             float accuracy,boolean hasSpeed,float rawSpeed){
    if(utc<=0||elapsed<=0||!Double.isFinite(latitude)||!Double.isFinite(longitude)||
        Math.abs(latitude)>90||Math.abs(longitude)>180||
        !Float.isFinite(accuracy)||accuracy<=0||accuracy>MAX_ACCURACY_M){
      return new Result(null,"精度差/坐标异常");
    }
    if(hasSpeed&&(!Float.isFinite(rawSpeed)||rawSpeed<0||rawSpeed>MAX_RUN_SPEED_MPS)){
      return new Result(null,"速度不可信");
    }
    float reportedSpeed=hasSpeed?rawSpeed:-1;
    if(anchor==null){
      anchor=new Fix(elapsed,utc,latitude,longitude,accuracy,reportedSpeed,cumulative);
      return new Result(anchor,"初始定位");
    }
    long deltaNanos=elapsed-anchor.elapsedNanos;
    if(deltaNanos<MIN_INTERVAL_NS)return new Result(null,"重复或过密定位");
    if(deltaNanos>GAP_NS){
      // Never connect a long GPS blackout with an artificial straight line.
      anchor=new Fix(elapsed,utc,latitude,longitude,accuracy,reportedSpeed,cumulative);
      hasCandidate=false;consecutiveJumps=0;
      return new Result(anchor,"断点重定位");
    }
    double secs=deltaNanos/1.0e9;
    double moved=distance(anchor.latitude,anchor.longitude,latitude,longitude);
    double implied=moved/secs;
    if(implied>MAX_RUN_SPEED_MPS|| // implausible for pedestrian running
        (hasSpeed&&rawSpeed<0.6&&implied>2.3)){
      // Avoid counting a teleport; allow stable points at the new location to
      // re-anchor after repeated jump observations, without adding distance.
      if(!hasCandidate||elapsed-candidateElapsed>6_000_000_000L||
          distance(candidateLatitude,candidateLongitude,latitude,longitude)>Math.max(8,accuracy*1.5)){
        consecutiveJumps=1;
      }else consecutiveJumps++;
      candidateLatitude=latitude;candidateLongitude=longitude;
      candidateElapsed=elapsed;hasCandidate=true;
      if(consecutiveJumps>=3){
        anchor=new Fix(elapsed,utc,latitude,longitude,accuracy,reportedSpeed,cumulative);
        hasCandidate=false;consecutiveJumps=0;
        return new Result(anchor,"跳点后重新锁定");
      }
      return new Result(null,"跳点/瞬时速度异常");
    }
    hasCandidate=false;consecutiveJumps=0;
    // Require travel exceeding horizontal GPS uncertainty. Small back-and-forth
    // jitter remains uncounted even if Android still reports a position fix.
    double minDisplacement=Math.max(3.5,Math.min(12,0.55*(anchor.accuracyMeters+accuracy)));
    if(moved<minDisplacement)return new Result(null,"精度范围内抖动");
    if(hasSpeed&&rawSpeed<0.6f)return new Result(null,"静止漂移");
    cumulative+=(float)moved;
    anchor=new Fix(elapsed,utc,latitude,longitude,accuracy,
      hasSpeed?rawSpeed:(float)implied,cumulative);
    return new Result(anchor,"有效位移");
  }
  static double distance(double lat1,double lon1,double lat2,double lon2){
    double a=Math.sin(Math.toRadians(lat2-lat1)/2);
    double b=Math.sin(Math.toRadians(lon2-lon1)/2);
    double s=a*a+Math.cos(Math.toRadians(lat1))*Math.cos(Math.toRadians(lat2))*b*b;
    return 2*EARTH_RADIUS_M*Math.asin(Math.min(1,Math.sqrt(Math.max(0,s))));
  }
}
