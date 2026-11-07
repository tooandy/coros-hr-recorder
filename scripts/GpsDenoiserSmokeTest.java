package fun.aniss.coroshr;

/** Offline regression tests for GPS jitter, jumps, lost fixes, and recovery. */
public final class GpsDenoiserSmokeTest {
  private static void check(boolean yes,String label){
    if(!yes)throw new AssertionError(label);
  }
  private static GpsDenoiser.Result add(GpsDenoiser denoiser,long sec,
                                         double lat,double lon,float accuracy,boolean hasSpeed,float speed){
    return denoiser.add(1793700000000L+sec*1000,1_000_000_000L+sec*1_000_000_000L,
      lat,lon,accuracy,hasSpeed,speed);
  }
  public static void main(String[] args){
    GpsDenoiser d=new GpsDenoiser();
    double lat=30.252000,lon=120.164000;
    check(add(d,0,lat,lon,4,true,0).fix!=null,"initial GPS");
    // Several small coordinate shifts, plus larger shifts with device speed=0.
    for(int s=1;s<=16;s++){
      double jitter=((s%3)-1)*0.000022;
      check(add(d,s,lat+jitter,lon,4,true,0).fix==null,"stationary jitter "+s);
    }
    check(d.distanceMeters()==0,"stationary GPS must count zero distance");

    // Genuine running movement of ~4m/s, eventually clears GPS uncertainty.
    for(int s=17;s<=26;s++)
      add(d,s,lat+(s-16)*0.000036,lon,4,true,4);
    float beforeJump=d.distanceMeters();
    check(beforeJump>30&&beforeJump<55,"running distance plausible: "+beforeJump);
    check(add(d,27,lat+0.01,lon,4,true,4).fix==null,"teleport rejected");
    check(d.distanceMeters()==beforeJump,"teleport adds no distance");
    // GPS reacquires plausible position; future running should continue.
    add(d,28,lat+11*0.000036,lon,4,true,4);
    check(d.distanceMeters()>=beforeJump,"GPS recovery after outlier");
    float beforeGap=d.distanceMeters();
    check(add(d,90,lat+0.006,lon,4,true,4).fix!=null,"gap becomes new anchor");
    check(d.distanceMeters()==beforeGap,"no straight-line interpolation across outage");
    add(d,92,lat+0.006072,lon,4,true,4);
    check(d.distanceMeters()>beforeGap,"moving after gap accumulates distance");

    d.reset();
    check(add(d,0,lat,lon,3,true,0).fix!=null,"reset baseline");
    check(add(d,1,lat+0.02,lon,3,true,0).fix==null,"first relocated point");
    check(add(d,2,lat+0.020002,lon,3,true,0).fix==null,"second relocated point");
    GpsDenoiser.Result relocation=add(d,3,lat+0.020001,lon,3,true,0);
    check(relocation.fix!=null,"stable distant points re-anchor without teleport");
    check(d.distanceMeters()==0,"relock must not add distance");
    check(add(d,4,lat+0.020001,lon,40,true,0).fix==null,"poor accuracy");
    check(add(d,5,lat+0.020001,lon,3,true,99).fix==null,"invalid speed");
    check(add(d,3,lat+0.020001,lon,3,true,0).fix==null,"non-monotonic timestamp");
    System.out.println("GPS DENOISE PASS: stationary jitter, running, teleport, gap, relock, accuracy and timestamps");
  }
}
