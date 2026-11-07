#!/bin/sh
set -eu
PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
javac -d "$OUT" "$PROJECT_DIR/app/src/main/java/fun/aniss/coroshr/BatteryLevel.java" "$PROJECT_DIR/scripts/BatteryLevelSmokeTest.java"
java -cp "$OUT" fun.aniss.coroshr.BatteryLevelSmokeTest
