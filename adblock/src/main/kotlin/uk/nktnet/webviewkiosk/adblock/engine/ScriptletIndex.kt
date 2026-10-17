package uk.nktnet.webviewkiosk.adblock.engine

import uk.nktnet.webviewkiosk.adblock.ScriptletInjection
import uk.nktnet.webviewkiosk.adblock.ScriptletInstruction

/** Host-indexed routing is mirrored in the synchronous document-start dispatcher. */
internal class ScriptletIndex(private val rules: List<ScriptletRule>) {
    private val hosts = linkedMapOf<String, MutableList<Int>>()
    private val entities = linkedMapOf<String, MutableList<Int>>()
    private val usesEntities = rules.any { it.domains.usesEntities }
    private val genericExceptions = mutableListOf<Int>()

    init {
        rules.forEachIndexed { index, rule ->
            if (
                rule.domains.included.isEmpty() &&
                    rule.domains.includedEntities.isEmpty() &&
                    rule.exception
            ) {
                genericExceptions += index
            }
            rule.domains.includedEntities.forEach { entity ->
                entities.getOrPut(entity) { mutableListOf() }.add(index)
            }
            rule.domains.included.forEach { domain ->
                hosts.getOrPut(domain) { mutableListOf() }.add(index)
            }
        }
    }

    fun instructions(url: String): List<ScriptletInstruction> {
        val address = httpAddress(url) ?: return emptyList()
        val host = address.host.removeSuffix(".")
        if (host in PRIVATE_HOSTS) {
            return emptyList()
        }
        val candidates = sortedSetOf<Int>().apply { addAll(genericExceptions) }
        hostSuffixes(host) { hosts[it]?.let(candidates::addAll) }
        val domain =
            if (usesEntities) {
                address.topPrivateDomain()
            } else {
                null
            }
        entityName(host, domain)?.let { entity ->
            hostSuffixes(entity) { entities[it]?.let(candidates::addAll) }
        }
        val matching =
            candidates.map(rules::get).filter {
                it.domains.matches(host, domain)
            }
        if (matching.any { it.exception && it.instruction == null }) {
            return emptyList()
        }
        val excepted =
            matching.filter { it.exception }.map { it.instruction }.toSet()
        return matching
            .asSequence()
            .filterNot { it.exception }
            .mapNotNull { it.instruction }
            .filterNot { it in excepted }
            .distinct()
            .take(MAX_PER_PAGE)
            .toList()
    }

