# Upstream scriptlet catalogue

The supplied archive contains 99 parameterized scriptlets and 30 JavaScript resources:
129 canonical names, or 194 names including aliases. Its 54 helper functions are separate.
Only 21 of the 30 resources have upstream inline-injection eligibility (`data: text`).

This version implements 114 canonical names: 113 tested subsets plus
`prevent-navigation`, which requires a native Navigation API. The other 15 remain skipped as `+js` entry points.
The earlier supplied list used 39 names resolving to 38 canonical names. Absence from
that list is not a reason for exclusion.
All ✅ entries remain subject to the argument contracts in [SCRIPTLETS.md](SCRIPTLETS.md).
API availability, matching documents, and documented resource limits also apply.

✅ Executable documented subset. ⚠️ Conditional feature. ↪️ Redirect resource. ❌ Skipped with reason.

## What the skipped entries mean

Most skipped entries are **not implemented yet**. Their absence does not establish a
WebView limitation. The reasons below distinguish missing work from platform constraints.
Supported entries also reject unimplemented argument forms rather than approximating them.

| Reason               | Examples                                         | What would be needed                                                          |
| -------------------- | ------------------------------------------------ | ----------------------------------------------------------------------------- |
| Argument subsets     | Remaining JSONPath forms and accessor-based data | Correct bounded semantics without invoking site getters                       |
| Eval semantics       | `evaldata-prune`                                 | A transparent direct-eval contract for unrelated calls                        |
| Native integration   | `close-window`                                   | A native window lifecycle and a defined kiosk close policy                    |
| Deliberate exclusion | Debug helpers                                    | Debugger hooks are not installed in the production blocker                    |
| Provider capability  | Document start                                   | `DOCUMENT_START_SCRIPT`; late injection cannot intercept earlier page scripts |
| Provider capability  | `prevent-navigation`                             | Native Navigation API                                                         |

Fresh approved uBlock preset downloads now carry trusted provenance. A custom list,
matching source URL or directive does not gain trust. Trusted editors, pruning, clipboard protection and IMA DAI have
explicit tested contracts; unsupported argument forms remain skipped. Several redirect-only files
in the archive are resources, not upstream `+js` entry points.

The concrete WebView constraint for all property/function hooks is reliable page-world
document-start injection. Providers without `DOCUMENT_START_SCRIPT` skip scriptlets.
Late evaluation cannot reliably intercept earlier page scripts. Native browser APIs,
HttpOnly cookies and cross-origin access retain their normal browser restrictions.
Fetch/XHR interception of page calls remains feasible in JavaScript; a complete extension
network/CSP/response-rewrite pipeline is not supplied by the current WebView integration.

## Canonical entries

