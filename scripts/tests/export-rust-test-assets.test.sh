#!/usr/bin/env bash
# Exercise Rust export dispatch, offline vendoring, archives, and failure safety.
# All dependency and toolchain commands are mocked; no network is required.
set -euo pipefail

PROJECT_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd -P)"
TEMP_DIR="$(mktemp -d)"
trap 'rm -rf -- "$TEMP_DIR"' EXIT
TEST_PROJECT="$TEMP_DIR/project with spaces"
mkdir -p "$TEST_PROJECT/scripts/lib" "$TEST_PROJECT/app/native-adblock" "$TEMP_DIR/bin"
cp "$PROJECT_ROOT/scripts/export-rust-test-assets.sh" "$TEST_PROJECT/scripts/"
cp "$PROJECT_ROOT/scripts/lib/android-test-asset-archive.sh" "$TEST_PROJECT/scripts/lib/"
cp "$PROJECT_ROOT/app/native-adblock/Cargo.toml" \
    "$PROJECT_ROOT/app/native-adblock/rust-toolchain.toml" \
    "$TEST_PROJECT/app/native-adblock/"
cp -R "$PROJECT_ROOT/app/native-adblock/src" "$TEST_PROJECT/app/native-adblock/"
EXPORTER="$TEST_PROJECT/scripts/export-rust-test-assets.sh"

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
        printf '%s\n' "$@" > "$CAPTURE_DIR/docker-$1.args"
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

bash "$EXPORTER" --help > "$TEMP_DIR/help.log"
grep -F 'Cargo.lock' "$TEMP_DIR/help.log" >/dev/null
[[ ! -e "$TEMP_DIR/docker-run.args" ]]

# Docker runs Linux amd64 on macOS/Apple Silicon and retains only the Cargo
# dependency cache. Source and output mounts are separately isolated.
output="$TEMP_DIR/rust output with spaces"
FAKE_OS=Darwin FAKE_ARCH=arm64 bash "$EXPORTER" --output "$output" > "$TEMP_DIR/mac.log"
assert_contains "$TEMP_DIR/docker-build.args" 'linux/amd64'
assert_contains "$TEMP_DIR/docker-build.args" "$TEST_PROJECT/scripts/Dockerfile.rust-test-assets"
assert_contains "$TEMP_DIR/docker-run.args" 'linux/amd64'
assert_contains "$TEMP_DIR/docker-run.args" "$TEST_PROJECT:/source:ro"
assert_contains "$TEMP_DIR/docker-run.args" "$output:/export"
assert_contains "$TEMP_DIR/docker-run.args" 'webview-kiosk-rust-test-assets-cargo:/var/cache/native-adblock-rust'
# The Docker image installs a version-numbered release, not a stable-* alias.
# The native exporter must discover that release instead of forcing rustup to
# select (and potentially try to download) an uninstalled stable toolchain.
if grep -F 'RUSTUP_TOOLCHAIN=' "$TEMP_DIR/docker-run.args"; then
    echo 'Docker must not override rustup before the installed toolchain is discovered' >&2
    exit 1
fi
grep -F -- '--cargo-bin-dir /usr/local/cargo/bin' "$TEMP_DIR/docker-run.args" >/dev/null
grep -F -- 'exec bash /tmp/project/scripts/export-rust-test-assets.sh' \
    "$TEMP_DIR/docker-run.args" >/dev/null

rm "$TEMP_DIR/docker-build.args" "$TEMP_DIR/docker-run.args"
if FAKE_OS=Darwin bash "$EXPORTER" --rustup-home /tmp/foo > "$TEMP_DIR/override.log" 2>&1; then
    echo 'Docker path must reject native rustup directories' >&2
    exit 1
fi
grep -F 'native Linux options' "$TEMP_DIR/override.log" >/dev/null
[[ ! -e "$TEMP_DIR/docker-run.args" ]]

if FAKE_DOCKER_DOWN=1 bash "$EXPORTER" > "$TEMP_DIR/down.log" 2>&1; then
    echo 'Docker must reject an unavailable daemon' >&2
    exit 1
fi
grep -F 'Docker daemon is unavailable' "$TEMP_DIR/down.log" >/dev/null
if FAKE_DOCKER_OS=windows bash "$EXPORTER" > "$TEMP_DIR/windows.log" 2>&1; then
    echo 'Docker must reject Windows containers' >&2
    exit 1
fi
grep -F 'Docker must be configured to run Linux containers' "$TEMP_DIR/windows.log" >/dev/null

