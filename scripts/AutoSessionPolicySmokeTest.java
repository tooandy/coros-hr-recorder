package fun.aniss.coroshr;

public final class AutoSessionPolicySmokeTest {
  private static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);}
  public static void main(String[] args){
    for(int connect=0;connect<=1;connect++)for(int record=0;record<=1;record++){
      boolean autoConnect=connect==1,autoRecord=record==1;
      check(AutoSessionPolicy.connectOnLaunch(autoConnect,true)==autoConnect,
        "remembered device connects iff auto-connect enabled "+connect+record);
      check(AutoSessionPolicy.startAfterConnection(autoRecord)==autoRecord,
        "manual and automatic connection both use auto-record preference "+connect+record);
      check(!AutoSessionPolicy.connectOnLaunch(autoConnect,false),"no saved device never reconnects");
    }
    // Existing installations previously auto-restored regardless of the
    // autoRecord checkbox; default autoConnect=true preserves that behavior.
    check(AutoSessionPolicy.connectOnLaunch(true,true),"legacy auto connection preserved");
    check(!AutoSessionPolicy.startAfterConnection(false),"legacy opt-out of recording preserved");
    System.out.println("AUTO SESSION PASS: independent four combinations, no remembered sensor and legacy defaults");
  }
}
