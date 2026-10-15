package uk.nktnet.webviewkiosk.filterengine

import uk.nktnet.webviewkiosk.filterengine.engine.FilterParser
import uk.nktnet.webviewkiosk.filterengine.engine.ParsedFilter

/** Saved-rule counts before deduplication or precedence across lists. */
data class FilterRuleCounts(
    val networkRules: Int,
    val cosmeticRules: Int,
    val unsupportedRules: Int,
    val invalidRules: Int,
)

/** Inspect text without compiling it or changing an engine's active snapshot. */
fun inspectFilterList(text: String): FilterRuleCounts {
    var network = 0
    var cosmetic = 0
    var unsupported = 0
    var invalid = 0
    FilterParser.visit(text) { _, rule ->
        when (rule) {
            is ParsedFilter.Network -> network++
            is ParsedFilter.Cosmetic -> cosmetic++
            ParsedFilter.Unsupported -> unsupported++
            is ParsedFilter.Invalid -> invalid++
        }
    }
    return FilterRuleCounts(network, cosmetic, unsupported, invalid)
}
