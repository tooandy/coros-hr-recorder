package fun.aniss.coroshr;

import com.garmin.fit.Decode;
import com.garmin.fit.MesgNum;
import com.garmin.fit.RecordMesg;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

/** An immutable in-memory HR series with original UTC timestamps in milliseconds. */
public final class HrSeries {
  public static final int ABSOLUTE=0, ELAPSED=1, CLOCK=2;
  private static final int MAX_RECORDS=300000;
  public final String name;
  public final File file;
  public final boolean imported;
  public final long[] time;
  public final int[] heartRate;
  public final long start,end,midnight;
  private HrSeries(String name,File file,boolean imported,List<long[]> rows) throws IOException {
    if(rows.isEmpty())throw new IOException("文件没有带时间戳的心率采样");
    rows.sort(Comparator.comparingLong(a->a[0]));
    this.name=name;this.file=file;this.imported=imported;
    this.time=new long[rows.size()];this.heartRate=new int[rows.size()];
    for(int i=0;i<rows.size();i++){time[i]=rows.get(i)[0];heartRate[i]=(int)rows.get(i)[1];}
    start=time[0];end=time[time.length-1];
    Calendar cal=Calendar.getInstance();
    cal.setTimeInMillis(start);
    cal.set(Calendar.HOUR_OF_DAY,0);cal.set(Calendar.MINUTE,0);
    cal.set(Calendar.SECOND,0);cal.set(Calendar.MILLISECOND,0);
    midnight=cal.getTimeInMillis();
  }
  public static HrSeries load(File file,String name,boolean imported) throws IOException {
    if(!file.isFile())throw new IOException("文件不存在");
    ArrayList<long[]> rows=new ArrayList<>();
    if(file.getName().toLowerCase(Locale.ROOT).endsWith(".csv")){
      try(BufferedReader reader=new BufferedReader(new InputStreamReader(new FileInputStream(file),StandardCharsets.UTF_8))){
        String line=reader.readLine();
        while((line=reader.readLine())!=null){
          String[] v=line.split(",",4);
          if(v.length<3)continue;
          try{
            long ms=Long.parseLong(v[0].trim());
            int hr=Integer.parseInt(v[2].trim());
            if(ms>0&&hr>0&&hr<=255)rows.add(new long[]{ms,hr});
          }catch(NumberFormatException ignored){}
          if(rows.size()>MAX_RECORDS)throw new IOException("心率采样超过限制 "+MAX_RECORDS);
        }
      }
    }else if(file.getName().toLowerCase(Locale.ROOT).endsWith(".fit")){
      Decode decoder=new Decode();
      try(InputStream input=new BufferedInputStream(new FileInputStream(file))){
        boolean ok=decoder.read(input,mesg->{
          if(mesg.getNum()!=MesgNum.RECORD)return;
          RecordMesg record=new RecordMesg(mesg);
          if(record.getTimestamp()==null||record.getHeartRate()==null)return;
          int hr=record.getHeartRate()&0xffff;
          if(hr<=0||hr>255)return;
          rows.add(new long[]{record.getTimestamp().getDate().getTime(),hr});
          if(rows.size()>MAX_RECORDS)throw new IllegalStateException("心率数据过多");
        });
        if(!ok)throw new IOException("FIT 内容校验失败或文件不完整");
      }catch(RuntimeException ex){throw new IOException("FIT 解码失败："+ex.getMessage(),ex);}
    }else throw new IOException("目前支持 .fit 和 .csv");
    return new HrSeries(name,file,imported,rows);
  }
  public long baseline(int mode){
    return mode==ELAPSED?start:mode==CLOCK?midnight:0;
  }
  public long firstX(int mode){return start-baseline(mode);}
  public long lastX(int mode){return end-baseline(mode);}
  public long sampleX(int index,int mode){return time[index]-baseline(mode);}
  /** No fake interpolation across missing data: nearest sample only if within tolerance. */
  public int nearestBpm(long x,int mode,long toleranceMs){
    long target=x+baseline(mode);
    int idx=Arrays.binarySearch(time,target);
    if(idx<0)idx=-idx-1;
    int nearest=-1;
    if(idx<time.length)nearest=idx;
    if(idx>0&&(nearest<0||Math.abs(time[idx-1]-target)<=Math.abs(time[nearest]-target)))nearest=idx-1;
    return nearest>=0&&Math.abs(time[nearest]-target)<=toleranceMs?heartRate[nearest]:-1;
  }
  public String timeLabel(){
    return new SimpleDateFormat("MM-dd HH:mm",Locale.getDefault()).format(new Date(start));
  }
}
