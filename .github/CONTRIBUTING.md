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
packages. Pure logic uses JUnit; Android-dependent helpers use Robolectric with
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
pinned Gradle/JBR/SDK components; the export also requires network access to
resolve the project's dependencies. No host Android SDK, Java or Gradle setup
is necessary in Docker mode.

Only `--output` carries over to Docker mode. The native-only `--sdk-root`,
`--gradle-user-home` and `--gradle-bin` options are rejected on other hosts.
Docker uses a disposable copy of the checkout without `local.properties`,
keystores, or local build caches and writes the archive parts to the requested
host output directory. Existing exports in that directory are replaced by the
same rules as the native exporter.

The exporter does **not** include the Gradle dependency cache or Gradle
distribution. It writes `android-sdk.tar.gz` and `robolectric-maven.tar.gz`
without a `.part-000` suffix if they are below 512,000,000 bytes. Larger
archives are split into numbered `.part-*` files; concatenate those parts
in order before extraction. `SHA256SUMS` verifies each exported file and
`CONTENTS.txt` records what is included.
