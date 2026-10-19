package uk.nktnet.webviewkiosk.adblock.engine

import java.util.Locale
import okhttp3.HttpUrl
import uk.nktnet.webviewkiosk.adblock.BlockingResponse
import uk.nktnet.webviewkiosk.adblock.CosmeticProcedure
import uk.nktnet.webviewkiosk.adblock.FilterSource
import uk.nktnet.webviewkiosk.adblock.ScriptletInjection
import uk.nktnet.webviewkiosk.adblock.ScriptletInstruction

/** Validates rule text and builds the immutable indexes used by request threads. */
internal object FilterCompiler {
    internal const val MAX_RULES = 2_000_000

    fun validate(text: String, trusted: Boolean = false) {
        var supported = 0
        var unsupported = 0
        var invalid = 0
        val errors = mutableListOf<String>()
        FilterParser.visit(text, trusted) { line, result ->
            when (result) {
                is ParsedFilter.Network,
                is ParsedFilter.Scriptlet,
                is ParsedFilter.Cosmetic -> supported++

                is ParsedFilter.Unsupported -> unsupported++

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
                "Filter list has no supported network, cosmetic, or scriptlet rules. " +
                    "Unsupported rules: $unsupported. No changes were saved."
            }
        }
    }

    fun parse(lists: List<String>): CompiledRuleSet {
        var total = 0
        return merge(
            lists.map {
                parseSource(FilterSource(it), MAX_RULES - total).also { rules ->
                    total += rules.totalRules
                }
            }
        )
    }

    fun parseSource(
        source: FilterSource,
        limit: Int = MAX_RULES,
    ): CompiledRuleSet {
        val networks = linkedSetOf<NetworkIdentity>()
        val disabled = linkedSetOf<NetworkIdentity>()
        val cosmetics = linkedSetOf<CosmeticRule>()
        val scriptlets = linkedSetOf<ScriptletRule>()
        var skipped = 0
        var total = 0
        FilterParser.visit(source.text, source.trusted) { _, result ->
            require(++total <= limit) {
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
                is ParsedFilter.Scriptlet -> {
                    scriptlets += result.rule
                    require(scriptlets.size <= ScriptletIndex.MAX_RULES) {
                        "Too many scriptlet rules"
                    }
                }
                else -> skipped++
            }
        }
        // Keep badfilter identities until all sources are merged. A later source
        // may disable a rule from an unchanged, independently cached source.
        return CompiledRuleSet(
            networks,
            cosmetics,
            skipped,
            scriptlets.toList(),
            disabled,
            totalRules = total,
        )
    }

    fun merge(sources: List<CompiledRuleSet>): CompiledRuleSet {
        require(sources.sumOf { it.totalRules.toLong() } <= MAX_RULES) {
            "At most $MAX_RULES rules can be enabled together"
        }
        val networks = linkedSetOf<NetworkIdentity>()
        val disabled = hashSetOf<NetworkIdentity>()
        val cosmetics = linkedSetOf<CosmeticRule>()
        val scriptlets = linkedSetOf<ScriptletRule>()
        sources.forEach { rules ->
            networks += rules.networks
            disabled += rules.disabledNetworks
            cosmetics += rules.cosmetics
            scriptlets += rules.scriptlets
        }
        require(scriptlets.size <= ScriptletIndex.MAX_RULES) {
            "At most ${ScriptletIndex.MAX_RULES} scriptlet rules can be enabled"
        }
        networks.removeAll(disabled)
        return CompiledRuleSet(
            networks,
            cosmetics,
            sources.sumOf { it.skippedRules },
            scriptlets.toList(),
            totalRules = sources.sumOf { it.totalRules },
        )
    }
}

