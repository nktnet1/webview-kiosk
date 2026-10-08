package uk.nktnet.webviewkiosk.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.TemporaryFolder
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
class InputValidationTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun acceptsSupportedDataUrls() {
        val urls = listOf(
            "data:,hello", "data:text/plain,hello", "data:;base64,SGVsbG8=",
            "data:application/pdf;base64,JVBERg==",
        )
        for (url in urls) {
            assertTrue(url, isDataSchemeUrl(url))
            assertTrue(url, validateUrl(url))
        }
    }

    @Test
    fun rejectsMalformedDataUrls() {
        for (url in listOf("data:", "data:text/plain", "data:base64,abc", "not-data:,hello")) {
            assertFalse(url, isDataSchemeUrl(url))
            assertFalse(url, validateUrl(url))
        }
    }

    @Test
    fun acceptsEmptyAndSupportedNetworkUrlsIncludingLocalHosts() {
        val urls = listOf(
            "", " https://example.com/path?q=1 ", "http://localhost:8080",
            "https://LOCALHOST:8443",
        )
        for (url in urls) {
            assertTrue(url, validateUrl(url))
        }
    }

    @Test
    @Config(sdk = [28, 29])
    fun acceptsIpv6UrlsWithAndWithoutAPort() {
        val urls = listOf(
            "https://[2001:db8::1]/",
            "https://[2001:db8::1]:8443/path?q=1#section",
            "https://[::1]/",
            "https://[::ffff:192.0.2.1]/",
        )
        for (url in urls) {
            assertTrue(url, validateUrl(url))
        }
    }

    @Test
    @Config(sdk = [28, 29])
    fun rejectsMalformedIpv6Urls() {
        val urls = listOf(
            "https://[2001:db8:::1]/",
            "https://[::gg]/",
            "https://[2001:db8::1/",
            "https://[1234]/",
            "https://[::1]:invalid/",
            "https://[::1]/bad path",
        )
        for (url in urls) {
            assertFalse(url, validateUrl(url))
        }
    }

    @Test
    fun rejectsUnsupportedSchemesAndNonUrlText() {
        val urls = listOf(
            " ", "example.com", "search terms", "javascript:alert(1)",
            "content://provider/file", "ftp://example.com", "https://bad host/",
        )
        for (url in urls) {
            assertFalse(url, validateUrl(url))
        }
    }

    @Test
    fun acceptsOnlyExistingFilePaths() {
        val existing = temporaryFolder.newFile("page.html")
        val missing = File(temporaryFolder.root, "missing.html")

        assertTrue(validateUrl(existing.toURI().toString()))
        assertFalse(validateUrl(missing.toURI().toString()))
    }

    @Test
    fun validatesEveryNonBlankRegexLine() {
        assertTrue(validateMultilineRegex("\n ^https://example\\.com/ \n\t\n.*blocked.*\n"))
        assertFalse(validateMultilineRegex(".*\n[\n.*"))
        assertFalse(validateMultilineRegex("(unclosed"))
    }
}
