package uk.nktnet.webviewkiosk.blocking

import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.Callable
import java.util.concurrent.Executors

// The app resolves OkHttp's Android variant, whose public-suffix data is an Android
// asset. The engine's source itself has no Android APIs; a JVM library uses OkHttp/JVM.
@RunWith(RobolectricTestRunner::class)
class KotlinFilterEngineTest {
    @Before
    fun initializeSuffixData() {
        okhttp3.OkHttp.initialize(org.robolectric.RuntimeEnvironment.getApplication())
    }

    private fun engine(vararg lists: String) = KotlinFilterEngine().also { it.replace(lists.toList()) }
    private fun blocked(engine: KotlinFilterEngine, url: String, source: String = "https://publisher.example/",
                        type: String = "other", method: String = "GET") = engine.shouldBlock(url, source, type, method)

    @Test
    fun matchesTheNetworkFixtureCorpus() {
        val stream = javaClass.getResourceAsStream("/filter-engine/network.tsv")!!
        var count = 0
        stream.bufferedReader().useLines { lines ->
            for (line in lines.drop(1)) {
                val fields = line.split('\t')
                val rules = fields[1].replace("\\n", "\n")
                val engine = engine(rules)
                assertEquals(fields[0], fields[6] == "1", blocked(engine, fields[2], fields[3], fields[4], fields[5]))
                count++
            }
        }
        assertTrue("The reference corpus must remain substantial", count >= 100)
    }

    @Test(timeout = 10_000)
    fun pathologicalWildcardWorkFailsOpenWithoutStallingTheRequestThread() {
        val engine = engine("a*" + "a".repeat(2048) + "b")
        assertFalse(blocked(engine, "https://site.example/" + "a".repeat(100_000)))
    }

    @Test
    fun invalidUrlsAndUnknownSourceContextFailOpen() {
        val engine = engine("*")
        assertFalse(blocked(engine, "not a URL"))
        assertFalse(blocked(engine, "file:///private.html"))
        assertFalse(blocked(engine, "https://ads.example/a", ""))
        assertFalse(blocked(engine, "https://ads.example/a", "null"))
    }

    @Test
    fun underscoresUnicodeAndUppercaseHostsRemainUsable() {
        val engine = engine("||ads_name.example^\n||例子.测试^")
        engine.validate("||ads_name.example^\n||例子.测试^")
        assertTrue(blocked(engine, "https://ADS_NAME.EXAMPLE/a"))
        assertTrue(blocked(engine, "https://例子.测试/a"))
    }

    @Test
    fun importantBlocksOverrideOrdinaryExceptionsAndBadfilterDisablesAcrossLists() {
        val rules = "||ads.example^\n@@||ads.example/allowed\n||ads.example/important\$important"
        val engine = engine(rules)
        assertFalse(blocked(engine, "https://ads.example/allowed"))
        assertTrue(blocked(engine, "https://ads.example/important"))
        val disabled = engine(rules, "||ads.example^\$badfilter\n||ads.example/important\$badfilter,important")
        assertFalse(blocked(disabled, "https://ads.example/anything"))
        assertFalse(blocked(disabled, "https://ads.example/important"))
    }

    @Test
    fun badfilterIdentityUsesParsedOptionsAndNormalizedDomains() {
        val engine = engine(
            "||ads.example^\$script,domain=publisher.example|other.example,third-party",
            "||ADS.EXAMPLE^\$3p,domain=other.example|publisher.example,script,badfilter",
        )
        assertFalse(blocked(engine, "https://ads.example/a.js", type = "script"))
    }

    @Test
    fun unknownModifiersDiscardTheWholeRuleIncludingExceptions() {
        val engine = engine("||cdn.example^\$script,future-option\n@@||ads.example^\$future-option\n||ads.example^")
        assertFalse(blocked(engine, "https://cdn.example/image.png", type = "image"))
        assertFalse(blocked(engine, "https://cdn.example/a.js", type = "script"))
        assertTrue(blocked(engine, "https://ads.example/a.js", type = "script"))
        assertEquals(2, engine.unsupportedRuleCount())
    }