| Canonical name                         | Status / contract or reason                              |
| -------------------------------------- | -------------------------------------------------------- |
| `abort-current-script.js`              | ✅ current-script                                        |
| `set-attr.js`                          | ✅ attributes                                            |
| `trusted-set-attr.js`                  | ✅ trusted safe attribute / literal value                |
| `remove-attr.js`                       | ✅ attributes                                            |
| `trusted-create-html.js`               | ✅ trusted bounded HTML; top frame only                  |
| `href-sanitizer.js`                    | ✅ links                                                 |
| `proxy-apply-config.js`                | ❌ configuration/debug                                   |
| `edit-outbound-object.js`              | ✅ validated editing subset; see SCRIPTLETS.md           |
| `trusted-edit-outbound-object.js`      | ✅ validated editing subset; see SCRIPTLETS.md           |
| `json-edit.js`                         | ✅ bounded JSONPath subset; see argument contract        |
| `trusted-json-edit.js`                 | ✅ validated editing subset; see SCRIPTLETS.md           |
| `edit-inbound-object.js`               | ✅ validated editing subset; see SCRIPTLETS.md           |
| `trusted-edit-inbound-object.js`       | ✅ validated editing subset; see SCRIPTLETS.md           |
| `edit-this-object.js`                  | ✅ validated editing subset; see SCRIPTLETS.md           |
| `trusted-edit-this-object.js`          | ✅ validated editing subset; see SCRIPTLETS.md           |
| `edit-object-on-getter.js`             | ✅ validated editing subset; see SCRIPTLETS.md           |
| `trusted-edit-object-on-getter.js`     | ✅ validated editing subset; see SCRIPTLETS.md           |
| `edit-object-on-setter.js`             | ✅ validated editing subset; see SCRIPTLETS.md           |
| `trusted-edit-object-on-setter.js`     | ✅ validated editing subset; see SCRIPTLETS.md           |
| `edit-element-object.js`               | ✅ validated editing subset; see SCRIPTLETS.md           |
| `trusted-edit-element-object.js`       | ✅ validated editing subset; see SCRIPTLETS.md           |
| `json-edit-xhr-response.js`            | ✅ bounded JSONPath subset; see argument contract        |
| `trusted-json-edit-xhr-response.js`    | ✅ validated editing subset; see SCRIPTLETS.md           |
| `json-edit-xhr-request.js`             | ✅ validated editing subset; see SCRIPTLETS.md           |
| `trusted-json-edit-xhr-request.js`     | ✅ validated editing subset; see SCRIPTLETS.md           |
| `json-edit-fetch-response.js`          | ✅ bounded JSONPath subset; see argument contract        |
| `trusted-json-edit-fetch-response.js`  | ✅ validated editing subset; see SCRIPTLETS.md           |
| `trusted-edit-inbound-element.js`      | ✅ validated editing subset; see SCRIPTLETS.md           |
| `json-edit-fetch-request.js`           | ✅ validated editing subset; see SCRIPTLETS.md           |
| `trusted-json-edit-fetch-request.js`   | ✅ validated editing subset; see SCRIPTLETS.md           |
| `jsonl-edit-xhr-response.js`           | ✅ bounded JSONPath subset; see argument contract        |
| `trusted-jsonl-edit-xhr-response.js`   | ✅ validated editing subset; see SCRIPTLETS.md           |
| `jsonl-edit-fetch-response.js`         | ✅ bounded JSONPath subset; see argument contract        |
| `trusted-jsonl-edit-fetch-response.js` | ✅ validated editing subset; see SCRIPTLETS.md           |
| `abort-on-stack-trace.js`              | ✅ stack                                                 |
| `trusted-prune-inbound-object.js`      | ✅ validated editing subset; see SCRIPTLETS.md           |
| `trusted-prune-outbound-object.js`     | ✅ validated editing subset; see SCRIPTLETS.md           |
| `json-prune.js`                        | ✅ json                                                  |
| `json-prune-fetch-response.js`         | ✅ bounded response pruning                              |
| `json-prune-xhr-response.js`           | ✅ bounded response pruning                              |
| `evaldata-prune.js`                    | ❌ general eval interception changes direct-eval scope   |
| `noeval-if.js`                         | ✅ eval                                                  |
| `prevent-addEventListener.js`          | ✅ listeners                                             |
| `prevent-bab.js`                       | ✅ BAB                                                   |
| `prevent-clipboard-write.js`           | ✅ requires trust; match/exclude; no alert UI            |
| `prevent-dialog.js`                    | ✅ dialog                                                |
| `prevent-fetch.js`                     | ✅ fetch                                                 |
| `trusted-prevent-fetch.js`             | ✅ trusted literal response                              |
| `freeze-element-property.js`           | ✅ freeze                                                |
| `prevent-innerHTML.js`                 | ✅ freeze                                                |
| `prevent-navigation.js`                | ⚠️ Navigation API                                        |
| `prevent-setTimeout.js`                | ✅ timers                                                |
| `prevent-setInterval.js`               | ✅ timers                                                |
| `prevent-requestAnimationFrame.js`     | ✅ animation                                             |
| `set-constant.js`                      | ✅ constants                                             |
| `trusted-set-constant.js`              | ✅ trusted bounded JSON constant                         |
| `trusted-replace-argument.js`          | ✅ trusted bounded argument/receiver replacement         |
| `spoof-css.js`                         | ✅ style                                                 |
| `prevent-xhr.js`                       | ✅ bounded native XHR emulation                          |
| `trusted-prevent-xhr.js`               | ✅ trusted literal response                              |
| `mpegdash-prune.js`                    | ✅ bounded fully timed XHR MPD Periods                   |
| `set-cookie.js`                        | ✅ cookies                                               |
| `set-cookie-reload.js`                 | ✅ cookies                                               |
| `trusted-set-cookie.js`                | ✅ trusted literal cookie                                |
| `trusted-set-cookie-reload.js`         | ✅ trusted literal cookie / reload                       |
| `remove-cookie.js`                     | ✅ cookies                                               |
| `set-local-storage-item.js`            | ✅ storage                                               |
| `set-session-storage-item.js`          | ✅ storage                                               |
| `trusted-set-local-storage-item.js`    | ✅ trusted literal storage                               |
| `trusted-set-session-storage-item.js`  | ✅ trusted literal storage                               |
| `abort-on-property-read.js`            | ✅ properties                                            |
| `abort-on-property-write.js`           | ✅ properties                                            |
| `adjust-setInterval.js`                | ✅ boosters                                              |
| `adjust-setTimeout.js`                 | ✅ boosters                                              |
| `prevent-refresh.js`                   | ✅ refresh                                               |
| `remove-class.js`                      | ✅ classes                                               |
| `webrtc-if.js`                         | ✅ webrtc                                                |
| `prevent-window-open.js`               | ✅ window-open                                           |
| `close-window.js`                      | ❌ native window adapter                                 |
| `window.name-defuser.js`               | ✅ name                                                  |
| `overlay-buster.js`                    | ✅ bounded shared observer / no polling                  |
| `alert-buster.js`                      | ✅ alert                                                 |
| `nowebrtc.js`                          | ✅ webrtc                                                |
| `disable-newtab-links.js`              | ✅ links                                                 |
| `xml-prune.js`                         | ✅ bounded CSS element pruning                           |
| `m3u-prune.js`                         | ✅ HLS EXTINF segment pruning                            |
| `call-nothrow.js`                      | ✅ calls                                                 |
| `remove-node-text.js`                  | ✅ node-text                                             |
| `prevent-canvas.js`                    | ✅ canvas                                                |
| `multiup.js`                           | ✅ multiup                                               |
| `break-on-call.js`                     | ❌ debugger                                              |
| `trusted-replace-node-text.js`         | ✅ trusted bounded node-text replacement                 |
| `trusted-replace-fetch-response.js`    | ✅ trusted bounded response text                         |
| `trusted-replace-xhr-response.js`      | ✅ trusted bounded response text                         |
| `trusted-click-element.js`             | ✅ trusted bounded CSS/shadow-root steps                 |
| `trusted-replace-outbound-text.js`     | ✅ trusted bounded returned text/base64                  |
| `trusted-suppress-native-method.js`    | ✅ trusted primitive signatures and stack predicate      |
| `trusted-prevent-dom-bypass.js`        | ✅ selected APIs in anonymous same-origin frames         |
| `trusted-override-element-method.js`   | ✅ trusted selector and constant/throw disposition       |
| `amazon_ads.js`                        | ✅ fixed API shim                                        |
| `amazon_apstag.js`                     | ↪️ bundled `$redirect`; upstream has no `+js` entry      |
| `ampproject_v0.js`                     | ↪️ bundled redirect; no equivalent inline context        |
| `adthrive_abd.js`                      | ✅ fixed API shim                                        |
| `chartbeat.js`                         | ↪️ bundled `$redirect`; upstream has no `+js` entry      |
| `doubleclick_instream_ad_status.js`    | ✅ fixed API shim                                        |
| `fingerprint2.js`                      | ✅ fixed API shim                                        |
| `fingerprint3.js`                      | ✅ fixed API shim                                        |
| `google-analytics_analytics.js`        | ✅ fixed API shim                                        |
| `google-analytics_cx_api.js`           | ↪️ bundled redirect-only resource; no `+js` eligibility  |
| `google-analytics_ga.js`               | ✅ fixed API shim                                        |
| `google-analytics_inpage_linkid.js`    | ↪️ bundled redirect-only resource; no `+js` eligibility  |
| `google-ima.js`                        | ✅ fixed media API shim; event/callback lifecycle tested |
| `google-ima-dai.js`                    | ✅ trusted fixed stream API shim; redirect and inline    |
| `googlesyndication_adsbygoogle.js`     | ✅ fixed iframe API shim; bounded subtree observer       |
| `googletagservices_gpt.js`             | ✅ fixed API shim                                        |
| `hd-main.js`                           | ↪️ bundled redirect; no equivalent inline context        |
| `nitropay_ads.js`                      | ✅ fixed API shim                                        |
| `nobab2.js`                            | ↪️ bundled redirect; no equivalent inline context        |
| `noeval.js`                            | ✅ eval-all                                              |
| `noeval-silent.js`                     | ✅ eval-all                                              |
| `nofab.js`                             | ✅ fixed API shim                                        |
| `noop.js`                              | ❌ no blocking behavior                                  |
| `outbrain-widget.js`                   | ↪️ bundled redirect; no equivalent inline context        |
| `piano-analytics.js`                   | ↪️ bundled `$redirect`; upstream has no `+js` entry      |
| `popads.js`                            | ✅ fixed API shim                                        |
| `popads-dummy.js`                      | ✅ fixed API shim                                        |
| `prebid-ads.js`                        | ✅ fixed API shim                                        |
| `scorecardresearch_beacon.js`          | ↪️ bundled redirect-only resource; no `+js` eligibility  |
| `sensors-analytics.js`                 | ✅ fixed API shim                                        |

