package uk.nktnet.webviewkiosk.diagnostics

import android.Manifest
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.location.LocationManager
import android.os.Build
import android.widget.Button
import android.widget.TextView
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.Q])
@LooperMode(LooperMode.Mode.PAUSED)
class LocationDiagnosticsActivityTest {
    private lateinit var controller: ActivityController<LocationDiagnosticsActivity>
    private lateinit var activity: LocationDiagnosticsActivity

    @Before
    fun setUp() {
        controller =
            Robolectric.buildActivity(LocationDiagnosticsActivity::class.java)
                .setup()
        activity = controller.get()
        UserSettings(activity).allowLocation = true
        shadowOf(activity.application)
            .grantPermissions(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
        shadowOf(manager()).setProviderEnabled(LocationManager.GPS_PROVIDER, true)
    }

    @After
    fun tearDown() {
        controller.pause().stop().destroy()
    }

    @Test
    fun diagnosticsArePrivateAndDoNotRequestLocationUntilStarted() {
        val info =
            activity.packageManager.getActivityInfo(
                ComponentName(activity, LocationDiagnosticsActivity::class.java),
                0,
            )
        assertFalse(info.exported)
        assertTrue(listeners().isEmpty())
        assertTrue(report().contains("Package: ${activity.packageName}"))
        assertTrue(report().contains("Android: 29"))
    }

    @Test
    fun pausingTheScreenCancelsRequestsAndCopyIncludesTheResult() {
        activity.findViewById<Button>(R.id.location_diagnostics_gps).performClick()
        assertEquals(1, listeners().size)
        controller.pause()
        assertTrue(listeners().isEmpty())
        assertTrue(report().contains("CANCELLED:"))
        controller.resume()
        activity.findViewById<Button>(R.id.location_diagnostics_copy).performClick()
        val clipboard =
            activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        assertEquals(report(), clipboard.primaryClip!!.getItemAt(0).text.toString())
    }

    @Suppress("DEPRECATION")
    private fun listeners() = shadowOf(manager()).getRequestLocationUpdateListeners()

    private fun manager() =
        activity.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private fun report() =
        activity
            .findViewById<TextView>(R.id.location_diagnostics_report)
            .text
            .toString()
}
