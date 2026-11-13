@file:Suppress("DEPRECATION")

package uk.nktnet.webviewkiosk.utils

import android.net.Uri
import android.net.http.SslError
import android.webkit.ClientCertRequest
import android.webkit.HttpAuthHandler
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebResourceErrorCompat
import androidx.webkit.WebViewClientCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import uk.nktnet.webviewkiosk.utils.webview.LegacyWebViewClient

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LegacyWebViewClientTest {
    @Test
    fun olderAndroidKeepsCompatNavigationEvenOnVulnerableProviders() {
        for (sdk in listOf(21, 22, 23)) {
            assertFalse(needsFrameworkWebViewClient(sdk, "67.0.3396.87"))
            assertFalse(needsFrameworkWebViewClient(sdk, "72.0.3626.105"))
            assertFalse(needsFrameworkWebViewClient(sdk, null))
        }
    }

    @Test
    fun requestAwareAndroidUsesFrameworkOnlyForVulnerableProviders() {
        for (sdk in listOf(24, 25, 28, 36)) {
            assertFalse(needsFrameworkWebViewClient(sdk, "66.0.3359.158"))
            assertTrue(needsFrameworkWebViewClient(sdk, "67.0.3396.87"))
            assertTrue(needsFrameworkWebViewClient(sdk, "72.0.3626.105"))
            assertFalse(needsFrameworkWebViewClient(sdk, "73.0.3683.90"))
            assertFalse(needsFrameworkWebViewClient(sdk, "130.0.6723.73"))
        }
    }

    @Test
    fun unknownOrInvalidProviderVersionsKeepCompatNavigation() {
        for (sdk in listOf(23, 24, 25, 28, 36)) {
            assertFalse(needsFrameworkWebViewClient(sdk, null))
            assertFalse(needsFrameworkWebViewClient(sdk, "not-a-version"))
            assertFalse(needsFrameworkWebViewClient(sdk, "0"))
            assertFalse(needsFrameworkWebViewClient(sdk, ""))
        }
    }

    @Test
    fun frameworkClientPreservesNavigationInterceptionAndErrorCallbacks() {
        val events = mutableListOf<String>()
        val response = WebResourceResponse("text/html", "UTF-8", null)
        val delegate =
            object : WebViewClientCompat() {
                override fun onPageStarted(
                    view: WebView?,
                    url: String?,
                    favicon: android.graphics.Bitmap?,
                ) {
                    events += "start:$url"
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    events += "finish:$url"
                }

                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?,
                ): WebResourceResponse {
                    events += "intercept"
                    return response
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest,
                ): Boolean {
                    events += "navigate:${request.isForMainFrame}"
                    return request.isForMainFrame
                }

                @Deprecated("For API < 24")
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    url: String?,
                ): Boolean {
                    events += "legacy-navigate"
                    return true
                }

                override fun doUpdateVisitedHistory(
                    view: WebView?,
                    url: String?,
                    isReload: Boolean,
                ) {
                    events += "history"
                }

                override fun onReceivedHttpError(
                    view: WebView,
                    request: WebResourceRequest,
                    errorResponse: WebResourceResponse,
                ) {
                    events += "http:${errorResponse.statusCode}"
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceErrorCompat,
                ) {
                    events += "resource:${error.errorCode}"
                }

                @Deprecated(
                    "For API < 23",
                    ReplaceWith("events += \"legacy-resource:\$errorCode\""),
                )
                override fun onReceivedError(
                    view: WebView?,
                    errorCode: Int,
                    description: String?,
                    failingUrl: String?,
                ) {
                    events += "legacy-resource:$errorCode"
                }
            }
        val client: WebViewClient = LegacyWebViewClient(delegate)
        val webView = WebView(RuntimeEnvironment.getApplication())
        val url = "https://example.invalid/page"
        val request = documentRequest(url)
        val serverError =
            WebResourceResponse("text/html", "UTF-8", 503, "Error", emptyMap(), null)

        // Chromium must see a framework client, not a WebViewClientCompat.
        assertFalse(client is WebViewClientCompat)
        client.onPageStarted(webView, url, null)
        assertTrue(client.shouldOverrideUrlLoading(webView, request))
        assertFalse(
            client.shouldOverrideUrlLoading(
                webView,
                documentRequest("https://example.invalid/iframe", mainFrame = false),
            )
        )
        assertTrue(client.shouldOverrideUrlLoading(webView, url))
        assertSame(response, client.shouldInterceptRequest(webView, request))
        client.doUpdateVisitedHistory(webView, url, false)
        client.onReceivedHttpError(webView, request, serverError)
        client.onReceivedError(webView, -2, "Host not found", url)
        client.onPageFinished(webView, url)

        assertEquals(
            listOf(
                "start:$url",
                "navigate:true",
                "navigate:false",
                "legacy-navigate",
                "intercept",
                "history",
                "http:503",
                "legacy-resource:-2",
                "finish:$url",
            ),
            events,
        )
    }

    @Test
    fun frameworkClientPreservesSecurityCallbacksAndRendererRecovery() {
        val events = mutableListOf<String>()
        val delegate =
            object : WebViewClientCompat() {
                override fun onReceivedClientCertRequest(
                    view: WebView?,
                    request: ClientCertRequest?,
                ) {
                    events += "certificate"
                }

                override fun onReceivedHttpAuthRequest(
                    view: WebView?,
                    handler: HttpAuthHandler?,
                    host: String?,
                    realm: String?,
                ) {
                    events += "http-auth"
                }

                override fun onReceivedSslError(
                    view: WebView?,
                    handler: SslErrorHandler?,
                    error: SslError?,
                ) {
                    events += "ssl"
                }

                override fun onPageCommitVisible(view: WebView, url: String) {
                    events += "page-visible"
                }

                override fun onRenderProcessGone(
                    view: WebView,
                    detail: RenderProcessGoneDetail,
                ): Boolean {
                    events += "renderer-gone"
                    return true
                }
            }
        val client: WebViewClient = LegacyWebViewClient(delegate)
        val webView = WebView(RuntimeEnvironment.getApplication())

        client.onReceivedClientCertRequest(webView, null)
        client.onReceivedHttpAuthRequest(
            webView,
            null,
            "example.invalid",
            "restricted",
        )
        client.onReceivedSslError(webView, null, null)
        client.onPageCommitVisible(webView, "https://example.invalid")
        assertTrue(
            client.onRenderProcessGone(
                webView,
                object : RenderProcessGoneDetail() {
                    override fun didCrash(): Boolean = true

                    override fun rendererPriorityAtExit(): Int = 0
                },
            )
        )
        assertEquals(
            listOf(
                "certificate",
                "http-auth",
                "ssl",
                "page-visible",
                "renderer-gone",
            ),
            events,
        )
    }

    private fun documentRequest(
        url: String,
        mainFrame: Boolean = true,
    ): WebResourceRequest =
        object : WebResourceRequest {
            override fun getUrl(): Uri = Uri.parse(url)

            override fun isForMainFrame(): Boolean = mainFrame

            override fun isRedirect(): Boolean = false

            override fun hasGesture(): Boolean = false

            override fun getMethod(): String = "GET"

            override fun getRequestHeaders(): Map<String, String> = emptyMap()
        }
}
