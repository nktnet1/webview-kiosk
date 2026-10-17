package uk.nktnet.webviewkiosk.adblock

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import uk.nktnet.webviewkiosk.adblock.engine.ScriptletIndex
import uk.nktnet.webviewkiosk.adblock.engine.ScriptletSuffixes

class ScriptletEntityTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun entitiesUseRegistrableDomainsIncludingPrivateWildcardAndUnknownSuffixes() {
        val engine = KotlinFilterEngine()
        engine.replace(
            listOf(
                "ticketmaster.*,other.example,~safe.ticketmaster.*" +
                    "##+js(set, entityMarker, false)"
            )
        )
        val cases =
            linkedMapOf(
                "ticketmaster.com" to true,
                "www.ticketmaster.co.uk" to true,
                "ticketmaster.k12.ak.us" to true,
                "ticketmaster.blogspot.com" to true,
                "ticketmaster.github.io" to true,
                "ticketmaster.unlisted-suffix" to true,
                "ticketmaster.a.ck" to true,
                "ticketmaster.www.ck" to false,
                "ticketmaster.compute.amazonaws.com" to false,
                "ticketmaster.foo.compute.amazonaws.com" to true,
                "other.example" to true,
                "safe.ticketmaster.fr" to false,
                "sub.safe.ticketmaster.co.uk" to false,
                "ticketmaster.com.evil.org" to false,
                "evilticketmaster.com" to false,
                "ticketmaster" to false,
                "co.uk" to false,
                "127.0.0.1" to false,
                "appassets.androidplatform.net" to false,
            )
        for ((host, expected) in cases) {
            assertEquals(
                host,
                expected,
                engine.scriptlets("https://$host:8443/a").isNotEmpty(),
            )
        }
        assertTrue(engine.scriptlets("file://ticketmaster.com/a").isEmpty())
        assertEquals(0, engine.unsupportedRuleCount())
        assertNotNull(engine.scriptletInjection())
    }

    @Test
    fun entityAndHostnameExceptionsAreOrderIndependentAndRespectExclusions() {
        val rules =
            listOf(
                "ticketmaster.*,other.example##+js(set, entityMarker, false)",
                "safe.ticketmaster.*#@#+js(set, entityMarker, false)",
                "ticketmaster.co.uk#@#+js()",
                "~ticketmaster.*#@#+js()",
            )
        for (lists in listOf(rules, rules.reversed())) {
            val engine = KotlinFilterEngine()
            engine.replace(lists)
            assertEquals(1, engine.scriptlets("https://ticketmaster.com/").size)
            for (host in
                listOf(
                    "safe.ticketmaster.com",
                    "ticketmaster.co.uk",
                    "other.example",
                )) {
                assertTrue(host, engine.scriptlets("https://$host/").isEmpty())
            }
        }
        val engine = KotlinFilterEngine()
        engine.replace(
            listOf(
                "ticketmaster.*##+js(set, entityMarker, false)\n" +
                    "ticketmaster.*,~safe.ticketmaster.*#@#+js()"
            )
        )
        assertTrue(engine.scriptlets("https://ticketmaster.com/").isEmpty())
        assertEquals(1, engine.scriptlets("https://safe.ticketmaster.fr/").size)
    }

    @Test
    fun entityScopesAndCookieOptionsSurviveAggregateAndPerSourceCaches() {
        val file = File(folder.root, "rules.bin")
        val rules =
            listOf(
                "ticketmaster.*,~safe.ticketmaster.*##+js(set, entityMarker, false)",
                "ticketmaster.co.uk#@#+js()",
                "usaa.com##+js(set-cookie, GPC, 1, , reload, 1)",
            )
        val original = KotlinFilterEngine(file)
        original.replace(rules)
        val restored = KotlinFilterEngine(file)
        val stages = mutableListOf<RuleLoadStage>()
        restored.replace(rules, stages::add)
        assertTrue(RuleLoadStage.RESTORING in stages)
        assertFalse(RuleLoadStage.COMPILING in stages)
        assertEquals(original.scriptletInjection(), restored.scriptletInjection())
        for (host in
            listOf(
                "ticketmaster.com",
                "safe.ticketmaster.fr",
                "ticketmaster.co.uk",
                "usaa.com",
            )) {
            assertEquals(
                original.scriptlets("https://$host/"),
                restored.scriptlets("https://$host/"),
            )
        }
        restored.replace(rules.take(1))
        assertEquals(1, restored.scriptlets("https://ticketmaster.co.uk/").size)
    }

    @Test
    fun exportsDispatcherParityCasesForTheEntireResolvedPublicSuffixList() {
        val engine = KotlinFilterEngine()
        engine.replace(
            listOf(
                "zz-scriptlet-entity.*,bücher.*,~safe.zz-scriptlet-entity.*" +
                    "##+js(set, entityMarker, false)\n" +
                    "skip.zz-scriptlet-entity.*#@#+js()"
            )
        )
        assertEquals(1, engine.scriptlets("https://bücher.de/").size)
        val cases =
            linkedSetOf(
                "xn--bcher-kva.de",
                "sub.xn--bcher-kva.co.uk",
                "other.com",
                "zz-scriptlet-entity.www.ck",
                "zz-scriptlet-entity.compute.amazonaws.com",
                "safe.zz-scriptlet-entity.fr",
                "skip.zz-scriptlet-entity.fr",
                "zz-scriptlet-entity.com.evil.org",
                "127.0.0.1",
                "[::1]",
            )
        val lists = Json.parseToJsonElement(ScriptletSuffixes.serialized).jsonArray
        for (suffix in lists.flatMap { it.jsonPrimitive.content.lines() }) {
            if (suffix.isNotEmpty()) {
                val tail = suffix.replace("*.", "wildcard.")
                cases += "zz-scriptlet-entity.$tail"
                cases += "sub.zz-scriptlet-entity.$tail"
            }
        }
        val rows = cases.map { host ->
            val count = engine.scriptlets("https://$host/").size
            "[${ScriptletIndex.json(host)},$count]"
        }
        assertTrue(rows.size > 10_000)
        System.getenv("SCRIPTLET_OUTPUT_DIR")?.let { output ->
            val directory = File(output).apply { mkdirs() }
            File(directory, "entity-injection.js")
                .writeText(engine.scriptletInjection()!!.source)
            File(directory, "entity-hosts.json")
                .writeText(rows.joinToString(",", "[", "]"))
        }
    }
}
