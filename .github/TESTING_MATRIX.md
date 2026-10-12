# Regression-testing matrix after v0.26.21

Snapshot: `further-testing` at `ba7cb40a` compared with the v0.26.21 release commit
`aa7ceca9`. This is an assessment of test *coverage*, not a finding that the
untested paths are defective. The app still declares `versionName = "0.26.21"`.

## Current automated-test gate status (12 October 2026)

| Gate | Most recent evidence | Status |
| --- | --- | --- |
| Android JVM/Robolectric | 442 tests across 42 suites passed at v1.0.25; no newer JVM-job result was supplied | Last verified pass is historical; current CI outcome unknown |
| Installed APK, API 23 | API 23 matrix is defined, but no completed job report was supplied | Unverified |
| Installed APK, API 35 | [CI run 38190273493](https://github.com/nktnet1/webview-kiosk/actions/runs/38190273493), after v1.0.30: one failing custom-password dialog visibility assertion | **Failing**, not fixed in v1.0.31 or v1.0.32 |
| Docs Vitest / Chromium Playwright | Jobs are defined; their passing output was not supplied | Unverified |
| Caching / speed | v1.0.31 configuration is staged; no post-change run timings were supplied | Unverified speedup |
| Local parity | v1.0.32 offline mocked dispatch/regression checks pass; emulator absent in this environment | Local orchestration verified, on-device runs pending |

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

## First installed-APK and browser automation phase (v1.0.26)

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
  left unchanged; it must be regenerated for the new `@playwright/test` dev
  dependency before CI's frozen install can succeed. The 442-test JVM pass above
  is historical v1.0.25 evidence, **not** a v1.0.26 test result.

## Installed-APK CI findings and diagnostics (v1.0.27–v1.0.30)

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

## Local automated CI parity and open authentication failure (v1.0.32)

- **Latest supplied CI evidence (12 October 2026, 01:09 UTC): API 35 is still RED.**
  `AuthenticationRouteDeviceTest.settingsRemainProtectedBeforeUnlockAndRequireAuthAfterRecreation`
  times out waiting for the `visible custom-password dialog`, despite the
  v1.0.30 accessibility-window change. Its earlier pending-authentication
  precondition passed, but the XML/log excerpt does not establish whether the
  Compose dialog was rendered, obscured by the keyboard, or undetectable via
  accessibility. This is **not fixed** by the v1.0.31 CI cache changes or by
  this local test-runner patch. Do not count the instrumentation gate as green.
- `scripts/ci/test-local.sh` provides dependency-free *orchestration* of the same
  local test tasks as CI, without downloading tools or replacing GitHub Actions.
  Run its `--help` for prerequisites and supported test selectors. Its suites:
  `checks` (exporter shell tests plus all Python regressions), `jvm`
  (`:app:testDebugUnitTest --build-cache --profile --stacktrace`), `android`
  (the **same CI wrapper**, `:app:connectedDebugAndroidTest`), `docs` (Biome
  check and Vitest), `docs-browser` (production docs build and Playwright), and
  `all` (all five, sequentially). The Android suite can filter one class or
  method using `--class` and require `--api 23` / `--api 35`.
- For the current API 35 failure, first boot an **isolated disposable API 35**
  emulator with Android Studio Device Manager, then run
  `sh scripts/ci/test-local.sh android --api 35 --class AuthenticationRouteDeviceTest`.
  To limit even further, use `--class AuthenticationRouteDeviceTest#settingsRemainProtectedBeforeUnlockAndRequireAuthAfterRecreation`.
  The runner refuses to mutate a host with zero or multiple connected devices,
  validates the requested SDK level, and keeps JUnit extraction and logcat on
  failure. It does not launch an AVD, install Android/Node tooling or select a
  hardware ABI. An API 35 arm64 emulator on Apple Silicon exercises the same
  Android API but **not** CI's `google_apis/x86_64` system image. CI's API 23
  target is `default/x86`; reproducing its ABI may require a Linux/Intel host.
- A local Gradle/Playwright run does not reproduce GitHub-hosted secrets,
  release-signing gates, pristine VM images, runner action code or emulator
  snapshot caching. `act` can run some workflow steps via Docker, but emulator
  acceleration is often unavailable in nested containers. Prefer native AVD +
  Gradle for debugging instrumentation. The script is intentionally not used to
  skip or weaken existing CI gates.
- **Validation:** 22 offline Python checks pass, including local CLI dispatch,
  API mismatch and multiple-device guards, class-filter forwarding, and CI
  wrapper exit-code preservation. Android instrumented tests, docs dependencies
  and AVD parity could not be executed in this local environment. API 35 remains
  an outstanding failure pending a real emulator run and further diagnostic
  evidence; v1.0.31 cache-hit and wall-clock improvements remain unmeasured.

## CI test-run performance work (v1.0.31)

- The supplied API 35 CI log (`12 October 2026`, 01:01:43–01:09:29 UTC)
  shows about **7m 46s wall time**: roughly **5m 55s Gradle** (including
  **1m 35s device tests**), **1m 23s emulator installation/boot**, and the
  remainder for Java/cache setup and reporting. All 73 Gradle tasks executed;
  Gradle was configured for configuration caching but **task-output build
  caching was not enabled**.
- Gradle jobs now use `gradle/actions/setup-gradle@v6` enhanced caching rather
  than also caching Gradle User Home with `actions/setup-java`. The unit and
  installed-APK commands request `--build-cache` and `--profile`. The
  generated Gradle timing profile is uploaded with test reports, so task-cache
  hits and elapsed times can be compared before claiming any speedup.
  Same-repository PRs may write Gradle cache entries; fork PRs are read-only.
- Both emulator variants cache a **clean, pre-test AVD snapshot** keyed by
  OS/API/system-image target/architecture/profile. A cache miss first boots a
  disposable AVD to create the snapshot, saves it **before** instrumentation,
  then runs with `-no-snapshot-save` so test state is not persisted. The
  cache restore/save steps are best-effort and never suppress test failures.
  First runs can therefore be **slower**. Snapshot caches may be evicted or become
  stale when the emulator binary or Android image changes; increment the
  `avd-v1` prefix to force a clean snapshot if that occurs.
- The reusable docs unit/browser jobs and docs-deployment test gate now cache
  pnpm's package store keyed by `docs/pnpm-lock.yaml`. They still run a frozen
  install, Vitest and/or Playwright as before. Chromium binaries and Linux
  system packages are deliberately **not** cached: Playwright advises that
  binary-cache restore time is often comparable to downloading them.
- **Verification:** 10 offline Python regression checks (including three
  new caching invariants) and the Android asset-exporter shell tests pass;
  both workflow files parse as YAML with their step ordering intact. Biome
  `check --write` on the docs config passes (Biome does not format YAML or
  Kotlin). Runtime speedups, cache-hit rates, emulator
  snapshot compatibility (API 23 and API 35), and fully green CI **remain
  unverified** until subsequent GitHub Actions runs. The v1.0.30 API 35
  password-dialog failure remains open independently of these changes.
- **Current scope:** prioritise repeatable automated tests, instrumentation,
  browser E2E, test diagnostics and CI efficiency. Manual/OEM device checks
  below remain a deferred release-readiness backlog, not active tasks.

## Remaining automated tests, ordered by regression risk

| Priority | Area / main code | Recommended tests and assertions | Layer |
| --- | --- | --- | --- |
| **P0** | Authentication (`managers/AuthenticationManager.kt`, `AuthenticationSession.kt`, `RequireAuthWrapper.kt`) | Session/prompt tests added in v1.0.15 passed. The 18 Compose/Robolectric checks in v1.0.22 verify protected-content gating, expired resume/recomposition, prompt deduplication, retry/cancel and effect cleanup. v1.0.26 instruments the installed settings route and custom-password gate. API 35 CI has executed it but the password-dialog visibility assertion still times out after v1.0.30; this automated test is open, not passing. Still test the actual custom-password UI, cancellation/retry, external-activity return, device-credential fallback and invalidated Android Keystore keys on devices. See the authentication follow-ups for verification limits. | Unit/Robolectric for sessions, prompts and Compose semantics/lifecycle; APK instrumentation and emulator/device for route integration, actual credential UI and keystore |
| **P0** | Navigation and active WebView (`utils/webview/WebviewNavigation.kt`, `WebViewNavigationSession.kt`, `ui/screens/WebviewScreen.kt`, `utils/createCustomWebview.kt`) | The 61 added history/session/preference regressions passed in the Android suite after v1.0.17. v1.0.26 adds real WebView redirects, SPA pushState, iframe errors, remote Home/navigation and recreation. The supplied post-v1.0.29 API 35 run reports no failures in these test cases, while the full instrumentation job remains red from authentication. Still test rapid repeated routes, SPA pushes *before* page finish, stale/duplicate callbacks (including same-URL), renderer death, delayed URL events and fullscreen teardown. See the navigation and cursor follow-ups above for implemented cases and verification limits. | Robolectric for history/preferences; pure state tests for policy; real WebView instrumentation for ownership and callbacks |
| **P0** | UnifiedPush and remote request/settings handoff (`managers/UnifiedPushManager.kt`, `RemoteMessageManager.kt`, `MainActivity.kt`, `MqttForegroundService.kt`) | The 33 registration/filtering/settings/request regressions passed in v1.0.18. v1.0.21 verifies MQTT TCP delivery through service collectors; v1.0.23 adds 15 actual activity/service handoff executions, and v1.0.24 adds 12 for start denial, activity fallback and service recovery. v1.0.26 adds installed-APK MQTT/TCP navigation, settings import and correlated request replies, plus background service command ownership; the supplied post-v1.0.29 API 35 run reports no failures in these cases, but the overall job still fails authentication. Still test settings-triggered UI reload, cold-process startup, device-owner commands, a real UnifiedPush distributor and TLS/WebSocket brokers. Existing command FIFO/claim tests need not be duplicated. See the remote-stream/loopback/handoff/fallback follow-ups for limits. | Robolectric activity/Compose/service lifecycle and real JVM MQTT TCP; coroutine tests for flows; APK instrumentation and on-device transport tests |
| **P0** | Foreground services (`services/MqttForegroundService.kt`, `LockTaskService.kt`) | The 45 service checks passed through v1.0.21, covering resources/notifications, MQTT restoration, repeated starts, connection rejection/recovery, disabling MQTT during connect and loopback delivery. The 27 handoff executions through v1.0.24 verify live/pending connection ownership, service toggles, actual activity collector cleanup, modeled start denial/fallback/recovery and older-service cleanup. v1.0.26 exercises one real Android foreground-service handoff, background command/request delivery and notification teardown. The supplied post-v1.0.29 API 35 run reports no failures in these cases, but overall instrumentation is still red. Still verify live reconnection, denied starts, Android 14+ background-start restrictions and OS process death/restart. See the service/notification/loopback/handoff/fallback follow-ups for limits. | Robolectric activity/service callbacks on API 28/29 and real JVM MQTT TCP; APK instrumentation and emulator/device lifecycle/transport scenarios |
| **P1** | Android 6 and owner initialisation (`Android6KioskActivity.kt`, `MainActivity.kt`, `utils/lockTaskUtils.kt`, `managers/DeviceOwnerManager.kt`) | v1.0.25 adds 18 SDK-gate/Robolectric checks for HOME vs launcher, one-shot lock requests, selected host and handoff on stop. v1.0.26 adds actual API 23 HOME/private task-ID separation (pending CI execution). Still test unlock/HOME/recreation, owner permission/refusal and pinned-to-locked transitions; test Dhizuku delayed readiness/permission refusal on API 28+. Do not regard an overridden SDK gate as an API 23 runtime. | Robolectric API 28 policy + API 23 device/emulator |
| **P1** | Real PDF viewer + certificate paths (`handlers/handlePdfSourceRequest.kt`, `utils/webview/PdfTlsState.kt`, PDF HTML) | Load a PDF in an actual WebView with Range requests, redirects and auth; show real pages, recover on retry, close old viewer requests; validate untrusted/expired TLS decisions and a local HTTPS server requesting an mTLS certificate. HTTP helper unit cases are already extensive. | Instrumented WebView + controlled HTTP(S) test server |
| **P1** | Picker/MediaStore bridge (`createCustomWebview.kt`, `interfaces/BlobInterface.kt`, `fileChooserUtils.kt`) | Actual file chooser cancellation and return; camera failure fallback returns exactly once; kill/swap WebView during pending selection; two simultaneous blob downloads leave correct bytes in the Android provider; failures do not leave pending MediaStore rows. Do not repeat the existing URI policy/transfer-helper tests. | Instrumentation with fake document provider and device camera smoke |
| **P1** | Settings, external intents and Compose UI (`ui/components/setting/dialog/ImportSettingDialog.kt`, `utils/intentUtils.kt`, `SystemSafeDialogs.kt`, `SystemSafeClipboard.kt`) | Invalid parcelables/intent extras, oversized or interrupted settings import, denial/cancellation, focus request after layout, IME dismissal on Android 6, and clipboard access while a restricted window is active. | Unit/Robolectric for parsers; Compose instrumentation for UI |
| **P1** | Docs QR UI (`docs/src/components/qr/`) | v1.0.26 adds Chromium assertions for source URLs, encoded payload, checksum and invalid JSON warning (pending CI execution). Still cover unreachable upstream endpoints at build/discovery time and scanned QR provisioning on hardware. | Vitest existing helpers + Playwright/browser E2E |
| **P1** | CI and release (`.github/workflows/tests.yaml`, `release.yaml`, `docs.yaml`) | v1.0.26 wires tag release to reusable unit/emulator/browser checks, docs Vitest+E2E deployment gates, minified APK install/launch and report uploads. API 35 CI executes but is red on the custom-password visibility test after v1.0.30; API 23 and docs CI results were not supplied. The existing `docs/pnpm-lock.yaml` contains `@playwright/test@1.56.1`; do not regenerate it only for that dependency. v1.0.31 adds Gradle build caching/profiles, clean AVD snapshots, and pnpm store caches; runtime savings are not yet measured. | CI |

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
