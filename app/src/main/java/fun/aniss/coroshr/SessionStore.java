package fun.aniss.coroshr;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

final class SessionStore {
  private final File file;
  private final BufferedWriter output;
  private int count;
  SessionStore(File dir)throws IOException{
    if(!dir.exists()&&!dir.mkdirs())throw new IOException("记录目录不可写");
    SimpleDateFormat sdf=new SimpleDateFormat("yyyyMMdd_HHmmss_SSS",Locale.US);
    sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
    file=new File(dir,"hr_"+sdf.format(new Date())+".csv");
    output=new BufferedWriter(new OutputStreamWriter(
        new FileOutputStream(file),StandardCharsets.UTF_8));
    output.write("utc_epoch_ms,elapsed_realtime_ns,bpm,payload_hex\n");
    output.flush();
  }
  synchronized int write(long epoch,long monotonic,int bpm,byte[] raw)throws IOException{
    StringBuilder hex=new StringBuilder();
    for(byte b:raw)hex.append(String.format(Locale.US,"%02X",b&255));
    output.write(epoch+","+monotonic+","+bpm+","+hex+"\n");
    output.flush();
    return ++count;
  }
  synchronized void close()throws IOException{output.close();}
  File file(){return file;}
}
