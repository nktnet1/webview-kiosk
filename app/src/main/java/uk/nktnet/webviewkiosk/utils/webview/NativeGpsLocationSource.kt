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
import android.os.Handler
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
    private class Registration(
        val manager: LocationManager,
        val listener: LocationListener,
    ) {
        var unavailable: String? = null
    }

    private val main = Handler(Looper.getMainLooper())
    private val retry = Runnable { register(logFailure = false) }
    private var registration: Registration? = null

    fun start(reportFailure: Boolean = true): String? {
        registration?.let {
            return it.unavailable
        }
        main.removeCallbacks(retry)
        val unavailable = register(logFailure = reportFailure)
        if (reportFailure && unavailable != null) {
            onError(2, unavailable)
        }
        return unavailable
    }

    private fun register(logFailure: Boolean): String? {
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
            return null
        }
        val manager = runCatching {
            context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        }
            .onFailure {
                if (logFailure) {
                    Log.w(TAG, "Unable to get Android location service", it)
                }
            }
            .getOrNull()
        if (manager == null) {
            return retryAfterFailure("Android location service is unavailable")
        }
        val startedAt = SystemClock.elapsedRealtimeNanos()
        var lastFixAt = startedAt - 1
        val request =
            object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (registration?.listener !== this) {
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
                    val current = registration ?: return
                    if (current.listener === this && current.unavailable == null) {
                        current.unavailable = GPS_DISABLED
                        onError(2, GPS_DISABLED)
                    }
                }

                override fun onProviderEnabled(provider: String) {
                    registration?.takeIf { it.listener === this }?.unavailable = null
                }

                @Suppress("OVERRIDE_DEPRECATION")
                override fun onStatusChanged(
                    provider: String?,
                    status: Int,
                    extras: Bundle?,
                ) = Unit
            }
        return try {
            if (LocationManager.GPS_PROVIDER !in manager.allProviders) {
                return retryAfterFailure("This device has no GPS provider")
            }
            val current = Registration(manager, request)
            registration = current
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
                current.unavailable = GPS_DISABLED
            }
            current.unavailable
        } catch (_: SecurityException) {
            stop()
            onError(1, "Precise location permission is unavailable")
            null
        } catch (_: IllegalArgumentException) {
            stop()
            retryAfterFailure("This device has no GPS provider")
        } catch (error: RuntimeException) {
            stop()
            if (logFailure) {
                Log.w(TAG, "Unable to start Direct GPS", error)
            }
            retryAfterFailure("Android GPS provider is unavailable")
        }
    }

    private fun retryAfterFailure(message: String): String {
        main.postDelayed(retry, RETRY_DELAY_MS)
        return message
    }

    // Cleanup must still be attempted after Android revokes location permission.
    @SuppressLint("MissingPermission")
    fun stop() {
        main.removeCallbacks(retry)
        val previous = registration ?: return
        registration = null
        try {
            previous.manager.removeUpdates(previous.listener)
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
        private const val GPS_DISABLED = "GPS is disabled"
        private const val RETRY_DELAY_MS = 5_000L
    }
}
