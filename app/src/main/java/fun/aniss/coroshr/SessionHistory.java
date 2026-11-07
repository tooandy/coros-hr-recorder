package fun.aniss.coroshr;

import java.text.SimpleDateFormat;
import java.text.ParsePosition;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Converts UTC-based session filenames into the phone's local date/time. */
final class SessionHistory {
  private SessionHistory() {}
  private static final String PREFIX="hr_";
  private static final String SUFFIX=".csv";
  private static final String PATTERN="yyyyMMdd_HHmmss_SSS";

  static Date startedAt(String filename){
    if(filename==null||!filename.startsWith(PREFIX)||!filename.endsWith(SUFFIX))return null;
    String value=filename.substring(PREFIX.length(),filename.length()-SUFFIX.length());
    SimpleDateFormat format=new SimpleDateFormat(PATTERN,Locale.US);
    format.setTimeZone(TimeZone.getTimeZone("UTC"));
    format.setLenient(false);
    ParsePosition pos=new ParsePosition(0);
    Date date=format.parse(value,pos);
    return date!=null&&pos.getIndex()==value.length()?date:null;
  }

  static String localFitFilename(String filename,TimeZone tz){
    Date started=startedAt(filename);
    if(started==null)return filename.replaceFirst("(?i)\\.csv$",".fit");
    SimpleDateFormat local=new SimpleDateFormat("yyyyMMdd_HHmmss_SSS",Locale.US);
    local.setTimeZone(tz);
    return "hr_"+local.format(started)+".fit";
  }

  static String localLabel(String filename,TimeZone tz){
    Date started=startedAt(filename);
    if(started==null)return "时间未知";
    SimpleDateFormat view=new SimpleDateFormat("MM-dd HH:mm:ss",Locale.getDefault());
    view.setTimeZone(tz);
    return view.format(started);
  }
}
