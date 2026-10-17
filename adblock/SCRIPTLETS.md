# Scriptlet compatibility - v1.0.28

## v1.0.28 editing and request compatibility

All names in the supplied JSON/JSONL editing suite now have typed entry points.
`trusted-` variants require approved list provenance, including on cache restore.
The ordinary versions can only delete data. Trusted queries additionally accept
`=JSON`, `+=JSON-object`, `=repl(JSON-object)` and
`=call([receiver, method, ...arguments])`. Replacements supply `regex` or `pattern`,
optional `flags`, and `replacement`. Call directives accept data arguments and
`${obj}`, `${key}` and `${val}` placeholders; a null receiver selects the page global.
They do not compile JavaScript expressions. Prototype-related keys are rejected.
JSON values are validated strictly, and traversal completes before mutations begin.
Root deletion/replacement is supported. Assignments target resolved existing
properties; object merges can add members. Existing per-operation safeguards remain.
Root-based filter queries are added. Invalid bare bracket names/regexes stay rejected.

`json-edit-fetch-request` and `json-edit-xhr-request`, and their trusted counterparts,
edit explicit string request bodies. Fetch also hooks Request construction with an
explicit data-property init body. Body/method accessors pass through to preserve
native coercion; other inherited getters retain their original receiver.
Request options, inherited init fields, headers, signals and
native errors are retained; a derived init shadows only the edited body. Existing
Request streams and other body types pass through without consuming them.
Response/JSONL editors retain their existing propsToMatch/jsonExtract contracts.

The object editors accept the upstream method/property chain and query:

- `edit-outbound-object`: edit a synchronously returned object.
- `edit-inbound-object`: chain, zero-based argument position, query; negative positions
  count from the end. The selected argument is JSON-cloned before editing.
- `edit-this-object`: chain, query, optional `after`; otherwise edit before the call.
- `edit-object-on-getter` / `edit-object-on-setter`: chain and query. Native accessors
  and object identity are preserved; late owners and assignments are observed.
- `edit-element-object`: CSS selector, query, optional lookup window in milliseconds.
  Existing/added matching elements are edited once each. Zero/default covers initial
  parsing; a positive window observes late matches, with no polling. The shared data
  interpreter traverses own data properties, not inherited DOM getters.
- `trusted-edit-inbound-element`: method chain, zero-based position (or `this`), CSS
  selector, query. Edit a matching native Element before forwarding the native call.

Every object editor above has a trusted counterpart; the inbound-element name is
trusted-only. Native method argument/outbound replacement hooks also follow late
owners and function replacements. Their other argument contracts remain unchanged.

`trusted-prune-inbound-object` uses a **one-based** positive argument position, prune
paths, optional needle paths and optional `dontOverwrite, 1` / `stackToMatch, pattern`.
It requires all prune/needle paths before acting. `dontOverwrite` clones the matching
argument. `trusted-prune-outbound-object` accepts chain, prune paths and optional
needle paths. Legacy pruning adds `{}` traversal, `[-]` array-entry removal and `{-}`
object-entry removal. Array removals preserve retained order even across prune paths.

`xml-prune` additionally accepts `xpath(expression)` for its first selector. Native
XPath operates on the already bounded parsed XML document, including attribute-node
removal. Root-document removal, malformed XML, external entities and oversized
responses remain excluded.

The new IMA DAI resource requires trust for both +js and network redirects. AdsByGoogle
is available inline and by redirect; its observer visits changed subtrees in bounded
batches. AMP, Outbrain, HD main and BAB 4.2 are redirect resources. BAB 4.2 needs the
original requested script URL/currentScript context and is not an inline entry point.

The contracts below retain their existing limits except where this section expands
the editing, trusted-mutation, legacy-entry-pruning and XML-XPath subsets.
Remaining WebView/DSL boundaries and their reasons are in [COMPATIBILITY.md](COMPATIBILITY.md).

The engine compiles validated `##+js(...)` data into fixed local JavaScript.
Ordinary imports cannot enable trusted operations. No downloaded list supplies
replacement functions or remote implementation modules. Trusted node-text/HTML rules
can alter executable page content; only approved preset downloads can enable them.
Other trusted replacements are strings or bounded JSON.

The pinned upstream catalogue has 129 canonical names. This version implements
114 names: 113 tested subsets and one conditional Navigation API operation. See the
[complete catalogue](SCRIPTLET-CATALOG.md) for aliases and remaining exclusions.
Support for a name does not imply support for all its upstream argument forms.

## Executable argument contracts

✅ means executable and behavior-tested within the listed subset, not support for
all upstream options. ⚠️ means a provider-dependent feature. ❌ means skipped.
Unknown arguments discard the whole instruction. There is no approximate fallback.

