package uk.nktnet.webviewkiosk.utils.webview

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import org.json.JSONObject

/** A foreground-only GPS subscription shared by the current document's requests. */
internal class NativeGpsLocationSource(
    private val context: Context,
    private val isAllowed: () -> Boolean,
    private val onPosition: (JSONObject) -> Unit,
    private val onError: (Int, String) -> Unit,
) {
    private val manager = runCatching {
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    }
        .onFailure { Log.w(TAG, "Unable to get Android location service", it) }
        .getOrNull()
    private var listener: LocationListener? = null

    fun start() {
        if (listener != null) {
            return
        }
        val manager = manager
        if (manager == null) {
            onError(2, "Android location service is unavailable")
            return
        }
        if (
            !isAllowed() ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                ) != PackageManager.PERMISSION_GRANTED
        ) {
            onError(
                1,
                "Direct GPS requires Allow Location and precise location permission",
            )
            return
        }
        val startedAt = SystemClock.elapsedRealtimeNanos()
        var lastFixAt = startedAt - 1
        val request =
            object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (listener !== this) {
                        return
                    }
                    if (
                        !isAllowed() ||
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_FINE_LOCATION,
                            ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        stop()
                        onError(1, "Location permission is no longer available")
                        return
                    }
                    val now = SystemClock.elapsedRealtimeNanos()
                    val fixAt = location.elapsedRealtimeNanos
                    if (
                        location.provider != LocationManager.GPS_PROVIDER ||
                            fixAt < startedAt ||
                            fixAt <= lastFixAt ||
                            fixAt > now ||
                            !location.latitude.isFinite() ||
                            location.latitude !in -90.0..90.0 ||
                            !location.longitude.isFinite() ||
                            location.longitude !in -180.0..180.0 ||
                            !location.hasAccuracy() ||
                            !location.accuracy.isFinite() ||
                            location.accuracy < 0
                    ) {
                        return
                    }
                    lastFixAt = fixAt
                    val coords =
                        JSONObject().apply {
                            put("latitude", location.latitude)
                            put("longitude", location.longitude)
                            put("accuracy", location.accuracy.toDouble())
                            put(
                                "altitude",
                                nullable(location.hasAltitude(), location.altitude),
                            )
                            put(
                                "altitudeAccuracy",
                                if (
                                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                                        location.hasVerticalAccuracy()
                                ) {
                                    nullable(
                                        true,
                                        location.verticalAccuracyMeters.toDouble(),
                                    )
                                } else {
                                    JSONObject.NULL
                                },
                            )
                            put(
                                "speed",
                                nullable(
                                    location.hasSpeed(),
                                    location.speed.toDouble(),
                                ),
                            )
                            put(
                                "heading",
                                if (location.hasSpeed() && location.speed == 0f) {
                                    JSONObject.NULL
                                } else {
                                    nullable(
                                        location.hasBearing(),
                                        location.bearing.toDouble(),
                                    )
                                },
                            )
                        }
                    onPosition(
                        JSONObject().apply {
                            put("coords", coords)
                            put("timestamp", location.time)
                            put("ageMs", (now - fixAt) / 1_000_000.0)
                        }
                    )
                }

                override fun onProviderDisabled(provider: String) {
                    if (listener === this) {
                        onError(2, "GPS is disabled")
                    }
                }

                override fun onProviderEnabled(provider: String) = Unit

                @Suppress("OVERRIDE_DEPRECATION")
                override fun onStatusChanged(
                    provider: String?,
                    status: Int,
                    extras: Bundle?,
                ) = Unit
            }
        listener = request
        try {
            if (LocationManager.GPS_PROVIDER !in manager.allProviders) {
                stop()
                onError(2, "This device has no GPS provider")
                return
            }
            // Explicitly name GPS so the legacy Criteria overload cannot select
            // fused.
            manager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1_000L,
                0f,
                request,
                Looper.getMainLooper(),
            )
            if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                onError(2, "GPS is disabled")
            }
        } catch (_: SecurityException) {
            stop()
            onError(1, "Precise location permission is unavailable")
        } catch (_: IllegalArgumentException) {
            stop()
            onError(2, "This device has no GPS provider")
        } catch (error: RuntimeException) {
            stop()
            Log.w(TAG, "Unable to start Direct GPS", error)
            onError(2, "Android GPS provider is unavailable")
        }
    }

    // Cleanup must still be attempted after Android revokes location permission.
    @SuppressLint("MissingPermission")
    fun stop() {
        val previous = listener ?: return
        listener = null
        try {
            manager?.removeUpdates(previous)
        } catch (error: RuntimeException) {
            // Queued callbacks are rejected by listener identity.
            Log.w(TAG, "Unable to remove Direct GPS listener", error)
        }
    }

    private fun nullable(present: Boolean, value: Double): Any =
        if (present && value.isFinite()) {
            value
        } else {
            JSONObject.NULL
        }

    companion object {
        private const val TAG = "NativeGpsLocationSource"
    }
}
