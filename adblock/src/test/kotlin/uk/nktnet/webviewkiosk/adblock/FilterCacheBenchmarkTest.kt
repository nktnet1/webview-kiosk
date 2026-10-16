package uk.nktnet.webviewkiosk.adblock

import java.io.File
import kotlin.system.measureNanoTime
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import uk.nktnet.webviewkiosk.adblock.engine.CompiledFilterCache
import uk.nktnet.webviewkiosk.adblock.engine.FilterCompiler

/** Opt-in host measurements; these are not Android startup timings or assertions. */
class FilterCacheBenchmarkTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun measureParsingBinaryRestoreAndIndexConstructionSeparately() {
        assumeTrue(System.getenv("FILTER_CACHE_BENCHMARK") == "1")
        val lists =
            listOf(
                (0 until 20_000).joinToString("\n") { "||ads$it.example^" },
                (0 until 10_000).joinToString("\n") {
                    "||cdn$it.example/advert\$script,domain=site$it.example"
                },
                (0 until 10_000).joinToString("\n") { "site$it.example##.advert" },
                (0 until 1_000).joinToString("\n") {
                    "site$it.example##+js(json-prune-fetch-response, ads)"
                },
            )
        val file = File(folder.root, "benchmark.bin")
        val cache = CompiledFilterCache(file)
        val key = cache.key(lists)
        val rules = FilterCompiler.parse(lists)
        cache.write(key, rules)
        val parse = mutableListOf<Long>()
        val restore = mutableListOf<Long>()
        val index = mutableListOf<Long>()
        repeat(10) { round ->
            val p = measureNanoTime { FilterCompiler.parse(lists) }
            val r = measureNanoTime { requireNotNull(cache.read(key)) }
            val i = measureNanoTime { rules.build() }
            if (round >= 3) {
                parse += p
                restore += r
                index += i
            }
        }
        fun median(values: List<Long>) =
            values.sorted()[values.size / 2] / 1_000_000.0
        println(
            "CACHE_BENCHMARK rules=41000 bytes=${file.length()} " +
                "parse_ms=${median(parse)} restore_ms=${median(restore)} index_ms=${median(index)}"
        )
    }
}
