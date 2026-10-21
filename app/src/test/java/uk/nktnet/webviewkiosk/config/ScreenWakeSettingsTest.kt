@file:Suppress("UsePropertyAccessSyntax")

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
class ScreenWakeSettingsTest {
    private lateinit var context: Context
    private lateinit var settings: UserSettings
    private lateinit var restrictions: RestrictionsManager
    private val key = UserSettingsKeys.WebLifecycle.REFRESH_ON_SCREEN_WAKE

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
    fun defaultsOffAndBothValuesPersistAndRoundTripJson() {
        assertFalse(settings.refreshOnScreenWake)
        assertFalse(settings.exportJson().getBoolean(key))
        settings.refreshOnScreenWake = true
        assertTrue(UserSettings(context).refreshOnScreenWake)
        val exported = settings.exportJson().toString()
        settings.refreshOnScreenWake = false
        assertTrue(settings.importJson(exported))
        assertTrue(settings.refreshOnScreenWake)
        assertTrue(settings.importJson(JSONObject().put(key, false).toString()))
        assertFalse(UserSettings(context).refreshOnScreenWake)
    }

    @Test
    fun olderImportsPreserveTheCurrentWakePreference() {
        assertTrue(settings.importJson("{}"))
        assertFalse(settings.refreshOnScreenWake)
        settings.refreshOnScreenWake = true
        assertTrue(settings.importJson("{}"))
        assertTrue(settings.refreshOnScreenWake)
    }

    @Test
    fun managedValuesOverrideLocalSettingsAndRejectImportsAndWrites() {
        settings.refreshOnScreenWake = true
        shadowOf(restrictions)
            .setApplicationRestrictions(Bundle().apply { putBoolean(key, false) })
        assertTrue(settings.isRestricted(key))
        assertFalse(settings.refreshOnScreenWake)
        settings.refreshOnScreenWake = true
        assertTrue(settings.importJson(JSONObject().put(key, true).toString()))
        assertFalse(settings.exportJson().getBoolean(key))
        shadowOf(restrictions).setApplicationRestrictions(Bundle())
        assertFalse(settings.isRestricted(key))
        assertTrue(settings.refreshOnScreenWake)
    }
}
