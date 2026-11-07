package fun.aniss.coroshr;

import com.garmin.fit.Decode;
import com.garmin.fit.MesgNum;
import com.garmin.fit.RecordMesg;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** End-to-end offline export/decode test using simulated phone GPS and step data. */
public final class WorkoutFitSmokeTest {
  static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
  static void write(File f,String contents)throws IOException {
    f.getParentFile().mkdirs();
    try(Writer out=new OutputStreamWriter(new FileOutputStream(f),StandardCharsets.UTF_8)){
      out.write(contents);
    }
  }
  public static void main(String[] args)throws Exception{
    File dir=new File(System.getProperty("java.io.tmpdir"),"coros-gps-test-"+System.nanoTime());
    dir.mkdirs();
    try{
      long t=1792314000000L;
      File hr=new File(dir,"hr_20261018_090000_000.csv");
      write(hr,"utc_epoch_ms,elapsed_realtime_ns,bpm,payload_hex\n"+
        t+",0,140,008C\n"+(t+1000)+",1000000000,145,0091\n"+
        (t+2000)+",2000000000,149,0095\n");
      File gps=TrackData.sidecar(hr,"gps");
      write(gps,"utc_epoch_ms,latitude,longitude,accuracy_m,altitude_m,speed_mps,distance_m\n"+
        (t+400)+",30.250000,120.160000,4.0,22,3.0,0.0\n"+
        (t+1400)+",30.250027,120.160000,4.0,22,3.0,3.0\n"+
        (t+2400)+",30.250054,120.160000,4.0,22,3.0,6.0\n"+
        (t+4400)+",30.250108,120.160000,4.0,22,3.0,12.0\n");
      File steps=TrackData.sidecar(hr,"steps");
      write(steps,"utc_epoch_ms,steps,cadence_spm,stride_m\n"+
        (t+300)+",1,0,0\n"+(t+900)+",3,180,1.0\n"+
        (t+1300)+",4,180,1.0\n"+(t+1900)+",6,185,1.0\n");
      TrackData loaded=TrackData.read(hr);
      check(loaded.fixes.size()==4&&loaded.steps.size()==4,"sidecars load");
      check(loaded.totalMeters==12f,"total distance");
      check(Math.abs(TrackData.meters(30.25,120.16,30.250027,120.16)-3)<0.5,
        "Haversine sanity");
      File fit=new File(dir,"out.fit");
      check(FitExporter.export(hr,fit)==3,"export HR count");
      Decode decode=new Decode();
      check(decode.checkFileIntegrity(new FileInputStream(fit)),"FIT CRC");
      final int[] heart={0},position={0},cadence={0},stride={0},distance={0},records={0};
      boolean ok=decode.read(new FileInputStream(fit),m->{
        if(m.getNum()!=MesgNum.RECORD)return;
        RecordMesg r=new RecordMesg(m);records[0]++;
        if(r.getHeartRate()!=null)heart[0]++;
        if(r.getPositionLat()!=null&&r.getPositionLong()!=null)position[0]++;
        if(r.getCadence()!=null)cadence[0]++;
        if(r.getStepLength()!=null)stride[0]++;
        if(r.getDistance()!=null)distance[0]++;
      });
      check(ok,"FIT decode");
      check(heart[0]==3,"heart record count");
      check(position[0]>=3,"gps position count");
      check(cadence[0]>=2,"cadence in FIT");
      check(stride[0]>=2,"estimated step length in FIT");
      check(distance[0]>=3,"distance in FIT");
      check(records[0]>=4,"append gps-only points beyond HR");
      System.out.println("GPS+STEPS FIT TEST PASS: "+records[0]+" records, "+position[0]+" GPS, "+cadence[0]+" cadence, "+stride[0]+" stride, valid CRC");
    }finally{
      File[] inner=new File(dir,"telemetry").listFiles();
      if(inner!=null)for(File f:inner)f.delete();
      new File(dir,"telemetry").delete();
      File[] children=dir.listFiles();if(children!=null)for(File f:children)f.delete();
      dir.delete();
    }
  }
}
