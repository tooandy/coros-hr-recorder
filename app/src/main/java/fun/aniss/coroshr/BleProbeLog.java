package fun.aniss.coroshr;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;

/** Raw BLE diagnostic capture; never decodes or sends proprietary commands. */
final class BleProbeLog implements Closeable {
  static final String[] NAMES={"Weloop UART","Nordic UART","COROS private"};
  static final String[] SERVICES={
    "6e400001-b5a3-f393-e0a9-77656c6f6f70",
    "6e400001-b5a3-f393-e0a9-e50e24dcca9e",
    "75f50001-e97c-43f9-9a1d-bd83438f6b99"
  };
  static final String[] NOTIFY={
    "6e400003-b5a3-f393-e0a9-77656c6f6f70",
    "6e400003-b5a3-f393-e0a9-e50e24dcca9e",
    "75f50002-e97c-43f9-9a1d-bd83438f6b99"
  };
  static final String HR_SERVICE="0000180d-0000-1000-8000-00805f9b34fb";
  static final String HR_NOTIFY="00002a37-0000-1000-8000-00805f9b34fb";
  private final File file;
  private final BufferedWriter output;
  private final int[] counts=new int[4];
  private boolean closed;
  private int pendingWrites;
  private long lastFlushTime;

  BleProbeLog(File dir,long startedMillis) throws IOException {
    if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("无法创建 BLE 诊断目录");
    SimpleDateFormat sdf=new SimpleDateFormat("yyyyMMdd_HHmmss_SSS",Locale.US);
    sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
    file=new File(dir,"ble_probe_"+sdf.format(new Date(startedMillis))+".csv");
    output=new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file),StandardCharsets.UTF_8),32768);
    output.write("utc_epoch_ms,elapsed_realtime_ns,type,service_uuid,characteristic_uuid,length,payload_hex,note\n");
    output.flush();
  }
  File file(){return file;}
  static int channel(UUID service,UUID characteristic){
    for(int i=0;i<SERVICES.length;i++)
      if(SERVICES[i].equalsIgnoreCase(service.toString())&&NOTIFY[i].equalsIgnoreCase(characteristic.toString()))
        return i;
    if(HR_SERVICE.equalsIgnoreCase(service.toString())&&HR_NOTIFY.equalsIgnoreCase(characteristic.toString()))
      return 3;
    return -1;
  }
  static String hex(byte[] payload){
    if(payload==null)return "";
    final char[] abc="0123456789ABCDEF".toCharArray();
    char[] chars=new char[payload.length*2];
    for(int i=0;i<payload.length;i++){
      chars[2*i]=abc[(payload[i]>>>4)&15];
      chars[2*i+1]=abc[payload[i]&15];
    }
    return new String(chars);
  }
  private static String safe(String value){
    return value==null?"":value.replace('\n',' ').replace('\r',' ').replace(',',';');
  }
  private void append(long now,long elapsedNanos,String type,String svc,String chr,byte[] payload,String note)
      throws IOException {
    if(closed)return;
    String record=now+","+elapsedNanos+","+type+","+safe(svc)+","+safe(chr)+","+
      (payload==null?0:payload.length)+","+hex(payload)+","+safe(note)+"\n";
    output.write(record);
    if(++pendingWrites>=30||now-lastFlushTime>=1000){
      output.flush();
      pendingWrites=0;
      lastFlushTime=now;
    }
  }
  synchronized void event(long now,long elapsedNanos,String note)throws IOException{
    append(now,elapsedNanos,"event","","",null,note);
  }
  synchronized void notification(long now,long elapsedNanos,UUID service,UUID chr,byte[] payload)
      throws IOException {
    int idx=channel(service,chr);
    if(idx<0||closed)return;
    append(now,elapsedNanos,"notification",service.toString(),chr.toString(),payload,"");
    counts[idx]++;
  }
  synchronized int[] counts(){return counts.clone();}
  synchronized int total(){return counts[0]+counts[1]+counts[2]+counts[3];}
  @Override public synchronized void close()throws IOException{
    if(closed)return;
    closed=true;output.close();
  }
}
