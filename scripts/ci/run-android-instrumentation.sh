#!/bin/sh
set -eu

if sh gradlew :app:connectedDebugAndroidTest --build-cache --profile --stacktrace; then
  exit 0
else
  status=$?
fi

results_dir=app/build/outputs/androidTest-results/connected
mkdir -p "$results_dir"
adb logcat -d -v threadtime > "$results_dir/device-logcat.txt" || true
exit "$status"
