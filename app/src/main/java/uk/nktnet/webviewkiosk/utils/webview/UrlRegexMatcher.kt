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
    // RE2/J avoids backtracking, but its NFA can still perform work proportional
    // to program size * input length. Cap the estimated work across all rules
    // considered for a single URL, including whitelist rules.
    private const val MAX_MATCH_WORK = 24_000_000L

    internal class MatchBudget {
        private var remaining = MAX_MATCH_WORK

        fun charge(estimatedCost: Int, urlLength: Int): Boolean {
            val work =
                estimatedCost.coerceAtLeast(1).toLong() * urlLength.coerceAtLeast(1)
            if (work > remaining) {
                return false
            }
            remaining -= work
            return true
        }
    }

    private sealed interface CompiledRule {
        data class Valid(
            val pattern: Pattern,
            val cost: Int,
            val anchoredPrefix: String?,
        ) : CompiledRule

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

        fun match(
            source: String,
            url: String,
            budget: MatchBudget = MatchBudget(),
        ): UrlRegexMatchResult {
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
                                        CompiledRule.Valid(
                                            Pattern.compile(source),
                                            estimatedCost,
                                            UrlRegexComplexity.anchoredLiteralPrefix(
                                                source
                                            ),
                                        )
                                    } catch (_: RuntimeException) {
                                        // Do not expose configured patterns or
                                        // visited URLs.
                                        Log.w(
                                            TAG,
                                            "Unsupported or invalid RE2/J URL rule",
                                        )
                                        CompiledRule.Invalid
                                    } catch (_: StackOverflowError) {
                                        Log.w(
                                            TAG,
                                            "RE2/J URL rule compilation exhausted the stack",
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
                is CompiledRule.Valid -> {
                    if (
                        rule.anchoredPrefix != null &&
                            !url.startsWith(rule.anchoredPrefix)
                    ) {
                        // An anchored pattern cannot match if its mandatory
                        // literal prefix differs. Do not consume the budget.
                        UrlRegexMatchResult.NO_MATCH
                    } else if (!budget.charge(rule.cost, url.length)) {
                        Log.w(
                            TAG,
                            "RE2/J URL rule exceeds the evaluation work budget",
                        )
                        UrlRegexMatchResult.ERROR
                    } else {
                        try {
                            if (rule.pattern.matcher(url).find()) {
                                UrlRegexMatchResult.MATCH
                            } else {
                                UrlRegexMatchResult.NO_MATCH
                            }
                        } catch (error: RuntimeException) {
                            Log.w(TAG, "RE2/J URL rule evaluation failed", error)
                            UrlRegexMatchResult.ERROR
                        } catch (_: StackOverflowError) {
                            Log.w(
                                TAG,
                                "RE2/J URL rule evaluation exhausted the stack",
                            )
                            UrlRegexMatchResult.ERROR
                        }
                    }
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
