# Upstream scriptlet catalogue

The supplied archive contains 99 parameterized scriptlets and 30 JavaScript resources:
129 canonical names, or 194 names including aliases. Its 54 helper functions are separate.
Only 21 of the 30 resources have upstream inline-injection eligibility (`data: text`).

This patch implements 56 canonical names: 55 behavior-tested supported subsets plus
`prevent-navigation`, which requires a native Navigation API. The other 73 remain skipped.
The active supplied list uses 39 names resolving to 38 canonical names; 91 canonical
names are absent. Absence is not a reason for exclusion: 27 supported names are absent.
All ✅ entries remain subject to the argument contracts in [SCRIPTLETS.md](SCRIPTLETS.md).
API availability, matching documents, and documented resource limits also apply.

✅ Executable documented subset. ⚠️ Conditional feature. ❌ Skipped with reason.

## What the skipped entries mean

Most skipped entries are **not implemented yet**. Their absence does not establish a
WebView limitation. The reasons below distinguish missing work from platform constraints.
Supported entries also reject unimplemented argument forms rather than approximating them.

| Reason | Examples | What would be needed |
| --- | --- | --- |
| Implementation | XHR prevention; Fetch/XHR pruning | Lifecycle, events, streams and tests |
| Implementation | JSON/object editing; XML, media playlists | Argument DSLs and parser contracts |
| Implementation | Eval-data pruning | Eval contracts and tests |
| Implementation gap | Media SDK and iframe shims | Audited adapters and browser behavior tests |
| Trust model | `trusted-*`, clipboard suppression | Source provenance and restricted capabilities |
| Missing native integration | `close-window` | An app-controlled window-closing adapter |
| Scope/performance | Overlay, debug/break helpers | Avoid polling and debugger behavior |
| Resource role | Redirect-only files, `nobab2` | Redirect or original script-element context |
| No blocking effect | `noop` | Not counted as executable blocking support |
| Provider capability | `prevent-navigation` | Native Navigation API |

The missing trust model is a security decision in this engine, not a general inability
of WebView to execute these operations. Ordinary downloaded lists must not gain the
arbitrary values, HTML, replacements or native privileges reserved for trusted filters.
Several redirect-only files in the archive are resources, not upstream `+js` entry points.

The concrete WebView constraint for all property/function hooks is reliable page-world
document-start injection. Providers without `DOCUMENT_START_SCRIPT` skip scriptlets.
Late evaluation cannot reliably intercept earlier page scripts. Native browser APIs,
HttpOnly cookies and cross-origin access retain their normal browser restrictions.
Fetch/XHR interception of page calls remains feasible in JavaScript; a complete extension
network/CSP/response-rewrite pipeline is not supplied by the current WebView integration.

## Canonical entries

