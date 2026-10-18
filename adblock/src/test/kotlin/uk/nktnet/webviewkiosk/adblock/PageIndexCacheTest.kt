package uk.nktnet.webviewkiosk.adblock

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import uk.nktnet.webviewkiosk.adblock.engine.CompiledFilterCache
import uk.nktnet.webviewkiosk.adblock.engine.CosmeticBucket
import uk.nktnet.webviewkiosk.adblock.engine.FilterCompiler
import uk.nktnet.webviewkiosk.adblock.engine.RuleBuckets
import uk.nktnet.webviewkiosk.adblock.engine.ScriptletBucket

class PageIndexCacheTest {
    @get:Rule val folder = TemporaryFolder()

    private val lists =
        listOf(
            listOf(
                    "##.generic",
                    "###generic-id",
                    "##[data-ad]",
                    "##[data-safe]",
                    "#@#[data-safe]",
                    "~excluded.example##[data-excluded]",
                    "site.example,other.example##.specific",
                    "site.example##.second",
                    "safe.site.example#@#.specific",
                    "publisher.*##.entity",
                    "safe.publisher.*#@#.entity",
                    "site.example##.card:has-text(Sponsored)",
                    "safe.site.example#@#.card:has-text(Sponsored)",
                    "publisher.*##.card:has-text(Entity)",
                    "safe.publisher.*#@#.card:has-text(Entity)",
                    "site.example,site.example/path,site.example/other" +
                        "##+js(set, marker, false)",
                    "site.example##+js(set, secondMarker, false)",
                    "safe.site.example#@#+js(set, marker, false)",
                    "publisher.*##+js(set, entityMarker, false)",
                    "parent.example>>##+js(set, ancestorMarker, false)",
                    "publisher.*>>##+js(set, ancestorEntityMarker, false)",
                    "/^regex[0-9]\\.example$/,/^re[0-9]\\.example$/" +
                        "##+js(set, regexMarker, false)",
                    "~site.example,~publisher.*,~child.example,~regex1.example#@#+js()",
                )
                .joinToString("\n")
        )

    private val urls =
        listOf(
            "https://site.example/path",
            "https://safe.site.example/path",
            "https://www.publisher.co.uk/",
            "https://safe.publisher.com/",
            "https://other.example/",
            "https://excluded.example/",
            "https://regex1.example/",
            "https://child.example/",
            "https://appassets.androidplatform.net/",
        )

    private val ancestors =
        listOf("https://parent.example/", "https://publisher.com/")
    private val classes = listOf("generic")
    private val ids = listOf("generic-id")

    @Test
    fun productionRestoresPageBucketsDirectlyWithIdenticalRulesAndDispatcher() {
        val file = File(folder.root, "pages.bin")
        val cold = KotlinFilterEngine(file)
        cold.replace(lists)
        assertEquals(0, cold.unsupportedRuleCount())
        val cache = CompiledFilterCache(file)
        val saved = cache.read(cache.key(lists))!!
        val snapshot = saved.pageIndexes!!
        assertTrue(snapshot.cosmetics.tables.all { it.isNotEmpty() })
        assertTrue(snapshot.scriptlets.tables.all { it.isNotEmpty() })
        val compiled = saved.build()
        assertTrue(compiled.restoredPageIndexes)
        assertSame(snapshot.cosmetics, compiled.pageIndexes.cosmetics)
        assertSame(snapshot.scriptlets, compiled.pageIndexes.scriptlets)
        val bytes = file.readBytes()
        val stages = mutableListOf<RuleLoadStage>()
        val warm = KotlinFilterEngine(file)
        warm.replace(lists, stages::add)
        assertTrue(RuleLoadStage.RESTORING in stages)
        assertFalse(RuleLoadStage.COMPILING in stages)
        assertFalse(RuleLoadStage.SAVING_CACHE in stages)
        assertTrue(bytes.contentEquals(file.readBytes()))
        assertEquals(cold.scriptletInjection(), warm.scriptletInjection())
        for (url in urls) {
            assertEquals(
                url,
                cold.cosmeticSelectors(url, classes, ids),
                warm.cosmeticSelectors(url, classes, ids),
            )
            assertEquals(
                url,
                cold.cosmeticProcedures(url),
                warm.cosmeticProcedures(url),
            )
            assertEquals(
                url,
                cold.scriptlets(url, ancestors),
                warm.scriptlets(url, ancestors),
            )
        }
        val selectors = warm.cosmeticSelectors(urls[0], classes, ids)
        assertTrue(
            selectors.containsAll(listOf(".specific", ".generic", "#generic-id"))
        )
        assertFalse("[data-safe]" in selectors)
        assertFalse(".specific" in warm.cosmeticSelectors(urls[1], classes, ids))
        assertTrue(warm.cosmeticProcedures(urls[1]).isEmpty())
        assertEquals(
            setOf("ancestorMarker", "ancestorEntityMarker"),
            warm
                .scriptlets("https://child.example/", ancestors)
                .map { it.arguments.first() }
                .toSet(),
        )
    }