| Status | Names / families | Implemented subset |
| --- | --- | --- |
| ✅ | `set`, `set-constant` | Dotted property, predefined value or integer ±32767 |
| ✅ | `aopr`, `aopw` | Nested/missing/replaced owners; shared property traps |
| ✅ | `aost` | Property, bounded normalized stack pattern, optional negation |
| ✅ | `acs`, `acis` | Property, current-script text, optional script source pattern |
| ✅ | `nowoif` | All calls or pattern/negation; no delayed decoys |
| ✅ | `aeld` | Exact event name or regex, callback/handleEvent source, readiness, element scopes and hook protection |
| ✅ | `nostif`, `nosiif` | Callback pattern, exact/range delay, negation |
| ✅ | `norafif` | Callback pattern or negation; logging-only invocation skipped |
| ✅ | `nano-stb`, `nano-sib` | Pattern, exact delay or `*`, finite multiplier |
| ✅ | `noeval-if` | One nonempty pattern; native page eval pass-through otherwise |
| ✅ | `noeval`, `noeval-silent` | Legacy no-argument prevention of all eval calls |
| ✅ | `json-prune` | Own paths, guards, `[]`/`*`; JSON.parse and Response.json |
| ✅ | `no-fetch-if` | URL/method predicates and safe empty canned responses |
| ✅ | Local/session storage setters | Key plus safe value or exact/regex `$remove$` |
| ✅ | Cookie setters/reload/removal | Safe values, validated path/reload flags, and trusted variants documented below |
| ✅ | `set-attr` | Safe attribute, empty/boolean/integer/copy value; after load |
| ✅ | `ra`, `rc` | Attribute/class tokens, optional selector, asap/stay/complete |
| ✅ | `rmnt` | Node name and text pattern; parsing phase only, no replace options |
| ✅ | `prevent-dialog` | Optional selector suffix; closes matching open dialogs |
| ✅ | `href-sanitizer` | Selector, text/attribute/single query parameter source |
| ✅ | `prevent-innerHTML`, freeze property | Selected DOM properties, selector, value pattern |
| ✅ | `spoof-css` | Selector and up to three allowed style/rectangle pairs |
| ✅ | `prevent-canvas` | Optional context-type pattern/negation |
| ✅ | `call-nothrow` | Existing dotted function; preserves receiver, arguments, result |
| ✅ | `window.name-defuser` | Clears top-frame name only |
| ✅ | `disable-newtab-links` | Removes target and cancels matching anchor clicks |
| ✅ | `prevent-refresh` | Meta refresh stop after load; optional immediate behavior |
| ✅ | `nowebrtc`, `webrtc-if` | Native API prevention or bounded ICE allow-pattern |
| ✅ | `nobab` | Upstream BAB eval signatures and timeout signature |
| ✅ | `alert-buster` | Defuses alert; confirm and prompt retain native behavior |
| ✅ | `multiup` | Direct form button link navigation, HTTP(S) destinations only |
| ✅ | Seventeen fixed API shims | No filter arguments; includes IMA lifecycle callbacks; see catalogue for exact names |
| ⚠️ | `prevent-navigation` | Requires native Navigation API and cancelable automatic events |
| ✅ | `no-xhr-if` / `prevent-xhr` | URL/method predicates, canned responses and native instances |
| ✅ | JSON Fetch/XHR pruning | Own paths, guards, URL/method and optional stack predicates |
| ✅ | Trusted operations and clipboard protection | Approved download provenance and contracts below |
| ✅ | `xml-prune` | CSS or native XPath selection, optional CSS guard and URL pattern |
| ✅ | `m3u-prune` | HLS EXTINF/URI segment pairs and following discontinuity |
| ✅ | `overlay-buster` | Shared observer, bounded sweep and 30-second deadline |
| ✅ | `mpegdash-prune` | XHR MPD Period durations/starts; fully timed manifests only |
| ✅ | JSON/JSONL response editors | Bounded JSONPath selectors, indexes, unions, filters, regexes, guarded query chains, Fetch/XHR predicates and JSONL capture extraction |
| ✅ | Request-body and object editors | String request bodies and method/property/element hooks; trusted data directives above |
| ❌ | Remaining editing forms | Arbitrary expressions, slices and accessor-defined JSON data |
| ❌ | Debug/close-window | Deliberate debugger exclusion and no native window lifecycle |

The safe constant set is empty string, undefined/null, booleans, empty arrays or
objects, noop/true/false/throw functions, and bounded integers. Functions are locally
predefined. Storage/cookie values use upstream's safe-word whitelist and integers.
Attribute setters reject executable attributes and navigation/HTML/CSS destinations.
Href sanitation accepts only HTTP(S) destinations; transformation pipelines are skipped.
CSS spoofing permits display, visibility, opacity, position, dimensions, offsets and
four private rectangle fields. No arbitrary CSS declarations are installed.

`aeld` accepts unique `runAt`, `elements` and `protect` option pairs after its two
positional arguments. `runAt` is `loading` (default), `interactive` or `complete`;
`elements` is `window`, `document` or a bounded native CSS selector without `:scope`;
`protect` is `1` (or empty/`0` for default). Unknown options discard the instruction.
Readiness listeners install the shared wrapper when needed. Native `matches()`
handles element tests without document-wide queries. Event-name literals match
exactly, as upstream specifies. Empty event/callback logging-only calls stay skipped.

