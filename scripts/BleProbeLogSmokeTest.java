package fun.aniss.coroshr;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;

/** Standalone JVM test of the raw capture CSV and all three notification UUID routes. */
public final class BleProbeLogSmokeTest {
  static void check(boolean ok,String what){if(!ok)throw new AssertionError(what);}
  public static void main(String[] args)throws Exception{
    File root=Files.createTempDirectory("ble-probe-log-").toFile();
    try{
      BleProbeLog log=new BleProbeLog(root,1792400000000L);
      long timestamp=1792400000000L;
      log.event(timestamp,123,"START_passive_notify_only");
      for(int i=0;i<3;i++){
        check(BleProbeLog.channel(UUID.fromString(BleProbeLog.SERVICES[i]),
              UUID.fromString(BleProbeLog.NOTIFY[i]))==i,"private channel "+i);
        log.notification(timestamp+i,1000+i,UUID.fromString(BleProbeLog.SERVICES[i]),
            UUID.fromString(BleProbeLog.NOTIFY[i]),new byte[]{0x00,(byte)0xff,(byte)i});
      }
      log.notification(timestamp+4,1004,UUID.fromString(BleProbeLog.HR_SERVICE),
          UUID.fromString(BleProbeLog.HR_NOTIFY),new byte[]{0x00,0x42});
      check(BleProbeLog.channel(UUID.randomUUID(),UUID.fromString(BleProbeLog.NOTIFY[0]))==-1,
          "must match service and characteristic, not characteristic alone");
      check(log.total()==4,"total count");
      int[] counts=log.counts();
      for(int v:counts)check(v==1,"channel count");
      log.event(timestamp+60000,99,"END_60seconds");
      File file=log.file();
      log.close();log.close();
      List<String> lines=Files.readAllLines(file.toPath(),StandardCharsets.UTF_8);
      check(lines.size()==7,"CSV line count");
      check(lines.get(0).contains("utc_epoch_ms")&&lines.get(0).contains("payload_hex"),"CSV header");
      check(lines.stream().anyMatch(line->line.contains("00FF02")),"original bytes preserved");
      check(lines.stream().anyMatch(line->line.contains("00002a37")),"standard HR retained");
      check(lines.get(lines.size()-1).contains("END_60seconds"),"end event");
      System.out.println("BLE DIAG PASS: 3 private services + standard HR, CSV and raw payload");
    }finally{
      File[] files=root.listFiles();
      if(files!=null)for(File f:files)f.delete();
      root.delete();
    }
  }
}
