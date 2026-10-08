package uk.nktnet.webviewkiosk.utils.webview

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ResolveUrlOrSearchTest {
    private val searchProvider = "https://search.example/?q="

    @Test
    fun preservesExplicitUrlsAfterTrimming() {
        val urls = listOf(
            "https://example.com/path?q=1", "http://localhost:8080",
            "file:///sdcard/page.html", "data:text/html,<h1>Hello</h1>",
        )
        for (url in urls) {
            assertEquals(url, resolveUrlOrSearch(searchProvider, "  $url  "))
        }
    }

    @Test
    fun addsHttpsForBareDomainNamesAndPaths() {
        assertEquals("https://example.com", resolveUrlOrSearch(searchProvider, "example.com"))
        assertEquals("https://sub.example.com/path?q=1", resolveUrlOrSearch(searchProvider, "sub.example.com/path?q=1"))
    }

    @Test
    fun addsHttpForIpAddressesAndLocalhostWithPorts() {
        assertEquals("http://192.168.1.2", resolveUrlOrSearch(searchProvider, "192.168.1.2"))
        assertEquals("http://192.168.1.2:8080/path", resolveUrlOrSearch(searchProvider, "192.168.1.2:8080/path"))
        assertEquals("http://localhost:3000/path", resolveUrlOrSearch(searchProvider, "localhost:3000/path"))
    }

    @Test
    fun encodesSearchTextAsASingleQueryValue() {
        assertEquals(
            "${searchProvider}coffee%20%26%20tea%2Fmilk%3F",
            resolveUrlOrSearch(searchProvider, " coffee & tea/milk? "),
        )
        assertEquals("${searchProvider}%E4%BD%A0%E5%A5%BD", resolveUrlOrSearch(searchProvider, "你好"))
        assertEquals(searchProvider, resolveUrlOrSearch(searchProvider, "   "))
    }
}
