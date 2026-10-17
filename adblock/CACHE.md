# Compiled cache - v1.0.26

Production now saves and restores the four native network lookup indexes. The
v1.0.25 prototype already supplied the portable format; this patch enables it and
validates bucket membership before use. Activation does not depend on a benchmark.
It avoids rebuilding those buckets without adding work inside WebView. No Android
startup speedup is claimed.

## Representation and validation

Schema 16 uses a disposable WKCF binary snapshot: source SHA-256, interned strings,
normalized network/cosmetic/scriptlet records, optional network buckets and trailing
CRC32. Buckets contain primitive integer rule IDs, not serialized Kotlin objects or
executable JavaScript. The four families are ordinary requests, cosmetic-disable
exceptions, redirect exceptions and query-parameter modifiers.

Restoration checks byte/count/reference budgets, IDs and checksum. Before accepting
a network index, it also verifies that:

- The rule count matches the corresponding normalized rule family.
- IDs in each bucket are strictly increasing, in bounds and correctly grouped.
- Every rule has exactly its required host/page/entity/token/fallback memberships.
- No bucket is empty and the fallback-pattern safeguard still holds.

Missing or semantically invalid buckets rebuild from normalized records and the
aggregate snapshot is repaired. This includes older normalized-only snapshots of
the current schema. Structurally damaged snapshots fall back to the source caches
and authoritative list text. The previous active immutable engine remains in use
if building a replacement fails.

The engine still partitions normalized network records and builds plain-host sets,
cosmetic indexes and scriptlet routing. This is persisted **network** indexing,
not a claim of complete runtime-state persistence. Source shards deliberately store
normalized records only, since their merged rule order can change.

## Invalidation and source reuse

Identity includes exact source text, list boundaries, ordering and trust bits.
Cached scriptlet arguments are validated again. JSONPath ASTs must match compilation
of their source query. The optional engine `cacheVersion` is an explicit compatibility
salt with a stable production default; ordinary app releases do not invalidate it.
Parsing, matching, scriptlet-contract and binary changes bump
`CompiledFilterCache.VERSION`.

Schema 15 introduced extended scriptlet scopes, partial `$all` identities, JSONPath
ASTs and optional network indexes. Schema 16 invalidates previous argument contracts
and adds native media-query cosmetics. Earlier schemas recompile once.

On an aggregate miss, SHA-256-keyed files in `compiled-rules.bin.sources` reuse
unchanged lists. Source records retain badfilters and pre-deduplication counts.
Final merging applies cross-list badfilters and exceptions, preserves first-rule
order, deduplicates and enforces the combined input budget. Reordering, changing or
removing one list cannot activate stale merged indexes.

Safeguards allow 2,000,000 combined entries and 65,536 scriptlet rules. Each cache
file and total source-cache use are capped at 64 MiB; the aggregate has its own
64 MiB cap. Writes now enforce their byte limit while streaming, before excessive
data reaches disk. Failed or oversized cache writes do not prevent activation of
successfully built rules. Evicted or oversized caches rebuild from source data.
Only enabled sources and their recoverable backups are retained.

## Replacement and recovery

`RecoverableCacheFile` supplies AtomicFile-style recovery in the portable module:

1. Recover `.bak` as the previous committed snapshot, if present.
2. Write bounded `.new` bytes, flush and sync the file descriptor.
3. Rename the base to `.bak`, then promote `.new` to the base.
4. Delete the backup only after successful promotion.

Interrupted promotion retains a recoverable previous snapshot. Pending files are
never read as committed data. Each source shard uses the same protocol. Production
has one writer; there is no multi-process transaction or directory-fsync claim.

## Verification and measurements

Tests verify production index persistence, warm restoration without rewriting,
normalized-only upgrades and repair of missing, duplicated, reordered and wrongly
keyed memberships even when a checksum is valid. Existing coverage checks the
network fixture corpus, bounds/truncation/checksums, trust separation, source
change/reordering/removal, badfilters, failed writes and backup recovery.

The optional `FilterCacheBenchmarkTest` remains available for future profiling.
The historical v1.0.25 host comparison measured 75.36 ms for rebuilding versus
76.16 ms for restoring primitive buckets on its 41,000-rule fixture. Indexes added
120,096 bytes (3,822,585 versus 3,942,681). These are host measurements, not Android
results, and are not a reason to disable the now-tested persistence path.
