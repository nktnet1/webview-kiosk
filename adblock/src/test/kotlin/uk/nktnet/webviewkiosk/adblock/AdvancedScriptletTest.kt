package uk.nktnet.webviewkiosk.adblock

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import uk.nktnet.webviewkiosk.adblock.engine.AdvancedScriptlets
import uk.nktnet.webviewkiosk.adblock.engine.ScriptletParser

class AdvancedScriptletTest {
    @get:Rule val folder = TemporaryFolder()
    private val examples =
        mapOf(
            ScriptletType.TRUSTED_REPLACE_NODE_TEXT to
                "trusted-rpnt, script, /advert/g, content, sedCount, 1",
            ScriptletType.TRUSTED_CLICK_ELEMENT to
                "trusted-click-element, #reject, , 1000",
            ScriptletType.TRUSTED_REPLACE_ARGUMENT to
                "trusted-replace-argument, Storage.prototype.setItem, 1, " +
                    "json:false, condition, ads",
            ScriptletType.TRUSTED_REPLACE_OUTBOUND_TEXT to
                "trusted-replace-outbound-text, atob, advert, content",
            ScriptletType.TRUSTED_SUPPRESS_NATIVE_METHOD to
                "trusted-suppress-native-method, Function.prototype.constructor, " +
                    "'\"debugger\"', abort",
            ScriptletType.TRUSTED_PREVENT_DOM_BYPASS to
                "trusted-prevent-dom-bypass, Node.prototype.appendChild, JSON.parse",
            ScriptletType.TRUSTED_OVERRIDE_ELEMENT_METHOD to
                "trusted-override-element-method, HTMLElement.prototype.click, .advert",
            ScriptletType.TRUSTED_CREATE_HTML to
                "trusted-create-html, body, <span>Content</span>, 1000",
            ScriptletType.PREVENT_CLIPBOARD_WRITE to
                "prevent-clipboard-write, danger",
        )

    @Test
    fun everyAliasRequiresTrustedProvenanceAndRevalidatesIdenticallyFromCache() {
        assertEquals(AdvancedScriptlets.aliases.values.toSet(), examples.keys)
        AdvancedScriptlets.aliases.forEach { (name, type) ->
            val example = examples.getValue(type)
            val expected = ScriptletParser.parse("+js($example)", true)!!
            val expression = "+js($name,${example.substringAfter(',')})"
            assertEquals(expected, ScriptletParser.parse(expression, true))
            assertNull(ScriptletParser.parse(expression))
            assertNull(ScriptletParser.validated(type, expected.arguments))
            assertEquals(
                expected,
                ScriptletParser.validated(type, expected.arguments, true),
            )
        }
    }

    @Test
    fun trustedSnapshotsRestoreAdvancedRuntimeAndKeepExceptions() {
        val source = examples.values.joinToString("\n") { "site.example##+js($it)" }
        val sources =
            listOf(
                FilterSource(source, true),
                FilterSource(
                    "sub.site.example#@#+js(trusted-rpnt, script, /advert/g, content, sedCount, 1)"
                ),
            )
        val file = File(folder.root, "rules.bin")
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
    }

    @Test
    fun unsupportedConstraintsAndUnsafeFormsDiscardTheWholeInstruction() {
        listOf(
                "trusted-rpnt, script, /ads/g, text, unknown, value",
                "trusted-rpnt, script, ads, text, condition, ads, includes, content",
                "trusted-click-element, #reject, unknown:value",
                "trusted-click-element, xpath://button",
                "trusted-click-element, #reject, , 99999",
                "trusted-replace-argument, method, 99, json:false",
                "trusted-replace-argument, method, 0, repl:/(a+)+$/text/",
                "trusted-suppress-native-method, method, unknown",
                "trusted-suppress-native-method, method, *, debug",
                "trusted-prevent-dom-bypass, Node.prototype.appendChild",
                "trusted-override-element-method, method, , arbitrary",
                "trusted-create-html, body, <span>text</span>, , limit, 999",
                "prevent-clipboard-write, danger, domAlert, arbitrary HTML",
            )
            .forEach { args ->
                assertNull(args, ScriptletParser.parse("+js($args)", true))
            }
        assertNotNull(
            ScriptletParser.parse(
                "+js(trusted-rpnt, script, advert, ${"x".repeat(1024)})",
                true,
            )
        )
        assertNull(
            ScriptletParser.parse(
                "+js(set, flag, ${"x".repeat(1024)})",
                true,
            )
        )
    }

    @Test
    fun untrustedExceptionsCanDisableTrustedOperationsWithoutExecutingThem() {
        val text = "site.example#@#+js(trusted-click-element, #reject)"
        val file = File(folder.root, "exceptions.bin")
        KotlinFilterEngine(file).replace(listOf(text))
        val restored = KotlinFilterEngine(file)
        val stages = mutableListOf<RuleLoadStage>()
        restored.replace(listOf(text), stages::add)
        assertFalse(RuleLoadStage.COMPILING in stages)
        assertNull(restored.scriptletInjection())
        assertEquals(0, restored.unsupportedRuleCount())
    }
}
