# Regression-testing matrix after v0.26.21

Snapshot: post-merge `additional-tests` at `b51ab06d` compared with the v0.26.21
release commit `aa7ceca9`. This is an assessment of test *coverage*, not a finding that the
untested paths are defective. The app still declares `versionName = "0.26.21"`.

## Current automated-test gate status (13 October 2026; v1.0.56)

**v1.0.56 verification:** The complete offline `:app:testDebugUnitTest`
suite passed **572/572 tests across 57 JUnit suites**, zero failures, errors
or skips. Four new Robolectric tests inspect the packaged Android network
security configuration and assert the app-wide `system` + `user` trust
anchors, retained cleartext policy, no pin overrides or debug override CAs,
and the unchanged default `BLOCK` SSL-error setting. Python CI tests passed
**11/11**, and the Android asset-exporter tests passed. These checks do not
install a real CA certificate or exercise Android's TLS implementation on a
physical device. No dependencies were changed.

**v1.0.55 verification:** The supplied offline JetBrains JDK 21, Gradle,
Android SDK and Robolectric dependencies passed the complete
`:app:testDebugUnitTest` suite: **568/568 tests in 56 JUnit suites**, with zero
failures, errors or skips. Eleven new tests cover activity-owned certificate
picker/installer result delivery, real Compose-scope disposal during selection
and file reading, cancellation, errors and duplicate requests. The preceding
v1.0.54 suite passed 557/557 tests in 54 suites; no dependencies changed.
These JVM tests do not execute Android's real KeyChain certificate installer.

**Testing policy:** Keep automated JVM/Robolectric, CI-script, Vitest and
Chromium Playwright tests. All Android APK instrumentation, AVD/emulator jobs,
and the release APK emulator smoke test have been removed. Device and AVD
acceptance checks are **manual-only** and are not CI release gates.

| Gate | Most recent evidence | Status |
| --- | --- | --- |
| Android JVM/Robolectric | v1.0.56 offline `:app:testDebugUnitTest`: **572/572 tests in 57 JUnit suites**, zero failures/errors/skips; four new Android user-CA trust-policy tests plus existing client-certificate, navigation and ANR regressions. Uses supplied offline toolchain, SDK 37.0, Robolectric jars and AAPT2 | **Verified locally** |
| Kotlin/Java Android Lint | v1.0.54 `:app:lintDebug` attempted, but offline Gradle lacks `com.android.tools.lint:lint-gradle:32.4.1`; source analysis did not run. User SARIF reports 7 `UnsafeOptInUsageError` findings at the v1.0.53 property-level opt-in | **Recheck with installed Lint runtime**; no source result claimed |
| Isolated Kotlin JVM regressions | v1.0.47: production `DeviceOwnerManager.kt` **9/9** mocked Dhizuku permission scenarios, production `lockTaskUtils.kt` **4/4** Android-version-gate success/denial scenarios, and 3/3 actual permission-token JUnit methods compiled/run with minimal JUnit shims. Both former failure classes were reproduced against v1.0.46. v1.0.46 independently passed 4/4 blob transport scenarios | **Verified narrowly** with temporary JVM stubs, not an Android runtime or full Robolectric suite |
| Docs Vitest / Chromium Playwright | Jobs remain configured; no passing CI output supplied | Unverified |
| Offline CI checks / local runner | v1.0.56: **11/11** Python CI-script tests and Android asset-exporter Bash tests passed. No TypeScript, dependency or generated-file changes | Verified local CI scripts |
| Gradle/pnpm caching | v1.0.31 Gradle build cache and pnpm store caching retained; no measured post-change timings | Speedup unverified |
| Installed APK, API 23 / API 35 | Instrumentation suites and CI jobs removed in v1.0.33 | Manual-only; **not** passing automated gates |
| Release APK launch | AVD smoke job removed; release build still depends on JVM/docs test gate | Manual-only; release APK not runtime-certified by CI |

