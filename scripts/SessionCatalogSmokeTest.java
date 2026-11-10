package fun.aniss.coroshr;

import java.util.*;

public final class SessionCatalogSmokeTest {
  static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
  public static void main(String[] args){
    TimeZone zone=TimeZone.getTimeZone("Asia/Shanghai");
    long november=1794117600000L,october=1791184800000L;
    List<SessionCatalog.Entry> rows=Arrays.asList(
      new SessionCatalog.Entry("hr_20261108.csv","无锡全马","天气晴，补给稳定",november),
      new SessionCatalog.Entry("hr_20261005.csv","日常慢跑","",october),
      new SessionCatalog.Entry("hr_20261006.csv","Tempo","配速训练",october+86400000L));
    check(SessionCatalog.filter(rows,"",null,false,0,zone).get(0).title.equals("无锡全马"),"newest");
    check(SessionCatalog.filter(rows,"补给",null,false,0,zone).size()==1,"search note");
    check(SessionCatalog.filter(rows,"hr_20261005",null,false,0,zone).size()==1,"search filename");
    check(SessionCatalog.filter(rows,"",null,true,0,zone).size()==2,"notes only");
    check(SessionCatalog.filter(rows,"","2026-10",false,0,zone).size()==2,"month filter");
    check(SessionCatalog.filter(rows,"",null,false,1,zone).get(0).title.equals("日常慢跑"),"oldest");
    check(SessionCatalog.filter(rows,"",null,false,2,zone).get(0).title.equals("Tempo"),"name sort");
    System.out.println("SESSION CATALOG PASS: notes, month, search, sorting and annotations");
  }
}
