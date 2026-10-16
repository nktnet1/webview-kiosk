#!/bin/sh
# Run the same automated test tasks as CI against locally installed tools.
# Nothing is downloaded or installed by this script.
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
cd "$ROOT"

usage() {
    cat <<'USAGE'
Usage: sh scripts/ci/test-local.sh <suite>

Suites:
  checks          Offline CI, asset-exporter, cosmetic and scriptlet tests (Python/Node)
  jvm             Filter-engine JVM and Android JVM/Robolectric unit tests
  docs            Documentation format/lint and Vitest tests
  docs-browser    Build docs and run Playwright Chromium tests
  all             Run all four suites in the order above

Examples:
  sh scripts/ci/test-local.sh checks
  sh scripts/ci/test-local.sh jvm
  sh scripts/ci/test-local.sh docs-browser

Requirements: Python 3.10+ and Node 24 for checks; JDK 21 and an Android SDK
for JVM/Robolectric tests; Node 24,
pnpm and installed docs/node_modules for docs tests, and Playwright Chromium
for docs-browser. The runner does not install tools or dependencies.
Android device/AVD scenarios are manual and are not run by this script.
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
    checks|jvm|docs|docs-browser|all) ;;
    *) die "Unknown suite '$suite'. See --help." ;;
esac

if [ "$#" -ne 0 ]; then
    die "Unexpected option '$1'. See --help."
fi

checks() {
    require_tool python3
    require_tool node
    printf 'Local tests: checking CI scripts, test assets, cosmetics and scriptlets\n'
    python3 -m unittest discover -s scripts/tests -p 'test_*.py'
    node --test scripts/tests/cosmetic.test.mjs scripts/tests/scriptlets.test.mjs \
      scripts/tests/additional-scriptlets.test.mjs scripts/tests/response-scriptlets.test.mjs
}

jvm() {
    require_tool java
    printf 'Local tests: running adblock JVM and Android JVM/Robolectric tests\n'
    sh gradlew :adblock:test :app:testDebugUnitTest --build-cache --profile --stacktrace
}

prepare_docs() {
    require_tool node
    require_tool pnpm
    [ -d docs/node_modules ] || die \
        'docs/node_modules is missing. Install the locked dependencies before running docs tests.'
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
    docs) docs ;;
    docs-browser) docs_browser ;;
    all)
        checks
        jvm
        docs
        docs_browser
        ;;
esac
