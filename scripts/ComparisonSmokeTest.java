package fun.aniss.coroshr;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** No Android runtime or extra dependencies; validates FIT read and time alignment. */
public final class ComparisonSmokeTest {
  private static void check(boolean condition,String reason){
    if(!condition)throw new AssertionError(reason);
  }
  private static File csv(File folder,String name,long start,int[] hr) throws Exception {
    File out=new File(folder,name+".csv");
    try(Writer w=new OutputStreamWriter(new FileOutputStream(out),StandardCharsets.UTF_8)){
      w.write("utc_epoch_ms,elapsed_realtime_ns,bpm,payload_hex\n");
      for(int i=0;i<hr.length;i++)w.write((start+i*1000)+","+(i*1000000000L)+","+hr[i]+",00\n");
    }
    return out;
  }
  public static void main(String[] args)throws Exception{
    File temp=new File(System.getProperty("java.io.tmpdir"),"coros-chart-tests-"+System.nanoTime());
    check(temp.mkdirs(),"Cannot make temp dir");
    try{
      Calendar current=Calendar.getInstance();
      current.set(2026,Calendar.OCTOBER,13,7,0,0);
      current.set(Calendar.MILLISECOND,0);
      long day1=current.getTimeInMillis();
      current.add(Calendar.DAY_OF_YEAR,-1);
      long day0=current.getTimeInMillis();
      File a=csv(temp,"today",day1,new int[]{120,130,140});
      File b=csv(temp,"yesterday",day0,new int[]{121,131,141,151,161});
      File first=new File(temp,"today.fit");
      File second=new File(temp,"yesterday.fit");
      check(FitExporter.export(a,first)==3,"first FIT sample count");
      check(FitExporter.export(b,second)==5,"second FIT sample count");
      HrSeries today=HrSeries.load(first,"today",true);
      HrSeries previous=HrSeries.load(second,"yesterday",true);
      check(today.heartRate.length==3&&previous.heartRate.length==5,"FIT decode count");
      check(today.start==day1&&previous.start==day0,"absolute timestamps");
      check(today.firstX(HrSeries.ABSOLUTE)>previous.lastX(HrSeries.ABSOLUTE),"different days have no absolute overlap");
      check(today.firstX(HrSeries.CLOCK)==previous.firstX(HrSeries.CLOCK),"same local clock overlays");
      check(today.firstX(HrSeries.ELAPSED)==0&&previous.firstX(HrSeries.ELAPSED)==0,"elapsed start alignment");
      long x=today.firstX(HrSeries.CLOCK)+1000;
      check(today.nearestBpm(x,HrSeries.CLOCK,200)==130,"today clock lookup");
      check(previous.nearestBpm(x,HrSeries.CLOCK,200)==131,"yesterday clock lookup");
      check(today.nearestBpm(today.firstX(HrSeries.ELAPSED)+4000,HrSeries.ELAPSED,250)==-1,"shorter file must not fill missing");
      check(previous.nearestBpm(previous.firstX(HrSeries.ELAPSED)+4000,HrSeries.ELAPSED,250)==161,"longer file has real tail");
      check(HrSeries.load(a,"raw",false).heartRate.length==3,"raw CSV load");
      System.out.println("PASS FIT decode, 3/5 unequal lengths, UTC/clock/elapsed alignment, missing-gap behavior");
    }finally{
      File[] children=temp.listFiles();if(children!=null)for(File f:children)f.delete();
      temp.delete();
    }
  }
}
