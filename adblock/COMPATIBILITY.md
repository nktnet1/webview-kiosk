# uAssets and subscription compatibility - v1.0.17

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

| Publisher input | SHA-256 | v1.0.16 skips | v1.0.17 skips |
| --- | --- | ---: | ---: |
| EasyList | `8c3ae2e3528ce9b66f217e3ccf6aa4c4c0bbaae3d299fd920e7a0617d03834f6` | 3177 | 3000 |
| EasyPrivacy | `1a8fc2e0f7706696a7f659203ad31ceb15f26c82c3dafd8a7dd6a61cd7ac3250` | 103 | 9 |

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

| Count | Family | Assessment |
| ---: | --- | --- |
| 2969 | `$popup` rules | Feasible with native popup ownership and opener/target context. The current blocker lacks that lifecycle; applying these to ordinary navigation would change their meaning. |
| 28 | Procedural cosmetics and CSS-property matching | Implementation/contract gaps, including sibling/nested chains and patterns beyond the text limits. WebView does not inherently forbid these. |
| 3 | CSP rules/exceptions | The current integration cannot edit original response headers. A response-owning network integration would be needed. |

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

| Gap | Feasibility and current decision |
| --- | --- |
| JSON/object/JSONL edit DSLs and additional trusted pruning | Feasible implementation work. They need a complete bounded parser, mutation semantics and response/request tests; approximating the DSL is unsafe. |
| Broader existing scriptlet arguments | Feasible work, including cookie reload extras, XPath, advanced media/text patterns and late custom method chains. Current subsets reject these. |
| Scriptlet entity/regex/path scopes and descendant-frame routing | Feasible but not supplied by the current JavaScript dispatcher. Native network/cosmetic entity matching does not imply scriptlet entity support. |
| Google IMA/DAI and other unbundled API/resource shims | Feasible compatibility work. The two new Amazon apstag/Chartbeat redirects do not make them valid `+js` entry points. |
| Popup filtering and `close-window` | Require native window ownership, opener context and a defined kiosk close policy. No page-callable app-exit bridge is introduced. |
| CSP and response-time HTML filtering (`##^`) | No transparent original-response editing path in the current WebView integration. Page DOM mutation is not equivalent to filtering before parsing. |
| Document-start hooks | Require `WebViewFeature.DOCUMENT_START_SCRIPT`; registration is feature-guarded. Unsupported providers skip scriptlets, since late injection cannot recover earlier author-script calls. |
| Navigation cancellation | Requires a native Navigation API and cancelable events. Ordinary noncancelable events cannot provide equivalent behavior. |
| Debug/configuration helpers | Deliberately excluded from production injection. |

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
