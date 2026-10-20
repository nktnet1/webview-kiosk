package uk.nktnet.webviewkiosk.adblock

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import uk.nktnet.webviewkiosk.adblock.engine.CompiledFilterCache
import uk.nktnet.webviewkiosk.adblock.engine.FilterCompiler
import uk.nktnet.webviewkiosk.adblock.engine.JsonEditing
import uk.nktnet.webviewkiosk.adblock.engine.NetworkRouting
import uk.nktnet.webviewkiosk.adblock.engine.ScriptletIndex
import uk.nktnet.webviewkiosk.adblock.engine.ScriptletParser

class WebViewCompatibilityTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun allMatchesEveryNativeTypeAndFullDocumentPathsWithExceptionsAndBadfilters() {
        val engine = KotlinFilterEngine()
        assertEquals(1, inspectFilterList($$"||site.example^$all").partialRules)
        assertEquals(0, inspectFilterList($$"||site.example^$script").partialRules)
        engine.replace(
            listOf(
                $$"||site.example/blocked$all\n" +
                    $$"@@||site.example/blocked/safe$all\n" +
                    $$"||site.example/disabled$all\n||site.example/disabled$all,badfilter"
            )
        )
        for (type in
            listOf(
                "other",
                "script",
                "image",
                "stylesheet",
                "font",
                "media",
                "subdocument",
                "xmlhttprequest",
                "object",
                "ping",
                "websocket",
                "document",
            )) {
            assertTrue(
                type,
                engine.shouldBlock(
                    "https://site.example/blocked",
                    "https://other.example/",
                    type,
                    "GET",
                ),
            )
            assertFalse(
                type,
                engine.shouldBlock(
                    "https://site.example/blocked/safe",
                    "https://other.example/",
                    type,
                    "GET",
                ),
            )
            assertFalse(
                type,
                engine.shouldBlock(
                    "https://site.example/disabled",
                    "https://other.example/",
                    type,
                    "GET",
                ),
            )
        }
        assertFalse(
            engine.shouldBlock(
                "https://site.example/unrelated",
                "",
                "document",
                "GET",
            )
        )
        for (option in
            listOf(
                "~all",
                "all=1",
                "all,popup",
                "all,inline-script",
                "all,csp=script-src 'none'",
            )) {
            assertEquals(
                option,
                1,
                inspectFilterList($$"||site.example^$$$option").unsupportedRules,
            )
        }
        engine.replace(listOf($$"||site.example/blocked$all,~document"))
        assertFalse(
            engine.shouldBlock("https://site.example/blocked", "", "document", "GET")
        )
        assertTrue(
            engine.shouldBlock(
                "https://site.example/blocked",
                "https://other.example/",
                "script",
                "GET",
            )
        )
    }

    @Test
    fun extendedScopesUseFrameUrlsPathsEntitiesAndKnownAncestorOrigins() {
        val rules =
            listOf(
                "site.example/path##+js(set, pathMarker, false)",
                "shop.*/checkout##+js(set, entityMarker, false)",
                "/^img[0-9]{1,3}\\.example$/##+js(set, regexMarker, false)",
                "/^site\\.example\\/promo[0-9]/##+js(set, urlMarker, false)",
                "parent.example>>##+js(set, frameMarker, false)",
                "site.example/path/safe#@#+js(set, pathMarker, false)",
                "site.example,~/^safe\\.site\\.example$/##+js(set, mixedMarker, false)",
            )
        val file = File(folder.root, "rules.bin")
        val engine = KotlinFilterEngine(file)
        engine.replace(rules)
        fun markers(url: String, ancestors: List<String> = emptyList()) =
            engine.scriptlets(url, ancestors).map { it.arguments.first() }.toSet()
        assertEquals(
            setOf("pathMarker", "mixedMarker"),
            markers("https://site.example:8443/path?q=1"),
        )
        assertEquals(setOf("mixedMarker"), markers("https://site.example/path/safe"))
        assertEquals(
            setOf("entityMarker"),
            markers("https://www.shop.co.uk/checkout"),
        )
        assertEquals(setOf("regexMarker"), markers("https://img123.example/"))
        assertTrue(markers("https://img1234.example/").isEmpty())
        assertEquals(
            setOf("urlMarker", "mixedMarker"),
            markers("https://site.example:8443/promo7"),
        )
        assertEquals(
            setOf("frameMarker"),
            markers("https://child.example/", listOf("https://parent.example/")),
        )
        assertTrue(markers("https://child.example/").isEmpty())
        assertTrue(markers("https://safe.site.example/").isEmpty())
        val restored = KotlinFilterEngine(file)
        val stages = mutableListOf<RuleLoadStage>()
        restored.replace(rules, stages::add)
        assertFalse(RuleLoadStage.COMPILING in stages)
        assertEquals(engine.scriptletInjection(), restored.scriptletInjection())
        System.getenv("SCRIPTLET_OUTPUT_DIR")?.let { output ->
            File(output).mkdirs()
            File(output, "extended-injection.js")
                .writeText(engine.scriptletInjection()!!.source)
        }
    }

    @Test
    fun lazyRoutingAcceptsFormerSubscriptionAndPageLimitsAndGuardsExcesses() {
        val engine = KotlinFilterEngine()
        engine.replace(
            listOf(
                (0 until 17_000).joinToString("\n") {
                    "site$it.example##+js(set, marker, false)"
                }
            )
        )
        assertEquals(1, engine.scriptlets("https://site16999.example/").size)
        assertTrue(engine.scriptlets("https://unrelated.example/").isEmpty())
        val source = engine.scriptletInjection()!!.source
        assertFalse(source.contains("JSON.parse(serialized)"))
        engine.replace(
            listOf(
                (0 until 1100).joinToString("\n") {
                    "site.example##+js(set, marker$it, false)"
                }
            )
        )
        assertEquals(1024, engine.scriptlets("https://site.example/").size)
    }

    @Test
    fun jsonEditingCompilesOnlySupportedDataQueriesAndRevalidatesCachedPrograms() {
        val queries =
            listOf(
                "..ads",
                "$.ads",
                "$.items[*].ad",
                "$.items[-1]",
                "$.items[?@.advert]",
                "v2:$.items[?@.kind==\"ad\"]",
                "$.items[0,2]",
                "$.items[?@.value>=3]",
                "$.items[?!@.content]",
                "$['ad-key']",
                "v2:$.items[*]{3};$..ads",
                "v2:$.missing{0};$..ads",
                "v2:$.missing;$..ads",
                "v2:$.items[*]{2,4};$.ads",
                "v2:$.items[*]{,2};$.ads",
                "v2:$.items[*]{3,};$.missing{0};$.ads",
                "v2:$../^ad/",
                "v2:$.items[?@.kind=/^AD$/i]",
                "v2:$.numbers[?@>=2]",
                "v2:$.items[?@.kind=/^ad$/];$.ads",
                "$.numbers./^[12]$/",
            )
        val rules = queries.map {
            "site.example##+js(json-edit, '${it.replace("'", "\\'")}')"
        }
        val file = File(folder.root, "json.bin")
        val engine = KotlinFilterEngine(file)
        engine.replace(rules)
        assertEquals(queries.size, engine.scriptlets("https://site.example/").size)
        val restored = KotlinFilterEngine(file)
        restored.replace(rules)
        assertEquals(engine.scriptletInjection(), restored.scriptletInjection())
        for (query in
            listOf(
                "$.x=1",
                "$.x=call([])",
                "$.x+=1",
                "$.__proto__",
                "$.x[?@.value=/(a+)+$/]",
                "$.x{1}",
                "$.x{2,1};$.y",
                "$.x;@.y",
                "$.x[/^(a+)+$/]",
                "v2:$.x[unquoted]",
                "$.x[unquoted]",
                "v2:$.x[/^ad/]",
                "$.x[/^ad/]",
                "v2:$.x[?!@.kind=/^ad$/]",
                "v2:$.x{0,0};$.ads",
            )) {
            assertNull(query, JsonEditing.compile(query))
        }
        assertNotNull(
            ScriptletParser.parse(
                "+js(jsonl-edit-fetch-response, ..ads, propsToMatch, url:/api, " +
                    "jsonExtract, ^data: (.*)$)"
            )
        )
        val instruction = ScriptletParser.parse("+js(json-edit, ..ads)")!!
        val tampered = instruction.arguments.toMutableList().apply { this[1] = "[]" }
        assertNull(ScriptletParser.validated(instruction.type, tampered))
        System.getenv("SCRIPTLET_OUTPUT_DIR")?.let { output ->
            val cases = queries.map { query ->
                val line =
                    "site.example##+js(json-edit, '${query.replace("'", "\\'")}')"
                val one = KotlinFilterEngine().apply { replace(listOf(line)) }
                val source = ScriptletIndex.json(one.scriptletInjection()!!.source)
                "[${ScriptletIndex.json(query)},$source]"
            }
            File(output, "json-edit-cases.json")
                .writeText(cases.joinToString(",", "[", "]"))
            for (name in
                listOf(
                    "json-edit-fetch-response",
                    "json-edit-xhr-response",
                    "jsonl-edit-fetch-response",
                    "jsonl-edit-xhr-response",
                )) {
                val one =
                    KotlinFilterEngine().apply {
                        replace(
                            listOf(
                                "site.example##+js($name, ..ads, propsToMatch, url:/api)"
                            )
                        )
                    }
                File(output, "$name.js").writeText(one.scriptletInjection()!!.source)
            }
        }
    }

    @Test
    fun unknownInitiatorsKeepOnlyContextFreeBlockingAndPotentialContextualExceptions() {
        val engine = KotlinFilterEngine()
        engine.replace(
            listOf(
                "||ads.example^\n" +
                    $$"||context.example^$third-party\n" +
                    $$"||scoped.example^$domain=publisher.example\n" +
                    $$"@@||ads.example/login$domain=publisher.example"
            )
        )
        assertTrue(
            engine.shouldBlock("https://ads.example/track", "", "script", "GET")
        )
        assertFalse(
            engine.shouldBlock("https://ads.example/login", "", "script", "GET")
        )
        assertFalse(
            engine.shouldBlock("https://context.example/", "", "script", "GET")
        )
        assertFalse(
            engine.shouldBlock("https://scoped.example/", "", "script", "GET")
        )
        assertTrue(
            engine.shouldBlock(
                "https://scoped.example/",
                "https://publisher.example/",
                "script",
                "GET",
            )
        )
    }

    @Test
    fun listenerOptionsAreCanonicalBoundedAndRevalidatedAfterCacheRestoration() {
        val expectedBasic = ScriptletParser.parse("+js(aeld, click)")!!
        // The canonical parsed form is expected; the option-rich form is actual.
        //noinspection KotlinMisorderedAssertEqualsArguments
        assertEquals(
            ScriptletParser.parse("+js(aeld, click, , runAt, loading)"),
            expectedBasic,
        )
        val text =
            "site.example##+js(aeld, click, advert, protect, 1, " +
                "elements, .ad, runAt, complete)"
        val file = File(folder.root, "listener.bin")
        val engine = KotlinFilterEngine(file)
        engine.replace(listOf(text))
        val restored = KotlinFilterEngine(file)
        restored.replace(listOf(text))
        assertEquals(engine.scriptletInjection(), restored.scriptletInjection())
        assertEquals(
            listOf(
                "click",
                "advert",
                "runAt",
                "complete",
                "elements",
                ".ad",
                "protect",
                "1",
            ),
            restored.scriptlets("https://site.example/").single().arguments,
        )
        for (options in
            listOf(
                "runAt, idle",
                "protect, yes",
                "elements, :scope > body",
                "elements, .ad, elements, .other",
                "unknown, 1",
            )) {
            assertNull(
                options,
                ScriptletParser.parse("+js(aeld, click, , $options)"),
            )
        }
        System.getenv("SCRIPTLET_OUTPUT_DIR")?.let { output ->
            File(output).mkdirs()
            File(output, "listener-injection.js")
                .writeText(engine.scriptletInjection()!!.source)
        }
    }

    @Test
    fun newShimsRetainTheirRedirectAndInjectionEligibility() {
        val text =
            "site.example##+js(google-ima3)\n" +
                $$"||ima.example^$script,redirect=google-ima3\n" +
                $$"||piano.example^$script,redirect=piano-analytics.js\n" +
                "site.example##+js(piano-analytics)"
        val engine = KotlinFilterEngine()
        engine.replace(listOf(text))
        assertEquals(1, engine.unsupportedRuleCount())
        assertEquals(
            listOf("google-ima.js"),
            engine.scriptlets("https://site.example/").single().arguments,
        )
        for ((url, expected) in
            listOf(
                "https://ima.example/script.js" to BlockingResponse.GOOGLE_IMA,
                "https://piano.example/script.js" to
                    BlockingResponse.PIANO_ANALYTICS,
            )) {
            assertEquals(
                expected,
                engine.responseFor(url, "https://site.example/", "script", "GET"),
            )
            assertNotNull(
                javaClass.getResourceAsStream(
                    "/adblock/ubo/${expected.resourcePath}"
                )
            )
        }
    }

    @Test
    fun aggregateSnapshotsRestorePortableBucketIdsAndSourceShardsRemainIndependent() {
        val rules =
            FilterCompiler.parse(
                listOf(
                    "||ads.example^\n" +
                        $$"||cdn.example/ad$script\n/api-ad$xhr,domain=site.example\n" +
                        $$"banner-token$image\n@@/api-ad$xhr,domain=safe.site.example\n" +
                        $$"||cdn.example/redirect$redirect=noop.js\n" +
                        $$"@@||cdn.example/redirect$redirect-rule\n" +
                        $$"||cdn.example^$removeparam=track"
                )
            )
        val cache = CompiledFilterCache(File(folder.root, "index.bin"))
        val key = cache.key(listOf("index test"))
        cache.write(key, rules, rules.build().networkIndexes)
        val restored = cache.read(key)!!
        assertEquals(rules.build().networkIndexes, restored.networkIndexes)
        assertEquals(rules.build().networkIndexes, restored.build().networkIndexes)
        val snapshot = rules.build().networkIndexes
        val invalid =
            NetworkRouting(
                snapshot.requests.ruleCount,
                mapOf("cdn.example" to intArrayOf(snapshot.requests.ruleCount)),
                emptyMap(),
                emptyMap(),
                emptyMap(),
                intArrayOf(),
            )
        cache.write(key, rules, snapshot.copy(requests = invalid))
        assertNull(cache.read(key))
        cache.write(key, rules, snapshot)
        val data = File(folder.root, "index.bin").readBytes()
        File(folder.root, "index.bin").writeBytes(data.copyOf(data.size - 9))
        assertNull(cache.read(key))
    }
}