Patterns use the existing bounded literal/regex subset. Lookarounds, backreferences,
repeated groups, unsafe repeats and sticky flags are skipped. Global flags are
accepted only by the new trusted text replacement helpers. Matchers compile
once per selected rule. Function-source matching caches at most 512 entries/256 KiB;
individual strings are limited to 64 KiB. Oversized or unusual native arguments pass
through. Timer multiplier defaults to 0.05 and is clamped to 0.001–50, as upstream.

## JSON editing subset

`json-edit` hooks `JSON.parse`; `json-edit-fetch-response` and
`json-edit-xhr-response` use the shared response adapters. JSONL variants edit each
JSON record independently and retain malformed lines, separators, and surrounding
text. Optional `propsToMatch` predicates support URL/method matching. JSONL
`jsonExtract` requires a safe anchored regex with one capture group and native
regex capture indices. `json-edit` also accepts its `matches` text predicate.

Accepted JSONPath forms include `.name`, `..name`, `[*]`, quoted property names,
positive/negative array indexes, unions, existence/primitive-comparison filters,
current-node comparisons (`v2:$.values[?@>=2]`), regex property selectors
(`v2:$../^ad/`) and regex comparisons (`v2:$.items[?@.kind=/^ad$/i]`).
Examples: `..ads`, `$.items[*].advert`, `$.items[?@.kind=="ad"]`, and
`v2:$.items[?!@.content]`. Legacy and `v2:` object-filter semantics differ upstream;
the interpreter preserves that distinction. Up to eight guard queries may precede
the editing query, for example `v2:$.required;$..ads` or
`v2:$.missing{0};$.items[*]{2,4};$..ads`. Guards never mutate their selections.
Supported cardinalities are `{n}`, `{n,}`, `{,n}`, `{n,m}`, with a default of one
or more and bounds up to 1,000,000. All guards share the editing traversal budget.
Root-based filter queries are supported. Parenthesized/logical filter expressions
and slices remain skipped. Trusted assignments and data directives follow the
contracts above. Regexes retain the bounded safe grammar;
selectors have no flags and regex comparisons permit only the optional `i` flag.
Use dot notation for regex selectors; bracket regexes are skipped to agree with
the pinned interpreter. Negated regex comparisons and
zero-ended range quantifiers are skipped because upstream interpretation is
inconsistent; use the exact `{0}` guard to test absence. Bracket property
names must be quoted.
Unsupported queries reject the whole instruction, rather than dropping constraints.

The compiler stores a data AST; cached ASTs must match recompilation of their query.
No query is executed as JavaScript. Traversal uses own data descriptors, a depth of
64, at most 4096 selected edits, and a shared work budget. Each query finishes
traversing before mutating. Numeric array-index deletion splices entries;
regex-selected string keys preserve upstream sparse-array behavior. Oversized or
exhausted work retains that query's input.

## Domains, ordering and security

Ordinary hostname/subdomain and public-suffix entity scopes (`site.*`) are supported,
including mixtures, exclusions, exact instruction exceptions, and blank `#@#+js()`
exceptions. Bounded hostname regexes and hostname/entity path prefixes are also
supported. Regex URL scopes follow upstream's split at the first escaped slash:
match the hostname suffix and path prefix separately, without schemes or ports.
Path matching includes the URL's query and fragment and remains case-sensitive.

`host>>` and `entity.*>>` target descendant frames using the browser's actual
`location.ancestorOrigins`, capped at 64 entries. They do not target the named
parent itself. Missing/opaque ancestor metadata causes constrained rules to pass
through. There is no parent-document probing, bridge, or page instrumentation.
The engine's optional ancestor-URL argument provides the equivalent native context.
Global positive injection without a hostname/entity/validated regex remains skipped.

Routing stores sorted newline-delimited hostname/entity buckets and independent
JSON rule records. A frame binary-searches applicable buckets and parses only their
records. Entity suffix data is decoded only for matching entity labels. Regex scope
matching still scans the bounded regex routing table; it cannot always use a direct
hostname lookup. Exceptions are resolved before deduplication and the page safeguard.
Normal CSS exceptions do not cancel scriptlet rules. First-rule ordering is preserved.

Conflicting terminal property hooks use the first instruction; compatible checks
share accessors. Non-configurable/read-only properties pass through. Missing owners
are hooked without manufacturing objects, within a 4096-slot safeguard.

Compiled snapshots use schema 17. Cached instructions, JSONPath programs, and extended
scope patterns are validated again. Resource keys select bundled implementations.
Only needed adapters and eligible shims are bundled. The separate request-policy
bridge returns only a filtering decision and does not expose scriptlet execution. Redirect-only resources do not become `+js` entry points.

## Response and trusted-operation contracts

Response operations accept the existing bounded pattern grammar. All selected
response edits and XHR preventers participate up to the shared 1024-instruction
frame safeguard; the old separate 32-operation truncations are removed. Bodies
remain limited to 1 MiB, with 4 MiB of aggregate text work per body. Fetch inspection also has a two-second deadline. Exceeding a limit
retains the original response and untouched body. UTF-8 text is supported; other
declared character sets, opaque responses, bodyless statuses, invalid data and
missing APIs fail open. No interception happens for nonmatching calls.

