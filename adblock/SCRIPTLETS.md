# Scriptlet compatibility — v1.0.14

The standalone Kotlin engine compiles validated `##+js(...)` data into a fixed,
locally bundled JavaScript library. Downloaded filters never supply executable
source, replacement functions, script URLs, HTML, or an unrestricted expression.
The native `eval` hook handles page-originated calls; filter text is never evaluated.

The supplied upstream archive has 99 parameterized scriptlets and 30 JavaScript
resources, totaling 129 canonical names (194 with aliases). Its 54 helper functions
are separate. This patch implements 56 canonical names: 55 tested subsets and one
conditional Navigation API operation. **27 implemented names are absent from the
supplied active filter corpus.** See the complete
[129-entry catalogue](SCRIPTLET-CATALOG.md), including aliases and exclusion reasons.

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
| ✅ | `aeld` | Event name and callback/handleEvent source; no extra options |
| ✅ | `nostif`, `nosiif` | Callback pattern, exact/range delay, negation |
| ✅ | `norafif` | Callback pattern or negation; logging-only invocation skipped |
| ✅ | `nano-stb`, `nano-sib` | Pattern, exact delay or `*`, finite multiplier |
| ✅ | `noeval-if` | One nonempty pattern; native page eval pass-through otherwise |
| ✅ | `noeval`, `noeval-silent` | Legacy no-argument prevention of all eval calls |
| ✅ | `json-prune` | Own paths, guards, `[]`/`*`; JSON.parse and Response.json |
| ✅ | `no-fetch-if` | URL/method predicates and safe empty canned responses |
| ✅ | Local/session storage setters | Key plus safe value or exact/regex `$remove$` |
| ✅ | Cookie setters/reload/removal | Safe values, default/root/none path; no extra options |
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
| ✅ | Fourteen fixed API shims | No filter arguments; see catalogue for exact names |
| ⚠️ | `prevent-navigation` | Requires native Navigation API and cancelable automatic events |
| ❌ | `no-xhr-if` / `prevent-xhr` | Async state, reuse, abort and events unverified |
| ❌ | Fetch/XHR response prune/edit | Response lifecycle/stream contracts not implemented |
| ❌ | Trusted scriptlets | Subscription storage has no trusted-source provenance |
| ❌ | XML, media, object editing DSLs | Complete parsers and lifecycle contracts unverified |
| ❌ | Overlay/debug/close-window | Polling, debugger or missing native-window adapter |

The safe constant set is empty string, undefined/null, booleans, empty arrays or
objects, noop/true/false/throw functions, and bounded integers. Functions are locally
predefined. Storage/cookie values use upstream's safe-word whitelist and integers.
Attribute setters reject executable attributes and navigation/HTML/CSS destinations.
Href sanitation accepts only HTTP(S) destinations; transformation pipelines are skipped.
CSS spoofing permits display, visibility, opacity, position, dimensions, offsets and
four private rectangle fields. No arbitrary CSS declarations are installed.

Patterns use the existing bounded literal/regex subset. Lookarounds, backreferences,
repeated groups, unsafe repeats and global/sticky flags are skipped. Matchers compile
once per selected rule. Function-source matching caches at most 512 entries/256 KiB;
individual strings are limited to 64 KiB. Oversized or unusual native arguments pass
through. Timer multiplier defaults to 0.05 and is clamped to 0.001–50, as upstream.

## Domains, ordering and security

Included ordinary hostnames and subdomains, domain exclusions, canonical exact
scriptlet exceptions, and blank `#@#+js()` exceptions are supported. Global injection,
entity (`site.*`), regex-domain, and IPv6-domain forms remain skipped. Normal CSS
exceptions do not silently cancel scriptlet rules. Conditional directives and expanded
includes retain list ordering. Aliases/default arguments normalize before deduplication.
Conflicting terminal property hooks use the first instruction; compatible checks share
accessors. Non-configurable/read-only properties fail open. Missing ancestors are hooked
without manufacturing objects; replacement owners are handled within a 512-slot budget.

Compiled snapshots use schema 7, so schema 6 is recompiled. Cached instructions undergo
validation again, including fixed resource keys. Filters cannot choose a resource path.
Only shims needed by the compiled snapshot are bundled into its injection. No JS bridge
or privileged page-callable method is introduced.

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
function hooks use one proxy per owner/property. Maximum instructions are 128 per frame,
8192 per compilation, eight argument fields, 512 characters each, and 2 MiB routing data.
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
“Add uBlock Origin defaults” adds those five plus EasyList, EasyPrivacy, Online Malicious
URL Blocklist and Peter Lowe’s Ad and tracking server list. This matches the nine global
defaults in the supplied upstream assets.json and the official source checked 2026-10-16:
https://github.com/gorhill/uBlock/blob/master/assets/assets.json

This is user-initiated. Existing subscriptions with the same source URL retain their
content, enabled state and order. Missing lists append in preset order. Individual
failures are reported and only missing entries are retried; one deferred rule-change
notification follows the batch. Locale-specific, optional annoyance and experimental
lists are not silently selected. No subscriptions are fetched at startup.

## Exact-input measurements

Input: the supplied `ublock-origin-filters.txt`, 473295 bytes. SHA-256:
`3bde7d558863ea2431b868f4d910a8d6d484486fbd576e464d0ecc85446ef3c6`.
Its header contains a timestamp placeholder, so the hash identifies the tested version.
Counts exclude comments and inactive conditional branches and precede page deduplication.
Original bundle and v1.0.13 counts were remeasured with the same bytes.

| Metric | Bundle v1.0.12 | Saved v1.0.13 | v1.0.14 |
| --- | ---: | ---: | ---: |
| Total active rules | 6064 | 6064 | 6064 |
| Network rules | 1432 | 1432 | 1432 |
| Cosmetic rules | 1771 | 1771 | 1771 |
| Executable scriptlet rules | 0 | 1525 | 1759 |
| Total supported | 3203 | 4728 | 4962 |
| Total skipped | 2861 | 1336 | 1102 |
| Skipped scriptlets | 2444 | 919 | 685 |
| Invalid rules | 0 | 0 | 0 |

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

The final implementation passed 57 standalone engine tests, 705 app/Robolectric
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

Robolectric tests registration ownership, provider failures and service publication;
it does not execute a real Chromium renderer. Node 24.19.0 V8 executes the bundled
code in separate VM realms, with controlled DOM/native-API fixtures and real Fetch
Response objects. These are not real Android WebView tests. Real-device verification
is still required for document-start timing, parser-blocking script interception,
cross-origin frame timing, browser history/BFCache, cookie policies, layout APIs,
WebRTC providers and representative authentication/media websites.

Measurements below are actual host-runtime medians, not phone page-load estimates.
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
