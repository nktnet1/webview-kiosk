#!/usr/bin/env bash
# Export a Linux x86_64 host Rust toolchain and offline Cargo dependencies.
# Android targets and the Android NDK are deliberately not installed or exported.
set -euo pipefail

readonly MAX_ARCHIVE_BYTES=512000000
readonly HOST_TRIPLE='x86_64-unknown-linux-gnu'
readonly TOOLCHAIN="stable-$HOST_TRIPLE"
readonly PROJECT_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
source "$PROJECT_ROOT/scripts/lib/android-test-asset-archive.sh"

OUTPUT_ARG="$PROJECT_ROOT/build/android-test-assets/rust"
CARGO_BIN_ARG=""
RUSTUP_HOME_ARG=""
WORK_DIR=""
STAGING_DIR=""

usage() {
    cat <<'HELP'
Usage: scripts/export-rust-test-assets.sh [options]

Export the Linux x86_64 stable Rust compiler/Cargo/rustup and the exact
native-adblock Cargo dependencies, for offline host-side Rust tests.
No Android NDK or Android Rust targets are needed.

On macOS or non-x86_64 hosts, Docker uses the official Linux amd64 Rust image.
On Linux x86_64, a rustup-managed stable toolchain must already be installed.

Options:
  --output DIR           Output directory (default: build/android-test-assets/rust).
  --cargo-bin-dir DIR    rustup proxy binary directory (native Linux only).
  --rustup-home DIR      Installed rustup directory (native Linux only).
  -h, --help             Show this help.

Output:
  rust-toolchain-linux-x86_64.tar.gz   cargo/bin and rustup/toolchains (host only)
  native-adblock-vendor.tar.gz        Cargo.lock, vendor/, .cargo/config.toml
  SHA256SUMS and CONTENTS.txt

Archives of 512,000,000 bytes or more are split into numbered .part-* files.
The source checkout is not modified. Neither tests nor Android builds run.
HELP
}

fail() {
    printf 'Error: %s\n' "$*" >&2
    exit 1
}

say() {
    printf '\n==> %s\n' "$*"
}

cleanup() {
    [[ -z "$STAGING_DIR" ]] || rm -rf -- "$STAGING_DIR"
    [[ -z "$WORK_DIR" ]] || rm -rf -- "$WORK_DIR"
}
trap cleanup EXIT

while (($#)); do
    case "$1" in
        --output|--cargo-bin-dir|--rustup-home)
            (($# >= 2)) || fail "Missing value for $1"
            [[ -n "$2" ]] || fail "Empty value for $1"
            case "$1" in
                --output) OUTPUT_ARG="$2" ;;
                --cargo-bin-dir) CARGO_BIN_ARG="$2" ;;
                --rustup-home) RUSTUP_HOME_ARG="$2" ;;
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

