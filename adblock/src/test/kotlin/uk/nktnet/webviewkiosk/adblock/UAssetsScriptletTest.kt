package uk.nktnet.webviewkiosk.adblock

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import uk.nktnet.webviewkiosk.adblock.engine.FilterParser
import uk.nktnet.webviewkiosk.adblock.engine.ParsedFilter
import uk.nktnet.webviewkiosk.adblock.engine.ScriptletParser

/**
 * Opt-in audit of physical rules, including optional lists and conditional branches.
 */
class UAssetsScriptletTest {
    @Test
    fun auditSuppliedUassetsScriptlets() {
        val directory = System.getenv("UASSETS_FILTERS_DIR")
        assumeTrue("Set UASSETS_FILTERS_DIR to audit uAssets", directory != null)
        val counts = sortedMapOf<String, IntArray>()
        File(directory!!)
            .listFiles()!!
            .filter { it.extension == "txt" }
            .forEach {
                val text = it.readText()
                val lines = text.lines()
                FilterParser.visit(text, trusted = true) { number, result ->
                    val line = lines[number - 1]
                    if ("##+js(" in line || "#@#+js(" in line) {
                        val expression = "+js(" + line.substringAfter("+js(")
                        val name =
                            expression
                                .substringAfter('(')
                                .substringBefore(',')
                                .substringBefore(')')
                        val count = counts.getOrPut(name) { IntArray(3) }
                        count[0]++
                        if (
                            ScriptletParser.parse(expression, trusted = true) != null
                        ) {
                            count[1]++
                        }
                        if (result is ParsedFilter.Scriptlet) {
                            count[2]++
                        }
                    }
                }
            }
        assertTrue(counts.isNotEmpty())
        counts.forEach { (name, count) ->
            println(
                "UASSETS $name total=${count[0]} arguments=${count[1]} rules=${count[2]}"
            )
        }
        println(
            "UASSETS totals=" + (0..2).map { i -> counts.values.sumOf { it[i] } }
        )
    }
}
