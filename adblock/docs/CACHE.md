# Compiled filter cache

`CompiledFilterCache` uses disposable WKCF snapshots (schema 18) with source
SHA-256 identities, normalized rule records, compact rule-ID indexes and CRC32.
Network, cosmetic and scriptlet hostname/entity/exception buckets are restored
directly without duplicating rule text, selectors or arguments.

Before activation, restoration validates schema, bounds, checksum, rule IDs,
ordering and complete bucket membership. Missing or invalid indexes rebuild from
normalized records and repair the aggregate snapshot. Bad snapshots recompile
from source text. No executable JavaScript is loaded from disk; the scriptlet
dispatcher is generated from validated records.

The aggregate cache is invalidated by list content, order, trust provenance or
schema changes. Unchanged lists can reuse SHA-256-keyed source shards under
`compiled-rules.bin.sources`; merge-time badfilters and exceptions are reapplied.
The engine's optional `cacheVersion` is a compatibility salt, not an app version.

Limits: 2,000,000 combined entries, 65,536 scriptlet rules and 64 MiB per cache
file (with a 64 MiB total source-cache budget). Writes are bounded and atomic:
`.new` is flushed and promoted via a recoverable `.bak`. Cache failure does not
prevent successfully compiled rules from activating.

`CompiledFilterCacheTest`, `FilterCacheBenchmarkTest` (optional) and the source
cache tests cover corruption, recovery, restoration and invalidation.
