#!/usr/bin/env bash
# Export everything needed to rerun :app:testDebugUnitTest on Linux x86_64.
# Archives are streamed directly into chunks smaller than 512 MB.
set -euo pipefail

readonly CHUNK_BYTES=480000000
readonly SDK_PLATFORM='android-37.0'
readonly SDK_BUILD_TOOLS='36.0.0'
# Pinned to the official Android Studio Linux download, with Google's SHA-256.
# Used only when the installed sdkmanager is absent or too old to install API 37.0.
readonly CMDLINE_TOOLS_ZIP='commandlinetools-linux-15859902_latest.zip'
readonly CMDLINE_TOOLS_SHA256='4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583'
readonly PROJECT_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
OUTPUT_ARG="$PROJECT_ROOT/build/android-test-assets"
SDK_ARG=""
GRADLE_HOME_ARG=""
GRADLE_BIN_ARG=""
WORK_DIR=""
STAGING_DIR=""

usage() {
    cat <<'HELP'
Usage: scripts/export-android-test-assets.sh [options]

Run on a network-connected Linux x86_64 machine with Android SDK command-line
tools, a working JDK (this project requires JetBrains JDK 21), and a locally
installed Gradle executable matching the version pinned by the wrapper.

Options:
  --sdk-root DIR         Android SDK directory (otherwise auto-detected).
  --output DIR           Output directory (default: build/android-test-assets).
  --gradle-user-home DIR Use an existing Gradle cache instead of a clean one.
  --gradle-bin FILE      Use this local Gradle executable (never download Gradle).
  -h, --help             Show this help.

Runs :app:testDebugUnitTest to populate Gradle and Robolectric caches, then
exports Android SDK components, Gradle dependency cache (including Foojay), and
Robolectric API 28/29 runtime jars. All exported .part-* files are at most
480,000,000 bytes. No keystores or Gradle credentials are exported. The Gradle
wrapper is not invoked and its distribution ZIP is not downloaded or exported;
keep that ZIP separately.
Upload exported archive parts with SHA256SUMS/CONTENTS.txt as needed.
HELP
}

fail() {
    printf 'Error: %s\n' "$*" >&2
    exit 1
}

say() {
    printf '\n==> %s\n' "$*"
}

install_sdk_packages() {
    local manager="$1"
    "$manager" "--sdk_root=$SDK_ROOT" --channel=3 \
        "platforms;$SDK_PLATFORM" "build-tools;$SDK_BUILD_TOOLS"
}

sdk_packages_installed() {
    [[ -f "$SDK_ROOT/platforms/$SDK_PLATFORM/android.jar" && \
       -f "$SDK_ROOT/build-tools/$SDK_BUILD_TOOLS/source.properties" ]]
}

bootstrap_sdkmanager() {
    command -v curl >/dev/null 2>&1 || fail 'curl is required to download current Android command-line tools.'
    command -v unzip >/dev/null 2>&1 || fail 'unzip is required to extract Android command-line tools.'
    local archive="$WORK_DIR/$CMDLINE_TOOLS_ZIP"
    say 'Downloading current Android command-line tools (SHA-256 verified)'
    curl --fail --location --silent --show-error --retry 3 \
        "https://dl.google.com/android/repository/$CMDLINE_TOOLS_ZIP" \
        --output "$archive" || fail 'Could not download Android command-line tools.'
    printf '%s  %s\n' "$CMDLINE_TOOLS_SHA256" "$archive" | sha256sum --check --status || \
        fail 'Android command-line tools SHA-256 mismatch.'
    unzip -q "$archive" -d "$WORK_DIR/sdk-tools" || \
        fail 'Could not unpack Android command-line tools.'
    SDK_MANAGER="$WORK_DIR/sdk-tools/cmdline-tools/bin/sdkmanager"
    [[ -x "$SDK_MANAGER" ]] || fail 'Downloaded SDK archive did not contain sdkmanager.'
}

cleanup() {
    if [[ -n "$STAGING_DIR" ]]; then
        rm -rf -- "$STAGING_DIR"
    fi
    if [[ -n "$WORK_DIR" ]]; then
        rm -rf -- "$WORK_DIR"
    fi
}
trap cleanup EXIT

while (($#)); do
    case "$1" in
        --sdk-root|--output|--gradle-user-home|--gradle-bin)
            (($# >= 2)) || fail "Missing value for $1"
            [[ -n "$2" ]] || fail "Empty value for $1"
            case "$1" in
                --sdk-root) SDK_ARG="$2" ;;
                --output) OUTPUT_ARG="$2" ;;
                --gradle-user-home) GRADLE_HOME_ARG="$2" ;;
                --gradle-bin) GRADLE_BIN_ARG="$2" ;;
            esac
            shift 2
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            usage >&2
            fail "Unknown argument: $1"
            ;;
    esac
