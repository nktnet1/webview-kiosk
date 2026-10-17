package uk.nktnet.webviewkiosk.adblock.engine

import uk.nktnet.webviewkiosk.adblock.ScriptletInjection
import uk.nktnet.webviewkiosk.adblock.ScriptletInstruction
import uk.nktnet.webviewkiosk.adblock.ScriptletType

/** Host-indexed routing is mirrored in the synchronous document-start dispatcher. */
internal class ScriptletIndex(private val rules: List<ScriptletRule>) {
    private val hosts = linkedMapOf<String, MutableList<Int>>()
    private val entities = linkedMapOf<String, MutableList<Int>>()
    private val ancestorHosts = linkedMapOf<String, MutableList<Int>>()
    private val ancestorEntities = linkedMapOf<String, MutableList<Int>>()
    private val regexRules = mutableListOf<Int>()
    private val genericExceptions = mutableListOf<Int>()
    private val usesEntities = rules.any {
        it.domains.usesEntities || it.targets.any { target -> target.entity }
    }

    init {
        require(rules.size <= MAX_RULES) { "Too many scriptlet rules" }
        require(rules.sumOf { rule -> rule.targets.count { it.regex } } <= 4096) {
            "Too many regex scriptlet scopes"
        }
        rules.forEachIndexed { index, rule ->
            if (!ScriptletScopes.hasPositive(rule) && rule.exception) {
                genericExceptions += index
            }
            rule.domains.included.forEach {
                hosts.getOrPut(it) { mutableListOf() }.add(index)
            }
            rule.domains.includedEntities.forEach {
                entities.getOrPut(it) { mutableListOf() }.add(index)
            }
            for (target in rule.targets.filterNot { it.excluded }) {
                if (target.regex) {
                    regexRules += index
                    continue
                }
                val table =
                    when {
                        target.ancestor && target.entity -> ancestorEntities
                        target.ancestor -> ancestorHosts
                        target.entity -> entities
                        else -> hosts
                    }
                table.getOrPut(target.host) { mutableListOf() }.add(index)
            }
        }
    }

