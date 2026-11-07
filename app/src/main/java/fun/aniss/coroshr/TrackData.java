package fun.aniss.coroshr;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Reads optional per-session location and step sensor sidecars. Raw HR CSV stays unchanged. */
final class TrackData {
  static final class Fix {
    final long t;
    final double lat,lon;
    final float accuracy,speed,distance;
    Fix(long t,double lat,double lon,float accuracy,float speed,float distance){
      this.t=t;this.lat=lat;this.lon=lon;this.accuracy=accuracy;this.speed=speed;this.distance=distance;
    }
  }
  static final class Step {
    final long t;
    final int count,cadence;
    final float stride;
    Step(long t,int count,int cadence,float stride){
      this.t=t;this.count=count;this.cadence=cadence;this.stride=stride;
    }
  }
  final List<Fix> fixes=new ArrayList<>();
  final List<Step> steps=new ArrayList<>();
  float totalMeters;
  int totalSteps;
  static File sidecar(File hrFile,String extension){
    return new File(new File(hrFile.getParentFile(),"telemetry"),
      hrFile.getName().replaceFirst("(?i)\\.csv$","."+extension+".csv"));
  }
  static TrackData read(File hrFile) throws IOException {
    TrackData data=new TrackData();
    File gps=sidecar(hrFile,"gps");
    if(gps.isFile()){
      try(BufferedReader br=new BufferedReader(new InputStreamReader(new FileInputStream(gps),StandardCharsets.UTF_8))){
        br.readLine();String s;
        while((s=br.readLine())!=null){
          String[] c=s.split(",",8);
          if(c.length<7)continue;
          try{
            long t=Long.parseLong(c[0]);
            double lat=Double.parseDouble(c[1]),lon=Double.parseDouble(c[2]);
            float acc=Float.parseFloat(c[3]),speed=Float.parseFloat(c[5]),dist=Float.parseFloat(c[6]);
            if(t>0&&Double.isFinite(lat)&&Double.isFinite(lon)&&Math.abs(lat)<=90&&Math.abs(lon)<=180
                &&acc>=0&&acc<=100&&speed>=0&&dist>=0)
              data.fixes.add(new Fix(t,lat,lon,acc,speed,dist));
          }catch(NumberFormatException ignored){}
          if(data.fixes.size()>300000)throw new IOException("GPS 数据量超过限制");
        }
      }
    }
    File stepFile=sidecar(hrFile,"steps");
    if(stepFile.isFile()){
      try(BufferedReader br=new BufferedReader(new InputStreamReader(new FileInputStream(stepFile),StandardCharsets.UTF_8))){
        br.readLine();String s;
        while((s=br.readLine())!=null){
          String[] c=s.split(",",5);
          if(c.length<4)continue;
          try{
            long t=Long.parseLong(c[0]);
            int count=Integer.parseInt(c[1]),cadence=Integer.parseInt(c[2]);
            float stride=Float.parseFloat(c[3]);
            if(t>0&&count>=0&&cadence>=0&&cadence<=255)
              data.steps.add(new Step(t,count,cadence,stride));
          }catch(NumberFormatException ignored){}
          if(data.steps.size()>300000)throw new IOException("计步数据量超过限制");
        }
      }
    }
    data.fixes.sort(Comparator.comparingLong(a->a.t));
    data.steps.sort(Comparator.comparingLong(a->a.t));
    if(!data.fixes.isEmpty())data.totalMeters=data.fixes.get(data.fixes.size()-1).distance;
    if(!data.steps.isEmpty())data.totalSteps=data.steps.get(data.steps.size()-1).count;
    return data;
  }
  static int semicircles(double degrees){
    return (int)Math.round(degrees*(2147483648.0/180.0));
  }
  static double meters(double lat1,double lon1,double lat2,double lon2){
    double r=6371008.8;
    double a=Math.sin(Math.toRadians(lat2-lat1)/2);
    double b=Math.sin(Math.toRadians(lon2-lon1)/2);
    double h=a*a+Math.cos(Math.toRadians(lat1))*Math.cos(Math.toRadians(lat2))*b*b;
    return 2*r*Math.asin(Math.min(1,Math.sqrt(Math.max(0,h))));
  }
}
