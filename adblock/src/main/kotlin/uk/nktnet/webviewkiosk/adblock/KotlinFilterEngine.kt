package uk.nktnet.webviewkiosk.adblock

import java.io.File
import uk.nktnet.webviewkiosk.adblock.engine.CompiledFilterCache
import uk.nktnet.webviewkiosk.adblock.engine.CompiledFilters
import uk.nktnet.webviewkiosk.adblock.engine.FilterCompiler
import uk.nktnet.webviewkiosk.adblock.engine.FilterSourceCache

/**
 * Compile on the loader thread, then publish one immutable snapshot to all readers.
 * cacheVersion is an optional engine compatibility salt, not an app build number.
 * Parsing, matching and binary-format changes must bump CompiledFilterCache.VERSION.
 */
class KotlinFilterEngine(cacheFile: File? = null, cacheVersion: Int = 0) :
    NetworkFilterEngine, FilterListValidator {
    private val cache = cacheFile?.let {
        CompiledFilterCache(it, cacheVersion)
    }

    private val sourceCache = cacheFile?.let { FilterSourceCache(it, cacheVersion) }

    @Volatile private var compiled = CompiledFilters.EMPTY

    override fun replace(lists: List<String>) = replace(lists) {}

    override fun replace(lists: List<String>, onProgress: (RuleLoadStage) -> Unit) =
        replaceSources(lists.map { FilterSource(it) }, onProgress)

    override fun replaceSources(
        sources: List<FilterSource>,
        onProgress: (RuleLoadStage) -> Unit,
    ) {
        onProgress(RuleLoadStage.CHECKING_CACHE)
        val trusted = sources.indices.filter { sources[it].trusted }.toSet()
        val key = cache?.key(sources.map { it.text }, trusted)
        val saved =
            if (key != null) {
                cache.read(key, trusted.isNotEmpty()) {
                    onProgress(RuleLoadStage.RESTORING)
                }
            } else {
                null
            }
        val cachedSources =
            if (saved == null) {
                sourceCache?.load(sources, onProgress)
            } else {
                null
            }
        var parsedTotal = 0
        val sourceRules =
            if (saved == null) {
                cachedSources?.map { it.rules }
                    ?: sources.map {
                        onProgress(RuleLoadStage.COMPILING)
                        FilterCompiler.parseSource(
                                it,
                                FilterCompiler.MAX_RULES - parsedTotal,
                            )
                            .also { rules -> parsedTotal += rules.totalRules }
                    }
            } else {
                null
            }
        val rules = saved ?: FilterCompiler.merge(requireNotNull(sourceRules))
        onProgress(RuleLoadStage.INDEXING)
        val replacement = rules.build()
        if (key != null && (saved == null || !replacement.restoredNetworkIndexes)) {
            onProgress(RuleLoadStage.SAVING_CACHE)
            cache.write(key, rules, replacement.networkIndexes)
            cachedSources?.let { sourceCache?.save(it) }
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

    override fun validateSource(source: FilterSource) {
        validateFilterFileFormat(source.text)
        FilterCompiler.validate(source.text, source.trusted)
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

    override fun removeQueryParameters(
        url: String,
        sourceUrl: String,
        type: String,
        method: String,
    ): String = compiled.removeQueryParameters(url, sourceUrl, type, method)

    override fun cosmeticSelectors(
        url: String,
        classes: List<String>,
        ids: List<String>,
    ): List<String> = compiled.cosmeticSelectors(url, classes, ids)

    override fun cosmeticProcedures(url: String): List<CosmeticProcedure> =
        compiled.cosmeticProcedures(url)

    override fun unsupportedRuleCount(): Int = compiled.skippedRules

    override fun scriptletInjection(): ScriptletInjection? =
        compiled.scriptletInjection

    override fun scriptlets(
        url: String,
        ancestorUrls: List<String>,
    ): List<ScriptletInstruction> = compiled.scriptlets(url, ancestorUrls)

    override fun scriptlets(url: String): List<ScriptletInstruction> =
        compiled.scriptlets(url)
}
