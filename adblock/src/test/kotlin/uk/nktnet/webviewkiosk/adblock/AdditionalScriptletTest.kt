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
import uk.nktnet.webviewkiosk.adblock.engine.AdditionalScriptlets
import uk.nktnet.webviewkiosk.adblock.engine.ScriptletParser
import uk.nktnet.webviewkiosk.adblock.engine.ScriptletResources

class AdditionalScriptletTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun cookieFlagsAreValidatedCanonicalizedAndMatchedByExceptions() {
        val rule = "+js(set-cookie, GPC, 1, , reload, 1)"
        assertEquals(
            listOf("GPC", "1", "", "reload", "1"),
            ScriptletParser.parse(rule)!!.arguments,
        )
        assertEquals(
            ScriptletParser.parse("+js(set-cookie, GPC, 1)"),
            ScriptletParser.parse("+js(set-cookie, GPC, 1, /, reload, 0)"),
        )
        val engine = KotlinFilterEngine()
        engine.replace(
            listOf(
                "usaa.com##+js(set-cookie, GPC, 1, , dontOverwrite, 1, reload, 1)\n" +
                    "usaa.com#@#+js(set-cookie, GPC, 1, /, reload, 1, dontOverwrite, 1)"
            )
        )
        assertTrue(engine.scriptlets("https://usaa.com/").isEmpty())
        for (tail in
            listOf(
                "reload",
                "reload, 2",
                "reload, 1, reload, 1",
                "domain, example.com",
                "dontOverwrite, true",
                "reload, 1, extra, 1",
            )) {
            assertNull(
                tail,
                ScriptletParser.parse("+js(set-cookie, GPC, 1, , $tail)"),
            )
        }
        assertNull(
            ScriptletParser.parse("+js(set-cookie, GPC, unsafe-value, , reload, 1)")
        )
    }

    // Real upstream names and forms, including operations absent from filters.txt.
    private val examples =
        linkedMapOf(
            ScriptletType.PREVENT_SET_INTERVAL to "nosiif, ads, 1000",
            ScriptletType.PREVENT_ANIMATION_FRAME to "norafif, ads",
            ScriptletType.ADJUST_SET_TIMEOUT to "nano-stb, redirect, 4000",
            ScriptletType.ADJUST_SET_INTERVAL to "nano-sib, , 1000",
            ScriptletType.PREVENT_EVAL to "noeval-if, ads",
            ScriptletType.ABORT_STACK_TRACE to "aost, onload, inlineScript",
            ScriptletType.SET_LOCAL_STORAGE to
                "set-local-storage-item, consent, false",
            ScriptletType.SET_SESSION_STORAGE to
                "set-session-storage-item, seen, true",
            ScriptletType.SET_COOKIE to "set-cookie, popunder_stop, 1",
            ScriptletType.SET_COOKIE_RELOAD to "set-cookie-reload, seen, true",
            ScriptletType.REMOVE_COOKIE to "cookie-remover, advert",
            ScriptletType.SET_ATTRIBUTE to "set-attr, .advert, data-blocked, true",
            ScriptletType.REMOVE_ATTRIBUTE to "ra, href|target, a, complete",
            ScriptletType.REMOVE_CLASS to "rc, advert, body, stay",
            ScriptletType.REMOVE_NODE_TEXT to "rmnt, script, adserverDomain",
            ScriptletType.PREVENT_ELEMENT_PROPERTY to
                "freeze-element-property, innerHTML, .ads, ads",
            ScriptletType.PREVENT_DIALOG to "prevent-dialog, .advert",
            ScriptletType.PREVENT_CANVAS to "prevent-canvas, webgl",
            ScriptletType.SPOOF_CSS to "spoof-css, .advert, display, none",
            ScriptletType.CALL_NOTHROW to "call-nothrow, api.run",
            ScriptletType.CLEAR_WINDOW_NAME to "window.name-defuser",
            ScriptletType.DISABLE_NEWTAB_LINKS to "disable-newtab-links",
            ScriptletType.PREVENT_REFRESH to "refresh-defuser",
            ScriptletType.PREVENT_WEBRTC to "nowebrtc",
            ScriptletType.WEBRTC_IF to "webrtc-if, good.example",
            ScriptletType.PREVENT_BAB to "nobab",
            ScriptletType.ALERT_BUSTER to "alert-buster",
            ScriptletType.HREF_SANITIZER to "href-sanitizer, a, ?to",
            ScriptletType.PREVENT_NAVIGATION to "prevent-navigation, ads.example",
            ScriptletType.MULTIUP to "multiup",
            ScriptletType.OVERLAY_BUSTER to "overlay-buster",
        )

    @Test
    fun examplesCompileToExecutableOperations() {
        assertEquals(AdditionalScriptlets.aliases.values.toSet(), examples.keys)
        examples.forEach { (type, example) ->
            val rule = "site.example##+js($example)"
            val engine = KotlinFilterEngine()
            engine.replace(listOf(rule))
            assertEquals(
                example,
                type,
                engine.scriptlets("https://site.example").single().type,
            )
            assertEquals(1, inspectFilterList(rule).scriptletRules)
            assertNotNull(engine.scriptletInjection())
        }
    }

    @Test
    fun everyAliasNormalizesAndExceptionsDeduplicate() {
        AdditionalScriptlets.aliases.forEach { (name, type) ->
            if (name == "prevent-innerHTML") {
                return@forEach
            }
            val tail = examples.getValue(type).substringAfter(',', "")
            val args =
                if (tail.isEmpty()) {
                    ""
                } else {
                    ",$tail"
                }
            val expected = ScriptletParser.parse("+js(${examples.getValue(type)})")
            assertEquals(name, expected, ScriptletParser.parse("+js($name$args)"))
            assertEquals(name, expected, ScriptletParser.parse("+js($name.js$args)"))
        }
        assertEquals(
            ScriptletParser.parse(
                "+js(freeze-element-property, innerHTML, .ads, ads)"
            ),
            ScriptletParser.parse("+js(prevent-innerHTML, .ads, ads)"),
        )
        assertEquals(
            ScriptletParser.parse("+js(noeval)"),
            ScriptletParser.parse("+js(noeval-silent)"),
        )
        assertEquals(
            ScriptletParser.parse("+js(noeval)"),
            ScriptletParser.parse("+js(silent-noeval.js)"),
        )
        val engine = KotlinFilterEngine()
        engine.replace(
            listOf(
                "site.example##+js(nano-stb)\n" +
                    "site.example##+js(adjust-setTimeout, , , )\n" +
                    "sub.site.example#@#+js(nano-setTimeout-booster)"
            )
        )
        assertEquals(1, engine.scriptlets("https://site.example").size)
        assertTrue(engine.scriptlets("https://sub.site.example").isEmpty())
    }

    @Test
    fun staticResourcesAreWhitelistedAndSelectedByDataOnly() {
        assertEquals(15, ScriptletResources.files.size)
        ScriptletResources.aliases.forEach { (alias, file) ->
            val instruction = ScriptletParser.parse("+js($alias)")!!
            assertEquals(ScriptletType.RESOURCE, instruction.type)
            assertEquals(listOf(file), instruction.arguments)
            assertEquals(instruction, ScriptletParser.parse("+js($alias.js)"))
            assertNull(ScriptletParser.parse("+js($alias, arbitrary)"))
        }
        assertNull(
            ScriptletParser.validated(ScriptletType.RESOURCE, listOf("../../x.js"))
        )
        val engine = KotlinFilterEngine()
        engine.replace(
            listOf(
                "site.example##+js(nofab)\nsite.example##+js(fuckadblock.js-3.2.0)"
            )
        )
        assertEquals(1, engine.scriptlets("https://site.example").size)
        val source = engine.scriptletInjection()!!.source
        assertTrue(source.contains("const Fab ="))
        assertFalse(source.contains("const FingerprintJS ="))
    }

    @Test
    fun unsafeAndUnsupportedFormsStaySkipped() {
        listOf(
                "nosiif",
                "norafif",
                "noeval-if",
                "aost, prop",
                "nosiif, /(a+)+/",
                "nano-stb, , *, NaN",
                "aost, constructor.x, stack",
                "set-cookie, name, arbitrary",
                "set-cookie, name, true, /path",
                "set-local-storage-item, key, \$now\$",
                "set-cookie, name, true, , unknown, 1",
                "set-attr, body, onclick, true",
                "set-attr, iframe, srcdoc, true",
                "ra, onclick, a, unexpected",
                "ra, onclick, [",
                "rc, advert, div:has-text(ads)",
                "rmnt, script, ads, condition, cookie",
                "freeze-element-property, unknown",
                "spoof-css, a, debug, true",
                "href-sanitizer, a, ?url -base64",
                "prevent-navigation",
                "trusted-set, flag, true",
                "json-edit, ads",
                "noop",
            )
            .forEach { input ->
                assertNull(input, ScriptletParser.parse("+js($input)"))
                assertEquals(
                    1,
                    inspectFilterList("site.example##+js($input)").unsupportedRules,
                )
            }
    }

    @Test
    fun cacheRestoresAdditionalInstructionsAndInvalidatesVersionSix() {
        val file = File(folder.root, "cache.bin")
        val rules =
            listOf(
                examples.values.joinToString("\n") { "site.example##+js($it)" } +
                    "\nsite.example##+js(nofab)"
            )
        val first = KotlinFilterEngine(file)
        first.replace(rules)
        val second = KotlinFilterEngine(file)
        val stages = mutableListOf<RuleLoadStage>()
        second.replace(rules, stages::add)
        assertTrue(RuleLoadStage.RESTORING in stages)
        assertEquals(first.scriptletInjection(), second.scriptletInjection())
        val bytes = file.readBytes()
        bytes[7] = 6
        file.writeBytes(bytes)
        stages.clear()
        second.replace(rules, stages::add)
        assertFalse(RuleLoadStage.COMPILING in stages)
        assertTrue(RuleLoadStage.RESTORING in stages)
        assertEquals(
            first.scriptlets("https://site.example"),
            second.scriptlets("https://site.example"),
        )
    }
}
