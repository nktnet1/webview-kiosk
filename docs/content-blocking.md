# Content blocking

**Status:** Network filtering and basic CSS element hiding. No automatic downloads
or updates, bundled filter rules, or scriptlets. Manage lists in **Settings → Web Content →
Content Blocking**. Blocking is enabled when at least one user-imported or explicitly
downloaded list is enabled.

## Engine and build

The parser and indexed matcher are Kotlin/JVM code under `blocking/engine`.
`KotlinFilterEngine` publishes immutable compiled snapshots to request threads;
replacement is atomic and a failed compilation retains the last working snapshot.
The engine reuses the app's existing OkHttp URL/IDN parser and public-suffix data
for first/third-party classification, including multi-label and private suffixes.
Suffix data is initialized on the compilation worker when needed.

Builds use JDK 21, Gradle 9.8.0, and the Android SDK. The Python test-asset exporter
exports Gradle/Android dependencies, offers verified Gradle/JBR/SDK downloads, and
automatically splits archives above 180 MB.

Initial WebView creation waits for provider warmup and the first rule load without
blocking the UI or request threads. An initial load failure releases the startup
gate, permits browsing, and reports the error in settings. Reloading keeps old
rules active until the new snapshot is ready. Requests use hostname and mandatory
literal-token indexes; unconditional host rules have a separate compact fast path.
There is no full-list scan per request, except a bounded fallback for patterns
without a usable index.

Imported rule text is saved in private files. Startup and list changes read the
enabled files on the loader thread. A versioned binary cache holds the normalized,
deduplicated rules from the last successful compilation. A SHA-256 digest of the exact
enabled contents (including list boundaries), the cache format, and the app build
identify a reusable snapshot. Cache hits skip text parsing and reconstruct hostname/token
maps and CSS-selector indexes from the normalized records. Missing, incompatible, or
checksum-damaged caches fall back to compilation; failed cache writes do not disable
blocking. The disposable cache is limited to one file of at most 64 MiB in app cache storage
and is replaced atomically. Clearing app cache makes the next load compile again.
Changes to parsing or matching semantics must bump the cache format version.

The active snapshot stays in RAM
while blocking is active. Compilation prepares these data structures for matching;
it does not turn the rules into executable code or remove their memory cost.
During a reload, the old and new snapshots can briefly coexist. Temporary input
text and parsing objects become eligible for collection after loading finishes;
the old snapshot does so when readers release it. Disabling all lists clears the active
snapshot. The first launch after this change, and loads after rule content changes,
still perform compilation; subsequent launches avoid that parse. Index construction,
disk reads, and WebView provider startup still have a cost.

Startup and the list screen display the actual load phase: reading saved lists,
checking the cache, restoring compiled rules into memory, compiling rules,
building indexes, saving the cache, or disabling rules. Compilation and replacement
finish before the startup gate opens or success feedback appears.

## Filter-list management

