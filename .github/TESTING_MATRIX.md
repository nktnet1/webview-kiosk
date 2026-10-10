# Regression-testing matrix after v0.26.21

Snapshot: `further-testing` at `ba7cb40a` compared with the v0.26.21 release commit
`aa7ceca9`. This is an assessment of test *coverage*, not a finding that the
untested paths are defective. The app still declares `versionName = "0.26.21"`.

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
  Retrofix-rewritten APK. Activity/service lifecycle handoff, TLS/WebSocket broker
  delivery, process restarts and Android 14+ restrictions remain unverified.
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

## Next automated tests, ordered by regression risk

| Priority | Area / main code | Recommended tests and assertions | Layer |
| --- | --- | --- | --- |
| **P0** | Authentication (`managers/AuthenticationManager.kt`, `AuthenticationSession.kt`, `RequireAuthWrapper.kt`) | Session/prompt tests added in v1.0.15 passed. The 18 Compose/Robolectric checks in v1.0.22 verify protected-content gating, expired resume/recomposition, prompt deduplication, retry/cancel and effect cleanup. Instrument the full APK's settings/navigation lifecycle, external-activity return and recreation; verify actual credential fallback and invalidated Android Keystore keys on devices. See the authentication follow-ups for verification limits. | Unit/Robolectric for sessions, prompts and Compose semantics/lifecycle; APK instrumentation and emulator/device for route integration, actual credential UI and keystore |
| **P0** | Navigation and active WebView (`utils/webview/WebviewNavigation.kt`, `WebViewNavigationSession.kt`, `ui/screens/WebviewScreen.kt`, `utils/createCustomWebview.kt`) | The 61 added history/session/preference regressions passed in the Android suite after v1.0.17. Instrument repeated routes, rapid navigation and redirects, SPA pushes before page finish, stale/duplicate callbacks (including repeated identical URLs), iframe vs main-frame errors, renderer recreation, delayed MQTT URL events and fullscreen cleanup. See the navigation and cursor follow-ups above for implemented cases and verification limits. | Robolectric for history/preferences; pure state tests for policy; real WebView instrumentation for ownership and callbacks |
| **P0** | UnifiedPush and remote request/settings handoff (`managers/UnifiedPushManager.kt`, `RemoteMessageManager.kt`, `MainActivity.kt`, `MqttForegroundService.kt`) | The 33 registration/filtering/settings/request regressions passed in v1.0.18. v1.0.21 additionally verifies MQTT TCP command/settings/request delivery through production service collectors. Exercise a real distributor, OS cold-process initialisation, device-owner commands and activity-to-service host transitions; verify exactly-once handling with actual activity lifecycle collectors and concurrent transport deliveries. Existing command FIFO/claim tests need not be duplicated. See the remote-stream and loopback follow-ups for limits. | Robolectric for manager/preferences and loopback MQTT; coroutine tests for flow mechanics; lifecycle instrumentation and on-device transport tests |
| **P0** | Foreground services (`services/MqttForegroundService.kt`, `LockTaskService.kt`) | The 45 service checks passed through v1.0.21, covering lifecycle resources/notifications, uninitialised and disconnected MQTT restoration, repeated starts, connection rejection/recovery, disabling MQTT during connect and loopback delivery. Verify activity/service host transitions, broker reconnects and exactly-once handling through real transports; exercise Android 14+ foreground restrictions and OS process death. See the service/notification/loopback follow-ups for limits. | Robolectric service callbacks on API 28/29 and real JVM MQTT TCP; emulator/device lifecycle and transport scenarios |
| **P1** | Android 6 and owner initialisation (`Android6KioskActivity.kt`, `MainActivity.kt`, `utils/lockTaskUtils.kt`, `managers/DeviceOwnerManager.kt`) | HOME vs launcher intent detection, one-shot lock request, host selection, task-switch transition, Dhizuku delayed readiness/permission refusal and activity recreation. Do not regard Robolectric as proof of actual API 23 lock task. | Robolectric + API 23 device/emulator |
| **P1** | Real PDF viewer + certificate paths (`handlers/handlePdfSourceRequest.kt`, `utils/webview/PdfTlsState.kt`, PDF HTML) | Load a PDF in an actual WebView with Range requests, redirects and auth; show real pages, recover on retry, close old viewer requests; validate untrusted/expired TLS decisions and a local HTTPS server requesting an mTLS certificate. HTTP helper unit cases are already extensive. | Instrumented WebView + controlled HTTP(S) test server |
| **P1** | Picker/MediaStore bridge (`createCustomWebview.kt`, `interfaces/BlobInterface.kt`, `fileChooserUtils.kt`) | Actual file chooser cancellation and return; camera failure fallback returns exactly once; kill/swap WebView during pending selection; two simultaneous blob downloads leave correct bytes in the Android provider; failures do not leave pending MediaStore rows. Do not repeat the existing URI policy/transfer-helper tests. | Instrumentation with fake document provider and device camera smoke |
| **P1** | Settings, external intents and Compose UI (`ui/components/setting/dialog/ImportSettingDialog.kt`, `utils/intentUtils.kt`, `SystemSafeDialogs.kt`, `SystemSafeClipboard.kt`) | Invalid parcelables/intent extras, oversized or interrupted settings import, denial/cancellation, focus request after layout, IME dismissal on Android 6, and clipboard access while a restricted window is active. | Unit/Robolectric for parsers; Compose instrumentation for UI |
| **P1** | Docs QR UI (`docs/src/components/qr/`) | Browser-level validation that changing sources updates the QR/download URL and checksum, field edits update encoded payload, validation errors render, and inaccessible upstream endpoints retain safe source-specific fallbacks. | Vitest existing helpers + Playwright/browser E2E |
| **P1** | CI and release (`.github/workflows/tests.yaml`, `release.yaml`, `docs.yaml`) | Gate the tag-triggered release workflow on passing automated tests; verify `assembleRelease` and launch the minified APK; enforce docs Vitest as a condition of deployment, not just formatting/build checks. Capture device-test reports as artifacts. | CI |

