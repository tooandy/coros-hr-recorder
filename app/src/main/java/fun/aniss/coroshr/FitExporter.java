package fun.aniss.coroshr;

import com.garmin.fit.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Generates FIT activities with every BLE HR sample and optional phone GPS/steps. */
public final class FitExporter {
  private FitExporter(){}
  private static final class Sample {
    final long t;
    final Short hr;
    TrackData.Fix fix;
    Sample(long t,Short hr,TrackData.Fix fix){this.t=t;this.hr=hr;this.fix=fix;}
  }
  public static int export(java.io.File csv,java.io.File fit)throws IOException {
    ArrayList<Sample> records=new ArrayList<>();
    try(BufferedReader br=new BufferedReader(new InputStreamReader(
        new FileInputStream(csv),StandardCharsets.UTF_8))){
      br.readLine();String line;
      while((line=br.readLine())!=null){
        String[] fields=line.split(",",4);
        if(fields.length<3)continue;
        try{
          long t=Long.parseLong(fields[0]);
          int h=Integer.parseInt(fields[2]);
          if(t>0&&h>0&&h<=255)records.add(new Sample(t,(short)h,null));
        }catch(NumberFormatException ignored){}
      }
    }
    if(records.isEmpty())throw new IOException("没有心率记录");
    records.sort(Comparator.comparingLong(x->x.t));
    final int hrCount=records.size();
    TrackData track=TrackData.read(csv);
    // Attach nearby GPS fixes to heart-rate records so consumers that only use
    // heart-rate records can still see the corresponding route data.
    int nextFix=0;
    for(Sample row:records){
      while(nextFix<track.fixes.size()&&track.fixes.get(nextFix).t<row.t)nextFix++;
      TrackData.Fix best=null;
      if(nextFix<track.fixes.size())best=track.fixes.get(nextFix);
      if(nextFix>0){
        TrackData.Fix prior=track.fixes.get(nextFix-1);
        if(best==null||Math.abs(prior.t-row.t)<=Math.abs(best.t-row.t))best=prior;
      }
      if(best!=null&&Math.abs(best.t-row.t)<=1500)row.fix=best;
    }
    // Keep independent GPS points during any HR disconnect or long HR gap.
    int hrIndex=0;
    for(TrackData.Fix fix:track.fixes){
      while(hrIndex<hrCount&&records.get(hrIndex).t<fix.t)hrIndex++;
      long nearest=Long.MAX_VALUE;
      if(hrIndex<hrCount)nearest=Math.min(nearest,Math.abs(records.get(hrIndex).t-fix.t));
      if(hrIndex>0)nearest=Math.min(nearest,Math.abs(records.get(hrIndex-1).t-fix.t));
      if(nearest>1200)records.add(new Sample(fix.t,null,fix));
    }
    records.sort(Comparator.comparingLong(x->x.t));
    long begin=records.get(0).t,end=records.get(records.size()-1).t;
    float duration=Math.max(1f,(end-begin)/1000f);
    Sport sport=track.fixes.isEmpty()&&track.steps.isEmpty()?Sport.GENERIC:Sport.RUNNING;
    FileEncoder enc=new FileEncoder(fit,Fit.ProtocolVersion.V2_0);
    try{
      FileIdMesg id=new FileIdMesg();
      id.setType(com.garmin.fit.File.ACTIVITY);
      id.setManufacturer(Manufacturer.DEVELOPMENT);
      id.setProduct(1);id.setProductName("COROS HR Logger");
      id.setTimeCreated(at(begin));enc.write(id);
      EventMesg start=new EventMesg();
      start.setTimestamp(at(begin));start.setEvent(Event.TIMER);
      start.setEventType(EventType.START);enc.write(start);
      int nextStep=0;
      TrackData.Step lastStep=null;
      int cadenceSum=0,cadenceCount=0;
      for(Sample row:records){
        RecordMesg m=new RecordMesg();m.setTimestamp(at(row.t));
        if(row.hr!=null)m.setHeartRate(row.hr);
        if(row.fix!=null){
          m.setPositionLat(TrackData.semicircles(row.fix.lat));
          m.setPositionLong(TrackData.semicircles(row.fix.lon));
          m.setDistance(row.fix.distance);
          m.setSpeed(row.fix.speed);
        }
        while(nextStep<track.steps.size()&&track.steps.get(nextStep).t<=row.t)
          lastStep=track.steps.get(nextStep++);
        if(lastStep!=null&&row.t-lastStep.t<=12000){
          if(lastStep.cadence>0){
            m.setCadence((short)lastStep.cadence);
            if(row.hr!=null){cadenceSum+=lastStep.cadence;cadenceCount++;}
          }
          if(lastStep.stride>0)m.setStepLength(lastStep.stride);
        }
        enc.write(m);
      }
      EventMesg stop=new EventMesg();stop.setTimestamp(at(end));
      stop.setEvent(Event.TIMER);stop.setEventType(EventType.STOP_ALL);enc.write(stop);
      LapMesg lap=new LapMesg();
      lap.setTimestamp(at(end));lap.setStartTime(at(begin));
      lap.setTotalElapsedTime(duration);lap.setTotalTimerTime(duration);
      lap.setSport(sport);
      if(!track.fixes.isEmpty()){
        lap.setTotalDistance(track.totalMeters);
        lap.setAvgSpeed(track.totalMeters/duration);
      }
      if(cadenceCount>0)lap.setAvgCadence((short)(cadenceSum/cadenceCount));
      enc.write(lap);
      SessionMesg session=new SessionMesg();
      session.setTimestamp(at(end));session.setStartTime(at(begin));
      session.setTotalElapsedTime(duration);session.setTotalTimerTime(duration);
      session.setSport(sport);session.setFirstLapIndex(0);session.setNumLaps(1);
      if(!track.fixes.isEmpty()){
        session.setTotalDistance(track.totalMeters);
        session.setAvgSpeed(track.totalMeters/duration);
      }
      if(cadenceCount>0)session.setAvgCadence((short)(cadenceSum/cadenceCount));
      enc.write(session);
      ActivityMesg activity=new ActivityMesg();
      activity.setTimestamp(at(end));activity.setTotalTimerTime(duration);
      activity.setNumSessions(1);activity.setEvent(Event.ACTIVITY);
      activity.setEventType(EventType.STOP);activity.setType(Activity.MANUAL);
      enc.write(activity);
    }finally{enc.close();}
    return hrCount;
  }
  private static com.garmin.fit.DateTime at(long ms){
    return new com.garmin.fit.DateTime(new Date(ms));
  }
}
