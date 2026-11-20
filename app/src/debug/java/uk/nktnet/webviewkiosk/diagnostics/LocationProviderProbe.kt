package uk.nktnet.webviewkiosk.diagnostics

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Criteria
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import uk.nktnet.webviewkiosk.config.UserSettings

/**
 * Compare Android's Criteria request with explicit GPS without replacing WebView.
 */
@Suppress("DEPRECATION")
internal class LocationProviderProbe(
    private val context: Context,
    private val report: (String) -> Unit,
) {
    enum class Mode {
        WEBVIEW_CRITERIA,
        DIRECT_GPS,
    }

    private val manager =
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    private val handler = Handler(Looper.getMainLooper())
    private var listener: LocationListener? = null
    private var timeout: Runnable? = null

    fun start(mode: Mode) {
        stop()
        val fine = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        report(
            "${mode.name}: allowLocation=${UserSettings(context).allowLocation}, fine=$fine, coarse=$coarse"
        )
        if (!UserSettings(context).allowLocation || (!fine && !coarse)) {
            report(
                "BLOCKED: enable Allow Location and grant this app location permission first"
            )
            return
        }
        if (mode == Mode.DIRECT_GPS && !fine) {
            report("BLOCKED: direct GPS requires precise location permission")
            return
        }
        val service = manager
        if (service == null) {
            report("UNAVAILABLE: no Android location service")
            return
        }

        try {
            val providers = service.allProviders
            report(
                "Providers: ${providers.joinToString { "$it(enabled=${service.isProviderEnabled(it)})" }}"
            )
            if (
                mode == Mode.DIRECT_GPS && LocationManager.GPS_PROVIDER !in providers
            ) {
                report("UNAVAILABLE: no GPS provider")
                return
            }

            val startedNanos = SystemClock.elapsedRealtimeNanos()
            var reportedCachedFix = false
            val current =
                object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        if (listener !== this) {
                            return
                        }
                        // A last-known fix cannot establish that updates recovered.
                        val nowNanos = SystemClock.elapsedRealtimeNanos()
                        val fixNanos = location.elapsedRealtimeNanos
                        val fresh = fixNanos in startedNanos..nowNanos
                        if (fresh || !reportedCachedFix) {
                            val ageMs = (nowNanos - fixNanos) / 1_000_000
                            val label =
                                when {
                                    fresh -> "FRESH_FIX"
                                    fixNanos > nowNanos || fixNanos < 0 ->
                                        "INVALID_FIX"
                                    else -> "CACHED_FIX"
                                }
                            val accuracy =
                                if (location.hasAccuracy()) {
                                    location.accuracy.toString()
                                } else {
                                    "unknown"
                                }
                            report(
                                "$label: provider=${location.provider}, ageMs=$ageMs, accuracyM=$accuracy, hasSpeed=${location.hasSpeed()}, hasBearing=${location.hasBearing()}"
                            )
                            reportedCachedFix = true
                        }
                        if (fresh) {
                            finish("PASS: received a fresh native fix")
                        }
                    }

                    override fun onProviderEnabled(provider: String) {
                        if (listener === this) {
                            report("Provider enabled: $provider")
                        }
                    }

                    override fun onProviderDisabled(provider: String) {
                        if (listener === this) {
                            report("Provider disabled: $provider")
                        }
                    }

                    @Suppress("OVERRIDE_DEPRECATION")
                    override fun onStatusChanged(
                        provider: String?,
                        status: Int,
                        extras: Bundle?,
                    ) = Unit
                }
            listener = current
            timeout = Runnable {
                finish("TIMEOUT: no fresh native fix in 60 seconds")
            }
            handler.postDelayed(timeout!!, TIMEOUT_MS)
            report("START: waiting up to 60 seconds for a fresh fix")
            when (mode) {
                // Chromium's Android provider uses this Criteria overload.
                // Android routes Criteria requests through its fused provider.
                Mode.WEBVIEW_CRITERIA ->
                    service.requestLocationUpdates(
                        0L,
                        0f,
                        Criteria().apply {
                            if (fine) {
                                accuracy = Criteria.ACCURACY_FINE
                            }
                        },
                        current,
                        Looper.getMainLooper(),
                    )
                Mode.DIRECT_GPS ->
                    service.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        0L,
                        0f,
                        current,
                        Looper.getMainLooper(),
                    )
            }
        } catch (_: SecurityException) {
            finish("DENIED: Android rejected location access")
        } catch (_: IllegalArgumentException) {
            finish("UNAVAILABLE: Android rejected the provider request")
        } catch (_: IllegalStateException) {
            finish(
                "UNAVAILABLE: Android location service could not accept the request"
            )
        }
    }

    fun stop() {
        if (listener != null) {
            finish("CANCELLED: native probe stopped")
        }
    }

    // Attempt cleanup even after permission revocation, handling native denial
    // below.
    @SuppressLint("MissingPermission")
    private fun finish(message: String) {
        val previous = listener
        listener = null
        timeout?.let(handler::removeCallbacks)
        timeout = null
        if (previous != null) {
            try {
                manager?.removeUpdates(previous)
            } catch (_: SecurityException) {
                report("Cleanup denied by Android")
            } catch (_: IllegalArgumentException) {
                report("Provider already removed by Android")
            }
        }
        report(message)
    }

    private fun hasPermission(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED

    companion object {
        const val TIMEOUT_MS = 60_000L
    }
}
