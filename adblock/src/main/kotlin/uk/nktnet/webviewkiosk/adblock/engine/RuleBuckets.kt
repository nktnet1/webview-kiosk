package uk.nktnet.webviewkiosk.adblock.engine

internal typealias BucketVisitor = (table: Int, key: String, rule: Int) -> Unit

/**
 * Compact rule IDs; normalized records own the selectors and scriptlet arguments.
 */
internal class RuleBuckets(
    val ruleCount: Int,
    val tables: List<Map<String, IntArray>>,
) {
    /** Validate membership without allocating replacement hostname/token indexes. */
    fun validFor(
        count: Int,
        tableCount: Int,
        visit: (BucketVisitor) -> Unit,
    ): Boolean {
        if (ruleCount != count || tables.size != tableCount) {
            return false
        }
        var actual = 0L
        for (table in tables) {
            for (ids in table.values) {
                if (ids.isEmpty()) {
                    return false
                }
                var previous = -1
                for (id in ids) {
                    if (id !in 0 until count || id <= previous) {
                        return false
                    }
                    previous = id
                }
                actual += ids.size
            }
        }
        var expected = 0L
        var valid = true
        visit { table, key, id ->
            expected++
            if ((tables[table][key]?.binarySearch(id) ?: -1) < 0) {
                valid = false
            }
        }
        // Ordered IDs prevent duplicates. Exact membership plus equal totals also
        // rejects extra keys, misplaced rules and missing exception buckets.
        return valid && actual == expected
    }

    companion object {
        fun build(
            count: Int,
            tableCount: Int,
            visit: (BucketVisitor) -> Unit,
        ): RuleBuckets {
            val tables = List(tableCount) { linkedMapOf<String, MutableList<Int>>() }
            visit { table, key, id ->
                tables[table].getOrPut(key) { mutableListOf() }.add(id)
            }
            return RuleBuckets(
                count,
                tables.map { table -> table.mapValues { it.value.toIntArray() } },
            )
        }
    }
}

internal data class PageIndexSnapshot(
    val cosmetics: RuleBuckets,
    val scriptlets: RuleBuckets,
)
