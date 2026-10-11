#!/bin/sh
# Verify the EXACT R8-minified release APK produced by the build job.
set -eu

APK_PATH="$(find app/build/outputs/apk/release -maxdepth 1 -type f -name '*.apk' -print -quit)"
test -n "$APK_PATH" || { echo 'No release APK in artifact' >&2; exit 1; }

adb install -r "$APK_PATH"
adb logcat -c
LAUNCH_OUTPUT="$(adb shell am start -W -n uk.nktnet.webviewkiosk/.MainActivity 2>&1)"
printf '%s\n' "$LAUNCH_OUTPUT"
printf '%s\n' "$LAUNCH_OUTPUT" | grep -q 'Status: ok' || {
  echo 'Release Activity did not launch successfully' >&2
  exit 1
}

# Do not mistake a successful am invocation for an app that remained running.
sleep 6
PID="$(adb shell pidof uk.nktnet.webviewkiosk | tr -d '\r' || true)"
test -n "$PID" || { echo 'Release APK exited after startup' >&2; exit 1; }
echo "Release app remains running (PID $PID)"
