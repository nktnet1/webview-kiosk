package uk.nktnet.webviewkiosk.utils.webview

import android.util.Log
import com.google.re2j.Pattern

internal enum class UrlRegexMatchResult {
    MATCH,
    NO_MATCH,
    ERROR,
}

internal object UrlRegexMatcher {
    private const val TAG = "UrlRegexMatcher"
    private const val MAX_CACHED_RULES = 4096

    private sealed interface CompiledRule {
        data class Valid(val pattern: Pattern) : CompiledRule

        data object Invalid : CompiledRule
    }

    internal class RuleSet
    internal constructor(
        val blacklist: List<String>,
        val whitelist: List<String>,
    ) {
        private val compiled = HashMap<String, CompiledRule>()

        internal val cachedRuleCount: Int
            get() = synchronized(compiled) { compiled.size }

        fun match(source: String, url: String): UrlRegexMatchResult {
            val rule =
                synchronized(compiled) {
                    compiled[source]
                        ?: run {
                            val result =
                                try {
                                    CompiledRule.Valid(Pattern.compile(source))
                                } catch (_: RuntimeException) {
                                    // Avoid logging configured patterns or visited
                                    // URLs.
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
                            if (compiled.size < MAX_CACHED_RULES) {
                                compiled[source] = result
                            }
                            result
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
                    } catch (_: StackOverflowError) {
                        Log.w(TAG, "RE2/J URL rule evaluation exhausted the stack")
                        UrlRegexMatchResult.ERROR
                    }
                CompiledRule.Invalid -> UrlRegexMatchResult.ERROR
            }
        }
    }

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
