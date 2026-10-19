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

class CookieScriptletTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun trustedExpiryPathAndOptionsUseTheUpstreamArgumentPositions() {
        val expression =
            "+js(trusted-set-cookie, token, abc=, 1year, /, " +
                "dontOverwrite, 1, reload, 1, domain, .EXAMPLE.com)"
        val parsed = ScriptletParser.parse(expression, true)!!
        assertEquals(
            listOf(
                "token",
                "abc=",
                "1year",
                "",
                "domain",
                "example.com",
                "reload",
                "1",
                "dontOverwrite",
                "1",
            ),
            parsed.arguments,
        )
        assertEquals(
            parsed,
            ScriptletParser.validated(parsed.type, parsed.arguments, true),
        )
        assertNull(ScriptletParser.parse(expression))
        assertNull(ScriptletParser.validated(parsed.type, parsed.arguments))
        assertEquals(
            ScriptletParser.parse("+js(trusted-set-cookie, token, abc=)", true),
            ScriptletParser.parse(
                "+js(trusted-set-cookie, token, abc=, , /, reload, 0)",
                true,
            ),
        )
        for (expiry in listOf("", "0", "1day", "1year", "2147483647")) {
            assertNotNull(
                expiry,
                ScriptletParser.parse(
                    "+js(trusted-set-cookie, token, abc=, $expiry, none)",
                    true,
                ),
            )
        }
        assertEquals(
            "3600",
            ScriptletParser.parse(
                    "+js(trusted-set-cookie, token, abc=, 0003600)",
                    true,
                )!!
                .arguments[2],
        )
    }

    @Test
    fun cookieDefaultsDomainsAndOptionOrderingKeepExceptionsIdentical() {
        val block =
            "site.example##+js(trusted-set-cookie-reload, token, abc=, 1day, , " +
                "reload, 0, dontOverwrite, 1, domain, .bücher.de)"
        val exception =
            "site.example#@#+js(trusted-set-cookie-reload, token, abc=, 1day, /, " +
                "domain, xn--bcher-kva.de, dontOverwrite, 1, reload, 0)"
        for (sources in listOf(listOf(block, exception), listOf(exception, block))) {
            val engine = KotlinFilterEngine()
            engine.replaceSources(sources.map { FilterSource(it, true) }) {}
            assertEquals(0, engine.unsupportedRuleCount())
            assertTrue(engine.scriptlets("https://site.example/").isEmpty())
        }
        assertEquals(
            ScriptletParser.parse("+js(set-cookie, consent, true)"),
            ScriptletParser.parse(
                "+js(set-cookie, consent, true, /, dontOverwrite, 0, reload, 0)"
            ),
        )
    }

    @Test
    fun cacheRoundTripsTenArgumentsAndKeepsSourceTrustAndExceptions() {
        val file = File(folder.root, "rules.bin")
        val source =
            FilterSource(
                "site.example##+js(trusted-set-cookie, token, abc=, 1year, , " +
                    "domain, site.example, reload, 1, dontOverwrite, 1)",
                true,
            )
        val exception =
            FilterSource(
                "safe.site.example#@#+js(trusted-set-cookie, token, abc=, 1year, /, " +
                    "dontOverwrite, 1, reload, 1, domain, .site.example)"
            )
        val first = KotlinFilterEngine(file)
        first.replaceSources(listOf(source, exception)) {}
        val stages = mutableListOf<RuleLoadStage>()
        val restored = KotlinFilterEngine(file)
        restored.replaceSources(listOf(source, exception), stages::add)
        assertTrue(RuleLoadStage.RESTORING in stages)
        assertFalse(RuleLoadStage.COMPILING in stages)
        assertEquals(first.scriptletInjection(), restored.scriptletInjection())
        System.getenv("SCRIPTLET_OUTPUT_DIR")?.let { output ->
            File(output).mkdirs()
            File(output, "cookie-injection.js")
                .writeText(restored.scriptletInjection()!!.source)
        }
        assertEquals(
            10,
            restored.scriptlets("https://site.example/").single().arguments.size,
        )
        assertTrue(restored.scriptlets("https://safe.site.example/").isEmpty())
        restored.replaceSources(listOf(source)) {}
        assertEquals(1, restored.scriptlets("https://safe.site.example/").size)
        restored.replaceSources(listOf(source.copy(trusted = false))) {}
        assertTrue(restored.scriptlets("https://site.example/").isEmpty())
    }

    @Test
    fun unsupportedOrUnsafeFormsStillSkipTheWholeRule() {
        for (arguments in
            listOf(
                "token, value, /",
                "token, value, none",
                "token, value, -1",
                "token, value, 2147483648",
                "token, value, 1e9",
                "token, value, 1year, /path",
                "token, value, , , reload",
                "token, value, , , reload, true",
                "token, value, , , reload, 1, reload, 0",
                "token, value, , , domain, /example/",
                "token, value, , , domain, example.com; Secure",
                "token, value, , , domain, [::1]",
                "token, value, , , unknown, 1",
                "bad;name, value",
                "bad=name, value",
                $$"token, $unknown$",
            )) {
            assertNull(
                arguments,
                ScriptletParser.parse("+js(trusted-set-cookie, $arguments)", true),
            )
        }
        assertNotNull(
            ScriptletParser.parse(
                $$"+js(trusted-set-cookie, token, $now$-$currentDate$-$currentISODate$, 1day)",
                true,
            )
        )
        assertNull(ScriptletParser.parse("+js(set-cookie, token, arbitrary)"))
        assertNull(
            ScriptletParser.parse(
                "+js(set-cookie, token, true, , domain, example.com)"
            )
        )
    }
}