Fetch uses native Response readers, streams and body-used errors. Status, URL,
redirected/type metadata and cloning are retained. Changed bodies discard stale
content-length, content-encoding, ETag and Content-MD5 headers. Unchanged bodies
return the original Response. Native rejection reasons are preserved. Matching
fetch promises can be delayed up to two seconds during body inspection; this is
not transparent stream editing.

XHR retains the native constructor/prototype and validates `open` natively.
Prevention handles reuse, cancellation, reentrant handlers, duplicate sends,
headers, async progress/load ordering and native synchronous event sequencing.
Text, JSON, empty arraybuffer/blob/document responses are supported. Read-only or
frozen instances fail open. Pruning starts only at DONE; partial responses remain
native. Prevention uses the requested literal body for text responses; typed
responses use `{}` or empty binary/document values. This is a documented subset,
not every XHR IDL edge case.

JSON response syntax is `paths [, guards [, propsToMatch, predicate
[, stackToMatch, pattern]]]`. Options can appear in either order and normalize.
Own-property handling, guards and work budgets match `json-prune`. Unknown
options discard the whole instruction. XML supports CSS element selectors or
native XPath, including attribute nodes, and optional guard/URL matching. Malformed
XML and DOCTYPE/entity inputs pass through. HLS supports EXTINF followed immediately by
a matching segment URI, optionally followed by a discontinuity tag. Multiline
regex and wildcard-string forms are rejected; other playlist tags are retained.

Only successful downloads of the five core uBlock presets and the two optional
uBlock annoyance presets carry trusted provenance. Optional lists are excluded
from the nine-list default set. Includes and redirects retain the existing same-origin restrictions.
A matching URL, list name, old preset record, directive or custom import cannot
grant trust. Existing presets gain trust only after a successful refresh.
EasyList and other presets remain untrusted. Trust is persisted with saved bytes
and included in aggregate/per-source cache identity. Failed refreshes retain the
prior bytes and trust. This is provenance policy, not signature verification.

| Trusted operation | Supported subset |
| --- | --- |
| `trusted-prevent-fetch`, `trusted-prevent-xhr` | URL/method predicate and literal/empty body; WAR, generated-length and random-body directives skipped |
| `trusted-replace-fetch-response`, `trusted-replace-xhr-response` | Bounded literal/regex pattern, replacement string and optional URL/method predicate; no extras |
| `trusted-set-local-storage-item`, `trusted-set-session-storage-item` | Key and literal string; dynamic `$...$` tokens skipped |
| `trusted-set-cookie`, `trusted-set-cookie-reload` | Name, literal string and root/none path; dynamic, expiry and domain extras skipped |
| `trusted-set-constant` / `trusted-set` | Safe constant, `json:` JSON or object with `value`, depth at most 16; no extras |
| `trusted-set-attr` | Safe selector, explicitly allowed attribute and literal value; all other attributes skipped |

Trusted constants remain locked across type changes. JSON parsing never evaluates
source. Trusted attributes use literal values, not attribute-copy shorthand.
Allowed attribute names are `data-*`, `aria-*`, `class`, `id`, `role`, `title`,
`alt`, `hidden`, `disabled`, `checked`, `selected`, `readonly`, `tabindex`, `dir`,
`lang`, `open`, `value` and `name`; matching is case-insensitive.
Other trusted names remain unimplemented despite the available provenance model.

Overlay removal shares the DOM observer/frame batch, checks at most 64 centre-point
ancestors and removes at most eight viewport-covering fixed/high-z-index elements
per sweep. It restores scrolling and stops after 30 seconds using one protected
deadline timer, without recurring polling. It remains an explicit heuristic:
matching intentional full-screen UI can be removed, as with upstream.

## Additional uAssets contracts in v1.0.17

[COMPATIBILITY.md](COMPATIBILITY.md) records the supplied-list audit and remaining gaps.

| Operation | Accepted subset |
| --- | --- |
| `trusted-rpnt` and aliases | Node name/pattern, replacement pattern/string, literal replacement; at most two option pairs from `condition`/`includes`, `excludes`, `sedCount` 1–1024 and `quitAfter` 0–30000 |
| `trusted-click-element` | At most 16 CSS/delay steps, optional `;`/`|` separator, open shadow roots via `>>>`, cookie/localStorage assertions, delay/timeout at most 30 s; XPath and visibility directives skipped |
| `trusted-replace-argument` | Existing dotted method, index ±31 or `this`, safe/JSON constant, `add:number` or `repl:/pattern/replacement/flags`, optional condition |
| `trusted-replace-outbound-text` | Existing method, pattern, replacement, optional condition/base64 encoding; native nonstrings/errors preserved |
| `trusted-suppress-native-method` | Existing method, up to 16 primitive/pattern signature slots or `*`, optional prevent/abort and stack pattern; unknown slots and debug forms skipped |
| `trusted-prevent-dom-bypass` | Existing insertion method plus explicit target API; delegates only anonymous/same-document same-origin child contexts; wholesale window replacement skipped |
| `trusted-override-element-method` | Existing method, optional native selector, empty/constant/throw disposition |
| `trusted-create-html` | Top-frame parent CSS selector, HTML, optional removal duration, up to four targets and loading/interactive/complete run phase |
| `prevent-clipboard-write` | Requires trusted provenance despite its name; pattern and optional `excludeMatches`; missing clipboard APIs pass through; alert markup skipped |
| `mpegdash-prune` | XHR URL/method predicate and CSS Period selector; fully specified start/duration attributes, at most 128 Periods, zero selected durations and recalculate starts/total; XPath/live/incomplete manifests pass through |