    @Test
    fun publicSuffixesPrivateSuffixesAndIpAddressesDeterminePartyCorrectly() {
        val engine = engine("*\$third-party")
        assertFalse(blocked(engine, "https://cdn.example.co.uk/a", "https://www.example.co.uk/"))
        assertTrue(blocked(engine, "https://example.co.uk/a", "https://other.co.uk/"))
        assertTrue(blocked(engine, "https://one.github.io/a", "https://two.github.io/"))
        assertFalse(blocked(engine, "https://127.0.0.1/a", "http://127.0.0.1/"))
        assertTrue(blocked(engine, "https://127.0.0.1/a", "http://127.0.0.2/"))
        assertFalse(blocked(engine, "https://[::1]/a", "http://[::1]/"))
    }

    @Test
    fun mixedHostsAndAdblockRulesKeepTheirMeaningInEitherOrder() {
        for (rules in listOf(
            "0.0.0.0 telemetry.example alias.example # comment\n:: ipv6.example\n||ads.example^\n@@||ads.example/allowed",
            "||ads.example^\n0.0.0.0 telemetry.example alias.example\n:: ipv6.example\n@@||ads.example/allowed",
        )) {
            val engine = engine(rules)
            for (host in listOf("telemetry.example", "alias.example", "ipv6.example", "ads.example")) {
                assertTrue(host, blocked(engine, "https://$host/a"))
            }
            assertFalse(blocked(engine, "https://ads.example/allowed"))
        }
        val hosts = engine("0.0.0.0 telemetry.example\nads.example")
        assertTrue(blocked(hosts, "https://ads.example/a"))
        assertFalse(blocked(hosts, "https://publisher.example/ads.example.js"))
    }

    @Test
    fun domainAnchorsDoNotMatchCredentialsPathsOrSimilarHostnames() {
        val engine = engine("||ads.example^")
        for (url in listOf(
            "https://ads.example.evil.test/a", "https://notads.example/a",
            "https://publisher.example/ads.example/a", "https://ads.example@publisher.example/a",
        )) assertFalse(url, blocked(engine, url))
        assertTrue(blocked(engine, "https://sub.ads.example:8443/a"))
    }

