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
import uk.nktnet.webviewkiosk.adblock.engine.ResponseScriptlets
import uk.nktnet.webviewkiosk.adblock.engine.ScriptletParser

class ResponseScriptletTest {
    @get:Rule val folder = TemporaryFolder()
    private val examples =
        mapOf(
            ScriptletType.TRUSTED_SET to
                "trusted-set, config, json:{\"value\":\"text\"}",
            ScriptletType.TRUSTED_SET_ATTRIBUTE to
                "trusted-set-attr, main, data-custom, arbitrary text",
            ScriptletType.PREVENT_XHR to "prevent-xhr, url:ads method:GET, emptyObj",
            ScriptletType.JSON_PRUNE_FETCH to
                "json-prune-fetch-response, ads, data, propsToMatch, /api",
            ScriptletType.JSON_PRUNE_XHR to "json-prune-xhr-response, ads",
            ScriptletType.XML_PRUNE to "xml-prune, Ad, MPD, /manifest",
            ScriptletType.M3U_PRUNE to "m3u-prune, /advert/, .m3u8",
            ScriptletType.MPEGDASH_PRUNE to "mpegdash-prune, Period.advert, /dash",
            ScriptletType.TRUSTED_REPLACE_FETCH to
                "trusted-replace-fetch-response, /advert/, removed, method:GET",
            ScriptletType.TRUSTED_REPLACE_XHR to
                "trusted-replace-xhr-response, advert, removed",
            ScriptletType.TRUSTED_PREVENT_FETCH to
                "trusted-prevent-fetch, ads, response text",
            ScriptletType.TRUSTED_PREVENT_XHR to
                "trusted-prevent-xhr, ads, response text",
            ScriptletType.TRUSTED_LOCAL_STORAGE to
                "trusted-set-local-storage-item, key, arbitrary text",
            ScriptletType.TRUSTED_SESSION_STORAGE to
                "trusted-set-session-storage-item, key, arbitrary text",
            ScriptletType.TRUSTED_SET_COOKIE to
                "trusted-set-cookie, key, arbitrary text",
            ScriptletType.TRUSTED_SET_COOKIE_RELOAD to
                "trusted-set-cookie-reload, key, arbitrary text",
        )

    @Test
    fun aliasesAndCachedArgumentsRetainIdenticalValidatedInstructions() {
        assertEquals(ResponseScriptlets.aliases.values.toSet(), examples.keys)
        ResponseScriptlets.aliases.forEach { (name, type) ->
            val example = examples.getValue(type)
            val expected = ScriptletParser.parse("+js($example)", trusted = true)!!
            val args = example.substringAfter(',')
            assertEquals(expected, ScriptletParser.parse("+js($name,$args)", true))
            assertEquals(
                expected,
                ScriptletParser.parse("+js($name.js,$args)", true),
            )
            assertEquals(
                expected,
                ScriptletParser.validated(type, expected.arguments, true),
            )
            if (type.requiresTrust) {
                assertNull(ScriptletParser.parse("+js($example)"))
                assertNull(ScriptletParser.validated(type, expected.arguments))
            }
        }
    }

    @Test
    fun sourceTrustCannotBeRequestedByFilterTextOrReusedAcrossCacheKeys() {
        val file = File(folder.root, "cache.bin")
        val text =
            "! trusted source\n!#trusted\n" +
                "site.example##+js(trusted-prevent-xhr, ads, response text)"
        val trusted = KotlinFilterEngine(file)
        trusted.replaceSources(listOf(FilterSource(text, true))) {}
        assertEquals(1, trusted.scriptlets("https://site.example/").size)
        val stages = mutableListOf<RuleLoadStage>()
        val untrusted = KotlinFilterEngine(file)
        untrusted.replace(listOf(text), stages::add)
        assertTrue(RuleLoadStage.COMPILING in stages)
        assertTrue(untrusted.scriptlets("https://site.example/").isEmpty())
        assertEquals(1, untrusted.unsupportedRuleCount())
        assertEquals(0, inspectFilterList(text).scriptletRules)
        assertEquals(1, inspectFilterList(text, trusted = true).scriptletRules)
    }

    @Test
    fun slashContainingLiteralRequestPathsAreNotMistakenForRegexFlags() {
        assertNotNull(
            ScriptletParser.parse("+js(no-xhr-if, /i/api/1.1/flow/viewer.json)")
        )
        assertNotNull(ScriptletParser.parse("+js(no-fetch-if, /api/player/config)"))
    }

    @Test
    fun responseRulesRoundTripAndKeepExceptionsAndListOrder() {
        val text = examples.values.joinToString("\n") { "site.example##+js($it)" }
        val file = File(folder.root, "cache.bin")
        val sources =
            listOf(
                FilterSource(text, true),
                FilterSource(
                    "sub.site.example#@#+js(no-xhr-if, url:ads method:GET, emptyObj)"
                ),
            )
        val original = KotlinFilterEngine(file)
        original.replaceSources(sources) {}
        val stages = mutableListOf<RuleLoadStage>()
        val restored = KotlinFilterEngine(file)
        restored.replaceSources(sources, stages::add)
        assertFalse(RuleLoadStage.COMPILING in stages)
        assertEquals(original.scriptletInjection(), restored.scriptletInjection())
        assertEquals(
            examples.size,
            restored.scriptlets("https://site.example/").size,
        )
        assertEquals(
            examples.size - 1,
            restored.scriptlets("https://sub.site.example/").size,
        )
        assertNotNull(restored.scriptletInjection())
    }

    @Test
    fun trustedAttributesUseAnExplicitLiteralAttributeSubset() {
        listOf("data-custom", "aria-label", "CLASS", "disabled", "value").forEach {
            attribute ->
            assertNotNull(
                ScriptletParser.parse(
                    "+js(trusted-set-attr, main, $attribute, arbitrary text)",
                    trusted = true,
                )
            )
        }
    }

    @Test
    fun rejectsUnsupportedOptionsRatherThanBroadeningResponseOperations() {
        listOf(
                "prevent-xhr",
                "prevent-xhr, mode:cors",
                "prevent-xhr, ads, war:remote",
                "json-prune-fetch-response",
                "json-prune-xhr-response, constructor.x",
                "json-prune-fetch-response, ads, , propsToMatch, /api, propsToMatch, other",
                "json-prune-fetch-response, ads, , unknown, value",
                "json-prune-fetch-response, ads, , propsToMatch, mode:cors",
                "xml-prune, xpath(//Ad)",
                "xml-prune, Ad, , ads, logdoc, 1",
                "m3u-prune",
                "m3u-prune, *ads*",
                "m3u-prune, /ads/m",
                "trusted-replace-fetch-response, /ad/g, text",
                "trusted-replace-xhr-response, /(a+)+/, text",
                "trusted-set-cookie, key, value, /path",
                "trusted-set-local-storage-item, key, \$now\$",
                "trusted-set, value, json:{invalid}",
                "trusted-set-attr, a, href, https://example.com",
                "trusted-set-attr, a, onclick, alert(1)",
                "trusted-set-attr, use, xlink:href, https://example.com",
                "trusted-set-attr, object, data, https://example.com",
                "trusted-set-attr, meta, content, https://example.com",
                "trusted-set-attr, script, type, module",
            )
            .forEach { args ->
                assertNull(args, ScriptletParser.parse("+js($args)", trusted = true))
            }
    }
}