    @Test
    fun missingPageIndexesUpgradeWithoutReparsingOrRebuildingNetworkBuckets() {
        val file = File(folder.root, "normalized.bin")
        val rules = FilterCompiler.parse(lists)
        val cache = CompiledFilterCache(file)
        val key = cache.key(lists)
        cache.write(key, rules, rules.build().networkIndexes)
        val stages = mutableListOf<RuleLoadStage>()
        KotlinFilterEngine(file).replace(lists, stages::add)
        assertFalse(RuleLoadStage.COMPILING in stages)
        assertTrue(RuleLoadStage.SAVING_CACHE in stages)
        val compiled = cache.read(key)!!.build()
        assertTrue(compiled.restoredNetworkIndexes)
        assertTrue(compiled.restoredPageIndexes)
    }

    @Test
    fun wideDomainRulesRestoreWithoutASeparatePerRuleBucketLimit() {
        val domains = (0 until 1200).joinToString(",") { "h${it.toString(36)}" }
        val inputs =
            listOf("$domains##.wide", "$domains##+js(set, wideMarker, false)")
        val file = File(folder.root, "wide.bin")
        KotlinFilterEngine(file).replace(inputs)
        val cache = CompiledFilterCache(file)
        val saved = cache.read(cache.key(inputs))!!
        assertTrue(saved.build().restoredPageIndexes)
        val stages = mutableListOf<RuleLoadStage>()
        val engine = KotlinFilterEngine(file)
        engine.replace(inputs, stages::add)
        assertFalse(RuleLoadStage.COMPILING in stages)
        assertFalse(RuleLoadStage.SAVING_CACHE in stages)
        val url = "https://h${1199.toString(36)}/"
        assertEquals(
            listOf(".wide"),
            engine.cosmeticSelectors(url, emptyList(), emptyList()),
        )
        assertEquals("wideMarker", engine.scriptlets(url).single().arguments.first())
    }

    @Test
    fun validChecksumCannotActivateDamagedPageBucketsOrLoseExceptions() {
        val file = File(folder.root, "damaged.bin")
        val rules = FilterCompiler.parse(lists)
        val original = rules.build()
        val snapshot = original.pageIndexes
        val cache = CompiledFilterCache(file)
        val key = cache.key(lists)
        for (scriptlets in listOf(false, true)) {
            val routing =
                if (scriptlets) {
                    snapshot.scriptlets
                } else {
                    snapshot.cosmetics
                }
            val hostTable =
                if (scriptlets) {
                    ScriptletBucket.HOST.ordinal
                } else {
                    CosmeticBucket.HOST.ordinal
                }
            val exceptionTable =
                if (scriptlets) {
                    ScriptletBucket.GENERIC_EXCEPTION.ordinal
                } else {
                    CosmeticBucket.GENERIC_EXCEPTION.ordinal
                }
            val hosts = routing.tables[hostTable]
            val entries = hosts.getValue("site.example")
            assertTrue(entries.size > 1)
            val malformed =
                listOf(
                    hostTable to (hosts - "site.example"),
                    hostTable to (hosts + ("wrong.example" to entries)),
                    hostTable to
                        (hosts + ("site.example" to entries.reversedArray())),
                    hostTable to
                        (hosts +
                            ("site.example" to intArrayOf(entries[0], entries[0]))),
                    exceptionTable to emptyMap(),
                )
            for ((table, values) in malformed) {
                val tables =
                    routing.tables.toMutableList().apply { this[table] = values }
                val damaged = RuleBuckets(routing.ruleCount, tables)
                val invalid =
                    if (scriptlets) {
                        snapshot.copy(scriptlets = damaged)
                    } else {
                        snapshot.copy(cosmetics = damaged)
                    }
                cache.write(key, rules, original.networkIndexes, invalid)
                val saved = cache.read(key)!!
                val rebuilt = saved.build()
                assertFalse(rebuilt.restoredPageIndexes)
                assertTrue(rebuilt.restoredNetworkIndexes)
                for (url in urls) {
                    assertEquals(
                        original.cosmeticSelectors(url, classes, ids),
                        rebuilt.cosmeticSelectors(url, classes, ids),
                    )
                    assertEquals(
                        original.cosmeticProcedures(url),
                        rebuilt.cosmeticProcedures(url),
                    )
                    assertEquals(
                        original.scriptlets(url, ancestors),
                        rebuilt.scriptlets(url, ancestors),
                    )
                }
                val stages = mutableListOf<RuleLoadStage>()
                KotlinFilterEngine(file).replace(lists, stages::add)
                assertFalse(RuleLoadStage.COMPILING in stages)
                assertTrue(RuleLoadStage.SAVING_CACHE in stages)
                assertTrue(cache.read(key)!!.build().restoredPageIndexes)
            }
        }
    }
}
