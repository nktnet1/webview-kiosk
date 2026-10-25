package uk.nktnet.webviewkiosk.utils

import android.app.Activity
import android.os.Looper
import android.view.View
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/** Tests the API 30 compat path, not SystemUI's transient surface animations. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
@LooperMode(LooperMode.Mode.PAUSED)
@Suppress("DEPRECATION") // AndroidX intentionally uses these flags on Android 11.
class ImmersiveModeAndroid11Test {
    private lateinit var controller: ActivityController<Activity>
    private lateinit var activity: Activity

    @Before
    fun setUp() {
        controller = Robolectric.buildActivity(Activity::class.java).setup()
        activity = controller.get()
    }

    @After
    fun tearDown() {
        controller.pause().stop().destroy()
    }

    @Test
    fun enteringInstallsTheWindowBackedStickyWorkaround() {
        enterImmersiveMode(activity)
        assertStickyPolicy()
    }

    @Test
    fun brightnessLayoutUpdatesDoNotDiscardTheStickyWorkaround() {
        enterImmersiveMode(activity)
        setWindowBrightness(activity, 25)
        shadowOf(Looper.getMainLooper()).idle()
        assertStickyPolicy()
        setWindowBrightness(activity, 80)
        shadowOf(Looper.getMainLooper()).idle()
        assertStickyPolicy()
    }

    @Test
    fun leavingImmersiveModeClearsTheCompatStickyPolicy() {
        enterImmersiveMode(activity)
        exitImmersiveMode(activity)
        assertEquals(
            0,
            activity.window.decorView.systemUiVisibility and
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY,
        )
        assertEquals(
            WindowInsetsControllerCompat.BEHAVIOR_DEFAULT,
            WindowCompat.getInsetsController(
                    activity.window,
                    activity.window.decorView,
                )
                .systemBarsBehavior,
        )
    }

    private fun assertStickyPolicy() {
        assertEquals(
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY,
            activity.window.decorView.systemUiVisibility and
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY,
        )
        assertEquals(
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE,
            WindowCompat.getInsetsController(
                    activity.window,
                    activity.window.decorView,
                )
                .systemBarsBehavior,
        )
    }
}
