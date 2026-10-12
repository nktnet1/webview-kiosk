package uk.nktnet.webviewkiosk.utils.webview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationUrlTest {
    @Test
    fun anOptionalPathSlashIsEquivalent() {
        assertTrue(sameNavigationUrl("https://example.com/path/", "https://example.com/path"))
        assertTrue(
            sameNavigationUrl("https://example.com/path/?q=ok", "https://example.com/path?q=ok")
        )
        assertEquals(
            "https://example.com/path?q=ok",
            normalizedNavigationUrl("https://example.com/path/?q=ok")
        )
    }

    @Test
    fun aSlashInQueryDataIsSignificant() {
        val withSlash = "https://example.com/path?return=/"
        val withoutSlash = "https://example.com/path?return="
        assertFalse(sameNavigationUrl(withSlash, withoutSlash))
        assertEquals(withSlash, normalizedNavigationUrl(withSlash))
    }

    @Test
    fun aSlashInSpaFragmentDataIsSignificant() {
        val withSlash = "https://example.com/path#/route/"
        val withoutSlash = "https://example.com/path#/route"
        assertFalse(sameNavigationUrl(withSlash, withoutSlash))
        assertEquals(withSlash, normalizedNavigationUrl(withSlash))
    }

    @Test
    fun queryAndFragmentOrderingRemainsUnchanged() {
        val url = "https://example.com/app/?q=one/#/two/"
        assertEquals("https://example.com/app?q=one/#/two/", normalizedNavigationUrl(url))
        assertFalse(sameNavigationUrl(url, "https://example.com/app?q=one/#/two"))
    }

    @Test
    fun missingUrlsNeverCompareEqual() {
        assertFalse(sameNavigationUrl(null, null))
        assertFalse(sameNavigationUrl(null, "https://example.com"))
        assertFalse(sameNavigationUrl("https://example.com", null))
    }
}
