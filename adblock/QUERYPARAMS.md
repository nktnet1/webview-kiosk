# Query parameter removal - v1.0.21

`$removeparam` and its legacy `$queryprune` alias are modifiers, separate from
network blocking and exception indexes. They remove matching query parameters
without blocking a request or granting a network exception. Supported values:

- A case-sensitive literal parameter name, matched against its raw spelling.
- An empty value to remove all parameters.
- A bounded `/regex/` or `/regex/i` tested against `name=decodedValue`.
- A leading `~` to invert the name/regex test.
- The legacy `|prefix` case-insensitive regex form.

Regex values use percent decoding with literal `+`, matching `decodeURIComponent`.
Malformed percent escapes or UTF-8 retain the raw value. Escaped commas in modifier
regexes and `$` regex anchors do not split the filter's options. `_` placeholders
are accepted. Unsupported regex constructs, unknown modifiers, duplicate removal
options and combinations with another modifying action discard the whole rule.
Regex compilation and request matching retain the existing RE2/J/work bounds.
Inputs over 32,768 characters or 1,024 query parts are left unchanged.

Exceptions cancel the identical modifier value. An empty exception cancels all
ordinary removals. Important removals override both forms of exception. These
decisions are independent of list order. Conditions and cross-list badfilter use
the existing normalized identities. Positive literal names supply index tokens
for otherwise global filters, keeping those rules out of the fallback bucket.

The output retains the exact bytes, ordering, duplicate keys and empty-value
spelling of surviving query parts, plus the fragment. This deliberately avoids
uBO's incidental normalization of `name=` to `name` during reconstruction.
Fragment-only text is never treated as a query.

## WebView execution

App GET loads, both `loadUrl` overloads, modern main-frame navigation callbacks
and the PDF source fetcher apply removal. App loads clean the URL before PDF
routing. Native GET rewrites retain supplied request headers. The PDF fetcher
checks its source and every redirect before connecting, including retries.
Original URL policy and document blocks are evaluated before modification;
the cleaned URL is checked again. Disabled blocking leaves the URL untouched.

POSTs and other methods are not rewritten in WebView, where request bodies are
unavailable for replay. URL-only legacy callbacks lack frame/method context, so
they do not rewrite. Request interception still enforces document blocks but
cannot issue a redirect response: Android rejects 3xx `WebResourceResponse`s.
Subresources, native navigations that bypass modern callbacks, and redirect hops
not reported by WebView are not cleaned. No replacement networking stack or
JavaScript navigation shim is used. The engine API can evaluate modifier
conditions for other types/methods; parser acceptance does not imply those
requests are rewritten by the app.

## Verification

`ParameterRemovalTest` exercises removal, encoded and repeated values, regex
delimiters, priority, conditions, limits, badfilters, warm/per-list restoration
and schema-10 invalidation. Its 36 golden cases come from the supplied uBO core
at `dfca51bea2b8605353547f605e9cffbb6dfa0789`, using `filterQuery` with a real
`FilteringContext` and `matchAndFetchModifiers`. They are checked in both list
orders. Separate tests cover retained empty-value spelling and fragment-only
input, which differ from directly invoking uBO on non-network URL strings.
Robolectric covers the mounted WebView paths, headers, method/frame restrictions,
global disable and original URL policy. Local HTTP server tests verify the PDF
requests reaching the server and refusal before connecting to blocked targets.
Android device/provider verification remains outstanding.

Sources:

- [uBO query matching and priority](https://github.com/gorhill/uBlock/blob/dfca51bea2b8605353547f605e9cffbb6dfa0789/src/js/static-net-filtering.js)
- [uBO modifier value parsing](https://github.com/gorhill/uBlock/blob/dfca51bea2b8605353547f605e9cffbb6dfa0789/src/js/static-filtering-parser.js)
- [uBO filter syntax](https://github.com/gorhill/uBlock/wiki/Static-filter-syntax#removeparam)
- [Android navigation callbacks](https://developer.android.com/reference/android/webkit/WebViewClient#shouldOverrideUrlLoading(android.webkit.WebView,android.webkit.WebResourceRequest))
- [Android response constraints](https://developer.android.com/reference/android/webkit/WebResourceResponse#WebResourceResponse(java.lang.String,java.lang.String,int,java.lang.String,java.util.Map,java.io.InputStream))
