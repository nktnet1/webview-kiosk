package uk.nktnet.webviewkiosk.adblock

import uk.nktnet.webviewkiosk.adblock.engine.FilterParser
import uk.nktnet.webviewkiosk.adblock.engine.ParsedFilter

/** Saved-rule counts before deduplication or precedence across lists. */
data class FilterRuleCounts(
    val networkRules: Int,
    val cosmeticRules: Int,
    val unsupportedRules: Int,
    val invalidRules: Int,
    val scriptletRules: Int = 0,
    val skipReasons: Map<FilterSkipReason, Int> = emptyMap(),
)

/** Inspect text without compiling it or changing an engine's active snapshot. */
fun inspectFilterList(text: String, trusted: Boolean = false): FilterRuleCounts {
    var network = 0
    var cosmetic = 0
    var unsupported = 0
    var invalid = 0
    var scriptlets = 0
    val reasons =
        java.util.EnumMap<FilterSkipReason, Int>(FilterSkipReason::class.java)
    FilterParser.visit(text, trusted) { _, rule ->
        when (rule) {
            is ParsedFilter.Network -> network++
            is ParsedFilter.Cosmetic -> cosmetic++
            is ParsedFilter.Scriptlet -> scriptlets++
            is ParsedFilter.Unsupported -> {
                unsupported++
                reasons[rule.reason] = (reasons[rule.reason] ?: 0) + 1
            }
            is ParsedFilter.Invalid -> {
                invalid++
                reasons[FilterSkipReason.INVALID] =
                    (reasons[FilterSkipReason.INVALID] ?: 0) + 1
            }
        }
    }
    return FilterRuleCounts(
        network,
        cosmetic,
        unsupported,
        invalid,
        scriptlets,
        reasons.toMap(),
    )
}

/**
 * Include expansion runs on the download worker, never on a WebView request thread.
 */
fun expandFilterListIncludes(text: String, loader: (String) -> String): String =
    FilterParser.expandIncludes(text, loader)
