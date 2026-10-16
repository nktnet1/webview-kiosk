package uk.nktnet.webviewkiosk.adblock

import java.io.File
import uk.nktnet.webviewkiosk.adblock.engine.CompiledFilterCache
import uk.nktnet.webviewkiosk.adblock.engine.CompiledFilters
import uk.nktnet.webviewkiosk.adblock.engine.FilterCompiler

/**
 * Compile on the loader thread, then publish one immutable snapshot to all readers.
 */
class KotlinFilterEngine(cacheFile: File? = null, cacheVersion: Int = 0) :
    NetworkFilterEngine, FilterListValidator {
    private val cache = cacheFile?.let {
        CompiledFilterCache(it, cacheVersion)
    }

    @Volatile private var compiled = CompiledFilters.EMPTY

    override fun replace(lists: List<String>) = replace(lists) {}

    override fun replace(lists: List<String>, onProgress: (RuleLoadStage) -> Unit) {
        onProgress(RuleLoadStage.CHECKING_CACHE)
        val key = cache?.key(lists)
        val saved =
            if (key != null) {
                cache.read(key) { onProgress(RuleLoadStage.RESTORING) }
            } else {
                null
            }
        val rules =
            saved
                ?: run {
                    onProgress(RuleLoadStage.COMPILING)
                    FilterCompiler.parse(lists)
                }
        onProgress(RuleLoadStage.INDEXING)
        val replacement = rules.build()
        if (saved == null && key != null) {
            onProgress(RuleLoadStage.SAVING_CACHE)
            cache.write(key, rules)
        }
        compiled = replacement
    }

    override fun clear() {
        compiled = CompiledFilters.EMPTY
    }

    override fun validate(text: String) {
        validateFilterFileFormat(text)
        FilterCompiler.validate(text)
    }

    override fun shouldBlock(
        url: String,
        sourceUrl: String,
        type: String,
        method: String,
    ): Boolean = compiled.shouldBlock(url, sourceUrl, type, method)

    override fun responseFor(
        url: String,
        sourceUrl: String,
        type: String,
        method: String,
    ): BlockingResponse = compiled.responseFor(url, sourceUrl, type, method)

    override fun hasCosmeticRules(): Boolean = compiled.hasCosmetics

    override fun cosmeticSelectors(
        url: String,
        classes: List<String>,
        ids: List<String>,
    ): List<String> = compiled.cosmeticSelectors(url, classes, ids)

    override fun cosmeticProcedures(url: String): List<CosmeticProcedure> =
        compiled.cosmeticProcedures(url)

    override fun unsupportedRuleCount(): Int = compiled.skippedRules
}
