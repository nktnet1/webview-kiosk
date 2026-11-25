package uk.nktnet.webviewkiosk.utils.webview

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import java.time.Duration
import org.json.JSONObject
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.Q])
@LooperMode(LooperMode.Mode.PAUSED)
class NativeGpsLocationSourceTest {
    private val app = RuntimeEnvironment.getApplication()
    private val manager =
        app.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val locations = shadowOf(manager)
    private val main = shadowOf(Looper.getMainLooper())
    private var allowed = true
    private val positions = mutableListOf<JSONObject>()
    private val errors = mutableListOf<Int>()
    private val source =
        NativeGpsLocationSource(app, { allowed }, positions::add) { code, _ ->
            errors.add(code)
        }

    @Before
    fun setUp() {
        shadowOf(app)
            .grantPermissions(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
        locations.setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        locations.setProviderEnabled("fused", false)
        main.idleFor(Duration.ofSeconds(1))
    }

    @After fun tearDown() = source.stop()

    @Test
    fun usesOneGpsRequestEvenWhenFusedIsDisabledAndNeverRequestsNetworkOrCriteria() {
        source.start()
        source.start()
        assertEquals(1, listeners().size)
        assertEquals(
            1,
            locations.getLegacyLocationRequests(LocationManager.GPS_PROVIDER).size,
        )
        assertTrue(locations.getLegacyLocationRequests("fused").isEmpty())
        assertTrue(
            locations
                .getLegacyLocationRequests(LocationManager.NETWORK_PROVIDER)
                .isEmpty()
        )
        listeners().single().onLocationChanged(fix())
        assertEquals(1, positions.size)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun appPermissionAndPreciseAndroidPermissionAreRequired() {
        allowed = false
        source.start()
        assertEquals(listOf(1), errors)
        assertTrue(listeners().isEmpty())
        allowed = true
        shadowOf(app).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        source.start()
        assertEquals(listOf(1, 1), errors)
        assertTrue(listeners().isEmpty())
    }

    @Test
    fun rejectsCachedFutureNonGpsDuplicateAndOutOfOrderFixes() {
        val cached = fix()
        main.idleFor(Duration.ofSeconds(1))
        source.start()
        val listener = listeners().single()
        listener.onLocationChanged(cached)
        listener.onLocationChanged(
            fix().apply { elapsedRealtimeNanos += 1_000_000_000L }
        )
        listener.onLocationChanged(fix(LocationManager.NETWORK_PROVIDER))
        assertTrue(positions.isEmpty())
        val first = fix()
        listener.onLocationChanged(first)
        listener.onLocationChanged(first)
        listener.onLocationChanged(cached)
        assertEquals(1, positions.size)
        main.idleFor(Duration.ofSeconds(1))
        listener.onLocationChanged(fix())
        assertEquals(2, positions.size)
    }

    @Test
    fun rejectsInvalidCoordinatesAndMissingOrInvalidAccuracy() {
        source.start()
        val listener = listeners().single()
        listOf(
                fix().apply { latitude = Double.NaN },
                fix().apply { longitude = Double.POSITIVE_INFINITY },
                fix().apply { latitude = 91.0 },
                fix().apply { longitude = -181.0 },
                fix().apply { removeAccuracy() },
                fix().apply { accuracy = -1f },
                fix().apply { accuracy = Float.NaN },
            )
            .forEach(listener::onLocationChanged)
        assertTrue(positions.isEmpty())
    }

    @Test
    fun mapsCoordinatesUnitsOptionalValuesAndFixTimestamp() {
        source.start()
        val location =
            fix().apply {
                altitude = 45.0
                verticalAccuracyMeters = 8f
                speed = 12f
                bearing = 270f
            }
        main.idleFor(Duration.ofMillis(10))
        listeners().single().onLocationChanged(location)
        val position = positions.single()
        assertEquals(location.time, position.getLong("timestamp"))
        assertEquals(10.0, position.getDouble("ageMs"), 0.001)
        val coords = position.getJSONObject("coords")
        assertEquals(12.0, coords.getDouble("speed"), 0.001)
        assertEquals(270.0, coords.getDouble("heading"), 0.001)
        assertEquals(45.0, coords.getDouble("altitude"), 0.001)
        assertEquals(8.0, coords.getDouble("altitudeAccuracy"), 0.001)
    }

    @Test
    fun missingOptionalValuesAndStationaryHeadingAreNull() {
        source.start()
        listeners().single().onLocationChanged(fix())
        val coords = positions.single().getJSONObject("coords")
        for (name in listOf("altitude", "altitudeAccuracy", "heading", "speed")) {
            assertTrue(coords.isNull(name))
        }
        main.idleFor(Duration.ofSeconds(1))
        listeners()
            .single()
            .onLocationChanged(
                fix().apply {
                    speed = 0f
                    bearing = 90f
                }
            )
        assertTrue(positions.last().getJSONObject("coords").isNull("heading"))
    }

    @Test
    fun stopAndRestartRejectQueuedCallbacksAndDemandAFreshPostWakeFix() {
        source.start()
        val previous = listeners().single()
        val preSleep = fix()
        source.stop()
        source.stop()
        assertTrue(listeners().isEmpty())
        main.idleFor(Duration.ofSeconds(1))
        source.start()
        val current = listeners().single()
        assertFalse(previous === current)
        previous.onLocationChanged(fix())
        previous.onProviderDisabled(LocationManager.GPS_PROVIDER)
        current.onLocationChanged(preSleep)
        assertTrue(positions.isEmpty())
        assertTrue(errors.isEmpty())
        current.onLocationChanged(fix())
        assertEquals(1, positions.size)
    }

    @Test
    fun runtimePermissionRevocationStopsDeliveryAndRemovesListener() {
        source.start()
        val listener = listeners().single()
        shadowOf(app).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        listener.onLocationChanged(fix())
        assertTrue(positions.isEmpty())
        assertEquals(listOf(1), errors)
        assertTrue(listeners().isEmpty())
    }

    @Test
    fun appSettingRevocationStopsDeliveryAndRemovesListener() {
        source.start()
        val listener = listeners().single()
        allowed = false
        listener.onLocationChanged(fix())
        assertTrue(positions.isEmpty())
        assertEquals(listOf(1), errors)
        assertTrue(listeners().isEmpty())
    }

    @Test
    fun disabledGpsReportsUnavailableButCanRecoverWithoutAReboot() {
        locations.setProviderEnabled(LocationManager.GPS_PROVIDER, false)
        source.start()
        assertTrue(errors.contains(2))
        val listener = listeners().single()
        locations.setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        main.idle()
        listener.onLocationChanged(fix())
        assertEquals(1, positions.size)
    }

    @Test
    fun missingGpsProviderDoesNotRetainAListener() {
        locations.removeProvider(LocationManager.GPS_PROVIDER)
        source.start()
        assertEquals(listOf(2), errors)
        assertTrue(listeners().isEmpty())
    }

    @Test
    fun unavailableAndroidLocationServiceReportsAnErrorAndCanBeStopped() {
        for (throwOnLookup in listOf(false, true)) {
            val context =
                object : ContextWrapper(app) {
                    override fun getSystemService(name: String): Any? {
                        if (name != Context.LOCATION_SERVICE) {
                            return super.getSystemService(name)
                        }
                        if (throwOnLookup) {
                            throw IllegalStateException(
                                "Location service unavailable"
                            )
                        }
                        return null
                    }
                }
            val errors = mutableListOf<Int>()
            val unavailable =
                NativeGpsLocationSource(context, { true }, positions::add) { code, _
                    ->
                    errors.add(code)
                }
            unavailable.start()
            unavailable.stop()
            assertEquals(listOf(2), errors)
            assertTrue(positions.isEmpty())
        }
    }

    // Retain callbacks to check rejection after cancellation and wake.
    @Suppress("DEPRECATION")
    private fun listeners() = locations.getRequestLocationUpdateListeners()

    private fun fix(provider: String = LocationManager.GPS_PROVIDER) =
        Location(provider).apply {
            latitude = 12.345678
            longitude = 98.765432
            accuracy = 7f
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        }
}
