#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p .tools/ci/{battery,gps,probe,reminder,compare,history}
javac -d .tools/ci/battery app/src/main/java/fun/aniss/coroshr/BatteryLevel.java scripts/BatteryLevelSmokeTest.java
java -cp .tools/ci/battery fun.aniss.coroshr.BatteryLevelSmokeTest
javac -d .tools/ci/gps app/src/main/java/fun/aniss/coroshr/GpsDenoiser.java scripts/GpsDenoiserSmokeTest.java
java -cp .tools/ci/gps fun.aniss.coroshr.GpsDenoiserSmokeTest
javac -d .tools/ci/probe app/src/main/java/fun/aniss/coroshr/BleProbeLog.java scripts/BleProbeLogSmokeTest.java
java -cp .tools/ci/probe fun.aniss.coroshr.BleProbeLogSmokeTest
javac -d .tools/ci/reminder app/src/main/java/fun/aniss/coroshr/HrReminderSchedule.java scripts/HrReminderScheduleSmokeTest.java
java -cp .tools/ci/reminder fun.aniss.coroshr.HrReminderScheduleSmokeTest
javac -d .tools/ci/reminder app/src/main/java/fun/aniss/coroshr/AppearanceModePolicy.java scripts/AppearanceModeSmokeTest.java
java -cp .tools/ci/reminder fun.aniss.coroshr.AppearanceModeSmokeTest
javac -d .tools/ci/reminder app/src/main/java/fun/aniss/coroshr/AutoSessionPolicy.java scripts/AutoSessionPolicySmokeTest.java
java -cp .tools/ci/reminder fun.aniss.coroshr.AutoSessionPolicySmokeTest
javac -d .tools/ci/history app/src/main/java/fun/aniss/coroshr/SessionHistory.java scripts/SessionHistorySmokeTest.java
java -cp .tools/ci/history fun.aniss.coroshr.SessionHistorySmokeTest
javac -d .tools/ci/history app/src/main/java/fun/aniss/coroshr/SessionCatalog.java scripts/SessionCatalogSmokeTest.java
java -cp .tools/ci/history fun.aniss.coroshr.SessionCatalogSmokeTest
FIT_JAR=$(find "$HOME/.gradle/caches/modules-2/files-2.1/com.garmin/fit/21.205.0" -name '*.jar' -print -quit 2>/dev/null || true)
if [[ -z "$FIT_JAR" || ! -f "$FIT_JAR" ]]; then
  echo 'Garmin FIT JAR not found. Run ./gradlew :app:compileDebugJavaWithJavac first.' >&2
  exit 1
fi
javac -cp "$FIT_JAR" -d .tools/ci/compare \
  app/src/main/java/fun/aniss/coroshr/{HrSeries,FitExporter,TrackData}.java \
  scripts/{WorkoutFitSmokeTest,ComparisonSmokeTest}.java
java -cp "$FIT_JAR:.tools/ci/compare" fun.aniss.coroshr.WorkoutFitSmokeTest
java -cp "$FIT_JAR:.tools/ci/compare" fun.aniss.coroshr.ComparisonSmokeTest
echo 'All offline regression tests passed.'