**Known historical failure retained:** [API 35 CI run 38190273493](https://github.com/nktnet1/webview-kiosk/actions/runs/38190273493)
failed the custom-password dialog visibility assertion after v1.0.30. Removing
that automation does **not** fix the underlying UI, nor confirm that the
password dialog works on a device. Verify it during manual device acceptance.

## Android-installed user CA trust (v1.0.56)

The app already referenced `@xml/network_security_config` in its manifest.
The previous base config preserved cleartext access but did not opt in to the
Android user-installed CA store for modern target SDKs. Its `<trust-anchors>`
now lists both `system` and `user`, enabling ordinary Android WebView HTTPS
verification of private/local server certificates signed by a CA that the
user installed through Android Settings. This intentionally applies **app-wide**:
it is not restricted to the kiosk URL and is **not** a runtime toggle. The
original cleartext-traffic policy remains unchanged. No blanket SSL-error
approval is introduced; hostname, validity and certificate-chain checks still
apply, and the default SSL-error mode remains `BLOCK`.

The Mutual TLS setting now explains that its PKCS#12 client-identity installer
is distinct from Android CA installation. README documents the broader trust
scope and expected setup. The PDF-specific TLS policy and client-certificate
lifecycle code remain untouched.

Four Robolectric tests parse the **compiled** XML resource to verify CA sources,
unchanged cleartext policy, and lack of pin overrides/debug-only exceptions,
and assert that the default SSL-error mode still blocks invalid certificates.
The full Android JVM suite passed **572/572 tests across 57 suites**; Python
CI tests passed **11/11** and asset-exporter tests passed. Android Lint was
not rerun because the offline cache lacks `lint-gradle:32.4.1`; actual device
CA installation and trust resolution remain unverified.

## Activity-owned mutual-TLS certificate installation (v1.0.55)

In v0.26.21 through v1.0.54, `MutualTlsSetting` owned the document-picker
and KeyChain-installation launchers as composition-scoped activity results. It
also used `rememberCoroutineScope` to read the selected PKCS#12 document and
launch KeyChain. Navigating away from settings while selection or reading was
in progress could unregister its callbacks, cancel the coroutine and display a
misleading `rememberCoroutineScope left the composition` failure toast.

`MainActivity` now registers both callbacks at activity creation, before
`STARTED`, using stable activity-result registry keys and its `lifecycleScope`
for file reading/installer handoff. The callbacks and read survive removal of
the settings composable and the activity's normal stop/start transitions;
true activity destruction still cancels its scope, without treating coroutine
cancellation as a certificate-installation error. Reading retains the
`Dispatchers.IO` boundary and 10 MiB file cap. Picker/installer launch failures
remain visible, and repeated requests cannot overlap one certificate handoff.
The existing PKCS#12 import format, client-certificate behavior and server-TLS
trust policy are unchanged. This patch does **not** enable Android user-CA
trust for arbitrary HTTPS sites.

Nine Robolectric controller tests cover picker/installer result delivery during
stop/start, cancellation vs genuine failure, retries and duplicate requests.
Two additional Robolectric tests dispose an actual Compose `rememberCoroutineScope`
while the activity stays alive, then complete the picker or file read and verify
the KeyChain installation intent still launches. **The complete offline Android
suite passed 568/568 tests across 56 suites.** Python CI script checks passed
**11/11**, and the asset-exporter tests passed. Real Android KeyChain prompts,
credential confirmation and installation remain outside the JVM test harness.

## WebView asynchronous startup lint opt-in (v1.0.54)

The user-provided Android Lint SARIF reports seven `UnsafeOptInUsageError`
findings, all in `WebViewStartup.kt`, for WebKit 1.14's experimental async
startup API. The v1.0.53 property annotation did not cover experimental calls
inside the startup initializer lambda according to Android Lint. v1.0.54 moves
the AndroidX `@OptIn` annotation to the enclosing `WebViewStartup` object,
covering the initializer and its callback without propagating the experimental
marker to callers or disabling the lint rule. Runtime behaviour and dependency
versions remain unchanged. Kotlin production/test compilation and the full
Android JVM/Robolectric suite passed 557/557 tests in 54 suites with no
failures/errors/skips. The compiled `WebViewStartup` class contains the new
class-level `androidx.annotation.OptIn` marker.

Local `:app:lintDebug` did **not** reach source analysis because the supplied
offline dependency cache lacks `com.android.tools.lint:lint-gradle:32.4.1`.
The user's Lint runtime must verify that the seven reported errors are gone;
no passing local Lint run is claimed.

## Historical Android ANR mitigations (v1.0.53)

The five supplied ANR traces describe **four classes** seen around v0.26.21.
They do not establish that those issues still reproduce in the current code.
Earlier patches had already bounded link/image previews, protected WebView
navigation lifetime and provided a configurable `allowDefaultLongPress` setting.
This patch targets only remaining plausible application-level contributors:

- **ANRs 1 and 2, Android Minikin/Compose text layout:** History, Bookmarks and
  Local Files dialogs previously inserted a zero-width separator after every
  UTF-16 code unit in unbounded labels. Display text is now bounded to 256
  UTF-16 units *before* inserting separators between Unicode code points; the
  underlying full URL, bookmark and filename remain unchanged. The shortcut
  dialog now bounds its displayed current URL. The existing settings-export
  preview is reduced from 8192 to 2048 characters; copying/exporting still
  uses the complete value. This only reduces worst-case shaping cost; it
  does not fix defects inside Android's native text layout.
- **ANR 3, Chromium selection action toolbar:** Existing
  `allowDefaultLongPress` already offers an opt-out. No silent disabling of
  native text selection or speculative icon-decoding workaround was added.
- **ANR 4, Chromium native frame rendering:** No Kotlin-specific defect was
  demonstrated, so no changes were made to graphics, renderer or frame timing.
- **ANR 5, WebView native library startup:** `MainActivity` now kicks off the
  existing AndroidX WebKit 1.14 experimental async warmup before Compose mounts
  its navigation host. Startup coordination is one-shot and non-blocking; the
  host shows a progress indicator until the callback, then performs ordinary
  WebView construction on the main thread. UI-thread-required WebView startup
  work is not attempted during warmup. An eventual failure or a missing native
  callback falls back to normal construction (12-second bounded safeguard)
  rather than leaving an indefinitely blank host. Remote clear-cache WebView
  creation waits for the same one-shot startup gate. Android API 21 minimum and
  the existing WebKit dependency are preserved.

Three pure JVM tests validate the startup gate's callback ordering, failure
fallback and concurrent requests. Three tests validate bounded display text,
including long input and surrogate pairs. Existing real MainActivity refresh
and navigation tests now inject a deterministic provider-startup completion,
preventing Robolectric's native Chromium startup limitations from introducing
order-dependent failures. **The full offline Android JVM/Robolectric suite
passed 557/557 across 54 suites.** CI-script Python tests **11/11** and asset
exporter shell tests passed. These tests do not emulate native Android Minikin
or Chromium ANRs, and no physical-device or hosted CI result is claimed.

## WebView recreation and navigation regression suite (v1.0.52)

v1.0.51 preserves a single WebView `NavBackStackEntry`, using its
`SavedStateHandle` to recompose the WebView content instead of replacing the
root. v1.0.52 extracts the existing keyed destination content into
`WebViewRootDestination` and provides an internal content-only `SetupNavHost`
overload. The public application's `SetupNavHost(navController)` still renders
the production `WebviewScreen`; the content seam permits isolated Compose
lifecycle assertions without replacing the graph or loading a real browser.

- Five Robolectric tests mount **the production SetupNavHost graph** in a
  `ComposeView` and drive recomposition with a frame clock. They assert exactly
  one disposal per replaced WebView composition, no recreation on unrelated
  recomposition, identity and graph retention across file-import-like temporary
  host removal, rapid settings/root navigation, returns during an actual
  composed administrator transition, and `NavController` saved-state restore.
- Four additional Robolectric tests mount **real MainActivity + WebviewScreen**.
  They assert an external URL creates a new WebView and loads its URL, remote
  settings reload creates a WebView with the updated JavaScript policy,
  replaced WebViews are destroyed, delayed retired-WebView callbacks cannot
  rewrite the replacement's browsing history,
  and activity recreation remounts a single functional root and permits another
  refresh. The v1.0.51 settings-to-root assertions remain in place.
- The nine new focused methods passed and the **full Android JVM/Robolectric
  suite passed 551/551 tests in 52 suites**. CI-script tests passed 11/11 and
  asset-exporter tests passed. Robolectric does not emulate a device
  predictive-back gesture; these tests
  protect observable navigation and WebView lifecycles, not the exact original
  framework animation race. No dependency or generated-file changes.

## Predictive-back navigation entry ownership (v1.0.51)

`navigateToWebViewScreen()` used `popUpTo(WebView) { inclusive = true }`
followed by `navigate(WebView)`. This replaced the start destination with a new
`NavBackStackEntry` even if the current screen was already WebView. When this
interleaves with Navigation Compose predictive back, the host can prepare a
transition for an entry already removed from its back stack. The app also built
its navigation graph through a fresh builder closure, making graph identity
sensitive to recomposition and temporary NavHost removal.

- Returning from settings and the administrator notice now pops **to but not
  including** the existing WebView entry; repeated return requests leave its
  identity unchanged. In unusual graphs where no WebView entry exists (e.g.
  isolated authentication tests), navigation still creates the WebView entry.
- Every intentional WebView recreation is signalled through the destination's
  `SavedStateHandle`; a Compose `key` recreates the screen content while retaining
  its navigation entry. This keeps refresh/settings/intent behaviour without
  replacing the root destination during a back gesture.
- The graph is remembered by controller and reuses its existing graph across
  NavHost disposal/recreation, avoiding graph replacement during in-flight
  navigation animations.
- Five new Robolectric/JUnit tests exercise nested settings, root refresh
  identity, administrator notice, missing-root fallback and repeated returns.
  The existing 18 authentication-wrapper cases still pass, including cancel
  navigation. The **full Android JVM/Robolectric suite passed 542/542 tests in
  50 suites**. Python CI tests passed 11/11; asset-exporter tests passed.
  Predictive-back gesture race conditions are not directly emulated by the
  Robolectric tests, so field confirmation of crash resolution remains separate.

## Incoming intents, settings documents and keep-awake lifecycle (v1.0.50)

`MainActivity` is exported for browser URLs and document sharing. Previously an
explicit `ACTION_VIEW` could pass an unsupported scheme such as `javascript:`
straight to `WebView.loadUrl`, bypassing the manifest intent-filter scheme
policy. Android versions can also throw `ClassCastException` for extras whose
values are not the requested types. A `text/plain` document shared via
`EXTRA_STREAM` was lost if it did not also provide a valid text URL.

- `handleMainIntent` now accepts HTTP(S) VIEW links only when they have a host,
  and continues to accept `content:` VIEW attachments. `ACTION_SEND` file
  attachments require a `content:` or `file:` stream URI, including when a
  `text/plain` share has no valid text URL. Supported network URL text shares
  and their validation are unchanged. Extra reads now handle both corrupt
  parcelables and type mismatches without crashing the exported activity.
- Settings export opens, writes and closes the selected SAF provider stream on
  `Dispatchers.IO`. Null streams and close failures are treated as errors, not
  silent successes; the success toast is emitted only after the stream closes.
  Import/export cancellation no longer generates an erroneous failure toast.
- The keep-awake Compose effect now uses the activity's window and a lifecycle
  observer that applies `FLAG_KEEP_SCREEN_ON` only in `RESUMED`, even when
  the setting recomposes during a pause. It clears the flag on pause and
  disposal, and restores it only on resume.
- Five `IntentUtilsTest`, three `SettingsExportTest` and three
  `KeepScreenOnWindowControllerTest` methods cover these regressions. All
  11 new methods passed. Production and test Kotlin sources compiled, and
  **537/537 Android JVM/Robolectric tests across 49 suites** passed offline.
  Python CI-script checks **11/11** and the asset-exporter Bash tests also
  passed. These are local tests; Android Lint, hosted CI, docs Vitest/Playwright,
  APK/device interactions (including API 23/35 and SAF-provider variations)
  remain unverified. No runtime dependency, APK instrumentation/AVD job,
  generated file or TLS trust policy was changed.

## PDF source connection and authentication ownership (v1.0.49)

`cancelPdfSourceRequests()` previously closed TLS prompts alone; it left
in-flight PDF HTTP connections and Basic authentication prompts alive until
their response or the 24-hour source-registration expiry. A replaced token
could therefore finish network work after navigation, and its cancelled
authentication state could retain credentials.

- Cancellation now closes tracked HTTP connections and cancels HTTP Basic
  requests as well as TLS requests. A closed token refuses new source loads;
  in-flight requests check source ownership before handling response cookies,
  redirects and returned content.
- Each HTTP response stream releases only the connection it owns. Cancelling
  one viewer does not disconnect another viewer's PDF stream. Request cleanup
  is idempotent, including normal close after prior viewer cancellation.
- The next token for the **same PDF URL** inherits previously saved credentials
  before the old token is closed. The old token then clears its credentials,
  and permission to save late Basic credentials is checked atomically with
  cancellation. This does not alter global browser cookies or TLS decisions.
- Five new Robolectric cases cover late Basic prompts, viewer replacement,
  retained authentication across a deliberate retry, response-stream isolation,
  and cancellation during an HTTP redirect. TLS `BLOCK`, `PROMPT`, `PROCEED`,
  platform certificate validation, client-certificate choice and Android API
  compatibility are unchanged.
- **Verification:** focused PDF suites **40/40**, full offline Android JVM/
  Robolectric suite **526/526 in 46 suites**, Python CI-script tests **11/11**,
  and Android asset-exporter shell tests passed. Device HTTPS/KeyChain, PDF
  rendering, hosted CI, Android Lint, docs Vitest/browser and manual release
  acceptance remain separate checks.

## Device-owner permission and lock-task failure ownership (v1.0.47)

Two lock-task exit paths could falsely notify the WebView controls that a kiosk
was unlocked: the no-authentication shortcut emitted `unlockSuccess` after
`tryUnlockTask()` returned `false`, and the non-Android-6 authenticated path
similarly emitted success when `stopLockTask()` was denied. Both now send the
success notification **only** after `tryUnlockTask()` succeeds. The existing
Android 6 authenticated guard and the authentication prompt's wait-state
semantics are unchanged. Failure still uses the existing error toast.

Dhizuku permission requests could finish out of order: an old granted or
denied callback could act after a replacement request, be delivered twice, or
report success after the device owner/permission was lost. Reentrant transport
failures could also invalidate the replacement binder state. The request path
now uses a thread-safe single-use token, checks that the owner is still Dhizuku,
verifies effective permission before reporting a grant, and isolates consumer
callback exceptions. Switching to actual platform device ownership and
invalidating a broken Dhizuku wrapper discard pending tokens. No SDK or device
policy bypass, additional permission or dependency was introduced.

- Four new `UnlockFailureNotificationTest` Robolectric cases cover denied
  unlocks, Android 6's SDK-gated path (using an API-28 Robolectric runtime with
  a temporary SDK override), successful unlock exactly once and retry after
  failure. This override **does not emulate a real Android 6 device**.
- Three new `PendingDhizukuPermissionRequestsTest` JUnit cases cover stale
  replacements, duplicate results and invalidation. The tests exercise the
  production request-token class without Android framework dependencies.
- **Verified locally:** `DeviceOwnerManager.kt` compiled against temporary
  Android/Dhizuku stubs and passed **9/9** permission callback scenarios,
  including revoked permission, stale grants, repeated callbacks, replacement
  during a remote exception and a throwing consumer. The actual
  `lockTaskUtils.kt` compiled against temporary JVM shims passed **4/4**
  denial/success/retry checks at SDK gates 23 and 28. Both classes of defect
  reproduced **red on v1.0.46** and passed **green on v1.0.47**. All 3 pure
  JUnit methods compiled and passed using minimal JUnit stubs. Python CI
  tests **11/11**, shell asset-exporter tests, attached Biome `check --write`
  (no modifications), and Git patch checks passed.
- **Pending:** all 7 new JUnit methods under Android Gradle/Robolectric,
  cumulative Android JVM suite, Android Lint, docs Vitest/browser, hosted CI,
  and manual Android 6/Dhizuku/device-owner acceptance. The actual Android SDK,
  Gradle dependency cache and Robolectric runtime files needed for Gradle
  weren't in this handover; no dependencies were installed.

No AVD, instrumentation, APK smoke test, dependency or generated-file
modification, non-Kotlin/Java inspection cleanup, or PDF TLS change.

## Blob download stale-transfer ownership (v1.0.46)

When a MediaStore output stream fails while a page replaces its transfer ID,
`appendChunk` and `finishDownload` previously caught the old stream's failure
and aborted by ID alone. That cleanup could close and delete the *new* pending
MediaStore row instead. A reentrant provider write/flush could also cause a
successful but obsolete write to be acknowledged, or attempt to publish a
row that had already been abandoned. The regression tests use controlled
write/flush callbacks to reproduce this ordering deterministically.

- Failure cleanup now removes a download only when the active map still holds
  the precise `ActiveDownload` instance that failed; explicit abort and ID
  reuse still target the current transfer as before. Stale failure callbacks
  no longer display an error toast for a newer transfer.
- Append verifies that its transfer remains active after the write. Finish
  verifies ownership after flush/close and before making a MediaStore item
  visible. Already completed downloads and unrelated transfer IDs are unchanged.
- Three new `BlobMediaStoreTest` cases cover a stale write failure, successful
  write after reentrant replacement, and both successful and failed flushes
  after replacement. They also check that obsolete failures do not replace
  a pending user notification. Existing normal completion, explicit abort,
  publication failure, and disposal cases remain in the same suite.
- **Local evidence:** a no-dependency Kotlin/JVM harness compiled the actual
  production bridge against temporary Android shims and passed four relevant
  scenarios; running the same harness against v1.0.45 failed on the stale-write
  deletion assertion. Python CI tests **11/11**, asset-exporter shell tests,
  Biome `check --write` (no modifications), and `git diff --check` passed.
  **Not verified:** running the added Robolectric methods or the full Android
  Gradle suite in this session (SDK/dependency cache absent), docs Vitest/
  Chromium Playwright, hosted CI, Android Lint and manual device acceptance.
  The three new tests must pass in an Android-capable environment before the
  previous 510-test passing count can be updated.

No AVD, instrumentation, release-APK smoke job, dependency/lockfile change,
PDF TLS trust-policy modification or non-Kotlin/Java inspection cleanup.