Trusted text replacements retain `$1`, `$&`, `$$` and prefix/suffix substitution.
Bodies and expanded results are capped at 64 KiB, with at most 1024 replacements;
oversized output fails open before constructing a large result. Node work uses
4096 visits and 4 MiB text work per batch. At most 32 advanced DOM rules share one
observer, with at most 128 observed roots and a 30 s lifetime. Click steps run once
without polling. Advanced DOM work stops on pagehide. It does not emulate user
activation or bypass clipboard permissions, CSP or Trusted Types. HTML parsing
retains native script inertness and policy checks. Existing APIs are hooked;
late-created custom method chains are not added by these helpers.

A custom untrusted exception may disable a trusted instruction, since exceptions
execute no code. Cache restoration preserves that distinction. An untrusted
positive rule still cannot enable an operation through its short alias or cache.

## WebView integration and limits

AndroidX WebKit 1.14.0 and minimum SDK 21 are unchanged. The existing worker prepares
routing, scripts and resources before initial loading. `WebViewCompat` document-start
registration uses feature detection and the legacy API's page world, which can access
page globals. There is one registration per WebView/snapshot. Recompile, disable and
disposal replace or remove it; deferred compilation before WebView loading is preserved.

AndroidX's allowed-origin grammar cannot express arbitrary HTTP(S) ports. Registration
therefore uses the native wildcard, followed by a synchronous own-frame HTTP(S)/host
gate before parsing rule data or installing hooks. Private app assets are excluded.
Cross-origin frames match their own hostname, not their parent. about:blank, data and
file frames are excluded. Unrelated pages install no hooks. The routing table uses
literal host buckets, not prototype-keyed objects.

Registration and removal have positive feature checks verified by Android lint.
Providers lacking DOCUMENT_START_SCRIPT skip scriptlets and retain network/cosmetic
filtering. There is no late injection fallback. Native API-dependent operations fail
open when their API is absent. Existing documents, including restored BFCache documents,
retain their installed hooks until reload. Rules updated during a document's lifetime
become active when a new document loads. Main-world page code can also overwrite
configurable native function hooks; this is not a hostile-page security boundary.

DOM operations share one MutationObserver and a protected native animation-frame batch;
there is no periodic polling. Attribute observation is limited to relevant names unless
dialog rules require general observation. Attribute/class writes avoid repeated no-op
mutations. Limits are 32 DOM instructions, 4096 nodes per sweep and 1024 per rule.
Default attribute removal waits for the interactive DOM in WebView; `asap` starts early.
Class removal starts early and stops at complete unless `stay` is specified. Attribute
setting starts at complete and tracks later inserted elements. Node-text removal is
bounded and stops at interactive. Its ability to prevent the first parser-blocking
inline script still requires real WebView timing verification.

Captured native timers/listeners protect cosmetics and scriptlet housekeeping from
other selected hooks. No wrapper is installed without a matching instruction. Shared
function hooks use one proxy per owner/property. Maximum instructions are 1024 per frame,
65,536 per compilation, and 16 MiB of UTF-8 routing data. Most operations have eight
argument fields; trusted cookies retain their ten-field contract. Most arguments
remain limited to 512 characters; JSON editors and trusted helpers accept up to 8192 characters
within the 8192-character expression/line cap and their operation-specific limits.
The CSS selector length/depth and storage/cookie enumeration budgets are also bounded.
Native selector-query cost still depends on document size and selector complexity.

Eval wrapping changes direct eval into an indirect native call, as upstream does; code
that relies on lexical eval scope can behave differently. Cookies cannot access HttpOnly
values, and browser cookie/storage policies still apply. WebRTC shims intentionally
remove selected APIs/configurations and can affect communication sites if enabled by a
matching list rule. Fixed vendor shims implement the upstream subset, not entire vendor
SDKs. Ordinary login callbacks, session keys, nonmatching requests/events and properties
are covered by behavior tests; arbitrary websites still need device checks.

API references:
- https://developer.android.com/reference/androidx/webkit/WebViewCompat
- https://developer.android.com/reference/androidx/webkit/WebViewFeature

## Default subscription action

The preset picker starts with uBlock Ads, Privacy, Badware, Quick fixes and Unbreak.
**Actions → Add uBlock Origin defaults** adds those five plus EasyList, EasyPrivacy,
Online Malicious URL Blocklist and Peter Lowe’s Ad and tracking server list. This matches
the nine global defaults in the supplied upstream assets.json and the official source
checked 2026-10-16:
https://github.com/gorhill/uBlock/blob/master/assets/assets.json

