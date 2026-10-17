package uk.nktnet.webviewkiosk.adblock

import java.io.File
import java.io.RandomAccessFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DocumentFilteringTest {
    @get:Rule val folder = TemporaryFolder()

    private fun engine(vararg lists: String) =
        KotlinFilterEngine().also { it.replace(lists.toList()) }

    private fun blocked(
        engine: KotlinFilterEngine,
        url: String,
        method: String = "GET",
        source: String = "",
    ): Boolean = engine.shouldBlock(url, source, "document", method)

    @Test
    fun explicitDocumentsAndDocAliasKeepPathExceptionsAndResourceTypes() {
        // Forms used in uAssets badware.txt and the URLHaus navigation variant.
        val engine =
            engine(
                "||malware.example^\$document\n" +
                    "||01net.com/telecharger/\$doc\n" +
                    "@@||01net.com/telecharger/safe\$doc\n" +
                    "||cdn.example^\$script\n||frames.example^\$subdocument"
            )
        assertEquals(0, engine.unsupportedRuleCount())
        assertTrue(blocked(engine, "https://malware.example/"))
        assertTrue(blocked(engine, "https://01net.com/telecharger/unsafe.exe"))
        assertFalse(blocked(engine, "https://01net.com/telecharger/safe.exe"))
        assertFalse(blocked(engine, "https://01net.com/news"))
        assertFalse(blocked(engine, "https://cdn.example/ads.js"))
        assertFalse(blocked(engine, "https://frames.example/"))
        assertFalse(
            engine.shouldBlock(
                "https://malware.example/a.js",
                "https://site.example/",
                "script",
                "GET",
            )
        )
        assertTrue(
            engine.shouldBlock(
                "https://01net.com/telecharger/file",
                "",
                "doc",
                "GET",
            )
        )
    }

    @Test
    fun genericHostFiltersBlockPagesButIncidentalPathsAndQueriesDoNot() {
        val cases =
            listOf(
                Triple("||ads.example^", "https://sub.ads.example/page", true),
                Triple("ads.example", "https://ads.example/page", true),
                Triple("ads.example", "https://ads.example.attacker.test/", false),
                Triple(
                    "ads.example",
                    "https://site.example/path/ads.example",
                    false,
                ),
                Triple("ads.example", "https://site.example/?q=ads.example", false),
                Triple("/ads/", "https://site.example/ads/", false),
                Triple("||site.example/ads/", "https://site.example/ads/", false),
                Triple(
                    "||site.example/ads/\$important",
                    "https://site.example/ads/",
                    false,
                ),
                Triple(
                    "/^https?:\\/\\/ads\\.example[\\/:]/",
                    "https://ads.example/a",
                    true,
                ),
                Triple("/ads\\.example/", "https://site.example/ads.example", false),
                Triple("||*.ads.example^", "https://cdn.ads.example/a", true),
                Triple(
                    "||*.ads.example/path",
                    "https://cdn.ads.example/path",
                    false,
                ),
                Triple("*", "https://site.example/", false),
                Triple("/^/", "https://site.example/", false),
                Triple("*\$document", "https://site.example/", true),
            )
        cases.forEach { (rule, url, expected) ->
            assertEquals("$rule on $url", expected, blocked(engine(rule), url))
        }
        assertTrue(
            engine("/ads/")
                .shouldBlock(
                    "https://site.example/ads/",
                    "https://site.example/",
                    "image",
                    "GET",
                )
        )
    }

    @Test
    fun strictMatchingHandlesHostBoundariesCredentialsPortsIdnAndFqdn() {
        val engine = engine("||ads.example^\$method=GET\n||例子.测试^\n||[2001:db8::1]^")
        listOf(
                "https://ads.example./a",
                "https://ADS.EXAMPLE:8443/a",
                "https://user:secret@ads.example/a",
                "https://sub.ads.example/a",
                "https://例子.测试/",
                "https://[2001:db8::1]:8443/",
            )
            .forEach { assertTrue(it, blocked(engine, it)) }
        listOf(
                "https://ads.example@safe.example/",
                "https://safe.example/ads.example",
                "https://ads.example.attacker.test/",
                "https://[2001:db8::2]/",
            )
            .forEach { assertFalse(it, blocked(engine, it)) }
        assertFalse(
            blocked(
                engine("ads.example"),
                "https://ads.example@long-safe.example/",
            )
        )
        assertTrue(blocked(engine("/ads\\.example./"), "https://ads.example./a"))
    }

    @Test
    fun genericAndExplicitExceptionsCombineWithImportantPrecedenceInAnyOrder() {
        val url = "https://ads.example/path"
        val cases =
            listOf(
                listOf("||ads.example^", "@@||ads.example^\$document") to false,
                listOf("||ads.example^\$document", "@@||ads.example/path") to false,
                listOf(
                    "||ads.example^\$document",
                    "@@||ads.example/path\$document",
                ) to false,
                listOf("||ads.example^\$important", "@@||ads.example^\$document") to
                    true,
                listOf(
                    "||ads.example^\$document,important",
                    "@@||ads.example/path",
                ) to true,
                listOf("||ads.example^", "@@||ads.example/path\$script") to true,
                listOf("||ads.example^", "@@||ads.example/path\$~image") to true,
                listOf(
                    "||ads.example/path\$important",
                    "@@||ads.example^\$document",
                ) to false,
            )
        for ((rules, expected) in cases) {
            for (ordered in listOf(rules, rules.reversed())) {
                assertEquals(
                    ordered.toString(),
                    expected,
                    blocked(engine(*ordered.toTypedArray()), url),
                )
            }
        }
    }

    @Test
    fun documentsUseTheirOwnFirstPartyContextAndActualMethod() {
        val engine =
            engine(
                "||ads.example^\$document,domain=ads.example,~third-party,method=POST\n" +
                    "||third.example^\$document,third-party\n" +
                    "||excluded.example^\$~document\n" +
                    "||negated.example^\$~image\n" +
                    "||explicit.example/path\$doc,~image"
            )
        assertTrue(blocked(engine, "https://ads.example/a", "post", "about:blank"))
        assertFalse(blocked(engine, "https://ads.example/a", "GET"))
        assertFalse(
            blocked(
                engine,
                "https://third.example/a",
                source = "https://other.test/",
            )
        )
        assertFalse(blocked(engine, "https://excluded.example/"))
        assertFalse(blocked(engine, "https://negated.example/"))
        assertTrue(blocked(engine, "https://explicit.example/path"))
        assertFalse(blocked(engine, "file:///ads.example/a", "POST"))
        assertFalse(blocked(engine, "not a URL", "POST"))
    }

    @Test
    fun redirectsNeverSupplySurrogatesToDocumentsAndRedirectOnlyDoesNotBlock() {
        val engine =
            engine(
                "||ads.example^\$document,redirect=noopjs\n" +
                    "@@||ads.example^\$redirect-rule\n" +
                    "||redirect-only.example^\$document,redirect-rule=noophtml\n" +
                    "@@||ads.example/safe\$document"
            )
        assertEquals(
            BlockingResponse.BLOCK,
            engine.responseFor("https://ads.example/a", "", "document", "GET"),
        )
        assertFalse(blocked(engine, "https://redirect-only.example/"))
        assertFalse(blocked(engine, "https://ads.example/safe"))
    }

    @Test
    fun exhaustedMatchingBudgetFailsOpenForDocuments() {
        val engine = engine("/" + "a".repeat(2000) + "/\$document")
        assertFalse(blocked(engine, "https://site.example/" + "a".repeat(8000)))
    }

    @Test
    fun cacheRoundTripsKeepDocumentSemanticsAndCrossListBadfilter() {
        val file = File(folder.root, "rules.bin")
        val base = "||ads.example^\$doc\n/ads/\n||bad.example^\$document"
        val exception =
            "@@||ads.example/safe\$document\n||bad.example^\$document,badfilter"
        val lists = listOf(base, exception)
        KotlinFilterEngine(file).replace(lists)
        val restored = KotlinFilterEngine(file)
        val stages = mutableListOf<RuleLoadStage>()
        restored.replace(lists, stages::add)
        assertFalse(RuleLoadStage.COMPILING in stages)
        assertTrue(RuleLoadStage.RESTORING in stages)
        assertTrue(blocked(restored, "https://ads.example/"))
        assertFalse(blocked(restored, "https://ads.example/safe"))
        assertFalse(blocked(restored, "https://safe.example/ads/"))
        assertFalse(blocked(restored, "https://bad.example/"))
        restored.replace(listOf(base))
        assertTrue(blocked(restored, "https://bad.example/"))
    }

    @Test
    fun schemaNineIsRecompiledOnceIncludingIndependentSourceCaches() {
        val file = File(folder.root, "rules.bin")
        val lists = listOf("||ads.example^\$doc", "/ads/")
        KotlinFilterEngine(file).replace(lists)
        (listOf(file) + File(folder.root, "rules.bin.sources").listFiles().orEmpty())
            .filter { it.isFile }
            .forEach {
                RandomAccessFile(it, "rw").use { old ->
                    old.seek(4)
                    old.writeInt(9)
                }
            }
        val restored = KotlinFilterEngine(file)
        val stages = mutableListOf<RuleLoadStage>()
        restored.replace(lists, stages::add)
        assertTrue(RuleLoadStage.COMPILING in stages)
        assertTrue(blocked(restored, "https://ads.example/"))
        stages.clear()
        restored.replace(lists, stages::add)
        assertFalse(RuleLoadStage.COMPILING in stages)
    }
}
