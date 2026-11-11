package fun.aniss.coroshr;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** Filter/sort pure-Java session index; designed for thousands of records. */
final class SessionCatalog {
  static final int NEWEST=0, OLDEST=1, NAME=2;
  static final class Entry {
    final String filename,title,note;
    final long startMillis;
    Entry(String filename,String title,String note,long startMillis){
      this.filename=filename;this.title=title;this.note=note;this.startMillis=startMillis;
    }
  }
  static String month(long epochMillis,TimeZone zone){
    SimpleDateFormat fmt=new SimpleDateFormat("yyyy-MM",Locale.US);
    fmt.setTimeZone(zone);
    return fmt.format(new Date(epochMillis));
  }
  static List<Entry> filter(List<Entry> all,String query,String yearMonth,
                            boolean onlyWithNotes,int sort,TimeZone zone){
    String search=query==null?"":query.trim().toLowerCase(Locale.ROOT);
    List<Entry> result=new ArrayList<>();
    SimpleDateFormat dateFormat=new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.getDefault());
    dateFormat.setTimeZone(zone);
    for(Entry entry:all){
      if(onlyWithNotes&&(entry.note==null||entry.note.isEmpty()))continue;
      if(yearMonth!=null&&!yearMonth.isEmpty()&&!month(entry.startMillis,zone).equals(yearMonth))continue;
      String haystack=(entry.title+" "+entry.note+" "+entry.filename+" "+month(entry.startMillis,zone)+" "+dateFormat.format(new Date(entry.startMillis)))
        .toLowerCase(Locale.ROOT);
      if(!haystack.contains(search))continue;
      result.add(entry);
    }
    Comparator<Entry> byNewest=Comparator.comparingLong((Entry item)->item.startMillis).reversed()
      .thenComparing(item->item.filename);
    if(sort==OLDEST)result.sort(byNewest.reversed());
    else if(sort==NAME)result.sort(Comparator.<Entry,String>comparing(item->item.title, String.CASE_INSENSITIVE_ORDER)
      .thenComparing(byNewest));
    else result.sort(byNewest);
    return result;
  }
}