/** Normalized, deduplicated rules. Disk restoration bypasses the text parser. */
internal data class CompiledRuleSet(
    val networks: Collection<NetworkIdentity>,
    val cosmetics: Collection<CosmeticRule>,
    val skippedRules: Int,
    val scriptlets: List<ScriptletRule> = emptyList(),
    val disabledNetworks: Collection<NetworkIdentity> = emptyList(),
    val networkIndexes: NetworkIndexSnapshot? = null,
    val pageIndexes: PageIndexSnapshot? = null,
    val totalRules: Int =
        networks.size +
            cosmetics.size +
            skippedRules +
            scriptlets.size +
            disabledNetworks.size,
) {
    fun build(): CompiledFilters {
        if (
            networks.any {
                it.conditions.thirdParty != null ||
                    it.conditions.domains.usesEntities ||
                    it.conditions.targets.usesEntities
            } ||
                cosmetics.any { it.domains.usesEntities } ||
                scriptlets.any {
                    it.domains.usesEntities ||
                        it.targets.any { target -> target.entity }
                }
        ) {
            // Load OkHttp's existing public-suffix data on the compilation worker.
            httpAddress("https://example.com/")!!.topPrivateDomain()
        }
        return CompiledFilters(
            networks,
            cosmetics,
            skippedRules,
            scriptlets,
            networkIndexes,
            pageIndexes,
        )
    }
}

