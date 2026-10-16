package uk.nktnet.webviewkiosk.adblock.engine

import java.util.Locale
import okhttp3.HttpUrl

/** Validates rule text and builds the immutable indexes used by request threads. */
internal object FilterCompiler {
    internal const val MAX_RULES = 500_000

    fun validate(text: String) {
        var supported = 0
        var unsupported = 0
        var invalid = 0
        val errors = mutableListOf<String>()
        FilterParser.visit(text) { line, result ->
            when (result) {
                is ParsedFilter.Network,
                is ParsedFilter.Cosmetic -> supported++

                ParsedFilter.Unsupported -> unsupported++

                is ParsedFilter.Invalid -> {
                    invalid++
                    if (errors.size < 5) {
                        errors += "Line $line: ${result.message}"
                    }
                }
            }
        }
        require(supported + unsupported + invalid <= MAX_RULES) {
            "At most $MAX_RULES rules are supported"
        }
        require(invalid == 0) {
            "Invalid filter syntax:\n" +
                errors.joinToString("\n") +
                if (invalid > errors.size) {
                    "\n… and ${invalid - errors.size} more errors"
                } else {
                    ""
                }
        }
        require(supported > 0) {
            if (unsupported == 0) {
                "Filter list has no rules (only comments or blank lines)"
            } else {
                "Filter list has no supported network or CSS-hiding rules. " +
                    "Unsupported rules: $unsupported. No changes were saved."
            }
        }
    }

    fun parse(lists: List<String>): CompiledRuleSet {
        val networks = LinkedHashSet<NetworkIdentity>()
        val disabled = HashSet<NetworkIdentity>()
        val cosmetics = LinkedHashSet<CosmeticRule>()
        var skipped = 0
        var total = 0
        for (text in lists) {
            FilterParser.visit(text) { _, result ->
                require(++total <= MAX_RULES) {
                    "At most $MAX_RULES rules can be enabled together"
                }
                when (result) {
                    is ParsedFilter.Network -> {
                        if (result.rule.badfilter) {
                            disabled += result.rule.identity
                        } else {
                            networks += result.rule.identity
                        }
                    }

                    is ParsedFilter.Cosmetic -> cosmetics += result.rule

                    // Old saved lists can contain syntax this subset does not
                    // implement.
                    // Keep their useful rules; staged imports still reject malformed
                    // syntax.
                    else -> skipped++
                }
            }
        }
        networks.removeAll(disabled)
        return CompiledRuleSet(networks, cosmetics, skipped)
    }
}

/** Normalized, deduplicated rules. Disk restoration bypasses the text parser. */
internal data class CompiledRuleSet(
    val networks: Collection<NetworkIdentity>,
    val cosmetics: Collection<CosmeticRule>,
    val skippedRules: Int,
) {
    fun build(): CompiledFilters {
        if (networks.any { it.conditions.thirdParty != null }) {
            // Load OkHttp's existing public-suffix data on the compilation worker.
            httpAddress("https://example.com/")!!.topPrivateDomain()
        }
        return CompiledFilters(networks, cosmetics, skippedRules)
    }
}

private class RequestFacts(
    val target: HttpUrl,
    val source: HttpUrl,
    val type: Int,
    val method: String,
) {
    val url = target.toString()
    val targetHost = target.host.removeSuffix(".")
    val sourceHost = source.host.removeSuffix(".")
    val budget = MatchBudget()
    private var thirdParty: Boolean? = null

    fun matches(conditions: Conditions): Boolean {
        if (conditions.types != 0 && conditions.types and type == 0) {
            return false
        }
        if (
            conditions.excludedTypes and type != 0 ||
                !conditions.domains.matches(sourceHost)
        ) {
            return false
        }
        if (
            method in conditions.excludedMethods ||
                (conditions.methods.isNotEmpty() && method !in conditions.methods)
        ) {
            return false
        }
        if (conditions.thirdParty != null) {
            val party =
                thirdParty
                    ?: ((target.topPrivateDomain()?.removeSuffix(".")
                            ?: targetHost) !=
                            (source.topPrivateDomain()?.removeSuffix(".")
                                ?: sourceHost))
                        .also { thirdParty = it }
            if (party != conditions.thirdParty) {
                return false
            }
        }
        return true
    }
}

/**
 * Each rule lives in one index family. Only truly unindexable patterns use the small
 * fallback.
 */