## IDE inspection follow-up: correctness and Kotlin/Java diagnostics (v1.0.45)

The updated Android Studio inspection export (supplied after v1.0.44) no
longer lists the two older minimum-API errors. It still flags one compat
WebView getter and the intentional custom PDF TLS trust manager, plus
Kotlin/Java, general and Groovy inspections. This increment is scoped to
safe corrections and existing behavior preservation:

- **Android correctness:** Places the `WEB_RESOURCE_ERROR_GET_DESCRIPTION`
  feature guard directly around the `WebResourceErrorCompat.description`
  read, in addition to the existing short-circuiting helper. This makes
  the feature requirement independently explicit at the access site.
- **Kotlin threading:** Both PDF.js asset transfer and file import copy
  helpers now independently enforce `Dispatchers.IO` for blocking network
  and file operations, instead of relying solely on their current callers.
  Existing cancellation and temporary-file cleanup behavior is preserved.
- **Kotlin/general cleanup:** Simplifies two equivalent boolean checks;
  keeps the editable-text cap unchanged while removing an always-constant
  private helper parameter; removes six IDE-reported redundant `unused`
  suppressions from `@JavascriptInterface` methods (the public JavaScript
  bridge annotations and signatures are unchanged).
- **Groovy:** Uses an explicit `ProjectDependency` import in the Android
  test-asset resolver; dependency filtering behavior remains unchanged.
- **Automated coverage:** Adds an import regression that checks copy
  progress runs off the calling thread and verifies the complete file is
  published without temporary files. Retains API/feature-gating,
  authentication, file-size and cancellation regressions.

**Warnings retained intentionally:** `PdfTlsState` installs a per-viewer
`X509TrustManager` that first delegates platform/hostname verification to
Android; configured `BLOCK`/`PROMPT`/`PROCEED` error handling is a deliberate
trust-policy feature. Its `CustomX509TrustManager` security inspection should
remain visible until a dedicated security-policy review, not be silenced
by a broad suppression. The apparently unused MQTT subscription helper is
used by reflection in tests; lock-task action lambdas are called via their
Activity receiver; Kotlin property-delegate override parameters, JavaScript
bridge methods, Android lifecycle entry points and a service method returning
`START_NOT_STICKY` have intentional signatures. These should not be deleted
or rewritten simply to make an IDE inspection counter fall.

Non-code style, Gradle-dependency-pruning, module-layout, markdown, HTML and
spelling findings remain outside this increment. No Android instrumentation
or AVD tests, generated files, dependency changes or TLS trust-policy change.
The dependency-only Gradle asset task is deliberately invoked with
`--no-configuration-cache` by the exporter; running it without that flag
fails task-state serialization, even when dependency resolution succeeds.
Android Gradle Lint cannot be rerun fully offline without the previously
missing `com.android.tools.lint:lint-gradle:32.4.1` cache artifact, so the
updated IDE inspection still requires an independent rescan.

## Kotlin/Java inspection follow-up (v1.0.44)

The supplied Android Studio inspection export reported two min-API errors and an
AndroidX WebView feature-gate warning in Kotlin production code. This patch:

- Guards the API 23 AndroidKeyStore authentication cipher factory on Android 5.x;
  the existing credential fallback remains unchanged. A new regression covers the
  unsupported API boundary without creating an AVD test.
- Removes the API 24 `CompletableFuture.isDone` call from the MQTT disconnect
  hot path. The identity guard is sufficient because completion clears
  `pendingDisconnect` under the same monitor before completing its future.
- Checks `WEB_RESOURCE_ERROR_GET_DESCRIPTION` support before obtaining a
  WebView compat error description. Three new tests cover short-circuiting on
  old Android, unsupported WebView, and matching/nonmatching error descriptions.
- Removes a redundant biometric callback override; retains the otherwise
  apparently unused MQTT subscription helper, which tests call reflectively.
- Waits for asynchronous MQTT publication logging in an existing Robolectric
  regression instead of assuming completion callbacks execute inline.

**Inspection findings reviewed but deliberately unchanged:** The per-viewer PDF
`X509TrustManager` delegates default server validation to Android's
`X509TrustManagerExtensions`, with separate configured SSL-error approval paths;
its security warning should not be silenced without a dedicated trust-policy
review. The reported blocking PDF.js and file-import calls are already reached
through `Dispatchers.IO`. Android entry-point and JavaScript-bridge reflection,
unused delegate signature parameters, and minor style/suppression inspections
were not treated as production defects. No non-Kotlin/Java inspection fixes,
generated artifacts, or dependency changes were made. An attempted offline
`:app:lintDebug` run was blocked because `lint-gradle:32.4.1` is absent from
the supplied Gradle cache; the IDE inspection was not independently rerun.
Hosted CI remains unverified.

## AVD retirement and retained automated coverage (v1.0.33)

- Removed the API 23/35 Android instrumentation matrix, emulator boot/snapshot
  caching, instrumentation report/logcat tooling and all eight files under
  `app/src/androidTest`. Removed AndroidJUnitRunner configuration and its
  instrumentation-only dependencies. No APK UI or device test is run in CI.
- Removed the API 35 **release smoke** job, emulator launch script and its
  dependency in `.github/workflows/release.yaml`. A draft release now depends
  on successful Android JVM/docs checks and a successful minified APK build;
  on-device installation, launch, permissions and behaviour need manual sign-off.
- `scripts/ci/test-local.sh` supports `checks`, `jvm`, `docs`, `docs-browser`
  and `all` (those four suites only). The AVD test selector and filters are
  intentionally removed. Robolectric and JVM MQTT/HTTP tests do **not** require
  an AVD; docs Playwright still runs Chromium and is unaffected.
- Preserved the Gradle task-output cache, build profiles, pnpm store caches,
  docs Vitest/Chromium E2E and existing JVM/Robolectric regressions.
- **Validation:** 11 offline Python regression tests and the Android test-asset
  exporter shell checks pass. Both workflows parse as YAML; the version catalog
  parses as TOML, shell syntax passes, and the attached Biome `check --write`
  passes in `docs/` (Biome does not format Kotlin, Python or these workflows).
  None establishes an on-device pass. Post-v1.0.33 CI results are unavailable.
- Older v1.0.26–v1.0.32 entries below remain as **historical evidence** for
  why this choice was made; their emulator automation and CI wiring are no
  longer present. Do not treat retired device tests as passed coverage.

## P0 authentication host-replacement regressions (v1.0.34)

- A native biometric or legacy credential prompt opened on one activity must not
  leave a new activity waiting on a callback bound to the old host. When
  `AuthenticationManager.init` receives a **different** activity while an
  authentication is pending, it now invalidates the old pending request before
  cancelling it. A subsequent prompt may be initiated by the new host. The
  callback-invalidation ordering prevents synchronous cancellation errors and
  stale successes from affecting the replacement request.
- Added **five Robolectric tests** to `AuthenticationManagerTest`: idempotent
  `init` on the same host; replacement during biometric authentication with
  stale success/error callbacks and delayed old-host cleanup; preservation of
  an already authenticated session; cancellation/retry for an unfinished custom
  password prompt; and rejection of the old activity's legacy credential result
  after the new host has a distinct request code. The legacy case changes only
  the SDK gate on an API 28 Robolectric runtime, not the installed platform.
- **Remaining P0 automated coverage:** stress session expiry, lifecycle
  transitions and prompt retry/cancel order with controlled clocks/callbacks;
  then continue the navigation, remote-message ownership and foreground-service
  rows below. These five tests target host ownership, not real credential UI.
- **Validation:** 11 offline Python CI regression tests and the Android
  asset-exporter shell checks passed; shell syntax, Biome `check --write` in
  `docs/` and patch whitespace checks passed. Kotlin/Robolectric execution and
  APK runtime behaviour are **not verified locally** (Android SDK/dependency
  cache unavailable). The five new JVM tests must pass in CI before being
  counted as verified. No instrumentation tests, AVD jobs, lockfile changes or
  emulator gates added.
- **Manual-only finding remains open:** API 35 custom-password dialog visibility
  timed out in run 38190273493. New host-replacement tests do not prove that
  dialog was displayed or fix the historical failure.

## P0 WebView navigation and stale-callback regressions (v1.0.35)

- After a redirect has **committed a different URL**, a delayed `onPageStarted`
  or `doUpdateVisitedHistory` callback for the *original request* must not
  rewind the active load or reset the new page's error/authentication state.
  `WebViewNavigationSession` now records this transition and rejects the stale
  original callback. A new explicit redirect back to the original URL and an
  intentional new load still work. Redirects continuing to **new URLs** without
  an override callback remain supported for legacy WebViews.
- URL equality previously used `trimEnd('/')` on the entire string, which
  confused distinct query values such as `?return=/` vs `?return=` and SPA
  fragment routes such as `#/route/` vs `#/route`. The shared comparison now
  trims only the **path's** trailing slash, preserving query/fragment data in
  both callback acceptance and persisted history. It also prevents a previous
  URL ending in a path slash from incorrectly treating a new SPA entry as a
  redirect and retaining the wrong forward-history branch.
- Added **six** pure `WebViewNavigationSessionTest` scenarios, **five** new
  pure `NavigationUrlTest` scenarios, and **four** Robolectric
  `WebViewNavigationTest` cases. The new tests cover stale page starts/history,
  explicit redirect-back, legacy continuation, reentrant new loads, query and
  fragment slash identity, path-slash equivalence and history metadata.
- **Validation:** All **70 navigation tests** (33 session, five URL, and 32
  history cases) passed in a standalone Kotlin/JDK harness with lightweight
  JUnit assertions. History cases used in-memory settings and URLUtil stand-ins,
  **not Robolectric**. The 10 new regressions that exercise previously existing
  functions **failed against the v1.0.34 baseline**, then passed with this fix;
  five new standalone normalization-helper tests also passed. Eleven offline
  Python CI checks and the Android asset-exporter script passed, and Biome
  `check --write` passed in `docs/`. Full Gradle/Robolectric execution, the
  new createCustomWebview wiring, and real-Android callbacks require CI/manual
  verification respectively.
- **Still open:** without a WebView load token, two overlapping loads of the
  exact same URL cannot always be distinguished from callback URLs alone.
  Redirects back to the original URL **without any new override callback**
  remain ambiguous, particularly on legacy WebViews. Real renderer death,
  iframe failures, fullscreen teardown, user-driven redirects and SDK-specific
  callback ordering remain **manual-only** per v1.0.33. This patch adds no
  instrumentation tests, emulator jobs or lockfile changes.

## P0 MQTT connection completion and retired-subscription callbacks (v1.0.36)

- `MqttManager.handleConnectResult` previously decided whether a successful
  connection was current **before** setting up subscriptions, then reported
  success even if that setup coincided with cancellation, client replacement,
  a configuration disable, a pending disconnect or a socket disconnect. The
  completion now checks the *current ready client* both before and after
  subscriptions, with the latter check outside the exception handler for
  subscription setup. A superseded attempt reports cancellation rather than
  a stale `onConnected` callback. A pending disconnect also prevents initial
  success independently of its cancellation flag.