The action is available even with an empty subscription list and is absent from the
Add list dialog. This is user-initiated. Existing subscriptions with the same source URL
retain their content, enabled state and order. Missing lists append in preset order.
Individual failures produce a short snackbar with Retry, and only missing entries are
retried; one deferred rule-change notification follows the batch. Locale-specific,
optional annoyance and experimental lists are not silently selected. No subscriptions
are fetched at startup.

The URLHaus shortcut uses the maintainer's Vivaldi/Brave online variant, whose `$document`
rules preserve malicious paths and IP addresses. This supplies navigation protection;
it does not claim uBO's broader `$all` behavior. The former AdGuard-format shortcut
contained only `$all` rules and could not import. Silently dropping `$all` would change
its semantics: upstream also covers popups and inline font/script restrictions via CSP.
The engine still skips `$all`. Existing manually imported sources are not rewritten.
Format reference: https://gitlab.com/malware-filter/urlhaus-filter#lite-version-online-links-only

Raw parser diagnostics, exception messages and failed-list names are logged, never bound
to screen text. Add/edit failures use fixed input messages. Refresh/default batches use
counts and a retry action; a compile failure shows a short neutral status with Retry.
Previously active rules and subscription data remain intact when an update fails.

### Reported subscription failures, rechecked 2026-10-16

The same Quick fixes and Unbreak bytes were measured against the v1.0.14 and v1.0.15
source trees. The former rejected these complete lists because valid advanced scopes
were classified as invalid. They now import their supported rules and skip those scopes.

| Input | v1.0.14 import | v1.0.15 import | v1.0.15 supported / skipped |
| --- | --- | --- | ---: |
| Quick fixes | Rejected: 3 invalid scopes | Accepted; 0 invalid | 250 / 176 |
| Unbreak | Rejected: 1 invalid scope | Accepted; 0 invalid | 2200 / 339 |
| URLHaus AdGuard format | Rejected: `$all` unsupported | Still unsupported | 0 / 23980 |
| URLHaus Vivaldi/Brave format | Accepted by engine | Used by new preset downloads | 23980 / 0 |

The two URLHaus formats are different published inputs; this is a subscription-format
correction, not an increase in scriptlet support. SHA-256 values identify the tested files:

- Quick fixes: `65cdd45ec71e63c5fc5c5988e0faaaba760a5c7557e8e64837ffad5209f2a966`
- Unbreak: `e9d95cbb61cc83990e75104a4103585af146c6c6e8dfda07cad6c9713ddbb632`
- URLHaus AdGuard: `77038af09b0c3a0b74c803f1e19313cc47d9880b7970f85f2cfb0127cc3bfc8b`
- URLHaus Vivaldi/Brave: `676bba3c308420a93a4b7ee4d178c129286fafd05ca01f2da9b55fdbee9a532f`

## Cache and current verification

[CACHE.md](CACHE.md) assesses all four opportunities, records the binary format and
recovery design, and measures parse/restore/index costs. The app version no longer
invalidates cache identity. Per-source records survive changes to other lists.

Coverage includes JVM engine and app/Robolectric tests, Android lint, offline
Python/Node tests and an opt-in native Chromium suite. The latter exercises Fetch
streams, XHR, DOMParser/XMLSerializer, native synchronous events, cloning, abort,
reuse, XML/HLS pruning, trusted data, oversized/stalled bodies and overlays.
These are host Chromium tests, not Android WebView device tests. Android provider
checks remain necessary for document-start/frame timing, Navigation API, BFCache,
and representative authentication/media pages.

`SCRIPTLET_CHROMIUM` opts in to the browser suite using an installed executable;
Playwright must be available. Offline checks never install a browser.
`FILTER_CACHE_BENCHMARK=1` enables the JVM cache benchmark.

## Historical measurements through v1.0.15

The following data describes the historical baseline through v1.0.15.

### Exact-input measurements

Input: the supplied `ublock-origin-filters.txt`, 473295 bytes. SHA-256:
`3bde7d558863ea2431b868f4d910a8d6d484486fbd576e464d0ecc85446ef3c6`.
Its header contains a timestamp placeholder, so the hash identifies the tested version.
Counts exclude comments and inactive conditional branches and precede page deduplication.
Original bundle and v1.0.13 counts were remeasured with the same bytes.

| Metric | Bundle v1.0.12 | Saved v1.0.13 | v1.0.14 | v1.0.15 |
| --- | ---: | ---: | ---: | ---: |
| Total active rules | 6064 | 6064 | 6064 | 6064 |
| Network rules | 1432 | 1432 | 1432 | 1432 |
| Cosmetic rules | 1771 | 1771 | 1771 | 1771 |
| Executable scriptlet rules | 0 | 1525 | 1759 | 1759 |
| Total supported | 3203 | 4728 | 4962 | 4962 |
| Total skipped | 2861 | 1336 | 1102 | 1102 |
| Skipped scriptlets | 2444 | 919 | 685 | 685 |
| Invalid rules | 0 | 0 | 0 | 0 |

The v1.0.15 follow-up changes subscription handling and feedback; it adds no scriptlet
implementations and does not alter this corpus's executable-rule count.

