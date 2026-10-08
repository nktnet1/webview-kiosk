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
