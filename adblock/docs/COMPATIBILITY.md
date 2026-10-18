# Filter compatibility

The `adblock` module implements a bounded subset of uBlock Origin, AdGuard and
EasyList syntax for Android System WebView. Unsupported rules are skipped rather
than approximated. This is not a browser-extension engine.

## Supported

- Network blocking, exceptions, priorities, resource types, methods, domain
  restrictions, `$badfilter`, `$document`, redirects and `$removeparam`.
- Cosmetic selectors, exceptions, media queries and targeted procedural rules:
  text/attribute/style predicates, relative XPath, open shadow roots and template
  content. Native CSS stays in stylesheets; procedural rules use scoped observers.
- Hostname, entity, ancestor and constrained regex/path scriptlet scopes. Scriptlet
  names and arguments use validated subsets; see [scriptlets](SCRIPTLETS.md).
- Native request context from `Origin`, `Referer` and reliable fetch metadata.
  Unknown initiators get only context-independent rules. WebSocket and beacon
  hooks use the calling window's origin when available.
- Compiled network, cosmetic and scriptlet indexes; see [cache](CACHE.md).

## Not supported

- `$popup`/`$popunder` and `close-window`: there is no native multi-window
  opener/close lifecycle.
- Complete `$all`, CSP/header modification, HTML response filtering (`##^`),
  and `click2load.html`: WebView does not expose a general response-rewriting or
  frame-unblocking pipeline.
- Closed shadow roots, arbitrary DOM property evaluation, template-content
  hide/style rules, or continuous DOM polling.
- Arbitrary scriptlet JavaScript, prototype/accessor traversal, and unrestricted
  JSONPath expressions or argument forms.

## WebView boundaries

`shouldInterceptRequest()` cannot reliably identify every frame or service-worker
initiator, read an upstream response's headers/body, or replay POST bodies. Unknown
context is never filled in from the current top-level page. Page-world WebSocket
and `sendBeacon` hooks do not run in workers or substitute for native interception;
they are installed only with JavaScript enabled and compiled active filter lists.
Providers without `DOCUMENT_START_SCRIPT` skip scriptlets and page API hooks.

See [navigation](NAVIGATION.md) and [query removal](QUERYPARAMS.md) for the
native request-path restrictions. Behavior is covered by engine, Robolectric and
Chromium tests; physical WebView providers can still differ.
