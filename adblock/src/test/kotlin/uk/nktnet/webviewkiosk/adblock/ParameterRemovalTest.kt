package uk.nktnet.webviewkiosk.adblock

import java.io.File
import java.io.RandomAccessFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ParameterRemovalTest {
    @get:Rule val folder = TemporaryFolder()

    private fun engine(vararg rules: String) =
        KotlinFilterEngine().also {
            it.replace(rules.toList())
        }

    private fun clean(engine: KotlinFilterEngine, url: String): String =
        engine.removeQueryParameters(url, "", "document", "GET")

    @Test
    fun removalDoesNotBlockAndExceptionsDoNotWhitelistRequests() {
        val url = "https://site.example/?track=1"
        val engine = engine("*\$removeparam=track")
        assertEquals(
            BlockingResponse.ALLOW,
            engine.responseFor(url, "", "doc", "GET"),
        )
        assertEquals("https://site.example/", clean(engine, url))
        engine.replace(listOf("||site.example^\$doc", "@@*\$removeparam=track"))
        assertEquals(
            BlockingResponse.BLOCK,
            engine.responseFor(url, "", "doc", "GET"),
        )
        assertEquals(url, clean(engine, url))
    }

    @Test
    fun literalRemovalPreservesRetainedBytesOrderDuplicatesAndFragment() {
        val engine = engine("*\$removeparam=track")
        assertEquals(
            "https://site.example/?sig=a%2fb+z&empty=&dup=1&dup=2#?track=kept",
            clean(
                engine,
                "https://site.example/?track=one&sig=a%2fb+z&track=two&" +
                    "empty=&dup=1&dup=2#?track=kept",
            ),
        )
        assertEquals(
            "https://site.example/#hash",
            clean(
                engine,
                "https://site.example/?track&track=#hash",
            ),
        )
        val untouched = "https://site.example/?Track=1&%74rack=2#track=3"
        assertEquals(untouched, clean(engine, untouched))
        assertEquals(
            "https://site.example/#?track=1",
            clean(engine, "https://site.example/#?track=1"),
        )
    }

    @Test
    fun unicodeLiteralNamesDoNotUseTokensFromTheirDifferentUrlEncoding() {
        assertEquals(
            "https://site.example/?keep=1",
            clean(
                engine("*\$removeparam=跟踪参数"),
                "https://site.example/?跟踪参数=2&keep=1",
            ),
        )
    }

    @Test
    fun regularExpressionsMatchRawKeysAndDecodedValuesWithFlagsAndDollarAnchors() {
        val engine =
            engine(
                "*\$removeparam=/^utm_/i\n" +
                    "*\$removeparam=/^ref=hello world\$/\n" +
                    "*\$removeparam=/^code=[0-9]{2\\,3}\$/\n" +
                    "*\$removeparam=/^plus=a\\+b\$/"
            )
        assertEquals(0, engine.unsupportedRuleCount())
        assertEquals(
            "https://site.example/?ref=hello+world&code=1&keep=%2F",
            clean(
                engine,
                "https://site.example/?UTM_source=a&ref=hello%20world&ref=hello+world&" +
                    "code=12&code=1&plus=a+b&keep=%2F",
            ),
        )
        val malformed = "https://site.example/?ref=%FF%20world&ref=hello%2"
        assertEquals(malformed, clean(engine, malformed))
    }

    @Test
    fun negationRemoveAllAndLegacyPrefixAndAliasAreSupported() {
        val url = "https://site.example/?keep=1&track=2&UTM_source=3#x"
        assertEquals(
            "https://site.example/?keep=1#x",
            clean(engine("*\$removeparam=~keep"), url),
        )
        assertEquals(
            "https://site.example/?keep=1#x",
            clean(engine("*\$removeparam=~/^keep=/"), url),
        )
        assertEquals(
            "https://site.example/?keep=1&track=2#x",
            clean(engine("*\$queryprune=|utm_"), url),
        )
        assertEquals("https://site.example/#x", clean(engine("*\$removeparam"), url))
        assertEquals(
            "https://site.example/?",
            clean(
                engine("*\$removeparam"),
                "https://site.example/?",
            ),
        )
    }

    @Test
    fun exceptionsCancelMatchingModifierValuesAndImportantSurvivesExceptAll() {
        val url = "https://site.example/?a=1&b=2&c=3"
        val cases =
            listOf(
                listOf(
                    "*\$removeparam=a",
                    "*\$removeparam=b",
                    "@@*\$removeparam=a",
                ) to "https://site.example/?a=1&c=3",
                listOf("*\$removeparam=a", "*\$removeparam=b", "@@*\$removeparam") to
                    url,
                listOf("*\$removeparam=a,important", "@@*\$removeparam=a") to
                    "https://site.example/?b=2&c=3",
                listOf(
                    "*\$removeparam=a,important",
                    "*\$removeparam=b",
                    "@@*\$removeparam",
                ) to "https://site.example/?b=2&c=3",
                // A name exception cancels that modifier, not a different regex.
                listOf("*\$removeparam=/^[ab]=/", "@@*\$removeparam=a") to
                    "https://site.example/?c=3",
            )
        for ((rules, expected) in cases) {
            for (ordered in listOf(rules, rules.reversed())) {
                assertEquals(
                    ordered.toString(),
                    expected,
                    clean(engine(*ordered.toTypedArray()), url),
                )
            }
        }
    }

    @Test
    fun conditionsUseDestinationContextForDocumentsAndActualResourceTypes() {
        val engine =
            engine(
                "||site.example/path\$doc,removeparam=track,domain=site.example," +
                    "~third-party,method=GET\n" +
                    "||site.example/other\$script,removeparam=track"
            )
        val url = "https://site.example/path?track=1&keep=2"
        assertEquals("https://site.example/path?keep=2", clean(engine, url))
        assertEquals(url, engine.removeQueryParameters(url, "", "doc", "POST"))
        assertEquals(
            url,
            engine.removeQueryParameters(
                url,
                "https://site.example/",
                "image",
                "GET",
            ),
        )
        val other = "https://site.example/other?track=1"
        assertEquals(other, clean(engine, other))
        assertEquals(
            "https://site.example/other",
            engine.removeQueryParameters(
                other,
                "https://site.example/",
                "script",
                "GET",
            ),
        )
        assertEquals("file:///tmp/x?track=1", clean(engine, "file:///tmp/x?track=1"))
    }

    @Test
    fun networkRegexAndModifierRegexHaveIndependentDelimiters() {
        val engine =
            engine(
                "/site\\.example\\/path/\$removeparam=/^track=[0-9]\$/,_\n" +
                    "/ad-\$removeparam=/^foo=bar\\d\$/,_"
            )
        assertEquals(0, engine.unsupportedRuleCount())
        assertEquals(
            "https://site.example/path?keep=2",
            clean(engine, "https://site.example/path?track=1&keep=2"),
        )
        assertEquals(
            "https://site.example/ad-?foo=baz",
            clean(engine, "https://site.example/ad-?foo=bar1&foo=baz"),
        )
    }

    @Test
    fun unsupportedModifiersAndRegexesDiscardTheWholeRule() {
        val rules =
            listOf(
                "*\$removeparam=track,unknown",
                "*\$removeparam=track,redirect=noopjs",
                "@@*\$removeparam=track,elemhide",
                "*\$removeparam=track,removeparam=other",
                "*\$~removeparam=track",
                "*\$removeparam=/(a)\\1/",
                "*\$removeparam=/a{999999}/",
                "*\$removeparam=a|b",
            )
        val engine = engine(*rules.toTypedArray())
        assertEquals(rules.size, engine.unsupportedRuleCount())
        val url = "https://site.example/?track=1"
        assertEquals(url, clean(engine, url))
        assertFalse(engine.shouldBlock(url, "", "doc", "GET"))
    }

    @Test
    fun globalLiteralRulesAreIndexedAndMatchingLimitsFailOpen() {
        val rules = (0 until 3000).joinToString("\n") { "*\$removeparam=param$it" }
        assertEquals(
            "https://site.example/?keep=2",
            clean(
                engine(rules),
                "https://site.example/?param2500=1&keep=2",
            ),
        )
        val expensive = engine("*\$removeparam=/" + "a".repeat(2000) + "/")
        val url = "https://site.example/?track=" + "a".repeat(8000)
        assertEquals(url, clean(expensive, url))
        val tooMany =
            "https://site.example/?" + (0..1024).joinToString("&") { "a=$it" }
        assertEquals(tooMany, clean(engine("*\$removeparam"), tooMany))
        val tooLong = "https://site.example/?a=" + "x".repeat(32768)
        assertEquals(tooLong, clean(engine("*\$removeparam"), tooLong))
    }

    @Test
    fun warmAndIncrementalCachesPreserveModifiersExceptionsAndBadfilter() {
        val file = File(folder.root, "rules.bin")
        val base = "*\$removeparam=/^utm_/\n*\$removeparam=track\n*\$removeparam=old"
        val exceptions =
            "@@||site.example^\$removeparam=track\n" + "*\$removeparam=old,badfilter"
        val lists = listOf(base, exceptions)
        KotlinFilterEngine(file).replace(lists)
        val restored = KotlinFilterEngine(file)
        val stages = mutableListOf<RuleLoadStage>()
        restored.replace(lists, stages::add)
        assertFalse(RuleLoadStage.COMPILING in stages)
        val url = "https://site.example/?utm_source=1&track=2&old=3"
        assertEquals("https://site.example/?track=2&old=3", clean(restored, url))
        stages.clear()
        restored.replace(listOf(base), stages::add)
        assertFalse(RuleLoadStage.COMPILING in stages)
        assertEquals("https://site.example/", clean(restored, url))
    }

    @Test
    fun schemaTenRecordsAreRecompiledInAggregateAndPerListCaches() {
        val file = File(folder.root, "rules.bin")
        val lists = listOf("*\$removeparam=track")
        KotlinFilterEngine(file).replace(lists)
        (listOf(file) + File(folder.root, "rules.bin.sources").listFiles().orEmpty())
            .filter { it.isFile }
            .forEach {
                RandomAccessFile(it, "rw").use { old ->
                    old.seek(4)
                    old.writeInt(10)
                }
            }
        val restored = KotlinFilterEngine(file)
        val stages = mutableListOf<RuleLoadStage>()
        restored.replace(lists, stages::add)
        assertTrue(RuleLoadStage.COMPILING in stages)
        assertEquals(
            "https://site.example/",
            clean(restored, "https://site.example/?track=1"),
        )
    }

    @Test
    fun matchesUpstreamQueryModifierFixturesInEitherListOrder() {
        val fixtures =
            javaClass
                .getResourceAsStream("/adblock/removeparam-ubo.json")!!
                .bufferedReader()
                .use { Json.parseToJsonElement(it.readText()) }
                .jsonObject
                .getValue("cases")
                .jsonArray
        fixtures.forEach { entry ->
            val case = entry.jsonObject
            fun field(name: String) = case.getValue(name).jsonPrimitive.content
            val rules =
                case.getValue("rules").jsonArray.map { it.jsonPrimitive.content }
            for (ordered in listOf(rules, rules.reversed())) {
                assertEquals(
                    field("name"),
                    field("expected"),
                    engine(*ordered.toTypedArray())
                        .removeQueryParameters(
                            field("url"),
                            case["sourceUrl"]?.jsonPrimitive?.content.orEmpty(),
                            field("type"),
                            field("method"),
                        ),
                )
            }
        }
    }
}
