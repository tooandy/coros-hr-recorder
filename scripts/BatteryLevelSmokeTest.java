package fun.aniss.coroshr;

/** Runs with plain javac, without Gradle or external test dependencies. */
public final class BatteryLevelSmokeTest {
  private static void check(byte[] raw, int expected) {
    int actual=BatteryLevel.parse(raw);
    if(actual!=expected)
      throw new AssertionError("Expected "+expected+", got "+actual);
  }
  public static void main(String[] args) {
    check(new byte[]{0}, 0);
    check(new byte[]{19}, 19);
    check(new byte[]{100}, 100);
    check(new byte[]{(byte)101}, -1);
    check(new byte[]{(byte)255}, -1);
    check(new byte[]{}, -1);
    check(new byte[]{20, 30}, -1);
    check(null, -1);
    System.out.println("BatteryLevel smoke test PASS (8 cases)");
  }
}
