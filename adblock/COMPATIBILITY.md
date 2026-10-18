# uAssets and subscription compatibility

## v1.0.29 assessment

This section supersedes the earlier assessments below. The four applicable roadmap
items are implemented; popup/popunder filtering remains conditional on a native
multiple-window lifecycle that this app does not provide.

### Scriptlet arguments and JSON DSLs

The JSONPath data interpreter adds array slices, `&&`/`||`, grouped conditions and
nested filters. These are explicit safe extensions beyond
[uBlock's documented JSONPath subset](https://github.com/gorhill/uBlock/wiki/uBO-flavored-JSONPath),
which still lists slices, logical operators and parentheses as unsupported.
They do not evaluate JavaScript expressions. Trusted constants, storage values and
replacement arguments gain the concrete options documented in
[SCRIPTLETS.md](SCRIPTLETS.md). Property traversal uses own data descriptors;
prototype names, accessor reads and arbitrary expression evaluation remain excluded.

### Procedural cosmetics

- `:xpath(...)` evaluates relative native XPath against an explicit CSS subject.
  Absolute document operands, `id()`, variables and JavaScript syntax are rejected.
  Compiled expressions are reused, with dependencies observed at the relevant
  subtree or shared root for expressions using wider axes.
- `:shadow(CSS)` selects within an open shadow root using captured native DOM APIs.
  A lightweight `attachShadow` hook handles late attachment to an already-matched
  host. It does not expose or retain closed roots.
- `:content(CSS)` matches within a template's content, including nested predicates
  and explicit removal actions. Reached content roots observe later mutations.
  Direct content hide/style rules are explicitly unsupported. Reversible effects
  apply only to connected results, keeping inert templates free of private style
  markers that their clones would inherit.

Observers attach only to roots reached by applicable procedures and release their
subjects, effects and styles when no longer needed. Suspension/resumption and
blocking shutdown clean up observers. No continuous polling or arbitrary DOM
property predicate is added.

### Frame and service-worker context

A present `Origin` header takes precedence over `Referer`; an opaque, null or
malformed origin stays unknown rather than borrowing the referrer's context.
Absent origin metadata can use a valid referrer or a `Sec-Fetch-Site: same-origin`
fact. The latter supplies only the target's canonical origin. Concrete local asset
origins keep their exemptions. Unknown initiators still use context-independent
rules, with the existing conservative treatment of contextual exceptions.

The top page's URL is no longer a fallback or exemption for unknown child requests.
The existing WebSocket/beacon hooks capture the calling global's actual origin,
including inherited `about:blank`/`srcdoc` origins. Opaque frames use unknown context.
No new bridge or script injection is added solely to reconstruct network metadata.
Native callback and worker metadata limitations remain; see the
[WebResourceRequest API](https://developer.android.com/reference/android/webkit/WebResourceRequest).

### Cosmetic and scriptlet index persistence

Schema 18 stores compact hostname/entity/class/ID buckets and scriptlet ancestor,
regex and exception buckets as primitive rule IDs. Restoration validates complete
membership and directly reuses the saved maps/arrays. It does not regroup those
indexes or duplicate selectors and arguments. Missing or invalid buckets rebuild
from normalized records and repair the aggregate snapshot; see [CACHE.md](CACHE.md).
Scriptlet executable source is still generated from validated records.

### Popup/popunder filtering

`$popup` and `$popunder` remain explicit unsupported modifiers. The app has no
`onCreateWindow()`/opener/close lifecycle or enabled multiple-window support.
Android treats new-window requests as ordinary main-frame navigation when multiple
windows are disabled; classifying those navigations as popups would require the
excluded heuristics. See
[WebSettings.setSupportMultipleWindows](https://developer.android.com/reference/android/webkit/WebSettings#setSupportMultipleWindows(boolean)).

### Verification

The patch passes `spotlessApply`, formatting checks and Android debug lint, plus
145 engine tests, 373 targeted Android/WebView tests and 219 Node/native Chromium
tests. Six optional engine corpus/benchmark tests and three optional upstream
differential tests were skipped. The incremental patch applies cleanly to the
supplied v1.0.28 baseline. Chromium and Robolectric do not establish compatibility
with every physical Android WebView provider; no device-level parity or performance
improvement is claimed.

## Historical v1.0.28 assessment

The following records the previous implementation and remaining work. The v1.0.29
assessment above supersedes its JSONPath, procedural and index-persistence gaps.

The JSON editor now covers every named JSON/JSONL request/response and object-editing
entry in the supplied catalogue, including their trusted counterparts. Trusted
assignments, merges, replacements and method-call directives use a validated data
AST. Method/property hooks follow late owners and replacements. Legacy trusted
inbound/outbound pruning, array/object entry pruning and native XML XPath are added.
These remain explicit argument subsets, not a claim of complete upstream parity;
see [SCRIPTLETS.md](SCRIPTLETS.md).

Procedural chains add nested text/has/not predicates, sibling/descendant selection,
attribute, computed-style, text-length and path predicates, ancestors and actions.
The observer processes changed subtrees and affected subjects in resumable batches.
It does not run document-wide selector queries after every mutation. Pure CSS/media
rules remain in CSSOM. Procedural hides track and restore the author's inline
display, including important declarations; conditional styles use private attributes. Computed-style
predicates read without this runner's effects, and stylesheet/path changes revisit
cached subjects. Disconnected subjects release ancestor effects. Native CSS relationships can still require revisiting
an affected subtree; this is distinct from scanning every rule over every document.

Frame/worker initiators come from actual Referer/Origin headers or a reliable
same-origin fetch-metadata fact. A top-page URL is no longer substituted for an
unknown child-frame initiator. Window WebSocket/beacon calls use the calling frame's
URL and the live native matcher through a boolean-only bridge. Allowed calls keep
native objects, arguments and receiver validation. Suppressed sockets throw
SecurityError before connecting; suppressed beacons return true. These are partial
page API hooks, not transport-level interception.

Nine additional redirect resources are bundled: AMP, Outbrain, HD main, BAB 4.2,
AdsByGoogle, IMA DAI, both no-eval resources and the half-second silent MP3. Existing
transparent-resource aliases are recognized. AdsByGoogle uses bounded subtree work
instead of its upstream repeated document query. DAI retains upstream request/reset
and event behavior, and requires approved source provenance both as a redirect and
as an inline resource. Redirect-only resources retain their eligibility restrictions.

### What remains unimplemented, and why

- **Complete upstream argument/DSL parity:** The new named editors are implemented as validated
  subsets. Arbitrary expressions, prototype traversal, accessor-defined JSON data, unsupported
  JSONPath forms and debug/logging configurations remain rejected; interpreting them
  approximately would change rule meaning or invoke site getters during data traversal.
  `evaldata-prune` remains skipped because another general eval wrapper changes direct-eval
  scope for calls unrelated to JSON pruning.

- **Repeated element-object editing:** Each matching element is edited once during its lookup
  window. Silent later JavaScript property assignments provide no DOM mutation signal;
  repeatedly detecting them would require polling. Native method/getter/setter editors cover
  known API/property paths.

- **Wider procedural operators:** XPath cosmetics, template/shadow traversal and arbitrary DOM-
  property predicates remain skipped. Their full forms need broader scans, access across
  separate DOM roots or side-effectful property reads. Native/targeted procedures are
  preferred. Closed shadow roots remain inaccessible.

- **Missing iframe/service-worker facts:** WebView callbacks do not expose a reliable
  frame/client ID or full initiator for every request. Missing metadata remains unknown;
  contextual exceptions are conservative. No unrelated tab or last-seen frame is borrowed.

- **Worker WebSocket/beacon interception:** Page-world JavaScript and the bridge do not run
  inside workers. Creating a replacement worker loader/network layer solely for these requests
  would be disproportionate. Providers without document-start support keep native filtering but
  skip page API/scriptlet hooks.

- **Streaming/non-string request bodies:** Request editing handles explicit string bodies in
  Fetch/Request init and XHR.send. Existing Request streams, blobs, forms and buffers pass
  through. Reading/rebuilding them would alter consumption, encoding, cancellation or streaming
  behavior.

- **Cosmetic/scriptlet index persistence:** Optional and left out. Normalized rules and
  JSON/procedural programs are already cached; their lookup maps and routing source rebuild off
  the UI thread. Another derived snapshot would duplicate data and require additional semantic
  validation without adding filtering coverage. Network-index persistence stays enabled.

- **Popup/popunder and close-window:** The app does not own a multiple-window/opener/close
  lifecycle. Ordinary navigations cannot be reliably reclassified as popup requests.

- **Full CSP modification and pre-parse HTML filtering (`##^`):** Native interception supplies
  request data and optional replacement responses, not the original response headers/body.
  Transparent support would require owning the response pipeline, with additional networking,
  cookie, cache and streaming behavior.

- **`click2load.html`:** Requires an unblock widget and a trusted frame reload policy, rather
  than merely returning an HTML placeholder. No such lifecycle is introduced.

Verification covers Kotlin/cache/provenance behavior, Android content-blocking and
WebView integration, Node API fixtures and native Chromium DOM/Fetch/XHR/media
lifecycles. Chromium and Robolectric do not establish parity with every physical
Android WebView provider. No subscription corpus coverage percentage is claimed.

The final checks passed formatting, Android lint, 135 engine tests (six optional
corpus/benchmark tests skipped), 368 targeted Android/WebView tests, 198 Node/native
Chromium tests, and 50 Python tests.


## Historical v1.0.26 roadmap assessment

Production persists all four native network bucket families, validates their
complete membership and rebuilds/repairs invalid ones. Activation does not depend
on a benchmark result. Plain-host sets, cosmetic indexes and scriptlet routing
still rebuild; see [CACHE.md](CACHE.md).

Document-start routing now pools identical instructions across domain rules. Each
matching instruction is decoded once, exception/deduplication checks use IDs, and
only runtime families selected for the current frame are initialized. Wildcard
origin registration remains necessary for arbitrary ports and descendant scopes;
it does not imply processing all rules in each frame.

Listener scriptlets add `runAt`, `elements` and `protect` arguments. Readiness uses
native events and element scopes use native `matches()`, without selector scans.
JSONPath editing adds bounded regex selectors, regex comparisons, current-node
filters and guarded query chains with cardinality quantifiers. Query execution
remains a data interpreter with shared traversal budgets; this is not complete
upstream DSL or trusted-assignment support.

`:matches-media()` on a native CSS subject, optionally followed by `:style()`, now
produces an `@media` stylesheet rule. Browser CSS handles resize/media changes;
no new observer, polling or DOM scan is added. IMA's fixed local media API shim
and the redirect-only Piano Analytics shim are bundled on demand. IMA callbacks
use captured animation frames when injected alongside scriptlets.

| Roadmap item                   | Result                                                                                                                      |
| ------------------------------ | --------------------------------------------------------------------------------------------------------------------------- |
| Document-start overhead        | Improved with shared instruction data and per-frame runtime selection. JavaScript source still has to be parsed by WebView. |
| Scriptlet arguments / DSLs     | Expanded tested subsets; request-body streams, trusted assignments/functions and unsupported options remain explicit skips. |
| Efficient procedural cosmetics | Native media queries added. Wider DOM procedures require incremental targeting before expansion.                            |
| Redirect/API shims             | IMA and Piano added. DAI remains unsupported pending trust and request-lifecycle integration.                               |
| Iframe and worker metadata     | Existing native Referer/Origin and ancestor-origin routing retained; unavailable initiators remain unknown.                 |
| Compiled index persistence     | Enabled in production for four network families, independently of benchmark results.                                        |
| Popup/popunder                 | Still conditional; this app does not own a multiple-window lifecycle.                                                       |
| WebSocket / beacon             | Optional partial wrappers remain future work, not a promise of complete interception.                                       |
| CSP / HTML response rewriting  | Remains unsupported; no replacement networking layer is introduced.                                                         |

The principle remains maximum correct compatibility without disproportionate
WebView workarounds. Capacity safeguards and malicious-input validation remain.

The empty list screen now offers the same Add Default action as the Actions menu.
Both open a confirmation listing only missing presets. Downloading starts after
confirmation, and the downloader is restricted to that named batch. Existing
source URLs are skipped, preserving custom names, ordering and enabled states.

## v1.0.25 changes and remaining work

The following section records the v1.0.25 state; the assessment above supersedes
its disabled-persistence decision and the corresponding completed items.

The capacity safeguards rise to 2,000,000 combined rule entries, 65,536 scriptlet
rules, 1024 instructions per frame, and 16 MiB routing data. These bound untrusted
inputs rather than limiting compatibility to a typical subscription size. Parsing,
regex, recursion, response-body, DOM-work, cache-size, and include-depth safeguards
remain. Native network indexes use primitive ID arrays to avoid retained boxed IDs.

`$all` now matches the intercepted network subset and complete root-document URLs.
It remains **partial**: there is no popup/popunder or inline script/font enforcement.
Inspection reports these rules separately in the list information dialog. Explicit
unsupported modifiers still reject the whole rule; `$all` is not used to silently
accept arbitrary popup, CSP, or inline modifiers.

Extended scriptlet routing now handles bounded hostname/URL regexes, ordinary/entity
path prefixes, exclusions, and actual descendant-frame origins. Entity scopes were
already implemented before this patch. Regex routes require iteration; ordinary
hostnames and entities use direct bucket lookups. Frames parse only relevant rule
records rather than an entire subscription's arguments.

JSON editing adds five documented scriptlets using the existing JSON/Fetch/XHR hooks,
including JSONL records and safe capture extraction. Its JSONPath subset and rejected
forms are specified in [SCRIPTLETS.md](SCRIPTLETS.md). It does not imply complete
upstream DSL or request-body editing support. Eight redirect resources are added:
three fixed API shims, VAST 2/3/4, VMAP 1, and a 3x2 PNG, loaded only when selected.
The three new API resources retain upstream's redirect-only eligibility.

Native classification recognizes worker script destinations, object/embed requests,
and explicit XMLHttpRequest headers. An explicit empty/unknown destination is not
reclassified from its Accept header. Empty Fetch destinations alone do not reliably
distinguish Fetch, XHR, and beacon requests. Frame Referer/Origin metadata is preferred
where supplied. Worker requests lacking initiators evaluate context-free rules;
potential contextual exceptions conservatively allow the request. No unrelated tab
URL is borrowed, and internal asset origins remain excluded.

| Remaining work                                        | Android WebView assessment                                                                                                                                                               |
| ----------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Production persisted indexes                          | Prototype available; disabled because the host warm-load comparison did not establish a benefit. Profile combined read/validation/build time and allocations on Android before enabling. |
| Remaining scriptlet arguments and JSONPath operations | Recommended where a bounded parser and correct API hook suffice. Request-body streams and additional trusted mutations need separate lifecycle and provenance tests.                     |
| Wider procedural cosmetics                            | Selective: prefer native CSS and targeted, incremental observers. Do not expand repeated document-wide scans merely to accept more syntax.                                               |
| Larger redirect shims, including IMA/DAI              | Feasible, with upstream API/callback and media tests before accepting the resource.                                                                                                      |
| Popup/popunder and close-window                       | Conditional on native multi-window ownership; this app has no such lifecycle.                                                                                                            |
| WebSocket/beacon wrappers                             | Optional partial coverage. They cannot replace network-level interception or cover workers completely.                                                                                   |
| Subresource removeparam                               | Avoid a replacement network stack solely for URL rewriting. Navigation removal remains supported.                                                                                        |
| CSP and HTML response rewriting                       | Unsupported: ordinary interception does not expose original response headers/bodies for transparent editing.                                                                             |

Compatibility work is recommended when WebView can implement the rule correctly
without disproportionate platform-specific workarounds. More enabled rules alone
are not a reason to reject a feature. This patch adds no polling, DOM observer,
response proxy, or JavaScript resource-type instrumentation.

## Historical v1.0.17 subscription audit

This audit compares v1.0.16 with this incremental update. A skipped rule is a
parser/contract rejection, not a measured missed advert. Accepted rules still
require a matching document/request and the documented WebView capabilities.
Unsupported options discard the complete rule; they are never silently removed.

## Inputs and reproducibility

The supplied uBlock archive is pinned to
`dfca51bea2b8605353547f605e9cffbb6dfa0789`; its SHA-256 is
`02eb8dbacc8ff09fd51524acd718998865c69a06041d3619def9d7204856311a`.
The attached uAssets archive SHA-256 is
`815e62de47dd15ed745a87035150e5b72f5b99ef1028564999ca633ad9e191ba`.
No subscription text is added to the application or repository.

The publisher downloads taken on 2026-10-17 reproduce both reported baseline
counts. EasyPrivacy identifies version `202610170019` in its header.

| Publisher input | SHA-256                                                            | v1.0.16 skips | v1.0.17 skips |
| --------------- | ------------------------------------------------------------------ | ------------: | ------------: |
| EasyList        | `8c3ae2e3528ce9b66f217e3ccf6aa4c4c0bbaae3d299fd920e7a0617d03834f6` |          3177 |          3000 |
| EasyPrivacy     | `1a8fc2e0f7706696a7f659203ad31ceb15f26c82c3dafd8a7dd6a61cd7ac3250` |           103 |             9 |

The attached uAssets third-party copies are different inputs. Their EasyList
SHA-256 is `f5396aef1f299e1bd28c2db7750ea9bc4682d8f576292482a9c983f14a9d358f`;
the EasyPrivacy SHA-256 is
`92f0fe46fd56a7b5d2e7e27776636cc990d3712d30aaf39d70eb02dc2ac6a5d8`.
Their baseline skips are 3177 and 87 respectively. In particular, the attached
EasyPrivacy baseline cannot substantiate a claim about the reported 103 skips.

`SubscriptionCompatibilityTest` audits exact local bytes when `EASYLIST_DIR`
contains `easylist.txt` and `easyprivacy.txt`. `FILTER_AUDIT_OUTPUT_DIR` optionally
records every skipped physical line. No network access occurs during the test.

## Fixes identified by the list audit

- Linear-time RE2/J network regex matching, with bounded compilation/program size,
  counted repeats, aggregate matching work and cache revalidation. Lookaround,
  backreferences and unsupported escapes remain skipped.
- Wildcard hostname anchors, literal internal pipes, and multi-label entity scopes
  such as `www.google.*` and `read.amazon.*`, using OkHttp's public suffix data.
- Redirect exceptions which cancel a surrogate without allowing an independently
  blocked request, and the known local `abp-resource:blank-*` rewrite aliases.
- Additional fixed JSON, PNG, silent MP3/MP4 and API shim redirects. Redirect names
  select bundled enum resources, never arbitrary files or remote URLs.
- One bounded literal/regex text predicate, optionally inside one native `:has()`;
  direct-child matching, quoted attributes and the allowed `transform` style.
- Slash-containing literal request paths are distinguished from regex/flag forms.

Schema 9 stores the added normalized fields. Existing schemas recompile once;
app release numbers remain absent from cache identity. See [CACHE.md](CACHE.md).

## What the EasyList skips mean

The remaining 3000 publisher EasyList rules are:

| Count | Family                                         | Assessment                                                                                                                                                                  |
| ----: | ---------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
|  2969 | `$popup` rules                                 | Feasible with native popup ownership and opener/target context. The current blocker lacks that lifecycle; applying these to ordinary navigation would change their meaning. |
|    28 | Procedural cosmetics and CSS-property matching | Implementation/contract gaps, including sibling/nested chains and patterns beyond the text limits. WebView does not inherently forbid these.                                |
|     3 | CSP rules/exceptions                           | The current integration cannot edit original response headers. A response-owning network integration would be needed.                                                       |

The popup count includes rules combining popup and other type modifiers. The
whole rule stays skipped until every modifier has its intended semantics.

## What the EasyPrivacy skips mean

The remaining nine publisher rules are two CSP rules, two scriptlet rules whose
mixed hostname scopes include `ticketmaster.*`, two stack regex forms beyond the
current contract, one cookie reload option form, one `json-edit` DSL instruction
and one response-time HTML filter. The entity-scoped rules are skipped as complete
rules rather than injected on a broader set of sites. All except the response/CSP
integration gaps are feasible parser/dispatcher work in Android WebView.

## Remaining worthwhile work

Most remaining scriptlet gaps are feasible in a sufficiently capable WebView.
The canonical catalogue and precise accepted argument forms are in
[SCRIPTLET-CATALOG.md](SCRIPTLET-CATALOG.md) and [SCRIPTLETS.md](SCRIPTLETS.md).

| Gap                                                             | Feasibility and current decision                                                                                                                                                         |
| --------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| JSON/object/JSONL edit DSLs and additional trusted pruning      | Feasible implementation work. They need a complete bounded parser, mutation semantics and response/request tests; approximating the DSL is unsafe.                                       |
| Broader existing scriptlet arguments                            | Feasible work, including cookie reload extras, XPath, advanced media/text patterns and late custom method chains. Current subsets reject these.                                          |
| Scriptlet entity/regex/path scopes and descendant-frame routing | Feasible but not supplied by the current JavaScript dispatcher. Native network/cosmetic entity matching does not imply scriptlet entity support.                                         |
| Google IMA/DAI and other unbundled API/resource shims           | Feasible compatibility work. The two new Amazon apstag/Chartbeat redirects do not make them valid `+js` entry points.                                                                    |
| Popup filtering and `close-window`                              | Require native window ownership, opener context and a defined kiosk close policy. No page-callable app-exit bridge is introduced.                                                        |
| CSP and response-time HTML filtering (`##^`)                    | No transparent original-response editing path in the current WebView integration. Page DOM mutation is not equivalent to filtering before parsing.                                       |
| Document-start hooks                                            | Require `WebViewFeature.DOCUMENT_START_SCRIPT`; registration is feature-guarded. Unsupported providers skip scriptlets, since late injection cannot recover earlier author-script calls. |
| Navigation cancellation                                         | Requires a native Navigation API and cancelable events. Ordinary noncancelable events cannot provide equivalent behavior.                                                                |
| Debug/configuration helpers                                     | Deliberately excluded from production injection.                                                                                                                                         |

New trusted node-text, click, argument/outbound replacement, method suppression,
DOM bypass/override and HTML helpers have explicit bounded contracts. Clipboard
protection also requires trusted provenance. Only approved successful preset
refreshes/downloads carry trust; matching a preset URL manually does not confer it.
The two annoyance presets are opt-in and do not change the nine default lists.

The supplied uAssets active branches contain 14608 physical scriptlet rule lines
across all top-level filter files, including optional and specialized lists.
Argument parsing/scoped rule acceptance improves from 12072/10369 to
13320/11473. This is an audit of those input lines, not default
subscription coverage, unique operations, runtime execution or blocking efficacy.
`UAssetsScriptletTest` reproduces it with `UASSETS_FILTERS_DIR`; inactive conditional
branches are excluded by the normal parser.

## Verification boundary

Regression coverage includes the Kotlin parser, cache restore, trusted-source
policy, app response MIME/binary handling, bounded JavaScript behavior and native
Chromium response/DOM behavior. Native Chromium verifies inline-script timing,
Trusted Types enforcement, shadow-root click steps and MPD XML timing. It does not
prove identical behavior on every Android WebView provider. Robolectric exercises
Android integration with shadows; no physical Android device or emulator was
available for this update.

The final run passed 86 engine tests (two optional corpus/benchmark tests were
skipped), 113 Node tests, 50 Python tests and 22 native Chromium tests. Formatting
and Android lint passed. The app suite passed 712 of 713 tests: one MQTT lifecycle
test timed out. Rerunning its 27-test class passed that test but failed a different
MQTT loopback case. The full app suite is therefore not green; all content-blocking
tests passed. This patch changes no MQTT source or tests.

Relevant native API contracts:
[WebViewCompat](https://developer.android.com/reference/androidx/webkit/WebViewCompat),
[WebViewFeature](https://developer.android.com/reference/androidx/webkit/WebViewFeature),
[WebChromeClient](https://developer.android.com/reference/android/webkit/WebChromeClient),
[WebViewClient](https://developer.android.com/reference/android/webkit/WebViewClient).
