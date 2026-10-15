package uk.nktnet.webviewkiosk.adblock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Callable
import java.util.concurrent.Executors

class KotlinFilterEngineTest {
    private fun engine(vararg lists: String) =
        KotlinFilterEngine().also { it.replace(lists.toList()) }

    private fun blocked(
        engine: KotlinFilterEngine,
        url: String,
        source: String = "https://publisher.example/",
        type: String = "other",
        method: String = "GET",
    ) = engine.shouldBlock(url, source, type, method)

    @Test
    fun matchesTheNetworkFixtureCorpus() {
        val stream = javaClass.getResourceAsStream("/adblock/network.tsv")!!
        var count = 0
        stream.bufferedReader().useLines { lines ->
            for (line in lines.drop(1)) {
                val fields = line.split('\t')
                val rules = fields[1].replace("\\n", "\n")
                val engine = engine(rules)
                assertEquals(
                    fields[0],
                    fields[6] == "1",
                    blocked(engine, fields[2], fields[3], fields[4], fields[5]),
                )
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
        val disabled =
            engine(rules, "||ads.example^\$badfilter\n||ads.example/important\$badfilter,important")
        assertFalse(blocked(disabled, "https://ads.example/anything"))
        assertFalse(blocked(disabled, "https://ads.example/important"))
    }

    @Test
    fun badfilterIdentityUsesParsedOptionsAndNormalizedDomains() {
        val engine =
            engine(
                "||ads.example^\$script,domain=publisher.example|other.example,third-party",
                "||ADS.EXAMPLE^\$3p,domain=other.example|publisher.example,script,badfilter",
            )
        assertFalse(blocked(engine, "https://ads.example/a.js", type = "script"))
    }

    @Test
    fun unknownModifiersDiscardTheWholeRuleIncludingExceptions() {
        val engine =
            engine(
                "||cdn.example^\$script,future-option\n" +
                    "@@||ads.example^\$future-option\n||ads.example^",
            )
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
        for (
        rules in
        listOf(
            "0.0.0.0 telemetry.example alias.example # comment\n" +
                ":: ipv6.example\n||ads.example^\n@@||ads.example/allowed",
            "||ads.example^\n0.0.0.0 telemetry.example alias.example\n" +
                ":: ipv6.example\n@@||ads.example/allowed",
        )
        ) {
            val engine = engine(rules)
            for (
            host in
            listOf("telemetry.example", "alias.example", "ipv6.example", "ads.example")
            ) {
                assertTrue(host, blocked(engine, "https://$host/a"))
            }
            assertFalse(blocked(engine, "https://ads.example/allowed"))
        }
        val hosts = engine("0.0.0.0 telemetry.example\nads.example")
        assertTrue(blocked(hosts, "https://ads.example/a"))
        assertFalse(blocked(hosts, "https://publisher.example/ads.example.js"))
    }

    @Test
    fun cssColonsAndSpacesAreNotMisclassifiedAsHostsRecords() {
        val generic = "a[href^=\"https://affiliate.example/click/\"][href*=\"/adref:\"] > img"
        val specific = "[style=\"height:120px;padding:10px 0;\"]"
        val rules =
            "##$generic\nsite.example##$specific\n" +
                "site.example#?#div:has(span:has-text(Sponsored content))"
        val engine = engine(rules)
        engine.validate(rules)
        assertEquals(
            setOf(generic, specific),
            engine.cosmeticSelectors("https://site.example/", emptyList(), emptyList()).toSet(),
        )
        assertEquals(
            listOf(generic),
            engine.cosmeticSelectors("https://other.example/", emptyList(), emptyList()),
        )
        assertEquals(1, engine.unsupportedRuleCount())
        // Hosts parsing still validates real address tokens and preserves aliases.
        assertThrows(IllegalArgumentException::class.java) {
            engine.validate("2001:db8:::1 ads.example")
        }
        assertThrows(IllegalArgumentException::class.java) {
            engine.validate("2001:db8:invalid::1 ads.example")
        }
        assertThrows(IllegalArgumentException::class.java) {
            engine.validate("999.0.0.1 ads.example")
        }
        engine.validate("::1 ads.example alias.example\n$rules")
    }

    @Test
    fun hostnameAndIpPrefixesKeepTheirLiteralDotsAndDomainAnchor() {
        val rules = "||142.91.159.\n||ads."
        val engine = engine(rules)
        engine.validate(rules)
        for (
        url in
        listOf(
            "https://142.91.159.1/a",
            "http://142.91.159.254:8080/a",
            "https://ads.example/a",
        )
        ) {
            assertTrue(url, blocked(engine, url))
        }
        for (
        url in
        listOf(
            "https://142.91.15.1/a",
            "https://142.91.160.1/a",
            "https://ads123.example/a",
            "https://notads.example/a",
            "https://site.example/142.91.159.1",
            "https://ads.example@site.example/a",
        )
        ) {
            assertFalse(url, blocked(engine, url))
        }
        assertThrows(IllegalArgumentException::class.java) { engine.validate("||142.91.159.^") }
        assertThrows(IllegalArgumentException::class.java) { engine.validate("||bad..example^") }
        assertThrows(IllegalArgumentException::class.java) { engine.validate("||bad..example") }
        assertThrows(IllegalArgumentException::class.java) { engine.validate("||bad.example..^") }
    }

    @Test
    fun optionsOnlyRulesKeepTypePartyDomainAndHidingRestrictions() {
        val rules = "\$third-party,xmlhttprequest,domain=site.example"
        val engine = engine(rules)
        engine.validate(rules)
        assertTrue(
            blocked(engine, "https://cdn.example/a", "https://site.example/", "xmlhttprequest"),
        )
        assertFalse(blocked(engine, "https://cdn.example/a", "https://site.example/", "image"))
        assertFalse(
            blocked(engine, "https://site.example/a", "https://site.example/", "xmlhttprequest"),
        )
        assertFalse(
            blocked(engine, "https://cdn.example/a", "https://other.example/", "xmlhttprequest"),
        )
        val hiding = engine("##.ad\nsite.example##.sponsor\n@@\$generichide,domain=site.example")
        hiding.validate("@@\$generichide,domain=site.example")
        assertEquals(
            listOf(".sponsor"),
            hiding.cosmeticSelectors("https://site.example/", listOf("ad"), emptyList()),
        )
        assertEquals(
            listOf(".ad"),
            hiding.cosmeticSelectors("https://other.example/", listOf("ad"), emptyList()),
        )
        val unknown = engine("\$image,future-option")
        assertFalse(blocked(unknown, "https://cdn.example/a", type = "image"))
        assertEquals(1, unknown.unsupportedRuleCount())
        assertThrows(IllegalArgumentException::class.java) { engine.validate("@@") }
        assertThrows(IllegalArgumentException::class.java) { engine.validate("\$domain=") }
    }

    @Test
    fun domainAnchorsDoNotMatchCredentialsPathsOrSimilarHostnames() {
        val engine = engine("||ads.example^")
        for (
        url in
        listOf(
            "https://ads.example.evil.test/a",
            "https://notads.example/a",
            "https://publisher.example/ads.example/a",
            "https://ads.example@publisher.example/a",
        )
        ) {
            assertFalse(url, blocked(engine, url))
        }
        assertTrue(blocked(engine, "https://sub.ads.example:8443/a"))
    }

    @Test
    fun cosmeticSelectorsHonorDomainsExceptionsAndObservedGenericTokens() {
        val engine =
            engine(
                "##.ad\n###advert\npublisher.example##.sponsor\n" +
                    "publisher.example#@#.ad\nother.example##.other",
            )
        assertTrue(engine.hasCosmeticRules())
        assertEquals(
            setOf(".sponsor", "#advert"),
            engine
                .cosmeticSelectors(
                    "https://publisher.example/",
                    listOf("ad"),
                    listOf("advert"),
                )
                .toSet(),
        )
        assertEquals(
            setOf(".ad", ".other"),
            engine.cosmeticSelectors("https://other.example/", listOf("ad"), emptyList()).toSet(),
        )
        assertTrue(
            engine
                .cosmeticSelectors("https://unrelated.example/", emptyList(), emptyList())
                .isEmpty(),
        )
    }

    @Test
    fun genericAndAllHidingExceptionsRespectUrlAndBadfilter() {
        val engine =
            engine(
                "##.ad\npublisher.example##.sponsor\n" +
                    "@@||publisher.example/no-generic\$generichide\n" +
                    "@@||publisher.example/no-hiding\$elemhide",
            )
        assertEquals(
            setOf(".sponsor"),
            engine
                .cosmeticSelectors(
                    "https://publisher.example/no-generic",
                    listOf("ad"),
                    emptyList(),
                )
                .toSet(),
        )
        assertTrue(
            engine
                .cosmeticSelectors("https://publisher.example/no-hiding", listOf("ad"), emptyList())
                .isEmpty(),
        )
        val disabled =
            engine(
                "##.ad\n@@||publisher.example^\$generichide\n" +
                    "@@||publisher.example^\$badfilter,generichide",
            )
        assertEquals(
            listOf(".ad"),
            disabled.cosmeticSelectors("https://publisher.example/", listOf("ad"), emptyList()),
        )
    }

    @Test
    fun selectorListsAndComplexSelectorsCannotBeMissedByTokenIndexing() {
        val engine = engine("##.ad, .sponsor\n##div[data-ad]\n##.banner > .inner\n##.first.second")
        assertEquals(
            setOf(".ad, .sponsor", "div[data-ad]"),
            engine
                .cosmeticSelectors("https://site.example/", listOf("sponsor"), emptyList())
                .toSet(),
        )
        assertTrue(
            engine
                .cosmeticSelectors("https://site.example/", listOf("banner", "first"), emptyList())
                .containsAll(listOf(".banner > .inner", ".first.second")),
        )
    }

    @Test
    fun cosmeticDelimitersInsideSelectorsRemainLiteral() {
        val selector = "a[href*=\"#@#\"]"
        val engine = engine("site.example##$selector\nallowed.site.example#@#$selector")
        assertEquals(
            listOf(selector),
            engine.cosmeticSelectors("https://site.example/", emptyList(), emptyList()),
        )
        assertTrue(
            engine
                .cosmeticSelectors("https://allowed.site.example/", emptyList(), emptyList())
                .isEmpty(),
        )
    }

    @Test
    fun cosmeticExceptionsRespectPositiveAndNegativeScopes() {
        val engine = engine("##.ad\nsite.example,~ads.site.example#@#.ad\n~other.example#@#.ad")
        assertTrue(
            engine.cosmeticSelectors("https://site.example/", listOf("ad"), emptyList()).isEmpty(),
        )
        assertTrue(
            engine
                .cosmeticSelectors("https://ads.site.example/", listOf("ad"), emptyList())
                .isEmpty(),
        )
        assertEquals(
            listOf(".ad"),
            engine.cosmeticSelectors("https://other.example/", listOf("ad"), emptyList()),
        )
    }

    @Test
    fun unsupportedCosmeticActionsAreNeverExposedToThePage() {
        val engine =
            engine(
                "||ads.example^\nsite.example##+js(set, foo, bar)\n" +
                    "site.example##.ad:style(color:red)\n" +
                    "site.example##.ad:has-text(Ads)\nsite.example##.ad:remove()",
            )
        assertFalse(engine.hasCosmeticRules())
        assertTrue(
            engine.cosmeticSelectors("https://site.example/", listOf("ad"), emptyList()).isEmpty(),
        )
        assertEquals(4, engine.unsupportedRuleCount())
    }

    @Test
    fun selectorRepliesAreBoundedAndDisablingClearsTheEngine() {
        val engine = engine((0 until 3000).joinToString("\n") { "site.example##.ad-$it" })
        assertEquals(
            2048,
            engine.cosmeticSelectors("https://site.example/", emptyList(), emptyList()).size,
        )
        engine.clear()
        assertFalse(engine.hasCosmeticRules())
        assertTrue(
            engine.cosmeticSelectors("https://site.example/", listOf("ad"), emptyList()).isEmpty(),
        )
        assertFalse(blocked(engine, "https://ads.example/a"))
    }

    @Test
    fun validationReportsOriginalLinesAndBoundsDiagnostics() {
        val engine = KotlinFilterEngine()
        val error =
            assertThrows(IllegalArgumentException::class.java) {
                engine.validate(
                    "\uFEFF! header\r\n\n||ads.example^\$domain=\n" +
                        (0 until 100).joinToString("\n") { "||" },
                )
            }
        assertTrue(error.message!!.contains("Line 3:"))
        assertTrue(error.message!!.contains("more errors"))
        assertTrue(error.message!!.length < 1000)
        assertThrows(IllegalArgumentException::class.java) {
            engine.validate("999.0.0.1 ads.example")
        }
        assertThrows(IllegalArgumentException::class.java) {
            engine.validate("||ads.example^\n! comment\u0000")
        }
    }

    @Test
    fun emptyUnsupportedAndUsefulMixedListsHaveDistinctValidationResults() {
        val engine = KotlinFilterEngine()
        for (text in listOf(" ", "! comment\n# comment\n[Adblock Plus 2.0]")) {
            assertTrue(
                assertThrows(IllegalArgumentException::class.java) { engine.validate(text) }
                    .message!!
                    .contains("no rules"),
            )
        }
        for (
        text in
        listOf(
            "site.example##+js(set, foo, bar)",
            "||ads.example^\$future-option",
            "/ads[0-9]+/",
        )
        ) {
            assertTrue(
                assertThrows(IllegalArgumentException::class.java) { engine.validate(text) }
                    .message!!
                    .contains("no supported"),
            )
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
            val readers =
                (0 until 3).map {
                    executor.submit(
                        Callable {
                            repeat(1000) {
                                assertFalse(blocked(engine, "https://unrelated.example/a"))
                            }
                        },
                    )
                }
            repeat(50) {
                engine.replace(listOf(if (it % 2 == 0) "||ads.example^" else "||tracker.example^"))
            }
            readers.forEach { it.get() }
        } finally {
            executor.shutdownNow()
        }
    }
}