## Upstream aliases

Aliases of supported names use the same typed instruction and exception identity.
Aliases of skipped names remain skipped. The optional `.js` suffix is normalized.

- `abort-current-script.js`: `abort-current-inline-script.js`, `acis.js`, `acs.js`
- `remove-attr.js`: `ra.js`
- `href-sanitizer.js`: `urlskip.js`
- `abort-on-stack-trace.js`: `aost.js`
- `noeval-if.js`: `prevent-eval-if.js`
- `prevent-addEventListener.js`: `addEventListener-defuser.js`, `aeld.js`
- `prevent-bab.js`: `bab-defuser.js`, `nobab.js`
- `prevent-fetch.js`: `no-fetch-if.js`
- `prevent-setTimeout.js`: `no-setTimeout-if.js`, `nostif.js`, `setTimeout-defuser.js`
- `prevent-setInterval.js`: `no-setInterval-if.js`, `nosiif.js`, `setInterval-defuser.js`
- `prevent-requestAnimationFrame.js`: `no-requestAnimationFrame-if.js`, `norafif.js`
- `set-constant.js`: `set.js`
- `trusted-set-constant.js`: `trusted-set.js`
- `prevent-xhr.js`: `no-xhr-if.js`
- `remove-cookie.js`: `cookie-remover.js`
- `abort-on-property-read.js`: `aopr.js`
- `abort-on-property-write.js`: `aopw.js`
- `adjust-setInterval.js`: `nano-setInterval-booster.js`, `nano-sib.js`
- `adjust-setTimeout.js`: `nano-setTimeout-booster.js`, `nano-stb.js`
- `prevent-refresh.js`: `refresh-defuser.js`
- `remove-class.js`: `rc.js`
- `prevent-window-open.js`: `nowoif.js`, `no-window-open-if.js`, `window.open-defuser.js`
- `close-window.js`: `window-close-if.js`
- `remove-node-text.js`: `rmnt.js`
- `trusted-replace-node-text.js`: `trusted-rpnt.js`, `replace-node-text.js`, `rpnt.js`
- `trusted-replace-fetch-response.js`: `trusted-rpfr.js`
- `amazon_ads.js`: `amazon-adsystem.com/aax2/amzn_ads.js`
- `ampproject_v0.js`: `ampproject.org/v0.js`
- `chartbeat.js`: `static.chartbeat.com/chartbeat.js`
- `doubleclick_instream_ad_status.js`: `doubleclick.net/instream/ad_status.js`
- `google-analytics_analytics.js`: `google-analytics.com/analytics.js`, `googletagmanager_gtm.js`,
  `googletagmanager.com/gtm.js`
- `google-analytics_cx_api.js`: `google-analytics.com/cx/api.js`
- `google-analytics_ga.js`: `google-analytics.com/ga.js`
- `google-analytics_inpage_linkid.js`: `google-analytics.com/inpage_linkid.js`
- `google-ima.js`: `google-ima3`
- `google-ima-dai.js`: `google-ima3-dai`
- `googlesyndication_adsbygoogle.js`: `googlesyndication.com/adsbygoogle.js`,
  `googlesyndication-adsbygoogle`
- `googletagservices_gpt.js`: `googletagservices.com/gpt.js`, `googletagservices-gpt`
- `noeval-silent.js`: `silent-noeval.js`
- `nofab.js`: `fuckadblock.js-3.2.0`
- `noop.js`: `noopjs`, `abp-resource:blank-js`
- `outbrain-widget.js`: `widgets.outbrain.com/outbrain.js`
- `popads.js`: `popads.net.js`, `prevent-popads-net.js`
- `scorecardresearch_beacon.js`: `scorecardresearch.com/beacon.js`

Source snapshot and licence references are recorded in
[the packaged notice](src/main/resources/META-INF/ublock-NOTICE.txt).