# The Linux path requires a rustup-managed host toolchain, not a generic
# architecture-mismatched compiler or the Android NDK.
CARGO_BIN="$TEMP_DIR/mock-cargo/bin"
RUSTUP_HOME_DIR="$TEMP_DIR/mock-rustup"
TOOLCHAIN_DIR="$RUSTUP_HOME_DIR/toolchains/stable-x86_64-unknown-linux-gnu"
mkdir -p "$CARGO_BIN" "$TOOLCHAIN_DIR/bin" \
    "$TOOLCHAIN_DIR/lib/rustlib/x86_64-unknown-linux-gnu/lib" \
    "$TOOLCHAIN_DIR/lib/rustlib/aarch64-linux-android/lib"
printf 'not needed' > "$TOOLCHAIN_DIR/lib/rustlib/aarch64-linux-android/lib/android.rlib"
printf 'host stdlib' > "$TOOLCHAIN_DIR/lib/rustlib/x86_64-unknown-linux-gnu/lib/libstd.rlib"
printf 'fake installed compiler' > "$TOOLCHAIN_DIR/bin/rustc"
chmod +x "$TOOLCHAIN_DIR/bin/rustc"
cat > "$RUSTUP_HOME_DIR/settings.toml" <<'SETTINGS'
version = "12"
default_toolchain = "stable-x86_64-unknown-linux-gnu"
[overrides]
"/private/sensitive/path" = "nightly"
SETTINGS
cat > "$CARGO_BIN/rustc" <<'RUSTC'
#!/usr/bin/env bash
[[ "${RUSTUP_TOOLCHAIN:-}" == "${FAKE_EXPECTED_TOOLCHAIN:-stable-x86_64-unknown-linux-gnu}" ]] || exit 13
case "$1" in
    -vV) printf 'rustc 1.99.0 (fixture)\nhost: %s\n' "${FAKE_RUST_HOST:-x86_64-unknown-linux-gnu}" ;;
    --version) printf 'rustc 1.99.0 (fixture)\n' ;;
    *) exit 2 ;;
esac
RUSTC
cat > "$CARGO_BIN/rustdoc" <<'RUSTDOC'
#!/usr/bin/env bash
printf 'rustdoc proxy fixture\n'
RUSTDOC
cat > "$CARGO_BIN/rustup" <<'RUSTUP'
#!/usr/bin/env bash
printf 'rustup proxy fixture\n'
RUSTUP
cat > "$CARGO_BIN/cargo" <<'CARGO'
#!/usr/bin/env bash
set -euo pipefail
[[ "${RUSTUP_TOOLCHAIN:-}" == "${FAKE_EXPECTED_TOOLCHAIN:-stable-x86_64-unknown-linux-gnu}" ]] || exit 13
printf '%s\n' "$*" >> "$CAPTURE_DIR/cargo-calls.log"
case "$1" in
    generate-lockfile)
        printf '# fixture lockfile\nversion = 4\n' > Cargo.lock
        ;;
    vendor)
        [[ " $* " == *' --locked '* ]] || exit 3
        [[ " $* " == *' --versioned-dirs '* ]] || exit 4
        [[ -f Cargo.lock ]] || exit 5
        [[ "${FAKE_CARGO_VENDOR_FAIL:-}" != 1 ]] || exit 22
        mkdir -p vendor/example-1.0
        printf 'dependency sources' > vendor/example-1.0/lib.rs
        printf '[source.crates-io]\nreplace-with = "vendored-sources"\n\n[source.vendored-sources]\ndirectory = "vendor"\n'
        ;;
    metadata)
        [[ " $* " == *' --locked '* && " $* " == *' --offline '* ]] || exit 6
        [[ -f Cargo.lock && -f .cargo/config.toml && -f vendor/example-1.0/lib.rs ]] || exit 7
        [[ "${FAKE_CARGO_METADATA_FAIL:-}" != 1 ]] || exit 23
        printf '{}\n'
        ;;
    --version)
        printf 'cargo 1.99.0 (fixture)\n'
        ;;
    *) exit 8 ;;
