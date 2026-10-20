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
import uk.nktnet.webviewkiosk.adblock.engine.JsonEditing
import uk.nktnet.webviewkiosk.adblock.engine.ScriptletParser

class RoadmapCompatibilityTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun trustedMutationsAreRecompiledAndNeverGrantedToCustomLists() {
        for (query in listOf("$.x[?$.y]", "$.x['quoted']", "$.x./^ad/")) {
            assertNotNull(query, JsonEditing.compile(query))
        }
        val queries =
            listOf(
                "$.ads=false",
                "$.settings+={\"enabled\":false}",
                "$.label=repl({\"regex\":\"ad\",\"flags\":\"g\",\"replacement\":\"ok\"})",
                $$"$.label=call([\"${obj}\",\"mark\",\"${key}\",\"${val}\"])",
                "$={\"replaced\":true}",
                "$.missing=null",
            )
        for (query in queries) {
            assertNull(query, JsonEditing.compile(query))
            assertNotNull(query, JsonEditing.compile(query, true))
            val expression = "+js(trusted-json-edit, `$query`)"
            assertNull(ScriptletParser.parse(expression))
            val instruction = requireNotNull(ScriptletParser.parse(expression, true))
            assertEquals(
                instruction,
                ScriptletParser.validated(
                    instruction.type,
                    instruction.arguments,
                    true,
                ),
            )
            assertNull(
                ScriptletParser.validated(
                    instruction.type,
                    instruction.arguments.toMutableList().also { it[1] = "[]" },
                    true,
                )
            )
        }
        val cases =
            (queries +
                    listOf(
                        "$.items[0]['advert']",
                        "$.items[0]./^ad/",
                        "v2:$.items[?$.ads==1]",
                        "v2:$.items[*]{3};$.ads=false",
                    ))
                .map { query ->
                    val engine = KotlinFilterEngine()
                    engine.replaceSources(
                        listOf(
                            FilterSource(
                                "site.example##+js(trusted-json-edit, `$query`)",
                                true,
                            )
                        )
                    ) {}
                    val source = engine.scriptletInjection()!!.source
                    "[${uk.nktnet.webviewkiosk.adblock.engine.ScriptletIndex.json(query)}," +
                        "${uk.nktnet.webviewkiosk.adblock.engine.ScriptletIndex.json(source)}]"
                }
        export("json-mutations.json", cases.joinToString(",", "[", "]"))
        for (query in
            listOf(
                "$.x={\"__proto__\":{}}",
                "$.x+=[]",
                "$.x=call([null,\"constructor\"])",
                "$.x=run(1)",
            )) {
            assertNull(query, JsonEditing.compile(query, true))
        }
    }

    @Test
    fun objectAndRequestEditorsRestoreCanonicalArgumentsAndProvenance() {
        val entries =
            listOf(
                "edit-outbound-object, app.read, $.ads",
                "edit-inbound-object, app.write, -1, $.ads",
                "edit-this-object, app.next, $.ads, after",
                "edit-object-on-getter, app.state, $.ads",
                "edit-object-on-setter, app.state, $.ads",
                "trusted-edit-outbound-object, app.read, $.ads=false",
                "trusted-edit-inbound-object, app.write, 0, $.ads=false",
                "trusted-edit-this-object, app.next, $.ads=false",
                "trusted-edit-object-on-getter, app.state, $.ads=false",
                "trusted-edit-object-on-setter, app.state, $.ads=false",
                "json-edit-fetch-request, $.ads, propsToMatch, method:POST",
                "json-edit-xhr-request, $.ads",
                "trusted-json-edit-fetch-request, $.ads=false",
                "trusted-json-edit-xhr-request, $.ads=false",
                "trusted-json-edit-fetch-response, $.ads=false",
                "trusted-json-edit-xhr-response, $.ads=false",
                "trusted-jsonl-edit-fetch-response, $.ads=false",
                "trusted-jsonl-edit-xhr-response, $.ads=false",
                "edit-element-object, .ad, $.ads, 500",
                "trusted-edit-element-object, .ad, $.ads=false, 500",
                "trusted-edit-inbound-element, app.element, this, .ad, $.ads=false",
                "trusted-prune-inbound-object, app.write, 1, ads, , dontOverwrite, 1",
                "trusted-prune-outbound-object, app.read, ads",
            )
        val source = entries.joinToString("\n") { "site.example##+js($it)" }
        val cache = folder.newFile()
        val first = KotlinFilterEngine(cache)
        first.replaceSources(listOf(FilterSource(source, true))) {}
        assertEquals(0, first.unsupportedRuleCount())
        val second = KotlinFilterEngine(cache)
        val stages = mutableListOf<RuleLoadStage>()
        second.replaceSources(listOf(FilterSource(source, true)), stages::add)
        assertTrue(RuleLoadStage.RESTORING in stages)
        assertFalse(RuleLoadStage.COMPILING in stages)
        assertEquals(first.scriptletInjection(), second.scriptletInjection())
        assertEquals(entries.size, second.scriptlets("https://site.example/").size)
        second.replace(listOf(source))
        assertEquals(
            entries.count { it.startsWith("trusted-") },
            second.unsupportedRuleCount(),
        )
        assertNull(
            ScriptletParser.parse("+js(edit-inbound-object, app.write, nope, $.ads)")
        )
        assertNull(
            ScriptletParser.parse("+js(edit-this-object, app.next, $.ads, before)")
        )
        export("objects.js", first.scriptletInjection()!!.source)
    }

    @Test
    fun proceduralChainsAreDataOnlyDomainScopedAndCacheStable() {
        val selectors =
            listOf(
                ".heading:has-text(Ads) + ul:remove()",
                ".card:has(> .label:has-text(/sponsored/i)):upward(2):remove()",
                ".card:has-text(Ads):has-text(Offer):style(opacity: 0 !important;)",
                ".card:matches-attr(data-kind=advert):min-text-length(5)",
                ".card:matches-css(display: block):matches-path(/news/)",
                ".card:if-not(.paid:has-text(Subscription)):watch-attr(data-state)",
                ".card:not(:has-text(Article))",
                ".card:not(.label:has-text(News))",
                ".card:has(:has-text(Ads)):not(.paid)",
                ".card:has-text(Ads).nested",
                ".card:matches-css-before(content: \"Offer\")",
                ":matches-path(/news/) .route",
            )
        val source = selectors.joinToString("\n") { "site.example##$it" }
        selectors.forEach { selector ->
            assertEquals(
                selector,
                0,
                inspectFilterList("site.example##$selector").unsupportedRules,
            )
        }
        val cache = folder.newFile()
        val first = KotlinFilterEngine(cache)
        first.replace(listOf(source))
        assertEquals(0, first.unsupportedRuleCount())
        assertEquals(
            selectors.size,
            first.cosmeticProcedures("https://site.example/").size,
        )
        assertTrue(first.cosmeticProcedures("https://other.example/").isEmpty())
        val second = KotlinFilterEngine(cache)
        val stages = mutableListOf<RuleLoadStage>()
        second.replace(listOf(source), stages::add)
        assertTrue(RuleLoadStage.RESTORING in stages)
        assertEquals(
            first.cosmeticProcedures("https://site.example/"),
            second.cosmeticProcedures("https://site.example/"),
        )
        for (selector in selectors) {
            assertEquals(1, inspectFilterList("##$selector").unsupportedRules)
        }
        for (selector in
            listOf(
                ".card:has-text(/(a+)+$/):remove()",
                ".card:has-text(Ads):style(background: url(https://bad.example/))",
                ".card:remove():has-text(Ads)",
            )) {
            assertEquals(
                selector,
                1,
                inspectFilterList("site.example##$selector").unsupportedRules,
            )
        }
        val data =
            first.cosmeticProcedures("https://site.example/").joinToString(
                ",",
                "[",
                "]",
            ) {
                it.operations!!
            }
        export("procedures.json", data)
    }

    @Test
    fun websocketMatchingRetainsItsSchemeAndNormalPartyAndExceptionSemantics() {
        val engine = KotlinFilterEngine()
        engine.replace(
            listOf(
                $$"|wss://ads.example/socket|$websocket,domain=frame.example\n" +
                    $$"||beacon.example^$ping,third-party\n" +
                    $$"@@||beacon.example/safe$ping\n" +
                    $$"||ws.example^$websocket,important"
            )
        )
        assertTrue(
            engine.shouldBlock(
                "wss://ads.example/socket",
                "https://frame.example/",
                "websocket",
                "GET",
            )
        )
        assertFalse(
            engine.shouldBlock(
                "ws://ads.example/socket",
                "https://frame.example/",
                "websocket",
                "GET",
            )
        )
        assertFalse(
            engine.shouldBlock(
                "wss://ads.example/socket",
                "https://other.example/",
                "websocket",
                "GET",
            )
        )
        assertTrue(
            engine.shouldBlock(
                "ws://ws.example/",
                "https://frame.example/",
                "websocket",
                "GET",
            )
        )
        assertTrue(
            engine.shouldBlock(
                "https://beacon.example/report",
                "https://frame.example/",
                "ping",
                "POST",
            )
        )
        assertFalse(
            engine.shouldBlock(
                "https://beacon.example/safe",
                "https://frame.example/",
                "ping",
                "POST",
            )
        )
    }

    @Test
    fun newResourcesRespectInlineEligibilityAndDaiTrustOnRedirectsAndInjection() {
        val engine = KotlinFilterEngine()
        for (name in listOf("google-ima-dai", "google-ima3-dai")) {
            val script = "site.example##+js($name)"
            val redirect = $$"||ads.example^$redirect=$$name.js"
            assertEquals(1, inspectFilterList(script).unsupportedRules)
            assertEquals(1, inspectFilterList(redirect).unsupportedRules)
            engine.replaceSources(
                listOf(FilterSource("$script\n$redirect", true))
            ) {}
            assertEquals(
                ScriptletType.TRUSTED_RESOURCE,
                engine.scriptlets("https://site.example/").single().type,
            )
            assertEquals(
                BlockingResponse.GOOGLE_IMA_DAI,
                engine.responseFor(
                    "https://ads.example/a.js",
                    "https://site.example/",
                    "script",
                    "GET",
                ),
            )
        }
        for (name in
            listOf("ampproject_v0", "outbrain-widget", "hd-main", "nobab2")) {
            assertNull(ScriptletParser.parse("+js($name)"))
            assertEquals(
                1,
                inspectFilterList($$"||ads.example^$redirect=$$name.js")
                    .networkRules,
            )
        }
        assertNotNull(ScriptletParser.parse("+js(googlesyndication-adsbygoogle)"))
        assertNotNull(ScriptletParser.parse("+js(xml-prune, xpath(//Ad/@id))"))
        assertNull(ScriptletParser.parse("+js(xml-prune, xpath(//Ad)"))
    }

    private fun export(name: String, text: String) {
        System.getenv("ROADMAP_OUTPUT_DIR")?.let {
            File(it).mkdirs()
            File(it, name).writeText(text)
        }
    }
}