run_in_docker() {
    [[ -z "$CARGO_BIN_ARG" && -z "$RUSTUP_HOME_ARG" ]] || \
        fail '--cargo-bin-dir and --rustup-home are native Linux options; Docker supplies its own Linux toolchain.'
    command -v docker >/dev/null 2>&1 || \
        fail 'Docker is required to export Linux x86_64 Rust assets on this host.'
    local docker_os
    docker_os="$(docker info --format '{{.OSType}}')" || \
        fail 'The Docker daemon is unavailable; start Docker and try again.'
    [[ "$docker_os" == linux ]] || fail 'Docker must be configured to run Linux containers.'
    command -v id >/dev/null 2>&1 || fail 'Missing command: id'

    mkdir -p -- "$OUTPUT_ARG"
    local output_dir
    output_dir="$(cd -- "$OUTPUT_ARG" && pwd -P)"
    local image='webview-kiosk-rust-test-assets:stable-bookworm'
    local cache_volume='webview-kiosk-rust-test-assets-cargo'

    say 'Preparing the Linux x86_64 Rust exporter image (cached after first use)'
    docker build --platform linux/amd64 --tag "$image" \
        --file "$PROJECT_ROOT/scripts/Dockerfile.rust-test-assets" \
        "$PROJECT_ROOT/scripts"

    say "Exporting Rust host test assets to $output_dir"
    docker run --rm --platform linux/amd64 \
        --user "$(id -u):$(id -g)" \
        --env HOME=/tmp/rust-assets-home \
        --env CARGO_HOME=/var/cache/native-adblock-rust/cargo \
        --env RUSTUP_HOME=/usr/local/rustup \
        --env RUSTUP_TOOLCHAIN="$TOOLCHAIN" \
        --volume "$PROJECT_ROOT:/source:ro" \
        --volume "$output_dir:/export" \
        --volume "$cache_volume:/var/cache/native-adblock-rust" \
        --workdir /tmp "$image" bash -c '
            set -euo pipefail
            mkdir -p /tmp/rust-assets-home /tmp/project/app/native-adblock \
                /tmp/project/scripts/lib /var/cache/native-adblock-rust/cargo
            # Only copy the crate inputs and the exporter, never host credentials,
            # target/, .cargo/config.toml, caches, or unrelated project files.
            cp /source/app/native-adblock/Cargo.toml \
               /source/app/native-adblock/rust-toolchain.toml \
               /tmp/project/app/native-adblock/
            cp -R /source/app/native-adblock/src /tmp/project/app/native-adblock/
            if [[ -f /source/app/native-adblock/Cargo.lock ]]; then
                cp /source/app/native-adblock/Cargo.lock /tmp/project/app/native-adblock/
            fi
            cp /source/scripts/export-rust-test-assets.sh /tmp/project/scripts/
            cp /source/scripts/lib/android-test-asset-archive.sh /tmp/project/scripts/lib/
            exec bash /tmp/project/scripts/export-rust-test-assets.sh \
                --cargo-bin-dir /usr/local/cargo/bin \
                --rustup-home /usr/local/rustup \
                --output /export
        '
}

if [[ "$(uname -s)" != Linux || "$(uname -m)" != x86_64 ]]; then
    run_in_docker
    exit 0
fi

for tool in tar split sha256sum mktemp sed grep head; do
    command -v "$tool" >/dev/null 2>&1 || fail "Missing command: $tool"
done
[[ -f "$PROJECT_ROOT/app/native-adblock/Cargo.toml" && \
   -f "$PROJECT_ROOT/app/native-adblock/rust-toolchain.toml" && \
   -d "$PROJECT_ROOT/app/native-adblock/src" ]] || \
    fail 'Native adblock Cargo manifest, toolchain declaration, or sources are missing.'

RUSTUP_DIR="${RUSTUP_HOME_ARG:-${RUSTUP_HOME:-$HOME/.rustup}}"
[[ -d "$RUSTUP_DIR" ]] || fail "Rustup home does not exist: $RUSTUP_DIR"
RUSTUP_DIR="$(cd -- "$RUSTUP_DIR" && pwd -P)"
if [[ -n "$CARGO_BIN_ARG" ]]; then
    CARGO_BIN_DIR="$CARGO_BIN_ARG"
else
    command -v cargo >/dev/null 2>&1 || fail 'cargo is required on native Linux.'
    CARGO_BIN_DIR="$(dirname -- "$(command -v cargo)")"
fi
[[ -d "$CARGO_BIN_DIR" ]] || fail "Cargo binary directory does not exist: $CARGO_BIN_DIR"
CARGO_BIN_DIR="$(cd -- "$CARGO_BIN_DIR" && pwd -P)"
[[ -d "$RUSTUP_DIR/toolchains/$TOOLCHAIN" && \
   -x "$RUSTUP_DIR/toolchains/$TOOLCHAIN/bin/rustc" ]] || \
    fail "Rustup toolchain $TOOLCHAIN is missing from $RUSTUP_DIR. Install the stable host toolchain first."
for bin in cargo rustup rustc rustdoc; do
    [[ -x "$CARGO_BIN_DIR/$bin" ]] || \
        fail "Rustup proxy $bin is missing from $CARGO_BIN_DIR"
