package uk.nktnet.webviewkiosk.utils.webview

import android.util.Log
import com.google.re2j.Pattern

internal enum class UrlRegexMatchResult {
    MATCH,
    NO_MATCH,
    ERROR,
}

/**
 * Match configured URL rules with RE2/J only. Invalid or excessively complex rules
 * fail closed. No Java/ICU fallback, worker threads, or timeouts.
 */
internal object UrlRegexMatcher {
    private const val TAG = "UrlRegexMatcher"
    private const val MAX_CACHED_RULES = 4096
    private const val MAX_CACHED_COST = 65536

    private sealed interface CompiledRule {
        data class Valid(val pattern: Pattern) : CompiledRule

        data object Invalid : CompiledRule
    }

    /**
     * The cache belongs to a configuration, so 257 rules cannot evict each other.
     */
    internal class RuleSet
    internal constructor(
        val blacklist: List<String>,
        val whitelist: List<String>,
    ) {
        private val compiled = HashMap<String, CompiledRule>()
        private var cachedCost = 0

        internal val cachedRuleCount: Int
            get() = synchronized(compiled) { compiled.size }

        fun match(source: String, url: String): UrlRegexMatchResult {
            val rule =
                synchronized(compiled) {
                    compiled[source]
                        ?: run {
                            val estimatedCost =
                                UrlRegexComplexity.estimatedCost(source)
                            val value =
                                if (estimatedCost == null) {
                                    Log.w(
                                        TAG,
                                        "RE2/J URL rule exceeds the compilation complexity budget",
                                    )
                                    CompiledRule.Invalid
                                } else {
                                    try {
                                        CompiledRule.Valid(Pattern.compile(source))
                                    } catch (_: RuntimeException) {
                                        // Do not expose configured patterns or
                                        // visited URLs.
                                        Log.w(
                                            TAG,
                                            "Unsupported or invalid RE2/J URL rule",
                                        )
                                        CompiledRule.Invalid
                                    }
                                }
                            // Keep the first rules hot without unbounded cache
                            // memory
                            // or LRU churn when the configured list is very large.
                            val cost = estimatedCost ?: 1
                            if (
                                compiled.size < MAX_CACHED_RULES &&
                                    cachedCost + cost <= MAX_CACHED_COST
                            ) {
                                compiled[source] = value
                                cachedCost += cost
                            }
                            value
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

    // Only the current configuration is retained. In-flight navigations keep
    // their own RuleSet reference; changes to settings replace the cache.
    private var activeRules: RuleSet? = null

    fun forRules(blacklist: List<String>, whitelist: List<String>): RuleSet =
        synchronized(this) {
            activeRules?.takeIf {
                it.blacklist == blacklist && it.whitelist == whitelist
            }
                ?: RuleSet(blacklist.toList(), whitelist.toList()).also {
                    activeRules = it
                }
        }
}
