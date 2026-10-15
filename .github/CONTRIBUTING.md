# Contributing

## Running tests

Run the relevant suites before opening a pull request.

> [!NOTE]
> Android tests run on pull requests, pushes to `main`, and release tags
> through the [tests workflow](./workflows/tests.yaml). Documentation checks
> run through the [docs workflow](./workflows/docs.yaml) only for docs changes
> on pull requests and `main`, or when started manually. Chromium browser tests
> run for browser-relevant docs changes and manual workflow runs.

### Android

Use JetBrains Java 21 (JBRSDK) and the Android SDK required by
`app/build.gradle.kts`. Point `JAVA_HOME` at that JDK and run from the repository
root:

```bash
./gradlew :app:testDebugUnitTest
```

> [!NOTE]
> `gradle/gradle-daemon-jvm.properties` requires both Java 21 and the JetBrains
> vendor. The test and release workflows install a matching JDK before running
> Gradle, so they can use it without provisioning a daemon JDK through Foojay.

Tests live in `app/src/test/java/uk/nktnet/webviewkiosk`, mirroring the production
packages. Pure logic uses JUnit. Android-dependent helpers use Robolectric with
a pinned API 28 runtime. IPv6 validation also runs on API 29 to cover both sides
of the Android URI parsing change. Keyboard tests use a scoped shadow for the
native key-name lookup missing from Robolectric 4.17. The tests use local fixtures
and do not require an emulator or MQTT broker.

### Documentation site

Use Node.js 24 and the pnpm version declared in `docs/package.json`. After
installing the docs dependencies, run:

```bash
cd docs
pnpm test
```

For watch mode, run `pnpm test:watch` from `docs/`.

Tests live under `docs/tests`, mirroring `docs/src`, and use the existing `#/`
subpath imports. The standalone config in `docs/config/vitest.config.ts` keeps
the site build plugins and their network requests out of the test run.
Release-discovery tests inject fixture responses and never contact the
installation repositories.

The suite covers source-specific version selection and fallback behavior,
malformed metadata, HTTP and timeout failures, GitHub token scoping, and request
timeouts.

### Exporting Android test assets

To export Linux x86_64 Android SDK components and Robolectric runtime archives
for use when running Android unit tests, use:

```bash
scripts/export-android-test-assets.sh --output build/android-test-assets
```

On Linux x86_64, the script uses the locally installed Android SDK, JetBrains
JDK 21 and version-matched Gradle executable. On macOS and other architectures,
it automatically builds `scripts/Dockerfile.android-test-assets` and runs the
Linux exporter inside Docker (Docker Desktop on macOS). The image downloads the
pinned Gradle/JBR/SDK components. The export also requires network access to
resolve the project's dependencies on its first run. No host Android SDK,
Java or Gradle setup is necessary in Docker mode. Docker on Apple Silicon runs
an x86_64 container, which can be slower than native Linux due to emulation.

Only `--output` carries over to Docker mode. The native-only `--sdk-root`,
`--gradle-user-home` and `--gradle-bin` options are rejected on other hosts.
Docker uses a disposable copy of the checkout without `local.properties`,
keystores, or host build caches and writes the archives to the requested output
directory. A project-specific Docker volume keeps Gradle caches and Robolectric
runtime downloads between exports. No compiled app outputs or test
results are saved. Initial downloads and x86_64 emulation are still slower than
native execution. Existing exports are replaced by the native exporter rules.

The exporter always includes the Gradle dependencies and resolution metadata
from `caches/modules-2` in `gradle-deps.tar.gz`, alongside
`android-sdk.tar.gz` and `robolectric-maven.tar.gz`. It excludes Gradle lock
files, credentials, keystores, and the Gradle distribution. The script runs
`resolveAndroidTestAssets`, which resolves the required debug/JVM-test and
available Kotlin compiler configurations **without compiling or running
`testDebugUnitTest`**.
Robolectric's pinned API 28/29 runtime jars and their SHA-512 checksums are
retrieved directly from Maven Central. A fresh Gradle home keeps the exported
dependencies specific to this project. Passing `--gradle-user-home` on Linux
reuses previously cached modules and Robolectric runtimes. Docker reuses them
by default through its named volume.

