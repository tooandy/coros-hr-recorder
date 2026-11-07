package fun.aniss.coroshr;

/** BLE Battery Level (0x2A19): a single unsigned percentage byte, 0..100. */
final class BatteryLevel {
  private BatteryLevel() {}
  static int parse(byte[] value) {
    if (value == null || value.length != 1) return -1;
    int percent = value[0] & 0xff;
    return percent <= 100 ? percent : -1;
  }
}