| Active scriptlet name | Total | v1.0.13 executable | v1.0.14 executable |
| --- | ---: | ---: | ---: |
| `acs` | 345 | 250 | 250 |
| `aeld` | 199 | 134 | 134 |
| `aopr` | 635 | 473 | 473 |
| `aopw` | 181 | 122 | 122 |
| `aost` | 22 | 0 | 19 |
| `cookie-remover` | 2 | 0 | 2 |
| `disable-newtab-links` | 2 | 0 | 1 |
| `href-sanitizer` | 1 | 0 | 1 |
| `json-edit` | 1 | 0 | 0 |
| `json-edit-fetch-response` | 1 | 0 | 0 |
| `json-prune` | 19 | 12 | 12 |
| `json-prune-fetch-response` | 1 | 0 | 0 |
| `json-prune-xhr-response` | 1 | 0 | 0 |
| `jsonl-edit-xhr-response` | 1 | 0 | 0 |
| `nano-sib` | 51 | 0 | 40 |
| `nano-stb` | 31 | 0 | 23 |
| `no-fetch-if` | 23 | 19 | 19 |
| `no-xhr-if` | 22 | 0 | 0 |
| `nobab` | 15 | 0 | 13 |
| `noeval` | 24 | 0 | 21 |
| `noeval-if` | 5 | 0 | 4 |
| `nofab` | 10 | 0 | 9 |
| `norafif` | 1 | 0 | 1 |
| `nosiif` | 26 | 0 | 23 |
| `nostif` | 193 | 175 | 175 |
| `nowebrtc` | 47 | 0 | 24 |
| `nowoif` | 178 | 87 | 87 |
| `popads-dummy` | 18 | 0 | 12 |
| `popads.net` | 3 | 0 | 1 |
| `ra` | 28 | 0 | 12 |
| `refresh-defuser` | 2 | 0 | 2 |
| `rmnt` | 27 | 0 | 19 |
| `rpnt` | 11 | 0 | 0 |
| `set` | 301 | 252 | 252 |
| `set-constant` | 1 | 1 | 1 |
| `set-cookie` | 6 | 0 | 6 |
| `set-local-storage-item` | 1 | 0 | 1 |
| `trusted-replace-xhr-response` | 7 | 0 | 0 |
| `trusted-rpfr` | 2 | 0 | 0 |

## Validation and performance

The final v1.0.15 implementation passed 61 standalone engine tests, 709 app/Robolectric
unit tests, 90 Node JavaScript/cosmetic tests, 50 Python tooling tests, Spotless and
Kotlin-brace checks, and debug APK assembly. The APK was inspected: both runtimes,
all 14 shims, the central attribution notice and full GPL text match their sources.
No dependencies or Android SDK requirements changed.

Tests include real list examples, every added alias, malformed/unsafe arguments,
non-configurable properties, shared/conflicting hooks, domain exceptions/exclusions,
cache restoration/invalidation, navigation/frame snapshots, unavailable providers,
DOM milestones/BFCache observer cleanup, safe callback queues, storage denial,
conditional WebRTC/Navigation APIs, and unrelated login/session behavior.
The default-list batch tests cover existing disabled URL subscriptions, ordering,
deduplication, partial failures and retrying only missing subscriptions.
New regressions exercise real Quick fixes/Unbreak scopes through parser validation
and download/save, preserve saved rules after malformed refreshes, verify URLHaus
navigation/path boundaries, and prevent raw diagnostics from entering UI messages.

Robolectric tests registration ownership, provider failures and service publication;
it does not execute a real Chromium renderer. Node 24.19.0 V8 executes the bundled
code in separate VM realms, with controlled DOM/native-API fixtures and real Fetch
Response objects. These are not real Android WebView tests. Real-device verification
is still required for document-start timing, parser-blocking script interception,
cross-origin frame timing, browser history/BFCache, cookie policies, layout APIs,
WebRTC providers and representative authentication/media websites.

Measurements below were collected for v1.0.14 and are actual host-runtime medians,
not phone page-load estimates. This follow-up changes no JavaScript or page hooks.
Both JS revisions used the same current harness and exact Kotlin-produced payloads.
There are five payload checks: YouTube property rules, publisher constants, unrelated
hosts and the newly supported imgdrive.net timer booster. Construction uses 31
samples, document-start 31 measured samples after five warmups, and timer dispatch
11 measured samples after five warmups, 200000 calls each. Native timers are stubs;
these values exclude Android scheduling, rendering, networking and real DOM traversal.

| Host measurement | v1.0.13 | v1.0.14 |
| --- | ---: | ---: |
| Injection source bytes | 189426 | 241640 |
| Source compilation, ms | 0.936 | 1.605 |
| Unrelated document gate, ms | 0.032 | 0.040 |
| YouTube document start, ms | 1.093 | 1.371 |
| Publisher document start, ms | 1.114 | 1.377 |
| Native timer stub, µs/call | 0.169 | 0.189 |
| One defuser rule, µs/call | 0.436 | 0.396 |
| Eight defuser rules, µs/call | 0.591 | 0.595 |

