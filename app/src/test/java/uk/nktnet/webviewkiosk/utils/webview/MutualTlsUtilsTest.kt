package uk.nktnet.webviewkiosk.utils.webview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class MutualTlsUtilsTest {
    @Test
    fun normalizesHostNamesPortsAndIpv6Addresses() {
        assertEquals("example.com:443", mutualTlsSiteKey(" EXAMPLE.COM. ", -1))
        assertEquals("example.com:8443", mutualTlsSiteKey("example.com", 8443))
        assertEquals("[2001:db8::1]:443", mutualTlsSiteKey("[2001:DB8::1]", -1))
        assertEquals("[2001:db8::1]:8443", mutualTlsSiteKey("2001:DB8::1", 8443))
    }

    @Test
    fun normalizationDoesNotDependOnTheDeviceLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals("internal.example:443", mutualTlsSiteKey("INTERNAL.EXAMPLE", -1))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun parsesExactSitesAndOptionalAliasesInOrder() {
        val rules = requireNotNull(parseMutualTlsRules(
            "\n Example.com = work=key \nlocalhost:8443\n[2001:db8::1]:65535 = \n",
        ))
        assertEquals(listOf("example.com:443", "localhost:8443", "[2001:db8::1]:65535"), rules.map { it.siteKey })
        assertEquals(listOf("work=key", null, null), rules.map { it.alias })
    }

    @Test
    fun acceptsEmptyRulesAndPortBoundaries() {
        assertEquals(emptyList<MutualTlsRule>(), parseMutualTlsRules(" \n\t"))
        assertTrue(validateMutualTls("example.com:1\nexample.com:65535"))
    }

    @Test
    fun rejectsAmbiguousOrInvalidSites() {
        val invalid = listOf(
            "https://example.com", "user@example.com", "example.com/", "example.com/path",
            "example.com?query=value", "example.com#fragment", "*.example.com", "example.com:0",
            "example.com:65536", "example.com:not-a-port", "=alias", "[2001:db8::1",
        )
        for (value in invalid) {
            assertNull(value, parseMutualTlsRules(value))
            assertFalse(value, validateMutualTls(value))
        }
    }

    @Test
    fun rejectsDuplicateSitesAfterNormalizationEvenWithDifferentAliases() {
        for (value in listOf(
            "Example.com\nexample.com:443=alias",
            "example.com.\nEXAMPLE.COM=other",
            "[2001:DB8::1]\n[2001:db8::1]:443=alias",
        )) {
            assertNull(value, parseMutualTlsRules(value))
        }
    }
}