done
[[ -f "$RUSTUP_DIR/settings.toml" ]] || \
    fail "Rustup settings.toml is missing from $RUSTUP_DIR"

# An override prevents rust-toolchain.toml from installing its four Android
# targets. Host tests and cargo vendor require only the host standard library.
export RUSTUP_TOOLCHAIN="$TOOLCHAIN"
rust_host="$("$CARGO_BIN_DIR/rustc" -vV | sed -n 's/^host: //p')" || \
    fail "Cannot query host toolchain $TOOLCHAIN"
[[ "$rust_host" == "$HOST_TRIPLE" ]] || \
    fail "Expected $HOST_TRIPLE rustc, found: $rust_host"

mkdir -p -- "$PROJECT_ROOT/build" "$OUTPUT_ARG"
OUTPUT_DIR="$(cd -- "$OUTPUT_ARG" && pwd -P)"
WORK_DIR="$(mktemp -d "$PROJECT_ROOT/build/.rust-test-export.XXXXXXXX")"
STAGING_DIR="$(mktemp -d "$OUTPUT_DIR/.staging.XXXXXXXX")"
CRATE_COPY="$WORK_DIR/native-adblock"
mkdir -p -- "$CRATE_COPY"
cp -- "$PROJECT_ROOT/app/native-adblock/Cargo.toml" \
    "$PROJECT_ROOT/app/native-adblock/rust-toolchain.toml" "$CRATE_COPY/"
cp -R -- "$PROJECT_ROOT/app/native-adblock/src" "$CRATE_COPY/"
if [[ -f "$PROJECT_ROOT/app/native-adblock/Cargo.lock" ]]; then
    cp -- "$PROJECT_ROOT/app/native-adblock/Cargo.lock" "$CRATE_COPY/"
fi

say 'Resolving and vendoring native-adblock Cargo dependencies'
(
    cd "$CRATE_COPY"
    if [[ ! -f Cargo.lock ]]; then
        "$CARGO_BIN_DIR/cargo" generate-lockfile || fail 'Cargo could not generate the native-adblock lockfile.'
    fi
    mkdir -p .cargo
    "$CARGO_BIN_DIR/cargo" vendor --locked --versioned-dirs vendor > .cargo/config.toml || \
        fail 'Cargo could not vendor the locked native-adblock dependencies.'
    [[ -s .cargo/config.toml && -d vendor && -f Cargo.lock ]] || \
        fail 'Cargo vendoring did not produce the expected source config and lockfile.'
    "$CARGO_BIN_DIR/cargo" metadata --locked --offline --format-version 1 > /dev/null || \
        fail 'Cargo cannot resolve the exported dependencies offline.'
)

say 'Exporting the Linux host Rust toolchain (excluding registries and credentials)'
TOOLCHAIN_LAYOUT="$WORK_DIR/toolchain"
mkdir -p "$TOOLCHAIN_LAYOUT/cargo/bin" "$TOOLCHAIN_LAYOUT/rustup/toolchains"
# Only ship the rustup proxies needed by host tests. Other executables in
# CARGO_BIN_DIR may be unrelated user-installed tools or private wrappers.
for bin in cargo rustup rustc rustdoc; do
    ln -s -- "$CARGO_BIN_DIR/$bin" "$TOOLCHAIN_LAYOUT/cargo/bin/$bin"
done
ln -s -- "$RUSTUP_DIR/toolchains/$TOOLCHAIN" \
    "$TOOLCHAIN_LAYOUT/rustup/toolchains/$TOOLCHAIN"
