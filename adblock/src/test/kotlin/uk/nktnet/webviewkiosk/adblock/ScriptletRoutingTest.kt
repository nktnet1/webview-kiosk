package uk.nktnet.webviewkiosk.adblock

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.nktnet.webviewkiosk.adblock.engine.DomainScope
import uk.nktnet.webviewkiosk.adblock.engine.RuleBuckets
import uk.nktnet.webviewkiosk.adblock.engine.ScriptletBucket
import uk.nktnet.webviewkiosk.adblock.engine.ScriptletRouting
import uk.nktnet.webviewkiosk.adblock.engine.ScriptletRule
import uk.nktnet.webviewkiosk.adblock.engine.ScriptletTarget

class ScriptletRoutingTest {
    private val instruction =
        ScriptletInstruction(ScriptletType.SET, listOf("marker", "false"))

    private fun RuleBuckets.table(bucket: ScriptletBucket) = tables[bucket.ordinal]

    @Test
    fun domainOnlyRulesKeepSeparateFamiliesAndOrderedRuleIds() {
        val rules =
            listOf(
                ScriptletRule(
                    instruction,
                    DomainScope(
                        included = linkedSetOf("site.example", "publisher"),
                        includedEntities = linkedSetOf("publisher", "other"),
                    ),
                    false,
                ),
                ScriptletRule(
                    instruction,
                    DomainScope(included = setOf("site.example")),
                    false,
                ),
                ScriptletRule(
                    null,
                    DomainScope(excluded = setOf("safe.example")),
                    true,
                ),
                ScriptletRule(
                    instruction,
                    DomainScope(included = setOf("site.example")),
                    true,
                ),
            )
        val routing = ScriptletRouting.build(rules)
        assertEquals(
            listOf("site.example", "publisher"),
            routing.table(ScriptletBucket.HOST).keys.toList(),
        )
        assertEquals(
            listOf("publisher", "other"),
            routing.table(ScriptletBucket.ENTITY).keys.toList(),
        )
        assertArrayEquals(
            intArrayOf(0, 1, 3),
            routing.table(ScriptletBucket.HOST).getValue("site.example"),
        )
        assertArrayEquals(
            intArrayOf(0),
            routing.table(ScriptletBucket.HOST).getValue("publisher"),
        )
        assertArrayEquals(
            intArrayOf(0),
            routing.table(ScriptletBucket.ENTITY).getValue("publisher"),
        )
        assertArrayEquals(
            intArrayOf(2),
            routing.table(ScriptletBucket.GENERIC_EXCEPTION).getValue(""),
        )
        assertTrue(ScriptletRouting.validFor(routing, rules))
    }

    @Test
    fun extendedScopesStillDeduplicateOverlappingRoutes() {
        val scopes =
            DomainScope(
                included = setOf("site.example"),
                includedEntities = setOf("publisher"),
            )
        val rule =
            ScriptletRule(
                instruction,
                scopes,
                false,
                listOf(
                    ScriptletTarget("site.example", "/one"),
                    ScriptletTarget("site.example", "/two"),
                    ScriptletTarget("publisher", "/one", entity = true),
                    ScriptletTarget("publisher", "/two", entity = true),
                    ScriptletTarget("site.example", ancestor = true),
                    ScriptletTarget("site.example", ancestor = true),
                    ScriptletTarget("publisher", entity = true, ancestor = true),
                    ScriptletTarget("publisher", entity = true, ancestor = true),
                    ScriptletTarget("^first", regex = true),
                    ScriptletTarget("^second", regex = true),
                    ScriptletTarget("excluded.example", "/path", excluded = true),
                ),
            )
        val exception =
            ScriptletRule(
                null,
                DomainScope.ANY,
                true,
                listOf(
                    ScriptletTarget("excluded.example", "/path", excluded = true)
                ),
            )
        val rules = listOf(rule, exception)
        val routing = ScriptletRouting.build(rules)
        for (bucket in ScriptletBucket.entries) {
            val table = routing.table(bucket)
            assertEquals(1, table.size)
            assertArrayEquals(
                intArrayOf(
                    if (bucket == ScriptletBucket.GENERIC_EXCEPTION) {
                        1
                    } else {
                        0
                    }
                ),
                table.values.single(),
            )
        }
        assertEquals(setOf("site.example"), routing.table(ScriptletBucket.HOST).keys)
        assertEquals(setOf("publisher"), routing.table(ScriptletBucket.ENTITY).keys)
        assertTrue(ScriptletRouting.validFor(routing, rules))
    }

    @Test
    fun cachedDomainOnlyRoutesStillRequireExactMembership() {
        val rules =
            listOf(
                ScriptletRule(
                    instruction,
                    DomainScope(included = setOf("site.example")),
                    false,
                )
            )
        val routing = ScriptletRouting.build(rules)
        val host = ScriptletBucket.HOST.ordinal
        val malformed =
            listOf(
                emptyMap(),
                mapOf("wrong.example" to intArrayOf(0)),
                mapOf("site.example" to intArrayOf(0, 0)),
                mapOf(
                    "site.example" to intArrayOf(0),
                    "extra.example" to intArrayOf(0),
                ),
            )
        for (table in malformed) {
            val tables = routing.tables.toMutableList().apply { this[host] = table }
            assertFalse(
                ScriptletRouting.validFor(RuleBuckets(rules.size, tables), rules)
            )
        }
    }
}
