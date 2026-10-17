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
    internal const val MAX_RULES = 500_000

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
            total,
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
                scriptlets.any { it.domains.usesEntities }
        ) {
            // Load OkHttp's existing public-suffix data on the compilation worker.
            httpAddress("https://example.com/")!!.topPrivateDomain()
        }
        return CompiledFilters(networks, cosmetics, skippedRules, scriptlets)
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
    private val targetDomain by lazy { target.topPrivateDomain() }
    private val sourceDomain by lazy { source.topPrivateDomain() }
    val sourceEntity: String? by lazy { entityName(sourceHost, sourceDomain) }

    fun matches(conditions: Conditions): Boolean {
        if (conditions.types != 0 && conditions.types and type == 0) {
            return false
        }
        if (conditions.excludedTypes and type != 0) {
            return false
        }
        // Only entity restrictions need the public suffix lookup; ordinary
        // host/domain rules stay on the cheap matching path.
        if (conditions.domains != DomainScope.ANY) {
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
        if (conditions.thirdParty != null) {
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
private class NetworkIndex(rules: Collection<NetworkIdentity>) {
    private val hosts = HashMap<String, MutableList<NetworkIdentity>>()
    private val pages = HashMap<String, MutableList<NetworkIdentity>>()
    private val pageEntities = HashMap<String, MutableList<NetworkIdentity>>()
    private val tokens = HashMap<Int, MutableList<NetworkIdentity>>()
    private val fallback = mutableListOf<NetworkIdentity>()

    init {
        for (rule in rules) {
            val pattern = rule.pattern
            when {
                pattern.hostIndex != null ->
                    hosts.getOrPut(pattern.hostIndex) { mutableListOf() }.add(rule)

                rule.conditions.domains.included.isNotEmpty() ||
                    rule.conditions.domains.includedEntities.isNotEmpty() -> {
                    rule.conditions.domains.included.forEach { domain ->
                        pages.getOrPut(domain) { mutableListOf() }.add(rule)
                    }
                    rule.conditions.domains.includedEntities.forEach { entity ->
                        pageEntities.getOrPut(entity) { mutableListOf() }.add(rule)
                    }
                }

                pattern.indexToken != null || rule.removeParam?.indexToken != null ->
                    tokens
                        .getOrPut(
                            pattern.indexToken
                                ?: requireNotNull(rule.removeParam?.indexToken)
                        ) {
                            mutableListOf()
                        }
                        .add(rule)

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
) {
    private val scriptletIndex = ScriptletIndex(scriptletRules)
    val scriptletInjection: ScriptletInjection? = scriptletIndex.injection()

    fun scriptlets(url: String): List<ScriptletInstruction> =
        scriptletIndex.instructions(url)

    private val blockedHosts = HashSet<String>()
    private val allowedHosts = HashSet<String>()
    private val importantHosts = HashSet<String>()
    private val requests: NetworkIndex
    private val redirectExceptions: NetworkIndex
    private val hideExceptions: NetworkIndex
    private val parameterRules: NetworkIndex
    private val specific = HashMap<String, MutableList<CosmeticRule>>()
    private val generic = mutableListOf<CosmeticRule>()
    private val classes = HashMap<String, MutableList<CosmeticRule>>()
    private val ids = HashMap<String, MutableList<CosmeticRule>>()
    private val specificExceptions = HashMap<String, MutableList<CosmeticRule>>()
    private val entitySpecific = HashMap<String, MutableList<CosmeticRule>>()
    private val entityExceptions = HashMap<String, MutableList<CosmeticRule>>()
    private val procedures = HashMap<String, MutableList<CosmeticRule>>()
    private val entityProcedures = HashMap<String, MutableList<CosmeticRule>>()
    private val procedureExceptions = HashMap<String, MutableList<CosmeticRule>>()
    private val entityProcedureExceptions =
        HashMap<String, MutableList<CosmeticRule>>()
    private val genericExceptions = mutableListOf<CosmeticRule>()
    val hasCosmetics = cosmetics.any { !it.exception }
    private val cosmeticsNeedEntities = cosmetics.any { it.domains.usesEntities }

    init {
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
        requests = NetworkIndex(ordinary)
        hideExceptions = NetworkIndex(hiding)
        redirectExceptions = NetworkIndex(redirecting)
        parameterRules = NetworkIndex(parameters)
        for (rule in cosmetics) {
            if (rule.procedure != null) {
                val domainIndex =
                    if (rule.exception) {
                        procedureExceptions
                    } else {
                        procedures
                    }
                val entityIndex =
                    if (rule.exception) {
                        entityProcedureExceptions
                    } else {
                        entityProcedures
                    }
                rule.domains.included.forEach { domain ->
                    domainIndex.getOrPut(domain) { mutableListOf() }.add(rule)
                }
                rule.domains.includedEntities.forEach { entity ->
                    entityIndex.getOrPut(entity) { mutableListOf() }.add(rule)
                }
                continue
            }
            when {
                rule.exception -> {
                    if (
                        rule.domains.included.isEmpty() &&
                            rule.domains.includedEntities.isEmpty()
                    ) {
                        genericExceptions += rule
                    } else {
                        rule.domains.included.forEach { domain ->
                            specificExceptions
                                .getOrPut(domain) { mutableListOf() }
                                .add(rule)
                        }
                        rule.domains.includedEntities.forEach { entity ->
                            entityExceptions
                                .getOrPut(entity) { mutableListOf() }
                                .add(rule)
                        }
                    }
                }

                rule.domains.included.isNotEmpty() ||
                    rule.domains.includedEntities.isNotEmpty() -> {
                    rule.domains.included.forEach { domain ->
                        specific.getOrPut(domain) { mutableListOf() }.add(rule)
                    }
                    rule.domains.includedEntities.forEach { entity ->
                        entitySpecific.getOrPut(entity) { mutableListOf() }.add(rule)
                    }
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

    fun responseFor(
        url: String,
        sourceUrl: String,
        type: String,
        method: String,
    ): BlockingResponse {
        val target = httpAddress(url) ?: return BlockingResponse.ALLOW
        if (ResourceTypes.request(type) == ResourceTypes.request("document")) {
            return documentResponse(target, method)
        }
        val source = httpAddress(sourceUrl) ?: return BlockingResponse.ALLOW
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
            )
        val redirectExcepted =
            redirectExceptions.any(facts) { rule ->
                facts.matches(rule.conditions) &&
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
        genericExceptions.forEach(::except)
        hostSuffixes(host) { domain ->
            specificExceptions[domain]?.forEach(::except)
        }
        entity?.let { value ->
            hostSuffixes(value) { entityExceptions[it]?.forEach(::except) }
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
        hostSuffixes(host) { domain -> specific[domain]?.forEach(::add) }
        entity?.let { value ->
            hostSuffixes(value) { entitySpecific[it]?.forEach(::add) }
        }
        if (hide == 0) {
            generic.forEach(::add)
            pageClasses.take(256).forEach { token -> classes[token]?.forEach(::add) }
            pageIds.take(256).forEach { token -> ids[token]?.forEach(::add) }
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
            source: Map<String, MutableList<CosmeticRule>>,
            key: String,
            visitor: (CosmeticRule) -> Unit,
        ) {
            source[key]?.forEach(visitor)
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
            return if (tokenEnd in 2..129) {
                selector[0] to selector.substring(1, tokenEnd)
            } else {
                null
            }
        }
    }
}