- HiveMQ may deliver a previously registered subscription callback after a
  stop or client replacement. The inbound callback in `subscribeTopic` now
  checks the owning client against the current ready connection **before**
  decoding or dispatching the payload. That prevents stale subscriptions from
  submitting extra commands, settings or requests after handoff. Deliveries
  already admitted before a concurrent stop are not retroactively cancelled,
  nor is MQTT QoS redelivery deduplicated by this guard.
- Added **six Robolectric tests** in `MqttConnectedCallbacksTest`: controlled
  five-way transitions *inside* subscription setup (cancel/replacement/
  disable/pending disconnect/socket loss), rejection of a pending disconnect
  before its cancellation flag is set, delivery ownership after replacement,
  stale callbacks through stop and reconnection states, and cancellation
  arising during the client state read, plus an observer exception that must
  not be misreported as a second connection-error callback. Assertions check
  missing **and** duplicate/late notifications; no emulator/instrumentation coverage added.
- **Validation:** The v1.0.35 connection-completion source failed a focused
  baseline Kotlin/JDK harness (`cancel` reported `connected`); extracted
  v1.0.36 production methods passed **seven connection-completion scenarios**
  and **eight subscription-delivery state assertions** with minimal HiveMQ
  client/config stand-ins on JDK 21. This proves only those JVM method branches,
  **not** that Robolectric or actual HiveMQ callbacks were run. All 11 offline
  Python CI checks and the Android test-asset exporter passed. Attached Biome
  `check --write` checked 70 docs files without changes; Biome does not validate
  Kotlin. Full Android Gradle/Robolectric tests remain **pending** (Android SDK
  and cached Android/Gradle dependencies unavailable in this session).
- **Still open:** actual asynchronous subscription acknowledgement/duplicate
  MQTT QoS deliveries, callbacks accepted immediately *before* a stop,
  automatic reconnect with an external broker and real UnifiedPush distributor
  behaviour. Retain JVM loopback coverage where useful; real transport/device
  runtime is manual-only per v1.0.33.

## P0 MQTT topic-batch ownership and subscription-ack races (v1.0.38)

- `connectClient` now supplies the exact owning `Mqtt5AsyncClient` to the
  command/settings/request subscription batch. Each topic checks that **same**
  client for readiness before building the subscription. Previously a client
  replacement during the first topic could cause the *remaining two topics*
  to be registered on the replacement, even though the superseded connect
  attempt would report cancellation. An initial state-read replacement could
  also leak subscriptions to the replacement. The replacement must set up its
  own topics via its own connection completion.
- Subscription `whenComplete` callbacks ignore SUBACK/failure completions from
  a client that was replaced, disabled, cancelled or entered a pending
  disconnect. They no longer log stale `subscribe success`/`subscribe error`
  results as if they describe the active session. Current-client success and
  failure acknowledgements still produce one log entry each; this **does not**
  delay `onConnected` until subscription acknowledgement or interpret MQTT
  SUBACK reason codes as an application-level subscription guarantee.
- Added **five** Robolectric cases in `MqttOperationStateTest` for replacement
  during the first subscription builder and its initial client-state read,
  current acknowledgement success/error counts, retired-client acknowledgements,
  and stop/disable/cancellation during delayed completions. Existing event,
  response, MQTT lifecycle and subscription readiness tests are retained.
- **Red/green evidence:** Against the v1.0.37 production code, the corrected
  fixture ran 15 `MqttOperationStateTest` methods and **four failed** on the
  above stale ownership/ack behavior. After this fix, **15/15 passed** and
  the **full local `:app:testDebugUnitTest` run passed 473 tests in 43 suites**
  (no failures, errors or skips). Verification used the supplied offline
  Gradle dependencies, Robolectric API 28/29 Maven runtimes, Android SDK 37,
  JBR 21.0.11 and attached Gradle **9.8.0-milestone-1**. An SDK build-tools
  AAPT2 override and an external Robolectric offline init script were used
  only for local execution, not added to the repository. All **11 Python CI
  regression tests** and the **Android asset-exporter shell checks** passed.
- **Still open:** Hosted CI (including its pinned stable Gradle distribution),
  MQTT SUBACK reason-code failures, automatic reconnect of the *same* client,
  QoS redelivery deduplication and callbacks already admitted before stop.
  Real-broker, distributor, Android foreground and APK behavior remain manual
  device acceptance only. No AVD/instrumentation tests or lockfile edits.

## P0 custom-password retry ownership and WebView duplicate finish (v1.0.39)

Two small, independent P0 gaps were combined into one focused increment:

- **Custom-password ownership:** A queued password submission or dismiss action
  from an old Compose dialog used to invoke the no-argument
  `customAuthSuccess`/`customAuthCancel` functions. After lock, retry or host
  replacement, these could act on a **new** custom-password request. Custom
  prompts now expose an observable, opaque per-request identity; success and
  cancellation require that exact request. Reset, error and success invalidate
  the identity before any late callbacks can affect the next prompt. `key` in
  `CustomAuthPasswordDialog` discards entered password, `waiting` and error
  state between requests, even if reset/retry coalesces in a single frame.
  While waiting for an incorrect-password delay, an obsolete request can no
  longer display the old error toast or affect the current prompt. A rapid
  double-submit is ignored while `waiting` is true.
- **WebView finish idempotence:** `WebViewNavigationSession.acceptsCompletion`
  previously accepted a page-finish callback whenever its URL matched the
  current view, **even with no active load**. Some WebViews send duplicate
  `onPageFinished` at 100% progress; this could repeat injected scripts and
  completion side effects. A finish now requires a live load. `finishLoad`
  consumes that load, and a subsequent explicit reload or `onPageStarted`
  creates a new one. Redirect and SPA completion checks remain tied to their
  pending document.
- **Tests:** Added **four** Robolectric manager cases for old/new custom prompt
  identity, cancel/retry, replacement activity and preserved pending prompts.
  Added **four** JVM navigation-session cases for repeated finish, same-URL
  explicit reload, a POST-like page start without an override callback, and
  stale finishes during a later redirect. Existing Compose wrapper tests now
  complete the specific request rather than invoking a no-argument success.
- **Verification:** The focused three-suite Gradle run passed **92/92 tests**.
  The full offline `:app:testDebugUnitTest` run passed **481 tests across 43
  JUnit suites**, zero failures/errors/skips, with the supplied Android SDK,
  Robolectric API 28/29 artifacts, JBR 21.0.11 and Gradle
  9.8.0-milestone-1. As in v1.0.38, the local AAPT2 SDK override and offline
  Robolectric init script are not committed. All 11 Python CI tests and the
  Android asset-exporter shell tests passed; Biome `check --write` passed for
  the `docs/` root (the top-level Biome invocation rejects the nested config).
- **Still open:** Real dialog rendering and keyboard/credential fallback on
  Android remain manual. In particular, the historical API 35 custom-password
  visibility failure is **not** verified fixed. Overlapping identical-URL
  WebView loads without a native generation token remain ambiguous, as do
  actual WebView renderer death and OEM-specific callbacks. Hosted CI and
  installed APK validation have not been supplied. No AVD/instrumentation,
  generated-file or lockfile changes were made.

## P0 MQTT SUBACK/session ownership, UnifiedPush errors and service screen events (v1.0.40)

- **Same-client reconnect proof:** A real HiveMQ MQTT 5 client reconnects to the loopback
  broker and restores exactly three command/settings/request subscriptions, then
  answers an inbound request on the new TCP connection. This passed against
  v1.0.39: HiveMQ retains and restores subscription registrations. **Do not
  manually subscribe again on reconnect**; that would duplicate work/deliveries.
- **Subscription acknowledgement correctness:** A normally completed SUBACK
  carrying a rejection reason (code >= `0x80`) is now reported as `subscribe
  error`, rather than `subscribe success`. Missing acknowledgements and
  exceptional completions also produce an error. A broker-session generation
  prevents a late SUBACK for an earlier session being counted for its
  successor even when the HiveMQ client object is unchanged. HiveMQ's restored
  **message callbacks** remain valid across an automatic reconnect, and are
  still gated on current client readiness.
- **Foreground-service queued broadcast:** A screen/user-present receiver
  callback already queued when the service is stopped, disabled or replaced
  no longer publishes an MQTT event. Events resume after a later accepted
  service start without recreating duplicate receiver registrations.
- **UnifiedPush registration ownership:** Temporary-unavailability and
  registration-failure callbacks for an obsolete registration instance are now
  ignored, as endpoint/message/unregistration callbacks already were. Current
  registration failures are still reported.
- **Regression tests:** Two `MqttOperationStateTest` methods for negative
  SUBACK and late same-client ACKs; one real-broker reconnect test, one service
  late-broadcast test, and two UnifiedPush registration-error tests. Existing
  MQTT/foreground ownership and dispatch suites are retained.
- **Red/green evidence:** Against v1.0.39, the two SUBACK tests, the service
  late-broadcast test and the obsolete-registration test **failed**; the
  real-broker same-client reconnect test already **passed**. After the fixes,
  the full offline `:app:testDebugUnitTest` passed **487 tests in 43 JUnit
  suites**, zero failures/errors/skips, with the supplied Android SDK,
  Robolectric API 28/29 jars, JBR 21.0.11, Gradle 9.8.0-milestone-1 and
  offline dependency cache. The local AAPT2 override and Robolectric init
  script were not committed. All 11 CI-script tests, the Android asset-exporter
  shell tests, Biome `check --write` in `docs/`, and patch whitespace checks
  passed. Hosted CI and real-device behavior remain unverified.
- **Remaining limitations:** Hosted CI, real MQTT broker/distributor, Android
  background/notification rules and on-device event ordering remain to be
  checked separately. No AVD/instrumentation jobs or generated lockfile edits.

## P0 Remote command eligibility and failure-observer isolation (v1.0.43)

- `RemoteMessageManager.emitCommand` previously let exceptions from a stale MQTT
  activity/service `canHandle` callback cancel dispatch before the active native
  host could be selected. A throwing WebView eligibility callback similarly
  prevented fallback to another registered screen.
- Both host and screen selection now isolate eligibility failures, preserve
  coroutine cancellation and check registration membership after the callback.
  A callback that unregisters its own obsolete host or screen cannot win that
  dispatch. Command handling remains first-eligible-wins (no retrying a handler
  that already started executing a command).
- A failure observer or error toast throwing during error reporting no longer
  discards command-completion events or blocks subsequent commands from either
  MQTT or UnifiedPush. Secondary reporting errors are logged instead. The
  shared FIFO completion flow still emits after attempted execution.
- **New tests:** Five Robolectric tests in `RemoteMessageDispatchTest` for
  failed MQTT/screen eligibility, same-callback unregister handoffs and a
  throwing failure observer across transport sources. All five failed against
  v1.0.42; the three existing dispatcher tests remained green (**5 failures
  among 8 tests**). The fixed dispatcher passed **8/8**.
- **Validation:** Full offline `:app:testDebugUnitTest` passed **505 tests in
  43 suites** with zero failures/errors/skips, plus all **11** Python CI-script
  tests, Android asset-exporter shell checks and Biome `check --write` on the
  supported docs files. Host/device behavior, docs Vitest/Chromium and hosted
  CI have not been verified in this local run. No AVD/instrumentation tests,
  lockfile changes or dependencies added.

## Authentication expiry and WebView completion ownership (v1.0.42)

