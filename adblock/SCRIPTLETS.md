# Scriptlet compatibility

The Kotlin engine compiles a bounded subset of uBlock Origin `##+js(...)` filters.
A fixed, locally bundled runtime implements nine scriptlet families. Subscription
text supplies data only. No subscription JavaScript, `eval`, `Function`
constructor, remote resource code, or HTML script-element injection is used.

## Supported operations

✅ means executable, behavior-tested core semantics. ⚠️ identifies intentionally
unsupported variants. All families currently require ordinary included hostnames;
entity (`site.*`), regex-domain, IPv6-domain, and global injection rules are skipped.
Exclusions and global or domain-specific scriptlet exceptions are supported.

| Family | Status | Implemented arguments | Skipped variants |
| --- | --- | --- | --- |
| `set` | ✅ | Dotted property, predefined constant or bounded integer | ⚠️ `runAt`, `as`, trust |
| `aopr` | ✅ | Dotted property; missing and replaced ancestors | ⚠️ Unsafe property paths |
| `aopw` | ✅ | Dotted property; missing and replaced ancestors | ⚠️ Unsafe property paths |
| `nowoif` | ✅ | All calls, literal/regex, negation | ⚠️ Delay and decoy modes |
| `aeld` | ✅ | Event type and function/`handleEvent` source matching | ⚠️ `elements`, `runAt` |
| `nostif` | ✅ | Callback pattern, exact/ranged delay, negation | ⚠️ Additional options |
| `acs` | ✅ | Property, script text, optional script `src` match | ⚠️ Complex regex syntax |
| `json-prune` | ✅ | Own dotted paths, guards, `[]` and `*` | ⚠️ `[-]`, `{-}`, stacks |
| `no-fetch-if` | ✅ | URL/method predicates, empty/string/object/array response | ⚠️ Other options |
| `no-xhr-if` | ❌ | None | Faithful XHR state/event/abort emulation is not implemented |

Long names, `.js` suffixes, and the common aliases in `ScriptletParser` normalize
to typed instructions. `set` supports `undefined`, `null`, booleans, empty strings,
empty arrays/objects, `noopFunc`, `trueFunc`, `falseFunc`, `throwFunc`, and integers
from -32767 to 32767. Arbitrary strings, expressions, and trusted scriptlets stay
unsupported. Constants relinquish their trap after a non-null type-changing write.

`aeld` and `nostif` with no useful matching arguments are logging-only in uBO;
they remain skipped. `nowoif` with no arguments prevents all `window.open` calls.
JSON pruning covers both `JSON.parse` (including revivers) and `Response.json`.
Fetch handling supports string URLs and `Request` objects with method overrides;
other request inputs pass through. Fetch response directives are limited to empty
text, `emptyStr`, `emptyObj`, and `emptyArr`. Random, WAR, custom response-property,
throttle, and unknown response directives are skipped as complete instructions.
XHR defusers are possible in WebView, but require faithful synchronous/asynchronous
state transitions, response types, event order, abort, and reuse behavior. This
patch does not approximate them with a network failure or a partial fake object.

## Integration and lifecycle

Parsing, argument validation, deduplication, indexing, and injection-source
construction run on the existing compilation worker. The app receives one
immutable `ScriptletInjection` after successful compilation. Settings retain
their existing deferred-compilation gate. Cache schema 6 stores typed instructions
and scopes, validates restored arguments, and invalidates schema 5 caches.

`WebViewScriptlets` installs one `addDocumentStartJavaScript` registration on Main
before navigation. The legacy API executes in page world and in child frames.
It is available with the existing AndroidX WebKit 1.14.0 dependency; minimum
Android SDK 21 is unchanged. No isolated-world or asynchronous bridge is used.
A StateFlow subscription replaces/removes the registration on snapshot changes,
and the app's navigation boundary synchronously refreshes it before starting a
load. Repeated refreshes of the same snapshot do not install duplicates. Disposal
cancels the subscription and removes the registration.

AndroidX's documented origin grammar cannot express a hostname on every possible
HTTP(S) port. The native registration therefore uses `*`, with a small synchronous
own-frame protocol/host gate before the JSON instruction payload is deserialized. No
native callable bridge is exposed. The gate uses only compiled hostname strings,
includes the hostname apex and subdomains, respects label boundaries, and excludes
private app assets. HTTP(S) on custom ports works. The per-frame dispatcher then
applies exclusions, exact normalized exceptions, blanket `#@#+js()` exceptions,
and instruction deduplication. Unrelated frames install no hooks and do not read
the instruction payload. An unrelated top-level page may contain a targeted
cross-origin child frame, which independently receives its own matching hooks.

Scriptlet exceptions take precedence across lists regardless of their order.
Within the remaining rules, first occurrence order is retained; the first
terminal property instruction wins a conflict. Multiple ACS checks and shared
ancestors use one accessor pair. Function defusers share one proxy per target.
CSS-only `#@#` exceptions and network cosmetic-hiding options do not silently
cancel scriptlets. Existing cosmetic updates capture their native timer functions
before scriptlets run, so a matching `nostif` cannot defuse cosmetic scheduling.