| Canonical name | Status / contract or reason |
| --- | --- |
| `abort-current-script.js` | ✅ current-script |
| `set-attr.js` | ✅ attributes |
| `trusted-set-attr.js` | ❌ trust provenance required |
| `remove-attr.js` | ✅ attributes |
| `trusted-create-html.js` | ❌ trust provenance required |
| `href-sanitizer.js` | ✅ links |
| `proxy-apply-config.js` | ❌ configuration/debug |
| `edit-outbound-object.js` | ❌ unimplemented editing DSL |
| `trusted-edit-outbound-object.js` | ❌ trust provenance required |
| `json-edit.js` | ❌ unimplemented editing DSL |
| `trusted-json-edit.js` | ❌ trust provenance required |
| `edit-inbound-object.js` | ❌ unimplemented editing DSL |
| `trusted-edit-inbound-object.js` | ❌ trust provenance required |
| `edit-this-object.js` | ❌ unimplemented editing DSL |
| `trusted-edit-this-object.js` | ❌ trust provenance required |
| `edit-object-on-getter.js` | ❌ unimplemented editing DSL |
| `trusted-edit-object-on-getter.js` | ❌ trust provenance required |
| `edit-object-on-setter.js` | ❌ unimplemented editing DSL |
| `trusted-edit-object-on-setter.js` | ❌ trust provenance required |
| `edit-element-object.js` | ❌ unimplemented editing DSL |
| `trusted-edit-element-object.js` | ❌ trust provenance required |
| `json-edit-xhr-response.js` | ❌ unimplemented editing DSL |
| `trusted-json-edit-xhr-response.js` | ❌ trust provenance required |
| `json-edit-xhr-request.js` | ❌ unimplemented editing DSL |
| `trusted-json-edit-xhr-request.js` | ❌ trust provenance required |
| `json-edit-fetch-response.js` | ❌ unimplemented editing DSL |
| `trusted-json-edit-fetch-response.js` | ❌ trust provenance required |
| `trusted-edit-inbound-element.js` | ❌ trust provenance required |
| `json-edit-fetch-request.js` | ❌ unimplemented editing DSL |
| `trusted-json-edit-fetch-request.js` | ❌ trust provenance required |
| `jsonl-edit-xhr-response.js` | ❌ unimplemented editing DSL |
| `trusted-jsonl-edit-xhr-response.js` | ❌ trust provenance required |
| `jsonl-edit-fetch-response.js` | ❌ unimplemented editing DSL |
| `trusted-jsonl-edit-fetch-response.js` | ❌ trust provenance required |
| `abort-on-stack-trace.js` | ✅ stack |
| `trusted-prune-inbound-object.js` | ❌ trust provenance required |
| `trusted-prune-outbound-object.js` | ❌ trust provenance required |
| `json-prune.js` | ✅ json |
| `json-prune-fetch-response.js` | ❌ response lifecycle |
| `json-prune-xhr-response.js` | ❌ response lifecycle |
| `evaldata-prune.js` | ❌ unverified eval contract |
| `noeval-if.js` | ✅ eval |
| `prevent-addEventListener.js` | ✅ listeners |
| `prevent-bab.js` | ✅ BAB |
| `prevent-clipboard-write.js` | ❌ trust provenance required |
| `prevent-dialog.js` | ✅ dialog |
| `prevent-fetch.js` | ✅ fetch |
| `trusted-prevent-fetch.js` | ❌ trust provenance required |
| `freeze-element-property.js` | ✅ freeze |
| `prevent-innerHTML.js` | ✅ freeze |
| `prevent-navigation.js` | ⚠️ Navigation API |
| `prevent-setTimeout.js` | ✅ timers |
| `prevent-setInterval.js` | ✅ timers |
| `prevent-requestAnimationFrame.js` | ✅ animation |
| `set-constant.js` | ✅ constants |
| `trusted-set-constant.js` | ❌ trust provenance required |
| `trusted-replace-argument.js` | ❌ trust provenance required |
| `spoof-css.js` | ✅ style |
| `prevent-xhr.js` | ❌ XHR emulation |
| `trusted-prevent-xhr.js` | ❌ trust provenance required |
| `mpegdash-prune.js` | ❌ unverified stream/parser contract |
| `set-cookie.js` | ✅ cookies |
| `set-cookie-reload.js` | ✅ cookies |
| `trusted-set-cookie.js` | ❌ trust provenance required |
| `trusted-set-cookie-reload.js` | ❌ trust provenance required |
| `remove-cookie.js` | ✅ cookies |
| `set-local-storage-item.js` | ✅ storage |
| `set-session-storage-item.js` | ✅ storage |
| `trusted-set-local-storage-item.js` | ❌ trust provenance required |
| `trusted-set-session-storage-item.js` | ❌ trust provenance required |
| `abort-on-property-read.js` | ✅ properties |
| `abort-on-property-write.js` | ✅ properties |
| `adjust-setInterval.js` | ✅ boosters |
| `adjust-setTimeout.js` | ✅ boosters |
| `prevent-refresh.js` | ✅ refresh |
| `remove-class.js` | ✅ classes |
| `webrtc-if.js` | ✅ webrtc |
| `prevent-window-open.js` | ✅ window-open |
| `close-window.js` | ❌ native window adapter |
| `window.name-defuser.js` | ✅ name |
| `overlay-buster.js` | ❌ polling and heuristic scope |
| `alert-buster.js` | ✅ alert |
| `nowebrtc.js` | ✅ webrtc |
| `disable-newtab-links.js` | ✅ links |
| `xml-prune.js` | ❌ unverified stream/parser contract |
| `m3u-prune.js` | ❌ unverified stream/parser contract |
| `call-nothrow.js` | ✅ calls |
| `remove-node-text.js` | ✅ node-text |
| `prevent-canvas.js` | ✅ canvas |
| `multiup.js` | ✅ multiup |
| `break-on-call.js` | ❌ debugger |
| `trusted-replace-node-text.js` | ❌ trust provenance required |
| `trusted-replace-fetch-response.js` | ❌ trust provenance required |
| `trusted-replace-xhr-response.js` | ❌ trust provenance required |
| `trusted-click-element.js` | ❌ trust provenance required |
| `trusted-replace-outbound-text.js` | ❌ trust provenance required |
| `trusted-suppress-native-method.js` | ❌ trust provenance required |
| `trusted-prevent-dom-bypass.js` | ❌ trust provenance required |
| `trusted-override-element-method.js` | ❌ trust provenance required |
| `amazon_ads.js` | ✅ fixed API shim |
| `amazon_apstag.js` | ❌ redirect-only upstream resource |
| `ampproject_v0.js` | ❌ redirect-only upstream resource |
| `adthrive_abd.js` | ✅ fixed API shim |
| `chartbeat.js` | ❌ redirect-only upstream resource |
| `doubleclick_instream_ad_status.js` | ✅ fixed API shim |
| `fingerprint2.js` | ✅ fixed API shim |
| `fingerprint3.js` | ✅ fixed API shim |
| `google-analytics_analytics.js` | ✅ fixed API shim |
| `google-analytics_cx_api.js` | ❌ redirect-only upstream resource |
| `google-analytics_ga.js` | ✅ fixed API shim |
| `google-analytics_inpage_linkid.js` | ❌ redirect-only upstream resource |
| `google-ima.js` | ❌ unverified media adapter |
| `google-ima-dai.js` | ❌ trust provenance required |
| `googlesyndication_adsbygoogle.js` | ❌ unverified iframe adapter |
| `googletagservices_gpt.js` | ✅ fixed API shim |
| `hd-main.js` | ❌ redirect-only upstream resource |
| `nitropay_ads.js` | ✅ fixed API shim |
| `nobab2.js` | ❌ currentScript redirect context |
| `noeval.js` | ✅ eval-all |
| `noeval-silent.js` | ✅ eval-all |
| `nofab.js` | ✅ fixed API shim |
| `noop.js` | ❌ no blocking behavior |
| `outbrain-widget.js` | ❌ redirect-only upstream resource |
| `piano-analytics.js` | ❌ redirect-only upstream resource |
| `popads.js` | ✅ fixed API shim |
| `popads-dummy.js` | ✅ fixed API shim |
| `prebid-ads.js` | ✅ fixed API shim |
| `scorecardresearch_beacon.js` | ❌ redirect-only upstream resource |
| `sensors-analytics.js` | ✅ fixed API shim |

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