- In **Settings → Web Content → Content Blocking**, **Add list** starts with a **Type**
  dropdown defaulting to **Preset**, with **Custom** (typed rules or a text file),
  **URL** (a custom HTTPS link and display name), or **Preset** (a compact second dropdown of [EasyList](https://easylist.to/easylist/easylist.txt),
  [EasyPrivacy](https://easylist.to/easylist/easyprivacy.txt), and
  [HaGeZi Multi NORMAL](https://cdn.jsdelivr.net/gh/hagezi/dns-blocklists@latest/adblock/multi.txt)).
  The selected preset's category and selectable source URL appear in a details card.
  Installed presets have red **Already added** text and are marked in the preset dropdown; a matching URL entry
  does not mark the preset as installed. The app bundles only shortcut URLs,
  never the filter content. All downloads
  require an explicit user action; no subscriptions or automatic refreshes run.
- Each row has a switch on the left and a three-dot actions menu on the right:
  **Edit**, **Refresh** (for URL/preset lists), **Info**, **Clone**, and **Delete**. Deletion requires
  confirmation. While work is in progress, an overlay covers the list with a centered
  spinner and italic gray phase text. The buttons, heading, and rows keep their positions;
  the normal 12 dp list spacing stays the same during work. The compact **Actions** menu beside **Add list** contains
  **Enable all**, **Disable all**, and **Refresh all**. **Refresh all** refetches every URL/preset list, including
  disabled ones, but not locally imported files. Individual failures are shown
  without stopping remaining downloads or overwriting their old rules; the
  blocking engine is reloaded once after the batch.
- A **Filter Lists** heading appears directly above the list, with total and enabled
  counts on the right. Counts include disabled clones and update after saved changes.
  Each row's type label has a matching icon: a checklist for Preset, a link for URL,
  and a document for Custom.
- **Enable all** and **Disable all** toggle every saved list, including clones,
  with one metadata save and one rule reload. Each menu item is disabled when all
  lists already have the requested state or there are no lists. No file contents
  or list identities change.
- Add, refresh, enable/disable, and other rule-changing operations keep their busy
  state until rule compilation or clearing finishes, with a minimum visible duration
  of one second to avoid flashing. Phase changes share that interval; closing an import/edit
  dialog starts a fresh minimum for the main indicator. Success messages follow both
  rule-load completion and the indicator closing. The startup indicator uses the same
  minimum when shown. The timer suspends UI presentation only; it does not block the
  main thread or delay engine work. A failed reload reports an error while keeping the previous working
  engine; saved list changes remain visible. Adding or editing closes its dialog
  after the file is saved, while the main loading indicator covers rule loading.
- **Info**, directly above **Clone**, shows the saved list's type, enabled status, last refresh time,
  file size, total parsed rules, network rules, cosmetic rules, and skipped rules;
  invalid rules are shown as well. Its title is the list name. Counts include disabled lists and
  describe entries before deduplication and cross-list precedence. The saved file
  is read on an IO worker when the dialog opens; inspection changes neither the
  stored list nor the active engine. Metadata and source appear immediately, file
  size appears after a quick file check, and each pending figure has its own loading
  indicator. Rule counts share one scan and become final together. Read failures
  mark pending values unavailable and show an error. The full list name is available in
  its selectable, scrollable title. Source URLs are selectable and ellipsized, and the
  dialog body scrolls. Skipped-rule totals are not shown
  on the main list screen.
- **Last refreshed** records successful source downloads, including the initial download,
  identical-content refreshes, and URL changes. It is saved with the list metadata and shown
  immediately in Info using the device's date/time format and timezone. Failed downloads
  or validation leave it unchanged; renaming and toggling do not alter it. Older lists without a recorded timestamp show **Unknown** until
  their next successful refresh. Custom lists show **Not applicable**.
- The empty screen uses a card containing only **No filter lists have been added**.
  The two compact controls share one row, with additional space
  before the list. A lazy vertical list handles many rows and short screens;
  row names stay on one line with ellipses; picker labels and replacement filenames
  also use ellipses. Info displays the full list name.
- **Edit** allows names to be changed for Custom/URL lists; URLs can also be
  changed. A changed URL is fetched and validated before replacing the stored
  rules, URL, and name together. Unchanged URLs are not refetched on rename.
  Custom lists can be edited inline when smaller than 128 KiB, or replaced
  from a new text file; larger lists must be replaced via the file picker.
  Presets are read-only in Edit, but their rules can still be updated normally.
- Only Custom lists offer **Clone** in the three-dot menu. URL and Preset lists cannot
  be cloned; storage enforces this before reading or validating another copy. Custom
  clones have a new identity and start disabled. Editing the copy leaves the original intact.
- The Add dialog starts with the list type. Custom and URL forms place **Name** first.
  Custom accepts typed/pasted rules or a file, and requires an explicit **Add** after file
  selection. Uploaded files stay as URIs until import; their contents never fill the text
  editor, regardless of size. File reads and validation run on IO. The text area rejects
  pastes above 128 KiB with an inline message and keeps the previous text; it never silently
  truncates rules. Choose a file for larger lists. Import errors stay inside Add and clear
  when the input changes or the dialog closes. The **Filter Lists** heading stays below
  the page's action buttons, including while the loading overlay is visible.
- Existing stored metadata without a type field is migrated at read time:
  local imports remain Custom (stored internally as `FILE` for compatibility), known preset URLs become Preset, and other
  downloaded links become URL. Existing IDs, enablement, and rule text remain
  intact. Previously imported AdGuard Base lists are retained as custom URLs.
- Remote downloads require successful HTTPS responses (without downgrade),
  are performed on an IO dispatcher with timeouts, and use the same private-file,
  UTF-8, and size validation as local file imports. URL entries must have unique addresses
  among URL entries, and each preset can be added only once, including disabled lists.
  A URL entry and a preset may share the same address in either order. Custom lists may
  be duplicated. URL entry/edit forms flag an existing URL address and disable submission;
  download/storage checks enforce the rule as well. URL edits may use a preset's address.
- Each file must be UTF-8, nonempty, and no larger than 8 MiB. Storage is
  bounded to 16 lists / 32 MiB total; imports and replacements are staged as
  validated temporary files before changing the active rules. A failed
  download or file edit retains the previous list. A successful refresh or
  content change recompiles active rules; renaming alone does not.
- The original text is retained for rebuilding whenever the cache cannot be reused. Imported list files are
  not yet included in the app's existing settings JSON/Base64 export.
- Removing or disabling the last list clears the compiled engine snapshot.

## File syntax validation and supported rules

Imports, edits, replacement files, URL updates, refreshes, and clones validate the
complete staged UTF-8 text before changing saved content or metadata. Rejections
leave the old list intact. Validation uses the same Kotlin parser as compilation,
without replacing the active snapshot. BOMs, CRLF, headers, comments, mixed hosts/
Adblock files, IPv4/IPv6 hosts records, aliases, and inline hosts comments work.
Existing hosts files beginning with an IP record retain hostname semantics for
subsequent bare domains. In ordinary Adblock files, bare text is a URL substring
pattern. Local machine aliases without a blocking-domain equivalent are skipped.

Control characters other than tab/CR/LF are rejected with their original line
numbers. A file needs at least one supported network or CSS-hiding rule; exceptions
and `$badfilter` directives count. Empty/comment-only files and unsupported-only
files have distinct errors. Known malformed syntax reports original 1-based line
numbers with at most five diagnostic details and a count of additional errors.
Arbitrary document-shaped text may still be a legal substring pattern; validation
cannot infer filter intent and does not blacklist JSON/XML by filename or shape.

| Syntax | Support |
| --- | --- |
| Hosts records; `||host^` domain rules | Yes, with domain-label boundaries |
| Literal URL substrings, `*`, `^`, start/end `|` | Yes; unbounded `||` anchors also preserve hostname/IP prefixes such as `||142.91.159.` |
| Options-only rules, such as `$image,domain=example.com` or `@@$generichide,domain=example.com` | Implicit `*` URL pattern; all declared restrictions still apply |
| `@@` exceptions; `$important` blocks | Yes; important blocks override ordinary exceptions |
| `$badfilter` | Disables the matching parsed rule across enabled lists, regardless of option order |
| `$domain=` / `$from=` | Positive/negated source-domain restrictions |
| `$third-party` / `$first-party`, `$3p` / `$1p`, negations | Public-suffix-based classification, within WebView's available context |
| Resource types and negations | `other`, `script`, `image`, `stylesheet`/`css`, `font`, `media`, `subdocument`/`frame`, `xmlhttprequest`/`xhr`, `object`, `ping`, `websocket`, `document` |
| `$method=` | Positive/negated method names joined by `|` |
| `$match-case` | Case-sensitive URL matching; default patterns are case-insensitive |
| `##` / `#@#` | Basic CSS hiding/exceptions, with positive/negated domain scopes |
| `@@...$generichide` / `$ghide`; `$elemhide` / `$ehide` | Disable generic/all cosmetic hiding for matching page URLs |
| Full regex rules, regex/entity domain scopes, wildcard hostname anchors | Skipped |
| Scriptlets, procedural selectors, custom styles/removal actions | Skipped |
| Popup-specific rules, redirects, response rewriting, CSP, parameter stripping, unknown modifiers | Skipped |

An unsupported option discards the **whole rule**, including exception rules; its
remaining URL pattern is never applied without its restrictions. Each list's
**Info** dialog shows the number of skipped unsupported/invalid rules in its saved file. Old stored
lists remain readable and useful supported rules still compile. This is a documented
subset of EasyList, AdGuard, and uBlock syntax. Document-type rules do not introduce site-wide
allowlisting, and parsing a resource type does not make WebView expose that type.

Lists retain the existing 8 MiB/file, 16-list, and 32 MiB total storage limits. A file
or combined enabled set is limited to 500,000 parsed rules. At most 2,048 unique
network patterns without a hostname, positive domain scope, or mandatory four-character
literal token can be active. Exceeding compilation limits reports an error and keeps
the previous engine. Matching has a shared work budget for candidate evaluation and
glob processing; pathological requests fail open if that budget is exhausted. Selector
and page-token caps remain in place. Unsupported CSS is also rejected per selector
by WebView before a fixed `display: none !important` rule is inserted.

## CSS element hiding

The engine supplies domain-specific hide selectors, generic selectors looked up
from observed class/ID tokens, cosmetic exceptions, and `$generichide` exceptions.
Selectors cross a data-only AndroidX WebKit message bridge, which checks the
actual frame origin against the requested URL. List text is never executed as
JavaScript or arbitrary CSS declarations.

On providers supporting `WEB_MESSAGE_LISTENER` and `DOCUMENT_START_SCRIPT`, a
small bootstrap is registered before navigation for HTTP(S) frames. It observes
new elements and class/ID changes, debounces work, scans at most 400 elements per
turn, and batches up to 256 class and ID tokens per native query. Only one query
is outstanding per frame; the app bounds concurrent replies per WebView. Token,
selector, and reply-size caps limit the work and memory on large pages.

Network-only and hosts-only lists install no scanner. Cosmetic filtering requires
JavaScript and the WebKit message-listener feature. Without document-start support,
the app injects a top-frame fallback after page load; providers without the bridge
retain network filtering. Local files and private app/PDF asset hosts are excluded.
Reload the page after enabling or changing cosmetic lists so old styles and frame
bindings are replaced. Enabling a first cosmetic list in an existing WebView gets
the post-load fallback on its next completed navigation. The bootstrap can run
before a DOM exists, but asynchronous matching means some elements may briefly
appear. Page CSP may prevent an injected style from taking effect.

Scriptlets, procedural operators, custom styles, and DOM-removal actions are not
implemented. This is basic CSS hiding, not uBlock Origin's complete cosmetic engine.

## Service workers and WebView limitations

A process-wide `ServiceWorkerClientCompat` is installed before WebView creation
when the provider supports interception. It uses a valid HTTP(S) `Referer` or
`Origin` header as the request's source context and applies the same matcher.
The callback provides no owning WebView or worker URL. Requests without usable
source context are allowed, rather than borrowing an unrelated tab's page URL.
This improves coverage, but does not cover every service-worker or cached response.

`WebViewClient.shouldInterceptRequest` does not expose reliable subresource
kinds or initiating frames. This integration passes `Sec-Fetch-Dest` or `Accept`
when present, otherwise `other`. It uses the last accepted top-level page URL
as a *best-effort* initiator, rather than inventing a nested frame's origin.
Top-level navigations are intentionally left alone. `javascript:`, `blob:`,
and subsequent redirect destinations cannot be intercepted using this API.
Scriptlets, `$redirect` resources, response-body modification, per-site
allowlists, and periodically refreshed subscriptions are intentionally out of scope.

Popup rules need a separate new-window filtering path, which this integration
does not implement. WebView can reject new windows through
[`WebChromeClient.onCreateWindow`](https://developer.android.com/reference/android/webkit/WebChromeClient#onCreateWindow(android.webkit.WebView,boolean,boolean,android.os.Message)).
That callback exposes a user-gesture flag, but no target URL or trustworthy
initiating-frame identity. A separate child-WebView navigation path could support
some URL-based popup filtering, with incomplete source context. With multiple-window
support disabled, new-window requests are treated as top-level navigations instead;
they cannot safely be classified as popups by the request matcher alone. See the
[Chromium new-window flow](https://chromium.googlesource.com/chromium/src/+/HEAD/android_webview/docs/how-does-on-create-window-work.md)
and [`WebSettings.setSupportMultipleWindows`](https://developer.android.com/reference/android/webkit/WebSettings#setSupportMultipleWindows(boolean)).
The kiosk leaves multiple-window support at its default `false` and does not
create child windows. A single-window kiosk does not need to implement window
creation unless its intended workflow requires separate windows. This default
does not enforce a blanket popup-navigation ban: a new-window link can replace
the current page and remains subject to the app's ordinary navigation policy.

## Verification

Run the Kotlin parser/matcher, storage, startup, interception, and bridge tests with:

```sh
sh scripts/ci/test-local.sh jvm
```

Run the asset-exporter, local-runner, and cosmetic JavaScript tests with:

```sh
sh scripts/ci/test-local.sh checks
```

`KotlinFilterEngineTest` covers URL/domain boundaries, pattern tokens, resource and
method restrictions, public/private suffixes, exceptions and priority, cross-list
`$badfilter`, mixed file formats, CSS scopes and exceptions, hiding gates, bounded
replies, malformed imports, failed compilation, pathological wildcard work, and
concurrent replacement. The network fixture corpus contains 384 request/rule cases
with expected blocking decisions. See `app/src/test/resources/filter-engine/README.md`
for the fixture format and maintenance guidance.

EasyList import regressions cover CSS selectors with colons and spaces (which
must not be mistaken for IPv6 hosts records), partial numeric hostname anchors,
and options-only request/hiding rules. Real malformed IPv4/IPv6 hosts records and
domain restrictions still fail validation; unsupported features are still skipped
without dropping their restrictions or rejecting an otherwise usable list.
For the uploaded EasyList snapshot `202610151103` (82,732 lines), validation has
zero syntax errors: 78,828 network/CSS rules are supported and 3,628 rules are
skipped, including 2,965 popup-specific rules. These are parser counts for this
snapshot, not a measurement of ad-blocking coverage.

Robolectric tests also cover persisted list management, bulk toggles, staged validation, partial
refresh failures, startup readiness, awaited rule-load completion, superseded loads,
cancelling a screen wait, retaining active rules on failed updates,
service-worker source headers, and message/frame-origin bounds. Dependency-free
Node tests execute the shipped cosmetic script with a DOM/bridge fixture to check
dynamic changes, batching, retries, CSS rejection, style restoration, and cleanup.
Cache tests replay the complete network fixture corpus through restored engines,
check CSS exceptions and cross-list badfilter behavior, and verify content invalidation,
corruption/version recovery, and cache-write failure without losing blocking.

Host tests are not a device compatibility or performance measurement. On Android,
check cold startup, first-/third-party requests, domain/generic hiding and exceptions
in frames, dynamic ads, service-worker fetches with/without source headers, JavaScript
disabled, CSP, list disable/re-enable plus reload, and large enabled lists. Compare
release APK size, list-compilation time, request-matching latency, and peak RAM on
representative devices. No ad-coverage percentage or Android latency is guaranteed.