When `DOCUMENT_START_SCRIPT` is unavailable, scriptlets remain inactive. Network
and existing cosmetic filtering continue; no late scriptlet injection is attempted.
Updated rules apply to subsequent documents. Already modified live documents
(including restored history documents) need a reload or WebView recreation to
remove their hooks. Navigation/reload gets a fresh document and independent state.

## Bounds and failure behavior

- 8,192 enabled scriptlet records and 128 distinct instructions per document.
- 4,096 characters per instruction expression; 512 per argument; 256 per pattern.
- Property paths have at most 12 components; unsafe prototype access is rejected.
- JSON instructions have at most 16 paths, each with at most 24 components.
- JSON traversal plans at most 4,096 visits per rule/call before making deletions.
  Budget exhaustion skips the whole pruning instruction, avoiding partial edits.
- Property observers have at most 512 slots per document. No polling or observers
  scan the DOM, and no timer is created just to wait for a missing property.
- Function-source caches have at most 512 entries and 262,144 characters of text.
- Regexes compile once per selected instruction. The accepted subset rejects
  lookarounds, backreferences, quantified groups, bounded repeats, and quantified
  assertions. At most one repetition is allowed, with an absolute start anchor
  and no top-level alternation. There are at most four alternation operators.
  Stateful and Unicode flags are unsupported; repetitions also forbid multiline.
- Patterns inspect at most 65,536 characters of a candidate string; larger inputs fail
  open. Regex validation in Kotlin is never used to match page text.
- Routing data is bounded to two million characters; excess global input fails
  compilation atomically and retains the previous working snapshot.

Read-only and non-configurable properties are left intact. Configurable children
of read-only ancestors can still be trapped. Prototype/constructor access paths
that could expand the mutation surface are rejected, while explicit ordinary
`Object.prototype.someAdProperty` paths are supported. Nonmatching event handlers,
timeouts, popups, Fetch calls, and unrelated login/session state pass through.
Abort traps suppress only their own random-token `ReferenceError` error reports.

## Audit and verification

The runtime is a locally reviewed, bounded adaptation of uBO semantics and small
implementation patterns, with project-specific dispatch, hooks, and limits. The
upstream copyright notice and complete GPL-3.0-or-later licence are retained; see
[third-party notices](THIRD_PARTY_NOTICES.md). This review is not an independent
third-party security certification.
Review checks cover data/source separation, fixed dispatch, prototype pollution,
regex bounds, origin boundaries, native-descriptor handling, function deduplication,
cache invalidation, cancellation, and resource ownership. Subscription strings
are JSON-escaped, including quotes, backslashes, HTML delimiters, and U+2028/U+2029.
The host index uses Map entries rather than object-literal prototype-sensitive keys.

Kotlin tests cover each implemented family with real uBO examples, invalid forms,
quoting, aliases, ordering, conditional/include handling, exclusions/exceptions,
cache restoration/invalidation, data serialization, and caps. App JVM/Robolectric
tests exercise deferred publication, disable/re-enable, provider unavailability,
registration replacement, retry, and disposal. Node 24 executes the exact bundled
runtime in isolated V8 contexts: property reads/writes, late ancestors, readonly
properties, conflicts, error suppression, matching functions, timers, current
scripts, JSON parsing/Response bodies, Fetch, frames, navigation, and host gates.
The shared `checks` suite runs both cosmetic and scriptlet JavaScript tests.

Robolectric does not execute Chromium page JavaScript. No actual Android device or
emulator was available here. Remaining device checks are native document-start
ordering against the first inline page script, provider-specific execution worlds,
HTTP(S) cross-origin child frames, custom ports, redirects, reload/back-forward
behavior, updates during navigation, and representative login/authentication
flows. V8 fixtures are behavioral coverage, not a claim of live-site equivalence.
Worker/service-worker globals and opaque/about/srcdoc documents are not covered by
these frame-global hooks. Entity-domain routing would require additional public
suffix handling; it is an implementation gap, not an Android impossibility.

## Supplied corpus

The authoritative base is bundle commit `dbdb69357d212a7bf1b63083af569dd252f2152e`.
The uploaded list's timestamp field is `%timestamp%`; its exact version is pinned
by SHA-256, not by an inferred upstream date:

`3bde7d558863ea2431b868f4d910a8d6d484486fbd576e464d0ecc85446ef3c6`

Counts include active conditional branches, before deduplication across domains
or lists. Comments and inactive branches are excluded. The input is unchanged.

| Metric | Supplied bundle | This patch |
| --- | ---: | ---: |
| Active rules | 6,064 | 6,064 |
| Supported network | 1,432 | 1,432 |
| Supported cosmetic | 1,771 | 1,771 |
| Supported scriptlets | 0 | 1,525 |
| Supported total | 3,203 | 4,728 |
| Skipped total | 2,861 | 1,336 |
| Skipped scriptlets | 2,444 | 919 |
| Invalid | 0 | 0 |

