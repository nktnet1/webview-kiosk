package uk.nktnet.webviewkiosk.adblock

import java.io.File
import kotlin.system.measureNanoTime
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import uk.nktnet.webviewkiosk.adblock.engine.CompiledFilterCache
import uk.nktnet.webviewkiosk.adblock.engine.CompiledRuleSet
import uk.nktnet.webviewkiosk.adblock.engine.CosmeticRouting
import uk.nktnet.webviewkiosk.adblock.engine.FilterCompiler
import uk.nktnet.webviewkiosk.adblock.engine.NetworkRouting
import uk.nktnet.webviewkiosk.adblock.engine.ScriptletRouting

/** Synthetic default-list-scale benchmark, opt-in and not a device startup test. */
class DefaultListRestoreBenchmarkTest {
    @get:Rule val folder = TemporaryFolder()

    private fun sampleLists(): List<String> =
        listOf(
            (0 until 70_000).joinToString("\n") { "||ads$it.example^" },
            (0 until 40_000).joinToString("\n") {
                $$"||cdn$$it.example/advert$script,domain=site$$it.example"
            },
            (0 until 70_000).joinToString("\n") { "site$it.example##.advert" },
            (0 until 1_000).joinToString("\n") {
                "site$it.example##+js(json-prune-fetch-response, ads)"
            },
        )

    @Test
    fun compareNormalizedNetworkAndPageIndexRestoration() {
        assumeTrue(System.getenv("FILTER_CACHE_BENCHMARK") == "1")
        val lists = sampleLists()
        val rules = FilterCompiler.parse(lists)
        val indexed = rules.build()
        val key = CompiledFilterCache(File(folder.root, "normalized.bin")).key(lists)
        for (mode in listOf("normalized", "network", "page")) {
            val file = File(folder.root, "$mode.bin")
            val cache = CompiledFilterCache(file)
            cache.write(
                key,
                rules,
                if (mode == "normalized") {
                    null
                } else {
                    indexed.networkIndexes
                },
                if (mode == "page") {
                    indexed.pageIndexes
                } else {
                    null
                },
            )
            repeat(2) { run ->
                var restored: CompiledRuleSet? = null
                val readNs = measureNanoTime {
                    restored = requireNotNull(cache.read(key))
                }
                val buildNs = measureNanoTime {
                    val snapshot = requireNotNull(restored).build()
                    assertTrue(mode != "page" || snapshot.restoredPageIndexes)
                    assertTrue(
                        mode == "normalized" || snapshot.restoredNetworkIndexes
                    )
                }
                println(
                    "DEFAULT_LIST_RESTORE mode=$mode run=$run " +
                        "rules=181000 bytes=${file.length()} " +
                        "read_ms=${readNs / 1_000_000.0} " +
                        "build_ms=${buildNs / 1_000_000.0}"
                )
            }
        }
    }

    @Test
    fun profileAggregateWarmCacheRestoration() {
        assumeTrue(System.getenv("FILTER_CACHE_BENCHMARK") == "1")
        val lists = sampleLists()
        val cache = CompiledFilterCache(File(folder.root, "warm.bin"))
        val key = cache.key(lists)
        val rules = FilterCompiler.parse(lists)
        val indexed = rules.build()
        cache.write(key, rules, indexed.networkIndexes, indexed.pageIndexes)
        repeat(7) { round ->
            var restored: CompiledRuleSet? = null
            var phases = ""
            val readNs = measureNanoTime {
                restored = cache.read(key, onTimings = { phases = it })
            }
            val buildNs = measureNanoTime {
                val snapshot = requireNotNull(restored).build()
                assertTrue(snapshot.restoredNetworkIndexes)
                assertTrue(snapshot.restoredPageIndexes)
            }
            println(
                "WARM_CACHE_RESTORE round=$round " +
                    "read_ms=${readNs / 1_000_000.0} " +
                    "build_ms=${buildNs / 1_000_000.0} $phases"
            )
        }
    }

    @Test
    fun profilePersistedIndexValidation() {
        assumeTrue(System.getenv("FILTER_CACHE_BENCHMARK") == "1")
        val lists =
            listOf(
                (0 until 70_000).joinToString("\n") {
                    $$"||ads$$it.example/track$script,domain=site$$it.example"
                },
                (0 until 40_000).joinToString("\n") {
                    $$"||cdn$$it.example/banner$image"
                },
                (0 until 70_000).joinToString("\n") { "site$it.example##.advert" },
                (0 until 1_000).joinToString("\n") {
                    "site$it.example##+js(json-prune-fetch-response, ads)"
                },
            )
        val rules = FilterCompiler.parse(lists)
        val networks = rules.networks.toList()
        val cosmetics = rules.cosmetics.toList()
        val network = NetworkRouting.build(networks)
        val cosmetic = CosmeticRouting.build(cosmetics)
        val scriptlet = ScriptletRouting.build(rules.scriptlets)
        repeat(5) { round ->
            var valid = false
            val net = measureNanoTime { valid = network.validFor(networks) }
            assertTrue(valid)
            val cos = measureNanoTime {
                valid = CosmeticRouting.validFor(cosmetic, cosmetics)
            }
            assertTrue(valid)
            val scr = measureNanoTime {
                valid = ScriptletRouting.validFor(scriptlet, rules.scriptlets)
            }
            assertTrue(valid)
            println(
                "INDEX_VALIDATION round=$round networks=${networks.size} " +
                    "cosmetics=${cosmetics.size} " +
                    "network_ms=${net / 1_000_000.0} " +
                    "cosmetic_ms=${cos / 1_000_000.0} " +
                    "scriptlet_ms=${scr / 1_000_000.0}"
            )
        }
    }

    @Test
    fun profileScriptletInjectionGeneration() {
        assumeTrue(System.getenv("FILTER_CACHE_BENCHMARK") == "1")
        val rules =
            FilterCompiler.parse(
                listOf(
                    (0 until 10_000).joinToString("\n") {
                        "site$it.example##+js(set, marker$it, false)"
                    } + "\npublisher.*##+js(set, entityThing, false)"
                )
            )
        val first = rules.build().scriptletInjection
        requireNotNull(first)
        repeat(4) { round ->
            val startedAt = System.nanoTime()
            val restored = rules.build().scriptletInjection
            val elapsed = System.nanoTime() - startedAt
            assertTrue(first == restored)
            println(
                "SCRIPTLET_GENERATION round=$round rules=${rules.scriptlets.size} " +
                    "chars=${first.source.length} ms=${elapsed / 1_000_000.0}"
            )
        }
    }

    @Test
    fun profileAggregateCacheWrites() {
        assumeTrue(System.getenv("FILTER_CACHE_BENCHMARK") == "1")
        val lists = sampleLists()
        val file = File(folder.root, "written.bin")
        val cache = CompiledFilterCache(file)
        val key = cache.key(lists)
        val rules = FilterCompiler.parse(lists)
        val indexed = rules.build()
        repeat(4) { round ->
            val elapsed = measureNanoTime {
                cache.write(key, rules, indexed.networkIndexes, indexed.pageIndexes)
            }
            assertTrue(file.length() > 1024 * 1024)
            val restored = requireNotNull(cache.read(key)).build()
            assertTrue(restored.restoredNetworkIndexes)
            assertTrue(restored.restoredPageIndexes)
            println(
                "AGGREGATE_CACHE_WRITE round=$round rules=181000 " +
                    "size=${file.length()} ms=${elapsed / 1_000_000.0}"
            )
        }
    }
}
