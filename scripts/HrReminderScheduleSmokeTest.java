package fun.aniss.coroshr;

/** Fast JVM regression checks; avoids sleeping and never builds an APK. */
public final class HrReminderScheduleSmokeTest {
  static void check(boolean ok,String what){if(!ok)throw new AssertionError(what);}
  public static void main(String[] args){
    HrReminderSchedule clock=new HrReminderSchedule();
    long now=987_650L;
    check(HrReminderSchedule.clampMinutes(0)==1,"min clamp");
    check(HrReminderSchedule.clampMinutes(-5)==1,"negative clamp");
    check(HrReminderSchedule.clampMinutes(999)==120,"max clamp");
    check(HrReminderSchedule.clampMinutes(7)==7,"custom interval");
    clock.configure(false,true,5,now);
    check(!clock.active()&&clock.untilDue(now)==-1,"not recording never schedules");
    clock.configure(true,false,5,now);
    check(!clock.active(),"off means no reminders");
    clock.configure(true,true,5,now);
    check(clock.active(),"recording and enabled");
    check(clock.untilDue(now)==300_000L,"first reminder after full interval");
    check(!clock.due(now+299_999L),"not early");
    check(clock.due(now+300_000L),"exact deadline");
    clock.afterReminder(now+300_000L);
    check(!clock.due(now+300_001L),"no repeated instant reminder");
    check(clock.due(now+600_000L),"repeat after N minutes");
    clock.afterReminder(now+1_800_000L);
    check(clock.untilDue(now+1_800_000L)==300_000L,"late execution skips missed bursts");
    clock.configure(true,true,2,now+1_900_000L);
    check(clock.untilDue(now+1_900_000L)==120_000L,"live interval change resets countdown");
    clock.configure(true,false,2,now+1_950_000L);
    check(!clock.due(now+100_000_000L),"stop cancels everything");
    check(HrReminderSchedule.fresh(true,true,156,1_000,15_999),"fresh HR");
    check(!HrReminderSchedule.fresh(true,true,156,1_000,16_001),"stale HR");
    check(!HrReminderSchedule.fresh(false,true,156,1_000,1_001),"disconnected HR");
    check(!HrReminderSchedule.fresh(true,false,156,1_000,1_001),"not subscribed");
    check(!HrReminderSchedule.fresh(true,true,0,1_000,1_001),"invalid BPM");
    check(!HrReminderSchedule.fresh(true,true,156,0,1_001),"no sample");
    check(!HrReminderSchedule.fresh(true,true,156,2_000,1_999),"monotonic inversion");
    System.out.println("REMINDER PASS: custom intervals, scheduling, stop, late tick, staleness and disconnection");
  }
}