- **Expired authentication result:** `AuthenticationManager.checkAuthAndRefreshSession`
  now clears `AuthenticationSuccess`/`AuthenticationNotSet` back to `Loading`
  whenever the monotonic session or one-shot external grace window is no longer
  valid. A stale successful prompt cannot refresh an expired session, and the
  wrapper observes the invalidated result before presenting a fresh prompt.
- **Reentrant WebView completion:** `WebViewNavigationSession` returns an opaque
  identity token for a matching `onPageFinished` callback. The finish path
  consumes the token only while the same load and WebView owner remain active.
  A new navigation or a reload of the *identical URL* between acceptance and
  cleanup cannot be cleared by the prior callback or receive its completion
  side effects. Existing non-reentrant state fixtures retain their helper.
- **New tests:** Two clock-controlled authentication manager tests, plus five
  pure navigation-session tests for cross-URL replacement, same-URL reload,
  exactly-once completion, retired renderers and invalid callback URLs. The
  two authentication tests **failed against v1.0.41** (39 tests, two failures)
  and passed after the correction. Focused combined Robolectric pass: **81/81**.
- **Validation:** All **500 Android JVM/Robolectric tests** in **43 suites**
  passed with zero failures, errors or skips using the supplied offline SDK,
  Gradle dependencies and Robolectric jars. All **11** Python CI-script tests,
  the Android asset-exporter shell tests and Biome `check --write` in `docs/`
  passed. Full Gradle command and results are local-only; no hosted CI or docs
  browser/test results are asserted.
- **Scope:** Only automated lifecycle regressions. No AVD/instrumentation
  tests, dependency or lockfile edits, and no changes to manual device checks.

## Post-merge regression recovery and MQTT shutdown races (v1.0.41)

- **Authoritative baseline:** `additional-tests` at `b51ab06d` (from the newly
  supplied bundle). Diffing this branch against the original `further-testing`
  work and the v1.0.36–v1.0.40 patches revealed lost v1.0.34–v1.0.40
  protections. Restore only the missing changes, retaining the post-merge
  WebView/PDF improvements, Kotlin source and existing Android test fixtures.
- **Recovered authentication and navigation:** Host-replacement invalidation,
  request-scoped custom password submissions/cancellation, WebView URL
  normalization, redirect handling and exactly-once completion. Restore the
  original Robolectric regressions for these paths; do not silently drop the
  v1.0.35 redirect tests that were absent in the merged branch.
- **Recovered MQTT/remote-service ownership:** Connection completion guards,
  three-topic subscription batch ownership, inactive-client deliveries,
  negative or obsolete subscription acknowledgements, retired UnifiedPush
  registration errors and foreground-service screen-event ownership. Also
  recover the external-only Gradle test-asset dependency resolver change.
- **New shutdown regressions:** A completed disconnect operation no longer
  issues another DISCONNECT on a late PUBACK or a later timeout, even if HiveMQ
  reused the same client for a new session. Successful wire acknowledgements
  cannot report a disconnected state while the client is still connected.
  Delayed publish acknowledgements from a replaced client **or an older
  session on the same client** cannot populate the current publish log.
  Internal disconnect-event callbacks still run so cleanup completes.
- **Test evidence:** Four new shutdown ownership assertions failed against the
  recovered v1.0.40 implementation and passed with the correction. The focused
  MQTT shutdown suite passed **16/16**; authentication, WebView navigation
  session and MQTT shutdown combined passed **90/90**. Offline CI-script tests
  passed **11/11**, plus Android asset-exporter shell checks. Biome
  `check --write` passed on the 70 supported docs files. A fresh full-suite
  run against the corrected post-merge state is recorded in the gate table.
- **Scope:** No AVD tests, dependency changes or generated lockfile edits. The
  historical API 35 authentication-dialog problem remains manual-only and
  unverified; Robolectric results do not certify a running APK, HTTPS MQTT,
  device-owner behaviour or hosted CI.

## Existing baseline

- `:app:testDebugUnitTest` passed offline with **197 tests across 29 JUnit suites**,
  zero failures/errors/skips, using the exported Android SDK, Gradle cache and
  Robolectric runtimes. This verifies JVM/Robolectric behaviour, not a running APK.
- The new suites cover MQTT connection/shutdown/state races; command ordering,
  claims and inbound JSON; PDF HTTP/Ranges/cookies/redirects/Basic Auth/tokens;
  PDF TLS request lifecycle and mTLS rule parsing; blob and MediaStore failure
  handling; URI/file-import safety; and dialog-controller disposal.
- `docs/tests/components/qr/` contains three Vitest files covering version
  discovery, fallback versions and provisioning payloads. The Android unit-test
  result does **not** imply these docs tests have run in the same environment.
- No `app/src/androidTest` instrumentation tests, device-emulator test job,
  browser E2E suite or coverage threshold are configured in this snapshot.

## Authentication follow-up (v1.0.15)

- `AuthenticationSessionTest` adds **15 deterministic session tests** covering
  the exact five-minute timeout, refresh, clock zero, external-activity window
  boundaries, unauthenticated/expired round trips, one-shot preservation and
  explicit lock. Production intervals use `SystemClock.elapsedRealtime()` so
  wall-clock changes cannot extend them and time asleep counts toward expiry.
- `AuthenticationManagerTest` adds **28 tests** with a fake biometric prompt,
  controllable session clock, credential-launch shadow and fake keystore
  operations. They cover duplicate prompts, retryable scans, cancellation,
  stale/duplicate callbacks, host cleanup, custom and legacy credential results,
  credential fallback, missing crypto context, token validation and invalidated
  key recovery. The cipher tests use JVM AES/GCM, not Android Keystore hardware.
- The manager suite uses the exported **API 28** runtime. Where a test changes
  `Build.VERSION.SDK_INT`, it exercises an SDK gate with fake platform operations;
  this is not evidence of execution on API 21/22/23/29/30 devices or runtimes.
- Follow-up validation: the user reported **tests passed after applying v1.0.15**.
  This updates the earlier local-only verification status; no new suite count,
  device result or Compose instrumentation result was supplied.
- Wrapper lifecycle/rendering now has Compose/Robolectric coverage in v1.0.22.
  Still required: Compose instrumentation in the running APK and the
  biometric/credential/keystore device scenarios below. Added tests and the
  reported pass do not close these release-readiness requirements.

## Navigation and WebView ownership follow-up (v1.0.16)

- `WebViewNavigationTest` adds **28 history regressions**, configured for
  Robolectric **API 28**. They cover Back/Forward selection, first-callback and
  chained redirects, forward-branch preservation/truncation, entry IDs/timestamps,
  removal and both forms of clearing, corrupt indices, Home/refresh, SPA history,
  separate settings wrappers, rapid traversal and failed/reentrant loaders.
- `WebViewNavigationSessionTest` adds **27 pure state tests**. They cover loads
  before commit, superseded start/history/finish/error callbacks, redirect chains
  with and without override metadata, legacy new-document detection, PDF source
  identity, POST page starts, SPA pushes before/after page finish, iframe errors,
  same-route owners, renderer generations and idempotent disposal. These are
  policy tests, not invocations of actual WebView callbacks.
- The process-wide programmatic-navigation flag is removed. Each WebView tracks
  its selected history entry and pending URL. Redirects can update that entry
  without losing the forward branch, and rejected callbacks cannot change history
  or screen state. Native history-index changes within one document distinguish
  SPA entries from redirects, including pushes before the document finishes.
  Outgoing views cannot open new prompts/pickers/fullscreen UI,
  recreate the active renderer or reset the incoming view's HTTP-auth request.
  Fullscreen teardown removes its own overlay without changing an incoming
  screen's window mode. URL-event debounce survives recomposition, cancels its
  previous job and checks ownership again after the delay.
- Local verification: **27 session checks passed** in a standalone JDK 21 /
  Kotlin 2.4.10 harness with a minimal assertion shim. **28 history checks passed**
  against production navigation code using in-memory settings and URLUtil
  stand-ins; this does not verify Android preferences, URLUtil or Robolectric.
  Five targeted failures were reproduced against the pre-patch history code.
  All six changed Kotlin files passed syntax checks; attached Biome `check --write`
  passed on `docs/biome.json` (Biome does not check Kotlin).
- Follow-up validation: the user ran the Android suite after v1.0.16 and reported
  **295 tests, three failures**: `clearingAnEmptyHistoryRepairsTheCursor`,
  `homeClearsHistoryBeforeLoadingEvenWhenAlreadyAtHome` and
  `invalidTraversalIndicesNeverInvokeTheLoader`. The standalone settings
  stand-in did not reproduce the real preference delegate's default minimum of
  zero. It therefore missed the clamping of the empty-history cursor `-1` to `0`.
- Still required: instrument repeated route transitions, rapid
  navigation/redirects, queued callbacks, iframe failures, renderer death,
  fullscreen teardown and delayed MQTT URL events in a real WebView. Include
  API 21/23 legacy callbacks and a current WebView provider. URL callbacks expose
  no load ID, so repeated loads of the exact same URL also require device evidence.

## History cursor correction (v1.0.17)

- `SystemSettings.historyIndex` explicitly permits the `-1` sentinel on reads
  and writes. A missing preference, complete reset or wrong-type stored value
  now leaves no selected history entry. Other integer-setting bounds are unchanged.
- Forward traversal requires an existing selected entry. Both navigation menus
  use the same cursor bounds so empty, unselected and out-of-range cursors cannot
  enable Back/Forward actions. The existing invalid-traversal regression now
  includes an explicit unselected `-1` cursor with a nonempty stack.
- `SystemSettingsHistoryTest` adds **six Robolectric API 28 regressions** against
  Android `SharedPreferences` and the production preference delegate. They cover
  missing values, persisted unselected/selected cursors across settings wrappers,
  complete reset, corrupt negative stored values and wrong-type recovery.
- Full local Android validation: `:app:testDebugUnitTest --offline` passed with
  **301 tests across 34 JUnit suites**, zero failures/errors/skips, including all
  three reported v1.0.16 failures and the six new preference regressions. The
  earlier uploaded SDK, Gradle cache and Robolectric API 28/29 runtimes were
  restored. This run used attached **Gradle 9.8.0-milestone-1** and **JBR 21.0.11**;
  the project wrapper requests Gradle 9.8.0. Attached Biome `check --write` passed
  on `docs/biome.json` without changes; Biome does not check Kotlin.
- Follow-up validation: the user reported **v1.0.17 passes**.

## UnifiedPush registration and remote streams (v1.0.18)

- Message, endpoint and unregistration callbacks must match the currently
  configured UnifiedPush registration instance. The comparison uses the same
  default and variable expansion as registration. After an instance change,
  late callbacks from the previous instance cannot execute commands, import
  settings, replace endpoint credentials or erase the current endpoint.
  Payload `targetInstances` still identifies the app instance, not the
  UnifiedPush registration instance.
- `UnifiedPushManagerTest` adds **28 Robolectric API 28 regressions** using real
  connector messages/endpoints and Android preferences. They cover stale and
  changed instances; default/template instances; enabled and decrypted-message
  policy; omitted, null, empty, matching and mismatched targets; malformed
  envelopes and recovery; native command execution without an activity/MQTT
  host; settings notifications; and endpoint storage/redaction/unregistration.
  Six stale-instance cases failed against v1.0.17 before the production fix.
