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

# JetBrains hosts JBR binaries on its distribution CDN, not GitHub release
# attachments. This regression guard catches the broken URL before a Docker
# build spends time downloading the base image and installing system packages.
dockerfile="$PROJECT_ROOT/scripts/Dockerfile.android-test-assets"
grep -Fq 'https://cache-redirector.jetbrains.com/intellij-jbr/${JBR_ARCHIVE}' "$dockerfile"
if grep -Fq 'github.com/JetBrains/JetBrainsRuntime/releases/download' "$dockerfile"; then
    echo 'The JBR SDK must be downloaded from the JetBrains distribution CDN' >&2
    exit 1
fi

# Exercise streaming archive output with a tiny test limit. The production
# limit is 512,000,000 bytes: a single part must be renamed to .tar.gz, while
# an archive equal to the limit must use numbered parts strictly below it.
source "$PROJECT_ROOT/scripts/lib/android-test-asset-archive.sh"
archives="$TEMP_DIR/archives with spaces"
mkdir -p "$archives"
printf '12345678901' | export_test_asset_archive "$archives/small.tar.gz" 12
[[ -f "$archives/small.tar.gz" && ! -e "$archives/small.tar.gz.part-000" ]]
[[ "$(cat "$archives/small.tar.gz")" == '12345678901' ]]

