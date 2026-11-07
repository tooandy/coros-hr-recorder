package fun.aniss.coroshr;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.hardware.*;
import android.location.*;
import android.os.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Optional offline GNSS + phone step-sensor logger, parallel to raw BLE HR CSV. */
final class MotionGpsRecorder implements LocationListener, SensorEventListener {
  private final Context context;
  private final Runnable updated;
  private final File hrFile;
  private BufferedWriter gpsOut,stepOut;
  private LocationManager lm;
  private SensorManager sm;
  private Sensor stepSensor;
  private boolean gpsRunning,stepsRunning;
  private final GpsDenoiser denoiser=new GpsDenoiser();
  private int rejectedGpsFixes;
  private float meters,accuracy=-1,speed=-1;
  private int steps,cadence=-1,baseGpsSteps=-1;
  private int counterBaseline=-1;
  private float stride=-1;
  private final Deque<long[]> recent=new ArrayDeque<>();
  private long lastStepElapsed;
  private String gpsStatus,stepStatus;
  MotionGpsRecorder(Context ctx,File hrFile,Runnable updated){
    this.context=ctx;this.hrFile=hrFile;this.updated=updated;
    gpsStatus=ctx.getString(R.string.ui_off);
    stepStatus=gpsStatus;
  }
  boolean isGpsRunning(){return gpsRunning;}
  boolean isStepsRunning(){return stepsRunning;}
  String gpsStatus(){return gpsStatus;}
  String stepStatus(){return stepStatus;}
  float meters(){return meters;}
  float accuracy(){return accuracy;}
  float speed(){return speed;}
  int steps(){return steps;}
  int cadence(){
    if(lastStepElapsed==0||SystemClock.elapsedRealtime()-lastStepElapsed>12000)return -1;
    return cadence;
  }
  float stride(){return stride;}

