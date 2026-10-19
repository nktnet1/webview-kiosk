# Compiled filter cache

`CompiledFilterCache` uses disposable WKCF snapshots (schema 19) with source
SHA-256 identities, normalized rule records, compact rule-ID indexes and CRC32.
Network, cosmetic and scriptlet hostname/entity/exception buckets are restored
directly without duplicating rule text, selectors or arguments.

Before activation, restoration validates schema, bounds, checksum, rule IDs,
ordering and complete bucket membership. Reads and writes checksum the payload
in buffered chunks, excluding the trailing CRC32 long. The single-owner reader
decodes primitives and strings directly from a 64 KiB buffer instead of making
per-field stream reads. It never loads the entire snapshot into a byte array.
String IDs still deduplicate values; new strings have a two-byte tagged character
count followed by Latin-1 bytes or raw UTF-16BE code units. The latter preserves
all Unicode, including unpaired surrogates, without lossy charset replacement.
Strings remain limited to 8,192 UTF-16 code units. A reusable character buffer is
needed only for the wide-string path.

Warm restores still validate cached indexes, so the indexing stage can include
work without rebuilding the buckets. Missing or invalid indexes rebuild from normalized
records and repair the aggregate snapshot. Bad snapshots recompile
from source text. No executable JavaScript is loaded from disk; the scriptlet
dispatcher is generated from validated records. Its public-suffix data is
normalised from the pinned OkHttp list at build time and bundled as inert JSON,
so startup does not repeat hostname canonicalisation for every suffix.

Android reports `Rule restore revision` on successful aggregate cache hits,
splitting `RESTORING` into network, cosmetic and scriptlet records, saved
network and page buckets, and checksum/footer verification. It includes record,
string and byte counts without logging rule contents. `Rule index revision`
breaks `INDEXING` into scriptlet routing, scriptlet injection generation,
cosmetic routing, network partitioning, network routing and other work. Routing
timings include validation of saved indexes; they do not imply indexes were
rebuilt.

The aggregate cache is invalidated by list content, order, trust provenance or
schema changes. Upgrading from schema 18 intentionally rebuilds the aggregate
and source snapshots once from the saved list text. No subscriptions, settings
or source files are removed, and no network download is required. Later loads
reuse schema 19; preserving old disposable files is not a performance constraint.
Unchanged lists can reuse SHA-256-keyed source shards under
`compiled-rules.bin.sources`; merge-time badfilters and exceptions are reapplied.
The engine's optional `cacheVersion` is a compatibility salt, not an app version.

Limits: 2,000,000 combined entries, 65,536 scriptlet rules and 64 MiB per cache
file (with a 64 MiB total source-cache budget). Writes are bounded and atomic:
`.new` is flushed and promoted via a recoverable `.bak`. Cache failure does not
prevent successfully compiled rules from activating.

`CacheDataInputTest` covers primitive/string decoding, short reads, buffer splits,
Unicode and bounds. `CompiledFilterCacheTest`, the opt-in cache and index
benchmarks, and the source cache tests cover corruption, recovery, restoration
and one-time schema invalidation, including invalid records with a valid CRC32.