- `RemoteSettingsRequestDispatchTest` adds **five coroutine regressions** against
  the production flows: FIFO delivery of 256 settings or requests while a
  subscriber is blocked, one shared claim across two overlapping collectors for
  128 deliveries in each stream, and independent request/applied-settings
  progress while a settings subscriber is blocked. Source metadata is preserved.
- Full local Android validation: `:app:testDebugUnitTest --offline` passed with
  **334 tests across 36 JUnit suites**, zero failures/errors/skips, using the
  restored SDK/cache/API 28/29 runtimes, attached **Gradle 9.8.0-milestone-1** and
  **JBR 21.0.11**. Attached Biome `check --write` passed on `docs/biome.json`
  without changes; Biome does not check Kotlin.
- Follow-up validation: the user reported **v1.0.18 passes**.
- Still required: a real distributor/transport end-to-end test, true cold-process
  service initialisation and device-owner commands, activity/service host
  transitions, and foreground-service restart/cleanup. Controlled flow collectors
  verify queue/claim mechanics, not actual activity or service lifecycles.

## Foreground-service startup and cleanup (v1.0.19)

- The MQTT service acquires its wake lock, screen receiver and remote-message
  ownership only after entering the foreground. Settings/request collectors
  subscribe before connection restoration. Native command ownership and message
  claims recheck the current MQTT/foreground settings on each delivery.
- Disabled or denied starts immediately remove foreground notifications, cancel
  service jobs, unregister receivers/command ownership and release the wake lock;
  cleanup no longer waits for `onDestroy`. Repeated valid starts reuse resources,
  and a later enabled start can recover after a rejected start on the same host.
- Lock-task starts create notification channels even without a prior activity.
  Return broadcasts and denied starts immediately cancel monitoring, remove the
  foreground notification and unregister the return receiver.
- `MqttForegroundServiceTest` adds **18 Robolectric service regressions**, and
  `LockTaskServiceTest` adds **seven**. They exercise actual service callbacks,
  Android preferences/notifications/receivers/wake locks, native command dispatch,
  service settings/request collectors, repeated starts, configuration changes,
  denial and teardown. Both suites use **API 28**, with one **API 29** foreground
  case each. Fourteen cases failed against v1.0.18 before the production fix.
- Full local Android validation: `:app:testDebugUnitTest --offline` passed with
  **359 tests across 38 JUnit suites**, zero failures/errors/skips, using the
  restored SDK/cache/API 28/29 runtimes, attached **Gradle 9.8.0-milestone-1** and
  **JBR 21.0.11**. Attached Biome `check --write` passed on `docs/biome.json`
  without changes; Biome does not check Kotlin.
- These tests use an already initialised MQTT manager with a recording HiveMQ
  client proxy, shadowed foreground-start denial and shadowed lock-task state.
  They do not certify a cold MQTT connection, broker delivery, real lock task,
  activity/service handoff or Android 14+ foreground restrictions. Still required:
  status-notification updates, settings changes during connection restoration,
  and the device/transport/lifecycle scenarios below.

## Notification ownership and Robolectric API cleanup (v1.0.20)

- All six suites that used deprecated `LooperMode.Mode.LEGACY` now use
  `PAUSED`. Service receiver checks inspect the registered receiver filters
  instead of deprecated `getReceiversForIntent`/`hasReceiverForIntent` calls.
  The full suite passes with explicit main-looper pumping where needed.
- MQTT status notifications publish on the main lifecycle thread. Each result
  must still belong to the current start, so a delayed poll cannot recreate a
  notification after teardown/rejection or overwrite a newer start's status.
  The poll also stops the service and releases its resources when MQTT or
  foreground mode has been disabled since startup, at its next settings check.
- `MqttForegroundServiceTest` adds **seven API 28 regressions** with controlled
  state-read gates: status changes preserve one notification and its channel,
  action and ongoing flag; repeated starts reject stale results; destruction,
  rejected starts and re-enabled hosts reject cancelled polls; and each setting
  can disable a running service. Six cases failed against v1.0.19 before the fix.
- Full local Android validation: `:app:testDebugUnitTest --offline` passed with
  **366 tests across 38 JUnit suites**, zero failures/errors/skips, using the
  restored SDK/cache/API 28/29 runtimes, attached **Gradle 9.8.0-milestone-1** and
  **JBR 21.0.11**. Kotlin compilation emitted no warnings in this run. Attached
  Biome `check --write` passed on `docs/biome.json` without changes; Biome does
  not check Kotlin.
- Status and lifecycle tests still use a recording client proxy and Android
  shadows. Cold connection restoration, settings changes during actual connect,
  activity/service handoff, real transports and Android 14+ foreground rules
  remain unverified by these tests.

## MQTT restoration and loopback transport (v1.0.21)

- Foreground-service starts restore a `DISCONNECTED` MQTT manager even when its
  configuration was already initialised. A later start can recover after a broker
  rejection or corrected client settings. Existing connections and pending
  connect/reconnect attempts are preserved across repeated starts.
- Connected listeners and connection completions recheck the current MQTT-enabled
  preference. Disabling MQTT during a pending handshake closes a late success
  without publishing the connected event or subscribing. Reconnect scheduling and
  its delayed callback also recheck that preference.
- Host tests prepend the Kotlin compiler's original app classes to match their
  original JVM dependencies. Mixing Retrofix-rewritten app futures with HiveMQ's
  unmodified JVM futures previously caused a `ClassCastException` on actual
  connection completion. APK builds retain Retrofix and its backports.
- `MqttServiceConnectionTest` adds **13 test executions**: 12 scenarios with the
  cold-start case running on both **API 28 and 29**. A bounded MQTT 5 TCP peer
  binds an ephemeral loopback port and gates CONNACK delivery. Tests use the real
  HiveMQ client, production service callbacks and collectors, and Android
  preferences. They cover uninitialised/configured starts, repeated starts,
  rejected/invalid configuration recovery, native commands, settings application,
  request replies, response-topic/correlation properties, malformed-message
  recovery, disabling MQTT during connect and foreground-mode ownership changes.
  Four restoration/late-success regressions failed against v1.0.20 after the host
  test classpath was corrected, before the production fixes.
- Notification regressions gate background polls separately from lifecycle reads.
  Stale-result tests use a newer `CONNECTING` status so they preserve an in-flight
  connection while verifying notification ownership.
- Full local validation: `:app:testDebugUnitTest --offline` passed with **379 tests
  across 39 JUnit suites**, zero failures/errors/skips, using the restored
  SDK/cache/API 28/29 runtimes, attached **Gradle 9.8.0-milestone-1** and
  **JBR 21.0.11**. `:app:assembleDebug --offline` also built the debug APK. Kotlin
  compilation emitted no warnings. Attached Biome `check --write` passed on
  `docs/biome.json` without changes; Biome does not check Kotlin.
- This peer exercises plaintext MQTT 5 TCP with QoS 0/1, not a full external
  broker or UnifiedPush distributor. Clearing singleton fields models MQTT
  initialisation, not OS process death. JVM tests do not execute the installed,
  Retrofix-rewritten APK. Activity/service lifecycle handoff is covered by the
  v1.0.23 follow-up below; TLS/WebSocket broker delivery, process restarts and
  Android 14+ restrictions remain unverified.
  Manual device checks remain scheduled after automated coverage work.
- Follow-up validation: the user reported **v1.0.21 passes**, with the
  `Configuration.setVisible(boolean)` Gradle deprecation noted below.

## Compose authentication lifecycle and Gradle warning trace (v1.0.22)

- The authentication gate rechecks session validity when protected content
  changes. Its composables explicitly disable skipping because the clock is not
  Compose state and a content lambda can retain its identity while captured
  values change. Expired sessions cannot retain protected content through that
  recomposition, and the replacement prompt is requested exactly once.
- Lifecycle checks include `ON_RESUME`: a paused activity can resume without an
  `ON_START`. Valid sessions refresh without remounting protected content;
  expired sessions remove it and request authentication. Resuming after a user
  cancellation keeps the error and explicit Retry action. Lifecycle-state
  collection also refreshes rendering when an unsecured device establishes a
  new valid session with the same `AuthenticationNotSet` result.
- `RequireAuthWrapperTest` adds **18 test executions**: 17 scenarios on
  **Robolectric API 28**, with the expired pause/resume case also on **API 29**.
  They run the production wrapper in an attached `ComposeView`, assert its
  semantics and protected-content mount/disposal effects, and invoke the real
  Retry/Cancel semantics actions and navigation controller. A controlled frame
  clock drives recomposition without waiting on the spinner's infinite animation.
- Scenarios cover loading before start; successful, existing, expired and
  unsecured sessions; explicit lock; pause/resume and stop/start; valid refresh;
  changed content; repeated lifecycle/recomposition while a prompt is pending;
  error/retry/cancel; custom-password pending/success; wrapper disposal; and
  lifecycle-owner replacement. **Three API 28 failures were reproduced against
  v1.0.21** before the wrapper fix: expired pause/resume, expired content
  recomposition and renewal with an unchanged unsecured-authentication result.
- Full local Android validation: `:app:testDebugUnitTest :app:assembleDebug
  --offline` passed with **397 tests across 40 JUnit suites**, zero
  failures/errors/skips, and a debug APK. This used the restored SDK/cache/API
  28/29 runtimes, attached **Gradle 9.8.0-milestone-1** and **JBR 21.0.11**.
  Kotlin compilation emitted no warnings. Attached Biome `check --write`
  passed on `docs/biome.json` without changes; Biome does not check Kotlin.
