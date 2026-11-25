package uk.nktnet.webviewkiosk.config

import android.content.Context
import android.content.RestrictionsManager
import android.os.Bundle
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LockTaskHardeningSettingsTest {
    private lateinit var context: Context
    private lateinit var settings: UserSettings
    private lateinit var restrictions: RestrictionsManager
    private val relock = UserSettingsKeys.WebLifecycle.RELOCK_ON_UNEXPECTED_EXIT
    private val statusBar =
        UserSettingsKeys.Device.Owner.DISABLE_STATUS_BAR_OUTSIDE_LOCK_TASK

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context
            .getSharedPreferences(UserSettingsKeys.PREFS_NAME, 0)
            .edit()
            .clear()
            .commit()
        restrictions =
            context.getSystemService(Context.RESTRICTIONS_SERVICE)
                as RestrictionsManager
        shadowOf(restrictions).setApplicationRestrictions(Bundle())
        settings = UserSettings(context)
    }

    @Test
    fun bothPreferencesDefaultOffAndRoundTrip() {
        assertFalse(settings.relockOnUnexpectedExit)
        assertFalse(settings.disableStatusBarOutsideLockTask)
        assertFalse(settings.exportJson().getBoolean(relock))
        assertFalse(settings.exportJson().getBoolean(statusBar))
        settings.relockOnUnexpectedExit = true
        settings.disableStatusBarOutsideLockTask = true
        val exported = settings.exportJson().toString()
        settings.relockOnUnexpectedExit = false
        settings.disableStatusBarOutsideLockTask = false
        assertTrue(settings.importJson(exported))
        assertTrue(settings.relockOnUnexpectedExit)
        assertTrue(settings.disableStatusBarOutsideLockTask)
        assertTrue(UserSettings(context).relockOnUnexpectedExit)
        assertTrue(UserSettings(context).disableStatusBarOutsideLockTask)
    }

    @Test
    fun olderImportsKeepCurrentValues() {
        settings.relockOnUnexpectedExit = true
        settings.disableStatusBarOutsideLockTask = true
        assertTrue(settings.importJson("{}"))
        assertTrue(settings.relockOnUnexpectedExit)
        assertTrue(settings.disableStatusBarOutsideLockTask)
    }

    @Test
    fun managedRestrictionsOverrideLocalPreferences() {
        settings.relockOnUnexpectedExit = true
        settings.disableStatusBarOutsideLockTask = true
        shadowOf(restrictions)
            .setApplicationRestrictions(
                Bundle().apply {
                    putBoolean(relock, false)
                    putBoolean(statusBar, false)
                }
            )
        assertFalse(settings.relockOnUnexpectedExit)
        assertFalse(settings.disableStatusBarOutsideLockTask)
        assertTrue(
            settings.importJson(
                JSONObject().put(relock, true).put(statusBar, true).toString()
            )
        )
        assertFalse(settings.exportJson().getBoolean(relock))
        assertFalse(settings.exportJson().getBoolean(statusBar))
    }
}
