#!/bin/sh
# Run the same automated test tasks as CI against locally installed tools.
# Nothing is downloaded or installed by this script.
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
cd "$ROOT"

usage() {
    cat <<'USAGE'
Usage: sh scripts/ci/test-local.sh <suite> [options]

Suites:
  checks          Offline CI regression tests (Python and shell)
  jvm             Android JVM/Robolectric unit tests
  android         Installed-APK instrumentation against a running emulator/device
  docs            Documentation format/lint and Vitest tests
  docs-browser    Build docs and run Playwright Chromium tests
  all             Run every suite in the order above

Android-only options:
  --api 23|35     Require the connected device to use this API level
  --class NAME    Run only an instrumentation class or CLASS#method
                  Short names get the project's instrumentation package prefix.

Examples:
  sh scripts/ci/test-local.sh checks
  sh scripts/ci/test-local.sh jvm
  sh scripts/ci/test-local.sh android --api 35 --class AuthenticationRouteDeviceTest
  sh scripts/ci/test-local.sh android --api 35 --class AuthenticationRouteDeviceTest#settingsRemainProtectedBeforeUnlockAndRequireAuthAfterRecreation
  sh scripts/ci/test-local.sh docs-browser

Requirements: JDK 21 and a local Android SDK for Android tests; an already
booted, disposable AVD for 'android'; Node 24, pnpm and installed docs/node_modules
for docs tests, and Playwright Chromium for docs-browser. The runner does not
create an AVD, install SDK components, or install dependencies.

API 23 in CI is default/x86; API 35 is google_apis/x86_64. Check your local
AVD's system image and CPU architecture when strict parity matters.
USAGE
}

die() {
    printf 'Local tests: %s\n' "$1" >&2
    exit 2
}

require_tool() {
    command -v "$1" > /dev/null 2>&1 || die "'$1' is required for this suite."
}

if [ "$#" -eq 0 ]; then
    usage
    exit 2
fi

suite=$1
shift

case "$suite" in
    -h|--help|help)
        usage
        exit 0
        ;;
    checks|jvm|android|docs|docs-browser|all) ;;
    *) die "Unknown suite '$suite'. See --help." ;;
esac

requested_api=
requested_class=
while [ "$#" -gt 0 ]; do
    case "$1" in
        --api)
            [ "$#" -ge 2 ] || die 'Missing value for --api.'
            [ -n "$2" ] || die 'Missing value for --api.'
            requested_api=$2
            shift 2
            ;;
        --class)
            [ "$#" -ge 2 ] || die 'Missing value for --class.'
            [ -n "$2" ] || die 'Missing value for --class.'
            requested_class=$2
            shift 2
            ;;
        *) die "Unknown option '$1'. See --help." ;;
    esac
done

if [ "$suite" != android ] && { [ -n "$requested_api" ] || [ -n "$requested_class" ]; }; then
    die '--api and --class are only valid with the android suite.'
fi

if [ -n "$requested_api" ]; then
    case "$requested_api" in
        23|35) ;;
        *) die 'Supported CI API levels are 23 and 35.' ;;
    esac
fi

if [ -n "$requested_class" ]; then
    case "$requested_class" in
        *[!a-zA-Z0-9._#]*|*##*|*#|\#*|.*|*..*)
            die 'Invalid --class value. Use CLASS or CLASS#method.'
            ;;
    esac
    case "$requested_class" in
        *.*) ;;
        *) requested_class="uk.nktnet.webviewkiosk.instrumentation.$requested_class" ;;
    esac
fi

checks() {
    require_tool python3
    require_tool bash
    printf 'Local tests: checking CI scripts and diagnostics\n'
    bash scripts/tests/export-android-test-assets.test.sh
    python3 -m unittest discover -s scripts/tests -p 'test_*.py'
}

jvm() {
    require_tool java
    printf 'Local tests: running Android JVM/Robolectric tests\n'
    sh gradlew :app:testDebugUnitTest --build-cache --profile --stacktrace
}

android() {
    require_tool java
    require_tool adb

    # Gradle's connectedDebugAndroidTest may act on multiple attached devices.
    # Require an unambiguous target instead of accidentally mutating a phone.
    devices=$(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }')
    count=$(printf '%s\n' "$devices" | awk 'NF { n++ } END { print n+0 }')
    if [ "$count" -ne 1 ]; then
        die "Expected exactly one booted, authorised disposable test device, found $count. Disconnect other devices and start an AVD."
    fi

    device=$devices
    if [ -n "${ANDROID_SERIAL:-}" ] && [ "$ANDROID_SERIAL" != "$device" ]; then
        die "ANDROID_SERIAL '$ANDROID_SERIAL' does not match the single connected test device '$device'."
    fi
    ANDROID_SERIAL=$device
    export ANDROID_SERIAL

    observed_api=$(adb -s "$device" shell getprop ro.build.version.sdk | tr -d '\r')
    [ -n "$observed_api" ] || die 'Could not determine the device API level.'
    if [ -n "$requested_api" ] && [ "$observed_api" != "$requested_api" ]; then
        die "Connected device has API $observed_api, but --api $requested_api was requested."
    fi

    printf 'Local tests: running installed-APK tests on %s (API %s)\n' "$device" "$observed_api"
    if [ -n "$requested_class" ]; then
        printf 'Local tests: instrumentation filter: %s\n' "$requested_class"
        ANDROID_TEST_CLASS=$requested_class
        export ANDROID_TEST_CLASS
    else
        unset ANDROID_TEST_CLASS
    fi

    if sh scripts/ci/run-android-instrumentation.sh; then
        :
    else
        result=$?
        # The same report parser runs in CI. Never replace the Gradle failure.
        python3 scripts/ci/report_android_instrumentation.py || true
        exit "$result"
    fi
}

prepare_docs() {
    require_tool node
    require_tool pnpm
    [ -d docs/node_modules ] || die 'docs/node_modules is missing. Install the locked dependencies separately before running docs tests.'
}

docs() {
    prepare_docs
    printf 'Local tests: checking documentation and running Vitest\n'
    pnpm -C docs check
    pnpm -C docs test
}

docs_browser() {
    prepare_docs
    printf 'Local tests: building documentation and running Playwright\n'
    pnpm -C docs build
    pnpm -C docs test:e2e
}

case "$suite" in
    checks) checks ;;
    jvm) jvm ;;
    android) android ;;
    docs) docs ;;
    docs-browser) docs_browser ;;
    all)
        checks
        jvm
        docs
        docs_browser
        android
        ;;
esac
