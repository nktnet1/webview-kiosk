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

## R8 release audits

Every Android pull request builds the non-debuggable, R8-optimised `smokeRelease`
APK with test signing. Release CI audits the **actual signed release APK**. Both
jobs run `scripts/r8_audit.py`, which checks the retrostreams constructor guard,
all `@JavascriptInterface` method names, signatures and runtime annotations,
R8 mapping availability, missing-rule suggestions, and broad `-dontwarn` rules.
It fails CI for confirmed errors, not for informational warnings.

Generate a machine-readable report locally (no emulator needed):

```bash
python3 scripts/r8_audit.py --require-mapping --json build/reports/r8/smokeRelease.json
```

Or analyse the exact release artifact without rebuilding. Supply its **matching**
`mapping.txt` if the APK was downloaded or copied outside Gradle's output tree:

```bash
python3 scripts/r8_audit.py path/to/app-release.apk \
  --mapping path/to/mapping.txt --require-mapping \
  --json build/reports/r8/release.json
```

With a running Android 16 / API 36, 16 KiB-page emulator, optionally include a
cold-launch check of the optimised APK:

```bash
python3 scripts/r8_audit.py --device --serial emulator-5554 \
  --require-mapping --json build/reports/r8/device.json
```

`--device` installs and launches the APK, so use a dedicated test emulator. It
is **not** enabled in CI; the old instrumentation/AVD matrix remains retired.
The report contains `schemaVersion`, `generatedAt`, `gitCommit`, APK hash,
version and package, per-check `pass`/`fail`/`warn`/`not_run` statuses, R8 file
hashes, and a summary. On failure, the JSON is still written. CI uploads it as
part of `android-unit-results` or the `release-r8-audit` artifact for analysis.

These structural checks cannot prove correct MQTT/Netty connections, JSON
serialization, or execution of JavaScript in a live WebView. Exercise those
features against a release-optimised APK as part of device acceptance testing;
add a targeted test and keep rule if an actual R8 regression is discovered.
