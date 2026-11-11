package fun.aniss.coroshr;

/** Separate controls: reconnect on app launch vs record on HR subscription. */
final class AutoSessionPolicy {
  private AutoSessionPolicy(){}
  static boolean connectOnLaunch(boolean autoConnect,boolean hasRememberedSensor){
    return autoConnect&&hasRememberedSensor;
  }
  static boolean startAfterConnection(boolean autoRecord){
    return autoRecord;
  }
}
