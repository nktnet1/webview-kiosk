package uk.nktnet.webviewkiosk.adblock

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import uk.nktnet.webviewkiosk.adblock.engine.FilterCompiler
import uk.nktnet.webviewkiosk.adblock.engine.FilterParser
import uk.nktnet.webviewkiosk.adblock.engine.ParsedFilter
import uk.nktnet.webviewkiosk.adblock.engine.ScriptletIndex

/** Opt-in real-list measurement; the input bytes stay outside the repository. */
class ScriptletCorpusTest {
    @Test
    fun inspectAndBenchmarkSuppliedUblockCorpus() {
        val path = System.getenv("UBLOCK_FILTER_FILE")
        assumeTrue(
            "Set UBLOCK_FILTER_FILE to inspect a real subscription",
            path != null,
        )
        val file = File(path!!)
        val text = file.readText()
        val sha =
            MessageDigest.getInstance("SHA-256")
                .digest(file.readBytes())
                .joinToString("") { "%02x".format(it) }
        val lines = text.lines()
        val total = sortedMapOf<String, Int>()
        val supported = sortedMapOf<String, Int>()
        FilterParser.visit(text) { line, parsed ->
            val raw = lines[line - 1]
            if ("##+js(" in raw || "#@#+js(" in raw) {
                val type =
                    raw.substringAfter("+js(")
                        .substringBefore(',')
                        .substringBefore(')')
                total[type] = (total[type] ?: 0) + 1
                if (parsed is ParsedFilter.Scriptlet) {
                    supported[type] = (supported[type] ?: 0) + 1
                }
            }
        }
        val counts = inspectFilterList(text)
        // Pin this handover's exact input, while allowing the tool to inspect newer
        // lists.
        if (
            sha == "3bde7d558863ea2431b868f4d910a8d6d484486fbd576e464d0ecc85446ef3c6"
        ) {
            assertEquals(
                6064,
                counts.networkRules +
                    counts.cosmeticRules +
                    counts.scriptletRules +
                    counts.unsupportedRules +
                    counts.invalidRules,
            )
            assertEquals(0, counts.invalidRules)
            assertEquals(1432, counts.networkRules)
            assertEquals(1771, counts.cosmeticRules)
            assertTrue(counts.scriptletRules >= 1400)
        }
        val engine = KotlinFilterEngine()
        engine.validate(text)
        engine.replace(listOf(text))
        val compiled = FilterCompiler.parse(listOf(text))
        val domains = compiled.scriptlets.flatMap { it.domains.included }.distinct()
        val busiest =
            domains.maxByOrNull { engine.scriptlets("https://$it/").size }!!
        val times =
            (0..7)
                .map {
                    val start = System.nanoTime()
                    KotlinFilterEngine().replace(listOf(text))
                    (System.nanoTime() - start) / 1_000_000.0
                }
                .drop(1)
                .sorted()
        val lookup =
            (0..7)
                .map {
                    val start = System.nanoTime()
                    repeat(1000) { engine.scriptlets("https://$busiest/") }
                    (System.nanoTime() - start) / 1_000_000.0
                }
                .drop(1)
                .sorted()
        val injection = engine.scriptletInjection()!!
        println("CORPUS sha256=$sha counts=$counts")
        println("CORPUS totalScriptlets=${total.values.sum()} types=$total")
        println(
            "CORPUS supportedScriptlets=${supported.values.sum()} types=$supported"
        )
        println(
            "CORPUS compileMedianMs=${times[times.size / 2]} " +
                "lookupMedianUs=${lookup[lookup.size / 2]} busiestHost=$busiest " +
                "busiestInstructions=${engine.scriptlets("https://$busiest/").size} " +
                "sourceBytes=${injection.source.toByteArray().size} " +
                "origins=${injection.allowedOrigins.size}"
        )
        System.getenv("SCRIPTLET_OUTPUT_DIR")?.let { output ->
            val directory = File(output).apply { mkdirs() }
            File(directory, "ublock-injection.js").writeText(injection.source)
            val cases = domains.map { host ->
                "[${ScriptletIndex.json(host)},${engine.scriptlets("https://$host/").size}]"
            }
            File(directory, "ublock-hosts.json")
                .writeText(cases.joinToString(",", "[", "]"))
        }
    }
}
