# Management and Lock Task device validation

The automated `ProfileOwnerManagementTest` uses Android 9, 10, 11, 13, and 17
Robolectric runtimes with injected service permission failures and a fake
Dhizuku transport. Tests exercise the production manager, permission callbacks,
allowlist edits, feature dependencies, and locking utilities. A separate
compatibility suite runs on Android 6, 7, 8, and 8.1 runtimes.
Android 5 gates are exercised against a newer runtime. Those are version-guard
tests, not Android 5 emulation. The recording activity does not emulate
ActivityTaskManager. Provisioning-result tests verify fully
managed mode selection and cancellation of unsupported work-profile-only flows
on Android 10, 13, and 17.

`AuthenticationManagerTest` exercises system-success callbacks on Android 9–11,
including device credentials in a simulated work profile and stale/cancelled
results. Real biometric hardware and work-challenge handling use the device
checks below.

Real provisioning, OEM policy enforcement, affiliation transitions, and Dhizuku
Binder identity forwarding need a device/emulator. Do not describe the following
matrix as passed until it has been run. Use disposable test devices and record
Android version, OEM/build, package source, full user ID, owner component,
Dhizuku version, ADB result, and actual `dumpsys activity` Lock Task state.

| Device/API                              | Cases                                                                           | Expected result                                                                                                                                                                             |
| --------------------------------------- | ------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Android 5–7.1 / 21–25                   | Native Device Owner, pre-existing allowlist, unmanaged app                      | Existing entries survive self-allowlisting if the legacy getter is available, unsupported getter leaves policy untouched. No feature setter calls. Detailed lock state is Unknown on 21–22. |
| Android 8–8.1 / 26–27                   | Native Device Owner, affiliated secondary-user PO, independent PO               | Public package enumeration, affiliated secondary-user policies allowed by Android, independent PO has no assumed Lock Task access, features disabled.                                       |
| Android 9–10 / 28–29                    | Native and Dhizuku Device Owner, native and Dhizuku full-user PO without DO     | Correct source/owner identity, full-user scope through the owner API, packages and features work, external entries preserved.                                                               |
| Android 11–12L / 30–32                  | Same modes, ordinary personal/work-profile copies, affiliated user with DO      | Correct `UserManager` scope, ordinary work-profile policy controls disabled, profile lock cannot be presented as whole-device lock. Advanced activity-start flag is gated to 30+.           |
| Android 13+ / 33+ through target API 37 | Same modes, non-work profile types where available, policy coexistence/conflict | Other profile types are distinguished, denied policy reads/writes are safe, resolved policy and actual allowlisting decide behaviour.                                                       |
| Headless-system-user device             | Foreground full user has an ID other than 0                                     | Detection uses profile APIs, not user-0 identity, ADB enrollment uses the selected full-user ID.                                                                                            |

For each eligible owner mode:

1. Install the intended app variant and provision the native app or Dhizuku with
   the ADB command from the security guide. Check the installed receiver with
   package manager if using a custom build. Verify owner assignment with
   `adb shell dumpsys device_policy`. Guest users, existing ownership, and
   incompatible accounts must yield setup errors, not forced enrollment.
2. For Dhizuku, test ungranted, granted, denied, revoked, dead service, resumed
   service, duplicate results, and a late callback after another request/owner
   replaces it. Native ownership must continue to take precedence.
3. Allowlist another installed app and an uninstalled package before opening
   settings. Add/remove a third app. Confirm the original entries survive. Make
   a second policy change while a package dialog is open and repeat the edit.
4. Lock and verify `LOCK_TASK_MODE_LOCKED` / `get_system_info` response `data.app.lockState` = `LOCK_TASK`. Remove policy
   authority or allowlisting and verify the reported state is not silently
   labelled Lock Task. An ordinary unmanaged or work-profile app can request
   pinning. Approve it manually and confirm `SCREEN_PINNING`.
5. Toggle Lock Task features. Home must accompany Overview/Notifications, and
   the activity-start restriction must be absent before API 30. Deny a setter
   or revoke permission while a dialog is open: no crash and no false success.
6. Launch another allowlisted app, unlock, and confirm the full package list and
   feature flags are restored. Also test a failed temporary allowlist write.
7. Let an external DPC allowlist Webview Kiosk without Dhizuku permission. Lock,
   launch another permitted app, and unlock without changing the DPC policy.
8. Verify `lock_device` only for a full-user owner declaring force-lock. Ordinary
   work profiles must not expose it as a whole-device command.
9. On a disposable full user, test native ownership transfer and the appropriate
   clear-owner action. Work-profile clear-owner and shared-owner transfer/clear
   must not be offered. A transferred PO must remain a PO.
10. Scan Device Owner QR codes during supported fresh-device provisioning.
    Confirm the QR tool retains its Device Owner form without a management-mode
    selector. Test Google Play, GitHub/F-Droid, and Dhizuku receiver names
    separately for ADB. Confirm account/owner restrictions remain enforced and
    use the selected full-user ID rather than assuming 0.
11. Check the Management heading and readable values in About. Verify the Device
    Policies card separates owner role, Native/Dhizuku source, and user scope.
    Unavailable Lock Task fields must explain the version or permission reason
    in both the list and read-only dialog. Only app restrictions show Restricted.
    Revoke policy access while a feature dialog is open and confirm Save is
    unavailable. Apply an app restriction and confirm it cannot be edited.

Primary references and setup instructions are linked in
`docs/content/docs/security.mdx`.

For authentication regression checks, test both the personal and work copies
with unified and separate work-profile screen locks. A successful biometric or
PIN/pattern/password prompt must open settings without a Keystore operation.
Cancel, lock out, replace the activity, or lock the kiosk while a prompt is open.
Late callbacks must not authenticate a newer request or revive an expired
session. Also verify custom passwords and Android 5 credential fallback.
