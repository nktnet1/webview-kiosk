# Compiled cache - v1.0.20

All four opportunities are technically possible, with different tradeoffs:

| Opportunity | Result |
| --- | --- |
| Persist lookup indexes | Measured separately; indexes still rebuild. A portable bucket/ID format is feasible, but needs Android profiling to justify extra IO, size and validation. |
| Invalidate only changed lists | Implemented using per-source compiled records beneath the aggregate snapshot. |
| Avoid invalidating on every app release | Implemented. VERSION_CODE is no longer passed to the engine. |
| Recoverable replacement | Implemented using a pending file, backup, file-descriptor sync, checked renames and recovery, without adding Android dependencies to the engine. |

## Representation and invalidation

Schema 10 uses a disposable WKCF binary snapshot: source SHA-256, interned strings,
normalized network/cosmetic/scriptlet records, bounded counts and trailing CRC32.
It does not use Java object serialization. Identity includes exact input text,
list boundaries, ordering and trust bits. Cached scriptlet arguments are validated
again. The optional engine `cacheVersion` remains an explicit compatibility salt,
with a stable production default. Parsing, matching, scriptlet-contract and binary
changes require bumping `CompiledFilterCache.VERSION`; app-only releases do not.
Schema 9 added a regex discriminator and child-text cosmetic predicate fields.
Schema 10 invalidates records for document-specific navigation matching; it still
rebuilds runtime indexes. RE2/J programs are rebuilt and bounded when records are
restored. Older schemas recompile once.

The warm path reads one aggregate snapshot and constructs the indexes. On a miss,
per-source SHA-256-keyed records in `compiled-rules.bin.sources` allow only changed,
missing or damaged sources to reparse. Unchanged source files are not rewritten.
Reordering can reuse every source while rebuilding the merged rule order.

Source records retain badfilter identities and pre-deduplication counts. Final
merge applies cross-list badfilters, deduplicates all rule families, preserves
first-rule ordering and checks 500,000 total rules / 8,192 scriptlet rules.
Loading and parsing enforce the remaining combined rule budget before accepting
additional records, including source entries removed by deduplication. Removing
an overriding list restores the base source correctly. Exceptions are still global
across enabled lists. This is incremental parsing, not mutable incremental indexes.

After a successful build, only current enabled source files and their recoverable
backups are retained. Individual files and total source-cache use are each capped
at 64 MiB; the aggregate has a separate 64 MiB cap. The added disk usage trades off
against recompilation. Evicted caches recompile from authoritative source text.
Source text reading, hashing and index construction still run when necessary.

## Replacement and recovery

`RecoverableCacheFile` supplies AtomicFile-style recovery in the portable module:

1. Recover `.bak` as the previous committed snapshot, if present.
2. Write complete `.new`, flush buffered bytes, sync the file descriptor and check size.
3. Rename the base to `.bak`, then promote `.new` to the base.
4. Delete the backup only after successful promotion.

Interrupted promotion retains a recoverable previous snapshot. Failed and
oversized writes leave it intact. Reads recover before checking key, schema,
counts and checksum. Incomplete pending files are never read as committed caches.
Each source shard uses the same protocol. IO failures do not prevent successfully
built rules being published. Failed builds keep the previous immutable snapshot.

The production loader supplies one writer. The cache does not coordinate multiple
processes or provide a database transaction across shards. File bytes are synced;
there is no directory-fsync durability claim. These guarantees are appropriate for
an expendable optimization whose sources remain authoritative.

## Index rebuilding assessment

Persisting indexes could avoid rule grouping and host/token selection. It would
still allocate bucket storage and validate IDs, and could increase cache IO.
A suitable format uses interned key tables, offsets and integer rule IDs for
network, cosmetic and scriptlet buckets, separately from Kotlin runtime objects.
It needs bounded decoding, equivalence tests and a schema bump.

`FilterCacheBenchmarkTest` measures parsing, binary restoration and index/injection
construction independently. The deterministic fixture has 20,000 plain hosts,
10,000 conditional network rules, 10,000 cosmetics and 1,000 scriptlets. Each run
reports medians of seven rounds after three warmups on JBR 21.0.11.

The completed v1.0.16 host run produced these medians (historical measurements):

| Stage | Host median, ms |
| --- | ---: |
| Parse/normalize | 154.67 |
| Read/validate normalized binary | 72.99 |
| Build indexes and injection | 25.35 |

The compiled record file was 3,758,584 bytes. This run spent more time restoring
records than constructing indexes. Android IO/allocation profiling should guide
whether persisting buckets improves startup. These measurements exclude reading
and hashing original source files, Android scheduling/storage, WebView injection,
rendering and page loading. They are not phone timings or a measured user speedup.

## Verification

Tests cover source changes/reorder/removal without rewriting unchanged caches,
cross-source badfilter and scriptlet exceptions, corrupt shard repair, trust-key
separation, old/truncated/corrupt aggregate files, interrupted replacement, backup
recovery without a base, explicit write errors and size limits. Existing network
fixture and cosmetic/scriptlet round-trip coverage remains active.
