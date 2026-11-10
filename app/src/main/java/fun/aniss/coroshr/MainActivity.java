package fun.aniss.coroshr;

import android.Manifest;
import android.app.*;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;

public class MainActivity extends Activity {
  private static final int PERMISSION_REQUEST=101, SAVE_REQUEST=102, WORKOUT_PERMISSION_REQUEST=103, SAVE_PROBE_REQUEST=104, AUTO_RESTORE_PERMISSION_REQUEST=105, CONNECT_AUTO_PERMISSION_REQUEST=106, REMINDER_NOTIFICATION_PERMISSION_REQUEST=107;
  private final Handler handler=new Handler(Looper.getMainLooper());
  private final Map<String,BluetoothDevice> found=new LinkedHashMap<>();
  private BluetoothLeScanner scanner;
  private boolean scanning,connected,recording,batteryReadable;
  private String pendingConnectAddress,pendingConnectName;
  private String selected,latestKnown;
  private String gattDiagnostics;
  private File pendingFile;
  private File pendingProbeFile;
  private boolean probeActive,probeReady;
  private String probeFileName;
  private LinearLayout deviceList,historyList;
  private final List<SessionCatalog.Entry> sessionIndex=new ArrayList<>();
  private SessionAnnotations sessionAnnotations;
  private EditText historySearch;
  private Spinner historyMonths,historyOrder;
  private CheckBox historyNotesOnly;
  private String selectedMonth="";
  private int historySortMode=SessionCatalog.NEWEST,historyVisibleCount=60;
  private LinearLayout homePage,historyPage,settingsPage;
  private ScrollView contentScroll;
  private final Button[] tabs=new Button[3];
  private int selectedTab;
  private TextView heart,status,device,battery,count,label,motionInfo,probeStatus,probeCounts,lastDeviceLabel,reminderStatus,ttsStatusView,homeReminder;
  private Button scanButton,startButton,stopButton,fitButton,csvButton,batteryRefreshButton,diagnosticsButton,probeStartButton,probeStopButton,probeExportButton,homeDeviceButton;
  private CheckBox gpsCheck,stepsCheck,autoRecordCheck,reminderCheck;
  private EditText reminderMinutesInput;
  private int dp(int px){return Math.round(px*getResources().getDisplayMetrics().density);}
  private TextView text(String s,int size,boolean bold){
    TextView t=new TextView(this);t.setText(s);t.setTextSize(size);
    t.setTextColor(0xFF18313D);
    if(bold)t.setTypeface(null,Typeface.BOLD);return t;
  }
  private Button btn(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);return b;}
  private LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
  private void add(LinearLayout parent,View v,int gap){
    LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
    p.bottomMargin=dp(gap);parent.addView(v,p);
  }
  private LinearLayout card(LinearLayout parent){
    LinearLayout c=col();c.setPadding(dp(16),dp(16),dp(16),dp(16));
    android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();
    bg.setColor(Color.WHITE);bg.setCornerRadius(dp(15));c.setBackground(bg);
    add(parent,c,12);return c;
  }
  @Override public void onCreate(Bundle state){
    super.onCreate(state);
    gattDiagnostics=getString(R.string.ui_no_service_details_yet);
    sessionAnnotations=new SessionAnnotations(this);
    LinearLayout root=col();
    root.setBackgroundColor(0xFFF1F5F7);
    LinearLayout heading=col();heading.setPadding(dp(20),dp(20),dp(20),dp(10));
    add(heading,text(getString(R.string.ui_coros_heart_rate_recorder),23,true),2);
    add(heading,text(getString(R.string.nav_subtitle),12,false),0);
    add(root,heading,5);
    LinearLayout nav=new LinearLayout(this);
    nav.setOrientation(LinearLayout.HORIZONTAL);
    nav.setPadding(dp(12),dp(5),dp(12),dp(10));
    nav.setBackgroundColor(Color.WHITE);
    int[] labels={R.string.nav_workout,R.string.nav_history,R.string.nav_settings};
    for(int i=0;i<3;i++){
      final int page=i;
      Button tab=btn(getString(labels[i]));
      tab.setTextSize(13);
      LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(48),1f);
      lp.setMargins(dp(3),0,dp(3),0);
      nav.addView(tab,lp);
      tabs[i]=tab;
      tab.setOnClickListener(v->showTab(page));
    }
    contentScroll=new ScrollView(this);
    contentScroll.setFillViewport(true);
    LinearLayout content=col();
    content.setPadding(dp(16),dp(8),dp(16),dp(28));
    contentScroll.addView(content);
    root.addView(contentScroll,new LinearLayout.LayoutParams(-1,0,1));
    add(root,nav,0);
    setContentView(root);
    SafeArea.apply(this,root);
    homePage=col();historyPage=col();settingsPage=col();
    add(content,homePage,0);add(content,historyPage,0);add(content,settingsPage,0);
    LinearLayout live=card(homePage);
    add(live,text(getString(R.string.ui_live_heart_rate),15,true),5);
    heart=text("-- BPM",46,true);heart.setTextColor(0xFF007E78);add(live,heart,8);
    device=text(getString(R.string.ui_device_disconnected),13,false);add(live,device,4);
    battery=text(getString(R.string.ui_sensor_battery_disconnected),14,true);add(live,battery,5);
    status=text(getString(R.string.ui_status_disconnected),13,false);add(live,status,4);
    count=text(getString(R.string.ui_saved_0_samples),13,false);add(live,count,0);

    LinearLayout ble=card(settingsPage);
    add(ble,text(getString(R.string.ui_bluetooth),18,true),5);
    lastDeviceLabel=text(getString(R.string.ui_last_sensor_remembered_after_first_connection),13,false);
    add(ble,lastDeviceLabel,4);
    autoRecordCheck=new CheckBox(this);
    autoRecordCheck.setText(getString(R.string.ui_auto_connect_and_start_recording_when_opening));
    autoRecordCheck.setTextSize(13);
    autoRecordCheck.setChecked(getPreferences(0).getBoolean("autoRecord",true));
    add(ble,autoRecordCheck,6);
    autoRecordCheck.setOnCheckedChangeListener((v,value)->
      getPreferences(0).edit().putBoolean("autoRecord",value).apply());
    scanButton=btn(getString(R.string.ui_scan_for_nearby_bluetooth_devices));add(ble,scanButton,7);
    batteryRefreshButton=btn(getString(R.string.ui_refresh_sensor_battery));add(ble,batteryRefreshButton,7);
    batteryRefreshButton.setOnClickListener(v->command(HrService.REFRESH_BATTERY,null,null));
    diagnosticsButton=btn(getString(R.string.ui_copy_ble_service_diagnostics));add(ble,diagnosticsButton,7);
    diagnosticsButton.setOnClickListener(v->{
      android.content.ClipboardManager clipboard=(android.content.ClipboardManager)
          getSystemService(CLIPBOARD_SERVICE);
      clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.ui_ble_gatt_diagnostics),gattDiagnostics));
      toast(getString(R.string.ui_ble_service_details_copied_to_clipboard));
    });
    scanButton.setOnClickListener(v->requestAndScan());
    deviceList=col();ble.addView(deviceList);
    Button disconnect=btn(getString(R.string.ui_disconnect));add(ble,disconnect,0);
    disconnect.setOnClickListener(v->command(HrService.DISCONNECT,null,null));

    LinearLayout record=card(homePage);
    add(record,text(getString(R.string.ui_recording_controls),18,true),6);
    startButton=btn(getString(R.string.ui_start_recording));add(record,startButton,4);
    stopButton=btn(getString(R.string.ui_stop_and_save));add(record,stopButton,4);
    startButton.setMinHeight(dp(56));stopButton.setMinHeight(dp(56));
    startButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF007E78));
    startButton.setTextColor(Color.WHITE);
    stopButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFB9493F));
    stopButton.setTextColor(Color.WHITE);
    motionInfo=text(getString(R.string.ui_gps_off_steps_off),12,false);
    add(record,motionInfo,8);
    android.content.SharedPreferences reminderSummary=getSharedPreferences("hr_reminders",MODE_PRIVATE);
    homeReminder=text(reminderSummary.getBoolean("enabled",false)?
      getString(R.string.home_reminder_every)+reminderSummary.getInt("minutes",5)+
        getString(R.string.home_minute_suffix):getString(R.string.home_reminder_off),13,false);
    add(record,homeReminder,8);
    Button openDevices=btn(getString(R.string.home_connect_sensor));add(record,openDevices,5);
    openDevices.setOnClickListener(v->showTab(2));
    homeDeviceButton=openDevices;
    Button browse=btn(getString(R.string.home_open_history));add(record,browse,0);
    browse.setOnClickListener(v->showTab(1));
    startButton.setOnClickListener(v->startWorkout());
    stopButton.setOnClickListener(v->new AlertDialog.Builder(this)
      .setTitle(R.string.confirm_stop_title)
      .setMessage(R.string.confirm_stop_message)
      .setNegativeButton(android.R.string.cancel,null)
      .setPositiveButton(R.string.confirm_stop_action,(dialog,which)->{
        command(HrService.STOP,null,null);
        // Fallback when a save event is missed during an activity lifecycle change.
        handler.postDelayed(this::refresh,650);
      }).show());

    LinearLayout files=card(historyPage);
    add(files,text(getString(R.string.ui_sessions_export),18,true),6);
    Button refreshFiles=btn(getString(R.string.ui_refresh_sessions));add(files,refreshFiles,5);
    refreshFiles.setOnClickListener(v->{
      refresh();
      toast(getString(R.string.ui_refreshed_timestamps_are_shown_in_local_time));
    });
    Button compare=btn(getString(R.string.ui_landscape_fit_history_comparison));add(files,compare,7);
    compare.setOnClickListener(v->startActivity(new Intent(this,CompareActivity.class)
      .putExtra("focusFilename",selected)));
    label=text(getString(R.string.ui_no_recordings_yet),12,false);add(files,label,5);
    fitButton=btn(getString(R.string.ui_export_fit_activity));add(files,fitButton,4);
    csvButton=btn(getString(R.string.ui_export_raw_csv));add(files,csvButton,8);
    fitButton.setOnClickListener(v->export(true));
    csvButton.setOnClickListener(v->export(false));
    historySearch=new EditText(this);
    historySearch.setSingleLine(true);
    historySearch.setTextSize(14);
    historySearch.setHint(R.string.history_search_hint);
    add(files,historySearch,6);
    LinearLayout historyFilters=new LinearLayout(this);
    historyFilters.setOrientation(LinearLayout.HORIZONTAL);
    historyMonths=new Spinner(this);
    historyFilters.addView(historyMonths,new LinearLayout.LayoutParams(0,dp(52),1));
    historyOrder=new Spinner(this);
    historyOrder.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,
      new String[]{getString(R.string.history_newest),getString(R.string.history_oldest),
        getString(R.string.history_name_order)}));
    historyFilters.addView(historyOrder,new LinearLayout.LayoutParams(0,dp(52),1));
    add(files,historyFilters,3);
    historyNotesOnly=new CheckBox(this);
    historyNotesOnly.setText(R.string.history_notes_only);
    add(files,historyNotesOnly,6);
    historyList=col();files.addView(historyList);
    historySearch.addTextChangedListener(new TextWatcher(){
      public void beforeTextChanged(CharSequence c,int a,int n,int f){}
      public void onTextChanged(CharSequence c,int a,int before,int count){historyVisibleCount=60;renderHistory();}
      public void afterTextChanged(Editable e){}
    });
    historyNotesOnly.setOnCheckedChangeListener((button,value)->{historyVisibleCount=60;renderHistory();});
    historyOrder.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
      public void onNothingSelected(android.widget.AdapterView<?> parent){}
      public void onItemSelected(android.widget.AdapterView<?> parent,View item,int position,long id){
        historySortMode=position;historyVisibleCount=60;renderHistory();
      }
    });
    historyMonths.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
      public void onNothingSelected(android.widget.AdapterView<?> parent){}
      public void onItemSelected(android.widget.AdapterView<?> parent,View item,int position,long id){
        Object value=historyMonths.getItemAtPosition(position);
        selectedMonth=position==0?"":String.valueOf(value);
        historyVisibleCount=60;renderHistory();
      }
    });

    LinearLayout sensors=card(settingsPage);
    add(sensors,text(getString(R.string.settings_sensor_options),18,true),6);
    gpsCheck=new CheckBox(this);
    gpsCheck.setText(getString(R.string.ui_also_record_phone_gps_route_distance_pace));
    gpsCheck.setTextSize(13);
    gpsCheck.setChecked(getPreferences(0).getBoolean("wantGps",false));
    add(sensors,gpsCheck,1);
    gpsCheck.setOnCheckedChangeListener((v,enabled)->
      getPreferences(0).edit().putBoolean("wantGps",enabled).apply());
    stepsCheck=new CheckBox(this);
    stepsCheck.setText(getString(R.string.ui_also_record_phone_steps_cadence_estimated_stri));
    stepsCheck.setTextSize(13);
    stepsCheck.setChecked(getPreferences(0).getBoolean("wantSteps",false));
    add(sensors,stepsCheck,5);
    stepsCheck.setOnCheckedChangeListener((v,enabled)->
      getPreferences(0).edit().putBoolean("wantSteps",enabled).apply());
    add(sensors,text(getString(R.string.ui_gps_and_steps_are_recorded_only_during_a_sessi),12,false),7);

    LinearLayout reminders=card(settingsPage);
    add(reminders,text(getString(R.string.ui_periodic_heart_rate_reminders),15,true),5);
    android.content.SharedPreferences reminderPrefs=getSharedPreferences("hr_reminders",MODE_PRIVATE);
    reminderCheck=new CheckBox(this);
    reminderCheck.setText(getString(R.string.ui_speak_heart_rate_and_notify_every_n_minutes_wh));
    reminderCheck.setTextSize(13);
    reminderCheck.setChecked(reminderPrefs.getBoolean("enabled",false));
    add(reminders,reminderCheck,4);
    add(reminders,text(getString(R.string.ui_reminder_interval_n_minutes_1_120),12,false),2);
    reminderMinutesInput=new EditText(this);
    reminderMinutesInput.setSingleLine(true);
    reminderMinutesInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
    reminderMinutesInput.setFilters(new android.text.InputFilter[]{
      new android.text.InputFilter.LengthFilter(3)
    });
    reminderMinutesInput.setText(Integer.toString(reminderPrefs.getInt("minutes",5)));
    add(reminders,reminderMinutesInput,4);
    Button saveReminder=btn(getString(R.string.ui_save_reminder_interval));add(reminders,saveReminder,3);
    saveReminder.setOnClickListener(v->saveReminderSettings(false));
    reminderCheck.setOnCheckedChangeListener((v,checked)->saveReminderSettings(checked));
    Button testVoice=btn(getString(R.string.ui_test_spoken_reminder));add(reminders,testVoice,3);
    testVoice.setOnClickListener(v->command(HrService.TEST_CHINESE_VOICE,null,null));
    ttsStatusView=text(getString(R.string.ui_voice_not_tested),12,false);add(reminders,ttsStatusView,4);
    reminderStatus=text(getString(R.string.ui_reminder_not_started),12,false);add(reminders,reminderStatus,8);
    add(reminders,text(getString(R.string.ui_timer_starts_with_recording_stops_when_recordi),12,false),7);
    LinearLayout probe=card(settingsPage);
    add(probe,text(getString(R.string.ui_hrv_research_passive_ble_diagnostics),18,true),5);
    add(probe,text(getString(R.string.ui_subscribes_to_three_proprietary_notify_charact),12,false),6);
    probeStatus=text(getString(R.string.ui_diagnostics_not_started),13,false);add(probe,probeStatus,4);
    probeCounts=text(getString(R.string.ui_weloop_0_nordic_0_coros_0_reference_hr_0),12,false);
    add(probe,probeCounts,7);
    probeStartButton=btn(getString(R.string.ui_start_60_second_diagnostics));
    add(probe,probeStartButton,4);
    probeStartButton.setOnClickListener(v->command(HrService.START_PROBE,null,null));
    probeStopButton=btn(getString(R.string.ui_stop_early_and_save));
    add(probe,probeStopButton,4);
    probeStopButton.setOnClickListener(v->command(HrService.STOP_PROBE,null,null));
    probeExportButton=btn(getString(R.string.ui_export_latest_diagnostics_csv));
    add(probe,probeExportButton,0);
    probeExportButton.setOnClickListener(v->exportProbe());

    Button setting=btn(getString(R.string.ui_open_background_battery_settings));add(settingsPage,setting,7);
    setting.setOnClickListener(v->{
      Intent i=new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
      i.setData(Uri.parse("package:"+getPackageName()));startActivity(i);
    });
    add(settingsPage,text(getString(R.string.ui_when_comparing_watch_optical_heart_rate_do_not),12,false),0);
    refresh();updateButtons();
    updateLastDeviceLabel();
    showTab(state==null?0:state.getInt("selectedTab",0));
    // Only a fresh activity launch initiates restore; rotation / returning from
    // the comparison screen must never restart a manually stopped session.
    if(state==null)handler.post(this::restorePreviousDevice);
  }
  private void showTab(int target){
    selectedTab=Math.max(0,Math.min(2,target));
    LinearLayout[] pages={homePage,historyPage,settingsPage};
    for(int i=0;i<pages.length;i++){
      boolean active=i==selectedTab;
      pages[i].setVisibility(active?View.VISIBLE:View.GONE);
      tabs[i].setBackgroundTintList(android.content.res.ColorStateList.valueOf(
        active?0xFF007E78:0xFFE3ECEF));
      tabs[i].setTextColor(active?Color.WHITE:0xFF18313D);
      tabs[i].setTypeface(null,active?Typeface.BOLD:Typeface.NORMAL);
    }
    if(contentScroll!=null)contentScroll.post(()->contentScroll.scrollTo(0,0));
    if(selectedTab==1&&historyList!=null)refresh();
  }
  @Override protected void onSaveInstanceState(Bundle out){
    out.putInt("selectedTab",selectedTab);
    super.onSaveInstanceState(out);
  }
  @Override public void onBackPressed(){
    if(selectedTab!=0){showTab(0);return;}
    super.onBackPressed();
  }
  private void command(String what,String addr,String name){
    Intent i=new Intent(this,HrService.class).putExtra(HrService.COMMAND,what);
    if(addr!=null)i.putExtra(HrService.ADDRESS,addr);
    if(name!=null)i.putExtra(HrService.NAME,name);
    if(HrService.CONNECT.equals(what)||HrService.RESTORE.equals(what))
      i.putExtra("autoRecord",autoRecordCheck.isChecked());
    if(HrService.START.equals(what)||HrService.CONNECT.equals(what)||HrService.RESTORE.equals(what)){
      i.putExtra("gps",gpsCheck.isChecked()&&
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED);
      i.putExtra("steps",stepsCheck.isChecked()&&
        (Build.VERSION.SDK_INT<29||
        checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION)==PackageManager.PERMISSION_GRANTED));
    }
    if((HrService.CONNECT.equals(what)||HrService.RESTORE.equals(what)||HrService.START.equals(what))&&Build.VERSION.SDK_INT>=26)
      startForegroundService(i);
    else startService(i);
  }
  private void updateLastDeviceLabel(){
    android.content.SharedPreferences p=getSharedPreferences("last_ble_device",MODE_PRIVATE);
    String address=p.getString("address",null);
    String name=p.getString("name",getString(R.string.ui_previously_connected_heart_rate_sensor));
    lastDeviceLabel.setText(address==null?getString(R.string.ui_last_sensor_remembered_after_first_connection):
      getString(R.string.ui_last_sensor)+name+"（"+address+"）");
  }
  private List<String> missingAutoPermissions(boolean requireConnect){
    ArrayList<String> missing=new ArrayList<>();
    if(requireConnect&&Build.VERSION.SDK_INT>=31&&
        checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)
      missing.add(Manifest.permission.BLUETOOTH_CONNECT);
    if(autoRecordCheck.isChecked()){
      if(gpsCheck.isChecked()&&
          checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
        missing.add(Manifest.permission.ACCESS_FINE_LOCATION);
        missing.add(Manifest.permission.ACCESS_COARSE_LOCATION);
      }
      if(stepsCheck.isChecked()&&Build.VERSION.SDK_INT>=29&&
          checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION)!=PackageManager.PERMISSION_GRANTED)
        missing.add(Manifest.permission.ACTIVITY_RECOGNITION);
    }
    return missing;
  }
  private void restorePreviousDevice(){
    if(!getSharedPreferences("last_ble_device",MODE_PRIVATE).contains("address"))return;
    List<String> permissions=missingAutoPermissions(true);
    if(!permissions.isEmpty()){
      requestPermissions(permissions.toArray(new String[0]),AUTO_RESTORE_PERMISSION_REQUEST);
      return;
    }
    command(HrService.RESTORE,null,null);
  }
  private void connectSelected(String address,String name){
    pendingConnectAddress=address;pendingConnectName=name;
    List<String> permissions=missingAutoPermissions(false);
    if(!permissions.isEmpty()){
      requestPermissions(permissions.toArray(new String[0]),CONNECT_AUTO_PERMISSION_REQUEST);
      return;
    }
    command(HrService.CONNECT,address,name);
    pendingConnectAddress=null;pendingConnectName=null;
  }
  private void saveReminderSettings(boolean requestNotificationPermission){
    int minutes;
    try{
      minutes=Integer.parseInt(reminderMinutesInput.getText().toString().trim());
    }catch(NumberFormatException e){
      minutes=HrReminderSchedule.DEFAULT_MINUTES;
    }
    minutes=HrReminderSchedule.clampMinutes(minutes);
    reminderMinutesInput.setText(Integer.toString(minutes));
    getSharedPreferences("hr_reminders",MODE_PRIVATE).edit()
      .putBoolean("enabled",reminderCheck.isChecked())
      .putInt("minutes",minutes).apply();
    if(recording||connected)command(HrService.UPDATE_REMINDER,null,null);
    reminderStatus.setText(reminderCheck.isChecked()?
      getString(R.string.ui_saved_every)+minutes+getString(R.string.ui_minutes_while_recording):getString(R.string.ui_reminders_off));
    homeReminder.setText(reminderCheck.isChecked()?
      getString(R.string.home_reminder_every)+minutes+getString(R.string.home_minute_suffix):
      getString(R.string.home_reminder_off));
    if(requestNotificationPermission&&reminderCheck.isChecked()&&Build.VERSION.SDK_INT>=33&&
        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
      requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
        REMINDER_NOTIFICATION_PERMISSION_REQUEST);
    }
  }
  private void startWorkout(){
    List<String> missing=new ArrayList<>();
    if(gpsCheck.isChecked()&&
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
      missing.add(Manifest.permission.ACCESS_FINE_LOCATION);
      missing.add(Manifest.permission.ACCESS_COARSE_LOCATION);
    }
    if(stepsCheck.isChecked()&&Build.VERSION.SDK_INT>=29&&
        checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION)!=PackageManager.PERMISSION_GRANTED)
      missing.add(Manifest.permission.ACTIVITY_RECOGNITION);
    if(!missing.isEmpty()){
      requestPermissions(missing.toArray(new String[0]),WORKOUT_PERMISSION_REQUEST);
      return;
    }
    command(HrService.START,null,null);
  }
  private boolean permissions(){
    if(Build.VERSION.SDK_INT>=31)
      return checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)==PackageManager.PERMISSION_GRANTED &&
        checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED;
    return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;
  }
  private void requestAndScan(){
    if(permissions()){scan();return;}
    List<String> required=new ArrayList<>();
    if(Build.VERSION.SDK_INT>=31){
      required.add(Manifest.permission.BLUETOOTH_SCAN);
      required.add(Manifest.permission.BLUETOOTH_CONNECT);
    }else required.add(Manifest.permission.ACCESS_FINE_LOCATION);
    if(Build.VERSION.SDK_INT>=33)required.add(Manifest.permission.POST_NOTIFICATIONS);
    requestPermissions(required.toArray(new String[0]),PERMISSION_REQUEST);
  }
  @Override public void onRequestPermissionsResult(int req,String[] perms,int[] results){
    super.onRequestPermissionsResult(req,perms,results);
    if(req==PERMISSION_REQUEST){if(permissions())scan();else toast(getString(R.string.ui_please_grant_bluetooth_permission));}
    else if(req==REMINDER_NOTIFICATION_PERMISSION_REQUEST){
      if(Build.VERSION.SDK_INT>=33&&
          checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
        toast(getString(R.string.ui_notification_permission_denied_speech_may_work));
    }
    else if(req==AUTO_RESTORE_PERMISSION_REQUEST){
      if(Build.VERSION.SDK_INT>=31&&
          checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED){
        toast(getString(R.string.ui_bluetooth_permission_denied_cannot_reconnect));
      }else{
        if(!missingAutoPermissions(false).isEmpty())
          toast(getString(R.string.ui_some_fitness_permissions_denied_recording_only));
        command(HrService.RESTORE,null,null);
      }
    }else if(req==CONNECT_AUTO_PERMISSION_REQUEST){
      if(pendingConnectAddress!=null){
        if(!missingAutoPermissions(false).isEmpty())
          toast(getString(R.string.ui_some_fitness_permissions_denied_only_authorize));
        command(HrService.CONNECT,pendingConnectAddress,pendingConnectName);
        pendingConnectAddress=null;pendingConnectName=null;
      }
    }else if(req==WORKOUT_PERMISSION_REQUEST){
      boolean gpsGranted=!gpsCheck.isChecked()||
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;
      boolean stepGranted=!stepsCheck.isChecked()||Build.VERSION.SDK_INT<29||
        checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION)==PackageManager.PERMISSION_GRANTED;
      if(!gpsGranted||!stepGranted)
        toast(getString(R.string.ui_some_permissions_denied_only_authorized_sensor));
      command(HrService.START,null,null);
    }
  }
  private final ScanCallback callback=new ScanCallback(){
    @Override public void onScanResult(int type,ScanResult result){
      BluetoothDevice d=result.getDevice();if(d==null)return;
      String addr=d.getAddress();if(found.containsKey(addr))return;
      String n=null;
      try{n=d.getName();}catch(SecurityException ignored){}
      if(n==null&&result.getScanRecord()!=null)n=result.getScanRecord().getDeviceName();
      final String name=(n==null||n.isEmpty())?getString(R.string.ui_unnamed_ble_device):n;
      found.put(addr,d);
      runOnUiThread(()->{
        Button b=btn(name+"  ("+result.getRssi()+" dBm)");
        add(deviceList,b,2);
        b.setOnClickListener(v->{stopScan();connectSelected(addr,name);showTab(0);});
      });
    }
    @Override public void onScanFailed(int reason){runOnUiThread(()->toast(getString(R.string.ui_bluetooth_scan_error)+reason));}
  };
  private void scan(){
    stopScan();found.clear();deviceList.removeAllViews();
    BluetoothManager m=(BluetoothManager)getSystemService(BLUETOOTH_SERVICE);
    BluetoothAdapter a=m==null?null:m.getAdapter();
    if(a==null||!a.isEnabled()){toast(getString(R.string.ui_turn_on_bluetooth));return;}
    scanner=a.getBluetoothLeScanner();if(scanner==null){toast(getString(R.string.ui_bluetooth_scanner_unavailable));return;}
    try{
      scanner.startScan(null,new ScanSettings.Builder().setScanMode(
        ScanSettings.SCAN_MODE_LOW_LATENCY).build(),callback);
      scanning=true;scanButton.setText(getString(R.string.ui_scanning_12_seconds));
      handler.postDelayed(this::stopScan,12000);
    }catch(SecurityException e){toast(getString(R.string.ui_bluetooth_permission_missing));}
  }
  private void stopScan(){
    if(scanning&&scanner!=null)try{scanner.stopScan(callback);}catch(Exception ignored){}
    scanning=false;if(scanButton!=null)scanButton.setText(getString(R.string.ui_scan_again));
  }
  private final BroadcastReceiver receiver=new BroadcastReceiver(){
    @Override public void onReceive(Context c,Intent intent){
      boolean previouslyRecording=recording;
      connected=intent.getBooleanExtra("connected",false);
      recording=intent.getBooleanExtra("recording",false);
      batteryReadable=intent.getBooleanExtra("batteryReadable",false);
      probeActive=intent.getBooleanExtra("probeActive",false);
      probeReady=intent.getBooleanExtra("probeReady",false);
      String state=intent.getStringExtra("probeState");
      int seconds=intent.getIntExtra("probeRemaining",0);
      int[] channels=intent.getIntArrayExtra("probeCounts");
      int subscriptions=intent.getIntExtra("probeSubscribed",0);
      String lastFile=intent.getStringExtra("probeFileName");
      if(lastFile!=null)probeFileName=lastFile;
      if(probeStatus!=null)probeStatus.setText(getString(R.string.ui_diagnostics)+(state==null?getString(R.string.ui_not_started):state)+
        (probeActive?getString(R.string.ui_remaining)+seconds+getString(R.string.ui_sec_d9ad50):"")+getString(R.string.ui_subscribed)+subscriptions+"/3");
      if(channels!=null&&channels.length==4&&probeCounts!=null){
        probeCounts.setText("Weloop "+channels[0]+" · Nordic "+channels[1]+
          " · COROS "+channels[2]+getString(R.string.ui_reference_hr)+channels[3]);
      }
      String diagnostics=intent.getStringExtra("gattDiagnostics");
      if(diagnostics!=null)gattDiagnostics=diagnostics;
      int batteryPercent=intent.getIntExtra("batteryPercent",-1);
      String batteryStatus=intent.getStringExtra("batteryStatus");
      if(batteryPercent>=0){
        battery.setText(getString(R.string.ui_sensor_battery_9fe496)+batteryPercent+"%");
        battery.setTextColor(batteryPercent<=20?0xFFC1493F:0xFF007E78);
      }else{
        battery.setText(getString(R.string.ui_sensor_battery)+(batteryStatus==null?getString(R.string.ui_unknown):batteryStatus)+"）");
        battery.setTextColor(0xFF60737A);
      }
      int bpm=intent.getIntExtra("bpm",0);
      heart.setText((bpm>0?Integer.toString(bpm):"--")+" BPM");
      status.setText(getString(R.string.ui_status)+intent.getStringExtra("state"));
      device.setText(getString(R.string.ui_device)+intent.getStringExtra("device"));
      if(connected)updateLastDeviceLabel();
      count.setText(getString(R.string.ui_saved_6f6311)+intent.getIntExtra("count",0)+getString(R.string.ui_sessions_eb8c2d));
      boolean reminderActive=intent.getBooleanExtra("reminderActive",false);
      int reminderMinutes=intent.getIntExtra("reminderMinutes",5);
      String reminderLast=intent.getStringExtra("reminderLast");
      String voiceStatus=intent.getStringExtra("ttsStatus");
      if(voiceStatus!=null)ttsStatusView.setText(getString(R.string.ui_voice)+voiceStatus);
      int remaining=intent.getIntExtra("reminderSecondsToNext",-1);
      if(homeReminder!=null){
        homeReminder.setText(reminderCheck.isChecked()?
          getString(R.string.home_reminder_every)+reminderMinutes+
            getString(R.string.home_minute_suffix):
          getString(R.string.home_reminder_off));
      }
      if(reminderStatus!=null){
        if(reminderActive){
          reminderStatus.setText(getString(R.string.ui_reminder_running_every)+reminderMinutes+getString(R.string.ui_minutes_next_in)+
            remaining/60+getString(R.string.ui_min)+remaining%60+getString(R.string.ui_sec)+
            (reminderLast==null?getString(R.string.ui_waiting):reminderLast));
        }else reminderStatus.setText(reminderCheck.isChecked()?
          getString(R.string.ui_enabled_every)+reminderMinutes+getString(R.string.ui_minutes_6c3efb):getString(R.string.ui_reminders_off));
      }
      float meters=intent.getFloatExtra("gpsMeters",0),acc=intent.getFloatExtra("gpsAccuracy",-1);
      float gpsSpeed=intent.getFloatExtra("gpsSpeed",-1);
      int paceSec=gpsSpeed>=0.6f?Math.round(1000f/gpsSpeed):0;
      String pace=paceSec>=120&&paceSec<=1800?
        String.format(Locale.getDefault(),"%d′%02d″/km",paceSec/60,paceSec%60):"--";
      int steps=intent.getIntExtra("steps",0),cadence=intent.getIntExtra("cadence",-1);
      float stride=intent.getFloatExtra("stride",-1);
      String gpsState=intent.getStringExtra("gpsStatus");
      String stepState=intent.getStringExtra("stepStatus");
      String summary=String.format(Locale.getDefault(),
        getString(R.string.ui_gps_s_distance_2f_km_pace_s_s_nsteps_s_d_steps),
        gpsState==null?getString(R.string.ui_off):gpsState,meters/1000f,pace,
        acc>0?String.format(Locale.getDefault(),getString(R.string.ui_accuracy_0f_m),acc):"",
        stepState==null?getString(R.string.ui_off):stepState,steps,
        cadence>0?cadence+getString(R.string.ui_spm):"--",
        stride>0?String.format(Locale.getDefault(),getString(R.string.ui_text_2f_m_step),stride):"--");
      motionInfo.setText(summary);
      String savedFile=intent.getStringExtra("savedFile");
      if(savedFile!=null){
        if(intent.getBooleanExtra("saveOk",false)){
          selected=savedFile;
          latestKnown=savedFile;
          toast(getString(R.string.ui_session_saved_fit_export_is_available));
        }else toast(getString(R.string.ui_save_failed_check_session_data));
        refresh();
      }else if(previouslyRecording&&!recording){
        refresh();
      }
      // Do not recreate every file button on each BLE heart-rate notification;
      // this previously interrupted touches and could make selection unreliable.
      updateButtons();
    }
  };
  private void updateButtons(){
    if(stopButton!=null){
      stopButton.setEnabled(recording);
      stopButton.setVisibility(recording?View.VISIBLE:View.GONE);
    }
    if(probeStartButton!=null)probeStartButton.setEnabled(probeReady&&!probeActive);
    if(probeStopButton!=null)probeStopButton.setEnabled(probeActive);
    if(probeExportButton!=null)probeExportButton.setEnabled(!probeActive&&probeFileName!=null);
    if(startButton!=null){
      startButton.setEnabled(connected&&!recording&&!probeActive);
      startButton.setVisibility(recording?View.GONE:View.VISIBLE);
    }
    if(homeDeviceButton!=null)homeDeviceButton.setVisibility(connected?View.GONE:View.VISIBLE);
    if(batteryRefreshButton!=null)batteryRefreshButton.setEnabled(batteryReadable);
    if(diagnosticsButton!=null)diagnosticsButton.setEnabled(gattDiagnostics.startsWith(getString(R.string.ui_device)));
    if(fitButton!=null)fitButton.setEnabled(selected!=null&&!recording);
    if(csvButton!=null)csvButton.setEnabled(selected!=null&&!recording);
  }
  private void refresh(){
    File dir=new File(getFilesDir(),"sessions");
    File[] files=dir.listFiles((d,n)->n.toLowerCase(Locale.ROOT).endsWith(".csv"));
    sessionIndex.clear();
    if(files==null||files.length==0){
      selected=null;latestKnown=null;
      label.setText(R.string.ui_no_heart_rate_recordings);
      updateMonthOptions();renderHistory();updateButtons();return;
    }
    Arrays.sort(files,(a,b)->b.getName().compareTo(a.getName()));
    String newest=files[0].getName();
    if(!newest.equals(latestKnown)){
      selected=newest;latestKnown=newest;
    }else if(selected==null||!new File(dir,selected).isFile())selected=newest;
    TimeZone tz=TimeZone.getDefault();
    for(File file:files){
      String filename=file.getName();
      Date began=SessionHistory.startedAt(filename);
      long time=began==null?file.lastModified():began.getTime();
      String fallback=SessionHistory.localLabel(filename,tz);
      sessionIndex.add(new SessionCatalog.Entry(filename,
        sessionAnnotations.displayName(filename,fallback),sessionAnnotations.note(filename),time));
    }
    updateMonthOptions();renderHistory();updateButtons();
  }
  private void updateMonthOptions(){
    if(historyMonths==null)return;
    String keep=selectedMonth;
    TreeSet<String> months=new TreeSet<>(Collections.reverseOrder());
    for(SessionCatalog.Entry entry:sessionIndex)
      months.add(SessionCatalog.month(entry.startMillis,TimeZone.getDefault()));
    List<String> options=new ArrayList<>();
    options.add(getString(R.string.history_all_months));
    options.addAll(months);
    historyMonths.setAdapter(new ArrayAdapter<>(this,
      android.R.layout.simple_spinner_dropdown_item,options));
    int idx=keep.isEmpty()?0:options.indexOf(keep);
    if(idx<0)idx=0;
    selectedMonth=idx==0?"":options.get(idx);
    historyMonths.setSelection(idx);
  }
  private void renderHistory(){
    if(historyList==null||label==null||sessionAnnotations==null)return;
    int previousY=contentScroll.getScrollY();
    historyList.removeAllViews();
    List<SessionCatalog.Entry> entries=SessionCatalog.filter(sessionIndex,
      historySearch==null?"":historySearch.getText().toString(),selectedMonth,
      historyNotesOnly!=null&&historyNotesOnly.isChecked(),historySortMode,
      TimeZone.getDefault());
    String chosen=selected==null?getString(R.string.ui_no_recordings_yet):
      sessionAnnotations.displayName(selected,SessionHistory.localLabel(selected,TimeZone.getDefault()));
    label.setText(getString(R.string.history_selected_summary,chosen,entries.size(),sessionIndex.size()));
    if(entries.isEmpty()){
      add(historyList,text(getString(R.string.history_no_matches),14,false),8);
      return;
    }
    String priorMonth="";
    int count=Math.min(historyVisibleCount,entries.size());
    for(int i=0;i<count;i++){
      SessionCatalog.Entry entry=entries.get(i);
      String month=SessionCatalog.month(entry.startMillis,TimeZone.getDefault());
      if(historySortMode!=SessionCatalog.NAME&&!month.equals(priorMonth)){
        TextView heading=text(getString(R.string.history_group_month,month),15,true);
        heading.setPadding(dp(5),dp(12),0,dp(6));
        add(historyList,heading,3);
        priorMonth=month;
      }
      LinearLayout line=new LinearLayout(this);
      line.setOrientation(LinearLayout.HORIZONTAL);
      line.setGravity(Gravity.CENTER_VERTICAL);
      Button choose=btn((entry.filename.equals(selected)?"✓  ":"")+entry.title+"  · "+
        new java.text.SimpleDateFormat("MM-dd HH:mm",Locale.getDefault()).format(new Date(entry.startMillis))+
        (entry.note.isEmpty()?"":"  📝"));
      choose.setTextSize(12);
      choose.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);
      choose.setMaxLines(2);
      choose.setEllipsize(TextUtils.TruncateAt.END);
      if(entry.filename.equals(selected)){
        choose.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFD8EFEA));
      }
      line.addView(choose,new LinearLayout.LayoutParams(0,dp(66),1));
      choose.setOnClickListener(v->{selected=entry.filename;renderHistory();updateButtons();});
      Button edit=btn(getString(R.string.history_edit));
      edit.setTextSize(12);edit.setMinWidth(0);
      line.addView(edit,new LinearLayout.LayoutParams(dp(72),dp(66)));
      edit.setOnClickListener(v->editSession(entry));
      add(historyList,line,4);
      if(!entry.note.isEmpty()){
        TextView note=text(entry.note,12,false);
        note.setPadding(dp(8),0,dp(8),0);note.setMaxLines(2);
        note.setEllipsize(TextUtils.TruncateAt.END);
        add(historyList,note,7);
      }
    }
    if(entries.size()>count){
      Button more=btn(getString(R.string.history_load_more,entries.size()-count));
      add(historyList,more,8);
      more.setOnClickListener(v->{historyVisibleCount+=60;renderHistory();});
    }
    if(selectedTab==1)contentScroll.post(()->contentScroll.scrollTo(0,previousY));
  }
  private void editSession(SessionCatalog.Entry entry){
    LinearLayout editor=col();editor.setPadding(dp(20),dp(8),dp(20),0);
    add(editor,text(getString(R.string.history_name),13,true),2);
    EditText title=new EditText(this);
    title.setSingleLine(true);title.setMaxLines(1);
    title.setFilters(new InputFilter[]{new InputFilter.LengthFilter(80)});
    title.setHint(entry.filename);
    title.setText(sessionAnnotations.name(entry.filename));
    add(editor,title,8);
    add(editor,text(getString(R.string.history_note),13,true),2);
    EditText note=new EditText(this);
    note.setInputType(android.text.InputType.TYPE_CLASS_TEXT|
      android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|
      android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
    note.setMinLines(3);note.setMaxLines(5);
    note.setFilters(new InputFilter[]{new InputFilter.LengthFilter(600)});
    note.setHint(R.string.history_note_hint);
    note.setText(entry.note);
    add(editor,note,4);
    new AlertDialog.Builder(this).setTitle(R.string.history_edit_title)
      .setView(editor)
      .setNegativeButton(android.R.string.cancel,null)
      .setPositiveButton(R.string.history_save,(dialog,which)->{
        if(!sessionAnnotations.save(entry.filename,title.getText().toString(),note.getText().toString())){
          toast(getString(R.string.history_save_failed));return;
        }
        refresh();
      }).show();
  }
  private void exportProbe(){
    if(probeActive){toast(getString(R.string.ui_wait_for_diagnostics_to_finish));return;}
    if(probeFileName==null||!probeFileName.matches("ble_probe_\\d{8}_\\d{6}_\\d{3}\\.csv")){
      toast(getString(R.string.ui_no_diagnostics_csv_to_export));return;
    }
    File source=new File(new File(getFilesDir(),"ble_diagnostics"),probeFileName);
    if(!source.isFile()){toast(getString(R.string.ui_diagnostics_file_missing_run_again));return;}
    pendingProbeFile=source;
    Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
    intent.addCategory(Intent.CATEGORY_OPENABLE);
    intent.setType("text/csv");
    intent.putExtra(Intent.EXTRA_TITLE,probeFileName);
    startActivityForResult(intent,SAVE_PROBE_REQUEST);
  }
  private void export(boolean fit){
    if(selected==null||recording){toast(getString(R.string.ui_stop_recording_first));return;}
    File src=new File(new File(getFilesDir(),"sessions"),selected);
    try{
      if(fit){
        File dir=new File(getCacheDir(),"fit_exports");dir.mkdirs();
        pendingFile=new File(dir,SessionHistory.localFitFilename(selected,TimeZone.getDefault()));
        int total=FitExporter.export(src,pendingFile);
        toast(getString(R.string.ui_fit_encoded)+total+getString(R.string.ui_sessions_eb8c2d));
      }else pendingFile=src;
      Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
      intent.addCategory(Intent.CATEGORY_OPENABLE);
      intent.setType(fit?"application/vnd.ant.fit":"text/csv");
      intent.putExtra(Intent.EXTRA_TITLE,pendingFile.getName());
      startActivityForResult(intent,SAVE_REQUEST);
    }catch(Exception e){toast(getString(R.string.ui_export_error)+e.getMessage());}
  }
  @Override protected void onActivityResult(int req,int result,Intent data){
    super.onActivityResult(req,result,data);
    if((req!=SAVE_REQUEST&&req!=SAVE_PROBE_REQUEST)||result!=RESULT_OK||data==null)return;
    File source=req==SAVE_PROBE_REQUEST?pendingProbeFile:pendingFile;
    if(source==null)return;
    try(InputStream in=new FileInputStream(source);
        OutputStream out=getContentResolver().openOutputStream(data.getData())){
      if(out==null)throw new IOException(getString(R.string.ui_cannot_open_destination_file));
      byte[] buf=new byte[8192];int count;
      while((count=in.read(buf))!=-1)out.write(buf,0,count);
      toast(getString(R.string.ui_export_successful));
    }catch(Exception e){toast(getString(R.string.ui_save_failed)+e.getMessage());}
  }
  private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
  @Override protected void onResume(){
    super.onResume();
    IntentFilter filter=new IntentFilter(HrService.EVENT);
    if(Build.VERSION.SDK_INT>=33)registerReceiver(receiver,filter,Context.RECEIVER_NOT_EXPORTED);
    else registerReceiver(receiver,filter);
    command(HrService.STATUS,null,null);
    refresh();
  }
  @Override protected void onPause(){
    super.onPause();unregisterReceiver(receiver);stopScan();
  }
}
