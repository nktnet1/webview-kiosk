package uk.nktnet.webviewkiosk.adblock.engine

import java.io.File
import uk.nktnet.webviewkiosk.adblock.FilterSource
import uk.nktnet.webviewkiosk.adblock.RuleLoadStage

/** Per-source records retain badfilter data; only the final merge applies it. */
internal class FilterSourceCache(file: File, private val version: Int) {
    private val directory = File(file.parentFile, "${file.name}.sources")

    class Entry(
        val file: File,
        val key: ByteArray,
        val rules: CompiledRuleSet,
        val restored: Boolean,
    )

    fun load(
        sources: List<FilterSource>,
        onProgress: (RuleLoadStage) -> Unit,
    ): List<Entry> {
        var total = 0
        return sources.map { source ->
            val trusted =
                if (source.trusted) {
                    setOf(0)
                } else {
                    emptySet()
                }
            val key =
                CompiledFilterCache(directory).key(listOf(source.text), trusted)
            val name = key.joinToString("") { "%02x".format(it) } + ".bin"
            val target = File(directory, name)
            val saved =
                CompiledFilterCache(target, version).read(
                    key,
                    source.trusted,
                    FilterCompiler.MAX_RULES - total,
                ) {
                    onProgress(RuleLoadStage.RESTORING)
                }
            val rules =
                saved
                    ?: run {
                        onProgress(RuleLoadStage.COMPILING)
                        FilterCompiler.parseSource(
                            source,
                            FilterCompiler.MAX_RULES - total,
                        )
                    }
            total += rules.totalRules
            Entry(target, key, rules, saved != null)
        }
    }

    fun save(entries: List<Entry>) {
        entries
            .filterNot { it.restored }
            .forEach {
                CompiledFilterCache(it.file, version).write(it.key, it.rules)
            }
        // Retain only current inputs, including recoverable backups. Edits cannot
        // accumulate an unbounded history of compiled subscriptions.
        val active = entries.map { it.file.name }.toSet()
        directory
            .listFiles()
            ?.filter {
                it.name.removeSuffix(".bak").removeSuffix(".new") !in active
            }
            ?.forEach { it.delete() }
        // Bound aggregate disk use as well as the individual files. Evicted
        // sources recompile normally on a later aggregate-cache miss.
        val files = directory.listFiles()?.toList().orEmpty()
        var bytes = files.sumOf { it.length() }
        files
            .filter { it.name.endsWith(".bin") }
            .forEach {
                if (bytes > 64L * 1024 * 1024) {
                    val size = it.length()
                    if (it.delete()) {
                        bytes -= size
                    }
                }
            }
    }
}
