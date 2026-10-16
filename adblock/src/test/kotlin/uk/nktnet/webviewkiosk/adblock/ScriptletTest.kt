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
import uk.nktnet.webviewkiosk.adblock.engine.ScriptletParser

class ScriptletTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun compilesRealUblockExamplesForEveryImplementedType() {
        val examples =
            linkedMapOf(
                "timesofindia.indiatimes.com##+js(set, nsShowMaxCount, 0)" to
                    ScriptletType.SET,
                "aranzulla.it##+js(aopr, navigator.userAgent)" to
                    ScriptletType.ABORT_READ,
                "sueddeutsche.de##+js(aopw, SZAdBlockDetection)" to
                    ScriptletType.ABORT_WRITE,
                "dailyuploads.net##+js(nowoif)" to ScriptletType.PREVENT_WINDOW_OPEN,
                "newser.com##+js(aeld, load, Object)" to
                    ScriptletType.PREVENT_ADD_EVENT_LISTENER,
                "mediafire.com##+js(nostif, InfMediafireMobileFunc, 1000)" to
                    ScriptletType.PREVENT_SET_TIMEOUT,
                "motherless.com##+js(acs, jQuery, cookie)" to
                    ScriptletType.ABORT_CURRENT_SCRIPT,
                "chip.de##+js(json-prune, enabled, force_disabled)" to
                    ScriptletType.JSON_PRUNE,
                "mac2sell.net##+js(no-fetch-if, method:HEAD)" to
                    ScriptletType.PREVENT_FETCH,
            )
        for ((line, type) in examples) {
            val engine = KotlinFilterEngine()
            engine.validate(line)
            engine.replace(listOf(line))
            val host = line.substringBefore("##")
            assertEquals(
                line,
                type,
                engine.scriptlets("https://$host/").single().type,
            )
            assertNotNull(engine.scriptletInjection())
            assertEquals(1, inspectFilterList(line).scriptletRules)
            assertEquals(0, engine.unsupportedRuleCount())
        }
    }

    @Test
    fun aliasesAndDefaultsDeduplicateAndExceptionsWinRegardlessOfListOrder() {
        val block = "site.example##+js(aopr, detector)"
        val alias = "site.example##+js(abort-on-property-read.js, detector)"
        val exception = "sub.site.example#@#+js(abort-on-property-read, detector)"
        for (lists in
            listOf(
                listOf(block, alias, exception),
                listOf(exception, alias, block),
            )) {
            val engine = KotlinFilterEngine()
            engine.replace(lists)
            assertEquals(1, engine.scriptlets("https://site.example/").size)
            assertTrue(engine.scriptlets("https://sub.site.example/").isEmpty())
            assertTrue(engine.scriptlets("https://unrelated.example/").isEmpty())
        }
        val engine = KotlinFilterEngine()
        engine.replace(
            listOf("site.example##+js(nowoif)\nsite.example##+js(nowoif, )")
        )
        assertEquals(1, engine.scriptlets("https://site.example").size)
    }

    @Test
    fun exclusionsBlanketExceptionsAndCssExceptionsHaveDistinctScopes() {
        val engine = KotlinFilterEngine()
        engine.replace(
            listOf(
                "site.example,~safe.site.example##+js(set, ads, false)\n" +
                    "site.example#@#ads\nsub.site.example#@#+js()"
            )
        )
        assertEquals(1, engine.scriptlets("https://site.example:8443/a").size)
        assertTrue(engine.scriptlets("https://safe.site.example/").isEmpty())
        assertTrue(engine.scriptlets("https://sub.site.example/").isEmpty())
        assertTrue(
            engine.scriptlets("https://site.example.attacker.example/").isEmpty()
        )
        engine.replace(listOf("site.example##+js(nowoif)\n#@#+js()"))
        assertTrue(engine.scriptlets("https://site.example").isEmpty())
    }

    @Test
    fun preservesConditionalsIncludesAndFirstOccurrenceOrder() {
        val source =
            "!#if env_chromium\n!#include child.txt\n!#else\n" +
                "site.example##+js(set, other, true)\n!#endif"
        val expanded =
            expandFilterListIncludes(source) {
                assertEquals("child.txt", it)
                "site.example##+js(set, ads, false)\nsite.example##+js(set, ads, true)"
            }
        val engine = KotlinFilterEngine()
        engine.replace(listOf(expanded))
        assertEquals(
            listOf("false", "true"),
            engine.scriptlets("https://site.example").map { it.arguments[1] },
        )
    }

    @Test
    fun rejectsUnsupportedArgumentFormsRatherThanApproximatingThem() {
        val expressions =
            listOf(
                "+js(set, x, alert(1))",
                "+js(set, x, 32768)",
                "+js(set, x, false, runAt, idle)",
                "+js(set, __proto__.x, true)",
                "+js(aopr, a..b)",
                "+js(aopw, a, unexpected)",
                "+js(nowoif, ad, 10, obj)",
                "+js(nowoif, debug)",
                "+js(aeld)",
                "+js(aeld, click, , elements, [src])",
                "+js(nostif)",
                "+js(nostif, ad, 200-100)",
                "+js(nostif, /(a+)+/)",
                "+js(nostif, /a+$/)",
                "+js(nostif, /(a+|b)a+$/)",
                "+js(nostif, /^(a|aa)(a|aa)(a|aa)(a|aa)(a|aa)$/)",
                "+js(nostif, /^a+$/m)",
                "+js(nostif, /^\\b+/)",
                "+js(nostif, /^+/)",
                "+js(nostif, /(?=ad)/)",
                "+js(nostif, /(a)\\1/)",
                "+js(nostif, /ad/g)",
                "+js(nostif, /[a-/)",
                "+js(acs, x, test, , extra)",
                "+js(json-prune, items.[-].ad)",
                "+js(json-prune, constructor.x)",
                "+js(json-prune, items.[])",
                "+js(json-prune, ads, , stackToMatch)",
                "+js(no-fetch-if)",
                "+js(no-fetch-if, ads, war:resource)",
                "+js(no-fetch-if, ads, length:10, cors)",
                "+js(no-fetch-if, mode:cors)",
                "+js(no-xhr-if, ads)",
                "+js(trusted-set, x, alert(1))",
                "+js(aopr, 'unclosed)",
                "+js(aopr, 'x' trailing)",
                "+js(aopr, x) trailing",
                "+js(aopr, ${"x".repeat(513)})",
            )
        for (expression in expressions) {
            assertNull(expression, ScriptletParser.parse(expression))
            assertEquals(
                expression,
                1,
                inspectFilterList("site.example##$expression").unsupportedRules,
            )
        }
        for (scope in
            listOf("", "~site.example", "site.*", "/site[0-9]/", "[::1]")) {
            assertEquals(
                scope,
                1,
                inspectFilterList("$scope##+js(nowoif)").unsupportedRules,
            )
        }
    }

    @Test
    fun parsesQuotedEscapedAndEmptyArgumentsWithoutExecutingThem() {
        val empty = ScriptletParser.parse("+js(set, x, '')")!!
        assertEquals("", empty.arguments[1])
        val pattern = ScriptletParser.parse("+js(nostif, '/foo\\,bar/i', '!1000')")!!
        assertEquals(listOf("/foo,bar/i", "!1000"), pattern.arguments)
        assertEquals(
            "one,two",
            ScriptletParser.parse("+js(nowoif, `one,two`)")!!.arguments.single(),
        )
        assertEquals(
            "\\bads\\b",
            ScriptletParser.parse("+js(nostif, \\bads\\b)")!!.arguments[0],
        )
    }

    @Test
    fun validatesCachedScriptletsRestoresSourceAndInvalidatesOldSnapshots() {
        val file = File(folder.root, "cache.bin")
        val lists =
            listOf(
                "site.example##+js(set, ads, false)\n" +
                    "site.example##+js(aeld, load, ads)\nsub.site.example#@#+js()"
            )
        val first = KotlinFilterEngine(file)
        first.replace(lists)
        val restored = KotlinFilterEngine(file)
        val stages = mutableListOf<RuleLoadStage>()
        restored.replace(lists, stages::add)
        assertTrue(RuleLoadStage.RESTORING in stages)
        assertFalse(RuleLoadStage.COMPILING in stages)
        assertEquals(first.scriptletInjection(), restored.scriptletInjection())
        assertEquals(
            first.scriptlets("https://site.example"),
            restored.scriptlets("https://site.example"),
        )
        restored.replace(listOf("other.example##+js(nowoif)"))
        assertTrue(restored.scriptlets("https://site.example").isEmpty())
        assertEquals(1, restored.scriptlets("https://other.example").size)
        restored.clear()
        assertNull(restored.scriptletInjection())
        assertTrue(restored.scriptlets("https://other.example").isEmpty())
        // Simulate a cache written before scriptlets were added (schema 5).
        val bytes = file.readBytes()
        bytes[7] = 5
        file.writeBytes(bytes)
        restored.replace(lists, stages::add)
        assertTrue(RuleLoadStage.COMPILING in stages)
    }

    @Test
    fun boundsPerDocumentWorkAndRestrictsInjectionToHttpHostFamilies() {
        val engine = KotlinFilterEngine()
        engine.replace(
            listOf(
                (0..140).joinToString("\n") {
                    "site.example##+js(set, p$it, false)"
                }
            )
        )
        assertEquals(128, engine.scriptlets("https://site.example").size)
        assertEquals(
            setOf("*"),
            engine.scriptletInjection()!!.allowedOrigins,
        )
        assertTrue(engine.scriptlets("file://site.example/a").isEmpty())
        assertTrue(engine.scriptlets("data:text/html,test").isEmpty())
    }

    @Test
    fun emitsOnlyJsonDataEvenForSourceLikePatternArguments() {
        val engine = KotlinFilterEngine()
        val pattern = "x); self.compromised = true; // <script>"
        engine.replace(listOf("site.example##+js(nowoif, '$pattern')"))
        val source = engine.scriptletInjection()!!.source
        assertTrue(source.contains("\\u003c"))
        assertFalse(source.contains("<script>"))
        assertTrue(source.contains("\\\"PREVENT_WINDOW_OPEN\\\""))
    }
}
