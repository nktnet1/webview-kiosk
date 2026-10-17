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
