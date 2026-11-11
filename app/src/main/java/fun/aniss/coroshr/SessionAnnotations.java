package fun.aniss.coroshr;

import android.content.Context;
import android.content.SharedPreferences;

/** User labels for local sessions. Never rename the data file or change FIT timestamps. */
final class SessionAnnotations {
  private final SharedPreferences prefs;
  SessionAnnotations(Context context){
    prefs=context.getApplicationContext().getSharedPreferences("session_annotations_v1",Context.MODE_PRIVATE);
  }
  private static String key(String type,String filename){
    return type+":"+filename;
  }
  String name(String filename){return prefs.getString(key("name",filename),"");}
  String note(String filename){return prefs.getString(key("note",filename),"");}
  String displayName(String filename,String defaultName){
    String name=name(filename);
    return name.isEmpty()?defaultName:name;
  }
  boolean hasNote(String filename){return !note(filename).isEmpty();}
  static String normalize(String input,int limit){
    if(input==null)return "";
    String clean=input.trim().replace('\r',' ').replace('\n',' ');
    return clean.length()>limit?clean.substring(0,limit).trim():clean;
  }
  boolean save(String filename,String name,String note){
    if(filename==null||filename.isEmpty()||filename.contains("/")||filename.contains("\\"))return false;
    String title=normalize(name,80),details=normalize(note,600);
    SharedPreferences.Editor e=prefs.edit();
    if(title.isEmpty())e.remove(key("name",filename));else e.putString(key("name",filename),title);
    if(details.isEmpty())e.remove(key("note",filename));else e.putString(key("note",filename),details);
    return e.commit();
  }
  void delete(String filename){
    prefs.edit().remove(key("name",filename)).remove(key("note",filename)).apply();
  }
}
