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
- Verification in this patch environment: all 15 session test methods passed
  in a standalone JDK 21 / Kotlin 2.4.10 harness with a minimal assertion shim,
  and all four changed Kotlin files passed parsing. **The Gradle/JUnit/Robolectric
  suite has not run**: the Gradle-cache attachment was unavailable; offline
  configuration could not resolve `foojay-resolver-convention:1.0.0`.
  The historical 197-test baseline above has not been re-certified for this patch.
- Still required: run `:app:testDebugUnitTest` with the complete exported cache;
  exercise `RequireAuthWrapper` lifecycle/rendering in Compose instrumentation;
  and perform the biometric/credential/keystore device scenarios below. Added
  tests and standalone checks do not close these release-readiness requirements.

## Next automated tests, ordered by regression risk

| Priority | Area / main code | Recommended tests and assertions | Layer |
| --- | --- | --- | --- |
| **P0** | Authentication (`managers/AuthenticationManager.kt`, `AuthenticationSession.kt`, `RequireAuthWrapper.kt`) | Run the added session/prompt regressions with the complete cache. Verify the Compose wrapper only renders protected content for a valid session, re-prompts on expired resume, and does not duplicate a pending prompt across lifecycle/recomposition. Verify actual credential fallback and invalidated Android Keystore keys on devices. See the authentication follow-up above for implemented cases and verification limits. | Unit/Robolectric for session/prompt logic; Compose instrumentation and emulator/device for actual credential UI and keystore |
| **P0** | Navigation and active WebView (`utils/webview/WebviewNavigation.kt`, `ui/screens/WebviewScreen.kt`, `utils/createCustomWebview.kt`) | Back/forward after redirect, removal and clearing; rapid concurrent navigation; repeated WebView-route transitions; stale `onPageFinished`/error callbacks; iframe vs main-frame navigation; programmatic-navigation flag does not suppress the wrong screen; fullscreen cleanup after screen change. | Robolectric for history; real WebView instrumentation for ownership and callbacks |
| **P0** | UnifiedPush and remote request/settings handoff (`managers/UnifiedPushManager.kt`, `RemoteMessageManager.kt`, `MainActivity.kt`, `MqttForegroundService.kt`) | Target instance/username filtering (including empty and mismatched sets); decrypted-message policy; malformed messages; command execution from a cold process; changing from activity host to service host; requests/settings claimed exactly once when both collectors exist; preserve per-stream order under concurrent deliveries. Existing command FIFO/claim tests need not be duplicated. | Robolectric with controlled collectors; one end-to-end transport test |
| **P0** | Foreground services (`services/MqttForegroundService.kt`, `LockTaskService.kt`) | Null-intent sticky restart; notification creation/updates; denial of foreground-service start; repeated starts; wake-lock and receiver cleanup on error/stop; settings toggle during connect; foreground-service ownership and no double-processed remote requests. | Robolectric service tests plus emulator/device lifecycle scenarios |
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
