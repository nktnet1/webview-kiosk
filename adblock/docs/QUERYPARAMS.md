# Query parameter removal

`$removeparam` and `$queryprune` are URL modifiers, not blocking exceptions.
Supported values: a case-sensitive name, empty value (all parameters), bounded
`/regex/` or `/regex/i` against `name=decodedValue`, leading `~` inversion, and
the legacy case-insensitive `|prefix` form. Regexes use RE2/J limits; malformed
inputs and unsupported option combinations are skipped.

Matching exceptions cancel the same modifier; an empty exception cancels ordinary
removals; `$important` overrides exceptions. Surviving query parts retain their
original spelling, order, duplicates, `name=` form and fragments. URLs longer than
32,768 characters or containing more than 1,024 query parts pass through.

WebView rewrites app GET loads, modern main-frame GET callbacks and PDF source
requests (including redirects). It checks original and rewritten URLs against
blocking policy. POSTs, subresources, unsupported legacy callbacks and unseen
redirect hops are not rewritten: WebView cannot replay their requests safely or
return a redirect from `shouldInterceptRequest()`.

See `ParameterRemovalTest` and the
[upstream syntax](https://github.com/gorhill/uBlock/wiki/Static-filter-syntax#removeparam).
