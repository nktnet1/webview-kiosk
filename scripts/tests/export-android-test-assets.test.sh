#!/usr/bin/env bash
# Check the host OS dispatch without requiring Docker or Android dependencies.
set -euo pipefail

PROJECT_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd -P)"
EXPORTER="$PROJECT_ROOT/scripts/export-android-test-assets.sh"
TEMP_DIR="$(mktemp -d)"
trap 'rm -rf -- "$TEMP_DIR"' EXIT
mkdir -p "$TEMP_DIR/bin"

cat > "$TEMP_DIR/bin/uname" <<'UNAME'
#!/usr/bin/env bash
case "$1" in
    -s) printf '%s\n' "${FAKE_OS:-Darwin}" ;;
    -m) printf '%s\n' "${FAKE_ARCH:-arm64}" ;;
    *) exit 1 ;;
esac
UNAME
cat > "$TEMP_DIR/bin/docker" <<'DOCKER'
#!/usr/bin/env bash
case "$1" in
    info)
        [[ "${FAKE_DOCKER_DOWN:-}" != 1 ]] || exit 1
        printf '%s\n' "${FAKE_DOCKER_OS:-linux}"
        ;;
    build|run)
        printf '%s\n' "$@" > "$CAPTURE_DIR/$1.args"
        ;;
    *) exit 1 ;;
esac
DOCKER
chmod +x "$TEMP_DIR/bin/uname" "$TEMP_DIR/bin/docker"

export PATH="$TEMP_DIR/bin:$PATH" CAPTURE_DIR="$TEMP_DIR"
assert_contains() {
    grep -Fx -- "$2" "$1" >/dev/null || {
        printf 'Expected %s in %s\n' "$2" "$1" >&2
        exit 1
    }
}

# A simulated Apple Silicon host must not attempt native Gradle/SDK detection.
output="$TEMP_DIR/export with spaces"
FAKE_OS=Darwin FAKE_ARCH=arm64 bash "$EXPORTER" --output "$output" > "$TEMP_DIR/output.log"
[[ -d "$output" ]]
assert_contains "$TEMP_DIR/build.args" 'linux/amd64'
assert_contains "$TEMP_DIR/build.args" "$PROJECT_ROOT/scripts/Dockerfile.android-test-assets"
assert_contains "$TEMP_DIR/run.args" 'linux/amd64'
assert_contains "$TEMP_DIR/run.args" "$PROJECT_ROOT:/source:ro"
assert_contains "$TEMP_DIR/run.args" "$output:/export"
assert_contains "$TEMP_DIR/run.args" 'HOME=/tmp/android-assets-home'
grep -F -- '--sdk-root /opt/android-sdk' "$TEMP_DIR/run.args" >/dev/null

# Help does not require Docker, even on non-Linux hosts.
rm "$TEMP_DIR/build.args" "$TEMP_DIR/run.args"
bash "$EXPORTER" --help > "$TEMP_DIR/help.log"
grep -F 'Docker' "$TEMP_DIR/help.log" >/dev/null
[[ ! -e "$TEMP_DIR/build.args" && ! -e "$TEMP_DIR/run.args" ]]

# Native toolchain paths cannot leak macOS executables/SDK files into Docker.
if bash "$EXPORTER" --sdk-root /some/mac/sdk > "$TEMP_DIR/rejected.log" 2>&1; then
    echo 'Expected native-only flag to be rejected' >&2
    exit 1
fi
grep -F 'native Linux options' "$TEMP_DIR/rejected.log" >/dev/null
[[ ! -e "$TEMP_DIR/build.args" && ! -e "$TEMP_DIR/run.args" ]]

# Report an unavailable daemon, rather than silently reverting to host tools.
if FAKE_DOCKER_DOWN=1 bash "$EXPORTER" > "$TEMP_DIR/down.log" 2>&1; then
    echo 'Expected unavailable Docker to fail' >&2
    exit 1
fi
grep -F 'Docker daemon is unavailable' "$TEMP_DIR/down.log" >/dev/null

if FAKE_DOCKER_OS=windows bash "$EXPORTER" > "$TEMP_DIR/windows.log" 2>&1; then
    echo 'Expected Windows-container mode to be rejected' >&2
    exit 1
fi
grep -F 'Docker must be configured to run Linux containers' "$TEMP_DIR/windows.log" >/dev/null

# The native Linux x86_64 code path never shells out to Docker.
FAKE_OS=Linux FAKE_ARCH=x86_64 bash "$EXPORTER" --gradle-bin /missing/gradle \
    > "$TEMP_DIR/native.log" 2>&1 || true
[[ ! -e "$TEMP_DIR/build.args" && ! -e "$TEMP_DIR/run.args" ]]
printf 'Android test asset exporter OS dispatch: passed\n'
