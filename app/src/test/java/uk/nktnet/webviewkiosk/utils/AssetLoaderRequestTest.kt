package uk.nktnet.webviewkiosk.utils

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceResponse
import androidx.webkit.WebViewAssetLoader
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.Constants

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AssetLoaderRequestTest {
    @Test
    fun rejectsSchemeLessOpaqueAndNonWebUrisBeforeCallingAssetLoaders() {
        val loader = loader("appassets.androidplatform.net", "/local/", "local")
        val invalidUrls =
            listOf(
                "/local/test.txt",
                "//appassets.androidplatform.net/local/test.txt",
                "https:opaque",
                "https:///local/test.txt",
                "file:///local/test.txt",
                "data:text/plain,hello",
            )

        invalidUrls.forEach { url ->
            assertNull(
                url,
                interceptAssetRequest(Uri.parse(url), loader, loader),
            )
        }
    }

    @Test
    fun servesValidHttpAndHttpsAssetsFromTheirRespectiveLoaders() {
        val local = loader("appassets.androidplatform.net", "/local/", "local")
        val pdf = loader("pdf.example", "/pdfjs_local/", "pdf")

        assertEquals(
            "local",
            interceptAssetRequest(
                    Uri.parse(
                        "https://appassets.androidplatform.net/local/test.txt"
                    ),
                    local,
                    pdf,
                )
                ?.data
                ?.bufferedReader()
                ?.use { it.readText() },
        )
        assertEquals(
            "pdf",
            interceptAssetRequest(
                    Uri.parse("https://pdf.example/pdfjs_local/test.txt"),
                    local,
                    pdf,
                )
                ?.data
                ?.bufferedReader()
                ?.use { it.readText() },
        )
        assertEquals(
            "local",
            interceptAssetRequest(
                    Uri.parse("http://appassets.androidplatform.net/local/test.txt"),
                    local,
                    pdf,
                )
                ?.data
                ?.bufferedReader()
                ?.use { it.readText() },
        )
        assertNull(
            interceptAssetRequest(
                Uri.parse("https://unrelated.example/local/test.txt"),
                local,
                pdf,
            )
        )
    }

    @Test
    fun servesAUniqueFilenameAliasAndTheOriginalUuidLink() {
        val context = RuntimeEnvironment.getApplication()
        val file =
            File(getWebContentFilesDir(context), "test-id|short-name-test.txt")
        file.writeText("local asset")
        try {
            val loader = localFilesLoader(context)
            val alias =
                "https://appassets.androidplatform.net/web-content-files/short-name-test.txt"
            assertEquals(
                file.name,
                Uri.parse(file.getLocalFileLink()).lastPathSegment,
            )
            for (url in listOf(file.getLocalFileLink(), alias)) {
                assertEquals(
                    "local asset",
                    interceptAssetRequest(Uri.parse(url), loader, null, context)
                        ?.data
                        ?.bufferedReader()
                        ?.use { it.readText() },
                )
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun ambiguousFilenameAliasesServeAnHtmlChooserWithCanonicalUuidLinks() {
        val context = RuntimeEnvironment.getApplication()
        val dir = getWebContentFilesDir(context)
        val first = File(dir, "first-id|ambiguous-name-test.txt")
        val second = File(dir, "second-id|ambiguous-name-test.txt")
        first.writeText("content of first file")
        second.writeText("content of second file")
        try {
            val loader = localFilesLoader(context)
            val alias =
                Uri.parse(
                    "https://appassets.androidplatform.net/web-content-files/ambiguous-name-test.txt"
                )
            val response = interceptAssetRequest(alias, loader, null, context)
            assertEquals("text/html", response?.mimeType)
            assertEquals("UTF-8", response?.encoding)
            assertEquals("no-store", response?.responseHeaders?.get("Cache-Control"))
            val html = response?.data?.bufferedReader()?.use { it.readText() }
            assertNotNull(html)
            assertTrue(html!!.contains("<ul>"))
            assertTrue(html.contains("<dt>UUID</dt>"))
            assertTrue(html.contains("<dt>Size</dt>"))
            assertTrue(html.contains("<dt>Time</dt>"))
            assertTrue(html.contains("grid-template-columns: 3.5rem minmax(0, 1fr)"))
            assertTrue(html.contains("dd a { overflow-wrap: anywhere; }"))
            assertTrue(html.contains("dd:nth-of-type(2) { white-space: nowrap; }"))
            assertFalse(html.contains("<table>"))
            assertFalse(html.contains("<h2>Choose a local file</h2>"))
            assertFalse(html.contains("background:"))
            assertFalse(html.contains("<script"))
            for (file in listOf(first, second)) {
                assertTrue(html.contains("href=\"${file.getLocalFileLink()}\""))
                assertTrue(html.contains(">${file.getUUID()}</a>"))
            }
            assertFalse(html.contains("content of first file"))
            assertFalse(html.contains("content of second file"))

            for ((file, content) in
                listOf(
                    first to "content of first file",
                    second to "content of second file",
                )) {
                assertEquals(
                    content,
                    interceptAssetRequest(
                            Uri.parse(file.getLocalFileLink()),
                            loader,
                            null,
                            context,
                        )
                        ?.data
                        ?.bufferedReader()
                        ?.use { it.readText() },
                )
            }

            second.delete()
            assertEquals(
                "content of first file",
                interceptAssetRequest(alias, loader, null, context)
                    ?.data
                    ?.bufferedReader()
                    ?.use { it.readText() },
            )
        } finally {
            first.delete()
            second.delete()
        }
    }

    @Test
    fun chooserIncludesAppNameSizeAndLastModifiedTimestampForEachFile() {
        val context = RuntimeEnvironment.getApplication()
        val dir = getWebContentFilesDir(context)
        val first = File(dir, "first-id|metadata-chooser-test.txt")
        val second = File(dir, "second-id|metadata-chooser-test.txt")
        first.writeBytes(ByteArray(123))
        second.writeBytes(ByteArray(2048))
        try {
            assertTrue(first.setLastModified(1_700_000_000_000L))
            assertTrue(second.setLastModified(1_750_000_000_000L))
            val alias =
                Uri.parse(
                    "https://appassets.androidplatform.net/web-content-files/metadata-chooser-test.txt"
                )
            val response =
                interceptAssetRequest(
                    alias,
                    localFilesLoader(context),
                    null,
                    context,
                )
            assertEquals("text/html", response?.mimeType)
            val html = response?.data?.bufferedReader()?.use { it.readText() }
            assertNotNull(html)
            val appName = context.getString(R.string.app_name)
            assertTrue(html!!.contains("<title>$appName - Matching files</title>"))
            assertTrue(html.contains("<h1>$appName</h1>"))

            val dateFormat =
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            for (file in listOf(first, second)) {
                val expected =
                    """
                    <li>
                      <dl>
                        <dt>UUID</dt><dd><a href="${file.getLocalFileLink()}">${file.getUUID()}</a></dd>
                        <dt>Size</dt><dd>${humanReadableSize(context, file.length())}</dd>
                        <dt>Time</dt><dd>${dateFormat.format(Date(file.lastModified()))}</dd>
                      </dl>
                    </li>
                    """
                        .trimIndent()
                assertTrue(
                    "Missing metadata for ${file.name}",
                    html.contains(expected),
                )
            }
        } finally {
            first.delete()
            second.delete()
        }
    }

    @Test
    fun pdfFilenameAliasesWithDuplicatesAlsoGetAnHtmlChooser() {
        val context = RuntimeEnvironment.getApplication()
        val dir = getWebContentFilesDir(context)
        val first = File(dir, "first-id|ambiguous-pdf-test.pdf")
        val second = File(dir, "second-id|ambiguous-pdf-test.pdf")
        first.writeText("first pdf")
        second.writeText("second pdf")
        try {
            val response =
                interceptAssetRequest(
                    Uri.parse(
                        "https://appassets.androidplatform.net/web-content-files/ambiguous-pdf-test.pdf"
                    ),
                    localFilesLoader(context),
                    null,
                    context,
                )
            assertEquals("text/html", response?.mimeType)
            val html = response?.data?.bufferedReader()?.use { it.readText() }
            assertNotNull(html)
            assertTrue(html!!.contains(first.getLocalFileLink()))
            assertTrue(html.contains(second.getLocalFileLink()))
        } finally {
            first.delete()
            second.delete()
        }
    }

    @Test
    fun chooserEscapesUntrustedFilenameAndUuid() {
        val context = RuntimeEnvironment.getApplication()
        val name = "chooser-<svg onload=alert(1)>&\".txt"
        val dir = getWebContentFilesDir(context)
        val first = File(dir, "normal-id|$name")
        val second = File(dir, "<img src=x onerror=alert(2)>|$name")
        first.writeText("first")
        second.writeText("second")
        try {
            val alias =
                Uri.Builder()
                    .scheme("https")
                    .authority(WebViewAssetLoader.DEFAULT_DOMAIN)
                    .appendPath(Constants.WEB_CONTENT_FILES_DIR)
                    .appendPath(name)
                    .build()
            val response =
                interceptAssetRequest(
                    alias,
                    localFilesLoader(context),
                    null,
                    context,
                )
            assertEquals("text/html", response?.mimeType)
            val html = response?.data?.bufferedReader()?.use { it.readText() }
            assertNotNull(html)
            assertTrue(html!!.contains("&lt;svg"))
            assertTrue(html.contains("&lt;img"))
            assertFalse(html.contains("<svg"))
            assertFalse(html.contains("<img"))
            assertTrue(html.contains("href=\"${second.getLocalFileLink()}\""))
        } finally {
            first.delete()
            second.delete()
        }
    }

    @Test
    fun exactFilenameWinsOverConflictingShortAliases() {
        val context = RuntimeEnvironment.getApplication()
        val dir = getWebContentFilesDir(context)
        val exact = File(dir, "exact-name-test.txt")
        val first = File(dir, "first-id|exact-name-test.txt")
        val second = File(dir, "second-id|exact-name-test.txt")
        exact.writeText("exact file")
        first.writeText("first alias")
        second.writeText("second alias")
        try {
            val response =
                interceptAssetRequest(
                    Uri.parse(exact.getLocalFileLink()),
                    localFilesLoader(context),
                    null,
                    context,
                )
            assertEquals(
                "exact file",
                response?.data?.bufferedReader()?.use { it.readText() },
            )
            assertFalse(response?.mimeType == "text/html")
        } finally {
            exact.delete()
            first.delete()
            second.delete()
        }
    }

    @Test
    fun filenameChooserOnlyAppliesToTheAssetLoaderOrigin() {
        val context = RuntimeEnvironment.getApplication()
        val dir = getWebContentFilesDir(context)
        val first = File(dir, "first-id|origin-name-test.txt")
        val second = File(dir, "second-id|origin-name-test.txt")
        first.writeText("first")
        second.writeText("second")
        try {
            val loader = localFilesLoader(context)
            for (url in
                listOf(
                    "https://example.org/web-content-files/origin-name-test.txt",
                    "http://appassets.androidplatform.net/web-content-files/origin-name-test.txt",
                    "https://appassets.androidplatform.net:8443/web-content-files/origin-name-test.txt",
                    "https://user@appassets.androidplatform.net/web-content-files/origin-name-test.txt",
                )) {
                val uri = Uri.parse(url)
                assertTrue(uri.findLocalFileMatches(context).isEmpty())
                val response = interceptAssetRequest(uri, loader, null, context)
                assertFalse(url, response?.mimeType == "text/html")
            }
        } finally {
            first.delete()
            second.delete()
        }
    }

    @Test
    fun uuidPathAlwaysServesTheStoredFileRatherThanAConflictingAlias() {
        val context = RuntimeEnvironment.getApplication()
        val dir = getWebContentFilesDir(context)
        val uuid = "12345678-1234-1234-1234-123456789abc"
        val first = File(dir, "$uuid|asset-test.txt")
        val second =
            File(dir, "87654321-4321-4321-4321-cba987654321|$uuid|asset-test.txt")
        val third = File(dir, "other-id|$uuid|asset-test.txt")
        first.writeText("canonical")
        second.writeText("alias")
        third.writeText("another alias")
        try {
            val loader = localFilesLoader(context)
            val canonicalUrl = Uri.parse(first.getLocalFileLink())
            assertEquals(
                "canonical",
                interceptAssetRequest(canonicalUrl, loader, null, context)
                    ?.data
                    ?.bufferedReader()
                    ?.use { it.readText() },
            )
            first.delete()
            assertNull(canonicalUrl.resolveLocalFileLink(context))
            assertNull(
                interceptAssetRequest(canonicalUrl, loader, null, context)?.data
            )
            assertTrue(canonicalUrl.findLocalFileMatches(context).isEmpty())
        } finally {
            first.delete()
            second.delete()
            third.delete()
        }
    }

    private fun localFilesLoader(context: Context): WebViewAssetLoader =
        WebViewAssetLoader.Builder()
            .addPathHandler(
                "/${Constants.WEB_CONTENT_FILES_DIR}/",
                WebViewAssetLoader.InternalStoragePathHandler(
                    context,
                    getWebContentFilesDir(context),
                ),
            )
            .build()

    private fun loader(
        domain: String,
        path: String,
        body: String,
    ): WebViewAssetLoader =
        WebViewAssetLoader.Builder()
            .setDomain(domain)
            .setHttpAllowed(true)
            .addPathHandler(path) {
                WebResourceResponse("text/plain", "UTF-8", body.byteInputStream())
            }
            .build()
}
