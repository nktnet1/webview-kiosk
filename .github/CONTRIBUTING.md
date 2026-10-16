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

#### Formatting

Run `./gradlew spotlessApply` to format Kotlin sources, tests, and Gradle Kotlin
scripts in the app and `adblock` module. Run `./gradlew checkFormatting` to check
formatting and require braces on every `if`/`else` branch, including short guards
and expressions. These checks also run in CI and through the Kotlin pre-commit
hook. The root `check` task includes them as well.

Spotless uses pinned ktfmt with the Kotlin language style, four-space indentation,
and an 85-character line-width target. `.editorconfig` records the same editor
preferences. Wrapping is best effort: imports, package names, long URLs, string
literals, and embedded scripts may exceed 85 characters when splitting them
would change their meaning. Detekt checks only the brace policy; add missing
braces before running the formatter. Keep normal `else if` chains.

```kotlin
if (condition) {
    return "yes"
} else {
    return "no"
}
```

#### Tests

Use JetBrains Java 21 (JBRSDK) and the Android SDK required by
`app/build.gradle.kts`. Point `JAVA_HOME` at that JDK and run from the repository
root:

```bash
./gradlew :adblock:test :app:testDebugUnitTest
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

The parser, matcher, and compiled-cache tests live in `adblock/src/test/kotlin`.
They use JUnit and OkHttp's JVM public-suffix data, without Android or Robolectric.
Run just those tests with `./gradlew :adblock:test --configure-on-demand`.

Run the asset-exporter, CI runner, and cosmetic page-script regressions with
Python 3.10+ and Node.js 24 (no npm packages required):

```bash
sh scripts/ci/test-local.sh checks
```

The cosmetic script tests use a DOM/bridge fixture to exercise batching, dynamic
elements, CSS rejection, retries, and cleanup. They do not replace a real WebView
test for CSS rendering, CSP, frames, or provider feature support.

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

### Exporting offline test and lint assets

To export Linux x86_64 Android SDK, Gradle dependencies (including lint), and
Robolectric runtime archives for offline Android tests, use:

```bash
python3 scripts/export-android-test-assets.py --output build/android-test-assets
```

To include the original Gradle, JetBrains JBR 21, and Android SDK command-line
tools for an offline environment, run:

```bash
python3 scripts/export-android-test-assets.py --download-tools
```

The `--assets all` option is accepted as a compatibility alias for the default
Android-only export. Neither option downloads Node.js, pnpm, or docs assets.

The command requires Python 3.10 or newer and uses only the standard library.
On Linux x86_64, the script uses the locally installed Android SDK, JetBrains
JDK 21, and version-matched Gradle executable, or the optional downloads below.
On macOS and other architectures,
it automatically builds `scripts/Dockerfile.android-test-assets` and runs the
Linux exporter inside Docker (Docker Desktop on macOS). The image downloads the
pinned Gradle/JBR/SDK components. The export also requires network access to
resolve the project's dependencies on its first run. No host Android SDK,
Java or Gradle setup is necessary in Docker mode. Docker on Apple Silicon runs
an x86_64 container, which can be slower than native Linux due to emulation.

The Docker base image and exporter image tag use the Gradle version from
`gradle/wrapper/gradle-wrapper.properties`. Updating the wrapper also updates
the container's Gradle version; no separate version pin is needed in the
Dockerfile, exporter, or tests. The Dockerfile has a valid `gradle:jdk21-jammy`
default for direct builds; the exporter overrides it with the wrapper's exact
version.

The download flags and `--output` carry over to Docker mode. The
native-only `--sdk-root`, `--gradle-user-home`, and `--gradle-bin` options are
rejected on other hosts.
Docker uses a disposable copy of the checkout without `local.properties`,
keystores, or host build caches and writes the archives to the requested output
directory. A project-specific Docker volume keeps Gradle caches and Robolectric
runtime downloads between exports. No compiled app outputs or test
results are saved. Initial downloads and x86_64 emulation are still slower than
native execution. Existing exports are replaced by the native exporter rules.

The Android export includes the Gradle dependencies and resolution metadata
from `caches/modules-2` in `gradle-deps.tar.gz`, alongside
`android-sdk.tar.gz` and `robolectric-maven.tar.gz`. It excludes Gradle lock
files, credentials, and keystores. Original tool distributions are optional
downloads kept separately in `tools/`. The script runs
`resolveAndroidTestAssets`, which resolves debug/JVM-test dependencies,
`androidLintTool` (including `com.android.tools.lint:lint-gradle` and its
transitive dependencies), the AGP-matched Linux AAPT2, available Kotlin compiler
configurations, and ktfmt/Detekt dependencies **without compiling, running tests,
executing lint, or formatting files**. Spotless's Gradle plugin is also cached
during configuration, so `spotlessApply` and `checkFormatting` work offline with
a fresh export.
The AAPT2 version is matched to AGP 9.4.1 in `gradle/libs.versions.toml`.
The SDK archive includes `platform-tools` (`adb`), installed via `sdkmanager`
when missing.
Robolectric's pinned API 28/29 runtime jars and their SHA-512 checksums are
retrieved directly from Maven Central. A fresh Gradle home keeps the exported
dependencies specific to this project. Passing `--gradle-user-home` on Linux
reuses previously cached modules and Robolectric runtimes. Docker reuses them
by default through its named volume.

All exported archives at or below **180,000,000 bytes (180 MB, decimal)**
have no `.part-000` suffix. Larger archives, including Gradle dependencies,
SDKs and toolchains, are streamed into numbered
`<filename>.part-000`, `.part-001`, and subsequent files of at most 180 MB.
This includes the downloaded ZIP/tar archives. Generated archives are streamed
without buffering a complete oversized archive in memory or on disk.
Concatenate those parts in order before extraction. `SHA256SUMS` verifies each
exported file, and
`CONTENTS.txt` records their installation destinations. Extract
`gradle-deps.tar.gz` into `$GRADLE_USER_HOME` (or `~/.gradle` by default)
before running Gradle with `--offline`.

#### Optional tool downloads

Use `--download-tools` to download all three Linux x86_64 tool packages, or
select any combination of:

- `--download-gradle`: the exact ZIP URL, version, and SHA-256 from
  `gradle/wrapper/gradle-wrapper.properties`. A valid `distributionSha256Sum`
  is required. This avoids accidentally using a milestone in a stable build.
- `--download-jbr`: the pinned JetBrains JDK 21 SDK, matching the Docker image.
- `--download-android-tools`: the pinned Android command-line tools ZIP,
  matching the Docker image.

The original packages are exported into `<output>/tools/`, with independent
`SHA256SUMS` and `CONTENTS.txt` files, and the same 180 MB splitting rule.
`--download-cache DIR` changes the cache location (default:
`build/test-asset-downloads`). Cached originals may exceed 180 MB and remain
outside `tools/`; every cache entry is checksum-verified before reuse. Failed
downloads or checksum mismatches preserve previous exports.

To download and export packages on any host without Java, an SDK, or
Docker, use download-only mode:

```bash
python3 scripts/export-android-test-assets.py --download-tools --download-only
```

Without `--download-only`, the selected downloads accompany the normal asset
export. On native Linux, downloaded Gradle is used automatically unless
`--gradle-bin` is explicit, and downloaded JBR supplies Java for that invocation.
Extracted tools are temporary; global installations and shell settings are
not changed. Android tools can initialize an SDK under the download cache when
no SDK is selected. To accept SDK licences noninteractively when installing
the pinned SDK packages, explicitly pass `--accept-sdk-licenses`:

```bash
python3 scripts/export-android-test-assets.py --download-tools --accept-sdk-licenses
```

Only Linux x86_64 packages are downloaded, including in download-only mode on
macOS or Windows. Normal asset export on those hosts still uses Docker.

### Kotlin content-blocking tests

The content blocker is Kotlin and uses the existing OkHttp dependency. The Gradle
JVM suite includes parser/matcher and storage/integration regressions. See
[`docs/content-blocking.md`](../docs/content-blocking.md) for supported syntax,
limits, runtime storage, and device checks.
