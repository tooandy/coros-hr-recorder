package fun.aniss.coroshr;

import android.Manifest;
import android.app.*;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.media.AudioAttributes;
import android.speech.tts.TextToSpeech;
import java.io.File;
import java.io.IOException;
import android.util.Log;
import java.util.UUID;
import java.util.Locale;

public class HrService extends Service {
  static final String EVENT="fun.aniss.coroshr.event";
  static final String COMMAND="cmd", ADDRESS="addr", NAME="name";
  static final String CONNECT="connect", RESTORE="restore", START="start", STOP="stop", DISCONNECT="disconnect", STATUS="status", REFRESH_BATTERY="refresh_battery", START_PROBE="start_probe", STOP_PROBE="stop_probe", UPDATE_REMINDER="update_reminder", TEST_CHINESE_VOICE="test_chinese_voice";
  private static final UUID HR=UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb");
  private static final UUID DATA=UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb");
  private static final UUID CCCD=UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
  private static final UUID BATTERY_SERVICE=UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb");
  private static final UUID BATTERY_LEVEL=UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb");
  private static final long BATTERY_POLL_INTERVAL_MS=5*60*1000L;
  private static final long BATTERY_TIMEOUT_MS=8000L;
  private final Handler handler=new Handler(Looper.getMainLooper());
  private BluetoothGatt gatt;
  private BluetoothGattCharacteristic batteryCharacteristic;
  private volatile int batteryPercent=-1;
  private volatile boolean batteryReadPending=false;
  private volatile String batteryStatus;
  private volatile String gattDiagnostics;
  private final Runnable batteryPoll=new Runnable(){
    @Override public void run(){
      if(connected&&subscribed&&!shuttingDown){
        readBattery();
        handler.postDelayed(this,BATTERY_POLL_INTERVAL_MS);
      }
    }
  };
  private String address,deviceName,state;
  private boolean connected,subscribed,recording,shuttingDown,autoStartPending;
  private final Runnable reconnectTask=()->{
    if(!connected&&!shuttingDown)connect();
  };
  private volatile int bpm;
  private volatile long lastHeartRateAtMs;
  private int count,attempts;
  private static final int REMINDER_NOTIFICATION_ID=2027;
  private final HrReminderSchedule reminderSchedule=new HrReminderSchedule();
  private TextToSpeech reminderTts;
  private boolean reminderTtsReady;
  private boolean reminderTtsFailed;
  private boolean ttsTestPending;
  private String ttsStatus;
  private String reminderMessage;
  private final Runnable reminderTick=new Runnable(){
    @Override public void run(){
      if(!recording||!reminderSchedule.active())return;
      long now=SystemClock.elapsedRealtime();
      if(reminderSchedule.due(now)){
        boolean fresh=HrReminderSchedule.fresh(connected,subscribed,bpm,lastHeartRateAtMs,now);
        reminderMessage=fresh?getString(R.string.ui_reminded)+bpm+" BPM":getString(R.string.ui_heart_rate_unavailable_reminder_skipped);
        if(fresh){
          publishHeartRateReminder(bpm);
          if(reminderTtsReady&&reminderTts!=null){
            reminderTts.speak(getString(R.string.speech_heart_rate,bpm),
              TextToSpeech.QUEUE_FLUSH,null,"coros_hr_reminder");
          }
        }
        reminderSchedule.afterReminder(now);
        send();
      }
      scheduleReminderTick();
    }
  };
  private SessionStore store;
  private MotionGpsRecorder motion;
  private boolean gpsRequested,stepsRequested;
  private float lastDistance,lastAccuracy=-1,lastSpeed=-1,lastStride=-1;
  private int lastSteps,lastCadence=-1;
  private String lastGpsStatus,lastStepStatus;
  private BleProbeLog probeLog;
  private BluetoothGattCharacteristic probePending;
  private int probeIndex,probeSubscribed;
  private boolean probeActive;
  private long probeDeadline;
  private String probeState,probeFileName;
  private final Runnable probeTick=new Runnable(){
    @Override public void run(){
      if(!probeActive)return;
      send();
      handler.postDelayed(this,1000);
    }
  };
  private final Runnable probeStop=()->stopProbe(getString(R.string.ui_text_60_second_capture_finished));
  private final Runnable probeRetry=()->subscribeNextProbe();
  private final Runnable probeDescriptorTimeout=()->{
    if(probeActive&&probePending!=null){
      probeEvent("CCCD_CONFIRM_TIMEOUT");
      stopProbe(getString(R.string.ui_subscribe_timeout_disconnect_and_retry));
    }
  };

