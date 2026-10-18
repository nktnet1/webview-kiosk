# Scriptlet catalogue

The pinned uBlock assets include 99 parameterized scriptlets and 30 JavaScript
resources (129 canonical names). This engine handles 114 canonical names in
validated subsets; `prevent-navigation.js` additionally requires the Navigation
API. Normalized aliases, such as `set.js`, `aopr.js`, `aeld.js` and `no-xhr-if.js`,
share their canonical contracts. See [arguments and trust](SCRIPTLETS.md).

- Property/API: `abort-current-script`, `abort-on-property-read`,
  `set-constant`, `trusted-set-constant`, `freeze-element-property`.
- Network APIs: `prevent-fetch`, `prevent-xhr`,
  `trusted-replace-fetch-response`, `trusted-replace-xhr-response`.
- Data editing: `json-edit`, `json-prune`, `jsonl-edit-fetch-response`,
  `edit-inbound-object`, `trusted-json-edit`.
- DOM: `remove-attr`, `remove-class`, `href-sanitizer`, `overlay-buster`,
  `trusted-click-element`.
- Browser state: `set-cookie`, `trusted-set-cookie`, `remove-cookie`,
  `set-local-storage-item`.
- Timers/media: `prevent-setTimeout`, `adjust-setInterval`, `webrtc-if`,
  `mpegdash-prune`, `m3u-prune`.
- Shims: `google-ima`, `google-ima-dai`, `googletagservices_gpt`,
  `popads`, `fingerprint2`.

**Excluded as scriptlets:** `proxy-apply-config` (debug/config), `break-on-call`
(debugger), `evaldata-prune` (direct-eval semantics), `close-window` (no native
window lifecycle) and `noop.js` (no blocking behavior). Other unhandled canonical
entries are bundled redirect-only resources, not `+js` entry points; examples:
`ampproject_v0.js`, `outbrain-widget.js`, `chartbeat.js` and `piano-analytics.js`.

Availability also depends on provider features, list trust and validated argument
forms. A skipped name may be a deliberate exclusion rather than a WebView limit.