- `:app:help --no-configuration-cache --warning-mode all
  -Dorg.gradle.deprecation.trace=true` traced `Configuration.setVisible(boolean)`
  to **AGP 9.4.1 internals**, including `BasePlugin`, `SourceSetManager` and
  `VariantDependenciesBuilder`. There is no project call to remove. An
  [upstream AGP change](https://android.googlesource.com/platform/tools/base/+/b54a369d23f52967d0073cb715c70a9c998f51c6)
  removes some deprecated usages, but that does not establish a complete fix in
  the configured AGP version. The warning remains visible pending a verified
  compatible plugin update; build-tool versions and warning settings are unchanged.
- These are Compose/Robolectric tests with a fake prompt and session clock,
  not APK instrumentation or biometric/credential hardware evidence. They do
  not exercise the full `MainActivity` route lifecycle, Android Keystore,
  process recreation or external-activity round trips in an installed app.
  Manual device checks remain deferred until automated coverage work is complete.

## Activity/service MQTT lifecycle handoff (v1.0.23)

- `MainActivity` registers its native MQTT command host and subscribes to settings
  and requests in `onCreate`, before `onStart` connects MQTT. The lifecycle scope
  owns one pair of collectors until destruction, independent of when Compose
  attaches. Each delivery rechecks that the activity is started, is the selected
  application-event host, has MQTT enabled and has foreground-service mode off
  before claiming it. The native command host uses the same eligibility check
  and unregisters on destruction.
- Destroying the last activity preserves an enabled, opted-in foreground
  service's live or pending connection. When MQTT or foreground-service mode is
  disabled, activity teardown disconnects and requests service shutdown. Disabling
  MQTT also prevents an obsolete foreground-service preference from keeping the
  last stopped activity's connection alive.
- `MqttActivityHandoffTest` adds **15 executions**: 14 scenarios on **Robolectric
  API 28**, with last-activity destruction while the service is active also on
  **API 29**. They use production `MainActivity` lifecycle callbacks and Compose
  content, production service collectors, the real HiveMQ client and the existing
  gated loopback MQTT 5 TCP peer. A controlled frame clock drives Compose.
- Cases cover first deliveries before UI composition; activity-to-service and
  service-to-activity handoff with both hosts alive; repeated service/activity
  starts; stop/start; last-activity destruction during and after connect;
  configuration-change replacement before its UI attaches; overlapping
  activities; collector and native command-host removal; and disabled/stopped
  eligibility. Batches assert one applied-settings notification and correlated
  response per ID, settings order, one native toast per command and reuse of one
  connection/three topic subscriptions. Typed flow injections exercise already
  decoded late deliveries and rejection of UnifiedPush source envelopes.
- **Seven executions (six scenarios) failed against v1.0.22** before the fix:
  startup/replacement collectors were missing before composition, disabled and
  stopped activities claimed settings, and destruction requested service stop.
  The latter covers a pending handshake and connected API 28/29 hosts.
- Full local validation: `:app:testDebugUnitTest :app:assembleDebug --offline
  --no-configuration-cache` passed with **412 tests across 41 JUnit suites**,
  zero failures/errors/skips, and a debug APK. This used restored SDK/cache/API
  28/29 runtimes, attached **Gradle 9.8.0-milestone-1** and existing **JBR 21.0.11**.
  Derived Gradle transforms were regenerated locally. Final Kotlin compilation
  emitted no warnings. Attached Biome `check --write` passed on `docs/biome.json`
  without changes; Biome does not check Kotlin.
- These JVM tests model configuration replacement using lifecycle callbacks and
  the configuration-change flag, not OS recreation/process death. The peer uses
  plaintext TCP/QoS 0/1. They do not verify the installed Retrofix-rewritten APK,
  WebView renderer callbacks, UI reload/navigation after remote settings, failed
  foreground-start fallback, Android 6 dual-task ownership, TLS/WebSocket brokers,
  a UnifiedPush distributor or Android 14+ restrictions. Manual device checks
  remain deferred until automated coverage work is complete.

## Foreground-start failure fallback (v1.0.24)

- An opted-in foreground-service preference no longer makes the activity yield
  before the service starts. The service records ownership only after foreground
  promotion and registration of its native command host and settings/request
  collectors. Started activities handle MQTT whenever no eligible service owns
  those deliveries, without changing the user's foreground-service preference.
- Rejected starts and service destruction immediately release that ownership,
  allowing an existing activity to take over. Cleanup checks the owner identity
  so an older service cannot revoke a replacement's ownership. Later accepted
  starts hand messages back to the service while preserving a live connection.
- The last activity's normal stop disconnects MQTT if no service has accepted
  ownership, including cancellation of a pending handshake. Stopped/disabled
  activities leave queued messages unclaimed. Configuration replacement retains
  the connection; an accepted service retains background ownership. Destruction
  does not cancel an opted-in service start request that is still queued.
- `MqttActivityHandoffTest` adds **12 executions**: ten scenarios on
  **Robolectric API 28**, with platform start-request denial and foreground
  promotion denial also on **API 29**. The suite now has **27 executions** and
  uses actual activity/service callbacks and Compose content with the real HiveMQ
  client and gated loopback MQTT 5 TCP peer. Platform request denial is injected
  by an activity override; foreground promotion denial uses the service shadow.
- Cases cover deliveries before UI/service startup; both denial paths; rejection
  after an accepted start; same-host recovery; service destruction/replacement;
  older-service cleanup; fallback stop/reconnect; pending-handshake cancellation;
  configuration replacement before composition; and disabling MQTT after denial.
  Delivery batches assert one settings application and correlated response per
  ID, settings order, one native toast and connection/subscription reuse across
  accepted handoffs. Resource checks verify wake-lock release and collector cleanup.
- **Ten executions (eight scenarios) failed against v1.0.23** before the fix:
  eligible activities dropped deliveries while waiting for a service, after
  either denial path, after rejection/destruction and after configuration
  replacement. An unowned connection also survived the last activity stopping
  or being destroyed during a pending handshake.
- Full local validation: `:app:testDebugUnitTest :app:assembleDebug --offline
  --no-configuration-cache` passed with **424 tests across 41 JUnit suites**,
  zero failures/errors/skips, and a debug APK. This used the restored SDK/cache/API
  28/29 runtimes, attached **Gradle 9.8.0-milestone-1** and **JBR 21.0.11**.
  Derived Gradle transforms were regenerated locally. Kotlin compilation emitted
  no warnings. Attached Biome `check --write` passed on `docs/biome.json` without
  changes; Biome does not check Kotlin.
- Denial, configuration replacement and older-service cleanup are controlled
  JVM lifecycle scenarios, not Android 14+ enforcement or OS process death.
  Installed APK/WebView integration, real TLS/WebSocket brokers, distributor
  delivery and device-owner restrictions remain unverified. Manual device checks
  remain deferred until automated coverage work is complete.

## Android 6 private-task routing and active-host transfer (v1.0.25)

- `MainActivity.onStop` now transfers the API 23 application-event owner to the
  most recently started *remaining* activity when its current owner stops. A
  stopped HOME/private-task host no longer suppresses the still-started host's
  MQTT claim gate, broadcast handling or Compose host. Stopping a non-owner does
  not steal ownership; when no activity remains started, the previous owner is
  retained for background UI/event policy as before.
- `Android6KioskLifecycleTest` adds **18 Robolectric API 28 executions** with
  the Android 6 SDK gate overridden to 23. Actual `MainActivity` and
  `Android6KioskActivity` lifecycle callbacks cover HOME vs launcher selection,
  deferral until resume, routing to the distinct task with correct flags and
  extras, explicit/manual/launch locking, HOME priority, queued-intent
  coalescing, one-shot consumption after unlock/restart and no private-to-private
  redirects. Overlapping tasks exercise start/stop/destroy ownership,
  reassignment, background retention, non-owner stop, and exactly one eligible
  MQTT activity host. Two host-transfer regressions **failed against v1.0.24**
  before the fix. The existing MQTT handoff fixture also connects its loopback
  peer before attaching the Compose UI in the first rejection/recovery case,
  removing a cold-rendering race with the peer's bounded CONNACK gate.
- Full local validation: `:app:testDebugUnitTest :app:assembleDebug --offline
  --no-configuration-cache` passed with **442 tests across 42 JUnit suites**,
  zero failures/errors/skips, and a debug APK. This used the restored SDK/cache
  and Robolectric API 28/29 runtimes, attached **Gradle 9.8.0-milestone-1** and
  **JBR 21.0.11**. Attached Biome `check --write` passed on `docs/biome.json`
  without changes; Biome does not check Kotlin.
- The tests use the API **28** Robolectric framework with only the SDK decision
  gate set to 23; they do **not** execute an API 23 framework, device-owner
  provisioning, actual Android HOME task transitions or physical lock-task
  enforcement. API 23 OEM/device checks remain required. Dhizuku readiness and
  permission-refusal scenarios on API 28+ remain untested.

## First installed-APK and browser automation phase (v1.0.26; historical)

The AndroidJUnitRunner sources, CI emulator jobs and release smoke test
introduced here were **removed in v1.0.33**. Only the docs Playwright tests
remain. The bullets below record what was attempted at that time.

- Added `app/src/androidTest` with AndroidJUnitRunner and a **disposable
  emulator-only** HTTP/WebView fixture. `RealWebViewTest` exercises a real
  WebView's redirect/history, SPA pushState, iframe 404, remote navigation/Home
  and activity recreation. `AuthenticationRouteDeviceTest` checks the real
  settings route's Compose accessibility content, custom-auth gate and re-lock
  after recreation; it completes the manager's custom prompt programmatically,
  **not** via the password field or a hardware credential dialog.
- `InstalledMqttWebViewTest` sends an actual MQTT 5/TCP publication into the
  installed APK and checks WebView navigation, settings import and an exactly-once
  correlated response. `ForegroundServiceDeviceTest` verifies a real service
  holds command/request ownership after its activity stops, and polls foreground
  notification removal after MQTT is disabled. A bounded on-device MQTT peer
  and loopback HTTP server remove reliance on external networks. They do **not**
  cover TLS/WebSockets, UnifiedPush distributors, process kills/restarts,
  notification-start denial or Android 14+ background-start restrictions.
- `Android6TaskTest` executes only on **actual API 23**, checking that HOME
  routing and the kiosk host occupy distinct task IDs. It does not provision
  a device owner or verify hardware lock-task enforcement.
- `docs/tests/e2e/provision-device.spec.ts` adds Chromium browser checks for
  switching GitHub/F-Droid/IzzyOnDroid sources and URLs, checksum consistency,
  advanced form values and invalid admin extras. The QR component exposes its
  submitted payload in a diagnostic data attribute; browsers still render the
  real QR SVG. Existing Vitest tests remain independent.
- `.github/workflows/tests.yaml` now has a reusable test gate, API 23/35
  emulator matrix and docs Playwright job, with test artifacts. Docs deployment
  runs Vitest and Chromium E2E. The tag-triggered release runs those same tests,
  builds one minified/signed APK, installs/launches **that artifact** in an API 35
  emulator and creates a draft release only after all gates pass.
- **Verification status:** this patch adds tests and CI definitions but does not
  claim they have passed. The local workspace had neither a usable Android SDK/
  dependency cache nor installed docs packages/browser; no installed APK or
  browser run was possible here. Static YAML and shell checks and the attached
  Biome `check --write` have been run. `docs/pnpm-lock.yaml` was deliberately
  left unchanged in that patch; the later-checked lockfile **already contains**
  `@playwright/test@1.56.1`. The 442-test JVM pass above
  is historical v1.0.25 evidence, **not** a v1.0.26 test result.

## Installed-APK CI findings and diagnostics (v1.0.27–v1.0.30; historical)

These findings are preserved to inform manual acceptance, not to imply any
instrumentation test or diagnostic tool remains in the current CI workflow.

- v1.0.27 adds JUnit failure extraction and best-effort device logcat capture;
  v1.0.28 moves the instrumentation command into a POSIX shell script because
  `reactivecircus/android-emulator-runner` executes multiline script input as
  separate commands. A CI rerun confirmed Gradle launches and the failure
  reporter prints the actual test assertion into the job log.
- API 35 initially reported **seven failures**, all from calling `WebView.url`
  on the instrumentation thread in `InstalledAppHarness.awaitUrl`.
  v1.0.29 moves the WebView lookup and URL read to `scenario.onActivity`.
  The following CI run reported only the authentication-dialog failure; that
  supports the threading correction but does not make the whole suite green.
- v1.0.30 searches app accessibility windows rather than only the focused
  window to find the password dialog. **Latest supplied API 35 CI run
  (12 October 2026, around 01:09 UTC) still fails exactly one test**:
  `AuthenticationRouteDeviceTest.settingsRemainProtectedBeforeUnlockAndRequireAuthAfterRecreation`
  with `Timed out waiting for visible custom-password dialog` at
  `AuthenticationRouteDeviceTest.kt:69`. Treat this as an open test/flow
  investigation; do not mark authentication instrumentation as passing.
- The supplied logs establish that compilation, emulator boot and JUnit
  reporting worked on API 35. They do not establish that API 23 or docs E2E
  passed, nor do they prove physical-device behaviour.

## Local automated CI parity (v1.0.32; updated v1.0.33)

- v1.0.32 originally introduced `scripts/ci/test-local.sh` with an optional
  emulator/device instrumentation mode. **v1.0.33 removes that mode**; only
  `checks`, `jvm`, `docs`, `docs-browser` and `all` remain. These run the same
  non-AVD test classes as CI without installing dependencies or needing AVDs.
- Local Gradle/Playwright tests cannot replicate GitHub-hosted signing secrets,
  pristine hosted images or runner action implementations. They can exercise
  Kotlin/Robolectric and docs tests before pushing, without an emulator.
- The API 35 authentication-dialog timeout seen in run 38190273493 is still
  unresolved, but **no longer an automated CI failure gate**. The actual
  custom-password dialog, recreation and unlock path must be assessed manually.

## CI test-run performance work (v1.0.31; updated v1.0.33)

- The last supplied API 35 log (12 October 2026, 01:01:43–01:09:29 UTC)
  recorded about 7m 46s, including 5m 55s of Gradle build/testing and 1m 23s
  of emulator setup. This is **historical**, not a benchmark for the new CI
  pipeline; removing emulator execution eliminates that entire job.
- The Android **JVM** job retains `gradle/actions/setup-gradle@v6` enhanced
  caching, `--build-cache`, `--profile` and an uploaded Gradle timing profile.
  Fork PRs are read-only for the Gradle cache. Dependency-cache hits alone do
  not prove task-output reuse or a wall-time improvement.
- v1.0.31 introduced API 23/35 clean AVD snapshot caching; **those jobs and
  snapshots are removed in v1.0.33** and no longer relevant to CI speed.
- Docs unit, browser and deployment jobs retain pnpm store caching keyed by
  `docs/pnpm-lock.yaml`, frozen installs and Chromium E2E. Browser binaries
  and system packages are intentionally not cached here.
- **Current scope:** improve and extend only non-AVD automated tests. Keep
  device, emulator, OEM and release-APK runtime checks as manual acceptance
  scenarios below. Do not claim a green CI run or measured speedup without
  post-v1.0.33 CI evidence.

## Remaining automated tests, ordered by regression risk

| Priority | Area / main code | Recommended tests and assertions | Layer |
| --- | --- | --- | --- |
| **P0** | Authentication (`managers/AuthenticationManager.kt`, `AuthenticationSession.kt`, `RequireAuthWrapper.kt`) | v1.0.34 host-replacement tests and v1.0.39's four custom-dialog identity/cancel/retry regressions are included in the **481-test local Gradle pass**; hosted CI remains pending. Continue expiry/lifecycle and native credential tests only where a deterministic coverage gap is identified. Actual custom-password dialog visibility, input, biometric/credential fallback and Keystore behaviour are **manual**; the historical API 35 dialog failure remains unresolved. | JVM/Robolectric and deterministic Compose host tests; manual on-device UI |
| **P0** | Navigation and active WebView (`utils/webview/WebviewNavigation.kt`, `WebViewNavigationSession.kt`, `ui/screens/WebviewScreen.kt`, `utils/createCustomWebview.kt`) | v1.0.35 URL/redirect regressions and v1.0.39's four idempotent-completion cases are included in the **481-test local Gradle pass**. Continue overlapping same-URL and renderer-generation coverage where deterministic; overlapping identical URLs without tokens remain ambiguous. Actual redirects, iframe failures, fullscreen teardown and renderer-death behaviour are **manual**. | Pure state and Robolectric; manual WebView UI |
| **P0** | UnifiedPush and MQTT/remote request/settings handoff (`managers/UnifiedPushManager.kt`, `MqttManager.kt`, `RemoteMessageManager.kt`, `MainActivity.kt`, `MqttForegroundService.kt`) | Keep 33 UnifiedPush registration/stream tests, JVM loopback MQTT TCP and ownership/fallback coverage through v1.0.24. v1.0.36–40 MQTT callback and subscription ownership regressions are included in the **487-test local Gradle pass** (hosted CI pending). v1.0.40 verifies same-client auto-resubscription against a real loopback TCP broker, logs rejected SUBACK codes, and rejects obsolete UnifiedPush registration errors. Continue cross-transport cancellation/flow tests only where real gaps remain, without duplicating claim/FIFO tests. Actual brokers, backgrounding, TLS/WebSocket and distributor behavior remain manual. | JVM MQTT TCP, coroutine and Robolectric; manual transport/device |
| **P0** | Foreground services (`services/MqttForegroundService.kt`, `LockTaskService.kt`) | Retain existing service/notification and ownership/handoff tests, plus v1.0.40's new delayed screen-broadcast suppression after service stop/disable. Extend deterministic tests for late connection or notification cleanup only where missing. Android 14+ foreground-start restrictions, OS process death and notification behaviour on real devices are **manual**. | Robolectric API 28/29 and JVM TCP; manual device |
| **P1** | Android 6 and owner initialisation (`Android6KioskActivity.kt`, `MainActivity.kt`, `utils/lockTaskUtils.kt`, `managers/DeviceOwnerManager.kt`) | Keep v1.0.25's 18 Robolectric SDK-gate tests; add modeled denial/retry and Dhizuku readiness/permission cases where practical. API 23 HOME/private task separation, provisioning and lock-task enforcement are manual. Overriding `SDK_INT` is not an API 23 runtime. | Robolectric policy; manual API 23/device owner |
| **P1** | PDF request/TLS paths (`handlers/handlePdfSourceRequest.kt`, `utils/webview/PdfTlsState.kt`) | Extend helper/unit regressions only for uncovered Range requests, redirects, cancellation, retry or certificate-state isolation. Actual PDF page rendering, Android KeyChain, HTTPS client certificates and WebView dialogs are manual. | JVM HTTP/TLS helpers; manual WebView/KeyChain |
| **P1** | Picker/MediaStore bridge (`createCustomWebview.kt`, `interfaces/BlobInterface.kt`, `fileChooserUtils.kt`) | Test URL/URI policy, transfer errors, cleanup and exactly-once callback state with fakes where possible. Actual camera intents, Android document provider, simultaneous downloads and pending MediaStore rows are manual. | Unit/Robolectric helpers; manual device |
| **P1** | Settings, external intents and Compose UI (`ui/components/setting/dialog/ImportSettingDialog.kt`, `utils/intentUtils.kt`, `SystemSafeDialogs.kt`, `SystemSafeClipboard.kt`) | Unit/Robolectric tests for malformed extras, invalid imports, denied actions and modeled lifecycle/state transitions. Actual keyboard focus, IME dismissal, dialogs and clipboard restrictions are manual. | Unit/Robolectric; manual UI |
| **P1** | Docs QR UI (`docs/src/components/qr/`) | Preserve v1.0.26 Chromium/Playwright and existing Vitest tests; add testable upstream-discovery error/fallback cases. Actual Android setup wizard and QR provisioning are manual. | Vitest and Playwright Chromium; manual QR scan |
| **P1** | CI and release (`.github/workflows/tests.yaml`, `release.yaml`, `docs.yaml`) | CI now runs JVM/Robolectric plus docs Vitest/Playwright and scripts; release builds the minified APK after the required reusable test workflow. No automated device/AVD test or release APK launch gate remains. Preserve Gradle/pnpm caches, capture current CI timings and track manual release sign-off independently. `docs/pnpm-lock.yaml` already contains Playwright and is unchanged. | Non-AVD CI; manual release runtime |

Prioritise the first four P0 items **before broadening already strong MQTT or
PDF HTTP helper suites**. Any new asynchronous test should control scheduling
and assert not just final state but absence of duplicate calls/events.

## Manual device/AVD acceptance (outside automated CI)

All scenarios below require a human tester. They are not optional implied CI
passes; maintain evidence independently before claiming hardware/runtime
release readiness. No AVD tests are created or scheduled in the automated
workstream.

| Priority | Device / environment | Manual acceptance scenario (not run by CI) |
| --- | --- | --- |
| **P0** | Real Android 6 / API 23, preferably the affected OEM | Provision as device owner; HOME launches the private kiosk task; locking does not immediately exit; unlock and HOME do not relock unexpectedly; hardware Back/Home and soft keyboard/system bars remain usable as configured. Reboot and repeat. |
| **P0** | Actual biometric/credential hardware, Android 6-10 and a modern Android version | Confirm that the **custom-password dialog is visible and unlocks** on API 35 (historical automation timed out); use secure PIN/pattern/password with no biometrics enrolled; cancel and retry; change enrollment to invalidate key; return from camera/file picker/external activity without bypassing auth; timeout while backgrounded. |
| **P0** | Android 14+ foreground-service restrictions, active MQTT broker and UnifiedPush distributor | Deny notifications/foreground start where supported; kill process then restart; disconnect/reconnect network; enable/disable service; verify one persistent notification, no stale wake lock, exactly-once command/settings handling and no background crash. |
| **P0** | Android 17 / API 37 device or compatible emulator | Deny then grant local-network permission; navigate to a LAN URL; retry after grant, while switching WebViews and after activity recreation. Check target-SDK permission behaviour rather than trusting API 28 Robolectric. |
| **P1** | API 21 (supported minimum), API 29, and a current Android release | Smoke core navigation, external file picker/camera, shared-storage access (legacy vs scoped), PDF viewer, download visibility and permissions. |
| **P1** | HTTPS server with real server/client certificates and a protected PDF | Correct TLS warning, cancel/proceed behaviour, certificate selection via Android KeyChain, no leaked approvals to a different host/viewer, credential prompt, reload and close. Check visual PDF page/background rendering. |
| **P1** | Minified release APK on a clean test device/emulator | Install the exact release artifact produced by CI, launch `MainActivity`, check that the process stays alive and the kiosk renders, and record device/API/version/signing evidence. This replaced the removed release-AVD smoke gate. |
| **P1** | Factory-reset provisionable device and live QR source | Scan the generated QR with Android Setup Wizard; validate actual APK URL, signature checksum, device-owner setup and resulting policy. Static QR JSON tests cannot establish successful provisioning. |
| **P1** | OEM devices with alternate IME, WebView and camera apps | Focus/dialog rendering, keyboard visibility, clipboard, fullscreen video, NFC/share intents and camera cancellation during lock/unlock and app switching. |

## Release readiness definition

1. Android JVM/Robolectric and docs Vitest/Chromium Playwright suites pass in
   CI on the commit being released; CI script checks pass too. Do not infer
   browser-test success from Android-test results.
2. P0 **non-AVD** regression tests above run successfully. The historical
   authentication-dialog timeout is not silently reclassified as fixed.
3. Manual device/AVD checks above have separately recorded pass/fail evidence,
   including API 23 lock-task, credentials/Keystore, foreground-service and
   API 37 permissions. No automated run substitutes for an OEM-specific check.
4. CI builds a minified release APK only after its non-AVD test gates pass.
   Installation and real launch must be **manually signed off**: the release
   workflow no longer performs the API 35 smoke test. Creating a draft release
   does not certify runtime readiness.
5. Record failures separately from **not yet tested**. A passing unit suite is
   not a quantitative statement of device, runtime or branch coverage.