done

[[ "$(uname -s)" == Linux && "$(uname -m)" == x86_64 ]] || \
    fail 'Run this on Linux x86_64: Android Build Tools contain host executables.'
for tool in tar split sha256sum grep mktemp java; do
    command -v "$tool" >/dev/null 2>&1 || fail "Missing command: $tool"
done
[[ -f "$PROJECT_ROOT/gradle/wrapper/gradle-wrapper.properties" ]] || \
    fail 'The Gradle wrapper configuration was not found.'

# Using the wrapper with a fresh GRADLE_USER_HOME would download its distribution.
# Run a preinstalled, version-matched Gradle executable instead.
wrapper_url="$(sed -n 's/^distributionUrl=//p' \
    "$PROJECT_ROOT/gradle/wrapper/gradle-wrapper.properties" | tail -n 1)"
[[ "$wrapper_url" =~ /gradle-([0-9]+(\.[0-9]+)+)-bin\.zip$ ]] || \
    fail 'Cannot determine the pinned Gradle version from gradle-wrapper.properties.'
required_gradle_version="${BASH_REMATCH[1]}"

GRADLE_EXEC="$GRADLE_BIN_ARG"
if [[ -z "$GRADLE_EXEC" && -n "${GRADLE_HOME:-}" && -x "$GRADLE_HOME/bin/gradle" ]]; then
    GRADLE_EXEC="$GRADLE_HOME/bin/gradle"
fi
if [[ -z "$GRADLE_EXEC" ]]; then
    # The wrapper may have installed the distribution in a previous invocation.
    for cache_home in "$GRADLE_HOME_ARG" "${GRADLE_USER_HOME:-}" "$HOME/.gradle"; do
        [[ -d "$cache_home" ]] || continue
        for candidate in "$cache_home"/wrapper/dists/gradle-"$required_gradle_version"-bin/*/gradle-"$required_gradle_version"/bin/gradle; do
            [[ -x "$candidate" ]] || continue
            GRADLE_EXEC="$candidate"
            break 2
        done
    done
fi
if [[ -z "$GRADLE_EXEC" ]]; then
    GRADLE_EXEC="$(command -v gradle || true)"
fi
[[ -f "$GRADLE_EXEC" && -x "$GRADLE_EXEC" ]] || \
    fail "Gradle $required_gradle_version must already be installed. Pass --gradle-bin /path/to/gradle; this script will not download its distribution."
GRADLE_EXEC="$(cd -- "$(dirname -- "$GRADLE_EXEC")" && pwd -P)/${GRADLE_EXEC##*/}"
gradle_version_output="$("$GRADLE_EXEC" --version)" || \
    fail "Could not query local Gradle executable: $GRADLE_EXEC"
grep -Fxq "Gradle $required_gradle_version" <<< "$gradle_version_output" || \
    fail "Local Gradle executable does not match wrapper version $required_gradle_version: $GRADLE_EXEC"
say "Using locally installed Gradle $required_gradle_version ($GRADLE_EXEC)"

# Prefer the SDK in local.properties because AGP will use it over environment variables.
if [[ -n "$SDK_ARG" ]]; then
    SDK_ROOT="$SDK_ARG"
elif [[ -f "$PROJECT_ROOT/local.properties" ]] && \
    grep -q '^sdk\.dir=' "$PROJECT_ROOT/local.properties"; then
    SDK_ROOT="$(sed -n 's/^sdk\.dir=//p' "$PROJECT_ROOT/local.properties" | tail -n 1)"
elif [[ -n "${ANDROID_SDK_ROOT:-}" ]]; then
    SDK_ROOT="$ANDROID_SDK_ROOT"
elif [[ -n "${ANDROID_HOME:-}" ]]; then
    SDK_ROOT="$ANDROID_HOME"
elif [[ -d "$HOME/Android/Sdk" ]]; then
    SDK_ROOT="$HOME/Android/Sdk"
elif [[ -d "$HOME/Library/Android/sdk" ]]; then
    SDK_ROOT="$HOME/Library/Android/sdk"
else
    fail 'Set ANDROID_SDK_ROOT or pass --sdk-root to locate the Android SDK.'
fi
[[ -d "$SDK_ROOT" ]] || fail "Android SDK directory not found: $SDK_ROOT"
SDK_ROOT="$(cd -- "$SDK_ROOT" && pwd -P)"

