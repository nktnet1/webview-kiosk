# Contributing

## Android

Use JetBrains Java 21 and the Android SDK specified in `app/build.gradle.kts`.
Set `JAVA_HOME` and `ANDROID_HOME`, then run from the repository root:

```bash
./gradlew spotlessApply
./gradlew checkFormatting
./gradlew lint --warning-mode=all
./gradlew :adblock:test :app:testDebugUnitTest
```

Lefthook runs formatting and lint checks for Android source and configuration
changes before commits.

Script tests require Python 3.10+ and Node.js 24:

```bash
sh scripts/ci/test-local.sh checks
```

## Documentation site

Use Node.js 24 and the pnpm version declared in `docs/package.json`:

```bash
cd docs
pnpm install --frozen-lockfile
pnpm check
pnpm typecheck
pnpm test
```

For browser tests, run from `docs/`:

```bash
pnpm exec playwright install chromium
pnpm test:e2e
```

See [docs/README.md](../docs/README.md) for the development server.
CI checks are defined in the [Android](./workflows/tests.yaml) and
[documentation](./workflows/docs.yaml) workflows.

## Offline test assets

Export Linux x86_64 Android SDK, Gradle dependencies, and Robolectric runtimes:

```bash
python3 scripts/export-android-test-assets.py --output build/android-test-assets
```

The exporter requires Python 3.10+. On Linux x86_64, it uses local Gradle, JBR 21,
and Android SDK installations. Other hosts require Docker with Linux containers.

Add `--download-tools` to include Gradle, JBR, and Android command-line tool
archives. Use `--help` for other options.

Concatenate numbered archive parts in order before extraction. The export's
`CONTENTS.txt` lists installation destinations, and `SHA256SUMS` lists checksums.
