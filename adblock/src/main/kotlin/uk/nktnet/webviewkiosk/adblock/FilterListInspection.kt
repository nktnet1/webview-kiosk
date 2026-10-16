package uk.nktnet.webviewkiosk.adblock

import uk.nktnet.webviewkiosk.adblock.engine.FilterParser
import uk.nktnet.webviewkiosk.adblock.engine.ParsedFilter

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
    return FilterRuleCounts(
        network,
        cosmetic,
        unsupported,
        invalid,
    )
}

/**
 * Include expansion runs on the download worker, never on a WebView request thread.
 */
fun expandFilterListIncludes(text: String, loader: (String) -> String): String =
    FilterParser.expandIncludes(text, loader)
