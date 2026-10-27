package uk.nktnet.webviewkiosk.utils

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.view.View
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
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.option.ImmersiveModeOption

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 29])
@Suppress("DEPRECATION")
class ImmersiveModeUtilsTest {
    private lateinit var controller: ActivityController<Activity>
    private lateinit var activity: Activity
    private val hiddenFlags =
        View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    private val layoutFlags =
        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
    private val unrelatedFlags =
        View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
            View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE

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
    fun legacyEnterStillPreservesAppearanceAndLayoutFlags() {
        activity.window.decorView.systemUiVisibility = unrelatedFlags
        enterImmersiveMode(activity)
        assertEquals(
            unrelatedFlags or layoutFlags or hiddenFlags,
            activity.window.decorView.systemUiVisibility,
        )
        enterImmersiveMode(activity)
        assertEquals(
            unrelatedFlags or layoutFlags or hiddenFlags,
            activity.window.decorView.systemUiVisibility,
        )
    }

    @Test
    fun legacyExitStillRemovesOnlyImmersiveAndFullscreenLayoutFlags() {
        activity.window.decorView.systemUiVisibility = unrelatedFlags
        enterImmersiveMode(activity)
        exitImmersiveMode(activity)
        assertEquals(unrelatedFlags, activity.window.decorView.systemUiVisibility)
    }

    @Test
    fun alwaysOnDoesNotRequireLockTaskOrDeviceOwner() {
        val settings = UserSettings(activity)
        val manager =
            activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        shadowOf(manager).setLockTaskModeState(ActivityManager.LOCK_TASK_MODE_NONE)
        settings.immersiveMode = ImmersiveModeOption.ALWAYS_ON
        assertTrue(shouldBeImmersed(activity, settings))
        settings.immersiveMode = ImmersiveModeOption.ALWAYS_OFF
        assertFalse(shouldBeImmersed(activity, settings))
    }

    @Test
    fun onlyWhenLockedTracksLockTaskNotTheDeviceKeyguard() {
        val settings = UserSettings(activity)
        settings.immersiveMode = ImmersiveModeOption.ONLY_WHEN_LOCKED
        val manager =
            activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        shadowOf(manager).setLockTaskModeState(ActivityManager.LOCK_TASK_MODE_NONE)
        assertFalse(shouldBeImmersed(activity, settings))
        shadowOf(manager).setLockTaskModeState(ActivityManager.LOCK_TASK_MODE_LOCKED)
        assertTrue(shouldBeImmersed(activity, settings))
        shadowOf(manager).setLockTaskModeState(ActivityManager.LOCK_TASK_MODE_PINNED)
        assertTrue(shouldBeImmersed(activity, settings))
        settings.immersiveMode = ImmersiveModeOption.ALWAYS_OFF
        assertFalse(shouldBeImmersed(activity, settings))
    }
}
