# Contributing

## Running tests

Run the relevant suites before opening a pull request.

> [!NOTE]
> Both suites run on pull requests and pushes to `main` through the
> [tests workflow](./workflows/tests.yaml).

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