    fun instructions(
        url: String,
        ancestors: List<String> = emptyList(),
    ): List<ScriptletInstruction> {
        val address = httpAddress(url) ?: return emptyList()
        val host = address.host.removeSuffix(".")
        if (host in PRIVATE_HOSTS) {
            return emptyList()
        }
        val candidates =
            sortedSetOf<Int>().apply {
                addAll(genericExceptions)
                addAll(regexRules)
            }
        fun collect(
            url: String,
            hostTable: Map<String, List<Int>>,
            entityTable: Map<String, List<Int>>,
        ) {
            val frame = httpAddress(url) ?: return
            hostSuffixes(frame.host.removeSuffix(".")) {
                hostTable[it]?.let(candidates::addAll)
            }
            if (usesEntities) {
                entityName(frame.host.removeSuffix("."), frame.topPrivateDomain())
                    ?.let { entity ->
                        hostSuffixes(entity) {
                            entityTable[it]?.let(candidates::addAll)
                        }
                    }
            }
        }
        collect(url, hosts, entities)
        ancestors.take(64).forEach { collect(it, ancestorHosts, ancestorEntities) }
        val matching =
            candidates.map(rules::get).filter {
                ScriptletScopes.matches(it, url, ancestors)
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
        // Domain rules often share an instruction. Serialize arguments once, then
        // decode only selected instruction IDs in the document-start dispatcher.
        val instructionOffsets = linkedMapOf<ScriptletInstruction, Int>()
        val instructions = buildString {
            for (rule in rules) {
                val instruction = rule.instruction ?: continue
                if (instruction in instructionOffsets) {
                    continue
                }
                instructionOffsets[instruction] = length
                append('[').append(json(instruction.type.name)).append(',')
                strings(instruction.arguments)
                append(',').append(module(instruction.type)).append("]\n")
                require(length <= MAX_ROUTING_BYTES) {
                    "Scriptlet instruction data exceeds 16 MiB"
                }
            }
        }
        val offsets = mutableListOf<Int>()
        val data = buildString {
            for (rule in rules) {
                offsets += length
                append('[')
                val instruction = rule.instruction
                append(instruction?.let(instructionOffsets::get) ?: -1)
                append(",null")
                append(',')
                strings(rule.domains.excluded)
                append(',').append(rule.exception).append(',')
                strings(rule.domains.excludedEntities)
                append(",[")
                rule.targets.forEachIndexed { index, target ->
                    if (index != 0) {
                        append(',')
                    }
                    append('[').append(json(target.host)).append(',')
                    append(target.path?.let(::json) ?: "null").append(',')
                    append(target.regex)
                        .append(',')
                        .append(target.entity)
                        .append(',')
                    append(target.ancestor)
                        .append(',')
                        .append(target.excluded)
                        .append(']')
                }
                append("],")
                strings(rule.domains.included)
                append(',')
                strings(rule.domains.includedEntities)
                append("]\n")
                require(length <= MAX_ROUTING_BYTES) {
                    "Scriptlet routing data exceeds 16 MiB"
                }
            }
        }
        fun table(values: Map<String, List<Int>>): String = buildString {
            for ((name, indexes) in values.toSortedMap()) {
                append(name).append('\t')
                append(
                    indexes.distinct().joinToString(",", "[", "]") {
                        offsets[it].toString()
                    }
                )
                append('\n')
                require(length <= MAX_ROUTING_BYTES) {
                    "Scriptlet routing data exceeds 16 MiB"
                }
            }
        }
        val regex =
            regexRules
                .distinct()
                .flatMap { index ->
                    rules[index]
                        .targets
                        .filter { it.regex && !it.excluded }
                        .map { target ->
                            val path = target.path?.let(::json) ?: "null"
                            "[${json(target.host)},$path,${offsets[index]}]"
                        }
                }
                .joinToString(",", "[", "]")
        val entityLabels =
            (entities.keys +
                    ancestorEntities.keys +
                    rules.flatMap { it.domains.excludedEntities } +
                    rules.flatMap {
                        it.targets
                            .filter { target -> target.entity }
                            .map { target -> target.host }
                    })
                .map { it.substringAfterLast('.') }
                .distinct()
                .sorted()
                .joinToString("\n", postfix = "\n")
        val exceptionData =
            genericExceptions.joinToString(",", "[", "]") {
                offsets[it].toString()
            }
        val suffixData =
            if (usesEntities) {
                json(ScriptletSuffixes.serialized)
            } else {
                "null"
            }
        val routes =
            "{" +
                "\"hosts\":${json(table(hosts))}," +
                "\"entities\":${json(table(entities))}," +
                "\"ancestors\":${json(table(ancestorHosts))}," +
                "\"ancestorEntities\":${json(table(ancestorEntities))}," +
                "\"regex\":${json(regex)}," +
                "\"entityLabels\":${json(entityLabels)}," +
                "\"usesAncestors\":${rules.any { it.targets.any { target -> target.ancestor } }}," +
                "\"exceptions\":$exceptionData," +
                "\"suffixes\":$suffixData," +
                "\"limit\":$MAX_PER_PAGE}"
        require(
            data.toByteArray().size.toLong() +
                routes.toByteArray().size +
                instructions.toByteArray().size <= MAX_ROUTING_BYTES
        ) {
            "Scriptlet routing data exceeds 16 MiB"
        }
        // Only matching buckets and records are parsed in each frame. The wildcard
        // origin also covers arbitrary ports and cross-origin descendant scopes.
        // No bridge or privileged page-callable API is exposed.
        val modules = instructionOffsets.keys.map { module(it.type) }.toSet()
        val resources =
            instructionOffsets.keys
                .filter {
                    it.type in
                        setOf(ScriptletType.RESOURCE, ScriptletType.TRUSTED_RESOURCE)
                }
                .map { it.arguments.first() }
                .toSet()
        val libraries =
            listOf(
                    if (1 in modules) {
                        extraRuntime
                    } else {
                        "null"
                    },
                    ScriptletResources.factories(resources),
                    if (2 in modules) {
                        networkRuntime
                    } else {
                        "null"
                    },
                    if (3 in modules) {
                        advancedRuntime
                    } else {
                        "null"
                    },
                )
                .joinToString(",", prefix = ",")
        return ScriptletInjection(
            "$runtime($routes,${json(data)},${json(instructions)}$libraries);",
            setOf("*"),
        )
    }

    companion object {
        const val MAX_RULES = 65_536
        const val MAX_PER_PAGE = 1024
        const val MAX_ROUTING_BYTES = 16 * 1024 * 1024

        private fun module(type: ScriptletType): Int =
            when (type) {
                in ResponseScriptlets.networkTypes -> 2
                in ObjectEditing.elementTypes -> 1
                in ObjectEditing.types -> 0
                in ObjectPruning.types -> 0
                in AdvancedScriptlets.aliases.values -> 3
                ScriptletType.SET,
                ScriptletType.ABORT_READ,
                ScriptletType.ABORT_WRITE,
                ScriptletType.PREVENT_WINDOW_OPEN,
                ScriptletType.PREVENT_ADD_EVENT_LISTENER,
                ScriptletType.ABORT_CURRENT_SCRIPT,
                ScriptletType.JSON_PRUNE,
                ScriptletType.JSON_EDIT,
                ScriptletType.TRUSTED_JSON_EDIT,
                ScriptletType.PREVENT_FETCH,
                ScriptletType.TRUSTED_PREVENT_FETCH,
                ScriptletType.TRUSTED_SET -> 0
                else -> 1
            }

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
