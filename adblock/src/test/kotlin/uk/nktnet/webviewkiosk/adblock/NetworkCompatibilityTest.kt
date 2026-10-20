package uk.nktnet.webviewkiosk.adblock

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NetworkCompatibilityTest {
    @get:Rule val folder = TemporaryFolder()

    private fun blocked(
        engine: KotlinFilterEngine,
        url: String,
        type: String = "script",
    ) = engine.shouldBlock(url, "https://site.example/", type, "GET")

    @Test
    fun regexFiltersKeepAnchorsOptionsExceptionsCaseAndCacheIdentity() {
        val text =
            $$"/^https:\\/\\/ads[0-9]+\\.example\\/pixel/$image,third-party\n" +
                $$"@@/allowed[0-9]+/$image\n" +
                $$"/CaseSensitive/$script,match-case"
        val file = File(folder.root, "rules.bin")
        KotlinFilterEngine(file).replace(listOf(text))
        val engine = KotlinFilterEngine(file)
        engine.replace(listOf(text))
        assertEquals(0, engine.unsupportedRuleCount())
        assertTrue(blocked(engine, "https://ads12.example/pixel", "image"))
        assertFalse(blocked(engine, "https://ads12.example/pixel", "script"))
        assertFalse(blocked(engine, "https://ads12.example/pixel?allowed1", "image"))
        assertFalse(
            blocked(engine, "https://other.example/ads12.example/pixel", "image")
        )
        assertTrue(blocked(engine, "https://cdn.example/CaseSensitive"))
        assertFalse(blocked(engine, "https://cdn.example/casesensitive"))
    }

    @Test
    fun unsupportedRegexFeaturesAreSkippedAndNestedRepeatsDoNotBacktrack() {
        val engine = KotlinFilterEngine()
        engine.replace(listOf("/(?=ads)ads/\n/(a+)\\1/\n/(a+)+b$/\n/(a{64}){64}/"))
        assertEquals(3, engine.unsupportedRuleCount())
        assertFalse(blocked(engine, "https://cdn.example/" + "a".repeat(8000)))
        assertTrue(blocked(engine, "https://cdn.example/aaaaab"))
    }

    @Test
    fun wildcardHostnameAnchorsAndLiteralPipesRetainTheirUrlConstraints() {
        val engine = KotlinFilterEngine()
        engine.replace(
            listOf(
                "||geotargetly-api-*.com^\n" +
                    "||execute-api.*.amazonaws.com/forms-impressions\n" +
                    "/addyn|*;adtech;\n||api-v4.trbo.com*plvid="
            )
        )
        assertEquals(0, engine.unsupportedRuleCount())
        assertTrue(blocked(engine, "https://geotargetly-api-12.com/pixel"))
        assertTrue(
            blocked(
                engine,
                "https://execute-api.eu-west.amazonaws.com/forms-impressions",
            )
        )
        assertFalse(
            blocked(engine, "https://other.example/path?geotargetly-api-12.com")
        )
        assertFalse(
            blocked(engine, "https://geotargetly-api-12.com.evil.example/pixel")
        )
        assertTrue(blocked(engine, "https://api-v4.trbo.com/path?plvid=123"))
        assertTrue(blocked(engine, "https://cdn.example/addyn|zone;adtech;"))
    }

    @Test
    fun badwareHostPrefixesCanEndInHyphensButCompleteHostnameLabelsCannot() {
        val rules =
            $$"||air-bnb.check-$doc\n" +
                $$"||chrome-chrome-$doc\n" +
                $$"||claude-code-$doc,to=edgeone.app|pages.dev|squarespace.com"
        val file = File(folder.root, "badware-prefix.bin")
        val source = FilterSource(rules, trusted = true)
        val engine = KotlinFilterEngine(file)
        engine.validateSource(source)
        engine.replaceSources(listOf(source)) {}
        val restored = KotlinFilterEngine(file)
        restored.replaceSources(listOf(source)) {}
        assertEquals(0, restored.unsupportedRuleCount())
        assertTrue(
            blocked(
                restored,
                "https://air-bnb.check-verification.example/",
                "document",
            )
        )
        assertTrue(
            blocked(restored, "https://chrome-chrome-download.example/", "document")
        )
        assertTrue(
            blocked(restored, "https://claude-code-login.pages.dev/", "document")
        )
        assertFalse(
            blocked(restored, "https://claude-code-login.example/", "document")
        )
        assertFalse(blocked(restored, "https://air-bnb.check.example/", "document"))
        assertFalse(
            blocked(restored, "https://site.example/air-bnb.check-fake", "document")
        )
        try {
            engine.validate($$"||invalid-.example^$doc")
            throw AssertionError(
                "An incomplete non-final hostname label must be rejected"
            )
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("invalid host anchor"))
        }
    }

    @Test
    fun redirectExceptionsSuppressSurrogatesWithoutAllowingBlockedRequests() {
        val engine = KotlinFilterEngine()
        engine.replace(
            listOf(
                "||ads.example^\n" +
                    $$"||ads.example^$script,redirect-rule=noopjs\n" +
                    $$"@@||ads.example/excepted$script,redirect-rule"
            )
        )
        assertEquals(0, engine.unsupportedRuleCount())
        assertEquals(
            BlockingResponse.EMPTY_SCRIPT,
            engine.responseFor(
                "https://ads.example/normal",
                "https://site.example/",
                "script",
                "GET",
            ),
        )
        assertEquals(
            BlockingResponse.BLOCK,
            engine.responseFor(
                "https://ads.example/excepted",
                "https://site.example/",
                "script",
                "GET",
            ),
        )
        assertFalse(blocked(engine, "https://unblocked.example/excepted"))
    }

    @Test
    fun multiLabelEntitiesUseEffectivePublicSuffixesAndRespectExceptions() {
        val engine = KotlinFilterEngine()
        engine.replace(
            listOf(
                "www.google.*##.advert\nwww.google.*#@#.excepted\n" +
                    $$"google.*##.excepted\n*$script,domain=read.amazon.*"
            )
        )
        assertEquals(0, engine.unsupportedRuleCount())
        assertEquals(
            listOf(".advert"),
            engine.cosmeticSelectors(
                "https://www.google.co.uk/",
                emptyList(),
                emptyList(),
            ),
        )
        assertTrue(
            engine
                .cosmeticSelectors(
                    "https://www.google.com.evil.test/",
                    emptyList(),
                    emptyList(),
                )
                .isEmpty()
        )
        assertTrue(
            engine.shouldBlock(
                "https://cdn.example/a.js",
                "https://read.amazon.co.uk/",
                "script",
                "GET",
            )
        )
        assertFalse(
            engine.shouldBlock(
                "https://cdn.example/a.js",
                "https://amazon.co.uk/",
                "script",
                "GET",
            )
        )
    }
}
