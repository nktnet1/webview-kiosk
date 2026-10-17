# Compiled cache - v1.0.25

All four opportunities are technically possible, with different tradeoffs:

| Opportunity | Result |
| --- | --- |
| Persist lookup indexes | Implemented as a bounded network-bucket prototype. Production keeps rebuilding because host comparisons do not show a consistent gain; Android profiling is still needed. |
| Invalidate only changed lists | Implemented using per-source compiled records beneath the aggregate snapshot. |
| Avoid invalidating on every app release | Implemented. VERSION_CODE is no longer passed to the engine. |
| Recoverable replacement | Implemented using a pending file, backup, file-descriptor sync, checked renames and recovery, without adding Android dependencies to the engine. |

## Representation and invalidation

Schema 15 uses a disposable WKCF binary snapshot: source SHA-256, interned strings,
normalized network/cosmetic/scriptlet records, bounded counts and trailing CRC32.
It does not use Java object serialization. Identity includes exact input text,
list boundaries, ordering and trust bits. Cached scriptlet arguments are validated
again. The optional engine `cacheVersion` remains an explicit compatibility salt,
with a stable production default. Parsing, matching, scriptlet-contract and binary
changes require bumping `CompiledFilterCache.VERSION`; app-only releases do not.
Schema 9 added a regex discriminator and child-text cosmetic predicate fields.
Schema 10 invalidates records for document-specific navigation matching; it still
rebuilds runtime indexes. Schema 11 adds query-modifier identities, including
exceptions and badfilters, and invalidates records for modifier delimiter parsing.
RE2/J programs for network patterns and query modifiers are rebuilt and bounded
when records are restored. Schema 12 adds scriptlet entity scopes and canonical
cookie flags; older schemas recompile once. The binary layout remains normalized
rules, with runtime indexes rebuilt on load. Schema 13 invalidates old trusted-cookie
argument contracts and permits their bounded ten-argument representation.
Schema 14 recompiles source shards because incomplete hostname prefixes ending in
hyphens are now valid. The scriptlet-rule limit rises from 8,192 to 16,384:
the supplied uBlock Ads list with includes has 8,812 accepted scriptlet rules.
Schema 15 adds extended scriptlet targets, partial `$all` identity, revalidated
JSONPath AST arguments, and an optional portable network-index section. It raises
capacity safeguards to 2,000,000 combined entries, 65,536 scriptlet rules,
1024 instructions per frame, and 16 MiB routing data. Older schemas recompile once.

The warm path reads one aggregate snapshot and constructs the indexes. On a miss,
per-source SHA-256-keyed records in `compiled-rules.bin.sources` allow only changed,
missing or damaged sources to reparse. Unchanged source files are not rewritten.
Reordering can reuse every source while rebuilding the merged rule order.

Source records retain badfilter identities and pre-deduplication counts. Final
merge applies cross-list badfilters, deduplicates all rule families, preserves
first-rule ordering and checks 2,000,000 total rules / 65,536 scriptlet rules.
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

The schema-15 prototype persists the four network indexes as interned bucket keys
and primitive integer rule IDs. Reads bound counts, validate IDs and checksums,
and restore buckets without reparsing filter text. Request matching retains its
host/page/entity/token/fallback algorithm. No executable source or serialized Kotlin
objects are stored. Normalized records and source shards remain authoritative.

`CompiledFilterCache.write` can include an explicit `NetworkIndexSnapshot` for
experiments. The app does not pass one: current comparisons do not establish a
consistent total-load saving. Plain-host sets, cosmetics and scriptlet routes still
rebuild; this is not a claim of complete persisted-index coverage. Retained bucket
IDs use primitive arrays to avoid allocating boxed integers for active indexes.

Enable persisted indexes only after Android measurements show lower combined
read/validation/index time at both median and P95, with no request-lookup regression.
Measure cache bytes, peak allocations and fresh-list rebuild costs as well. Test
invalid IDs/offsets, truncated buckets, checksums, trust separation and fallback to
rebuilding. Larger files and rehydrated map allocations can offset the work saved;
host-JVM timings alone do not establish an Android benefit. This optimization needs
no new WebView interception, DOM work or per-request diagnostics.

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

A v1.0.23 rerun of the same host fixture measured 105.49 ms parse/normalize,
47.00 ms read/validate, and 12.00 ms index/injection construction, with a
3,788,584-byte cache. It includes subsequent rule-format changes and a different
host run, so it is not a controlled speed comparison with v1.0.16. It confirms
that index persistence must be evaluated against total restore time, not only
against the avoided grouping work. No Android speedup has been measured here.

## Verification

Tests cover source changes/reorder/removal without rewriting unchanged caches,
cross-source badfilter and scriptlet exceptions, corrupt shard repair, trust-key
separation, old/truncated/corrupt aggregate files, interrupted replacement, backup
recovery without a base, explicit write errors and size limits. Existing network
fixture and cosmetic/scriptlet round-trip coverage remains active.

## v1.0.25 prototype comparison

`compareWholeWarmLoadsWithPersistedNetworkBucketsAndRebuiltBuckets` compares both
complete paths using the same 41,000-rule fixture. It alternates execution order,
warms up five times, and reports fifteen timed rounds. It measures binary read,
validation, and index/injection construction together. Measurements omit source
file reading/hashing, Android storage/scheduling, and WebView startup.

The first boxed-ID prototype measured 62.73 ms rebuilding versus 68.92 ms restoring
buckets, with 3,792,585 versus 3,912,681 bytes. The primitive-ID revision removes
retained boxing. In an isolated JBR 21 run, the primitive-ID revision measured
75.36 ms rebuilding versus 76.16 ms restoring, with 3,822,585 versus 3,942,681 bytes.
That is no consistent warm-load improvement and adds 120,096 bytes. Production
persistence remains disabled; Android median/P95 and allocation profiling is still
required before activation.
