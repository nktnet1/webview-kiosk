package uk.nktnet.webviewkiosk.utils

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceResponse
import androidx.webkit.WebViewAssetLoader
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
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
    fun ambiguousFilenameAliasesDoNotServeAnArbitraryFile() {
        val context = RuntimeEnvironment.getApplication()
        val dir = getWebContentFilesDir(context)
        val first = File(dir, "first-id|ambiguous-name-test.txt")
        val second = File(dir, "second-id|ambiguous-name-test.txt")
        first.writeText("first")
        second.writeText("second")
        try {
            val loader = localFilesLoader(context)
            assertNull(
                interceptAssetRequest(
                        Uri.parse(
                            "https://appassets.androidplatform.net/web-content-files/ambiguous-name-test.txt"
                        ),
                        loader,
                        null,
                        context,
                    )
                    ?.data
            )
            for ((file, content) in listOf(first to "first", second to "second")) {
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
        first.writeText("canonical")
        second.writeText("alias")
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
        } finally {
            first.delete()
            second.delete()
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
