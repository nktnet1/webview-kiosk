# Compiled cache - v1.0.29

Production saves and directly restores network, cosmetic and scriptlet lookup
indexes. The normalized rule records remain authoritative. Restoring a valid index
reuses its primitive ID arrays instead of regrouping rules into new buckets.

## Representation and validation

Schema 18 uses a disposable WKCF binary snapshot: source SHA-256, interned strings,
normalized network/cosmetic/scriptlet records, optional indexes and trailing CRC32.
Buckets contain integer rule IDs, not serialized Kotlin objects or executable
JavaScript. Rule text, selectors, procedures and scriptlet arguments are stored once
in the existing records; bucket keys reuse the same interned string table.

Network families cover ordinary requests, cosmetic-disable exceptions, redirect
exceptions and query-parameter modifiers. Cosmetic families cover hostname/entity
selectors and exceptions, procedural selectors and exceptions, generic selectors
and exceptions, and leading class/ID selectors. Scriptlet families cover hostnames,
entities, ancestor hostnames/entities, regex candidates and generic exceptions.
Multiple scriptlet scopes sharing a bucket store their rule ID only once.

Restoration checks byte/count/reference budgets, IDs and checksum. Before accepting
an index, it also verifies that:

- The rule count matches the corresponding normalized rule family.
- IDs in each bucket are strictly increasing and in bounds.
- Every expected membership is present, including exclusions' exception rules.
- Total membership counts match, so extra keys or misplaced rules cannot activate.
- No bucket is empty and the existing network fallback safeguard still holds.

Page membership validation enumerates expected routes and searches the restored ID
arrays. It does not allocate replacement hostname/token maps. Missing or
semantically invalid indexes rebuild from normalized records and repair the
aggregate snapshot. A damaged page index does not rebuild valid network indexes.
Structurally damaged snapshots fall back to source caches and authoritative list
text. The previous active engine remains in use if building a replacement fails.

The engine still partitions normalized network records and builds plain-host sets.
It regenerates scriptlet dispatcher source, instruction pooling and bundled runtime
selection from validated records. Executable source is never persisted. Source
shards store normalized records only because merging can change their rule IDs.

## Invalidation and source reuse

Identity includes exact source text, list boundaries, ordering and trust bits.
Cached scriptlet arguments are validated again. JSONPath ASTs must match compilation
of their source query. The optional engine `cacheVersion` is an explicit compatibility
salt with a stable production default; ordinary app releases do not invalidate it.
Parsing, matching, scriptlet-contract and binary changes bump
`CompiledFilterCache.VERSION`.

Schema 18 adds cosmetic/scriptlet ID buckets and invalidates earlier parsing and
argument contracts. Schema 17 added procedural programs and editing/resource contracts.
Cosmetic procedures are recompiled from their selectors on restore, and every
editing AST is checked against its query and trust requirements. No executable
JavaScript is read from the cache. Index validation and dispatcher generation run
off the UI thread.

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

Tests verify direct reuse of restored page buckets, matching selectors, procedures,
scriptlets and dispatcher output, production persistence, warm restoration without rewriting,
normalized-only upgrades and repair of missing, duplicated, reordered and wrongly
keyed memberships even when a checksum is valid. Existing coverage checks the
network fixture corpus, bounds/truncation/checksums, trust separation, source
change/reordering/removal, badfilters, failed writes and backup recovery.

The optional `FilterCacheBenchmarkTest` remains available for future profiling.
The historical v1.0.25 host comparison measured 75.36 ms for rebuilding versus
76.16 ms for restoring primitive buckets on its 41,000-rule fixture. Indexes added
120,096 bytes (3,822,585 versus 3,942,681). These are host measurements, not Android
results, and are not a reason to disable the now-tested persistence path.
