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

class CompiledFilterCacheTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun cachedEngineMatchesTheEntireNetworkFixtureCorpusWithoutReparsing() {
        val file = File(folder.root, "compiled.bin")
        javaClass
            .getResourceAsStream("/adblock/network.tsv")!!
            .bufferedReader()
            .useLines { lines ->
                for (line in lines.drop(1)) {
                    val fields = line.split('\t')
                    val lists = listOf(fields[1].replace("\\n", "\n"))
                    KotlinFilterEngine(file).replace(lists)
                    val stages = mutableListOf<RuleLoadStage>()
                    val restored = KotlinFilterEngine(file)
                    restored.replace(lists, stages::add)
                    assertFalse(fields[0], RuleLoadStage.COMPILING in stages)
                    assertTrue(fields[0], RuleLoadStage.RESTORING in stages)
                    assertEquals(
                        fields[0],
                        fields[6] == "1",
                        restored.shouldBlock(
                            fields[2],
                            fields[3],
                            fields[4],
                            fields[5],
                        ),
                    )
                }
            }
    }

    @Test
    fun cachePreservesNormalizedConditionsCosmeticsAndCrossListBadfilter() {
        val lists =
            listOf(
                "||ads.example^\n@@||ads.example/allowed\n" +
                    "||tracker.example^\$script,third-party," +
                    "domain=site.example|~safe.site.example,method=GET|~POST\n" +
                    "||important.example^\$important,match-case\n@@||page.example^\$generichide\n" +
                    "##.ad\nsite.example,~safe.site.example##.sponsor\nsite.example#@#.ad\n" +
                    "||unsupported.example^\$redirect=noop\n0.0.0.0 hosts.example\n||例子.测试^",
                "||ads.example^\$badfilter\n##.ad",
            )
        val file = File(folder.root, "compiled.bin")
        val cache = CompiledFilterCache(file)
        val original = FilterCompiler.parse(lists)
        cache.write(cache.key(lists), original)
        val restored = cache.read(cache.key(lists))!!
        assertEquals(original.networks.toList(), restored.networks.toList())
        assertEquals(original.cosmetics.toList(), restored.cosmetics.toList())
        assertEquals(original.skippedRules, restored.skippedRules)
        val engine = KotlinFilterEngine(file).also { it.replace(lists) }
        assertFalse(
            engine.shouldBlock(
                "https://ads.example/pixel",
                "https://site.example/",
                "other",
                "GET",
            )
        )
        assertTrue(
            engine.shouldBlock(
                "https://hosts.example/pixel",
                "https://site.example/",
                "other",
                "GET",
            )
        )
        assertTrue(
            engine.shouldBlock(
                "https://tracker.example/a",
                "https://site.example/",
                "script",
                "GET",
            )
        )
        assertFalse(
            engine.shouldBlock(
                "https://tracker.example/a",
                "https://safe.site.example/",
                "script",
                "GET",
            )
        )
        assertFalse(
            engine.shouldBlock(
                "https://tracker.example/a",
                "https://site.example/",
                "script",
                "POST",
            )
        )
        assertEquals(
            listOf(".sponsor"),
            engine.cosmeticSelectors(
                "https://site.example/",
                listOf("ad"),
                emptyList(),
            ),
        )
        assertEquals(
            emptyList<String>(),
            engine.cosmeticSelectors(
                "https://page.example/",
                listOf("ad"),
                emptyList(),
            ),
        )
        assertEquals(1, engine.unsupportedRuleCount())
    }

    @Test
    fun cachePreservesEntityDestinationsProceduresAndSurrogates() {
        val lists =
            listOf(
                "||ads.example^\$script,redirect=noopjs,to=ads.example\n" +
                    "*\$image,from=publisher.*,denyallow=trusted.example\n" +
                    "publisher.*##.ad:has-text(Sponsored)\n" +
                    "publisher.*#@#.ad:has-text(Sponsored)\n" +
                    "publisher.*##.banner:remove()"
            )
        val file = File(folder.root, "advanced.bin")
        val cache = CompiledFilterCache(file)
        val original = FilterCompiler.parse(lists)
        cache.write(cache.key(lists), original)
        val restored = cache.read(cache.key(lists))!!
        assertEquals(original.networks.toList(), restored.networks.toList())
        assertEquals(original.cosmetics.toList(), restored.cosmetics.toList())
        val engine = KotlinFilterEngine(file)
        val stages = mutableListOf<RuleLoadStage>()
        engine.replace(lists, stages::add)
        assertTrue(RuleLoadStage.RESTORING in stages)
        assertEquals(
            BlockingResponse.EMPTY_SCRIPT,
            engine.responseFor(
                "https://ads.example/a.js",
                "https://publisher.co.uk/",
                "script",
                "GET",
            ),
        )
        assertEquals(
            BlockingResponse.ALLOW,
            engine.responseFor(
                "https://other.example/a.js",
                "https://unrelated.example/",
                "script",
                "GET",
            ),
        )
        assertEquals(
            1,
            engine.cosmeticProcedures("https://www.publisher.co.uk/").size,
        )
    }

    @Test
    fun exactContentAndListBoundariesInvalidateTheCache() {
        val file = File(folder.root, "compiled.bin")
        val cache = CompiledFilterCache(file)
        assertFalse(
            cache.key(listOf("ab", "c")).contentEquals(cache.key(listOf("a", "bc")))
        )
        val engine = KotlinFilterEngine(file)
        engine.replace(listOf("||old.example^", "@@||old.example^"))
        val stages = mutableListOf<RuleLoadStage>()
        engine.replace(listOf("||new.example^"), stages::add)
        assertTrue(RuleLoadStage.COMPILING in stages)
        assertTrue(
            engine.shouldBlock(
                "https://new.example/a",
                "https://site.example/",
                "other",
                "GET",
            )
        )
        assertFalse(
            engine.shouldBlock(
                "https://old.example/a",
                "https://site.example/",
                "other",
                "GET",
            )
        )
        // Dropping a disabled exception list must also invalidate the previous
        // snapshot.
        engine.replace(listOf("||old.example^"))
        assertTrue(
            engine.shouldBlock(
                "https://old.example/a",
                "https://site.example/",
                "other",
                "GET",
            )
        )
    }

    @Test
    fun missingTruncatedCorruptAndIncompatibleCachesRecompileAndRepair() {
        val file = File(folder.root, "compiled.bin")
        val cache = CompiledFilterCache(file, buildVersion = 1)
        val lists = listOf("||ads.example^\n##.ad")
        KotlinFilterEngine(file, cacheVersion = 1).replace(lists)
        val valid = file.readBytes()
        val variants =
            listOf(
                byteArrayOf(),
                valid.copyOf(valid.size / 2),
                valid.copyOf().apply { this[4] = 127 }, // Unknown cache format.
                valid.copyOf().apply { this[8] = 127 }, // Different app build.
                valid.copyOf().apply {
                    this[lastIndex - 10] =
                        (this[lastIndex - 10].toInt() xor 1).toByte()
                },
                valid + byteArrayOf(1),
            )
        for (bytes in variants) {
            file.writeBytes(bytes)
            val stages = mutableListOf<RuleLoadStage>()
            val engine = KotlinFilterEngine(file, cacheVersion = 1)
            engine.replace(lists, stages::add)
            assertTrue(RuleLoadStage.COMPILING in stages)
            assertTrue(
                engine.shouldBlock(
                    "https://ads.example/a",
                    "https://site.example/",
                    "other",
                    "GET",
                )
            )
            assertNotNull(cache.read(cache.key(lists)))
        }
        file.delete()
        val stages = mutableListOf<RuleLoadStage>()
        KotlinFilterEngine(file, cacheVersion = 1).replace(lists, stages::add)
        assertTrue(RuleLoadStage.COMPILING in stages)
    }

    @Test
    fun unavailableCacheStorageDoesNotPreventBlocking() {
        val parent = folder.newFile("not-a-directory")
        val engine = KotlinFilterEngine(File(parent, "compiled.bin"))
        engine.replace(listOf("||ads.example^"))
        assertTrue(
            engine.shouldBlock(
                "https://ads.example/a",
                "https://site.example/",
                "other",
                "GET",
            )
        )
    }
}
