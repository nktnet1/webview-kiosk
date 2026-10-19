package uk.nktnet.webviewkiosk.adblock

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import uk.nktnet.webviewkiosk.adblock.engine.CompiledFilterCache
import uk.nktnet.webviewkiosk.adblock.engine.FilterCompiler
import uk.nktnet.webviewkiosk.adblock.engine.NetworkRouting

class RuntimeIndexCacheTest {
    @get:Rule val folder = TemporaryFolder()

    private val lists =
        listOf(
            "||ads.example/track\$script\n" +
                "||ads.example/pixel\$image\n" +
                "||cdn.example/banner\$image\n" +
                "tracking-token\$xhr\n" +
                "/ad\$xhr,domain=site.example|other.example\n" +
                "*\$ping,domain=publisher.*\n" +
                "@@||site.example^\$generichide\n" +
                "@@||ads.example/track\$redirect-rule\n" +
                "||site.example^\$removeparam=track"
        )

    @Test
    fun productionPersistsAndRestoresAllFourNetworkFamilies() {
        val file = File(folder.root, "compiled.bin")
        val cold = KotlinFilterEngine(file)
        cold.replace(lists)
        val cache = CompiledFilterCache(file)
        val saved = cache.read(cache.key(lists))!!
        assertNotNull(saved.networkIndexes)
        assertTrue(saved.build().restoredNetworkIndexes)
        val bytes = file.readBytes()
        val stages = mutableListOf<RuleLoadStage>()
        val warm = KotlinFilterEngine(file)
        warm.replace(lists, stages::add)
        assertTrue(RuleLoadStage.RESTORING in stages)
        assertFalse(RuleLoadStage.COMPILING in stages)
        assertFalse(RuleLoadStage.SAVING_CACHE in stages)
        assertTrue(bytes.contentEquals(file.readBytes()))
        assertTrue(
            warm.shouldBlock(
                "https://ads.example/track",
                "https://site.example/",
                "script",
                "GET",
            )
        )
        assertEquals(
            cold.removeQueryParameters(
                "https://site.example/?track=1&keep=2",
                "",
                "document",
                "GET",
            ),
            warm.removeQueryParameters(
                "https://site.example/?track=1&keep=2",
                "",
                "document",
                "GET",
            ),
        )
    }

    @Test
    fun contextualExceptionsKeepTheirBehaviourWithoutAnInitiator() {
        val inputs =
            listOf(
                "||domain-exception.example^\n" +
                    "@@||domain-exception.example^\$script,domain=site.example\n" +
                    "||party-exception.example^\n" +
                    "@@||party-exception.example^\$script,third-party\n" +
                    "||hide.example^\n" +
                    "@@||hide.example^\$generichide,domain=site.example\n" +
                    "||redirect.example^\n" +
                    "@@||redirect.example^\$redirect-rule,domain=site.example\n" +
                    "||param.example^\n" +
                    "@@||param.example^\$removeparam=track,domain=site.example"
            )
        val file = File(folder.root, "contextual.bin")
        repeat(2) { run ->
            val engine = KotlinFilterEngine(file)
            val stages = mutableListOf<RuleLoadStage>()
            engine.replace(inputs, stages::add)
            assertEquals(0, engine.unsupportedRuleCount())
            if (run == 1) {
                assertTrue(RuleLoadStage.RESTORING in stages)
                assertFalse(RuleLoadStage.COMPILING in stages)
                assertFalse(RuleLoadStage.SAVING_CACHE in stages)
            }
            fun blocked(host: String, source: String = "", type: String = "script") =
                engine.shouldBlock("https://$host/ads.js", source, type, "GET")
            assertFalse(blocked("domain-exception.example"))
            assertFalse(blocked("party-exception.example"))
            assertTrue(blocked("domain-exception.example", type = "image"))
            assertTrue(blocked("party-exception.example", type = "image"))
            assertFalse(blocked("domain-exception.example", "https://site.example/"))
            assertTrue(blocked("domain-exception.example", "https://other.example/"))
            assertFalse(blocked("party-exception.example", "https://site.example/"))
            assertTrue(
                blocked(
                    "party-exception.example",
                    "https://party-exception.example/",
                )
            )
            for (host in
                listOf("hide.example", "redirect.example", "param.example")) {
                assertTrue(host, blocked(host))
            }
        }
    }

    @Test
    fun normalizedOnlySnapshotsUpgradeWithoutReparsingSourceText() {
        val file = File(folder.root, "normalized.bin")
        val cache = CompiledFilterCache(file)
        cache.write(cache.key(lists), FilterCompiler.parse(lists))
        val stages = mutableListOf<RuleLoadStage>()
        KotlinFilterEngine(file).replace(lists, stages::add)
        assertFalse(RuleLoadStage.COMPILING in stages)
        assertTrue(RuleLoadStage.SAVING_CACHE in stages)
        assertTrue(cache.read(cache.key(lists))!!.build().restoredNetworkIndexes)
    }

    @Test
    fun validChecksumCannotActivateMissingWrongOrDuplicatedBucketMembership() {
        val file = File(folder.root, "corrupt-buckets.bin")
        val cache = CompiledFilterCache(file)
        val rules = FilterCompiler.parse(lists)
        val snapshot = rules.build().networkIndexes
        val original = snapshot.requests
        val key = cache.key(lists)
        val first = original.hosts.entries.single { it.key == "ads.example" }
        val malformed =
            listOf(
                original.hosts - first.key,
                original.hosts + ("wrong.example" to first.value),
                original.hosts +
                    (first.key to intArrayOf(first.value[0], first.value[0])),
                original.hosts + (first.key to first.value.reversedArray()),
            )
        for (hosts in malformed) {
            val invalid =
                NetworkRouting(
                    original.ruleCount,
                    hosts,
                    original.pages,
                    original.entities,
                    original.tokens,
                    original.fallback,
                )
            cache.write(key, rules, snapshot.copy(requests = invalid))
            val restored = cache.read(key)!!
            assertFalse(restored.build().restoredNetworkIndexes)
            assertEquals(snapshot, restored.build().networkIndexes)
            val stages = mutableListOf<RuleLoadStage>()
            val engine = KotlinFilterEngine(file)
            engine.replace(lists, stages::add)
            assertFalse(RuleLoadStage.COMPILING in stages)
            assertTrue(RuleLoadStage.SAVING_CACHE in stages)
            assertTrue(cache.read(key)!!.build().restoredNetworkIndexes)
            assertTrue(
                engine.shouldBlock(
                    "https://ads.example/track",
                    "https://site.example/",
                    "script",
                    "GET",
                )
            )
        }
    }
}