# Do not export rustup's user-defined directory overrides or personal settings.
settings_version="$(sed -n 's/^version = "\([^"]*\)"$/\1/p' \
    "$RUSTUP_DIR/settings.toml" | head -n 1)"
[[ "$settings_version" =~ ^[0-9]+$ ]] || fail 'Cannot determine the rustup settings format version.'
printf 'version = "%s"\ndefault_host_triple = "%s"\ndefault_toolchain = "%s"\nprofile = "minimal"\n' \
    "$settings_version" "$HOST_TRIPLE" "$TOOLCHAIN" > "$TOOLCHAIN_LAYOUT/rustup/settings.toml"
tar --dereference \
    --exclude="rustup/toolchains/$TOOLCHAIN/lib/rustlib/aarch64-linux-android" \
    --exclude="rustup/toolchains/$TOOLCHAIN/lib/rustlib/armv7-linux-androideabi" \
    --exclude="rustup/toolchains/$TOOLCHAIN/lib/rustlib/i686-linux-android" \
    --exclude="rustup/toolchains/$TOOLCHAIN/lib/rustlib/x86_64-linux-android" \
    -C "$TOOLCHAIN_LAYOUT" -czf - \
    cargo/bin rustup/toolchains/"$TOOLCHAIN" rustup/settings.toml | \
    export_test_asset_archive "$STAGING_DIR/rust-toolchain-linux-x86_64.tar.gz" "$MAX_ARCHIVE_BYTES"

say 'Exporting vendored Cargo dependencies and their lockfile'
tar -C "$CRATE_COPY" -czf - Cargo.lock .cargo/config.toml vendor | \
    export_test_asset_archive "$STAGING_DIR/native-adblock-vendor.tar.gz" "$MAX_ARCHIVE_BYTES"

(
    cd "$STAGING_DIR"
    shopt -s nullglob
    archives=( *.tar.gz *.part-* )
    for name in rust-toolchain-linux-x86_64 native-adblock-vendor; do
        [[ -f "$name.tar.gz" || -f "$name.tar.gz.part-000" ]] || \
            fail "Missing required export: $name"
    done
    for archive in "${archives[@]}"; do
        (( $(wc -c < "$archive") < MAX_ARCHIVE_BYTES )) || \
            fail "Output exceeds archive limit: $archive"
    done
    sha256sum -- "${archives[@]}" > SHA256SUMS
)
cat > "$STAGING_DIR/CONTENTS.txt" <<CONTENTS
Platform: Linux x86_64 ($HOST_TRIPLE)
Rust channel: $TOOLCHAIN (minimal host toolchain; no Android targets)
Rust version: $("$CARGO_BIN_DIR/rustc" --version)
Cargo version: $("$CARGO_BIN_DIR/cargo" --version)
Crate: app/native-adblock/Cargo.toml
Cargo dependencies: vendored from Cargo.lock; verified with cargo metadata --locked --offline
Cargo tests: not run (no compilation or tests)
Android NDK: not required, installed, or exported
Archives under 512000000 bytes: one .tar.gz file (no .part-000 suffix)
Archives at or above 512000000 bytes: .part-* files smaller than 512000000 bytes
Archive reconstruction: concatenate numbered parts in suffix order; use .tar.gz directly otherwise.
Rust toolchain destination: extract into a writable directory, then set CARGO_HOME=<dir>/cargo and RUSTUP_HOME=<dir>/rustup and PATH=\$CARGO_HOME/bin:\$PATH
Cargo dependencies destination: extract into app/native-adblock/; cd app/native-adblock before running cargo test --offline --locked
Toolchain selection: set RUSTUP_TOOLCHAIN=$TOOLCHAIN to avoid installing the Android targets listed in rust-toolchain.toml
CONTENTS

# Only replace our own assets; never erase the Android/Gradle exports nearby.
rm -f -- "$OUTPUT_DIR"/rust-toolchain-linux-x86_64.tar.gz \
    "$OUTPUT_DIR"/rust-toolchain-linux-x86_64.tar.gz.part-* \
    "$OUTPUT_DIR"/native-adblock-vendor.tar.gz \
    "$OUTPUT_DIR"/native-adblock-vendor.tar.gz.part-* \
    "$OUTPUT_DIR"/SHA256SUMS "$OUTPUT_DIR"/CONTENTS.txt
mv -- "$STAGING_DIR"/* "$OUTPUT_DIR"/
say "Rust export complete: $OUTPUT_DIR"
ls -lh "$OUTPUT_DIR"
