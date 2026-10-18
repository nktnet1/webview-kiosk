# Scriptlets

`##+js(...)` rules compile to typed instructions using bundled implementations.
The [catalogue](SCRIPTLET-CATALOG.md) groups supported families and exclusions.
A known scriptlet name does not imply that all upstream arguments are accepted.
Invalid or unsafe arguments skip the whole instruction.

## Execution

- Requires `DOCUMENT_START_SCRIPT` and a matching HTTP(S) frame origin. Inherited
  HTTP(S) origins in `about:blank`, `srcdoc` or blob frames can match host rules;
  opaque origins cannot. Host, entity, ancestor, regex/path scopes and exceptions
  are resolved before dispatch. No late-injection fallback is used.
- Hooks run only in matching frames. Request-policy hooks additionally require
  enabled JavaScript and an active compiled filter list. Workers are not injected.
- Scriptlets can wrap page APIs, but cannot bypass browser security, HttpOnly
  cookies, CSP, native permissions or cross-origin restrictions.

## Arguments and trust

- Supported JSONPath includes property/index/wildcard selection, recursive keys,
  bounded regexes, comparisons, existence filters, guards/cardinalities, array
  slices (including negative steps), logical `&&`/`||`, grouping and nested filters.
  Slices and logical expressions extend uBO's documented
  [JSONPath subset](https://github.com/gorhill/uBlock/wiki/uBO-flavored-JSONPath).
- JSON edit/prune operations work on bounded data ASTs and own data descriptors.
  They do not execute expressions, consult prototypes or invoke page getters.
  Mutation runs after selection. Existing Request streams, binary and form bodies
  are not rewritten; explicit string request bodies may be edited.
- Trusted editors additionally accept validated JSON assignment/merge/replacement
  and fixed method calls; trusted constants accept `runAt`/`as` options. Trusted
  storage/cookies support documented fixed date tokens and bounded literal values.
  Function source, arbitrary JavaScript and unsafe method names are excluded.
- Only successful downloads of approved uBlock presets grant trusted provenance.
  Custom imports, list names, URLs and directives cannot grant trust. Cached
  trusted instructions are validated again. Untrusted exceptions may disable them.

## Limits

Typical limits: 1,024 instructions per frame, 65,536 per compilation, 64 levels of
JSON traversal and 4,096 selected JSON edits. Response text is limited to 1 MiB
and 4 MiB work per body; Fetch inspection has a two-second deadline. DOM rules
use scoped observers and bounded batches, without polling. When parsing or
resource limits fail, the original page operation is retained where possible.

Bundled upstream versions and licence notices are tracked in
[`ublock-NOTICE.txt`](../src/main/resources/META-INF/ublock-NOTICE.txt).
