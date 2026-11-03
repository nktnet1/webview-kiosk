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

AGP 9 normally registers JVM unit tests only for `testBuildType` (`smokeRelease`
here). The app explicitly enables host unit-test components for all variants to
retain `:app:testDebugUnitTest` for CI and Compose Preview resource packaging.
This does not run additional unit tests unless their tasks are requested.

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
R8 mapping availability, missing-rule suggestions, broad `-dontwarn` rules,
and redundant Public Suffix List resources. It also verifies that bundled
content-blocking scripts and redirect payloads survive packaging byte-for-byte.
This includes assets loaded by string path and is compared against the checkout
used for the build. The APK must retain OkHttp's raw list and the scriptlet JSON
resource, but not a second raw list under `adblock/`.
CI also fails on review warnings (for example, new broad suppressions or R8
missing-rule suggestions), rather than silently publishing an APK with
unreviewed shrinker diagnostics. Local runs can omit `--fail-on-warnings`
while investigating a warning; the JSON report still records it.

Generate a machine-readable report locally (no emulator needed):

```bash
python3 scripts/r8_audit.py --require-mapping --fail-on-warnings \
  --json build/reports/r8/smokeRelease.json
```

Or analyse the exact release artifact without rebuilding. Supply its **matching**
`mapping.txt` if the APK was downloaded or copied outside Gradle's output tree:

```bash
python3 scripts/r8_audit.py path/to/app-release.apk \
  --mapping path/to/mapping.txt --require-mapping --fail-on-warnings \
  --json build/reports/r8/release.json
```

With a running Android 16 / API 36, 16 KiB-page emulator, optionally include a
cold-launch check of the optimised APK:

```bash
python3 scripts/r8_audit.py --device --serial emulator-5554 \
  --require-mapping --json build/reports/r8/device.json
```

`--device` installs and launches the APK, so use a dedicated test emulator.
For actual runtime checks on the same R8-minified smokeRelease target:

```bash
python3 scripts/r8_audit.py --require-mapping --device --runtime \
  --json build/reports/r8/smokeRelease-runtime.json
```

`--runtime` builds a separate test-only APK with an instrumentation runner.
It verifies the app's Kotlin sealed JSON serializers, a real WebView call into
`BatteryInterface`, and HiveMQ/Netty MQTT 5 over disposable local TCP and
WebSocket broker endpoints. Both MQTT probes require three successful SUBACKs,
an outbound `url_changed` PUBLISH reaching the broker, and an inbound
`get_status` PUBLISH reaching the app's request flow. The per-probe JSON result
includes `subscriptionsAcknowledged`, `inboundDelivery`, and `outboundDelivery`.
Disconnect callback errors and timeouts fail the MQTT probe. If disconnect or
settings restoration also fails after another probe error, that original error
remains in `details.error` and cleanup diagnostics appear separately in
`details.cleanupError` in the JSON report. WebView teardown errors are
handled the same way. Failed probes also include full `details.stackTrace` and
(if cleanup fails) `details.cleanupStackTrace` fields; multiline exceptions are
preserved without truncation. MQTT preferences are changed briefly and restored;
use a **disposable emulator**, never a production kiosk. The
probes only support the locally built smokeRelease APK, not an unrelated
production release with a different obfuscation mapping. TLS/WSS, NFC, and the
other JavaScript interfaces are still out of scope.

Runtime probes are **not** enabled in CI; the old instrumentation/AVD matrix
remains retired.
The JSON report uses schema version **2**. It contains `generatedAt`,
`gitCommit`, APK hash/version/package, per-check results, R8 file hashes, and a
summary with separate `static`, `device`, and `runtime` status/counts. A static
CI run can pass its checks while `summary.status` is **`partial`** and
`summary.runtime.status` is **`not_run`**. `partial` is informational and does
not trigger `--fail-on-warnings`; CI exits nonzero for failures or warnings.
Use `summary.static.status` for static results, and inspect
`summary.runtime.status` and `summary.runtime.requested` for actual device
coverage. `summary.status` becomes `pass` only after all checks pass, including
optional runtime probes. On failure, JSON is still written. CI uploads it as
part of `android-unit-results` or `release-r8-audit`. Consumers of the old
`summary.status` should be updated for the new `partial` value.

The default static checks cannot prove runtime behaviour. The optional
`--device --runtime` checks cover the critical loopback and serialization paths
listed above, but are not a replacement for manual TLS/WSS, NFC and download
acceptance tests. If a runtime regression occurs, add a targeted test and the
minimum necessary keep rule.