private class NetworkIndex(rules: Collection<NetworkIdentity>) {
    private val hosts = HashMap<String, MutableList<NetworkIdentity>>()
    private val pages = HashMap<String, MutableList<NetworkIdentity>>()
    private val tokens = HashMap<Int, MutableList<NetworkIdentity>>()
    private val fallback = mutableListOf<NetworkIdentity>()

    init {
        for (rule in rules) {
            val pattern = rule.pattern
            when {
                pattern.hostIndex != null ->
                    hosts.getOrPut(pattern.hostIndex) { mutableListOf() }.add(rule)

                rule.conditions.domains.included.isNotEmpty() ->
                    rule.conditions.domains.included.forEach { domain ->
                        pages.getOrPut(domain) { mutableListOf() }.add(rule)
                    }

                pattern.indexToken != null ->
                    tokens.getOrPut(pattern.indexToken) { mutableListOf() }.add(rule)

                else -> fallback += rule
            }
        }
        require(fallback.size <= 2048) {
            "Too many URL patterns without a hostname, domain restriction, " +
                "or four-character literal token (maximum 2048)"
        }
    }

    /**
     * Return early only when the caller has a conclusive answer, e.g. an important
     * block.
     */
    fun any(facts: RequestFacts, visitor: (NetworkIdentity) -> Boolean): Boolean {
        fun check(rule: NetworkIdentity): Boolean =
            !facts.budget.consume(16) || visitor(rule)
        if (hosts.isNotEmpty()) {
            hostSuffixes(facts.targetHost) { host ->
                if (hosts[host]?.any(::check) == true) {
                    return true
                }
            }
        }
        if (pages.isNotEmpty()) {
            hostSuffixes(facts.sourceHost) { host ->
                if (pages[host]?.any(::check) == true) {
                    return true
                }
            }
        }
        if (tokens.isNotEmpty() && facts.url.length >= 4) {
            // Allocate a dedup set only for token buckets actually present in this
            // URL.
            var seen: MutableSet<Int>? = null
            for (start in 0..facts.url.length - 4) {
                if (!facts.budget.consume()) {
                    return true
                }
                val hash = tokenHash(facts.url, start)
                val candidates = tokens[hash] ?: continue
                val visited = seen ?: HashSet<Int>().also { seen = it }
                if (visited.add(hash) && candidates.any(::check)) {
                    return true
                }
            }
        }
        return fallback.any(::check)
    }
}

/**
 * Immutable after construction. Readers never parse lists or acquire an engine lock.
 */
