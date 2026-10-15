#!/usr/bin/env bash
# Export Linux x86_64 Android test dependencies, using Docker on other hosts.
# Archives smaller than 512 MB remain whole; larger ones use numbered parts.
set -euo pipefail

readonly MAX_ARCHIVE_BYTES=512000000
readonly SDK_PLATFORM='android-37.0'
readonly SDK_BUILD_TOOLS='36.0.0'
# Matched to Robolectric 4.17 (API 28 and 29). Update with its SDK mappings.
readonly ROBOLECTRIC_VERSIONS=(
    '9-robolectric-4913185-2-i7'
    '10-robolectric-5803371-i7'
)
readonly ROBOLECTRIC_MAVEN_URL='https://repo.maven.apache.org/maven2/org/robolectric/android-all-instrumented'
# Pinned to the official Android Studio Linux download, with Google's SHA-256.
# Used only when the installed sdkmanager is absent or too old to install API 37.0.
readonly CMDLINE_TOOLS_ZIP='commandlinetools-linux-15859902_latest.zip'
readonly CMDLINE_TOOLS_SHA256='4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583'
readonly PROJECT_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
# The same stream writer is tested without requiring an Android SDK or Docker.
source "$PROJECT_ROOT/scripts/lib/android-test-asset-archive.sh"
OUTPUT_ARG="$PROJECT_ROOT/build/android-test-assets"
SDK_ARG=""
GRADLE_HOME_ARG=""
GRADLE_BIN_ARG=""
WITH_RUST=0
WORK_DIR=""
STAGING_DIR=""

usage() {
    cat <<'HELP'
Usage: scripts/export-android-test-assets.sh [options]

On Linux x86_64, run with Android SDK command-line tools, JetBrains JDK 21,
and a locally installed Gradle executable matching the wrapper. On other hosts
(including macOS and Linux ARM64), Docker automatically runs the same export in
a Linux x86_64 container; only Docker and network access are required.

Options:
  --sdk-root DIR         Android SDK directory (native Linux only).
  --output DIR           Output directory (default: build/android-test-assets).
  --gradle-user-home DIR Use an existing Gradle cache (native Linux only).
  --gradle-bin FILE      Use this local Gradle executable (native Linux only).
  --with-rust            Also export the Linux host Rust toolchain and vendored
                         native-adblock Cargo dependencies into --output/rust.
  -h, --help             Show this help.

Resolves the Android JVM test configurations without compiling or running tests,
downloads the pinned Robolectric API 28/29 Maven runtimes, then exports Android
SDK components, Gradle dependency artifacts and resolution metadata
(caches/modules-2), and Robolectric jars.
Archives smaller than 512,000,000 bytes are ordinary .tar.gz files; larger
ones use numbered .part-* files, each smaller than 512,000,000 bytes. Gradle
credentials, keystores, and the Gradle distribution are not exported. Upload
the resulting archives (or parts) with SHA256SUMS and CONTENTS.txt.

Use --with-rust to invoke the separate Rust asset exporter after the Android
export, or run scripts/export-rust-test-assets.sh by itself to avoid repeating
Gradle resolution. The Rust host parser tests need no Android NDK.

Docker mode builds scripts/Dockerfile.android-test-assets on first use, runs
with an isolated project copy (ignoring host local.properties), and writes only
the exported archives to --output. Its named volume caches resolved Gradle and
Robolectric artifacts across runs. The image has pinned Gradle, JetBrains JDK
21 and the Linux Android SDK; host toolchain overrides are not accepted.
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
        --with-rust)
            WITH_RUST=1
            shift
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