private class RequestFacts(
    val target: HttpUrl,
    val source: HttpUrl?,
    val type: Int,
    val method: String,
    val url: String = target.toString(),
) {
    val targetHost = target.host.removeSuffix(".")
    val sourceHost = source?.host?.removeSuffix(".").orEmpty()
    val budget = MatchBudget()
    private var thirdParty: Boolean? = null
    private val targetDomain by lazy { target.topPrivateDomain() }
    private val sourceDomain by lazy { source?.topPrivateDomain() }
    val sourceEntity: String? by lazy { entityName(sourceHost, sourceDomain) }

    fun matches(conditions: Conditions, ignoreInitiator: Boolean = false): Boolean {
        if (
            source == null &&
                !ignoreInitiator &&
                (conditions.domains != DomainScope.ANY ||
                    conditions.thirdParty != null)
        ) {
            return false
        }
        if (conditions.types != 0 && conditions.types and type == 0) {
            return false
        }
        if (conditions.excludedTypes and type != 0) {
            return false
        }
        // Only entity restrictions need the public suffix lookup; ordinary
        // host/domain rules stay on the cheap matching path.
        if (source != null && conditions.domains != DomainScope.ANY) {
            val domain =
                if (conditions.domains.usesEntities) {
                    sourceDomain
                } else {
                    null
                }
            if (!conditions.domains.matches(sourceHost, domain)) {
                return false
            }
        }
        if (conditions.targets != DomainScope.ANY) {
            val domain =
                if (conditions.targets.usesEntities) {
                    targetDomain
                } else {
                    null
                }
            if (!conditions.targets.matches(targetHost, domain)) {
                return false
            }
        }
        if (
            method in conditions.excludedMethods ||
                (conditions.methods.isNotEmpty() && method !in conditions.methods)
        ) {
            return false
        }
        if (source != null && conditions.thirdParty != null) {
            val party =
                thirdParty
                    ?: ((targetDomain ?: targetHost) != (sourceDomain ?: sourceHost))
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
private class NetworkIndex(
    private val rules: List<NetworkIdentity>,
    restored: NetworkRouting? = null,
) {
    val routing =
        restored?.takeIf { it.validFor(rules) } ?: NetworkRouting.build(rules)
    val restoredIndexes = routing === restored
    private val hosts = routing.hosts
    private val pages = routing.pages
    private val pageEntities = routing.entities
    private val tokens = routing.tokens
    private val fallback = routing.fallback

    /**
     * Return early only when the caller has a conclusive answer, e.g. an important
     * block.
     */
    fun any(facts: RequestFacts, visitor: (NetworkIdentity) -> Boolean): Boolean {
        fun check(index: Int): Boolean =
            !facts.budget.consume(16) || visitor(rules[index])
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
        if (pageEntities.isNotEmpty()) {
            facts.sourceEntity?.let { entity ->
                hostSuffixes(entity) { key ->
                    if (pageEntities[key]?.any(::check) == true) {
                        return true
                    }
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
    scriptletRules: List<ScriptletRule> = emptyList(),
    networkIndexes: NetworkIndexSnapshot? = null,
    pageIndexes: PageIndexSnapshot? = null,
) {
    private val indexingStartedAt = System.nanoTime()
    private val scriptletIndex =
        ScriptletIndex(scriptletRules, pageIndexes?.scriptlets)
    private val scriptletRoutingEndedAt = System.nanoTime()
    val scriptletInjection: ScriptletInjection? = scriptletIndex.injection()
    private val scriptletInjectionEndedAt = System.nanoTime()

    fun scriptlets(
        url: String,
        ancestors: List<String> = emptyList(),
    ): List<ScriptletInstruction> = scriptletIndex.instructions(url, ancestors)

    private val blockedHosts = HashSet<String>()
    private val allowedHosts = HashSet<String>()
    private val importantHosts = HashSet<String>()
    private val unknownInitiatorExceptions = networks.filter {
        it.exception &&
            !it.redirectOnly &&
            it.hide == 0 &&
            it.removeParam == null &&
            (it.conditions.domains != DomainScope.ANY ||
                it.conditions.thirdParty != null)
    }
    private val requests: NetworkIndex
    private val redirectExceptions: NetworkIndex
    private val hideExceptions: NetworkIndex
    private val parameterRules: NetworkIndex
    private val cosmeticRules = cosmetics.toList()
    private val cosmeticRoutingStartedAt = System.nanoTime()
    private val cosmeticRouting =
        pageIndexes?.cosmetics?.takeIf {
            CosmeticRouting.validFor(it, cosmeticRules)
        } ?: CosmeticRouting.build(cosmeticRules)
    private val cosmeticRoutingEndedAt = System.nanoTime()
    private val restoredCosmeticIndexes = cosmeticRouting === pageIndexes?.cosmetics

    private fun bucket(kind: CosmeticBucket): Map<String, IntArray> =
        cosmeticRouting.tables[kind.ordinal]

    private val specific = bucket(CosmeticBucket.HOST)
    private val generic = bucket(CosmeticBucket.GENERIC)[""] ?: intArrayOf()
    private val classes = bucket(CosmeticBucket.CLASS)
    private val ids = bucket(CosmeticBucket.ID)
    private val specificExceptions = bucket(CosmeticBucket.EXCEPTION_HOST)
    private val entitySpecific = bucket(CosmeticBucket.ENTITY)
    private val entityExceptions = bucket(CosmeticBucket.EXCEPTION_ENTITY)
    private val procedures = bucket(CosmeticBucket.PROCEDURE_HOST)
    private val entityProcedures = bucket(CosmeticBucket.PROCEDURE_ENTITY)
    private val procedureExceptions = bucket(CosmeticBucket.PROCEDURE_EXCEPTION_HOST)
    private val entityProcedureExceptions =
        bucket(CosmeticBucket.PROCEDURE_EXCEPTION_ENTITY)
    private val genericExceptions =
        bucket(CosmeticBucket.GENERIC_EXCEPTION)[""] ?: intArrayOf()
    val hasCosmetics = cosmetics.any { !it.exception }
    private val cosmeticsNeedEntities = cosmetics.any { it.domains.usesEntities }
    private var networkPartitionNanos = 0L
    private var networkRoutingNanos = 0L

    init {
        val partitionStartedAt = System.nanoTime()
        val ordinary = mutableListOf<NetworkIdentity>()
        val hiding = mutableListOf<NetworkIdentity>()
        val redirecting = mutableListOf<NetworkIdentity>()
        val parameters = mutableListOf<NetworkIdentity>()
        for (rule in networks) {
            val host = rule.pattern.plainHost
            when {
                rule.removeParam != null -> parameters += rule
                rule.hide != 0 -> hiding += rule
                rule.exception && rule.redirectOnly -> redirecting += rule

                host != null &&
                    rule.conditions == Conditions.ANY &&
                    rule.redirect == null &&
                    !rule.redirectOnly ->
                    when {
                        rule.important -> importantHosts += host
                        rule.exception -> allowedHosts += host
                        else -> blockedHosts += host
                    }

                else -> ordinary += rule
            }
        }
        networkPartitionNanos = System.nanoTime() - partitionStartedAt
        val routingStartedAt = System.nanoTime()
        requests = NetworkIndex(ordinary, networkIndexes?.requests)
        hideExceptions = NetworkIndex(hiding, networkIndexes?.hiding)
        redirectExceptions = NetworkIndex(redirecting, networkIndexes?.redirects)
        parameterRules = NetworkIndex(parameters, networkIndexes?.parameters)
        networkRoutingNanos = System.nanoTime() - routingStartedAt
    }

    /** Diagnostic only: no routing data is omitted or trusted without validation. */
    internal fun indexTimingBreakdown(totalNanos: Long): String {
        val phases =
            listOf(
                "SCRIPTLET_ROUTING" to (scriptletRoutingEndedAt - indexingStartedAt),
                "SCRIPTLET_INJECTION" to
                    (scriptletInjectionEndedAt - scriptletRoutingEndedAt),
                "COSMETIC_ROUTING" to
                    (cosmeticRoutingEndedAt - cosmeticRoutingStartedAt),
                "NETWORK_PARTITION" to networkPartitionNanos,
                "NETWORK_ROUTING" to networkRoutingNanos,
            )
        val other = (totalNanos - phases.sumOf { it.second }).coerceAtLeast(0L)
        return (phases + ("OTHER" to other)).joinToString(" ") { (name, nanos) ->
            "$name=${nanos / 1_000_000}ms"
        }
    }

    val pageIndexes: PageIndexSnapshot
        get() = PageIndexSnapshot(cosmeticRouting, scriptletIndex.routing)

    val restoredPageIndexes: Boolean
        get() = restoredCosmeticIndexes && scriptletIndex.restoredIndexes

    val networkIndexes: NetworkIndexSnapshot
        get() =
            NetworkIndexSnapshot(
                requests.routing,
                hideExceptions.routing,
                redirectExceptions.routing,
                parameterRules.routing,
            )

    val restoredNetworkIndexes: Boolean
        get() =
            requests.restoredIndexes &&
                hideExceptions.restoredIndexes &&
                redirectExceptions.restoredIndexes &&
                parameterRules.restoredIndexes

    fun responseFor(
        url: String,
        sourceUrl: String,
        type: String,
        method: String,
    ): BlockingResponse {
        val socket =
            type == "websocket" &&
                (url.startsWith("ws://", true) || url.startsWith("wss://", true))
        val target =
            httpAddress(
                if (socket) {
                    "http" + url.substring(2)
                } else {
                    url
                }
            ) ?: return BlockingResponse.ALLOW
        if (ResourceTypes.request(type) == ResourceTypes.request("document")) {
            return documentResponse(target, method)
        }
        val source = httpAddress(sourceUrl)
        if (source == null && sourceUrl.isNotEmpty()) {
            return BlockingResponse.ALLOW
        }
        var blocked = false
        var allowed = false
        var redirect: BlockingResponse? = null
        var redirectRule: BlockingResponse? = null
        var importantRedirect: BlockingResponse? = null
        hostSuffixes(target.host.removeSuffix(".")) { host ->
            if (host in importantHosts) {
                return BlockingResponse.BLOCK
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
                if (socket) {
                    "ws" + target.toString().substring(4)
                } else {
                    target.toString()
                },
            )
        if (
            source == null &&
                unknownInitiatorExceptions.any { rule ->
                    // A contextual exception may allow this request. Without an
                    // initiator,
                    // conservatively honour it if every available fact matches.
                    !facts.budget.consume(16) ||
                        rule.exception &&
                            facts.matches(rule.conditions, ignoreInitiator = true) &&
                            rule.pattern.matches(target, facts.url, facts.budget)
                }
        ) {
            return BlockingResponse.ALLOW
        }
        val redirectExcepted =
            redirectExceptions.any(facts) { rule ->
                facts.matches(rule.conditions, ignoreInitiator = source == null) &&
                    rule.pattern.matches(target, facts.url, facts.budget)
            }
        val important =
            requests.any(facts) { rule ->
                (rule.important ||
                    rule.redirectOnly ||
                    (rule.exception && !allowed) ||
                    (!rule.exception && (!blocked || rule.redirect != null))) &&
                    facts.matches(rule.conditions) &&
                    rule.pattern.matches(target, facts.url, facts.budget) &&
                    if (rule.important) {
                        importantRedirect = rule.redirect
                        true
                    } else {
                        if (rule.exception) {
                            allowed = true
                        } else if (rule.redirectOnly) {
                            redirectRule = rule.redirect
                        } else {
                            blocked = true
                            if (rule.redirect != null) {
                                redirect = rule.redirect
                            }
                        }
                        false
                    }
            }
        if (facts.budget.exhausted) {
            return BlockingResponse.ALLOW
        }
        return when {
            important ->
                if (redirectExcepted) {
                    BlockingResponse.BLOCK
                } else {
                    importantRedirect ?: BlockingResponse.BLOCK
                }
            blocked && !allowed ->
                if (redirectExcepted) {
                    BlockingResponse.BLOCK
                } else {
                    redirect ?: redirectRule ?: BlockingResponse.BLOCK
                }
            else -> BlockingResponse.ALLOW
        }
    }

    private fun documentResponse(target: HttpUrl, method: String): BlockingResponse {
        var blocked = false
        var allowed = false
        hostSuffixes(target.host.removeSuffix(".")) { host ->
            if (host in importantHosts) {
                return BlockingResponse.BLOCK
            }
            if (host in blockedHosts) {
                blocked = true
            }
            if (host in allowedHosts) {
                allowed = true
            }
        }
        // A root document is its own first-party context. Never inherit the
        // previous page's hostname, including on redirects and an initial load.
        val facts =
            RequestFacts(
                target,
                target,
                ResourceTypes.request("document"),
                method.uppercase(Locale.ROOT),
            )
        val important =
            requests.any(facts) { rule ->
                !rule.redirectOnly &&
                    // Negated resource types are not typeless strict blocks.
                    // They apply to documents only with an explicit $document.
                    (rule.conditions.types != 0 ||
                        rule.conditions.excludedTypes == 0) &&
                    (rule.important ||
                        (rule.exception && !allowed) ||
                        (!rule.exception && !blocked)) &&
                    facts.matches(rule.conditions) &&
                    rule.pattern.matches(
                        target,
                        facts.url,
                        facts.budget,
                        hostnameOnly = !rule.exception && rule.conditions.types == 0,
                    ) &&
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
            }
        if (facts.budget.exhausted) {
            return BlockingResponse.ALLOW
        }
        // Document redirects are blocks, not script/image surrogate responses.
        // Important blocks override either generic or explicit exceptions.
        return if (important || (blocked && !allowed)) {
            BlockingResponse.BLOCK
        } else {
            BlockingResponse.ALLOW
        }
    }

    /** Query modifiers never act as blocking rules or whitelist network blocks. */
    fun removeQueryParameters(
        url: String,
        sourceUrl: String,
        type: String,
        method: String,
    ): String {
        if (url.length > 32768) {
            return url
        }
        val hash =
            url.indexOf('#').let {
                if (it < 0) {
                    url.length
                } else {
                    it
                }
            }
        val query = url.indexOf('?').takeIf { it in 0 until hash } ?: return url
        if (query + 1 == hash) {
            return url
        }
        val target = httpAddress(url) ?: return url
        val resourceType = ResourceTypes.request(type)
        val source =
            if (resourceType == ResourceTypes.request("document")) {
                target
            } else {
                httpAddress(sourceUrl) ?: return url
            }
        val facts =
            RequestFacts(target, source, resourceType, method.uppercase(Locale.ROOT))
        val normal = linkedMapOf<String, ParameterRemoval>()
        val important = linkedMapOf<String, ParameterRemoval>()
        val exceptions = hashSetOf<String>()
        parameterRules.any(facts) { rule ->
            if (
                facts.matches(rule.conditions) &&
                    rule.pattern.matches(target, facts.url, facts.budget)
            ) {
                val modifier = requireNotNull(rule.removeParam)
                when {
                    rule.important -> important[modifier.text] = modifier
                    rule.exception -> exceptions += modifier.text
                    else -> normal[modifier.text] = modifier
                }
            }
            false
        }
        if ("" in exceptions) {
            normal.clear()
        } else {
            exceptions.forEach(normal::remove)
        }
        val modifiers = normal + important
        if (modifiers.isEmpty() || facts.budget.exhausted) {
            return url
        }
        val parameters = url.substring(query + 1, hash).split('&')
        if (parameters.size > 1024) {
            return url
        }
        val remaining = ArrayList<String>(parameters.size)
        for (part in parameters) {
            val name = part.substringBefore('=')
            val value = part.substringAfter('=', "")
            var removed = false
            for (modifier in modifiers.values) {
                val matched = modifier.matches(name, value, facts.budget)
                if (facts.budget.exhausted) {
                    return url
                }
                if (matched) {
                    removed = true
                    break
                }
            }
            if (!removed) {
                remaining += part
            }
        }
        if (remaining.size == parameters.size) {
            return url
        }
        // Keep the spelling, order, duplicates and values of retained parameters.
        // Re-encoding a signed URL can change its meaning even after one removal.
        return url.substring(0, query) +
            (if (remaining.isEmpty()) {
                ""
            } else {
                "?" + remaining.joinToString("&")
            }) +
            url.substring(hash)
    }

    fun shouldBlock(
        url: String,
        sourceUrl: String,
        type: String,
        method: String,
    ): Boolean = responseFor(url, sourceUrl, type, method) != BlockingResponse.ALLOW

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
        val registeredDomain =
            if (
                entitySpecific.isNotEmpty() ||
                    entityExceptions.isNotEmpty() ||
                    cosmeticsNeedEntities
            ) {
                address.topPrivateDomain()
            } else {
                null
            }
        val entity = entityName(host, registeredDomain)
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
            if (rule.domains.matches(host, registeredDomain)) {
                exceptions += rule.selector
            }
        }
        genericExceptions.forEach { except(cosmeticRules[it]) }
        hostSuffixes(host) { domain ->
            specificExceptions[domain]?.forEach { except(cosmeticRules[it]) }
        }
        entity?.let { value ->
            hostSuffixes(value) { key ->
                entityExceptions[key]?.forEach { except(cosmeticRules[it]) }
            }
        }
        val result = LinkedHashSet<String>()
        var bytes = 0
        fun add(rule: CosmeticRule) {
            if (
                rule.selector in exceptions ||
                    rule.selector in result ||
                    !rule.domains.matches(host, registeredDomain) ||
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
        hostSuffixes(host) { domain ->
            specific[domain]?.forEach { add(cosmeticRules[it]) }
        }
        entity?.let { value ->
            hostSuffixes(value) { key ->
                entitySpecific[key]?.forEach { add(cosmeticRules[it]) }
            }
        }
        if (hide == 0) {
            generic.forEach { add(cosmeticRules[it]) }
            pageClasses.take(256).forEach { token ->
                classes[token]?.forEach { add(cosmeticRules[it]) }
            }
            pageIds.take(256).forEach { token ->
                ids[token]?.forEach { add(cosmeticRules[it]) }
            }
        }
        return result.toList()
    }

    /**
     * Domain-matched procedural actions, with normal exceptions and hiding switches.
     */
    fun cosmeticProcedures(url: String): List<CosmeticProcedure> {
        if (!hasCosmetics) {
            return emptyList()
        }
        val address = httpAddress(url) ?: return emptyList()
        val host = address.host.removeSuffix(".")
        val domain =
            if (
                entityProcedures.isNotEmpty() ||
                    entityProcedureExceptions.isNotEmpty() ||
                    cosmeticsNeedEntities
            ) {
                address.topPrivateDomain()
            } else {
                null
            }
        val entity = entityName(host, domain)
        val facts =
            RequestFacts(address, address, ResourceTypes.request("document"), "GET")
        var hide = false
        hideExceptions.any(facts) { rule ->
            if (
                rule.hide == 2 &&
                    facts.matches(rule.conditions) &&
                    rule.pattern.matches(address, facts.url, facts.budget)
            ) {
                hide = true
            }
            hide
        }
        if (hide || facts.budget.exhausted) {
            return emptyList()
        }
        val exceptions = HashSet<String>()
        val candidates = LinkedHashSet<CosmeticRule>()
        fun gather(
            source: Map<String, IntArray>,
            key: String,
            visitor: (CosmeticRule) -> Unit,
        ) {
            source[key]?.forEach { visitor(cosmeticRules[it]) }
        }
        hostSuffixes(host) { key ->
            gather(procedureExceptions, key) {
                if (it.domains.matches(host, domain)) {
                    exceptions += it.selector
                }
            }
            gather(procedures, key, candidates::add)
        }
        entity?.let { value ->
            hostSuffixes(value) { key ->
                gather(entityProcedureExceptions, key) {
                    if (it.domains.matches(host, domain)) {
                        exceptions += it.selector
                    }
                }
                gather(entityProcedures, key, candidates::add)
            }
        }
        return candidates
            .asSequence()
            .filter {
                it.selector !in exceptions && it.domains.matches(host, domain)
            }
            .mapNotNull(CosmeticRule::procedure)
            .distinct()
            .take(128)
            .toList()
    }

    companion object {
        val EMPTY = CompiledFilters(emptyList(), emptyList(), 0)
    }
}
