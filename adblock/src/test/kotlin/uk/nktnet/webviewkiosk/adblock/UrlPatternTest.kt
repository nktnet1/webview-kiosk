package uk.nktnet.webviewkiosk.adblock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import uk.nktnet.webviewkiosk.adblock.engine.UrlPattern

class UrlPatternTest {
    private fun pattern(host: String, text: String = "$host^") =
        UrlPattern(
            text = text,
            domainAnchor = true,
            startAnchor = false,
            endAnchor = false,
            matchCase = false,
            hostIndex = host,
        )

    @Test
    fun plainHostReturnsTheExistingHostForAnExactDomainPattern() {
        for (host in
            listOf("ads.example", "localhost", "192.0.2.1", "2001:db8::1")) {
            assertSame(host, pattern(host).plainHost)
            assertSame(host, pattern(host).copy(matchCase = true).plainHost)
        }
    }

    @Test
    fun plainHostRejectsPathsPrefixesExtraSeparatorsAndDifferentAnchors() {
        val host = "ads.example"
        for (text in
            listOf("", host, "$host/", "$host/path", "$host^^", "bad.example^")) {
            assertNull(text, pattern(host, text).plainHost)
        }
        assertNull(pattern(host).copy(domainAnchor = false).plainHost)
        assertNull(pattern(host).copy(endAnchor = true).plainHost)
        assertNull(pattern(host).copy(hostIndex = null).plainHost)
    }

    @Test
    fun plainHostKeepsTheExactPreviousPredicate() {
        // Include malformed combinations too: this getter must not broaden a
        // cached pattern just because its text contains a hostname prefix.
        val hosts = listOf("a", "ads.example", "2001:db8::1")
        for (host in hosts) {
            val texts = listOf(host, "$host^", "$host^^", "$host/ads", "x$host^")
            for (text in texts) {
                for (domain in listOf(false, true)) {
                    for (end in listOf(false, true)) {
                        val rule =
                            pattern(host, text)
                                .copy(
                                    domainAnchor = domain,
                                    endAnchor = end,
                                )
                        val expected = host.takeIf {
                            domain && text == "$it^" && !end
                        }
                        assertEquals(expected, rule.plainHost)
                    }
                }
            }
        }
    }
}