internal class CompiledFilters(
    networks: Collection<NetworkIdentity>,
    cosmetics: Collection<CosmeticRule>,
    val skippedRules: Int,
) {
    private val blockedHosts = HashSet<String>()
    private val allowedHosts = HashSet<String>()
    private val importantHosts = HashSet<String>()
    private val requests: NetworkIndex
    private val hideExceptions: NetworkIndex
    private val specific = HashMap<String, MutableList<CosmeticRule>>()
    private val generic = mutableListOf<CosmeticRule>()
    private val classes = HashMap<String, MutableList<CosmeticRule>>()
    private val ids = HashMap<String, MutableList<CosmeticRule>>()
    private val specificExceptions = HashMap<String, MutableList<CosmeticRule>>()
    private val genericExceptions = mutableListOf<CosmeticRule>()
    val hasCosmetics = cosmetics.any { !it.exception }

    init {
        val ordinary = mutableListOf<NetworkIdentity>()
        val hiding = mutableListOf<NetworkIdentity>()
        for (rule in networks) {
            val host = rule.pattern.plainHost
            when {
                rule.hide != 0 -> hiding += rule

                host != null && rule.conditions == Conditions.ANY ->
                    when {
                        rule.important -> importantHosts += host
                        rule.exception -> allowedHosts += host
                        else -> blockedHosts += host
                    }

                else -> ordinary += rule
            }
        }
        requests = NetworkIndex(ordinary)
        hideExceptions = NetworkIndex(hiding)
        for (rule in cosmetics) {
            when {
                rule.exception -> {
                    if (rule.domains.included.isEmpty()) {
                        genericExceptions += rule
                    } else {
                        rule.domains.included.forEach { domain ->
                            specificExceptions
                                .getOrPut(domain) { mutableListOf() }
                                .add(rule)
                        }
                    }
                }

                rule.domains.included.isNotEmpty() ->
                    rule.domains.included.forEach { domain ->
                        specific.getOrPut(domain) { mutableListOf() }.add(rule)
                    }

                else -> {
                    val token = genericToken(rule.selector)
                    if (token == null) {
                        generic += rule
                    } else {
                        (if (token.first == '.') {
                                classes
                            } else {
                                ids
                            })
                            .getOrPut(token.second) { mutableListOf() }
                            .add(rule)
                    }
                }
            }
        }
    }

    fun shouldBlock(
        url: String,
        sourceUrl: String,
        type: String,
        method: String,
    ): Boolean {
        val target = httpAddress(url) ?: return false
        val source = httpAddress(sourceUrl) ?: return false
        var blocked = false
        var allowed = false
        hostSuffixes(target.host.removeSuffix(".")) { host ->
            if (host in importantHosts) {
                return true
            }
            if (host in blockedHosts) {
                blocked = true
            }
            if (host in allowedHosts) {
                allowed = true
            }
        }
        val facts =
            RequestFacts(
                target,
                source,
                ResourceTypes.request(type),
                method.uppercase(Locale.ROOT),
            )
        val important =
            requests.any(facts) { rule ->
                if (
                    (rule.important ||
                        (rule.exception && !allowed) ||
                        (!rule.exception && !blocked)) &&
                        facts.matches(rule.conditions) &&
                        rule.pattern.matches(target, facts.url, facts.budget)
                ) {
                    if (rule.important) {
                        true
                    } else {
                        if (rule.exception) {
                            allowed = true
                        } else {
                            blocked = true
                        }
                        false
                    }
                } else {
                    false
                }
            }
        return !facts.budget.exhausted && (important || (blocked && !allowed))
    }

    fun cosmeticSelectors(
        url: String,
        pageClasses: List<String>,
        pageIds: List<String>,
    ): List<String> {
        if (!hasCosmetics) {
            return emptyList()
        }
        val address = httpAddress(url) ?: return emptyList()
        val host = address.host.removeSuffix(".")
        val facts =
            RequestFacts(address, address, ResourceTypes.request("document"), "GET")
        var hide = 0
        hideExceptions.any(facts) { rule ->
            if (
                facts.matches(rule.conditions) &&
                    rule.pattern.matches(address, facts.url, facts.budget)
            ) {
                hide = maxOf(hide, rule.hide)
            }
            hide == 2
        }
        if (hide == 2 || facts.budget.exhausted) {
            return emptyList()
        }
        val exceptions = HashSet<String>()
        fun except(rule: CosmeticRule) {
            if (rule.domains.matches(host)) {
                exceptions += rule.selector
            }
        }
        genericExceptions.forEach(::except)
        hostSuffixes(host) { domain ->
            specificExceptions[domain]?.forEach(::except)
        }
        val result = LinkedHashSet<String>()
        var bytes = 0
        fun add(rule: CosmeticRule) {
            if (
                rule.selector in exceptions ||
                    rule.selector in result ||
                    !rule.domains.matches(host) ||
                    result.size >= 2048
            ) {
                return
            }
            val length = rule.selector.toByteArray(Charsets.UTF_8).size
            if (bytes + length > 256 * 1024) {
                return
            }
            bytes += length
            result += rule.selector
        }
        hostSuffixes(host) { domain -> specific[domain]?.forEach(::add) }
        if (hide == 0) {
            generic.forEach(::add)
            pageClasses.take(256).forEach { token -> classes[token]?.forEach(::add) }
            pageIds.take(256).forEach { token -> ids[token]?.forEach(::add) }
        }
        return result.toList()
    }

    companion object {
        val EMPTY = CompiledFilters(emptyList(), emptyList(), 0)

        private fun genericToken(selector: String): Pair<Char, String>? {
            // A leading class/ID is mandatory unless the selector contains an OR.
            // Complex/escaped selectors take the unconditional CSS path instead.
            if (
                selector.firstOrNull() !in
                    listOf(
                        '.',
                        '#',
                    ) || ',' in selector || '\\' in selector
            ) {
                return null
            }
            val end =
                selector
                    .indexOfFirst {
                        it != '.' &&
                            it != '#' &&
                            !it.isLetterOrDigit() &&
                            it !in "_-"
                    }
                    .let {
                        if (it < 0) {
                            selector.length
                        } else {
                            it
                        }
                    }
            // Chained selectors .a.b and #a.b must be indexed by their first token.
            val tokenEnd =
                (1 until end).firstOrNull {
                    selector[it] == '.' || selector[it] == '#'
                } ?: end
            return if (tokenEnd > 1 && tokenEnd <= 129) {
                selector[0] to selector.substring(1, tokenEnd)
            } else {
                null
            }
        }
    }
}