if [[ -n "$SDK_ARG" ]] && [[ -f "$PROJECT_ROOT/local.properties" ]]; then
    local_sdk="$(sed -n 's/^sdk\.dir=//p' "$PROJECT_ROOT/local.properties" | tail -n 1)"
    if [[ -n "$local_sdk" ]]; then
        [[ -d "$local_sdk" ]] || \
            fail "local.properties selects a missing Android SDK: $local_sdk"
        local_sdk="$(cd -- "$local_sdk" && pwd -P)"
        [[ "$local_sdk" == "$SDK_ROOT" ]] || \
            fail "local.properties selects $local_sdk, but --sdk-root selects $SDK_ROOT"
    fi
fi

export ANDROID_HOME="$SDK_ROOT"
export ANDROID_SDK_ROOT="$SDK_ROOT"

mkdir -p -- "$PROJECT_ROOT/build" "$OUTPUT_ARG"
OUTPUT_DIR="$(cd -- "$OUTPUT_ARG" && pwd -P)"
WORK_DIR="$(mktemp -d "$PROJECT_ROOT/build/.android-test-export.XXXXXXXX")"
STAGING_DIR="$(mktemp -d "$OUTPUT_DIR/.staging.XXXXXXXX")"

if [[ -n "$GRADLE_HOME_ARG" ]]; then
    [[ -d "$GRADLE_HOME_ARG" ]] || fail "Gradle home does not exist: $GRADLE_HOME_ARG"
    CAPTURE_GRADLE_HOME="$(cd -- "$GRADLE_HOME_ARG" && pwd -P)"
    printf 'Note: using an existing Gradle cache may export unrelated cached Maven artifacts.\n'
else
    CAPTURE_GRADLE_HOME="$WORK_DIR/gradle-home"
    mkdir -p -- "$CAPTURE_GRADLE_HOME"
fi
export GRADLE_USER_HOME="$CAPTURE_GRADLE_HOME"

# Ensure Robolectric downloads into a private, exportable Maven repository.
# JAVA_TOOL_OPTIONS is inherited by Gradle test workers as well as the launcher.
ROBOLECTRIC_MAVEN="$WORK_DIR/robolectric-maven"
mkdir -p -- "$ROBOLECTRIC_MAVEN"
[[ "$ROBOLECTRIC_MAVEN" != *\"* && "$ROBOLECTRIC_MAVEN" != *$'\n'* ]] || \
    fail 'The checkout path contains a quote/newline unsupported by JAVA_TOOL_OPTIONS.'
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:+$JAVA_TOOL_OPTIONS }-Dmaven.repo.local=\"$ROBOLECTRIC_MAVEN\""

say "Checking Android SDK $SDK_PLATFORM and Build Tools $SDK_BUILD_TOOLS"
if ! sdk_packages_installed; then
    SDK_MANAGER=""
    for candidate in "$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" \
                     "$SDK_ROOT"/cmdline-tools/*/bin/sdkmanager \
                     "$SDK_ROOT/tools/bin/sdkmanager"; do
        if [[ -f "$candidate" && -x "$candidate" ]]; then
            SDK_MANAGER="$candidate"
            break
        fi
    done
    if [[ -z "$SDK_MANAGER" ]]; then
        SDK_MANAGER="$(command -v sdkmanager || true)"
    fi
    if [[ -n "$SDK_MANAGER" ]] && install_sdk_packages "$SDK_MANAGER" && sdk_packages_installed; then
        : # The existing sdkmanager is new enough.
    else
        printf 'Warning: local sdkmanager could not install Android SDK %s; trying current tools.\n' \
            "$SDK_PLATFORM" >&2
        bootstrap_sdkmanager
        install_sdk_packages "$SDK_MANAGER" || \
            fail "Could not install platforms;$SDK_PLATFORM and build-tools;$SDK_BUILD_TOOLS. Check network access and SDK licences."
    fi
fi
sdk_packages_installed || \
    fail "Android SDK $SDK_PLATFORM / Build Tools $SDK_BUILD_TOOLS are still missing after installation."

say 'Resolving project dependencies and Robolectric runtimes with the test task'
TEST_RESULT=passed
if ! (cd "$PROJECT_ROOT" && "$GRADLE_EXEC" \
    --no-daemon --no-configuration-cache --rerun-tasks --continue --console=plain \
    :app:testDebugUnitTest); then
    TEST_RESULT=failed
    printf 'Warning: Gradle/test task failed; checking whether dependencies were still populated.\n' >&2
fi

MODULES_DIR="$CAPTURE_GRADLE_HOME/caches/modules-2"
[[ -d "$MODULES_DIR/files-2.1" ]] || \
    fail 'Gradle dependency artifacts were not populated; resolve the build error above.'
metadata_dirs=("$MODULES_DIR"/metadata-2.*)
[[ -d "${metadata_dirs[0]}" ]] || \
    fail 'Gradle dependency metadata was not populated; resolve the build error above.'

# The project runs Robolectric on API 28 by default and API 29 in selected tests.
# These correspond to Robolectric Android versions 9 and 10, respectively.
robolectric_paths=()
for android_version in 9 10; do
    found_instrumented=false
    for artifact in android-all-instrumented android-all; do
        for dir in "$ROBOLECTRIC_MAVEN/org/robolectric/$artifact/$android_version"-robolectric-*; do
            [[ -d "$dir" ]] || continue
            jars=("$dir"/*.jar)
            [[ -f "${jars[0]}" ]] || continue
            robolectric_paths+=("org/robolectric/$artifact/${dir##*/}")
            if [[ "$artifact" == android-all-instrumented ]]; then
                found_instrumented=true
            fi
        done
    done
    [[ "$found_instrumented" == true ]] || \
        fail "Robolectric Android ${android_version} runtime was not downloaded. Resolve the test error above."
