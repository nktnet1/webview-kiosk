# Contributing

## Verification

Run checks from the repository root. Use JDK 21, the Android SDK, Python 3.10+
and Node.js 24. Documentation checks also require the pnpm version in
`docs/package.json` and installed browser-test dependencies.

For full verification, including the R8 release audit and Android 16 emulator
probes, run:

```bash
python3 scripts/verify.py
```

The script uses a compatible test emulator or a disposable AVD. It does not
wipe existing AVDs or install to a connected phone. `--skip-avd`, `--skip-docs`
and `--skip-browser` result in partial verification.

For focused checks:

```bash
./gradlew spotlessApply
./gradlew checkFormatting
./gradlew lint --warning-mode=all
./gradlew :adblock:test :app:testDebugUnitTest
python3 scripts/ci/test_local.py checks
```

For documentation:

```bash
pnpm -C docs install --frozen-lockfile
pnpm -C docs check
pnpm -C docs typecheck
pnpm -C docs test
pnpm -C docs test:e2e
```

See [docs/README.md](../docs/README.md) for site development.

## Android Studio inspections

Use Australian English (`en-AU`) in documentation and user-facing strings.
Select the project-level **WebviewKiosk** profile under **Editor > Inspections**
and again in **Code > Inspect Code**. Use a Git-tracked or Git-ignored-excluding
scope to avoid `.env*`, generated files and local diagnostics.

The profile disables specific noisy HTML and Java inspections and US-English
style suggestions. Grammar and spelling remain enabled for maintained Markdown
only. Disable individual false-positive grammar rules under **Editor > Natural
Languages > Grammar and Style > Rules**. Keep Android Lint and correctness
inspections enabled.

## Releases and CI

CI checks are defined in [Android CI](./workflows/tests.yaml) and
[documentation CI](./workflows/docs-ci.yaml). Docs-only changes may skip Android
CI, but release verification runs the full Android checks.

The R8 audit can also run independently:

```bash
python3 scripts/r8/r8_audit.py --require-mapping --fail-on-warnings
```

Use a disposable emulator for any `--device` or `--runtime` audit. Audit copied
release APKs with their matching `mapping.txt`. Runtime probes do not replace
manual NFC, external MQTT TLS/WSS and download acceptance tests.
