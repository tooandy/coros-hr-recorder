package fun.aniss.coroshr;

/** Pure mode logic. Used by Android theme bridge and offline JVM tests. */
final class AppearanceModePolicy {
  static final int SYSTEM=0, LIGHT=1, DARK=2;
  private AppearanceModePolicy(){}
  static int sanitize(int mode){return mode>=SYSTEM&&mode<=DARK?mode:SYSTEM;}
  static boolean isDark(int mode,boolean systemDark){
    return sanitize(mode)==DARK||(sanitize(mode)==SYSTEM&&systemDark);
  }
}