# SDK Build Tools and Robolectric artifacts are Linux x86_64-specific. Keep
# the existing native path unchanged, but provide a self-contained Docker path
# on macOS, Windows (bash), and Linux systems running another architecture.
run_in_docker() {
    [[ -z "$SDK_ARG" && -z "$GRADLE_HOME_ARG" && -z "$GRADLE_BIN_ARG" ]] || \
        fail '--sdk-root, --gradle-user-home and --gradle-bin are native Linux options; Docker supplies its own Linux toolchain.'
    command -v docker >/dev/null 2>&1 || \
        fail 'Docker is required to export Linux x86_64 test assets on this host.'
    local docker_os
    docker_os="$(docker info --format '{{.OSType}}')" || \
        fail 'The Docker daemon is unavailable; start Docker and try again.'
    [[ "$docker_os" == linux ]] || fail 'Docker must be configured to run Linux containers.'
    command -v id >/dev/null 2>&1 || fail 'Missing command: id'

    mkdir -p -- "$OUTPUT_ARG"
    local output_dir
    output_dir="$(cd -- "$OUTPUT_ARG" && pwd -P)"
    local image='webview-kiosk-android-test-assets:gradle-9.8.0-jbr21'
    local cache_volume='webview-kiosk-android-test-assets-deps'

    say 'Building the Linux x86_64 Android test exporter image (cached after first use)'
    docker build --platform linux/amd64 --tag "$image" \
        --file "$PROJECT_ROOT/scripts/Dockerfile.android-test-assets" \
        "$PROJECT_ROOT/scripts"

    say "Exporting with Docker to $output_dir"
    docker run --rm --platform linux/amd64 \
        --user "$(id -u):$(id -g)" \
        --env HOME=/tmp/android-assets-home \
        --volume "$PROJECT_ROOT:/source:ro" \
        --volume "$output_dir:/export" \
        --volume "$cache_volume:/var/cache/android-test-assets" \
        --workdir /tmp "$image" bash -c '
            set -euo pipefail
            mkdir -p /tmp/android-assets-home /tmp/project \
                /var/cache/android-test-assets/gradle
            # Do not copy host build output, caches, credentials or SDK paths.
            tar -C /source \
                --exclude=./.git --exclude=./.gradle --exclude=./.idea \
                --exclude=./build --exclude=./app/build \
                --exclude=./docs --exclude=./local.properties \
                --exclude=./app/release --exclude=./app/keystore.jks \
                --exclude=./.env* --exclude='*.jks' \
                --exclude='*.bundle' --exclude='*.patch' \
                -cf - . | tar -C /tmp/project -xf -
            exec /tmp/project/scripts/export-android-test-assets.sh \
                --sdk-root /opt/android-sdk \
                --gradle-bin /opt/gradle/bin/gradle \
                --gradle-user-home /var/cache/android-test-assets/gradle \
                --output /export
        '
}

if [[ "$(uname -s)" != Linux || "$(uname -m)" != x86_64 ]]; then
    run_in_docker
    if ((WITH_RUST)); then
        "$PROJECT_ROOT/scripts/export-rust-test-assets.sh" --output "$OUTPUT_ARG/rust"
    fi
    exit 0
fi
for tool in tar split sha256sum sha512sum grep mktemp java curl; do
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
    printf 'Note: reusing the supplied Gradle home; its previously resolved modules may also be exported.\n'
else
    CAPTURE_GRADLE_HOME="$WORK_DIR/gradle-home"
    mkdir -p -- "$CAPTURE_GRADLE_HOME"
fi
export GRADLE_USER_HOME="$CAPTURE_GRADLE_HOME"

# Cache the Robolectric Maven artifacts alongside an explicitly supplied
# Gradle home (including the Docker named volume), but do not put them inside
# caches/modules-2, which is exported separately.
ROBOLECTRIC_MAVEN="$WORK_DIR/robolectric-maven"
if [[ -n "$GRADLE_HOME_ARG" ]]; then
    ROBOLECTRIC_MAVEN="$CAPTURE_GRADLE_HOME/robolectric-maven"
fi
mkdir -p -- "$ROBOLECTRIC_MAVEN"

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

say 'Resolving Android JVM test dependencies (no compilation or test execution)'
(cd "$PROJECT_ROOT" && "$GRADLE_EXEC" \
    --no-daemon --no-configuration-cache --console=plain \
    --init-script "$PROJECT_ROOT/scripts/resolve-android-test-assets.init.gradle" \
    :app:resolveAndroidTestAssets) || \
    fail 'Gradle could not resolve the required Android JVM test dependencies.'

MODULES_DIR="$CAPTURE_GRADLE_HOME/caches/modules-2"
[[ -d "$MODULES_DIR/files-2.1" ]] || \
    fail 'Gradle dependency artifacts were not populated; resolve the build error above.'
metadata_dirs=("$MODULES_DIR"/metadata-2.*)
[[ -d "${metadata_dirs[0]}" ]] || \
    fail 'Gradle dependency resolution metadata was not populated; resolve the build error above.'

verify_maven_checksum() {
    local target="$1"
    [[ -s "$target" && -s "$target.sha512" ]] || return 1
    local expected
    expected="$(tr -d '[:space:]' < "$target.sha512")"
    [[ "$expected" =~ ^[[:xdigit:]]{128}$ ]] || return 1
    printf '%s  %s\n' "$expected" "$target" | sha512sum --check --status
}

download_robolectric_artifact() {
    local version="$1" extension="$2"
    local directory="$ROBOLECTRIC_MAVEN/org/robolectric/android-all-instrumented/$version"
    local filename="android-all-instrumented-$version.$extension"
    local target="$directory/$filename"
    local url="$ROBOLECTRIC_MAVEN_URL/$version/$filename"
    if verify_maven_checksum "$target"; then
        return 0
    fi
    mkdir -p -- "$directory"
    rm -f -- "$target.tmp" "$target.sha512.tmp"
    curl --fail --location --silent --show-error --retry 3 \
        "$url" --output "$target.tmp" || fail "Could not download Robolectric $filename"
    curl --fail --location --silent --show-error --retry 3 \
        "$url.sha512" --output "$target.sha512.tmp" || \
        fail "Could not download Robolectric checksum for $filename"
    mv -- "$target.tmp" "$target"
    mv -- "$target.sha512.tmp" "$target.sha512"
    verify_maven_checksum "$target" || fail "Robolectric checksum mismatch: $filename"
}