The final Kotlin corpus run measured warm compilation at 40.3 ms (v1.0.13) and
38.8 ms (v1.0.14), with lookup at 7.33 and 7.74 µs per call respectively. An earlier
expanded-engine run measured 50.8 ms; this variability prevents a speedup claim. Each is a median of
seven measured rounds after one warmup; lookup rounds each contain 1000 calls. These
are development-host observations affected by JVM warmup and load, not Android
benchmarks. The expanded script source costs approximately 51 KiB more for this
corpus; matching pages install only their selected operations. No Android page-load
or battery-impact measurement was available.

For reproduction, `ScriptletCorpusTest` accepts `UBLOCK_FILTER_FILE` and optionally
`SCRIPTLET_OUTPUT_DIR`. `scripts/benchmark-scriptlets.mjs` reads the exported injection;
its optional second path selects an older runtime for a same-harness comparison.
`scripts/ci/test-local.sh checks` runs all JavaScript and Python tests.

Attribution is consolidated in [the packaged notice](src/main/resources/META-INF/ublock-NOTICE.txt).
Required original notices remain on copied upstream source files.


## v1.0.22: entity routing, cookie flags and inspection reasons

Scriptlet scopes now accept entities (`ticketmaster.*`), mixed hostname/entity
inclusions, exclusions and both instruction-specific and blanket exceptions.
Entity names are derived from the registrable domain, including private suffixes,
wildcard suffixes, PSL exceptions, IDNs and unknown suffixes. Regex and path scopes
remain unsupported; they are never treated as ordinary hostname matches.

The build copies the suffix data from the resolved OkHttp JVM dependency into this
module's resources. No network fetch, separate PSL version or internal OkHttp API
is used at runtime. The compiler normalizes and sorts it once, then includes it only
in snapshots that use scriptlet entities. Document-start scripts keep the cheap
host/entity-label gate before parsing routing data. Candidate frames resolve the
suffix once using binary search, then use indexed entity buckets. No new request
hooks, observers or per-request diagnostics are installed. Existing 2 MiB routing,
8,192 rule and 128 instruction limits remain. This is bounded additional work on
entity candidate frames, not a claim of zero Android startup cost.

`set-cookie` and `set-cookie-reload` support `reload, 0|1` and
`dontOverwrite, 0|1` pairs after the path argument. Unknown, duplicate or incomplete
options skip the whole rule. Canonical option order and defaults preserve exception
matching and cache round trips. Reload happens only after a changed cookie is
successfully read back. Existing safe-value and trusted-source restrictions remain;
trusted-cookie expiry/domain options are not added by this patch.

List Info shows a breakdown of skipped rules: popup, CSP, HTML, unsupported
cosmetics, scriptlet scope/name/arguments/trust, network syntax, safety limits and
invalid syntax. Counts are collected during list inspection only. Parser acceptance
is separate from runtime effectiveness. `$popup`, CSP/header/body filtering and
unbounded scriptlet patterns are not made supported by assigning a reason.

Verification includes native lookup/cache tests and JavaScript routing/cookie tests.
For full suffix parity, run `:adblock:test` with `SCRIPTLET_OUTPUT_DIR` set to a
temporary directory, then run `node --test scripts/tests/entity-routing.test.mjs`
with the same environment variable. The Kotlin test exports dispatcher cases from
the entire resolved PSL; Node executes the actual Kotlin-produced source and
compares it with OkHttp-backed lookup results. This is desktop verification;
Android device timing remains a separate measurement.


## v1.0.23: property getters and cookie contracts

Constant scriptlets can replace configurable getters without setters, including
WebIDL getters which reject their prototype as the initial receiver. Only constant
leaf installation receives this capability. Incompatible untrusted values and
non-configurable or non-writable data properties retain their original descriptors.
Existing hook ordering, shared ancestors and type-change release remain; trusted
constants stay set. No observers, polling or new property-read branches are added.

Cookie setters preserve valid cookie bytes and percent-encode only unsupported
characters. Trusted cookies now use the upstream name/value/expiry/path positions,
add `Secure`, and support `1day`, `1year` or 0..2,147,483,647 expiry seconds. Calendar
expiry uses the captured native Date methods. `$now$`, `$currentDate$` and
`$currentISODate$` expand from one timestamp before adjusting expiry. Trusted names
must be cookie tokens, and unsupported placeholders still skip the whole rule.

Both cookie families accept `reload, 0|1` and `dontOverwrite, 0|1`; trusted cookies
also accept a literal `domain`. Domains are normalized, including IDNs. Regex domain
options remain skipped. Options/defaults are canonicalized for exceptions and cache
round trips. A same-value non-reloading write can refresh expiry, path or security
attributes. Reload requires a changed value to be successfully read back; denied
or ignored writes never trigger a reload. Trust gating and safe untrusted values
remain enforced. Schema 13 invalidates the old positional contract.

These changes run through the existing selected document-start hooks and cookie
writes. They add no network interception or DOM scans. Native-device performance
is not inferred from desktop tests. With `SCRIPTLET_OUTPUT_DIR` set, the engine tests
export a restored cookie dispatcher; the JavaScript tests execute it to verify
canonical argument positions, routing exceptions and reload guards end to end.
