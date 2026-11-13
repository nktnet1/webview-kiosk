package uk.nktnet.webviewkiosk.utils.webview

import android.util.Log
import com.google.re2j.Pattern
import java.util.LinkedHashMap

internal enum class UrlRegexMatchResult {
    MATCH,
    NO_MATCH,
    ERROR,
}

/**
 * Match configured URL rules with RE2/J only. No Java/ICU fallback, worker threads,
 * or timeouts are involved in WebView navigation decisions. Invalid rules fail
 * closed rather than being ignored.
 */
internal object UrlRegexMatcher {
    private const val TAG = "UrlRegexMatcher"
    internal const val MAX_PATTERN_LENGTH = 4096
    private const val MAX_URL_LENGTH = 65536
    private const val MAX_COMPILED_RULES = 256

    private sealed interface CompiledRule {
        data class Valid(val pattern: Pattern) : CompiledRule

        data object Invalid : CompiledRule
    }

    // Keep the compiled cache bounded and safe for both WebView and PDF
    // interception threads. Never hold the lock while matching a URL.
    private val compiled =
        object : LinkedHashMap<String, CompiledRule>(16, 0.75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<String, CompiledRule>?
            ): Boolean = size > MAX_COMPILED_RULES
        }

    fun match(source: String, url: String): UrlRegexMatchResult {
        if (source.length > MAX_PATTERN_LENGTH || url.length > MAX_URL_LENGTH) {
            Log.w(TAG, "URL rule or URL exceeds the evaluation size limit")
            return UrlRegexMatchResult.ERROR
        }

        val rule =
            synchronized(compiled) {
                compiled.getOrPut(source) {
                    try {
                        CompiledRule.Valid(Pattern.compile(source))
                    } catch (_: RuntimeException) {
                        // Do not expose configured patterns or visited URLs.
                        Log.w(TAG, "Unsupported or invalid RE2/J URL rule")
                        CompiledRule.Invalid
                    }
                }
            }
        return when (rule) {
            is CompiledRule.Valid ->
                try {
                    if (rule.pattern.matcher(url).find()) {
                        UrlRegexMatchResult.MATCH
                    } else {
                        UrlRegexMatchResult.NO_MATCH
                    }
                } catch (error: RuntimeException) {
                    Log.w(TAG, "RE2/J URL rule evaluation failed", error)
                    UrlRegexMatchResult.ERROR
                }
            CompiledRule.Invalid -> UrlRegexMatchResult.ERROR
        }
    }
}
