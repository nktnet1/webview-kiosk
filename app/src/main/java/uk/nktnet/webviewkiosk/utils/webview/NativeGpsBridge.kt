package uk.nktnet.webviewkiosk.utils.webview

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.webkit.GeolocationPermissions
import android.webkit.WebView
import androidx.core.content.ContextCompat
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.ScriptHandler
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.util.UUID
import org.json.JSONObject
import uk.nktnet.webviewkiosk.config.UserSettings

/**
 * Direct GPS for HTTPS main documents that received a real WebView location grant.
 * The browser's original geolocation request still enforces Permissions Policy.
 */
internal class NativeGpsBridge(
    private val webView: WebView,
    private val userSettings: UserSettings,
    private val replyToDocument: (JavaScriptReplyProxy, String) -> Unit =
        ::postToDocument,
) {
    private class Session(
        val origin: String,
        val reply: JavaScriptReplyProxy,
        val token: String = UUID.randomUUID().toString(),
        var allowed: Boolean = false,
        var subscribed: Boolean = false,
    )

    private var session: Session? = null
    private var pageOrigin: String? = null
    private var foreground = false
    private var disposed = false
    private var script: ScriptHandler? = null
    private val source =
        NativeGpsLocationSource(
            webView.context,
            isAllowed = {
                !disposed &&
                    foreground &&
                    userSettings.allowLocation &&
                    userSettings.useDirectGps &&
                    session?.allowed == true &&
                    session?.subscribed == true
            },
            onPosition = { position -> send("position", position) },
            onError = ::onSourceError,
        )

    private fun onSourceError(code: Int, message: String) {
        if (code == 1) {
            session?.allowed = false
            source.stop()
        }
        send("error", JSONObject().put("code", code).put("message", message))
    }

    fun onNavigationStarted(url: String?) {
        source.stop()
        session = null
        pageOrigin = httpsOrigin(url)
    }

    fun setForeground(value: Boolean) {
        if (disposed || foreground == value) {
            return
        }
        foreground = value
        send("foreground", JSONObject().put("visible", value))
        updateSubscription()
    }

    /** Only this callback, reached through Chromium, can authorize the bridge. */
    fun permissionCallback(
        origin: String,
        callback: GeolocationPermissions.Callback?,
    ): GeolocationPermissions.Callback {
        val pending = session?.takeIf { it.origin == httpsOrigin(origin) }
        return GeolocationPermissions.Callback { requestedOrigin, allow, _ ->
            val current = pending != null && session === pending && !disposed
            callback?.invoke(
                requestedOrigin,
                allow && (pending == null || current),
                false,
            )
            if (current) {
                pending.allowed = allow
                send("permission", JSONObject().put("allowed", allow))
                updateSubscription()
            }
        }
    }

    internal fun receive(
        incoming: WebMessageCompat,
        origin: Uri,
        isMainFrame: Boolean,
        reply: JavaScriptReplyProxy,
    ) {
        if (
            disposed || !isMainFrame || incoming.type != WebMessageCompat.TYPE_STRING
        ) {
            return
        }
        val sender = httpsOrigin(origin.toString()) ?: return
        if (
            sender != pageOrigin ||
                sender != runCatching { httpsOrigin(webView.url) }.getOrNull()
        ) {
            return
        }
        val data = incoming.data?.takeIf { it.length <= 4096 } ?: return
        val message = runCatching { JSONObject(data) }.getOrNull() ?: return
        if (message.optString("type") == "hello") {
            val nonce = message.optString("nonce")
            if (nonce.isEmpty() || nonce.length > 100) {
                return
            }
            val existing = session
            // A different endpoint must wait for the next main-frame navigation.
            if (existing != null && existing.reply !== reply) {
                return
            }
            if (existing == null) {
                session = Session(sender, reply)
            }
            send(
                "hello",
                JSONObject().put("nonce", nonce).put("visible", foreground),
            )
            return
        }
        val current = session ?: return
        if (
            current.reply !== reply ||
                current.origin != sender ||
                message.optString("token") != current.token
        ) {
            return
        }
        when (message.optString("type")) {
            "validate" -> {
                val allowed =
                    current.allowed &&
                        userSettings.allowLocation &&
                        userSettings.useDirectGps &&
                        ContextCompat.checkSelfPermission(
                            webView.context,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                        ) == PackageManager.PERMISSION_GRANTED
                if (!allowed) {
                    current.allowed = false
                    current.subscribed = false
                    source.stop()
                }
                send(
                    "validated",
                    JSONObject()
                        .put("id", message.optInt("id"))
                        .put("allowed", allowed),
                )
                if (allowed) {
                    // An existing watch may be subscribed while GPS registration
                    // is pending. Let the new request try the provider immediately.
                    updateSubscription()
                }
            }
            "subscribe" -> {
                current.subscribed = true
                updateSubscription()
            }
            "unsubscribe" -> {
                current.subscribed = false
                source.stop()
            }
            "release" -> {
                source.stop()
                session = null
            }
        }
    }

    private fun updateSubscription() {
        if (
            !disposed &&
                foreground &&
                session?.allowed == true &&
                session?.subscribed == true
        ) {
            source.start()
        } else {
            source.stop()
        }
    }

    private fun send(type: String, payload: JSONObject) {
        val current = session ?: return
        if (disposed) {
            return
        }
        payload.put("type", type).put("token", current.token)
        // Replies target the sending document, never whichever page navigated next.
        runCatching { replyToDocument(current.reply, payload.toString()) }
            .onFailure {
                source.stop()
                session = null
            }
    }

    fun dispose(rendererGone: Boolean = false) {
        if (disposed) {
            return
        }
        disposed = true
        source.stop()
        session = null
        if (!rendererGone) {
            runCatching {
                if (
                    WebViewFeature.isFeatureSupported(
                        WebViewFeature.DOCUMENT_START_SCRIPT
                    )
                ) {
                    script?.remove()
                }
            }
            runCatching {
                if (
                    WebViewFeature.isFeatureSupported(
                        WebViewFeature.WEB_MESSAGE_LISTENER
                    )
                ) {
                    WebViewCompat.removeWebMessageListener(webView, NAME)
                }
            }
        }
        script = null
    }

    companion object {
        private const val NAME = "WebviewKioskGps"

        private fun postToDocument(reply: JavaScriptReplyProxy, message: String) {
            if (
                WebViewFeature.isFeatureSupported(
                    WebViewFeature.WEB_MESSAGE_LISTENER
                )
            ) {
                reply.postMessage(message)
            } else {
                throw UnsupportedOperationException("Web messages are unavailable")
            }
        }

        fun attach(webView: WebView, userSettings: UserSettings): NativeGpsBridge? {
            if (!userSettings.allowLocation || !userSettings.useDirectGps) {
                return null
            }
            var candidate: NativeGpsBridge? = null
            return runCatching {
                if (
                    WebViewFeature.isFeatureSupported(
                        WebViewFeature.DOCUMENT_START_SCRIPT
                    )
                ) {
                    if (
                        WebViewFeature.isFeatureSupported(
                            WebViewFeature.WEB_MESSAGE_LISTENER
                        )
                    ) {
                        val bridge = NativeGpsBridge(webView, userSettings)
                        candidate = bridge
                        val js =
                            webView.context.assets
                                .open("native-gps.js")
                                .bufferedReader()
                                .use { it.readText() }
                        // Sites can navigate across origins. Check HTTPS, the main
                        // frame and the current document session on receipt.
                        WebViewCompat.addWebMessageListener(
                            webView,
                            NAME,
                            setOf("*"),
                        ) { _, message, origin, mainFrame, reply ->
                            bridge.receive(message, origin, mainFrame, reply)
                        }
                        bridge.script =
                            WebViewCompat.addDocumentStartJavaScript(
                                webView,
                                js,
                                setOf("*"),
                            )
                        return@runCatching bridge
                    }
                }
                null
            }
                .getOrElse { error ->
                    candidate?.dispose()
                    Log.w(
                        NAME,
                        "Direct GPS unavailable, using WebView geolocation",
                        error,
                    )
                    null
                }
        }

        internal fun httpsOrigin(url: String?): String? {
            val uri = url?.let(Uri::parse) ?: return null
            if (!uri.scheme.equals("https", ignoreCase = true)) {
                return null
            }
            val host =
                uri.host?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
            val port = uri.port
            return "https://$host" +
                if (port == -1 || port == 443) {
                    ""
                } else {
                    ":$port"
                }
        }
    }
}