esac
CARGO
chmod +x "$CARGO_BIN"/*

native_export() {
    FAKE_OS=Linux FAKE_ARCH=x86_64 bash "$EXPORTER" \
        --rustup-home "$RUSTUP_HOME_DIR" --cargo-bin-dir "$CARGO_BIN" \
        --output "$1"
}

native_output="$TEMP_DIR/native rust output with spaces"
native_export "$native_output" > "$TEMP_DIR/native.log"
[[ -f "$native_output/rust-toolchain-linux-x86_64.tar.gz" ]]
[[ -f "$native_output/native-adblock-vendor.tar.gz" ]]
[[ -f "$native_output/SHA256SUMS" && -f "$native_output/CONTENTS.txt" ]]
[[ ! -e "$native_output/rust-toolchain-linux-x86_64.tar.gz.part-000" ]]
[[ ! -e "$native_output/native-adblock-vendor.tar.gz.part-000" ]]
(
    cd "$native_output"
    sha256sum --check --status SHA256SUMS
    tar -tzf rust-toolchain-linux-x86_64.tar.gz > "$TEMP_DIR/toolchain.txt"
    tar -tzf native-adblock-vendor.tar.gz > "$TEMP_DIR/vendor.txt"
)
for path in cargo/bin/cargo cargo/bin/rustc cargo/bin/rustdoc cargo/bin/rustup \
    rustup/settings.toml \
    rustup/toolchains/stable-x86_64-unknown-linux-gnu/bin/rustc \
    rustup/toolchains/stable-x86_64-unknown-linux-gnu/lib/rustlib/x86_64-unknown-linux-gnu/lib/libstd.rlib; do
    assert_contains "$TEMP_DIR/toolchain.txt" "$path"
done
if grep -F 'rustlib/aarch64-linux-android' "$TEMP_DIR/toolchain.txt"; then
    echo 'Android Rust standard libraries were unexpectedly exported' >&2
    exit 1
fi
for path in Cargo.lock .cargo/config.toml vendor/example-1.0/lib.rs; do
    assert_contains "$TEMP_DIR/vendor.txt" "$path"
done
tar -xOzf "$native_output/rust-toolchain-linux-x86_64.tar.gz" \
    rustup/settings.toml > "$TEMP_DIR/exported-settings.toml"
if grep -F '/private/sensitive/path' "$TEMP_DIR/exported-settings.toml"; then
    echo 'Rustup directory overrides leaked into the toolchain archive' >&2
    exit 1
fi
[[ ! -e "$TEST_PROJECT/app/native-adblock/Cargo.lock" ]]
[[ ! -d "$TEST_PROJECT/app/native-adblock/vendor" ]]
grep -F 'Android NDK: not required' "$native_output/CONTENTS.txt" >/dev/null
grep -F 'Toolchain selection: set RUSTUP_TOOLCHAIN=' "$native_output/CONTENTS.txt" >/dev/null
grep -F 'Installed source toolchain: stable-x86_64-unknown-linux-gnu' "$native_output/CONTENTS.txt" >/dev/null
grep -F 'generate-lockfile' "$TEMP_DIR/cargo-calls.log" >/dev/null
grep -F 'vendor --locked --versioned-dirs vendor' "$TEMP_DIR/cargo-calls.log" >/dev/null
grep -F 'metadata --locked --offline --format-version 1' "$TEMP_DIR/cargo-calls.log" >/dev/null

# An existing lockfile is preserved and never regenerated in the staging copy.
printf 'previously locked fixture\n' > "$TEST_PROJECT/app/native-adblock/Cargo.lock"
printf '' > "$TEMP_DIR/cargo-calls.log"
native_export "$native_output" > "$TEMP_DIR/locked.log"
if grep -F 'generate-lockfile' "$TEMP_DIR/cargo-calls.log"; then
    echo 'An existing Cargo.lock was unexpectedly regenerated' >&2
    exit 1
fi
[[ "$(tar -xOzf "$native_output/native-adblock-vendor.tar.gz" Cargo.lock)" == 'previously locked fixture' ]]

# Regression: the official rust:1-bookworm image has ONLY a pinned versioned
# rustup directory and selects it in settings.toml. Cargo and rustc must use
# that installed version, but the portable export must still contain the
# stable-x86_64-unknown-linux-gnu directory and settings expected by restore.
versioned_toolchain='1.99.0-x86_64-unknown-linux-gnu'
mv -- "$TOOLCHAIN_DIR" "$RUSTUP_HOME_DIR/toolchains/$versioned_toolchain"
cat > "$RUSTUP_HOME_DIR/settings.toml" <<SETTINGS
version = "12"
default_toolchain = "$versioned_toolchain"
SETTINGS
FAKE_EXPECTED_TOOLCHAIN="$versioned_toolchain" native_export "$native_output" \
    > "$TEMP_DIR/versioned.log"
grep -F "Installed source toolchain: $versioned_toolchain" \
    "$native_output/CONTENTS.txt" >/dev/null
tar -tzf "$native_output/rust-toolchain-linux-x86_64.tar.gz" \
    > "$TEMP_DIR/versioned-archive.txt"
assert_contains "$TEMP_DIR/versioned-archive.txt" \
    'rustup/toolchains/stable-x86_64-unknown-linux-gnu/bin/rustc'
assert_contains "$TEMP_DIR/versioned-archive.txt" \
    'rustup/toolchains/stable-x86_64-unknown-linux-gnu/lib/rustlib/x86_64-unknown-linux-gnu/lib/libstd.rlib'
if grep -F "$versioned_toolchain" "$TEMP_DIR/versioned-archive.txt"; then
    echo 'Versioned source directory unexpectedly leaked into the archive' >&2
    exit 1
fi
tar -xOzf "$native_output/rust-toolchain-linux-x86_64.tar.gz" \
    rustup/settings.toml > "$TEMP_DIR/versioned-settings.toml"
grep -Fx 'default_toolchain = "stable-x86_64-unknown-linux-gnu"' \
    "$TEMP_DIR/versioned-settings.toml" >/dev/null
restored_toolchain="$TEMP_DIR/restored-versioned-toolchain"
mkdir -p "$restored_toolchain"
tar -xzf "$native_output/rust-toolchain-linux-x86_64.tar.gz" \
    -C "$restored_toolchain"
[[ -x "$restored_toolchain/rustup/toolchains/stable-x86_64-unknown-linux-gnu/bin/rustc" ]]
[[ ! -L "$restored_toolchain/rustup/toolchains/stable-x86_64-unknown-linux-gnu" ]]
[[ -f "$restored_toolchain/rustup/toolchains/stable-x86_64-unknown-linux-gnu/lib/rustlib/x86_64-unknown-linux-gnu/lib/libstd.rlib" ]]
(
    cd "$native_output"
    sha256sum --check --status SHA256SUMS
)

# Do not fall back to a nightly release or attempt a toolchain download when
# there is no installed stable/versioned default. Existing archives remain safe.
cat > "$RUSTUP_HOME_DIR/settings.toml" <<'SETTINGS'
version = "12"
default_toolchain = "nightly-x86_64-unknown-linux-gnu"
SETTINGS
if native_export "$TEMP_DIR/no-stable-toolchain" > "$TEMP_DIR/no-stable.log" 2>&1; then
    echo 'Missing stable/versioned Rust toolchain must fail early' >&2
    exit 1
fi
grep -F 'No installed stable or version-numbered' "$TEMP_DIR/no-stable.log" >/dev/null
[[ ! -f "$TEMP_DIR/no-stable-toolchain/rust-toolchain-linux-x86_64.tar.gz" ]]

mv -- "$RUSTUP_HOME_DIR/toolchains/$versioned_toolchain" "$TOOLCHAIN_DIR"
cat > "$RUSTUP_HOME_DIR/settings.toml" <<'SETTINGS'
version = "12"
default_toolchain = "stable-x86_64-unknown-linux-gnu"
SETTINGS

# A vendor/download failure must not replace a previous successful archive.
previous_hash="$(sha256sum "$native_output/native-adblock-vendor.tar.gz")"
if FAKE_CARGO_VENDOR_FAIL=1 native_export "$native_output" > "$TEMP_DIR/vendor-failed.log" 2>&1; then
    echo 'Expected cargo vendor failure to abort the export' >&2
    exit 1
fi
grep -F 'Cargo could not vendor' "$TEMP_DIR/vendor-failed.log" >/dev/null
[[ "$(sha256sum "$native_output/native-adblock-vendor.tar.gz")" == "$previous_hash" ]]

if FAKE_CARGO_METADATA_FAIL=1 native_export "$TEMP_DIR/metadata-failed" \
    > "$TEMP_DIR/metadata-failed.log" 2>&1; then
    echo 'Expected offline Cargo resolution failure to abort the export' >&2
    exit 1
fi
grep -F 'cannot resolve the exported dependencies offline' "$TEMP_DIR/metadata-failed.log" >/dev/null
[[ ! -e "$TEMP_DIR/metadata-failed/native-adblock-vendor.tar.gz" ]]

if FAKE_RUST_HOST=aarch64-unknown-linux-gnu native_export "$TEMP_DIR/wrong-arch" \
    > "$TEMP_DIR/wrong-arch.log" 2>&1; then
    echo 'Expected incorrect Rust host architecture to abort the export' >&2
    exit 1
fi
grep -F 'Expected x86_64-unknown-linux-gnu rustc' "$TEMP_DIR/wrong-arch.log" >/dev/null

printf 'Rust host asset exporter OS dispatch and offline archive contents: passed\n'