Prioritise the first four P0 items **before broadening already strong MQTT or
PDF HTTP helper suites**. Any new asynchronous test should control scheduling
and assert not just final state but absence of duplicate calls/events.

## Device-level checks that cannot be certified by JVM tests

| Priority | Device / environment | Manual acceptance scenario (automate the repeatable portion where possible) |
| --- | --- | --- |
| **P0** | Real Android 6 / API 23, preferably the affected OEM | Provision as device owner; HOME launches the private kiosk task; locking does not immediately exit; unlock and HOME do not relock unexpectedly; hardware Back/Home and soft keyboard/system bars remain usable as configured. Reboot and repeat. |
| **P0** | Actual biometric/credential hardware, Android 6-10 and a modern Android version | No biometrics enrolled but secure PIN/pattern/password configured; cancel and retry; change enrollment to invalidate key; return from camera/file picker/external activity without bypassing auth; timeout while backgrounded. |
| **P0** | Android 14+ foreground-service restrictions, active MQTT broker and UnifiedPush distributor | Deny notifications/foreground start where supported; kill process then restart; disconnect/reconnect network; enable/disable service; verify one persistent notification, no stale wake lock, exactly-once command/settings handling and no background crash. |
| **P0** | Android 17 / API 37 device or compatible emulator | Deny then grant local-network permission; navigate to a LAN URL; retry after grant, while switching WebViews and after activity recreation. Check target-SDK permission behaviour rather than trusting API 28 Robolectric. |
| **P1** | API 21 (supported minimum), API 29, and a current Android release | Smoke core navigation, external file picker/camera, shared-storage access (legacy vs scoped), PDF viewer, download visibility and permissions. |
| **P1** | HTTPS server with real server/client certificates and a protected PDF | Correct TLS warning, cancel/proceed behaviour, certificate selection via Android KeyChain, no leaked approvals to a different host/viewer, credential prompt, reload and close. Check visual PDF page/background rendering. |
| **P1** | Factory-reset provisionable device and live QR source | Scan the generated QR with Android Setup Wizard; validate actual APK URL, signature checksum, device-owner setup and resulting policy. Static QR JSON tests cannot establish successful provisioning. |
| **P1** | OEM devices with alternate IME, WebView and camera apps | Focus/dialog rendering, keyboard visibility, clipboard, fullscreen video, NFC/share intents and camera cancellation during lock/unlock and app switching. |

## Release readiness definition

1. Android JVM suite passes, docs Vitest suite passes, and both run in CI on the
   commit being released. Do not infer docs-test success from Android-test results.
2. P0 regression tests above are added and pass; at least one instrumented
   end-to-end test covers real WebView navigation and remote message delivery.
3. Physical API 23 lock-task, credential/keystore, foreground-service and API 37
   permission scenarios have recorded pass/fail evidence with OS, WebView and
   device-owner configuration. An emulator cannot replace the OEM-specific checks.
4. A minified release APK builds, installs and launches, and the tag-triggered
   release cannot publish a draft before required test gates have passed.
5. Record failures separately from **not yet tested**. A passing unit suite is
   not a quantitative statement of runtime or branch coverage.
