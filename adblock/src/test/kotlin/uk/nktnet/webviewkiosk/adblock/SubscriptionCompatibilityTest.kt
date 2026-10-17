package uk.nktnet.webviewkiosk.adblock

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import uk.nktnet.webviewkiosk.adblock.engine.FilterParser
import uk.nktnet.webviewkiosk.adblock.engine.ParsedFilter

class SubscriptionCompatibilityTest {
    @Test
    fun validExtendedScopesAreSkippedWithoutRejectingTheRestOfTheSubscription() {
        // Real Quick fixes and Unbreak examples from the reported failure.
        val extended =
            listOf(
                "f51rm.com>>##+js(nowoif, /^ [-\\d]/)",
                "igorslab.de/community/#@#img:remove-attr(/data-.+/)",
                "igorslab.de/community/#@##extras, #ilab-consent-banner",
                "nijie.info/ad_log.php#@#._banner_view",
                "site.example,~sub.site.example/path##.advert",
            )
        extended.forEach { rule ->
            val text = "||ads.example^\nsite.example##.advert\n$rule"
            val counts = inspectFilterList(text)
            assertEquals(rule, 0, counts.invalidRules)
            assertEquals(rule, 1, counts.unsupportedRules)
            val engine = KotlinFilterEngine()
            engine.validate(text)
            engine.replace(listOf(text))
            assertTrue(
                engine.shouldBlock(
                    "https://ads.example/a",
                    "https://site.example",
                    "script",
                    "GET",
                )
            )
            assertTrue(engine.scriptlets("https://f51rm.com").isEmpty())
            assertEquals(
                listOf(".advert"),
                engine.cosmeticSelectors(
                    "https://site.example",
                    listOf("advert"),
                    emptyList(),
                ),
            )
        }
    }

    @Test
    fun genuinelyMalformedDomainsAreStillRejectedAndAllIsNotPretendedToWork() {
        val engine = KotlinFilterEngine()
        listOf(
                "site example##.advert",
                "site example/path##.advert",
                ",site.example##.advert",
            )
            .forEach { rule ->
                assertTrue(inspectFilterList(rule).invalidRules > 0)
                assertThrows(IllegalArgumentException::class.java) {
                    engine.validate(rule)
                }
            }
        assertEquals(
            1,
            inspectFilterList("||malware.example^\$all").unsupportedRules,
        )
    }

    @Test
    fun publishedUrlhausNavigationFormatBlocksOnlyMatchingNavigations() {
        // Maintainer's Vivaldi/Brave variant retains specific malicious paths.
        val text =
            "||malware.example^\$document\n" +
                "||shared.example/uploads/malware.exe^\$document"
        val engine = KotlinFilterEngine()
        engine.validate(text)
        engine.replace(listOf(text))
        assertTrue(
            engine.shouldBlock(
                "https://malware.example/",
                "https://site.example",
                "document",
                "GET",
            )
        )
        assertTrue(
            engine.shouldBlock(
                "https://shared.example/uploads/malware.exe",
                "https://site.example",
                "document",
                "GET",
            )
        )
        assertFalse(
            engine.shouldBlock(
                "https://shared.example/login",
                "https://site.example",
                "document",
                "GET",
            )
        )
        assertFalse(
            engine.shouldBlock(
                "https://malware.example/a",
                "https://site.example",
                "xmlhttprequest",
                "GET",
            )
        )
        assertFalse(
            engine.shouldBlock(
                "https://malware.example.attacker.test/",
                "https://site.example",
                "document",
                "GET",
            )
        )
    }

    @Test
    fun inspectRealDownloadedSubscriptionsWhenAvailable() {
        val directory = System.getenv("DEFAULT_FILTER_TEST_DIR") ?: return
        listOf(
                "ublock-quick-fixes.txt",
                "ublock-unbreak.txt",
                "urlhaus.txt",
                "urlhaus-vivaldi.txt",
            )
            .forEach { name ->
                val file = File(directory, name)
                val text = file.readText()
                val counts = inspectFilterList(text)
                assertEquals(name, 0, counts.invalidRules)
                if (name == "urlhaus.txt") {
                    assertEquals(name, 0, counts.networkRules)
                    assertTrue(name, counts.unsupportedRules > 0)
                    assertThrows(IllegalArgumentException::class.java) {
                        KotlinFilterEngine().validate(text)
                    }
                } else {
                    KotlinFilterEngine().validate(text)
                }
                val hash =
                    MessageDigest.getInstance("SHA-256")
                        .digest(file.readBytes())
                        .joinToString("") { "%02x".format(it) }
                println("DEFAULT_LIST $name sha256=$hash counts=$counts")
            }
    }

    @Test
    fun auditEasylistAndEasyprivacy() {
        val path = System.getenv("EASYLIST_DIR")
        assumeTrue("Set EASYLIST_DIR to inspect subscriptions", path != null)
        for (name in listOf("easylist", "easyprivacy")) {
            val file = File(path!!, "$name.txt")
            val text = file.readText()
            val lines = text.lines()
            val unsupported = mutableListOf<String>()
            var invalid = 0
            FilterParser.visit(text) { number, result ->
                if (result == ParsedFilter.Unsupported) {
                    unsupported += lines[number - 1]
                }
                if (result is ParsedFilter.Invalid) {
                    invalid++
                }
            }
            assertEquals(name, 0, invalid)
            val sha =
                MessageDigest.getInstance("SHA-256")
                    .digest(file.readBytes())
                    .joinToString("") { "%02x".format(it) }
            println("SUBSCRIPTION $name sha256=$sha skipped=${unsupported.size}")
            System.getenv("FILTER_AUDIT_OUTPUT_DIR")?.let { output ->
                val directory = File(output).apply { mkdirs() }
                File(directory, "$name-skipped.txt")
                    .writeText(unsupported.joinToString("\n", postfix = "\n"))
            }
        }
    }
}