printf '123456789012' | export_test_asset_archive "$archives/boundary.tar.gz" 12
[[ ! -e "$archives/boundary.tar.gz" ]]
boundary_parts=( "$archives"/boundary.tar.gz.part-* )
[[ ${#boundary_parts[@]} -eq 2 ]]
[[ "$(cat "${boundary_parts[@]}")" == '123456789012' ]]

printf '12345678901234567890123' | export_test_asset_archive "$archives/large.tar.gz" 12
[[ ! -e "$archives/large.tar.gz" ]]
large_parts=( "$archives"/large.tar.gz.part-* )
[[ ${#large_parts[@]} -eq 3 ]]
[[ "$(cat "${large_parts[@]}")" == '12345678901234567890123' ]]
for part in "${boundary_parts[@]}" "${large_parts[@]}"; do
    (( $(wc -c < "$part") < 12 )) || {
        printf 'Archive part exceeded the test limit: %s\n' "$part" >&2
        exit 1
    }
done
(
    cd "$archives"
    sha256sum -- *.tar.gz *.part-* > SHA256SUMS
    sha256sum --check --status SHA256SUMS
)
if printf '' | export_test_asset_archive "$archives/empty.tar.gz" 12 > /dev/null 2>&1; then
    echo 'Expected an empty archive stream to fail' >&2
    exit 1
fi

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
assert_contains "$TEMP_DIR/run.args" 'webview-kiosk-android-test-assets-deps:/var/cache/android-test-assets'
assert_contains "$TEMP_DIR/run.args" 'HOME=/tmp/android-assets-home'
grep -F -- '--sdk-root /opt/android-sdk' "$TEMP_DIR/run.args" >/dev/null
grep -F -- '--gradle-user-home /var/cache/android-test-assets/gradle' "$TEMP_DIR/run.args" >/dev/null

# Opting in to Rust runs the separate Linux amd64 exporter after Gradle. The
# default path above remains Gradle-only and does not pull the Rust image.
FAKE_OS=Darwin FAKE_ARCH=arm64 bash "$EXPORTER" --with-rust --output "$output" \
    > "$TEMP_DIR/combined.log"
[[ -d "$output/rust" ]]
assert_contains "$TEMP_DIR/build.args" "$PROJECT_ROOT/scripts/Dockerfile.rust-test-assets"
assert_contains "$TEMP_DIR/run.args" "$output/rust:/export"
assert_contains "$TEMP_DIR/run.args" 'RUSTUP_TOOLCHAIN=stable-x86_64-unknown-linux-gnu'

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

# On Linux, run the full native exporter with a tiny SDK, fake Gradle
# dependency resolution, and fake Maven downloads. No tests are executed.
if [[ "$OSTYPE" == linux* ]]; then
    sdk="$TEMP_DIR/mock-sdk"
    output_dir="$TEMP_DIR/native output with spaces"
    mkdir -p "$sdk/platforms/android-37.0" "$sdk/build-tools/36.0.0" "$output_dir"
    printf 'sdk fixture' > "$sdk/platforms/android-37.0/android.jar"
    printf 'build fixture' > "$sdk/build-tools/36.0.0/source.properties"
    printf 'obsolete' > "$output_dir/gradle-cache.tar.gz.part-000"
    printf 'obsolete' > "$output_dir/gradle-cache.tar.gz"
    printf 'obsolete' > "$output_dir/gradle-deps.tar.gz.part-000"
    printf 'obsolete' > "$output_dir/gradle-deps.tar.gz"
    printf 'obsolete' > "$output_dir/android-sdk.tar.gz.part-000"
    printf 'obsolete' > "$output_dir/robolectric-maven.tar.gz.part-000"
    printf 'keep' > "$output_dir/unrelated.txt"
    cat > "$TEMP_DIR/bin/mock-gradle" <<'GRADLE'
#!/usr/bin/env bash
set -euo pipefail
if [[ "${1:-}" == --version ]]; then
    printf 'Gradle 9.8.0\n'
    exit 0
fi
printf '%s\n' "$@" > "$CAPTURE_DIR/gradle-task.args"
[[ " $* " == *' :app:resolveAndroidTestAssets '* ]] || {
    printf 'Unexpected Gradle task: %s\n' "$*" >&2
    exit 1
}
[[ " $* " != *':app:testDebugUnitTest'* ]] || exit 1
[[ " $* " != *' --rerun-tasks '* ]] || exit 1
[[ " $* " == *' --init-script '* ]] || exit 1
[[ "${FAKE_GRADLE_FAILURE:-}" != 1 ]] || exit 23
if [[ "${SKIP_FAKE_DEPS:-}" != 1 ]]; then
    modules="$GRADLE_USER_HOME/caches/modules-2"
    mkdir -p "$modules/files-2.1/example/tool/1.0/hash" "$modules/metadata-2.107"
    printf 'dependency' > "$modules/files-2.1/example/tool/1.0/hash/tool.jar"
    printf 'metadata' > "$modules/metadata-2.107/module-artifact.bin"
    printf 'excluded' > "$modules/modules-2.lock"
    printf 'excluded' > "$modules/gc.properties"
    printf 'credentials not for export' > "$GRADLE_USER_HOME/gradle.properties"
    mkdir -p "$GRADLE_USER_HOME/caches/transforms-4"
    printf 'excluded' > "$GRADLE_USER_HOME/caches/transforms-4/cache.bin"
fi
GRADLE
    cat > "$TEMP_DIR/bin/curl" <<'CURL'
#!/usr/bin/env bash
set -euo pipefail
url=''
output=''
while (($#)); do
    case "$1" in
        --output)
            output="$2"
            shift 2
            ;;
        https://*)
            url="$1"
            shift
            ;;
        *) shift ;;
    esac
done
[[ -n "$url" && -n "$output" ]] || exit 2
[[ "$url" == 'https://repo.maven.apache.org/maven2/org/robolectric/android-all-instrumented/'* ]] || exit 3
printf '%s\n' "$url" >> "$CAPTURE_DIR/curl-requests.log"
[[ "${FAKE_CURL_FAILURE:-}" != 1 ]] || exit 22
case "$url" in
    *.jar.sha512) printf 'test runtime' | sha512sum | cut -d ' ' -f 1 > "$output" ;;
    *.pom.sha512) printf 'test pom' | sha512sum | cut -d ' ' -f 1 > "$output" ;;
    *.jar) printf 'test runtime' > "$output" ;;
    *.pom) printf 'test pom' > "$output" ;;
    *) exit 4 ;;
esac
CURL
    chmod +x "$TEMP_DIR/bin/mock-gradle" "$TEMP_DIR/bin/curl"
    FAKE_OS=Linux FAKE_ARCH=x86_64 bash "$EXPORTER" \
        --sdk-root "$sdk" --gradle-bin "$TEMP_DIR/bin/mock-gradle" \
        --output "$output_dir" > "$TEMP_DIR/export.log"
    [[ -f "$output_dir/android-sdk.tar.gz" && -f "$output_dir/gradle-deps.tar.gz" && -f "$output_dir/robolectric-maven.tar.gz" ]]
    assert_contains "$TEMP_DIR/gradle-task.args" ':app:resolveAndroidTestAssets'
    assert_contains "$TEMP_DIR/gradle-task.args" "$PROJECT_ROOT/scripts/resolve-android-test-assets.init.gradle"
    grep -F 'Gradle test invocation: not run' "$output_dir/CONTENTS.txt" >/dev/null
    [[ ! -e "$output_dir/android-sdk.tar.gz.part-000" ]]
    [[ ! -e "$output_dir/gradle-deps.tar.gz.part-000" ]]
    [[ ! -e "$output_dir/robolectric-maven.tar.gz.part-000" ]]
    [[ ! -e "$output_dir/gradle-cache.tar.gz" ]]
    [[ ! -e "$output_dir/gradle-cache.tar.gz.part-000" ]]
    [[ "$(cat "$output_dir/unrelated.txt")" == keep ]]
    grep -F 'Gradle dependencies: caches/modules-2' "$output_dir/CONTENTS.txt" >/dev/null
    grep -F 'Gradle deps destination: $GRADLE_USER_HOME/caches/modules-2/' "$output_dir/CONTENTS.txt" >/dev/null
    grep -F 'gradle-deps.tar.gz' "$output_dir/SHA256SUMS" >/dev/null
    [[ "$(wc -l < "$TEMP_DIR/curl-requests.log")" == 8 ]]
    (
        cd "$output_dir"
        sha256sum --check --status SHA256SUMS
        tar -tzf android-sdk.tar.gz >/dev/null
        tar -tzf gradle-deps.tar.gz > "$TEMP_DIR/gradle-deps-contents.txt"
        tar -tzf robolectric-maven.tar.gz >/dev/null
    )
    grep -Fx 'caches/modules-2/files-2.1/example/tool/1.0/hash/tool.jar' \
        "$TEMP_DIR/gradle-deps-contents.txt" >/dev/null
    grep -Fx 'caches/modules-2/metadata-2.107/module-artifact.bin' \
        "$TEMP_DIR/gradle-deps-contents.txt" >/dev/null
    tar -tzf "$output_dir/robolectric-maven.tar.gz" > "$TEMP_DIR/robolectric-contents.txt"
    for version in 9-robolectric-4913185-2-i7 10-robolectric-5803371-i7; do
        grep -F "android-all-instrumented-$version.jar" "$TEMP_DIR/robolectric-contents.txt" >/dev/null
        grep -F "android-all-instrumented-$version.pom" "$TEMP_DIR/robolectric-contents.txt" >/dev/null
    done
    if grep -Eq '(^|/)([^/]*\.lock|gc\.properties)$|gradle\.properties|transforms-4' \
        "$TEMP_DIR/gradle-deps-contents.txt"; then
        echo 'Gradle export included lock files, credentials or unrelated caches' >&2
        exit 1
    fi

    # A caller may reuse a cache on native Linux. Verify those pre-existing
    # dependencies are included, rather than exporting only newly downloaded
    # modules, while unrelated Gradle home files remain excluded.
    existing_home="$TEMP_DIR/existing-gradle-home"
    existing_output="$TEMP_DIR/existing-home-output"
    mkdir -p "$existing_home/caches/modules-2/files-2.1/preexisting/library/1.0/hash"
    printf 'cached dependency' > \
        "$existing_home/caches/modules-2/files-2.1/preexisting/library/1.0/hash/library.jar"
    FAKE_OS=Linux FAKE_ARCH=x86_64 bash "$EXPORTER" \
        --sdk-root "$sdk" --gradle-bin "$TEMP_DIR/bin/mock-gradle" \
        --gradle-user-home "$existing_home" --output "$existing_output" \
        > "$TEMP_DIR/existing-home.log"
    tar -tzf "$existing_output/gradle-deps.tar.gz" > "$TEMP_DIR/existing-deps-contents.txt"
    grep -Fx 'caches/modules-2/files-2.1/preexisting/library/1.0/hash/library.jar' \
        "$TEMP_DIR/existing-deps-contents.txt" >/dev/null

    # Re-running against the same Gradle home reuses the Maven downloads.
    previous_downloads="$(wc -l < "$TEMP_DIR/curl-requests.log")"
    FAKE_OS=Linux FAKE_ARCH=x86_64 bash "$EXPORTER" \
        --sdk-root "$sdk" --gradle-bin "$TEMP_DIR/bin/mock-gradle" \
        --gradle-user-home "$existing_home" --output "$existing_output" \
        > "$TEMP_DIR/reused-home.log"
    [[ "$(wc -l < "$TEMP_DIR/curl-requests.log")" == "$previous_downloads" ]]

    # Corrupted cache entries are fetched again rather than exported.
    cached_jar="$existing_home/robolectric-maven/org/robolectric/android-all-instrumented/9-robolectric-4913185-2-i7/android-all-instrumented-9-robolectric-4913185-2-i7.jar"
    printf 'corrupted' > "$cached_jar"
    FAKE_OS=Linux FAKE_ARCH=x86_64 bash "$EXPORTER" \
        --sdk-root "$sdk" --gradle-bin "$TEMP_DIR/bin/mock-gradle" \
        --gradle-user-home "$existing_home" --output "$existing_output" \
        > "$TEMP_DIR/refreshed-home.log"
    [[ "$(wc -l < "$TEMP_DIR/curl-requests.log")" == "$((previous_downloads + 2))" ]]

    # Even if a fake Gradle task populates Robolectric, the export must fail
    # rather than claim to include dependencies when modules-2 is absent.
    if FAKE_OS=Linux FAKE_ARCH=x86_64 SKIP_FAKE_DEPS=1 bash "$EXPORTER" \
        --sdk-root "$sdk" --gradle-bin "$TEMP_DIR/bin/mock-gradle" \
        --output "$TEMP_DIR/missing-deps-output" \
        > "$TEMP_DIR/missing-deps.log" 2>&1; then
        echo 'Expected missing Gradle dependencies to abort the export' >&2
        exit 1
    fi
    grep -F 'Gradle dependency artifacts were not populated' "$TEMP_DIR/missing-deps.log" >/dev/null
    [[ ! -f "$TEMP_DIR/missing-deps-output/gradle-deps.tar.gz" ]]

    # Fail early on Gradle resolution errors, even if a cache already exists.
    if FAKE_OS=Linux FAKE_ARCH=x86_64 FAKE_GRADLE_FAILURE=1 bash "$EXPORTER" \
        --sdk-root "$sdk" --gradle-bin "$TEMP_DIR/bin/mock-gradle" \
        --output "$TEMP_DIR/resolve-failed-output" \
        > "$TEMP_DIR/resolve-failed.log" 2>&1; then
        echo 'Expected dependency resolution failure to abort the export' >&2
        exit 1
    fi
    grep -F 'Gradle could not resolve the required Android JVM test dependencies' \
        "$TEMP_DIR/resolve-failed.log" >/dev/null
    [[ ! -f "$TEMP_DIR/resolve-failed-output/gradle-deps.tar.gz" ]]

    # Failed downloads must not produce a purportedly complete export.
    if FAKE_OS=Linux FAKE_ARCH=x86_64 FAKE_CURL_FAILURE=1 bash "$EXPORTER" \
        --sdk-root "$sdk" --gradle-bin "$TEMP_DIR/bin/mock-gradle" \
        --output "$TEMP_DIR/download-failed-output" \
        > "$TEMP_DIR/download-failed.log" 2>&1; then
        echo 'Expected a failed Maven download to abort the export' >&2
        exit 1
    fi
    grep -F 'Could not download Robolectric' "$TEMP_DIR/download-failed.log" >/dev/null
    [[ ! -f "$TEMP_DIR/download-failed-output/robolectric-maven.tar.gz" ]]
fi

# The native Linux x86_64 code path never shells out to Docker.
FAKE_OS=Linux FAKE_ARCH=x86_64 bash "$EXPORTER" --gradle-bin /missing/gradle \
    > "$TEMP_DIR/native.log" 2>&1 || true
[[ ! -e "$TEMP_DIR/build.args" && ! -e "$TEMP_DIR/run.args" ]]
printf 'Android test asset exporter OS dispatch and archive boundaries: passed\n'
