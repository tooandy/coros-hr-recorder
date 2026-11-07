package fun.aniss.coroshr;

import java.util.Date;
import java.util.TimeZone;

public final class SessionHistorySmokeTest {
  static void assertEqual(String expected,String actual){
    if(!expected.equals(actual))throw new AssertionError(expected+" != "+actual);
  }
  public static void main(String[] args){
    String filename="hr_20261016_211140_585.csv";
    assertEqual("10-17 05:11:40",SessionHistory.localLabel(filename,TimeZone.getTimeZone("Asia/Shanghai")));
    assertEqual("hr_20261017_051140_585.fit",SessionHistory.localFitFilename(filename,TimeZone.getTimeZone("Asia/Shanghai")));
    assertEqual("hr_20261016_211140_585.fit",SessionHistory.localFitFilename(filename,TimeZone.getTimeZone("UTC")));
    assertEqual("10-16 21:11:40",SessionHistory.localLabel(filename,TimeZone.getTimeZone("UTC")));
    assertEqual("10-16 17:11:40",SessionHistory.localLabel(filename,TimeZone.getTimeZone("America/New_York")));
    if(SessionHistory.startedAt(filename)==null)throw new AssertionError("valid filename not parsed");
    if(SessionHistory.startedAt("hr_20261099_211140_585.csv")!=null)throw new AssertionError("invalid date accepted");
    if(SessionHistory.startedAt("hr_20261016_211140_585.csv.bak")!=null)throw new AssertionError("invalid suffix");
    assertEqual("时间未知",SessionHistory.localLabel("unknown.csv",TimeZone.getTimeZone("UTC")));
    System.out.println("SESSION_HISTORY PASS: UTC filename -> China, UTC, US local time; invalid input rejected");
  }
}