  @Override public void onCreate(){
    super.onCreate();
    batteryStatus=getString(R.string.ui_disconnected_f2f3e9);
    gattDiagnostics=getString(R.string.ui_no_bluetooth_services_discovered);
    deviceName=getString(R.string.ui_not_selected);
    state=getString(R.string.ui_disconnected_f2f3e9);
    ttsStatus=getString(R.string.ui_voice_not_tested_948c20);
    reminderMessage=getString(R.string.ui_off);
    lastGpsStatus=getString(R.string.ui_off);
    lastStepStatus=lastGpsStatus;
    probeState=getString(R.string.ui_not_started);
    probeFileName=getSharedPreferences("ble_probe",MODE_PRIVATE).getString("lastFile",null);
    ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(
      new NotificationChannel("hr_monitor",getString(R.string.ui_heart_rate_data_collection),NotificationManager.IMPORTANCE_LOW));
    ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(
      new NotificationChannel("hr_reminders",getString(R.string.ui_periodic_heart_rate_reminders),NotificationManager.IMPORTANCE_LOW));
  }
  @Override public int onStartCommand(Intent intent,int flags,int startId){
    if(intent==null)return START_STICKY;
    String cmd=intent.getStringExtra(COMMAND);
    if(CONNECT.equals(cmd)){
      String nextAddress=intent.getStringExtra(ADDRESS);
      if(nextAddress==null||!BluetoothAdapter.checkBluetoothAddress(nextAddress)){
        state=getString(R.string.ui_invalid_device_address);send();return START_STICKY;
      }
      // Explicitly choosing a device replaces the previous connection.
      if(recording)stopRecord();
      address=nextAddress;
      deviceName=intent.getStringExtra(NAME);
      if(deviceName==null||deviceName.isEmpty())deviceName=getString(R.string.ui_previously_connected_heart_rate_sensor);
      shuttingDown=false;
      autoStartPending=AutoSessionPolicy.startAfterConnection(intent.getBooleanExtra("autoRecord",false));
      gpsRequested=intent.getBooleanExtra("gps",false);
      stepsRequested=intent.getBooleanExtra("steps",false);
      foreground();connect();
    }else if(RESTORE.equals(cmd)){
      if(recording||probeActive){send();return START_STICKY;}
      if(gatt!=null||connected){
        // Reopening a fresh activity while the service is still alive should
        // reuse the BLE connection. If idle, start one new session; if still
        // connecting, arm autostart for the subscription-complete callback.
        boolean requested=AutoSessionPolicy.startAfterConnection(intent.getBooleanExtra("autoRecord",false));
        autoStartPending=requested&&!subscribed;
        gpsRequested=intent.getBooleanExtra("gps",false);
        stepsRequested=intent.getBooleanExtra("steps",false);
        if(requested&&connected&&subscribed)startRecord();
        else send();
        return START_STICKY;
      }
      android.content.SharedPreferences saved=getSharedPreferences("last_ble_device",MODE_PRIVATE);
      String remembered=saved.getString("address",null);
      if(remembered==null||!BluetoothAdapter.checkBluetoothAddress(remembered)){
        state=getString(R.string.ui_no_sensor_remembered_yet);send();return START_STICKY;
      }
      address=remembered;
      deviceName=saved.getString("name",getString(R.string.ui_previously_connected_heart_rate_sensor));
      autoStartPending=AutoSessionPolicy.startAfterConnection(intent.getBooleanExtra("autoRecord",false));
      gpsRequested=intent.getBooleanExtra("gps",false);
      stepsRequested=intent.getBooleanExtra("steps",false);
      shuttingDown=false;
      foreground();connect();
    }else if(TEST_CHINESE_VOICE.equals(cmd)){
      testChineseVoice();
    }else if(UPDATE_REMINDER.equals(cmd)){
      refreshReminderSettings();
      send();
    }else if(START_PROBE.equals(cmd)){startProbe();}
    else if(STOP_PROBE.equals(cmd)){stopProbe(getString(R.string.ui_stopped_manually));}
    else if(REFRESH_BATTERY.equals(cmd)){readBattery();}
    else if(START.equals(cmd)){
      gpsRequested=intent.getBooleanExtra("gps",false);
      stepsRequested=intent.getBooleanExtra("steps",false);
      startRecord();
    }
    else if(STOP.equals(cmd)){autoStartPending=false;stopRecord();}
    else if(DISCONNECT.equals(cmd)){
      shuttingDown=true;autoStartPending=false;stopProbe(getString(R.string.ui_disconnected));stopRecord();close();
      resetBattery(getString(R.string.ui_disconnected_f2f3e9));
      connected=false;subscribed=false;state=getString(R.string.ui_disconnected_3ed415);send();
      stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();
    }else if(STATUS.equals(cmd))send();
    return START_STICKY;
  }
  private void refreshReminderSettings(){
    android.content.SharedPreferences settings=getSharedPreferences("hr_reminders",MODE_PRIVATE);
    boolean enabled=settings.getBoolean("enabled",false);
    int minutes=HrReminderSchedule.clampMinutes(
      settings.getInt("minutes",HrReminderSchedule.DEFAULT_MINUTES));
    handler.removeCallbacks(reminderTick);
    reminderSchedule.configure(recording,enabled,minutes,SystemClock.elapsedRealtime());
    if(reminderSchedule.active()){
      reminderMessage=getString(R.string.ui_waiting_for_first_announcement);
      ensureReminderTts();
      scheduleReminderTick();
    }else{
      reminderMessage=getString(R.string.ui_off);
      if(reminderTts!=null)reminderTts.stop();
      ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).cancel(REMINDER_NOTIFICATION_ID);
    }
  }
  private void scheduleReminderTick(){
    handler.removeCallbacks(reminderTick);
    if(!reminderSchedule.active())return;
    handler.postDelayed(reminderTick,Math.max(100L,
      reminderSchedule.untilDue(SystemClock.elapsedRealtime())));
  }
  private void testChineseVoice(){
    if(reminderTtsReady&&reminderTts!=null){
      int status=reminderTts.speak(getString(R.string.speech_heart_rate,150),
        TextToSpeech.QUEUE_FLUSH,null,"coros_hr_voice_test");
      ttsStatus=status==TextToSpeech.SUCCESS?getString(R.string.ui_speech_test_queued_check_media_volume):getString(R.string.ui_speech_failed_check_tts_engine);
      send();
      return;
    }
    reminderTtsFailed=false;
    ttsTestPending=true;
    ttsStatus=getString(R.string.ui_initializing_speech_engine);
    if(reminderTts!=null){
      reminderTts.shutdown();
      reminderTts=null;
    }
    reminderTtsReady=false;
    ensureReminderTts();
    send();
  }
  private void ensureReminderTts(){
    if(reminderTts!=null||reminderTtsFailed)return;
    try{
      reminderTts=new TextToSpeech(this,status->handler.post(()->{
        TextToSpeech tts=reminderTts;
        if(tts==null)return;
        if(status!=TextToSpeech.SUCCESS){
          reminderTtsReady=false;reminderTtsFailed=true;
          reminderMessage=getString(R.string.ui_voice_initialization_failed_notifications_stil);
          ttsStatus=getString(R.string.ui_tts_engine_unavailable_check_system_speech_set);
          ttsTestPending=false;send();
          return;
        }
        Locale chosen=getResources().getConfiguration().getLocales().get(0);
        Locale speechLocale="zh".equals(chosen.getLanguage())?
          Locale.SIMPLIFIED_CHINESE:Locale.US;
        int language=tts.setLanguage(speechLocale);
        if((language==TextToSpeech.LANG_MISSING_DATA||
            language==TextToSpeech.LANG_NOT_SUPPORTED)&&"zh".equals(chosen.getLanguage()))
          language=tts.setLanguage(Locale.CHINESE);
        reminderTtsReady=language!=TextToSpeech.LANG_MISSING_DATA&&
          language!=TextToSpeech.LANG_NOT_SUPPORTED;
        tts.setSpeechRate(1.05f);
        tts.setAudioAttributes(new AudioAttributes.Builder()
          .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
          .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
        if(!reminderTtsReady){
          reminderMessage=getString(R.string.ui_requested_voice_language_unavailable_notificat);
          ttsStatus=getString(R.string.ui_speech_language_missing_install_voice_language);
          ttsTestPending=false;
        }else{
          ttsStatus=getString(R.string.ui_voice_ready);
          if(ttsTestPending){
            ttsTestPending=false;
            int result=tts.speak(getString(R.string.speech_heart_rate,150),
              TextToSpeech.QUEUE_FLUSH,null,"coros_hr_voice_test");
            ttsStatus=result==TextToSpeech.SUCCESS?getString(R.string.ui_speech_test_started_check_media_volume):getString(R.string.ui_speech_test_failed_check_tts_engine);
          }
        }
        send();
      }));
    }catch(Exception error){
      reminderTtsReady=false;reminderTtsFailed=true;
      reminderMessage=getString(R.string.ui_speech_unavailable_notifications_still_availab);
      ttsStatus=getString(R.string.ui_speech_engine_unavailable_check_tts_settings);
      ttsTestPending=false;send();
      Log.w("CorosHrRecorder","TTS unavailable",error);
    }
  }
  private void publishHeartRateReminder(int currentBpm){
    if(Build.VERSION.SDK_INT>=33&&
        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
      return;
    Notification.Builder notification=new Notification.Builder(this,"hr_reminders")
      .setSmallIcon(android.R.drawable.ic_dialog_info)
      .setContentTitle(getString(R.string.ui_heart_rate)+currentBpm+" BPM")
      .setContentText(getString(R.string.ui_periodic_hr_reminder_every)+reminderSchedule.minutes()+getString(R.string.ui_minutes))
      .setAutoCancel(true)
      .setCategory(Notification.CATEGORY_REMINDER)
      .setContentIntent(PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),
        PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT));
    ((NotificationManager)getSystemService(NOTIFICATION_SERVICE))
      .notify(REMINDER_NOTIFICATION_ID,notification.build());
  }
  private void cancelReminder(){
    handler.removeCallbacks(reminderTick);
    reminderSchedule.configure(false,false,reminderSchedule.minutes(),SystemClock.elapsedRealtime());
    if(reminderTts!=null)reminderTts.stop();
    ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).cancel(REMINDER_NOTIFICATION_ID);
    reminderMessage=getString(R.string.ui_stopped);
  }
  private void foreground(){
    Notification notification=new Notification.Builder(this,"hr_monitor")
      .setSmallIcon(android.R.drawable.ic_media_play)
      .setContentTitle(recording?getString(R.string.ui_recording_heart_rate):getString(R.string.ui_heart_rate_sensor_connection))
      .setContentText(state)
      .setOngoing(true)
      .setContentIntent(PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),
        PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT)).build();
    if(Build.VERSION.SDK_INT>=29){
      int types=android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE;
      if(gpsRequested&&recording&&
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED)
        types|=android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION;
      try{
        startForeground(2026,notification,types);
      }catch(SecurityException ex){
        gpsRequested=false;
        startForeground(2026,notification,
          android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
      }
    }else startForeground(2026,notification);
  }
  private boolean allowed(){
    return Build.VERSION.SDK_INT<31 ||
      checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED;
  }
  private void close(){
    handler.removeCallbacks(reconnectTask);
    stopProbe(getString(R.string.ui_bluetooth_connection_closed));
    handler.removeCallbacks(probeDescriptorTimeout);
    probePending=null;
    handler.removeCallbacks(batteryPoll);
    batteryCharacteristic=null;
    batteryReadPending=false;
    if(gatt==null)return;
    try{if(allowed())gatt.disconnect();gatt.close();}catch(Exception ignored){}
    gatt=null;
  }
  private void connect(){
    connected=false;
    subscribed=false;
    resetBattery(getString(R.string.ui_waiting_for_sensor));
    gattDiagnostics=getString(R.string.ui_connecting_copy_diagnostics_shortly);
    if(address==null||!allowed()){state=getString(R.string.ui_missing_device_address_or_bluetooth_permission);send();return;}
    close();
    try{
      BluetoothManager m=(BluetoothManager)getSystemService(BLUETOOTH_SERVICE);
      BluetoothAdapter a=m==null?null:m.getAdapter();
      if(a==null||!a.isEnabled()){state=getString(R.string.ui_bluetooth_disabled);send();return;}
      state=getString(R.string.ui_connecting)+deviceName;send();
      gatt=a.getRemoteDevice(address).connectGatt(this,false,gattCallback,BluetoothDevice.TRANSPORT_LE);
    }catch(Exception e){state=getString(R.string.ui_connection_error)+e.getMessage();send();retry();}
  }
  private void retry(){
    if(shuttingDown||address==null)return;
    int delay=Math.min(30000,1500*(1<<Math.min(4,attempts++)));
    // Cancel only the previous reconnect attempt: clearing the entire Handler
    // also discards the reminder, probe and battery timers.
    handler.removeCallbacks(reconnectTask);
    handler.postDelayed(reconnectTask,delay);
  }
  private final BluetoothGattCallback gattCallback=new BluetoothGattCallback(){
    @Override public void onConnectionStateChange(BluetoothGatt g,int status,int s){
      if(g!=gatt)return;
      if(status==BluetoothGatt.GATT_SUCCESS&&s==BluetoothProfile.STATE_CONNECTED){
        connected=true;subscribed=false;attempts=0;
        handler.removeCallbacks(reconnectTask);
        resetBattery(getString(R.string.ui_checking_battery_service));
        state=getString(R.string.ui_connected_discovering_services);send();
        if(allowed())g.discoverServices();
      }else if(s==BluetoothProfile.STATE_DISCONNECTED||status!=BluetoothGatt.GATT_SUCCESS){
        stopProbe(getString(R.string.ui_connection_lost));
        connected=false;subscribed=false;
        resetBattery(getString(R.string.ui_disconnected_3ed415));
        state=recording?getString(R.string.ui_bluetooth_lost_reconnecting):getString(R.string.ui_device_disconnected_a1a893);send();retry();
      }
    }
    @Override public void onServicesDiscovered(BluetoothGatt g,int status){
      if(g!=gatt)return;
      BluetoothGattService batteryService=status==BluetoothGatt.GATT_SUCCESS?g.getService(BATTERY_SERVICE):null;
      batteryCharacteristic=batteryService==null?null:batteryService.getCharacteristic(BATTERY_LEVEL);
      if(batteryCharacteristic==null){
        batteryStatus=getString(R.string.ui_no_standard_battery_service);
      }else if((batteryCharacteristic.getProperties()&BluetoothGattCharacteristic.PROPERTY_READ)==0){
        batteryCharacteristic=null;
        batteryStatus=getString(R.string.ui_battery_characteristic_is_not_readable);
      }else batteryStatus=getString(R.string.ui_waiting_for_heart_rate_subscription);
      if(status==BluetoothGatt.GATT_SUCCESS){
        StringBuilder services=new StringBuilder(getString(R.string.ui_device)+deviceName+"\n");
        for(BluetoothGattService service:g.getServices()){
          services.append(getString(R.string.ui_service)).append(service.getUuid()).append("\n");
          for(BluetoothGattCharacteristic item:service.getCharacteristics())
            services.append(getString(R.string.ui_characteristic)).append(item.getUuid()).append(getString(R.string.ui_properties))
              .append(item.getProperties()).append("\n");
        }
        gattDiagnostics=services.toString();
        Log.i("CorosHrRecorder","BLE GATT services: "+gattDiagnostics);
      }
      send();
      BluetoothGattService svc=status==BluetoothGatt.GATT_SUCCESS?g.getService(HR):null;
      BluetoothGattCharacteristic c=svc==null?null:svc.getCharacteristic(DATA);
      if(c==null){state=getString(R.string.ui_standard_hr_service_180d_2a37_missing);send();return;}
      BluetoothGattDescriptor descriptor=c.getDescriptor(CCCD);
      if(descriptor==null||!allowed()||!g.setCharacteristicNotification(c,true)){
        state=getString(R.string.ui_hr_subscription_failed);send();return;
      }
      byte[] config=(c.getProperties()&BluetoothGattCharacteristic.PROPERTY_NOTIFY)!=0?
        BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE:
        BluetoothGattDescriptor.ENABLE_INDICATION_VALUE;
      if(Build.VERSION.SDK_INT>=33){
        int result=g.writeDescriptor(descriptor,config);
        if(result!=BluetoothStatusCodes.SUCCESS){state=getString(R.string.ui_subscription_write_failed)+result;send();}
      }else{
        descriptor.setValue(config);
        if(!g.writeDescriptor(descriptor)){state=getString(R.string.ui_subscription_write_failed_d12a4d);send();}
      }
    }
    @Override public void onDescriptorWrite(BluetoothGatt g,BluetoothGattDescriptor d,int status){
      if(g!=gatt||!CCCD.equals(d.getUuid()))return;
      BluetoothGattCharacteristic owner=d.getCharacteristic();
      if(owner==null)return;
      if(!DATA.equals(owner.getUuid())){
        handler.post(()->probeDescriptorDone(g,owner,status));
        return;
      }
      subscribed=status==BluetoothGatt.GATT_SUCCESS;
      state=subscribed?getString(R.string.ui_subscribed_to_hr_notifications):getString(R.string.ui_hr_subscription_error)+status;send();
      if(subscribed){
        handler.post(()->{
          if(g!=gatt||shuttingDown||!subscribed)return;
          if(address!=null){
            getSharedPreferences("last_ble_device",MODE_PRIVATE).edit()
              .putString("address",address).putString("name",deviceName).apply();
          }
          if(autoStartPending&&!recording){
            autoStartPending=false;
            startRecord();
          }
        });
      }
      if(subscribed&&batteryCharacteristic!=null){
        readBattery();
        handler.removeCallbacks(batteryPoll);
        handler.postDelayed(batteryPoll,BATTERY_POLL_INTERVAL_MS);
      }
    }
    @Override public void onCharacteristicRead(BluetoothGatt g,BluetoothGattCharacteristic c,byte[] v,int status){
      if(g==gatt&&BATTERY_LEVEL.equals(c.getUuid()))batteryResult(v,status);
    }
    @SuppressWarnings("deprecation")
    @Override public void onCharacteristicRead(BluetoothGatt g,BluetoothGattCharacteristic c,int status){
      if(Build.VERSION.SDK_INT<33&&g==gatt&&BATTERY_LEVEL.equals(c.getUuid()))
        batteryResult(c.getValue(),status);
    }
    @Override public void onCharacteristicChanged(BluetoothGatt g,BluetoothGattCharacteristic c,byte[] v){
      if(g!=gatt)return;
      if(DATA.equals(c.getUuid()))measurement(v);
      captureProbe(g,c,v);
    }
    @SuppressWarnings("deprecation")
    @Override public void onCharacteristicChanged(BluetoothGatt g,BluetoothGattCharacteristic c){
      if(Build.VERSION.SDK_INT<33&&g==gatt){
        byte[] value=c.getValue();
        if(DATA.equals(c.getUuid()))measurement(value);
        captureProbe(g,c,value);
      }
    }
  };
  private void probeEvent(String note){
    BleProbeLog log=probeLog;
    if(log==null)return;
    try{
      log.event(System.currentTimeMillis(),SystemClock.elapsedRealtimeNanos(),note);
    }catch(IOException e){
      handler.post(()->stopProbe(getString(R.string.ui_log_write_failure)));
    }
  }
  private void startProbe(){
    if(probeActive){send();return;}
    if(probePending!=null){
      probeState=getString(R.string.ui_subscription_still_active_retry);send();return;
    }
    if(recording||!connected||!subscribed||gatt==null||!allowed()){
      probeState=getString(R.string.ui_connect_sensor_and_stop_recording_before_diagn);send();return;
    }
    try{
      probeLog=new BleProbeLog(new File(getFilesDir(),"ble_diagnostics"),System.currentTimeMillis());
    }catch(IOException e){
      probeState=getString(R.string.ui_cannot_create_diagnostics_file)+e.getMessage();send();return;
    }
    probeActive=true;
    probeFileName=null;
    probeIndex=0;
    probeSubscribed=0;
    probeState=getString(R.string.ui_subscribing_to_private_notify_channels);
    probeDeadline=SystemClock.elapsedRealtime()+60000;
    probeEvent("START_passive_notify_only_no_vendor_command");
    handler.removeCallbacks(probeStop);
    handler.removeCallbacks(probeTick);
    handler.postDelayed(probeStop,60000);
    handler.post(probeTick);
    handler.post(probeRetry);
    send();
  }
  private void subscribeNextProbe(){
    if(!probeActive||gatt==null||!connected||!subscribed||!allowed()||probePending!=null)return;
    if(batteryReadPending){
      handler.removeCallbacks(probeRetry);
      handler.postDelayed(probeRetry,250);
      return;
    }
    while(probeIndex<BleProbeLog.SERVICES.length){
      int index=probeIndex++;
      UUID serviceId=UUID.fromString(BleProbeLog.SERVICES[index]);
      UUID characteristicId=UUID.fromString(BleProbeLog.NOTIFY[index]);
      BluetoothGattService service=gatt.getService(serviceId);
      BluetoothGattCharacteristic characteristic=service==null?null:service.getCharacteristic(characteristicId);
      if(characteristic==null){
        probeEvent("MISSING_"+BleProbeLog.NAMES[index].replace(' ','_'));
        continue;
      }
      if((characteristic.getProperties()&BluetoothGattCharacteristic.PROPERTY_NOTIFY)==0){
        probeEvent("NOT_NOTIFY_"+BleProbeLog.NAMES[index].replace(' ','_'));
        continue;
      }
      BluetoothGattDescriptor descriptor=characteristic.getDescriptor(CCCD);
      if(descriptor==null){
        probeEvent("MISSING_CCCD_"+BleProbeLog.NAMES[index].replace(' ','_'));
        continue;
      }
      try{
        if(!gatt.setCharacteristicNotification(characteristic,true)){
          probeEvent("LOCAL_NOTIFY_FAILED_"+BleProbeLog.NAMES[index].replace(' ','_'));
          continue;
        }
        probePending=characteristic;
        boolean queued;
        if(Build.VERSION.SDK_INT>=33){
          queued=gatt.writeDescriptor(descriptor,BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            ==BluetoothStatusCodes.SUCCESS;
        }else{
          descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
          queued=gatt.writeDescriptor(descriptor);
        }
        if(!queued){
          probePending=null;
          probeEvent("CCCD_WRITE_NOT_QUEUED_"+BleProbeLog.NAMES[index].replace(' ','_'));
          continue;
        }
        handler.removeCallbacks(probeDescriptorTimeout);
        handler.postDelayed(probeDescriptorTimeout,7000);
        probeState=getString(R.string.ui_subscribing)+BleProbeLog.NAMES[index];
        probeEvent("CCCD_NOTIFY_REQUEST_"+BleProbeLog.NAMES[index].replace(' ','_'));
        send();
        return;
      }catch(Exception e){
        probePending=null;
        probeEvent("CCCD_EXCEPTION_"+e.getClass().getSimpleName());
      }
    }
    probeState=getString(R.string.ui_listening)+probeSubscribed+getString(R.string.ui_text_3_private_channels_subscribed);
    probeEvent("READY_subscribed_"+probeSubscribed);
    send();
  }
  private void probeDescriptorDone(BluetoothGatt device,BluetoothGattCharacteristic owner,int status){
    if(probePending!=owner)return;
    handler.removeCallbacks(probeDescriptorTimeout);
    probePending=null;
    if(!probeActive||device!=gatt)return;
    int index=-1;
    for(int i=0;i<BleProbeLog.NOTIFY.length;i++)
      if(BleProbeLog.NOTIFY[i].equalsIgnoreCase(owner.getUuid().toString()))index=i;
    if(status==BluetoothGatt.GATT_SUCCESS){
      probeSubscribed++;
      probeEvent("CCCD_NOTIFY_OK_"+(index<0?"unknown":BleProbeLog.NAMES[index].replace(' ','_')));
    }else{
      probeEvent("CCCD_NOTIFY_FAILED_"+status);
    }
    subscribeNextProbe();
  }
  private void captureProbe(BluetoothGatt device,BluetoothGattCharacteristic characteristic,byte[] raw){
    BleProbeLog log=probeLog;
    if(!probeActive||log==null||device!=gatt||raw==null)return;
    BluetoothGattService service=characteristic.getService();
    if(service==null||BleProbeLog.channel(service.getUuid(),characteristic.getUuid())<0)return;
    try{
      log.notification(System.currentTimeMillis(),SystemClock.elapsedRealtimeNanos(),
        service.getUuid(),characteristic.getUuid(),raw);
    }catch(IOException e){
      handler.post(()->stopProbe(getString(R.string.ui_log_write_failure_e5b669)+e.getMessage()));
    }
  }
  private void stopProbe(String reason){
    if(!probeActive)return;
    probeActive=false;
    handler.removeCallbacks(probeTick);
    handler.removeCallbacks(probeStop);
    handler.removeCallbacks(probeRetry);
    handler.removeCallbacks(probeDescriptorTimeout);
    probeEvent("END_"+reason.replace(' ','_'));
    BleProbeLog log=probeLog;
    if(log!=null){
      try{
        log.close();
        probeFileName=log.file().getName();
        getSharedPreferences("ble_probe",MODE_PRIVATE).edit()
          .putString("lastFile",probeFileName).apply();
      }
      catch(IOException e){probeState=getString(R.string.ui_cannot_save_diagnostics)+e.getMessage();send();return;}
    }
    probeState=reason+getString(R.string.ui_saved)+(log==null?0:log.total())+getString(R.string.ui_notifications);
    send();
  }
  private void resetBattery(String reason){
    handler.removeCallbacks(batteryPoll);
    batteryCharacteristic=null;
    batteryReadPending=false;
    batteryPercent=-1;
    batteryStatus=reason;
  }
  private void readBattery(){
    if(probeActive)return; // Serialise the private CCCD writes ahead of optional battery reads.
    BluetoothGatt current=gatt;
    if(current==null||!connected||!subscribed||batteryCharacteristic==null||!allowed()){
      if(batteryCharacteristic==null&&connected)
        batteryStatus=getString(R.string.ui_no_standard_battery_service);
      send();
      return;
    }
    if(batteryReadPending)return;
    batteryReadPending=true;
    batteryStatus=getString(R.string.ui_reading);
    boolean initiated;
    try{
      initiated=current.readCharacteristic(batteryCharacteristic);
    }catch(SecurityException|IllegalArgumentException error){
      batteryReadPending=false;
      batteryPercent=-1;
      batteryStatus=getString(R.string.ui_battery_read_error)+error.getClass().getSimpleName();
      send();
      return;
    }
    if(!initiated){
      batteryReadPending=false;
      batteryPercent=-1;
      batteryStatus=getString(R.string.ui_failed_to_request_battery_read);
      send();
      return;
    }
    send();
    handler.postDelayed(()->{
      if(gatt==current&&batteryReadPending){
        batteryReadPending=false;
        batteryPercent=-1;
        batteryStatus=getString(R.string.ui_battery_read_timed_out_retry);
        send();
      }
    },BATTERY_TIMEOUT_MS);
  }
  private void batteryResult(byte[] raw,int status){
    batteryReadPending=false;
    batteryPercent=-1;
    if(status!=BluetoothGatt.GATT_SUCCESS){
      batteryStatus=getString(R.string.ui_battery_read_failed_gatt)+status+"）";
    }else{
      int parsed=BatteryLevel.parse(raw);
      if(parsed<0)batteryStatus=getString(R.string.ui_invalid_battery_data_received);
      else{
        batteryPercent=parsed;
        batteryStatus=getString(R.string.ui_ok);
      }
    }
    send();
  }
  private synchronized void measurement(byte[] v){
    if(v==null||v.length<2)return;
    boolean is16=(v[0]&1)!=0;
    if(is16&&v.length<3)return;
    int h=is16?((v[1]&255)|((v[2]&255)<<8)):(v[1]&255);
    if(h<=0||h>255)return;
    bpm=h;
    lastHeartRateAtMs=SystemClock.elapsedRealtime();
    if(recording&&store!=null){
      try{count=store.write(System.currentTimeMillis(),SystemClock.elapsedRealtimeNanos(),h,v);}
      catch(Exception e){state=getString(R.string.ui_write_error)+e.getMessage();stopRecord();}
    }
    // A BLE callback may wake the app after device deep sleep. Re-check using
    // elapsedRealtime so reminders don't depend only on Handler uptime timers.
    if(recording)handler.post(()->{
      if(recording&&reminderSchedule.due(SystemClock.elapsedRealtime()))
        reminderTick.run();
    });
    send();
  }
  private synchronized void startRecord(){
    if(recording)return;
    if(probeActive){state=getString(R.string.ui_stop_ble_diagnostics_before_recording);send();return;}
    if(!connected||!subscribed){state=getString(R.string.ui_wait_until_hr_subscription_is_ready);send();return;}
    try{
      store=new SessionStore(new File(getFilesDir(),"sessions"));
      count=0;recording=true;state=getString(R.string.ui_recording);
      foreground();
      motion=new MotionGpsRecorder(this,store.file(),()->send());
      motion.start(gpsRequested,stepsRequested);
      refreshReminderSettings();
      send();
    }catch(Exception e){state=getString(R.string.ui_could_not_create_session)+e.getMessage();send();}
  }
  private synchronized void stopRecord(){
    cancelReminder();
    if(motion!=null){
      lastDistance=motion.meters();lastSteps=motion.steps();lastCadence=motion.cadence();
      lastStride=motion.stride();lastAccuracy=motion.accuracy();lastSpeed=motion.speed();
      lastGpsStatus=motion.gpsStatus();lastStepStatus=motion.stepStatus();
      motion.close();motion=null;
    }
    gpsRequested=false;stepsRequested=false;
    String savedFile=null;
    IOException saveError=null;
    if(store!=null){
      savedFile=store.file().getName();
      try{store.close();}catch(IOException e){saveError=e;}
      store=null;
    }
    recording=false;
    if(saveError!=null){
      state=getString(R.string.ui_failed_to_close_session)+saveError.getMessage();
    }else if(savedFile!=null){
      state=getString(R.string.ui_saved_3ac430)+count+getString(R.string.ui_hr_samples);
    }else if(connected&&subscribed){
      state=getString(R.string.ui_subscribed_to_hr_notifications);
    }
    send(savedFile,saveError==null);
    if(address!=null&&!shuttingDown)foreground();
  }
  private void send(){send(null,true);}
  private void send(String savedFile,boolean saveOk){
    Intent i=new Intent(EVENT);i.setPackage(getPackageName());
    if(savedFile!=null){
      i.putExtra("savedFile",savedFile);
      i.putExtra("saveOk",saveOk);
    }
    i.putExtra("state",state);i.putExtra("device",deviceName);
    i.putExtra("connected",connected&&subscribed);
    i.putExtra("recording",recording);i.putExtra("bpm",bpm);i.putExtra("count",count);
    i.putExtra("batteryPercent",batteryPercent);
    i.putExtra("batteryStatus",batteryStatus);
    i.putExtra("batteryReadable",connected&&subscribed&&batteryCharacteristic!=null);
    MotionGpsRecorder tracker=motion;
    i.putExtra("gpsMeters",tracker!=null?tracker.meters():lastDistance);
    i.putExtra("gpsAccuracy",tracker!=null?tracker.accuracy():lastAccuracy);
    i.putExtra("gpsSpeed",tracker!=null?tracker.speed():lastSpeed);
    i.putExtra("steps",tracker!=null?tracker.steps():lastSteps);
    i.putExtra("cadence",tracker!=null?tracker.cadence():lastCadence);
    i.putExtra("stride",tracker!=null?tracker.stride():lastStride);
    i.putExtra("gpsStatus",tracker!=null?tracker.gpsStatus():lastGpsStatus);
    i.putExtra("stepStatus",tracker!=null?tracker.stepStatus():lastStepStatus);
    i.putExtra("gattDiagnostics",gattDiagnostics);
    i.putExtra("probeActive",probeActive);
    i.putExtra("probeReady",connected&&subscribed&&!recording&&probePending==null);
    i.putExtra("probeState",probeState);
    i.putExtra("probeRemaining",probeActive?Math.max(0,(int)((probeDeadline-SystemClock.elapsedRealtime()+999)/1000)):0);
    i.putExtra("probeSubscribed",probeSubscribed);
    int[] probeCounts=probeLog==null?new int[4]:probeLog.counts();
    i.putExtra("probeCounts",probeCounts);
    i.putExtra("probeFileName",probeFileName);
    i.putExtra("reminderActive",reminderSchedule.active());
    i.putExtra("reminderMinutes",reminderSchedule.minutes());
    i.putExtra("reminderLast",reminderMessage);
    i.putExtra("ttsStatus",ttsStatus);
    i.putExtra("reminderSecondsToNext",
      reminderSchedule.active()?(int)((reminderSchedule.untilDue(SystemClock.elapsedRealtime())+999)/1000):-1);
    sendBroadcast(i);
  }
  @Override public void onDestroy(){
    shuttingDown=true;cancelReminder();
    if(reminderTts!=null){
      reminderTts.stop();
      reminderTts.shutdown();
      reminderTts=null;
    }
    stopProbe(getString(R.string.ui_service_stopped));handler.removeCallbacksAndMessages(null);stopRecord();close();super.onDestroy();
  }
  @Override public IBinder onBind(Intent i){return null;}
}