done

split_stream() {
    local prefix="$1"
    split -b "$CHUNK_BYTES" -d -a 3 - "$STAGING_DIR/${prefix}.part-"
}

say 'Exporting Android SDK components'
sdk_paths=("platforms/$SDK_PLATFORM" "build-tools/$SDK_BUILD_TOOLS")
[[ ! -d "$SDK_ROOT/licenses" ]] || sdk_paths+=(licenses)
[[ ! -d "$SDK_ROOT/platform-tools" ]] || sdk_paths+=(platform-tools)
tar -C "$SDK_ROOT" -czf - "${sdk_paths[@]}" | split_stream 'android-sdk.tar.gz'

say 'Exporting Gradle dependency artifacts and metadata (excluding lock files)'
tar --exclude='*.lock' --exclude='gc.properties' \
    -C "$CAPTURE_GRADLE_HOME" -czf - caches/modules-2 | split_stream 'gradle-cache.tar.gz'

say 'Exporting Robolectric API 28/29 Maven artifacts'
tar -C "$ROBOLECTRIC_MAVEN" -czf - "${robolectric_paths[@]}" | \
    split_stream 'robolectric-maven.tar.gz'

(
    cd "$STAGING_DIR"
    for part in *.part-*; do
        [[ -f "$part" ]] || fail 'No archive parts were created.'
        size="$(wc -c < "$part")"
        ((size < 512000000)) || fail "Output exceeds 512 MB: $part"
    done
    sha256sum -- *.part-* > SHA256SUMS
)
cat > "$STAGING_DIR/CONTENTS.txt" <<CONTENTS
Project task: :app:testDebugUnitTest
Platform: Linux x86_64
Gradle distribution: not exported (keep separately; version pinned by Gradle wrapper)
Android SDK: platforms/$SDK_PLATFORM, build-tools/$SDK_BUILD_TOOLS, optional licences/platform-tools
Gradle cache: caches/modules-2 (including plugin resolution metadata)
Robolectric: Android 9 / API 28 and Android 10 / API 29 Maven runtime artifacts
Gradle test invocation: $TEST_RESULT
Archive chunks: 480000000 bytes maximum; parts ordered by numeric suffix
Archive reconstruction: concatenate matching .part-* files in suffix order.
Gradle cache destination: \$GRADLE_USER_HOME/
Android SDK destination: \$ANDROID_SDK_ROOT/
Robolectric Maven destination: ~/.m2/repository/ (default)
CONTENTS

# Replace only files managed by this script; remove legacy Gradle ZIP exports.
rm -f -- "$OUTPUT_DIR"/gradle-*-bin.zip.part-* \
    "$OUTPUT_DIR"/android-sdk.tar.gz.part-* \
    "$OUTPUT_DIR"/gradle-cache.tar.gz.part-* \
    "$OUTPUT_DIR"/robolectric-maven.tar.gz.part-* \
    "$OUTPUT_DIR"/SHA256SUMS "$OUTPUT_DIR"/CONTENTS.txt
mv -- "$STAGING_DIR"/* "$OUTPUT_DIR"/
say "Export complete: $OUTPUT_DIR"
ls -lh "$OUTPUT_DIR"
if [[ "$TEST_RESULT" == failed ]]; then
    printf '\nWarning: tests failed, but all requested dependencies were captured.\n' >&2
fi