Archives below 512,000,000 bytes have no `.part-000` suffix. Larger archives
are split into numbered `.part-*` files. Concatenate those parts in order
before extraction. `SHA256SUMS` verifies each exported file, and
`CONTENTS.txt` records their installation destinations. Extract
`gradle-deps.tar.gz` into `$GRADLE_USER_HOME` (or `~/.gradle` by default)
before running Gradle with `--offline`.

### Offline native-adblock Rust host tests

To export the Linux x86_64 stable Rust compiler, Cargo, rustup, and the
`app/native-adblock/Cargo.toml` dependencies alongside the Gradle assets, use:

```bash
scripts/export-android-test-assets.sh --with-rust --output build/android-test-assets
```

The existing command **without `--with-rust` remains Gradle-only**. If its
Android/Gradle exports already exist, run only the Rust exporter instead:

```bash
scripts/export-rust-test-assets.sh --output build/android-test-assets/rust
```

On macOS, Apple Silicon, and non-x86_64 Linux, the Rust exporter builds a
small Docker image based on the official `rust:1-bookworm` Linux amd64 image.
It needs Docker and internet access for the initial image/dependency download,
but no host Rust, Java, or Android SDK installation. Its separate named Docker
volume retains downloaded Cargo dependencies. The official Docker image has
a version-numbered stable release, which the exporter reuses without downloading
another toolchain. On Linux x86_64, it uses an existing rustup-managed stable
toolchain or a version-numbered default; neither route downloads the Android
NDK or compiles the native JNI library.

The Rust exporter creates these files in the `rust/` output directory, with
independent `SHA256SUMS` and `CONTENTS.txt` metadata:

- `rust-toolchain-linux-x86_64.tar.gz`: Cargo/rustup proxies and an installed
  Rust host toolchain (`cargo/bin`, `rustup/toolchains`, and sanitized rustup
  settings), **without** registry caches, credentials, or Android targets.
- `native-adblock-vendor.tar.gz`: `Cargo.lock`, `vendor/`, and
  `.cargo/config.toml`. Cargo's `--locked` vendoring preserves an existing
  lockfile, or generates one in a temporary checkout if none exists. `cargo
  metadata --locked --offline` validates the vendored dependency resolution.

As with the Android archives, files over the upload limit are split into
`.part-*` pieces, and `SHA256SUMS` verifies the results. Reassemble any
numbered pieces in order before extraction. To run the parser tests on a Linux
x86_64 host after restoring both archives:

```bash
mkdir -p "$PWD/.rust-offline"
tar -xzf build/android-test-assets/rust/rust-toolchain-linux-x86_64.tar.gz -C "$PWD/.rust-offline"
tar -xzf build/android-test-assets/rust/native-adblock-vendor.tar.gz -C app/native-adblock
export CARGO_HOME="$PWD/.rust-offline/cargo"
export RUSTUP_HOME="$PWD/.rust-offline/rustup"
export RUSTUP_TOOLCHAIN=stable-x86_64-unknown-linux-gnu
export PATH="$CARGO_HOME/bin:$PATH"
cd app/native-adblock
cargo test --locked --offline
```

Run Cargo from the crate directory so it discovers the restored
`app/native-adblock/.cargo/config.toml`; the `--manifest-path` flag alone does
not change Cargo's configuration-file search path. `RUSTUP_TOOLCHAIN` is
important: the checked-in `rust-toolchain.toml` lists
Android targets for APK builds, but the host parser tests use only the Linux
standard library and a normal Linux linker, **not** the Android NDK. Nothing
under `app/native-adblock/vendor/`, `.cargo/config.toml`, or `Cargo.lock` is
committed; these are locally restored test assets.
