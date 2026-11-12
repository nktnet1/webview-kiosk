@file:Suppress("UsePropertyAccessSyntax", "DEPRECATION")

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
    fun onlyVulnerableWebViewProvidersUseTheFrameworkClient() {
        assertTrue(needsFrameworkWebViewClient(25, "72.0.3626.105"))
        assertTrue(needsFrameworkWebViewClient(25, "54.0.2840.85"))
        assertTrue(needsFrameworkWebViewClient(28, "72.0.3626.105"))
        assertFalse(needsFrameworkWebViewClient(25, "73.0.3683.90"))
        assertFalse(needsFrameworkWebViewClient(25, "130.0.6723.73"))
        assertFalse(needsFrameworkWebViewClient(36, "136.0.7103.1"))
        assertFalse(needsFrameworkWebViewClient(21, "54.0.2840.85"))
    }

    @Test
    fun unknownProviderVersionFallsBackOnOlderAndroidOnly() {
        assertTrue(needsFrameworkWebViewClient(23, null))
        assertTrue(needsFrameworkWebViewClient(25, "not-a-version"))
        assertFalse(needsFrameworkWebViewClient(28, null))
        assertFalse(needsFrameworkWebViewClient(36, "not-a-version"))
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
                    events += "navigate"
                    return true
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

                @Deprecated("For API < 23")
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
        assertTrue(client.shouldOverrideUrlLoading(webView, url))
        assertSame(response, client.shouldInterceptRequest(webView, request))
        client.doUpdateVisitedHistory(webView, url, false)
        client.onReceivedHttpError(webView, request, serverError)
        client.onReceivedError(webView, -2, "Host not found", url)
        client.onPageFinished(webView, url)

        assertEquals(
            listOf(
                "start:$url",
                "navigate",
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

    private fun documentRequest(url: String): WebResourceRequest =
        object : WebResourceRequest {
            override fun getUrl(): Uri = Uri.parse(url)

            override fun isForMainFrame(): Boolean = true

            override fun isRedirect(): Boolean = false

            override fun hasGesture(): Boolean = false

            override fun getMethod(): String = "GET"

            override fun getRequestHeaders(): Map<String, String> = emptyMap()
        }
}