say 'Downloading missing Robolectric API 28/29 Maven runtimes (not running tests)'
robolectric_paths=()
for version in "${ROBOLECTRIC_VERSIONS[@]}"; do
    download_robolectric_artifact "$version" pom
    download_robolectric_artifact "$version" jar
    robolectric_paths+=("org/robolectric/android-all-instrumented/$version")
done

say 'Exporting Android SDK components'
sdk_paths=("platforms/$SDK_PLATFORM" "build-tools/$SDK_BUILD_TOOLS")
[[ ! -d "$SDK_ROOT/licenses" ]] || sdk_paths+=(licenses)
[[ ! -d "$SDK_ROOT/platform-tools" ]] || sdk_paths+=(platform-tools)
tar -C "$SDK_ROOT" -czf - "${sdk_paths[@]}" | \
    export_test_asset_archive "$STAGING_DIR/android-sdk.tar.gz" "$MAX_ARCHIVE_BYTES"

say 'Exporting Gradle dependency artifacts and resolution metadata (modules-2)'
tar --exclude='*.lock' --exclude='gc.properties' \
    -C "$CAPTURE_GRADLE_HOME" -czf - caches/modules-2 | \
    export_test_asset_archive "$STAGING_DIR/gradle-deps.tar.gz" "$MAX_ARCHIVE_BYTES"

say 'Exporting Robolectric API 28/29 Maven artifacts'
tar -C "$ROBOLECTRIC_MAVEN" -czf - "${robolectric_paths[@]}" | \
    export_test_asset_archive "$STAGING_DIR/robolectric-maven.tar.gz" "$MAX_ARCHIVE_BYTES"

(
    cd "$STAGING_DIR"
    shopt -s nullglob
    archives=( *.tar.gz *.part-* )
    for name in android-sdk gradle-deps robolectric-maven; do
        [[ -f "$name.tar.gz" || -f "$name.tar.gz.part-000" ]] || \
            fail "Missing required export: $name"
    done
    for archive in "${archives[@]}"; do
        size="$(wc -c < "$archive")"
        ((size < MAX_ARCHIVE_BYTES)) || fail "Output exceeds archive limit: $archive"
    done
    sha256sum -- "${archives[@]}" > SHA256SUMS
)
cat > "$STAGING_DIR/CONTENTS.txt" <<CONTENTS
Project task: :app:resolveAndroidTestAssets (resolves dependencies only)
Platform: Linux x86_64
Gradle distribution: not exported (keep separately; version pinned by Gradle wrapper)
Android SDK: platforms/$SDK_PLATFORM, build-tools/$SDK_BUILD_TOOLS, optional licences/platform-tools
Gradle dependencies: caches/modules-2 (artifacts and plugin resolution metadata; excludes *.lock and gc.properties)
Robolectric: Android 9 / API 28 and Android 10 / API 29 Maven runtime artifacts
Gradle test invocation: not run (no compilation or tests)
Archives under 512000000 bytes: one .tar.gz file (no .part-000 suffix)
Archives at or above 512000000 bytes: .part-* files smaller than 512000000 bytes
Archive reconstruction: concatenate numbered parts in suffix order; use .tar.gz directly otherwise.
Gradle deps destination: \$GRADLE_USER_HOME/caches/modules-2/ (extract into \$GRADLE_USER_HOME/)
Android SDK destination: \$ANDROID_SDK_ROOT/
Robolectric Maven destination: ~/.m2/repository/ (default)
CONTENTS

# Replace only files managed by this script, including older cache archive names.
rm -f -- "$OUTPUT_DIR"/gradle-*-bin.zip.part-* \
    "$OUTPUT_DIR"/android-sdk.tar.gz "$OUTPUT_DIR"/android-sdk.tar.gz.part-* \
    "$OUTPUT_DIR"/gradle-deps.tar.gz "$OUTPUT_DIR"/gradle-deps.tar.gz.part-* \
    "$OUTPUT_DIR"/gradle-cache.tar.gz "$OUTPUT_DIR"/gradle-cache.tar.gz.part-* \
    "$OUTPUT_DIR"/robolectric-maven.tar.gz "$OUTPUT_DIR"/robolectric-maven.tar.gz.part-* \
    "$OUTPUT_DIR"/SHA256SUMS "$OUTPUT_DIR"/CONTENTS.txt
mv -- "$STAGING_DIR"/* "$OUTPUT_DIR"/
say "Export complete: $OUTPUT_DIR"
ls -lh "$OUTPUT_DIR"
if ((WITH_RUST)); then
    "$PROJECT_ROOT/scripts/export-rust-test-assets.sh" --output "$OUTPUT_DIR/rust"
fi
