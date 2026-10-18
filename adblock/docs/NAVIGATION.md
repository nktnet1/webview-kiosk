# Document filtering

Main-frame HTTP(S) loads support `$document` / `$doc`, exceptions, `$important`,
methods and domain restrictions. Typeless network rules also strictly block a
document when the match ends at its hostname boundary; an incidental path match
does not. Redirect-only rules do not block documents.

App `loadUrl`/`postUrl`, native navigation and main-frame interception check
document rules. A root navigation uses its destination as first-party context,
not the previously displayed page. Native interception uses its actual HTTP method.
Allowed loads retain WebView's request path, body and headers. A blocked document
uses the configured block page; interception returns it as a no-store HTTP 403.

The PDF source fetcher applies document policy before each request and redirect,
including authenticated retries. Local assets and non-HTTP(S) schemes are outside
network-filter matching. Legacy URL-only callbacks defer decisions to interception.

See [query removal](QUERYPARAMS.md) for GET URL rewriting and its limitations.
