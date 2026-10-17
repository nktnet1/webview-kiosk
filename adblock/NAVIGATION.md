# Document filtering - v1.0.21

Main-frame HTTP(S) navigations now reach the document matcher. `$document` and
its `$doc` alias support hostname/path rules, exceptions, methods, domain scopes,
and `$important`. Root requests use their destination as the first-party context,
including initial loads and redirects; the previously displayed page is not the
new document's source domain. Existing resource-type restrictions remain active.

Typeless network blocks also apply when the matched span ends at the hostname,
optionally including its separator or trailing DNS dot. This follows uBlock
Origin's strict-blocking distinction: an incidental `/ads/` path match cannot deny
a whole page. Such paths need an explicit `$document` option. Generic exceptions
can still match paths; important blocks override both kinds of exception.
Negated resource types alone do not create typeless document rules. Redirect-only
rules do not block documents, and document blocks never return script/image
surrogates. Matching reuses the existing hostname/token indexes and work budget.

The WebView integration covers app `loadUrl` overloads and `postUrl`, native
navigation callbacks, and main-frame request interception. Interception uses the
actual request method, including form POSTs and reloads. Allowed requests retain
their headers/body and WebView loading path. The legacy URL-only callback defers
document decisions to interception, which supplies main-frame metadata.
Internal local-file/PDF assets and non-HTTP(S) schemes remain outside network-filter
matching. PDF source requests
apply the same document policy before fetching and at every redirect target in
the existing PDF fetcher, including authenticated retries.

Native navigation callbacks use the configured block action. App loads display
the existing custom/default block page with a content-filter cause. Intercepted
documents return that HTML as HTTP 403 with `Cache-Control: no-store` and
`X-Content-Type-Options: nosniff`; interception cannot retain the previous page
after a native POST has begun. Subresource response behavior is unchanged.

Cache schema 10 invalidated old records once for the matching/alias changes.
It retains aggregate/per-list caching, checksums and recovery; runtime indexes
are still rebuilt. Schema 11 adds `$removeparam` records; see
[query removal](QUERYPARAMS.md). Persisted runtime indexes remain unimplemented.

## Verification and platform boundary

`DocumentFilteringTest` covers strict hostname boundaries, path/type separation,
exceptions and priority in both list orders, IDN/IP/credentials/ports, FQDN,
methods/first-party context, redirect-only rules, matching limits, cross-list
badfilter, warm restoration and schema-9 invalidation. App regression tests drive
the real mounted WebView's load/navigation/interception paths. PDF tests use a
local HTTP server to verify that blocked sources/redirects receive no request and
that document exceptions permit fetching.

Thirty-six navigation/priority cases were checked against the supplied uBO core
using its unmodified `shouldStrictBlock` and `validateStrictBlock` functions.
This is targeted behavioral verification, not a claim of complete uBO parity.
Robolectric cannot establish behavior on every Android WebView provider; device
checks remain necessary for real form submissions, provider redirect callbacks,
reload/history behavior and block-page rendering. No Android device was available.

Sources:

- [uBO strict blocking and validation](https://github.com/gorhill/uBlock/blob/dfca51bea2b8605353547f605e9cffbb6dfa0789/src/js/traffic.js)
- [uBO request/type/exception matching](https://github.com/gorhill/uBlock/blob/dfca51bea2b8605353547f605e9cffbb6dfa0789/src/js/static-net-filtering.js)
- [Android WebViewClient callbacks](https://developer.android.com/reference/android/webkit/WebViewClient)

Android does not call `shouldOverrideUrlLoading` for app `loadUrl` calls or POSTs,
which is why both explicit app guards and request interception are needed.
Interception only exposes the initial URL of a WebView redirect chain. Navigation
callbacks can inspect redirect targets that WebView reports, but this patch does
not promise complete native redirect-chain interception or filter same-document
SPA history changes as network navigations. The PDF fetcher's owned redirect loop
can check every hop.