    @Test
    fun cosmeticSelectorsHonorDomainsExceptionsAndObservedGenericTokens() {
        val engine = engine("##.ad\n###advert\npublisher.example##.sponsor\npublisher.example#@#.ad\nother.example##.other")
        assertTrue(engine.hasCosmeticRules())
        assertEquals(setOf(".sponsor", "#advert"), engine.cosmeticSelectors(
            "https://publisher.example/", listOf("ad"), listOf("advert"),
        ).toSet())
        assertEquals(setOf(".ad", ".other"), engine.cosmeticSelectors("https://other.example/", listOf("ad"), emptyList()).toSet())
        assertTrue(engine.cosmeticSelectors("https://unrelated.example/", emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun genericAndAllHidingExceptionsRespectUrlAndBadfilter() {
        val engine = engine("##.ad\npublisher.example##.sponsor\n@@||publisher.example/no-generic\$generichide\n@@||publisher.example/no-hiding\$elemhide")
        assertEquals(setOf(".sponsor"), engine.cosmeticSelectors("https://publisher.example/no-generic", listOf("ad"), emptyList()).toSet())
        assertTrue(engine.cosmeticSelectors("https://publisher.example/no-hiding", listOf("ad"), emptyList()).isEmpty())
        val disabled = engine("##.ad\n@@||publisher.example^\$generichide\n@@||publisher.example^\$badfilter,generichide")
        assertEquals(listOf(".ad"), disabled.cosmeticSelectors("https://publisher.example/", listOf("ad"), emptyList()))
    }

    @Test
    fun selectorListsAndComplexSelectorsCannotBeMissedByTokenIndexing() {
        val engine = engine("##.ad, .sponsor\n##div[data-ad]\n##.banner > .inner\n##.first.second")
        assertEquals(setOf(".ad, .sponsor", "div[data-ad]"), engine.cosmeticSelectors("https://site.example/", listOf("sponsor"), emptyList()).toSet())
        assertTrue(engine.cosmeticSelectors("https://site.example/", listOf("banner", "first"), emptyList()).containsAll(listOf(".banner > .inner", ".first.second")))
    }

    @Test
    fun cosmeticDelimitersInsideSelectorsRemainLiteral() {
        val selector = "a[href*=\"#@#\"]"
        val engine = engine("site.example##$selector\nallowed.site.example#@#$selector")
        assertEquals(listOf(selector), engine.cosmeticSelectors("https://site.example/", emptyList(), emptyList()))
        assertTrue(engine.cosmeticSelectors("https://allowed.site.example/", emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun cosmeticExceptionsRespectPositiveAndNegativeScopes() {
        val engine = engine("##.ad\nsite.example,~ads.site.example#@#.ad\n~other.example#@#.ad")
        assertTrue(engine.cosmeticSelectors("https://site.example/", listOf("ad"), emptyList()).isEmpty())
        assertTrue(engine.cosmeticSelectors("https://ads.site.example/", listOf("ad"), emptyList()).isEmpty())
        assertEquals(listOf(".ad"), engine.cosmeticSelectors("https://other.example/", listOf("ad"), emptyList()))
    }

    @Test
    fun unsupportedCosmeticActionsAreNeverExposedToThePage() {
        val engine = engine("||ads.example^\nsite.example##+js(set, foo, bar)\nsite.example##.ad:style(color:red)\nsite.example##.ad:has-text(Ads)\nsite.example##.ad:remove()")
        assertFalse(engine.hasCosmeticRules())
        assertTrue(engine.cosmeticSelectors("https://site.example/", listOf("ad"), emptyList()).isEmpty())
        assertEquals(4, engine.unsupportedRuleCount())
    }

    @Test
    fun selectorRepliesAreBoundedAndDisablingClearsTheEngine() {
        val engine = engine((0 until 3000).joinToString("\n") { "site.example##.ad-$it" })
        assertEquals(2048, engine.cosmeticSelectors("https://site.example/", emptyList(), emptyList()).size)
        engine.clear()
        assertFalse(engine.hasCosmeticRules())
        assertTrue(engine.cosmeticSelectors("https://site.example/", listOf("ad"), emptyList()).isEmpty())
        assertFalse(blocked(engine, "https://ads.example/a"))
    }

    @Test
    fun validationReportsOriginalLinesAndBoundsDiagnostics() {
        val engine = KotlinFilterEngine()
        val error = assertThrows(IllegalArgumentException::class.java) {
            engine.validate("\uFEFF! header\r\n\n||ads.example^\$domain=\n" + (0 until 100).joinToString("\n") { "||" })
        }
        assertTrue(error.message!!.contains("Line 3:"))
        assertTrue(error.message!!.contains("more errors"))
        assertTrue(error.message!!.length < 1000)
        assertThrows(IllegalArgumentException::class.java) { engine.validate("999.0.0.1 ads.example") }
        assertThrows(IllegalArgumentException::class.java) { engine.validate("||ads.example^\n! comment\u0000") }
    }

    @Test
    fun emptyUnsupportedAndUsefulMixedListsHaveDistinctValidationResults() {
        val engine = KotlinFilterEngine()
        for (text in listOf(" ", "! comment\n# comment\n[Adblock Plus 2.0]")) {
            assertTrue(assertThrows(IllegalArgumentException::class.java) { engine.validate(text) }.message!!.contains("no rules"))
        }
        for (text in listOf("site.example##+js(set, foo, bar)", "||ads.example^\$future-option", "/ads[0-9]+/")) {
            assertTrue(assertThrows(IllegalArgumentException::class.java) { engine.validate(text) }.message!!.contains("no supported"))
        }
        engine.validate("||ads.example^\n||cdn.example^\$future-option")
        engine.validate("site.example##.ad\nsite.example#@#.ad")
        engine.validate("@@||allowed.example^")
    }

    @Test
    fun failedCompilationKeepsTheLastWorkingSnapshot() {
        val engine = engine("||ads.example^")
        // Different conditions make these unique despite sharing an unindexable pattern.
        val oversized = (0..2048).joinToString("\n") { "ad\$method=M" + "X".repeat(it + 1) }
        assertThrows(IllegalArgumentException::class.java) { engine.replace(listOf(oversized)) }
        assertTrue(blocked(engine, "https://ads.example/a"))
    }

    @Test
    fun concurrentReadersSeeWholeSnapshotsWhileListsAreReplaced() {
        val engine = engine("||ads.example^")
        val executor = Executors.newFixedThreadPool(4)
        try {
            val readers = (0 until 3).map { executor.submit(Callable {
                repeat(1000) { assertFalse(blocked(engine, "https://unrelated.example/a")) }
            }) }
            repeat(50) { engine.replace(listOf(if (it % 2 == 0) "||ads.example^" else "||tracker.example^")) }
            readers.forEach { it.get() }
        } finally { executor.shutdownNow() }
    }
}
