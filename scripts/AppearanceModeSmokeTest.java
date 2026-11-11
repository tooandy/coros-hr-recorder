package fun.aniss.coroshr;

public final class AppearanceModeSmokeTest {
  private static void ok(boolean value,String what){if(!value)throw new AssertionError(what);}
  public static void main(String[] args){
    ok(AppearanceModePolicy.sanitize(-1)==AppearanceModePolicy.SYSTEM,"negative invalid -> system");
    ok(AppearanceModePolicy.sanitize(9)==AppearanceModePolicy.SYSTEM,"high invalid -> system");
    ok(!AppearanceModePolicy.isDark(AppearanceModePolicy.SYSTEM,false),"system light");
    ok(AppearanceModePolicy.isDark(AppearanceModePolicy.SYSTEM,true),"system dark");
    ok(!AppearanceModePolicy.isDark(AppearanceModePolicy.LIGHT,false),"forced light on light device");
    ok(!AppearanceModePolicy.isDark(AppearanceModePolicy.LIGHT,true),"forced light on dark device");
    ok(AppearanceModePolicy.isDark(AppearanceModePolicy.DARK,false),"forced dark on light device");
    ok(AppearanceModePolicy.isDark(AppearanceModePolicy.DARK,true),"forced dark on dark device");
    System.out.println("APPEARANCE PASS: 3 modes, manual override and system switching");
  }
}
