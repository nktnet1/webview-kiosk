package uk.nktnet.webviewkiosk.adblock

import java.io.File
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import uk.nktnet.webviewkiosk.adblock.engine.JsonEditing
import uk.nktnet.webviewkiosk.adblock.engine.ScriptletParser

class JsonPathExtensionsTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun slicesAndLogicalPredicatesCompileThroughTheProductionDispatcher() {
        val numbers = "{\"numbers\":[0,1,2,3,4,5,6]}"
        val flags = "[{\"flag\":false},{\"flag\":null},{\"flag\":0},{}]"
        val cases =
            listOf(
                Triple("$.numbers[1:5:2]", numbers, "{\"numbers\":[0,2,4,5,6]}"),
                Triple("$.numbers[-5:-1:2]", numbers, "{\"numbers\":[0,1,3,5,6]}"),
                Triple("$.numbers[5:1:-2]", numbers, "{\"numbers\":[0,1,2,4,6]}"),
                Triple("$.numbers[::-1]", numbers, "{\"numbers\":[]}"),
                Triple("$.numbers[::0]", numbers, numbers),
                Triple("$.numbers[3:1]", numbers, numbers),
                Triple("$.numbers[-50:50:3]", numbers, "{\"numbers\":[1,2,4,5]}"),
                Triple("$.numbers[5:1:-2,3,0]", numbers, "{\"numbers\":[1,2,4,6]}"),
                Triple("$.numbers[9007199254740991:]", numbers, numbers),
                Triple(
                    "$.numbers[:2]",
                    "{\"numbers\":false}",
                    "{\"numbers\":false}",
                ),
                Triple(
                    "$.numbers[?@<2 || @>=5 && @!=1]",
                    numbers,
                    "{\"numbers\":[2,3,4]}",
                ),
                Triple(
                    "$.numbers[?(@<2 || @>=5) && @!=1]",
                    numbers,
                    "{\"numbers\":[1,2,3,4]}",
                ),
                Triple(
                    "$.numbers[?!(@>=1 && @<=5)]",
                    numbers,
                    "{\"numbers\":[1,2,3,4,5]}",
                ),
                Triple("$[?@.flag || @.missing]", flags, "[{}]"),
                Triple(
                    "$[?!@.flag || @.flag==false]",
                    flags,
                    "[{\"flag\":null},{\"flag\":0}]",
                ),
                Triple("$[?(@.flag!=false)]", flags, "[{\"flag\":false}]"),
                Triple(
                    "$[?@==1 || @>1]",
                    "[\"2\",2,true,null]",
                    "[\"2\",true,null]",
                ),
                Triple(
                    "$[?(@.flag)]",
                    "{\"a\":{\"flag\":false},\"b\":{}}",
                    "{\"b\":{}}",
                ),
                Triple(
                    "v2:$[?@.children[?@.flag==true && @.score>=2]]",
                    "[{\"children\":[{\"flag\":true,\"score\":3}]}," +
                        "{\"children\":[{\"flag\":true,\"score\":1}]},{}]",
                    "[{\"children\":[{\"flag\":true,\"score\":1}]},{}]",
                ),
                Triple(
                    "v2:$[?!@.children[?@.flag]]",
                    "[{\"children\":[{\"flag\":false}]},{\"children\":[]},{}]",
                    "[{\"children\":[{\"flag\":false}]}]",
                ),
                Triple(
                    "$.items[?$.enabled==false && @.ad]",
                    "{\"enabled\":false,\"items\":[{\"ad\":0},{\"safe\":true}]}",
                    "{\"enabled\":false,\"items\":[{\"safe\":true}]}",
                ),
                Triple(
                    "$[?@.label==\"a] && (\" || @.flag]",
                    "[{\"label\":\"a] && (\"},{\"label\":\"safe\"}]",
                    "[{\"label\":\"safe\"}]",
                ),
            )
        val output = cases.map { (query, input, expected) ->
            assertNotNull(query, JsonEditing.compile(query))
            val engine = KotlinFilterEngine()
            engine.replace(listOf("site.example##+js(json-edit, `$query`)"))
            assertEquals(query, 1, engine.scriptlets("https://site.example/").size)
            val source = requireNotNull(engine.scriptletInjection()).source
            JsonArray(listOf(query, input, expected, source).map(::JsonPrimitive))
        }
        System.getenv("SCRIPTLET_OUTPUT_DIR")?.let { directory ->
            File(directory).mkdirs()
            File(directory, "jsonpath-extensions.json")
                .writeText(JsonArray(output).toString())
        }
    }

    @Test
    fun malformedAndExecutableFormsRemainRejected() {
        for (query in
            listOf(
                "$[1:2:3:4]",
                "$[01:2]",
                "$[-0:2]",
                "$[::+1]",
                "$[9007199254740992:]",
                "$[?@.flag &&]",
                "$[?|| @.flag]",
                "$[?(@.flag]",
                "$[?@.flag)]",
                "$[?@.flag & @.other]",
                "$[?@.flag || alert(1)]",
                "$[?@.flag == {\"x\":1}]",
                "$[?@.flag == @.other]",
                "$[?@.constructor || @.flag]",
                "$[?@['__proto__'] || @.flag]",
                "$[?@.prototype]",
            )) {
            assertNull(query, JsonEditing.compile(query))
        }
        assertNull(
            JsonEditing.compile("$[?" + "(".repeat(20) + "@" + ")".repeat(20) + "]")
        )
        for (method in
            listOf("eval", "Function", "execScript", "setTimeout", "setInterval")) {
            val query = "$.x=call([null,\"$method\",\"alert(1)\"])"
            assertNull(method, JsonEditing.compile(query, true))
        }
    }

    @Test
    fun extendedProgramsRestoreWithoutChangingTrustOrCanonicalQueries() {
        val text =
            "site.example##+js(trusted-json-edit, `$.items[?@.ad || @.sponsor]=false`)"
        val sources = listOf(FilterSource(text, true))
        val cache = File(folder.root, "jsonpath.bin")
        val original = KotlinFilterEngine(cache).apply { replaceSources(sources) {} }
        val stages = mutableListOf<RuleLoadStage>()
        val restored =
            KotlinFilterEngine(cache).apply { replaceSources(sources, stages::add) }
        assertFalse(RuleLoadStage.COMPILING in stages)
        assertEquals(original.scriptletInjection(), restored.scriptletInjection())
        val instruction = restored.scriptlets("https://site.example/").single()
        assertNull(
            ScriptletParser.validated(instruction.type, instruction.arguments)
        )
        val tampered = instruction.arguments.toMutableList().apply { this[1] = "[]" }
        assertNull(ScriptletParser.validated(instruction.type, tampered, true))
    }
}
