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
class BackgroundMediaPlaybackSettingsTest {
    private lateinit var context: Context
    private lateinit var settings: UserSettings
    private lateinit var restrictions: RestrictionsManager
    private val key = UserSettingsKeys.WebEngine.BACKGROUND_MEDIA_PLAYBACK

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
    fun defaultsFalseAndPersistsAndRoundTrips() {
        assertFalse(settings.backgroundMediaPlayback)
        assertFalse(settings.exportJson().getBoolean(key))
        settings.backgroundMediaPlayback = true
        assertTrue(UserSettings(context).backgroundMediaPlayback)
        val exported = settings.exportJson().toString()
        settings.backgroundMediaPlayback = false
        assertTrue(settings.importJson(exported))
        assertTrue(settings.backgroundMediaPlayback)
        assertTrue(settings.importJson(JSONObject().put(key, false).toString()))
        assertFalse(UserSettings(context).backgroundMediaPlayback)
    }

    @Test
    fun olderImportsPreserveCurrentValue() {
        assertTrue(settings.importJson("{}"))
        assertFalse(settings.backgroundMediaPlayback)
        settings.backgroundMediaPlayback = true
        assertTrue(settings.importJson("{}"))
        assertTrue(settings.backgroundMediaPlayback)
    }

    @Test
    fun managedValuesOverridePreferencesAndCannotBeChangedLocally() {
        settings.backgroundMediaPlayback = true
        shadowOf(restrictions)
            .setApplicationRestrictions(Bundle().apply { putBoolean(key, false) })
        assertTrue(settings.isRestricted(key))
        assertFalse(settings.backgroundMediaPlayback)
        settings.backgroundMediaPlayback = true
        assertTrue(settings.importJson(JSONObject().put(key, true).toString()))
        assertFalse(settings.exportJson().getBoolean(key))
        shadowOf(restrictions).setApplicationRestrictions(Bundle())
        assertFalse(settings.isRestricted(key))
        assertTrue(settings.backgroundMediaPlayback)
    }
}