    fun injection(): ScriptletInjection? {
        if (rules.none { !it.exception }) {
            return null
        }
        val domains = linkedSetOf<String>()
        val entityLabels = linkedSetOf<String>()
        rules
            .filterNot { it.exception }
            .forEach { rule ->
                rule.domains.includedEntities.forEach {
                    entityLabels += it.substringAfterLast('.')
                }
                rule.domains.included
                    .filterNot { it in PRIVATE_HOSTS }
                    .forEach { domain ->
                        domains += domain
                    }
            }
        if (domains.isEmpty() && entityLabels.isEmpty()) {
            return null
        }
        val data = buildString {
            append("{\"rules\":[")
            rules.forEachIndexed { index, rule ->
                if (index != 0) {
                    append(',')
                }
                append('[')
                val instruction = rule.instruction
                append(instruction?.type?.name?.let(::json) ?: "null")
                append(',')
                strings(instruction?.arguments.orEmpty())
                append(',')
                strings(rule.domains.excluded)
                append(',').append(rule.exception).append(',')
                strings(rule.domains.excludedEntities)
                append(']')
            }
            append("],\"hosts\":[")
            hosts.entries.forEachIndexed { index, (host, values) ->
                if (index != 0) {
                    append(',')
                }
                append('[')
                    .append(json(host))
                    .append(',')
                    .append(values.joinToString(",", "[", "]"))
                    .append(']')
            }
            append("],\"entities\":[")
            entities.entries.forEachIndexed { index, (entity, values) ->
                if (index != 0) {
                    append(',')
                }
                append('[')
                    .append(json(entity))
                    .append(',')
                    .append(values.joinToString(",", "[", "]"))
                    .append(']')
            }
            if (usesEntities) {
                append("],\"suffixes\":").append(ScriptletSuffixes.serialized)
            } else {
                append(']')
            }
            append(",\"exceptions\":")
            append(genericExceptions.joinToString(",", "[", "]"))
            append('}')
        }
        require(data.length <= 2 * 1024 * 1024) {
            "Scriptlet routing data exceeds 2 MiB"
        }
        // AndroidX's origin grammar cannot express arbitrary HTTP(S) ports.
        // A cheap synchronous host gate runs before the lazy routing payload.
        // This wildcard exposes no native bridge or privileged callable API.
        val gate =
            "[${json(domains.joinToString("|", "|", "|"))}," +
                "${json(entityLabels.joinToString("|", "|", "|"))}]"
        val additional = rules.any {
            (it.instruction?.type?.ordinal ?: 0) >
                uk.nktnet.webviewkiosk.adblock.ScriptletType.PREVENT_FETCH.ordinal
        }
        val libraries =
            if (additional) {
                val resources =
                    rules
                        .mapNotNull { rule ->
                            rule.instruction
                                ?.takeIf {
                                    it.type ==
                                        uk.nktnet.webviewkiosk.adblock.ScriptletType
                                            .RESOURCE
                                }
                                ?.arguments
                                ?.firstOrNull()
                        }
                        .toSet()
                val network =
                    if (
                        rules.any {
                            it.instruction?.type in ResponseScriptlets.networkTypes
                        }
                    ) {
                        ",$networkRuntime"
                    } else {
                        ""
                    }
                val advanced =
                    if (
                        rules.any {
                            it.instruction?.type in AdvancedScriptlets.aliases.values
                        }
                    ) {
                        if (network.isEmpty()) {
                            ",null,$advancedRuntime"
                        } else {
                            ",$advancedRuntime"
                        }
                    } else {
                        ""
                    }
                ",$extraRuntime,${ScriptletResources.factories(resources)}$network$advanced"
            } else {
                ""
            }
        return ScriptletInjection(
            "$runtime($gate,${json(data)}$libraries);",
            setOf("*"),
        )
    }

    companion object {
        const val MAX_RULES = 8192
        const val MAX_PER_PAGE = 128
        private val PRIVATE_HOSTS =
            setOf(
                "appassets.androidplatform.net",
                "pdf-dummy.webviewkiosk.nktnet.uk",
            )
        private val runtime by lazy {
            ScriptletIndex::class
                .java
                .getResourceAsStream("/adblock/scriptlets.js")!!
                .bufferedReader()
                .use { it.readText() }
        }

        private val extraRuntime by lazy {
            ScriptletIndex::class
                .java
                .getResourceAsStream("/adblock/scriptlets-extra.js")!!
                .bufferedReader()
                .use { it.readText() }
        }

        private val networkRuntime by lazy {
            ScriptletIndex::class
                .java
                .getResourceAsStream("/adblock/scriptlets-network.js")!!
                .bufferedReader()
                .use { it.readText() }
        }

        private val advancedRuntime by lazy {
            ScriptletIndex::class
                .java
                .getResourceAsStream("/adblock/scriptlets-advanced.js")!!
                .bufferedReader()
                .use { it.readText() }
        }

        /** JSON only: filter text can never escape into JavaScript source. */
        internal fun json(value: String): String = buildString {
            append('"')
            value.forEach { char ->
                when (char) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '<',
                    '>',
                    '&',
                    '\u2028',
                    '\u2029' ->
                        append("\\u").append(char.code.toString(16).padStart(4, '0'))
                    else -> {
                        if (char.code < 32) {
                            append("\\u")
                                .append(char.code.toString(16).padStart(4, '0'))
                        } else {
                            append(char)
                        }
                    }
                }
            }
            append('"')
        }

        private fun StringBuilder.strings(values: Collection<String>) {
            append(values.joinToString(",", "[", "]", transform = ::json))
        }
    }
}
