package uk.nktnet.webviewkiosk.diagnostics

import android.Manifest
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
import uk.nktnet.webviewkiosk.diagnostics.LocationProviderProbe.Mode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.Q])
@LooperMode(LooperMode.Mode.PAUSED)
class LocationProviderProbeTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val manager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val locations = shadowOf(manager)
    private val main = shadowOf(Looper.getMainLooper())
    private val lines = mutableListOf<String>()
    private val probe = LocationProviderProbe(context, lines::add)

    @Before
    fun setUp() {
        UserSettings(context).allowLocation = true
        shadowOf(RuntimeEnvironment.getApplication())
            .grantPermissions(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
        locations.setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        locations.setProviderEnabled(LocationManager.NETWORK_PROVIDER, true)
        main.idleFor(Duration.ofSeconds(1))
    }

    @After
    fun tearDown() {
        probe.stop()
    }

    @Test
    fun noLocationRequestsRunWithoutAppSettingAndAndroidPermission() {
        UserSettings(context).allowLocation = false
        probe.start(Mode.WEBVIEW_CRITERIA)
        assertTrue(lines.last().startsWith("BLOCKED:"))
        assertTrue(listeners().isEmpty())

        UserSettings(context).allowLocation = true
        shadowOf(RuntimeEnvironment.getApplication())
            .denyPermissions(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
        probe.start(Mode.DIRECT_GPS)
        probe.start(Mode.WEBVIEW_CRITERIA)
        val count = lines.size
        main.idleFor(Duration.ofSeconds(61))
        assertTrue(lines.last().startsWith("BLOCKED:"))
        assertTrue(listeners().isEmpty())
        assertEquals(count, lines.size)
    }

    @Test
    fun approximatePermissionAllowsCriteriaButNeverDirectGps() {
        shadowOf(RuntimeEnvironment.getApplication())
            .denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        probe.start(Mode.DIRECT_GPS)
        assertTrue(lines.last().contains("requires precise"))
        assertTrue(listeners().isEmpty())

        probe.start(Mode.WEBVIEW_CRITERIA)
        assertTrue(lines.any { it.contains("fine=false, coarse=true") })
        assertEquals(1, listeners().size)
    }

    @Test
    fun criteriaRequestCompletesOnFreshCallbackWithoutReportingCoordinates() {
        probe.start(Mode.WEBVIEW_CRITERIA)
        assertEquals(1, listeners().size)
        val listener = listeners().single()
        listener.onLocationChanged(fix())

        assertTrue(lines.any { it.startsWith("FRESH_FIX:") })
        assertTrue(lines.last().startsWith("PASS:"))
        assertTrue(listeners().isEmpty())
        val report = lines.joinToString("\n")
        assertFalse(report.contains("12.345678"))
        assertFalse(report.contains("98.765432"))
        assertTrue(report.contains("hasSpeed=false"))
        assertTrue(report.contains("hasBearing=false"))
        val count = lines.size
        main.idleFor(Duration.ofSeconds(61))
        assertEquals(count, lines.size)
    }

    @Test
    fun directGpsRequestReceivesGpsUpdatesAndIgnoresNetworkUpdates() {
        probe.start(Mode.DIRECT_GPS)
        assertEquals(
            1,
            locations.getLegacyLocationRequests(LocationManager.GPS_PROVIDER).size,
        )
        assertTrue(
            locations
                .getLegacyLocationRequests(LocationManager.NETWORK_PROVIDER)
                .isEmpty()
        )
        locations.simulateLocation(
            LocationManager.NETWORK_PROVIDER,
            fix(LocationManager.NETWORK_PROVIDER),
        )
        main.idle()
        assertFalse(lines.any { it.startsWith("PASS:") })

        main.idleFor(Duration.ofMillis(1))
        locations.simulateLocation(LocationManager.GPS_PROVIDER, fix())
        main.idle()
        assertTrue(lines.last().startsWith("PASS:"))
        assertTrue(listeners().isEmpty())
    }

    @Test
    fun cachedCallbacksDoNotEstablishRecoveryAndOnlyOneIsReported() {
        val cached = fix()
        main.idleFor(Duration.ofSeconds(1))
        probe.start(Mode.DIRECT_GPS)
        val listener = listeners().single()
        listener.onLocationChanged(cached)
        listener.onLocationChanged(cached)
        assertEquals(1, lines.count { it.startsWith("CACHED_FIX:") })
        assertFalse(lines.any { it.startsWith("PASS:") })
        assertEquals(1, listeners().size)

        listener.onLocationChanged(fix())
        assertTrue(lines.last().startsWith("PASS:"))
    }

    @Test
    fun futureTimestampCannotBeReportedAsAFreshFix() {
        probe.start(Mode.DIRECT_GPS)
        val listener = listeners().single()
        listener.onLocationChanged(
            fix().apply {
                elapsedRealtimeNanos =
                    SystemClock.elapsedRealtimeNanos() + 1_000_000_000L
            }
        )
        assertTrue(lines.last().startsWith("INVALID_FIX:"))
        assertFalse(lines.any { it.startsWith("PASS:") })
        listener.onLocationChanged(fix())
        assertTrue(lines.last().startsWith("PASS:"))
    }

    @Test
    fun timeoutUnregistersNativeListenerAndRejectsLateCallbacks() {
        probe.start(Mode.WEBVIEW_CRITERIA)
        val listener = listeners().single()
        main.idleFor(Duration.ofMillis(LocationProviderProbe.TIMEOUT_MS - 1))
        assertEquals(1, listeners().size)
        main.idleFor(Duration.ofMillis(1))
        assertTrue(lines.last().startsWith("TIMEOUT:"))
        assertTrue(listeners().isEmpty())
        val count = lines.size
        listener.onLocationChanged(fix())
        listener.onProviderEnabled(LocationManager.GPS_PROVIDER)
        listener.onProviderDisabled(LocationManager.GPS_PROVIDER)
        assertEquals(count, lines.size)
    }

    @Test
    fun switchingTestsCancelsOldRequestsAndIgnoresTheirQueuedCallbacks() {
        probe.start(Mode.WEBVIEW_CRITERIA)
        val previous = listeners().single()
        probe.start(Mode.DIRECT_GPS)
        val current = listeners().single()
        assertFalse(previous === current)
        assertEquals(1, lines.count { it.startsWith("CANCELLED:") })
        val count = lines.size
        previous.onLocationChanged(fix())
        assertEquals(count, lines.size)
        current.onLocationChanged(fix())
        assertTrue(lines.last().startsWith("PASS:"))
    }

    @Test
    fun stoppingIsIdempotentAndRemovesTheTimeout() {
        probe.start(Mode.DIRECT_GPS)
        probe.stop()
        val count = lines.size
        probe.stop()
        main.idleFor(Duration.ofSeconds(61))
        assertEquals(count, lines.size)
        assertTrue(lines.last().startsWith("CANCELLED:"))
        assertTrue(listeners().isEmpty())
    }

    @Test
    fun permissionRevocationDoesNotSkipListenerCleanup() {
        probe.start(Mode.DIRECT_GPS)
        assertEquals(1, listeners().size)
        shadowOf(RuntimeEnvironment.getApplication())
            .denyPermissions(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
        probe.stop()
        assertTrue(listeners().isEmpty())
        assertTrue(lines.last().startsWith("CANCELLED:"))
    }

    @Test
    fun missingProviderIsReportedWithoutRetainingARequest() {
        manager.allProviders.toList().forEach(locations::removeProvider)
        probe.start(Mode.DIRECT_GPS)
        assertTrue(lines.last().contains("no GPS provider"))
        probe.start(Mode.WEBVIEW_CRITERIA)
        assertTrue(lines.last().contains("rejected the provider request"))
        assertTrue(listeners().isEmpty())
        val count = lines.size
        main.idleFor(Duration.ofSeconds(61))
        assertEquals(count, lines.size)
    }

    // Retain callbacks to verify that cancelled requests reject queued updates.
    @Suppress("DEPRECATION")
    private fun listeners() = locations.getRequestLocationUpdateListeners()

    private fun fix(provider: String = LocationManager.GPS_PROVIDER) =
        Location(provider).apply {
            latitude = 12.345678
            longitude = 98.765432
            accuracy = 5f
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        }
}
