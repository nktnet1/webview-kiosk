package uk.nktnet.webviewkiosk.utils.webview

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import android.webkit.GeolocationPermissions
import android.webkit.WebView
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import java.time.Duration
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.Q])
@LooperMode(LooperMode.Mode.PAUSED)
class NativeGpsBridgeTest {
    private class Reply : JavaScriptReplyProxy() {
        val messages = mutableListOf<JSONObject>()
        var broken = false

        override fun postMessage(message: String) {
            deliver(message)
        }

        fun deliver(message: String) {
            check(!broken)
            messages.add(JSONObject(message))
        }

        override fun postMessage(arrayBuffer: ByteArray) =
            error("String messages only")
    }

    private val app = RuntimeEnvironment.getApplication()
    private val settings = UserSettings(app)
    private val view = WebView(app)
    private val manager =
        app.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val locations = shadowOf(manager)
    private val main = shadowOf(Looper.getMainLooper())
    private val reply = Reply()
    private val origin = "https://example.com"
    private val bridge =
        NativeGpsBridge(view, settings) { endpoint, message ->
            (endpoint as Reply).deliver(message)
        }
    private var token = ""

    @Before
    fun setUp() {
        settings.allowLocation = true
        settings.useDirectGps = true
        shadowOf(app)
            .grantPermissions(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
        locations.setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        main.idleFor(Duration.ofSeconds(1))
        view.loadUrl("$origin/page")
        bridge.onNavigationStarted("$origin/page")
        bridge.setForeground(true)
        hello()
    }

    @After
    fun tearDown() {
        bridge.dispose(rendererGone = true)
        view.destroy()
    }

    @Test
    fun javascriptCannotSelfAuthorizeNativeLocation() {
        send("subscribe")
        send("permission", JSONObject().put("allowed", true))
        assertTrue(listeners().isEmpty())
        grant()
        assertEquals(1, listeners().size)
    }

    @Test
    fun permissionDenialNeverStartsGpsAndTheOriginalBrowserCallbackIsResolved() {
        send("subscribe")
        var result: Boolean? = null
        bridge
            .permissionCallback(
                origin,
                GeolocationPermissions.Callback { _, allowed, retained ->
                    result = allowed
                    assertFalse(retained)
                },
            )
            .invoke(origin, false, true)
        assertEquals(false, result)
        assertTrue(listeners().isEmpty())
        assertFalse(reply.messages.last().getBoolean("allowed"))
    }

    @Test
    fun messagesFromIframesInsecureForeignAndOpaqueOriginsCannotStartOrStopGps() {
        grant()
        send("subscribe")
        val forged =
            JSONObject().put("type", "unsubscribe").put("token", token).toString()
        for (sender in
            listOf(
                "http://example.com",
                "https://other.example",
                "null",
                "file:///tmp/x",
            )) {
            bridge.receive(WebMessageCompat(forged), Uri.parse(sender), true, reply)
        }
        bridge.receive(WebMessageCompat(forged), Uri.parse(origin), false, reply)
        assertEquals(1, listeners().size)
    }

    @Test
    fun staleTokenAndAnotherReplyEndpointCannotUseAnAuthorizedSession() {
        grant()
        val message =
            JSONObject().put("type", "subscribe").put("token", "stale").toString()
        bridge.receive(WebMessageCompat(message), Uri.parse(origin), true, reply)
        bridge.receive(
            WebMessageCompat(message.replace("stale", token)),
            Uri.parse(origin),
            true,
            Reply(),
        )
        assertTrue(listeners().isEmpty())
        send("subscribe")
        assertEquals(1, listeners().size)
    }

    @Test
    fun navigationCancelsGpsAndLateGrantsAndFixesCannotReachTheNewDocument() {
        send("subscribe")
        val pending = bridge.permissionCallback(origin, null)
        grant()
        val previous = listeners().single()
        bridge.onNavigationStarted("$origin/next")
        view.loadUrl("$origin/next")
        hello()
        reply.messages.clear()
        pending.invoke(origin, true, false)
        previous.onLocationChanged(fix())
        send("subscribe")
        assertTrue(listeners().isEmpty())
        assertTrue(reply.messages.isEmpty())
    }

    @Test
    fun newDocumentReceivesOnlyItsOwnRepliesAfterSameOriginNavigation() {
        grant()
        send("subscribe")
        val oldToken = token
        val oldListener = listeners().single()
        val pending = bridge.permissionCallback(origin, null)
        val nextReply = Reply()
        bridge.onNavigationStarted("$origin/next")
        view.loadUrl("$origin/next")
        hello(nextReply)
        assertFalse(oldToken == token)
        val oldCount = reply.messages.size
        val newCount = nextReply.messages.size

        // A delayed hello, message, permission grant or fix from the old page
        // must not replace the new endpoint or authorize its session.
        bridge.receive(
            WebMessageCompat("{\"type\":\"hello\",\"nonce\":\"old\"}"),
            Uri.parse(origin),
            true,
            reply,
        )
        send("subscribe", endpoint = reply)
        send("subscribe", endpoint = nextReply, sessionToken = oldToken)
        pending.invoke(origin, true, false)
        oldListener.onLocationChanged(fix())
        assertTrue(listeners().isEmpty())
        assertEquals(oldCount, reply.messages.size)
        assertEquals(newCount, nextReply.messages.size)

        grant()
        send("subscribe", endpoint = nextReply)
        listeners().single().onLocationChanged(fix())
        assertEquals("position", nextReply.messages.last().getString("type"))
        assertEquals(token, nextReply.messages.last().getString("token"))
        assertEquals(oldCount, reply.messages.size)
    }

    @Test
    fun validationRepliesEchoEachAttemptIdentifier() {
        grant()
        for (validationId in listOf(1L, Int.MAX_VALUE.toLong() + 1)) {
            send(
                "validate",
                JSONObject().put("id", 42).put("validationId", validationId),
            )
            val checked = reply.messages.last()
            assertEquals("validated", checked.getString("type"))
            assertEquals(42, checked.getInt("id"))
            assertEquals(validationId, checked.getLong("validationId"))
            assertTrue(checked.getBoolean("allowed"))
        }
    }

    @Test
    fun backgroundAndScreenSleepStopGpsAndWakeRegistersAFreshListener() {
        grant()
        send("subscribe")
        val beforeSleep = listeners().single()
        bridge.setForeground(false)
        assertTrue(listeners().isEmpty())
        beforeSleep.onLocationChanged(fix())
        assertEquals("foreground", reply.messages.last().getString("type"))
        main.idleFor(Duration.ofSeconds(1))
        bridge.setForeground(true)
        val afterWake = listeners().single()
        assertFalse(beforeSleep === afterWake)
        afterWake.onLocationChanged(fix())
        assertEquals("position", reply.messages.last().getString("type"))
    }

    @Test
    fun foregroundWatchRecoversWhenGpsProviderReturnsAfterWake() {
        grant()
        send("subscribe")
        bridge.setForeground(false)
        locations.removeProvider(LocationManager.GPS_PROVIDER)
        bridge.setForeground(true)
        assertTrue(listeners().isEmpty())
        assertEquals(2, reply.messages.last().getInt("code"))
        val replies = reply.messages.size
        main.idleFor(Duration.ofSeconds(5))
        assertEquals(replies, reply.messages.size)

        locations.setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        main.idleFor(Duration.ofSeconds(5))
        listeners().single().onLocationChanged(fix())
        assertEquals("position", reply.messages.last().getString("type"))
    }

    @Test
    fun gpsRetryCannotRegisterWhenHiddenOrUnsubscribed() {
        grant()
        locations.removeProvider(LocationManager.GPS_PROVIDER)
        send("subscribe")
        bridge.setForeground(false)
        locations.setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        main.idleFor(Duration.ofSeconds(5))
        assertTrue(listeners().isEmpty())

        locations.removeProvider(LocationManager.GPS_PROVIDER)
        bridge.setForeground(true)
        send("unsubscribe")
        locations.setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        main.idleFor(Duration.ofSeconds(5))
        assertTrue(listeners().isEmpty())
    }

    @Test
    fun aNewRequestCanStartGpsBeforeTheNextScheduledRetry() {
        grant()
        locations.removeProvider(LocationManager.GPS_PROVIDER)
        send("subscribe")
        assertTrue(listeners().isEmpty())

        reply.messages.clear()
        send("validate", JSONObject().put("id", 41))
        assertEquals(
            "This device has no GPS provider",
            reply.messages.single().getString("unavailable"),
        )

        locations.setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        send("validate", JSONObject().put("id", 42))
        assertEquals(1, listeners().size)
        val listener = listeners().single()
        main.idleFor(Duration.ofSeconds(5))
        assertEquals(listener, listeners().single())
    }

    @Test
    fun validationReportsDisabledGpsWhileTheExistingWatchCanStillRecover() {
        grant()
        locations.setProviderEnabled(LocationManager.GPS_PROVIDER, false)
        send("subscribe")
        main.idle()
        val listener = listeners().single()

        reply.messages.clear()
        send("validate", JSONObject().put("id", 42))
        val replyWhenDisabled = reply.messages.single()
        assertEquals("validated", replyWhenDisabled.getString("type"))
        assertTrue(replyWhenDisabled.getBoolean("allowed"))
        assertEquals("GPS is disabled", replyWhenDisabled.getString("unavailable"))

        locations.setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        main.idle()
        send("validate", JSONObject().put("id", 43))
        assertFalse(reply.messages.last().has("unavailable"))
        assertEquals(listener, listeners().single())
    }

    @Test
    fun bothAppSettingsAndPrecisePermissionAreCheckedBeforeReturningACachedPosition() {
        for (restriction in listOf("location", "direct", "precise")) {
            settings.allowLocation = true
            settings.useDirectGps = true
            shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
            grant()
            when (restriction) {
                "location" -> settings.allowLocation = false
                "direct" -> settings.useDirectGps = false
                "precise" ->
                    shadowOf(app)
                        .denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            send("validate", JSONObject().put("id", 42))
            val checked = reply.messages.last()
            assertEquals("validated", checked.getString("type"))
            assertEquals(42, checked.getInt("id"))
            assertFalse(checked.getBoolean("allowed"))
            assertTrue(listeners().isEmpty())
        }
    }

    @Test
    fun runtimePermissionRevocationReturnsErrorOneWithoutCoordinates() {
        grant()
        send("subscribe")
        val listener = listeners().single()
        shadowOf(app).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        listener.onLocationChanged(fix())
        assertEquals(1, reply.messages.last().getInt("code"))
        assertFalse(reply.messages.last().has("coords"))
        assertTrue(listeners().isEmpty())
    }

    @Test
    fun unsubscribeReleaseAndRendererDeathRemoveGpsAndRejectQueuedCallbacks() {
        grant()
        send("subscribe")
        send("unsubscribe")
        assertTrue(listeners().isEmpty())
        send("subscribe")
        val previous = listeners().single()
        send("release")
        assertTrue(listeners().isEmpty())
        previous.onLocationChanged(fix())
        hello()
        grant()
        send("subscribe")
        val current = listeners().single()
        bridge.dispose(rendererGone = true)
        val count = reply.messages.size
        current.onLocationChanged(fix())
        bridge.setForeground(true)
        send("subscribe")
        assertTrue(listeners().isEmpty())
        assertEquals(count, reply.messages.size)
    }

    @Test
    fun failedDocumentReplyStopsLocationWork() {
        grant()
        send("subscribe")
        reply.broken = true
        listeners().single().onLocationChanged(fix())
        assertTrue(listeners().isEmpty())
    }

    @Test
    @SuppressLint(
        "RequiresFeature"
    ) // Construct a binary message without a live WebView.
    fun malformedAndOversizedMessagesAreIgnored() {
        val previous = reply.messages.toList()
        bridge.receive(
            WebMessageCompat(byteArrayOf()),
            Uri.parse(origin),
            true,
            reply,
        )
        for (message in listOf(null, "{", "[]", "x".repeat(4097))) {
            bridge.receive(WebMessageCompat(message), Uri.parse(origin), true, reply)
        }
        assertEquals(previous, reply.messages)
        assertTrue(listeners().isEmpty())
        grant()
        send("subscribe")
        assertEquals(1, listeners().size)
    }

    @Test
    fun directGpsSurvivesSettingsImportExport() {
        val key = UserSettingsKeys.Device.USE_DIRECT_GPS
        settings.useDirectGps = false
        assertFalse(settings.exportJson().getBoolean(key))
        assertTrue(settings.importJson(JSONObject().put(key, true).toString()))
        assertTrue(UserSettings(app).useDirectGps)
        assertTrue(settings.exportJson().getBoolean(key))
        assertTrue(settings.importJson("{}"))
        assertTrue(settings.useDirectGps)
    }

    @Test
    fun onlyHttpsOriginsAreEligibleAndDefaultPortsAreNormalized() {
        assertEquals(
            origin,
            NativeGpsBridge.httpsOrigin("https://EXAMPLE.com:443/path?q=1#x"),
        )
        assertEquals(
            "https://example.com:8443",
            NativeGpsBridge.httpsOrigin("https://example.com:8443/path"),
        )
        for (url in
            listOf(
                null,
                "http://example.com",
                "file:///tmp/x",
                "data:text/html,test",
                "https:///",
            )) {
            assertNull(NativeGpsBridge.httpsOrigin(url))
        }
    }

    private fun hello(endpoint: Reply = reply) {
        bridge.receive(
            WebMessageCompat("{\"type\":\"hello\",\"nonce\":\"test\"}"),
            Uri.parse(origin),
            true,
            endpoint,
        )
        token = endpoint.messages.last().getString("token")
    }

    private fun send(
        type: String,
        payload: JSONObject = JSONObject(),
        endpoint: Reply = reply,
        sessionToken: String = token,
    ) {
        bridge.receive(
            WebMessageCompat(
                payload.put("type", type).put("token", sessionToken).toString()
            ),
            Uri.parse(origin),
            true,
            endpoint,
        )
    }

    private fun grant() {
        bridge.permissionCallback(origin, null).invoke(origin, true, false)
    }

    @Suppress("DEPRECATION")
    private fun listeners() = locations.getRequestLocationUpdateListeners()

    private fun fix() =
        Location(LocationManager.GPS_PROVIDER).apply {
            latitude = 12.3
            longitude = 45.6
            accuracy = 7f
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        }
}
