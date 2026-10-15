package uk.nktnet.webviewkiosk.blocking

import uk.nktnet.webviewkiosk.blocking.engine.CompiledFilters
import uk.nktnet.webviewkiosk.blocking.engine.FilterCompiler

/** Compile on the loader thread, then publish one immutable snapshot to all readers. */
internal class KotlinFilterEngine : NetworkFilterEngine, FilterListValidator {
    @Volatile private var compiled = CompiledFilters.EMPTY

    override fun replace(lists: List<String>) {
        val replacement = FilterCompiler.compile(lists)
        compiled = replacement
    }

    override fun clear() { compiled = CompiledFilters.EMPTY }

    override fun validate(text: String) {
        validateFilterFileFormat(text)
        FilterCompiler.validate(text)
    }

    override fun shouldBlock(url: String, sourceUrl: String, type: String, method: String): Boolean =
        compiled.shouldBlock(url, sourceUrl, type, method)

    override fun hasCosmeticRules(): Boolean = compiled.hasCosmetics

    override fun cosmeticSelectors(url: String, classes: List<String>, ids: List<String>): List<String> =
        compiled.cosmeticSelectors(url, classes, ids)

    override fun unsupportedRuleCount(): Int = compiled.skippedRules
}