| Family (aliases combined) | Active input | Executable after | Still skipped |
| --- | ---: | ---: | ---: |
| `set` / `set-constant` | 302 | 253 | 49 |
| `aopr` | 635 | 473 | 162 |
| `aopw` | 181 | 122 | 59 |
| `nowoif` | 178 | 87 | 91 |
| `aeld` | 199 | 134 | 65 |
| `nostif` | 193 | 175 | 18 |
| `acs` | 345 | 250 | 95 |
| `json-prune` | 19 | 12 | 7 |
| `no-fetch-if` | 23 | 19 | 4 |
| `no-xhr-if` | 22 | 0 | 22 |
| Other families | 347 | 0 | 347 |

The 919 skipped scriptlets include unsupported domain scopes as well as argument
forms and unimplemented families. The remaining 417 skipped non-scriptlet rules
are unchanged. All ten requested families had zero executable rules in the base.

The other families remain ❌, with the same skipped counts before and after:

| Type | Active/skipped |
| --- | ---: |
| `aost` | 22 |
| `cookie-remover` | 2 |
| `disable-newtab-links` | 2 |
| `href-sanitizer` | 1 |
| `json-edit` | 1 |
| `json-edit-fetch-response` | 1 |
| `json-prune-fetch-response` | 1 |
| `json-prune-xhr-response` | 1 |
| `jsonl-edit-xhr-response` | 1 |
| `nano-sib` | 51 |
| `nano-stb` | 31 |
| `nobab` | 15 |
| `noeval` | 24 |
| `noeval-if` | 5 |
| `nofab` | 10 |
| `norafif` | 1 |
| `nosiif` | 26 |
| `nowebrtc` | 47 |
| `popads-dummy` | 18 |
| `popads.net` | 3 |
| `ra` | 28 |
| `refresh-defuser` | 2 |
| `rmnt` | 27 |
| `rpnt` | 11 |
| `set-cookie` | 6 |
| `set-local-storage-item` | 1 |
| `trusted-replace-xhr-response` | 7 |
| `trusted-rpfr` | 2 |

`ScriptletCorpusTest` accepts `UBLOCK_FILTER_FILE`, reports the exact digest,
per-type counts, and median compile/lookup times. Set `SCRIPTLET_OUTPUT_DIR` to
export the actual compiled payload and host counts outside the source tree.
`scripts/benchmark-scriptlets.mjs` accepts that payload's path, checks actual
Kotlin-produced rules on three fixtures, and reports host-V8 construction,
document-start, and timer-dispatch measurements. These are host microbenchmarks,
not estimates or measurements of Android page-load latency.

## Validation results

The full suites passed: 52 adblock JVM tests and 703 app JVM/Robolectric tests,
with zero failures or skips. The local `checks` runner passed 50 Python tests and
42 JavaScript tests. Three additional behavioral checks exercised the actual
Kotlin-exported corpus payload. `checkFormatting` passed Spotless and Detekt's
brace checks, and all added lines are at most 100 characters. `assembleDebug`
passed; the APK contains the exact runtime, attribution notice, and GPL text.

Earlier full app runs intermittently reached the MQTT loopback test broker's
five-second CONNACK gate timeout. A subsequent full run passed all 703 tests;
no MQTT production code or tests were modified.

Measured here on Linux, JDK 21, Node 24.19.0/V8, with an eight-CPU container limit:

| Measurement | Median |
| --- | ---: |
| Kotlin compile of the supplied full list, seven warm samples | 148.79 ms |
| Kotlin lookup at the busiest host, seven batches of 1,000 | 25.69 µs/call |
| V8 construction of the 189,426-byte corpus source, 31 samples | 5.66 ms |
| V8 document start, unrelated host, 31 samples | 0.070 ms |
| V8 document start, `www.youtube.com`, 31 samples | 6.42 ms |
| V8 document start, `timesofindia.indiatimes.com`, 31 samples | 3.35 ms |
| Timer-stub dispatch baseline, 11 warm batches of 200,000 | 0.372 µs/call |
| Timer-stub dispatch with one matching-family rule | 1.162 µs/call |
| Timer-stub dispatch with eight rules | 1.638 µs/call |

The busiest corpus host selects seven instructions. VM context construction is
outside document-start timings. Fresh Script constructions vary the source to
avoid V8's compilation-cache reuse. Timer callbacks are deliberately stubbed, so
these numbers measure proxy/matcher dispatch, not browser timer scheduling. The
increment over the stub baseline is about 0.79 µs/call for one rule and 1.27 µs/call
for eight. Results are measured medians, not estimates; they do not predict
Android renderer latency, memory use, or whole-page performance. No Android
performance result is claimed without device measurements.

## References reviewed

- [uBO resources and argument semantics][ubo]
- [AndroidX WebViewCompat API and origin grammar][webkit]
- [Chromium source][chromium], `android_webview/.../AwContents.java`, for the
  legacy API's `PAGE_WORLD_NAME` delegation.

[ubo]: https://github.com/gorhill/uBlock/wiki/Resources-Library
[webkit]: https://developer.android.com/reference/androidx/webkit/WebViewCompat
[chromium]: https://chromium.googlesource.com/chromium/src/+/main/android_webview/
