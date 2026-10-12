#!/bin/sh
set -eu

# A filter is optional. Without one, run the entire installed-APK test suite as CI does.
set --
if [ -n "${ANDROID_TEST_CLASS:-}" ]; then
  set -- "-Pandroid.testInstrumentationRunnerArguments.class=$ANDROID_TEST_CLASS"
fi

if sh gradlew :app:connectedDebugAndroidTest --build-cache --profile --stacktrace "$@"; then
  exit 0
else
  status=$?
fi

results_dir=app/build/outputs/androidTest-results/connected
mkdir -p "$results_dir"
adb logcat -d -v threadtime > "$results_dir/device-logcat.txt" || true
exit "$status"
