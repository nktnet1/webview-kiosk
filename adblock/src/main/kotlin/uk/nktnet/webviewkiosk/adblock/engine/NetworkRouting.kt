package uk.nktnet.webviewkiosk.adblock.engine

/**
 * Portable bucket/ID snapshot. Rules remain the authoritative, validated records.
 */
internal class NetworkRouting(
    val ruleCount: Int,
    val hosts: Map<String, IntArray>,
    val pages: Map<String, IntArray>,
    val entities: Map<String, IntArray>,
    val tokens: Map<Int, IntArray>,
    val fallback: IntArray,
) {
    /**
     * Check cached membership without allocating and grouping replacement buckets.
     */
    fun validFor(rules: List<NetworkIdentity>): Boolean {
        if (ruleCount != rules.size || fallback.size > 2048) {
            return false
        }
        val references = IntArray(ruleCount)
        fun check(ids: IntArray, accepts: (NetworkIdentity) -> Boolean): Boolean {
            if (ids.isEmpty()) {
                return false
            }
            var previous = -1
            for (id in ids) {
                if (id !in rules.indices || id <= previous || !accepts(rules[id])) {
                    return false
                }
                previous = id
                references[id]++
            }
            return true
        }
        fun scoped(rule: NetworkIdentity): Boolean =
            rule.conditions.domains.included.isNotEmpty() ||
                rule.conditions.domains.includedEntities.isNotEmpty()
        fun token(rule: NetworkIdentity): Int? =
            rule.pattern.indexToken ?: rule.removeParam?.indexToken
        if (
            hosts.any { (key, ids) ->
                !check(ids) { it.pattern.hostIndex == key }
            } ||
                pages.any { (key, ids) ->
                    !check(ids) {
                        it.pattern.hostIndex == null &&
                            key in it.conditions.domains.included
                    }
                } ||
                entities.any { (key, ids) ->
                    !check(ids) {
                        it.pattern.hostIndex == null &&
                            key in it.conditions.domains.includedEntities
                    }
                } ||
                tokens.any { (key, ids) ->
                    !check(ids) {
                        it.pattern.hostIndex == null &&
                            !scoped(it) &&
                            token(it) == key
                    }
                } ||
                fallback.isNotEmpty() &&
                    !check(fallback) {
                        it.pattern.hostIndex == null &&
                            !scoped(it) &&
                            token(it) == null
                    }
        ) {
            return false
        }
        return rules.indices.all { index ->
            val rule = rules[index]
            val expected =
                if (rule.pattern.hostIndex == null && scoped(rule)) {
                    rule.conditions.domains.included.size +
                        rule.conditions.domains.includedEntities.size
                } else {
                    1
                }
            references[index] == expected
        }
    }

    private fun <T> same(
        first: Map<T, IntArray>,
        second: Map<T, IntArray>,
    ): Boolean =
        first.size == second.size &&
            first.all { (key, values) -> values.contentEquals(second[key]) }

    override fun equals(other: Any?): Boolean =
        other is NetworkRouting &&
            ruleCount == other.ruleCount &&
            same(hosts, other.hosts) &&
            same(pages, other.pages) &&
            same(entities, other.entities) &&
            same(tokens, other.tokens) &&
            fallback.contentEquals(other.fallback)

    override fun hashCode(): Int = ruleCount

    companion object {
        fun build(rules: List<NetworkIdentity>): NetworkRouting {
            val hosts = hashMapOf<String, MutableList<Int>>()
            val pages = hashMapOf<String, MutableList<Int>>()
            val entities = hashMapOf<String, MutableList<Int>>()
            val tokens = hashMapOf<Int, MutableList<Int>>()
            val fallback = mutableListOf<Int>()
            for ((index, rule) in rules.withIndex()) {
                val pattern = rule.pattern
                when {
                    pattern.hostIndex != null ->
                        hosts
                            .getOrPut(pattern.hostIndex) { mutableListOf() }
                            .add(index)
                    rule.conditions.domains.included.isNotEmpty() ||
                        rule.conditions.domains.includedEntities.isNotEmpty() -> {
                        rule.conditions.domains.included.forEach {
                            pages.getOrPut(it) { mutableListOf() }.add(index)
                        }
                        rule.conditions.domains.includedEntities.forEach {
                            entities.getOrPut(it) { mutableListOf() }.add(index)
                        }
                    }
                    pattern.indexToken != null ||
                        rule.removeParam?.indexToken != null ->
                        tokens
                            .getOrPut(
                                pattern.indexToken
                                    ?: requireNotNull(rule.removeParam?.indexToken)
                            ) {
                                mutableListOf()
                            }
                            .add(index)
                    else -> fallback += index
                }
            }
            require(fallback.size <= 2048) {
                "Too many URL patterns without a hostname, domain restriction, " +
                    "or four-character literal token (maximum 2048)"
            }
            return NetworkRouting(
                rules.size,
                hosts.mapValues { it.value.toIntArray() },
                pages.mapValues { it.value.toIntArray() },
                entities.mapValues { it.value.toIntArray() },
                tokens.mapValues { it.value.toIntArray() },
                fallback.toIntArray(),
            )
        }
    }
}

internal data class NetworkIndexSnapshot(
    val requests: NetworkRouting,
    val hiding: NetworkRouting,
    val redirects: NetworkRouting,
    val parameters: NetworkRouting,
)
