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

## Filter-list management

- In **Settings → Web Content → Content Blocking**, **Add list** starts with a **Type**
  dropdown: **File** (Android's document picker), **URL** (a custom HTTPS link and
  display name), or **Preset list** (a compact second dropdown of [EasyList](https://easylist.to/easylist/easylist.txt),
  [EasyPrivacy](https://easylist.to/easylist/easyprivacy.txt), and
  [HaGeZi Multi NORMAL](https://cdn.jsdelivr.net/gh/hagezi/dns-blocklists@latest/adblock/multi.txt)).
  The app bundles only shortcut URLs, never the filter content. All downloads
  require an explicit user action; no subscriptions or automatic refreshes run.
- Each row has a switch on the left and a three-dot actions menu on the right:
  **Edit**, **Update** (for URL/preset lists), and **Delete**. Deletion requires
  confirmation. The loading indicator has a reserved height so toggling does
  not shift the rows. **Update all** refetches every URL/preset list, including
  disabled ones, but not locally imported files. Individual failures are shown
  without stopping remaining downloads or overwriting their old rules; the
  blocking engine is reloaded once after the batch.
- **Edit** allows names to be changed for File/URL lists; URLs can also be
  changed. A changed URL is fetched and validated before replacing the stored
  rules, URL, and name together. Unchanged URLs are not refetched on rename.
  Local File lists can be edited inline when smaller than 128 KiB, or replaced
  from a new text file; larger lists must be replaced via the file picker.
  Presets are read-only in Edit, but their rules can still be updated normally.
- All list types offer a **Clone list** checkbox in Edit, **off by default**.
  Clones are created with new identities, copies of the cached rules, and
  **disabled** status so they cannot silently change active blocking. A preset
  clone is an editable URL list; File and URL clones retain their types.
  Changing the clone's URL or File rules leaves the original unchanged. Cloning
  does not require a network request unless the cloned URL is changed.
- Existing stored metadata without a type field is migrated at read time:
  local imports remain File, known preset URLs become Preset, and other
  downloaded links become URL. Existing IDs, enablement, and rule text remain
  intact. Previously imported AdGuard Base lists are retained as custom URLs.
- Remote downloads require successful HTTPS responses (without downgrade),
  are performed on an IO dispatcher with timeouts, and use the same private-file,
  UTF-8, and size validation as local file imports. Attempting to add the same
  download source twice is rejected, though explicit clones may share a URL.
- Each file must be UTF-8, nonempty, and no larger than 8 MiB. Storage is
  bounded to 16 lists / 32 MiB total; imports and replacements are staged as
  validated temporary files before changing the active rules. A failed
  download or file edit retains the previous list. A successful refresh or
  content change recompiles active rules; renaming alone does not.
- The original text is retained for rebuilding on startup. No compiled
  binary cache is persisted in this increment. Imported list files are
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
allowlists, and periodically refreshed subscriptions are intentionally out of scope.

## Verification

Robolectric tests exercise persistence, migration, import/refresh limits, file edits,
custom URL downloads and edits, preset immutability and clones, bulk refresh
with partial failure, duplicate/rejection handling, enable/disable/reload, and resource
classification with a fake native matcher. Native Rust tests
cover blocking, exceptions, hosts lists, and malformed requests; Android
cross-compilation and real-device WebView tests are still required before
claiming production-level compatibility or latency.
