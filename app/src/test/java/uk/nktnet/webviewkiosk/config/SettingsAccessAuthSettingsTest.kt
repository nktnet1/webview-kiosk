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
class SettingsAccessAuthSettingsTest {
    private lateinit var context: Context
    private lateinit var settings: UserSettings
    private lateinit var restrictions: RestrictionsManager
    private val key = UserSettingsKeys.Device.REQUIRE_AUTH_FOR_SETTINGS

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
    fun defaultsOnAndPersistsBothValues() {
        assertTrue(settings.requireAuthForSettings)
        assertFalse(settings.exportJson().has(key))

        settings.requireAuthForSettings = false
        assertFalse(UserSettings(context).requireAuthForSettings)
        assertFalse(settings.exportJson().has(key))

        settings.requireAuthForSettings = true
        assertTrue(UserSettings(context).requireAuthForSettings)
    }

    @Test
    fun importsCannotChangeSettingsAuthenticationRequirement() {
        assertTrue(settings.importJson(JSONObject().put(key, false).toString()))
        assertTrue(settings.requireAuthForSettings)

        settings.requireAuthForSettings = false
        assertTrue(settings.importJson(JSONObject().put(key, true).toString()))
        assertFalse(settings.requireAuthForSettings)
    }

    @Test
    fun managedRestrictionsOverrideButDoNotOverwriteLocalPreference() {
        settings.requireAuthForSettings = true
        shadowOf(restrictions)
            .setApplicationRestrictions(Bundle().apply { putBoolean(key, false) })
        assertTrue(settings.isRestricted(key))
        assertFalse(settings.requireAuthForSettings)
        settings.requireAuthForSettings = true
        assertTrue(settings.importJson(JSONObject().put(key, true).toString()))
        assertFalse(settings.requireAuthForSettings)

        shadowOf(restrictions).setApplicationRestrictions(Bundle())
        assertTrue(settings.requireAuthForSettings)
    }
}
