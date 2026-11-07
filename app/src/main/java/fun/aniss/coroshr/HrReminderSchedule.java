package fun.aniss.coroshr;

/** A monotonic-time reminder schedule independent of Activity lifecycle. */
final class HrReminderSchedule {
  static final int DEFAULT_MINUTES=5;
  static final int MIN_MINUTES=1, MAX_MINUTES=120;
  static final long MAX_HEART_RATE_AGE_MS=15_000L;
  private boolean active;
  private int minutes=DEFAULT_MINUTES;
  private long nextAtMs;

  static int clampMinutes(int requested){
    return Math.max(MIN_MINUTES,Math.min(MAX_MINUTES,requested));
  }
  void configure(boolean recording,boolean enabled,int requestedMinutes,long elapsedMs){
    active=recording&&enabled;
    minutes=clampMinutes(requestedMinutes);
    nextAtMs=active?elapsedMs+minutes*60_000L:0L;
  }
  boolean active(){return active;}
  int minutes(){return minutes;}
  long untilDue(long elapsedMs){
    return active?Math.max(0L,nextAtMs-elapsedMs):-1L;
  }
  boolean due(long elapsedMs){return active&&elapsedMs>=nextAtMs;}
  void afterReminder(long elapsedMs){
    if(active)nextAtMs=elapsedMs+minutes*60_000L;
  }
  static boolean fresh(boolean connected,boolean subscribed,int bpm,
                       long heartRateAtMs,long nowElapsedMs){
    return connected&&subscribed&&bpm>0&&bpm<=255&&heartRateAtMs>0&&
      nowElapsedMs>=heartRateAtMs&&nowElapsedMs-heartRateAtMs<=MAX_HEART_RATE_AGE_MS;
  }
}
