package fun.aniss.coroshr;

import android.app.*;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import android.text.*;
import android.text.style.ForegroundColorSpan;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

/** Landscape FIT/CSV history viewer. Native, offline, no cloud or account required. */
public final class CompareActivity extends Activity {
  private static final int PICK_FILES=502, SAVE_COMPARISON=503;
  private static final long MAX_IMPORT_BYTES=32L*1024*1024;
  private static final int MAX_VISIBLE=8;
  private final ExecutorService executor=Executors.newSingleThreadExecutor();
  private final List<HrSeries> all=new ArrayList<>();
  private final LinkedHashSet<String> checked=new LinkedHashSet<>();
  private final SimpleDateFormat dateFmt=new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.getDefault());
  private final int INK=0xFF142C36,TEAL=0xFF007E78;
  private HrChartView chart;
  private LinearLayout list,topBar;
  private TextView status,readout;
  private EditText search;
  private CheckBox chosenOnly;
  private Spinner alignment,dateFilter;
  private boolean loading,initialLoad=true;
  private String filter="";
  private SessionAnnotations annotations;
  private String focusFilename;
  private int mode=HrSeries.ABSOLUTE;
  private int dateFilterIndex;
  private boolean useIntersection=true;

  private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
  private TextView tv(String text,int size,boolean bold){
    TextView t=new TextView(this);t.setText(text);t.setTextColor(INK);t.setTextSize(size);
    if(bold)t.setTypeface(null,Typeface.BOLD);return t;
  }
  private Button action(String text,Runnable r){
    Button b=new Button(this);b.setAllCaps(false);b.setTextSize(12);b.setText(text);
    b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(0);b.setMinimumHeight(0);
    b.setPadding(dp(9),dp(3),dp(9),dp(3));b.setOnClickListener(v->r.run());return b;
  }
  private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(0);return l;}
  private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
  @Override public void onCreate(Bundle b){
    super.onCreate(b);
    annotations=new SessionAnnotations(this);
    focusFilename=getIntent().getStringExtra("focusFilename");
    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
    buildUi();
    loadFiles(false);
  }
  private void buildUi(){
    LinearLayout root=column();root.setBackgroundColor(0xFFF3F6F8);setContentView(root);SafeArea.apply(this,root);
    HorizontalScrollView controls=new HorizontalScrollView(this);
    controls.setHorizontalScrollBarEnabled(false);
    topBar=row();topBar.setGravity(Gravity.CENTER_VERTICAL);
    topBar.setPadding(dp(8),dp(2),dp(8),dp(2));
    controls.addView(topBar);
    root.addView(controls,new LinearLayout.LayoutParams(-1,dp(49)));
    topBar.addView(action(getString(R.string.ui_back_to_sessions),this::finish));
    topBar.addView(action(getString(R.string.ui_import_fit_csv),this::openFiles));
    topBar.addView(action(getString(R.string.ui_refresh),()->loadFiles(false)));
    TextView modeLabel=tv(getString(R.string.ui_align),12,false);topBar.addView(modeLabel);
    alignment=new Spinner(this);
    String[] choices={getString(R.string.ui_absolute_time),getString(R.string.ui_align_from_start),getString(R.string.ui_same_time_of_day)};
    ArrayAdapter<String> modes=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,choices);
    alignment.setAdapter(modes);
    topBar.addView(alignment,new LinearLayout.LayoutParams(dp(136),dp(43)));
    alignment.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
      @Override public void onNothingSelected(android.widget.AdapterView<?> p){}
      @Override public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){
        mode=pos;rebuildChart();
      }
    });
    topBar.addView(action(getString(R.string.ui_overlapping_interval),()->{
      useIntersection=true;
      if(!chart.fit(true))toast(getString(R.string.ui_no_common_time_range_with_this_alignment));
      updateReadout();
    }));
    topBar.addView(action(getString(R.string.ui_entire_timeline),()->{
      useIntersection=false;chart.fit(false);updateReadout();
    }));
    topBar.addView(action(getString(R.string.ui_zoom_in),()->chart.zoom(1.8f,.5f)));
    topBar.addView(action(getString(R.string.ui_zoom_out),()->chart.zoom(.55f,.5f)));
    topBar.addView(action(getString(R.string.ui_export_comparison_csv),this::exportComparison));

    LinearLayout body=row();
    root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
    LinearLayout panel=column();
    panel.setPadding(dp(10),dp(5),dp(9),dp(6));
    panel.setBackgroundColor(0xFFFFFFFF);
    body.addView(panel,new LinearLayout.LayoutParams(dp(255),-1));
    TextView heading=tv(getString(R.string.ui_hr_sessions_up_to)+MAX_VISIBLE+getString(R.string.ui_sessions_eb8c2d),15,true);
    panel.addView(heading);
    search=new EditText(this);search.setSingleLine(true);search.setTextSize(13);
    search.setHint(getString(R.string.ui_search_date_or_file_name));
    panel.addView(search,new LinearLayout.LayoutParams(-1,dp(44)));
    search.addTextChangedListener(new TextWatcher(){
      public void beforeTextChanged(CharSequence s,int start,int count,int after){}
      public void onTextChanged(CharSequence s,int start,int before,int count){filter=s.toString().trim().toLowerCase(Locale.ROOT);renderFiles();}
      public void afterTextChanged(Editable e){}
    });
    LinearLayout filters=row();
    dateFilter=new Spinner(this);
    dateFilter.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,
        new String[]{getString(R.string.ui_all_dates),getString(R.string.ui_today),getString(R.string.ui_yesterday),getString(R.string.ui_last_7_days)}));
    filters.addView(dateFilter,new LinearLayout.LayoutParams(0,dp(38),1));
    dateFilter.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
      @Override public void onNothingSelected(android.widget.AdapterView<?> p){}
      @Override public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){
        dateFilterIndex=pos;renderFiles();
      }
    });
    chosenOnly=new CheckBox(this);chosenOnly.setTextSize(11);chosenOnly.setText(getString(R.string.ui_selected_f24ddc));
    chosenOnly.setOnCheckedChangeListener((b,checked)->renderFiles());
    filters.addView(chosenOnly);panel.addView(filters);
    LinearLayout actions=row();
    actions.addView(action(getString(R.string.ui_clear_all),()->{checked.clear();rebuildChart();renderFiles();}));
    actions.addView(action(getString(R.string.ui_select_latest_2),()->{
      checked.clear();
      for(int i=0;i<Math.min(2,all.size());i++)checked.add(all.get(i).file.getAbsolutePath());
      rebuildChart();renderFiles();
    }));
    panel.addView(actions);
    status=tv(getString(R.string.ui_reading_file),11,false);status.setTextColor(0xFF617481);
    panel.addView(status,new LinearLayout.LayoutParams(-1,dp(28)));
    ScrollView scroll=new ScrollView(this);
    list=column();scroll.addView(list);
    panel.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));

    LinearLayout graph=column();body.addView(graph,new LinearLayout.LayoutParams(0,-1,1));
    readout=tv(getString(R.string.ui_tap_chart_to_see_aligned_heart_rates_pinch_to),12,false);
    readout.setPadding(dp(12),dp(4),dp(8),dp(4));readout.setMaxLines(3);
    readout.setBackgroundColor(0xFFFFFFFF);
    graph.addView(readout,new LinearLayout.LayoutParams(-1,dp(69)));
    chart=new HrChartView(this);
    chart.setCursorListener(x->updateReadout(x));
    graph.addView(chart,new LinearLayout.LayoutParams(-1,0,1));
    TextView help=tv(getString(R.string.ui_tap_to_inspect_drag_to_scrub_pinch_to_zoom_swi),11,false);
    help.setTextColor(0xFF687987);help.setPadding(dp(9),0,dp(5),0);
    graph.addView(help,new LinearLayout.LayoutParams(-1,dp(21)));
  }
  private File importedDir(){return new File(getFilesDir(),"imported_fit");}
  private String title(File f){
    String name=f.getName();
    return name.replaceFirst("^im_[0-9]+_[a-z0-9]{8}_","");
  }
  private void loadFiles(boolean prioritizeNew){
    if(loading)return;
    loading=true;status.setText(getString(R.string.ui_loading_sessions));
    executor.execute(()->{
      List<HrSeries> loaded=new ArrayList<>();
      List<String> errors=new ArrayList<>();
      List<File> files=new ArrayList<>();
      File sessions=new File(getFilesDir(),"sessions");
      File[] locals=sessions.listFiles((d,n)->n.toLowerCase(Locale.ROOT).endsWith(".csv"));
      if(locals!=null)Collections.addAll(files,locals);
      File[] imports=importedDir().listFiles((d,n)->n.toLowerCase(Locale.ROOT).endsWith(".fit")||n.toLowerCase(Locale.ROOT).endsWith(".csv"));
      if(imports!=null)Collections.addAll(files,imports);
      files.sort((a,b)->Long.compare(b.lastModified(),a.lastModified()));
      // Ensure the historical session selected on the main History tab is
      // accessible even if older than the latest 100 imported sessions.
      if(focusFilename!=null){
        for(int i=0;i<files.size();i++)if(files.get(i).getName().equals(focusFilename)){
          File focused=files.remove(i);files.add(0,focused);break;
        }
      }
      for(File file:files){
        if(loaded.size()>=100){errors.add(getString(R.string.ui_more_than_100_files_showing_latest_100));break;}
        try{loaded.add(HrSeries.load(file,annotations.displayName(file.getName(),title(file)),file.getParentFile().equals(importedDir())));}
        catch(Exception e){errors.add(title(file)+": "+e.getMessage());}
      }
      loaded.sort((a,b)->Long.compare(b.start,a.start));
      runOnUiThread(()->{
        if(isFinishing()||isDestroyed())return;
        all.clear();all.addAll(loaded);
        checked.retainAll(fileKeys(loaded));
        if(initialLoad&&checked.isEmpty()&&!loaded.isEmpty()){
          HrSeries focus=null;
          for(HrSeries item:loaded){if(item.file.getName().equals(focusFilename)){focus=item;break;}}
          checked.add((focus==null?loaded.get(0):focus).file.getAbsolutePath());
        }
        initialLoad=false;loading=false;
        status.setText(all.size()+getString(R.string.ui_sessions_selected)+checked.size());
        renderFiles();rebuildChart();
        if(!errors.isEmpty())toast(getString(R.string.ui_found)+errors.size()+getString(R.string.ui_files_could_not_be_read)+errors.get(0));
      });
    });
  }
  private Set<String> fileKeys(List<HrSeries> rows){
    Set<String> keys=new HashSet<>();for(HrSeries s:rows)keys.add(s.file.getAbsolutePath());return keys;
  }
  private boolean passesDate(HrSeries s){
    if(dateFilterIndex==0)return true;
    Calendar start=Calendar.getInstance();start.set(Calendar.HOUR_OF_DAY,0);
    start.set(Calendar.MINUTE,0);start.set(Calendar.SECOND,0);start.set(Calendar.MILLISECOND,0);
    long today=start.getTimeInMillis();
    if(dateFilterIndex==1)return s.start>=today;
    if(dateFilterIndex==2)return s.start>=today-86400000L&&s.start<today;
    return s.start>=today-6*86400000L;
  }
  private void renderFiles(){
    if(list==null||status==null)return;
    list.removeAllViews();
    int visible=0;
    for(HrSeries s:all){
      String key=s.file.getAbsolutePath();
      if(chosenOnly!=null&&chosenOnly.isChecked()&&!checked.contains(key))continue;
      if(!passesDate(s))continue;
      if(!filter.isEmpty()&&!(s.name+" "+s.timeLabel()+" "+
          annotations.note(s.file.getName())).toLowerCase(Locale.ROOT).contains(filter))continue;
      visible++;
      LinearLayout line=column();line.setPadding(0,0,0,dp(7));
      CheckBox check=new CheckBox(this);
      check.setPadding(0,0,0,0);
      check.setText(s.timeLabel()+(s.imported?" · FIT":getString(R.string.ui_local)));
      check.setTextSize(12);check.setChecked(checked.contains(key));
      line.addView(check);
      TextView info=tv(s.name+"  ("+s.time.length+getString(R.string.ui_fixes_4c6783),11,false);
      info.setSingleLine(true);info.setEllipsize(TextUtils.TruncateAt.END);
      info.setPadding(dp(30),0,dp(2),0);line.addView(info);
      check.setOnCheckedChangeListener((v,isChecked)->{
        if(isChecked&&checked.size()>=MAX_VISIBLE){
          toast(getString(R.string.ui_maximum_overlays)+MAX_VISIBLE+getString(R.string.ui_sessions_search_to_filter_older_entries));
          check.setChecked(false);return;
        }
        if(isChecked)checked.add(key);else checked.remove(key);
        rebuildChart();
        status.setText(all.size()+getString(R.string.ui_sessions_selected)+checked.size());
      });
      if(s.imported){
        TextView remove=tv(getString(R.string.ui_delete_imported_copy),10,false);
        remove.setTextColor(0xFFB44C45);remove.setPadding(dp(30),0,0,0);
        remove.setOnClickListener(v->new AlertDialog.Builder(this)
          .setTitle(getString(R.string.ui_delete_this_imported_session))
          .setMessage(s.name+getString(R.string.ui_nonly_removes_the_app_copy_not_the_original_fi))
          .setNegativeButton(getString(R.string.ui_cancel),null)
          .setPositiveButton(getString(R.string.ui_delete),(dialog,w)->{
            if(!s.file.delete()){toast(getString(R.string.ui_delete_failed));return;}
            annotations.delete(s.file.getName());
            checked.remove(key);loadFiles(false);
          }).show());
        line.addView(remove);
      }
      list.addView(line);
    }
    status.setText(all.size()+getString(R.string.ui_sessions_showing)+visible+getString(R.string.ui_sessions_selected_d4364a)+checked.size());
  }
  private List<HrSeries> picked(){
    List<HrSeries> values=new ArrayList<>();
    for(HrSeries s:all)if(checked.contains(s.file.getAbsolutePath()))values.add(s);
    return values;
  }
  private void rebuildChart(){
    if(chart==null)return;
    List<HrSeries> values=picked();
    boolean ok=chartHasOverlap(values,mode);
    chart.setSeries(values,mode,useIntersection&&ok);
    updateReadout();
    if(useIntersection&&values.size()>1&&!ok)
      status.setText(getString(R.string.ui_no_overlap_in_this_alignment_showing_complete));
  }
  private boolean chartHasOverlap(List<HrSeries> values,int alignment){
    if(values.size()<2)return true;
    long left=Long.MIN_VALUE,right=Long.MAX_VALUE;
    for(HrSeries s:values){left=Math.max(left,s.firstX(alignment));right=Math.min(right,s.lastX(alignment));}
    return right>left;
  }
  private void updateReadout(){
    if(chart!=null)updateReadout((chart.visibleStart()+chart.visibleEnd())/2);
  }
  private void updateReadout(long x){
    if(readout==null||chart==null)return;
    List<HrSeries> series=chart.visibleSeries();
    if(series.isEmpty()){readout.setText(getString(R.string.ui_import_fit_or_select_recorded_sessions));return;}
    SpannableStringBuilder b=new SpannableStringBuilder();
    b.append(chart.displayX(x)+"  ·  ");
    int base=-1;
    for(int i=0;i<series.size();i++){
      HrSeries s=series.get(i);
      int value=s.nearestBpm(x,mode,3000);
      if(value>=0&&base<0)base=value;
      String shortName=s.name.length()>16?s.name.substring(0,16)+"…":s.name;
      String delta=value>=0&&base>=0&&i>0?" ("+(value-base>=0?"+":"")+(value-base)+")":"";
      String item=shortName+" "+(value>=0?value+" BPM"+delta:"--")+"  ";
      int pos=b.length();b.append(item);
      b.setSpan(new ForegroundColorSpan(HrChartView.COLORS[i%HrChartView.COLORS.length]),
        pos,b.length(),android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
    readout.setText(b);
  }
  private void openFiles(){
    Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
    intent.setType("*/*");intent.addCategory(Intent.CATEGORY_OPENABLE);
    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);
    // Do not MIME-filter: several Android document providers label .fit as
    // application/unknown; extension and decoder are validated after selection.
    startActivityForResult(intent,PICK_FILES);
  }
  @Override protected void onActivityResult(int request,int result,Intent data){
    super.onActivityResult(request,result,data);
    if(result!=RESULT_OK||data==null)return;
    if(request==PICK_FILES){
      ArrayList<Uri> targets=new ArrayList<>();
      if(data.getClipData()!=null){
        for(int i=0;i<data.getClipData().getItemCount();i++)
          targets.add(data.getClipData().getItemAt(i).getUri());
      }else if(data.getData()!=null)targets.add(data.getData());
      if(targets.size()>20){toast(getString(R.string.ui_maximum_20_files_per_import));return;}
      importUris(targets);
    }else if(request==SAVE_COMPARISON&&data.getData()!=null)
      saveComparison(data.getData());
  }
  private String documentName(Uri uri){
    String name=null;
    try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},
      null,null,null)){
      if(c!=null&&c.moveToFirst())name=c.getString(0);
    }catch(Exception ignored){}
    if(name==null||name.isEmpty())name="imported.fit";
    name=name.replaceAll("[^\\p{L}\\p{N}._-]","_");
    return name.length()>55?name.substring(name.length()-55):name;
  }
  private void importUris(List<Uri> targets){
    if(targets.isEmpty())return;
    status.setText(getString(R.string.ui_importing));
    executor.execute(()->{
      List<String> added=new ArrayList<>(),errors=new ArrayList<>();
      File dir=importedDir();dir.mkdirs();
      for(Uri uri:targets){
        String name=documentName(uri);
        if(!name.toLowerCase(Locale.ROOT).matches(".*\\.(fit|csv)")){
          errors.add(name+getString(R.string.ui_not_a_fit_csv_file));continue;
        }
        String filename="im_"+System.currentTimeMillis()+"_"+
          UUID.randomUUID().toString().substring(0,8)+"_"+name;
        File dst=new File(dir,filename);
        try(InputStream input=getContentResolver().openInputStream(uri);
          OutputStream output=new BufferedOutputStream(new FileOutputStream(dst))){
          if(input==null)throw new IOException(getString(R.string.ui_cannot_read_file));
          byte[] buffer=new byte[32768];int count;long total=0;
          while((count=input.read(buffer))>=0){
            total+=count;if(total>MAX_IMPORT_BYTES)throw new IOException(getString(R.string.ui_file_exceeds_32_mb_limit));
            output.write(buffer,0,count);
          }
          output.flush();
          HrSeries.load(dst,name,true);
          added.add(dst.getAbsolutePath());
        }catch(Exception e){dst.delete();errors.add(name+"："+e.getMessage());}
      }
      runOnUiThread(()->{
        checked.addAll(added);
        while(checked.size()>MAX_VISIBLE){String first=checked.iterator().next();checked.remove(first);}
        loading=false;
        loadFiles(false);
        if(!errors.isEmpty())new AlertDialog.Builder(this).setTitle(getString(R.string.ui_some_files_failed_to_import))
          .setMessage(android.text.TextUtils.join("\n",errors))
          .setPositiveButton(getString(R.string.ui_got_it),null).show();
        else toast(getString(R.string.ui_imported)+added.size()+getString(R.string.ui_files));
      });
    });
  }
  private void exportComparison(){
    if(picked().isEmpty()){toast(getString(R.string.ui_select_at_least_one_file));return;}
    long count=(chart.visibleEnd()-chart.visibleStart())/1000L;
    if(count>172800){toast(getString(R.string.ui_export_window_exceeds_48_hours_zoom_in_or_choo));return;}
    Intent save=new Intent(Intent.ACTION_CREATE_DOCUMENT);
    save.addCategory(Intent.CATEGORY_OPENABLE);save.setType("text/csv");
    save.putExtra(Intent.EXTRA_TITLE,"hr-comparison-"+System.currentTimeMillis()+".csv");
    startActivityForResult(save,SAVE_COMPARISON);
  }
  private void saveComparison(Uri uri){
    final List<HrSeries> values=picked();
    final long begin=chart.visibleStart(),end=chart.visibleEnd();
    final int currentMode=mode;
    executor.execute(()->{
      try(OutputStream out=getContentResolver().openOutputStream(uri)){
        if(out==null)throw new IOException(getString(R.string.ui_cannot_open_destination_file));
        BufferedWriter writer=new BufferedWriter(new OutputStreamWriter(out,StandardCharsets.UTF_8));
        writer.write("alignment_ms,label");
        for(HrSeries s:values)writer.write(","+csvCell(s.name));
        writer.write("\n");
        // Export aligned, once-per-second comparisons; missing data remains blank.
        for(long x=begin;x<=end;x+=1000){
          writer.write(Long.toString(x));writer.write(","+csvCell(axisLabel(x,currentMode)));
          for(HrSeries s:values){
            int bpm=s.nearestBpm(x,currentMode,3000);
            writer.write(",");if(bpm>=0)writer.write(Integer.toString(bpm));
          }
          writer.write("\n");
        }
        writer.flush();
        runOnUiThread(()->toast(getString(R.string.ui_comparison_csv_exported)));
      }catch(Exception e){runOnUiThread(()->toast(getString(R.string.ui_export_failed)+e.getMessage()));}
    });
  }
  private String axisLabel(long value,int alignment){
    if(alignment==HrSeries.ABSOLUTE)return dateFmt.format(new Date(value));
    long seconds=value/1000;
    return String.format(Locale.ROOT,"%02d:%02d:%02d",seconds/3600,seconds/60%60,seconds%60);
  }
  private String csvCell(String s){
    return "\""+s.replace("\"","\"\"")+"\"";
  }
  private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
  @Override protected void onDestroy(){executor.shutdownNow();super.onDestroy();}
}
