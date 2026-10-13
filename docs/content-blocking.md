# Native content blocking (first increment)

**Status:** Network-only baseline. No default subscriptions, no automatic downloads,
no cosmetic injection, no bundled scriptlets. Import lists in **Settings → Content blocking**.
Blocking is enabled when at least one user-imported list is enabled.

## Engine and build

The JNI library in `app/native-adblock` uses Brave `adblock-rust` 0.13.3
(MPL-2.0) and the `jni` Rust crate 0.21.1. The Rust wrapper follows
this repository's AGPL-3.0-or-later license; distributors must also satisfy
the MPL-2.0 obligations for the upstream engine (license notice and source
availability).
Do not bundle filter lists. The native engine is lazy-loaded and holds no
compiled rules when all lists are disabled.

Building an APK requires an Android NDK installation, a Rust toolchain, Cargo,
and Rust's Android cross-compilation targets. The Gradle `buildNativeAdblock`
task builds these targets and includes the resulting `.so` files in the APK.
It reads `ANDROID_NDK_HOME`, `ANDROID_NDK_ROOT`, or an SDK `ndk` directory.
The build is deliberately not allowed to silently package an APK with a
missing native engine. The release GitHub Actions workflow provisions Rust
Android targets and a pinned Android NDK; for local builds use an NDK and
Rust targets installed for the host OS. Host JVM/Robolectric tests can run
without an NDK.

The engine is compiled on a single background executor and then swapped into
use under a short native write lock. Request matching takes a native read lock
and does not parse rules, access preferences, or download data. A failed
native load **allows requests** and surfaces the error in the settings screen.

## Import safeguards

- Lists are selected explicitly through Android's document picker, copied to
  the app's private directory, and never fetched automatically.
- Each file must be UTF-8, nonempty, and no larger than 8 MiB. Storage is
  bounded to 16 lists / 32 MiB total; imports are written to temporary files
  and committed with the list metadata.
- The original text is retained for rebuilding on startup. No compiled
  binary cache is persisted in this first increment. Imported list files are
  not yet included in the app's existing settings JSON/Base64 export.
- Removing or disabling the last list unloads native engine state.

## Known WebView limitations

`WebViewClient.shouldInterceptRequest` does not expose reliable subresource
kinds or initiating frames. This integration passes `Sec-Fetch-Dest` or `Accept`
when present, otherwise `other`. It uses the last accepted top-level page URL
as a *best-effort* initiator, rather than inventing a nested frame's origin.
Top-level navigations are intentionally left alone. `javascript:`, `blob:`,
and subsequent redirect destinations cannot be intercepted using this API.
Service worker fetches are not covered in this increment.

Network blocking and exception rules can work today; cosmetic filters,
scriptlets, `$redirect` resources, response-body modification, per-site
allowlists, subscriptions, and downloads are intentionally out of scope.

## Verification

Robolectric tests exercise persistence, import limits, enable/disable/reload,
and resource classification with a fake native matcher. Native Rust tests
cover blocking, exceptions, hosts lists, and malformed requests; Android
cross-compilation and real-device WebView tests are still required before
claiming production-level compatibility or latency.
