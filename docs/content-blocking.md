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
Do not bundle filter lists. The native library is lazy-loaded for syntax validation
or the first enabled list, and holds no compiled rules when all lists are disabled.

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
  **Edit**, **Update** (for URL/preset lists), **Clone**, and **Delete**. Deletion requires
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
- All list types offer **Clone** in the three-dot menu. Cloning immediately creates
  a copy with a new identity, the cached rules, and
  **disabled** status so they cannot silently change active blocking. A preset
  clone is an editable URL list; File and URL clones retain their types.
  Use **Edit** on the copy to change its name, URL, or File rules without changing
  the original. Cloning never makes a network request. Presets remain read-only.
- The Add dialog starts with the list type; field labels appear above the type,
  URL, name, file picker, and preset picker controls. The page has no additional
  "Filter lists" heading above the list controls.
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

## File syntax validation

Imports, inline file edits, replacement files, and clones all validate the staged
UTF-8 text **before** changing saved rules or metadata. Downloads, URL edits, and
refreshes use the same gate. Validation runs on the settings screen's IO dispatcher,
with one JNI call per file, without compiling or swapping the active engine. The
existing 8 MiB per-file and 32 MiB total limits still apply. Parser failures leave
the old name, ID, source URL, enablement, and rule bytes intact and remove the
temporary file. If the native validator cannot load, the operation fails with an
explanation; it never silently saves unvalidated content. Existing stored lists
remain readable, and name-only edits do not revalidate their existing rules.

Before native rule parsing, a streaming file-format check rejects complete JSON
objects and arrays, including pretty-printed documents and arrays of filter
strings. Checking the entire document avoids relying on filenames or incorrectly
rejecting `[Adblock Plus 2.0]` headers, bracket-shaped patterns, and JSON fragments
inside URLs/rules. UTF-8 BOMs and surrounding whitespace do not bypass this check.
The check uses Android's existing `JsonReader` without building a JSON tree and
runs on the same IO dispatcher. Imports, replacements, inline edits, URL updates,
and clones all use it; rejected documents never replace saved rules or metadata.
Every candidate also rejects control characters other than tab, CR, and LF,
with the original line number. This check scans all text, including comments and
leading/trailing characters, rather than checking only particular document types.
The native parser must accept at least one network rule before the file is saved.
Exception rules and literal URL substring rules count; a file containing only
unsupported rules gets a distinct error instead of being imported with no effect.

The validator calls [`adblock::lists::parse_filter` from the pinned 0.13.3 engine](https://docs.rs/adblock/0.13.3/adblock/lists/fn.parse_filter.html)
with the same network-only options used by compilation. It does not use a custom
Adblock grammar or a JVM regex approximation. Explicit structural/hostname errors,
invalid negations of supported options, and missing redirect values produce an
error with original **1-based line numbers**. At most five error details are shown,
followed by the number of additional errors, keeping diagnostics bounded for large
files. Whitespace-only and comment-only files get a separate "no rules" message;
empty lines, list headers, `!` comments, and hosts `#` comments within a useful list
are benign. UTF-8 BOMs and CRLF line endings are supported.

Validation and compilation share line routing. Adblock network/exception rules
may be mixed with IP-prefixed hosts records in either order. IPv4/IPv6 hosts
records support multiple aliases and trailing comments; the upstream hosts parser
validates each hostname. Local machine aliases such as `localhost` and
`ip6-loopback` are ignored. Existing hosts lists beginning with an IP record keep
their hostname semantics for subsequent bare domains, using the upstream hosts
parser to recognise them. Other bare patterns/domains keep their existing Adblock
interpretation. IP-looking prefixes followed by a hostname are checked as IP
addresses; standalone IP patterns retain their Adblock interpretation.

### Compatibility limits

Upstream bulk list loading silently discards errors; its per-rule API exposes
enough detail to reject known malformed network syntax. It also reports engine
limitations as errors. Unknown options, unsupported domain forms, full regex
filters (disabled in this build), unsupported modifier combinations, cosmetic
filters, and scriptlets are therefore **compatibility warnings, not malformed
rules**. They are skipped as before, and the dialogs explain that unsupported
filters are ignored when supported network rules remain. A file containing only
unsupported rules is rejected with a "no supported network rules" explanation,
without labelling those rules malformed. No cosmetic or scriptlet validation is claimed.

Some malformed-looking text is legal as an Adblock substring pattern, and the
upstream parser accepts some incomplete option values (for example `$domain`
without a value). Unknown options may also be misspellings; they cannot reliably
be distinguished from another engine's extensions. These cases are accepted rather
than inventing stricter syntax rules. The validator does not promise that every
accepted line takes effect, and it does not verify filter intent or resource
availability. Future unclassified upstream errors are treated conservatively as
unsupported syntax. No alternate parser or new dependency is added.

The JSON document check deliberately targets complete objects/arrays. Scalar
values and literal patterns such as `true` or `123`, malformed JSON-shaped literals, and
fragments embedded in actual rules can also be valid Adblock substring patterns,
so they remain the rule parser's responsibility. File validation cannot reliably
infer the author's intent from arbitrary text; it is not a guarantee that every
unrelated text document will be recognised or that every accepted pattern is useful.

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
classification with a fake native matcher. Storage validation tests inject a parser
result to verify staging, cleanup, error propagation, failed replacements/clone edits,
native-unavailable failures, and metadata preservation without Android JNI.
Native Rust tests use the real parser for valid/invalid/empty/comment-only files,
unsupported rules, BOM/CRLF text, IPv4/IPv6 hosts, mixed formats, line numbers,
bounded diagnostics, blocking, exceptions, and malformed requests. The Tests workflow
runs these host Rust tests separately from Robolectric, without an NDK; Android
cross-compilation and real-device WebView tests are still required before
claiming production-level compatibility or latency.
