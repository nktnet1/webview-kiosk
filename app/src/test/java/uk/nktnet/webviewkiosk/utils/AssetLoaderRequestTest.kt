package uk.nktnet.webviewkiosk.utils

import android.net.Uri
import android.webkit.WebResourceResponse
import androidx.webkit.WebViewAssetLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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
