# Compiled filter cache

`CompiledFilterCache` uses disposable WKCF snapshots (schema 18) with source
SHA-256 identities, normalized rule records, compact rule-ID indexes and CRC32.
Network, cosmetic and scriptlet hostname/entity/exception buckets are restored
directly without duplicating rule text, selectors or arguments.

Before activation, restoration validates schema, bounds, checksum, rule IDs,
ordering and complete bucket membership. Reads and writes checksum the payload
in buffered chunks, excluding the trailing CRC32 long. Warm restores still
validate cached indexes, so the indexing stage can include work without
rebuilding the buckets. Missing or invalid indexes rebuild from normalized
records and repair the aggregate snapshot. Bad snapshots recompile
from source text. No executable JavaScript is loaded from disk; the scriptlet
dispatcher is generated from validated records. Its public-suffix data is
normalised from the pinned OkHttp list at build time and bundled as inert JSON,
so startup does not repeat hostname canonicalisation for every suffix.

Android reports a second `Rule index revision` log after each load. It breaks
`INDEXING` into scriptlet routing, scriptlet injection generation, cosmetic
routing, network partitioning, network routing and other work. Routing timings
include validation of saved indexes; they do not imply indexes were rebuilt.

The aggregate cache is invalidated by list content, order, trust provenance or
schema changes. Unchanged lists can reuse SHA-256-keyed source shards under
`compiled-rules.bin.sources`; merge-time badfilters and exceptions are reapplied.
The engine's optional `cacheVersion` is a compatibility salt, not an app version.

Limits: 2,000,000 combined entries, 65,536 scriptlet rules and 64 MiB per cache
file (with a 64 MiB total source-cache budget). Writes are bounded and atomic:
`.new` is flushed and promoted via a recoverable `.bak`. Cache failure does not
prevent successfully compiled rules from activating.

`CompiledFilterCacheTest`, the opt-in cache and index benchmarks, and the source
cache tests cover corruption, recovery, restoration and invalidation.
