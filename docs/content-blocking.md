# Native content blocking (first increment)

**Status:** Network-only baseline. No automatic downloads or updates, no cosmetic
injection, no bundled filter rules or scriptlets. Manage lists in **Settings → Web Content →
Content Blocking**. Blocking is enabled when at least one user-imported or explicitly
downloaded list is enabled.

## Engine and build

The JNI library in `app/native-adblock` uses Brave `adblock-rust` 0.13.3
(MPL-2.0) and the `jni` Rust crate 0.21.1. The Rust wrapper follows
this repository's AGPL-3.0-or-later license; distributors must also satisfy
the MPL-2.0 obligations for the upstream engine (license notice and source
availability).
Do not bundle filter lists. The native engine is lazy-loaded and holds no
compiled rules when all lists are disabled.

Building an APK requires **Android NDK 29.0.14206865**, a Rust toolchain, Cargo,
and Rust's four Android cross-compilation targets. The NDK version is pinned in
`app/build.gradle.kts` and CI. For local builds, install it with **Android Studio →
Settings → Languages & Frameworks → Android SDK → SDK Tools → Show Package Details →
NDK (Side by side)** (select **29.0.14206865**), or with:

```sh
sdkmanager --install "ndk;29.0.14206865"
```

Gradle reads the Android SDK directory through AGP, including the SDK from
`local.properties`, not only `ANDROID_HOME`. An externally installed NDK may
be selected with `ANDROID_NDK_HOME` or `ANDROID_NDK_ROOT` pointing to its root
(the directory containing `toolchains/llvm/prebuilt`). An incomplete/missing
NDK produces an actionable error rather than building an APK without the
native engine.

Install Rust with [rustup](https://rustup.rs/). The
`app/native-adblock/rust-toolchain.toml` file selects the **stable** toolchain
(with a minimal installation profile) and lists all four Android targets.
When Gradle invokes rustup-managed Cargo from the crate directory, rustup
automatically installs any missing toolchain components, including the Android
Rust standard libraries, on first use. This requires an internet connection.
It does **not** install the NDK, which is handled separately above.

If automatic installation is disabled (`RUSTUP_AUTO_INSTALL=0`), the Rust
installation is not managed by rustup, or a download fails, provision the
Android targets for the toolchain used by Cargo:

```sh
rustup target add aarch64-linux-android armv7-linux-androideabi i686-linux-android x86_64-linux-android
```

A manual Cargo invocation should be made from `app/native-adblock` so rustup
can discover its `rust-toolchain.toml` toolchain configuration. The file is a
Gradle task input: changing the toolchain declaration invalidates the native
library task's up-to-date state.

The Gradle `buildNativeAdblock` task runs Cargo with the NDK clang linker for
each target and includes the resulting `.so` files in the APK. On macOS,
`~/.cargo/bin/cargo` is used even when Android Studio is launched without a
shell `PATH`; `CARGO` or `CARGO_HOME` can override its location. Gradle's
configuration cache is supported: source changes cause a rebuild, while a
second unchanged build reuses the cached task graph. JVM/Robolectric tests
do not require native tools.

The native outputs are not Gradle **build-cache relocatable**, because external
Cargo dependencies and the Rust toolchain are not fully declared as Gradle
inputs. Standard up-to-date checks are still used; reinstalling or upgrading
Rust or NDK may require a native rebuild. The release GitHub Actions workflow
installs matching NDK and Rust Android targets.

The engine is compiled on a single background executor and then swapped into
use under a short native write lock. Request matching takes a native read lock
and does not parse rules, access preferences, or download data. A failed
native load **allows requests** and surfaces the error in the settings screen.

## Import safeguards

- Import a `.txt` list through Android's document picker or explicitly tap
  **Download** for one of three suggested lists: [EasyList](https://easylist.to/easylist/easylist.txt)
  (ads), [EasyPrivacy](https://easylist.to/easylist/easyprivacy.txt) (trackers), or
  [AdGuard Base](https://filters.adtidy.org/extension/ublock/filters/2.txt) (ads).
  AdGuard Base includes EasyList rules; avoid enabling both ads lists unnecessarily.
  The app bundles only shortcut URLs, never list content, and makes **no automatic
  network requests** for filters. Downloads are currently **one-time imports**, not
  periodically refreshed subscriptions.
- Suggested downloads require successful HTTPS responses (no redirect downgrade),
  are performed on a background dispatcher with timeouts, and use the **same
  private-file, UTF-8 and size validation** as local file imports. Duplicate
  downloads of the same suggestion are rejected until the imported list is removed.
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
allowlists, periodically refreshed subscriptions, and arbitrary list URLs
are intentionally out of scope.

## Verification

Robolectric tests exercise persistence, import limits, user-triggered suggested
list downloads, duplicate/rejection handling, enable/disable/reload, and resource
classification with a fake native matcher. Native Rust tests
cover blocking, exceptions, hosts lists, and malformed requests; Android
cross-compilation and real-device WebView tests are still required before
claiming production-level compatibility or latency.