  private BufferedWriter output(String kind,String header)throws IOException {
    File target=TrackData.sidecar(hrFile,kind);
    File dir=target.getParentFile();
    if(!dir.exists()&&!dir.mkdirs())throw new IOException(context.getString(R.string.ui_cannot_create_activity_data_directory));
    BufferedWriter out=new BufferedWriter(new OutputStreamWriter(new FileOutputStream(target),StandardCharsets.UTF_8));
    out.write(header+"\n");out.flush();
    return out;
  }
  void start(boolean wantGps,boolean wantSteps){
    if(wantGps){
      if(context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
        gpsStatus=context.getString(R.string.ui_precise_location_permission_denied);
      }else{
        try{
          lm=(LocationManager)context.getSystemService(Context.LOCATION_SERVICE);
          if(lm==null||!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)){
            gpsStatus=context.getString(R.string.ui_gps_disabled_check_location_settings);
          }else{
            gpsOut=output("gps","utc_epoch_ms,latitude,longitude,accuracy_m,altitude_m,speed_mps,distance_m");
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER,1000,0,this,Looper.getMainLooper());
            gpsRunning=true;gpsStatus=context.getString(R.string.ui_waiting_for_gps_fix);
            denoiser.reset();rejectedGpsFixes=0;
          }
        }catch(Exception ex){gpsStatus=context.getString(R.string.ui_gps_start_error)+ex.getClass().getSimpleName();}
      }
    }
    if(wantSteps){
      if(Build.VERSION.SDK_INT>=29&&
        context.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION)!=PackageManager.PERMISSION_GRANTED){
        stepStatus=context.getString(R.string.ui_physical_activity_permission_missing);
      }else{
        try{
          sm=(SensorManager)context.getSystemService(Context.SENSOR_SERVICE);
          if(sm!=null){
            stepSensor=sm.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
            if(stepSensor==null)stepSensor=sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
          }
          if(stepSensor==null){
            stepStatus=context.getString(R.string.ui_step_sensor_not_available);
          }else{
            stepOut=output("steps","utc_epoch_ms,steps,cadence_spm,stride_m");
            stepsRunning=sm.registerListener(this,stepSensor,SensorManager.SENSOR_DELAY_NORMAL);
            stepStatus=stepsRunning?context.getString(R.string.ui_waiting_for_step_sensor):context.getString(R.string.ui_cannot_start_step_sensor);
          }
        }catch(Exception ex){stepStatus=context.getString(R.string.ui_step_sensor_error)+ex.getClass().getSimpleName();}
      }
    }
    updated.run();
  }
  @Override public void onLocationChanged(Location l){
    if(!gpsRunning||l==null||l.isFromMockProvider())return;
    GpsDenoiser.Result result=denoiser.add(
      l.getTime(),l.getElapsedRealtimeNanos(),l.getLatitude(),l.getLongitude(),
      l.hasAccuracy()?l.getAccuracy():Float.POSITIVE_INFINITY,
      l.hasSpeed(),l.hasSpeed()?l.getSpeed():-1f);
    if(result.fix==null){
      rejectedGpsFixes++;
      if(l.hasSpeed()&&l.getSpeed()<0.6f)speed=0;
      // Avoid frequent UI redraws while stationary, but show initial rejection.
      if(rejectedGpsFixes==1||rejectedGpsFixes%15==0){
        gpsStatus=context.getString(R.string.ui_denoising)+result.reason+context.getString(R.string.ui_filtered)+rejectedGpsFixes+context.getString(R.string.ui_fixes_f625b3);
        updated.run();
      }
      return;
    }
    GpsDenoiser.Fix fix=result.fix;
    meters=fix.cumulativeMeters;
    accuracy=fix.accuracyMeters;
    speed=fix.speedMps;
    gpsStatus=context.getString(R.string.ui_gps_ready_filtered)+rejectedGpsFixes+context.getString(R.string.ui_fixes);
    if(baseGpsSteps<0&&stepsRunning)baseGpsSteps=steps;
    updateStride();
    try{
      gpsOut.write(fix.utcMillis+","+fix.latitude+","+fix.longitude+","+fix.accuracyMeters+","+
        (l.hasAltitude()?l.getAltitude():0)+","+Math.max(0,fix.speedMps)+","+meters+"\n");
      gpsOut.flush();
    }catch(IOException ex){gpsStatus=context.getString(R.string.ui_gps_write_error)+ex.getMessage();gpsRunning=false;}
    updated.run();
  }
  private void updateStride(){
    int stepsWithGps=baseGpsSteps>=0?steps-baseGpsSteps:0;
    stride=(meters>=25&&stepsWithGps>=10)?meters/stepsWithGps:-1;
    if(stride>3.5f||stride<0.25f)stride=-1;
  }
  @Override public void onSensorChanged(SensorEvent event){
    if(!stepsRunning||event.sensor!=stepSensor)return;
    long ms=System.currentTimeMillis()-
      (SystemClock.elapsedRealtimeNanos()-event.timestamp)/1000000L;
    long elapsedMs=event.timestamp/1000000L;
    if(stepSensor.getType()==Sensor.TYPE_STEP_DETECTOR){
      steps++;
    }else{
      int raw=Math.round(event.values[0]);
      if(counterBaseline<0){counterBaseline=raw;return;}
      if(raw<counterBaseline)return;
      steps=raw-counterBaseline;
    }
    if(recent.isEmpty()||elapsedMs>recent.peekLast()[0])
      recent.addLast(new long[]{elapsedMs,steps});
    while(recent.size()>1&&elapsedMs-recent.peekFirst()[0]>15000)
      recent.removeFirst();
    if(recent.size()>=2){
      long dt=elapsedMs-recent.peekFirst()[0];
      int count=steps-(int)recent.peekFirst()[1];
      int measured=dt>=2500?(int)Math.round(count*60000.0/dt):0;
      cadence=measured>=60&&measured<=250?measured:-1;
    }
    lastStepElapsed=SystemClock.elapsedRealtime();
    stepStatus=context.getString(R.string.ui_step_sensor_active);
    updateStride();
    try{
      stepOut.write(ms+","+steps+","+(cadence>=0?cadence:0)+","+
        (stride>0?stride:0)+"\n");
      stepOut.flush();
    }catch(IOException ex){stepStatus=context.getString(R.string.ui_steps_write_error)+ex.getMessage();stepsRunning=false;}
    updated.run();
  }
  @Override public void onAccuracyChanged(Sensor sensor,int accuracy){}
  @Override public void onProviderEnabled(String provider){}
  @Override public void onProviderDisabled(String provider){gpsStatus=context.getString(R.string.ui_gps_turned_off);updated.run();}
  void close(){
    if(lm!=null)try{lm.removeUpdates(this);}catch(Exception ignored){}
    if(sm!=null)sm.unregisterListener(this);
    gpsRunning=false;stepsRunning=false;
    if(gpsOut!=null)try{gpsOut.close();}catch(IOException ignored){}
    if(stepOut!=null)try{stepOut.close();}catch(IOException ignored){}
    gpsOut=null;stepOut=null;
  }
}
