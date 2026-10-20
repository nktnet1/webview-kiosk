@file:Suppress("UsePropertyAccessSyntax")

package uk.nktnet.webviewkiosk.config

import android.content.Context
import android.content.RestrictionsManager
import android.os.Bundle
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import uk.nktnet.webviewkiosk.config.option.ResetActionOption

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ResetActionsSettingsTest {
    private lateinit var context: Context
    private lateinit var settings: UserSettings
    private lateinit var restrictions: RestrictionsManager
    private val key = UserSettingsKeys.WebLifecycle.RESET_ACTIONS

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
    fun defaultsToHistoryOnly() {
        assertEquals(ResetActionOption.defaults(), settings.resetActions)
    }

    @Test
    fun emptySelectionIsStoredAndRoundTripsJson() {
        settings.resetActions = emptyList()
        assertEquals(
            emptyList<ResetActionOption>(),
            UserSettings(context).resetActions,
        )
        val exported = settings.exportJson()
        assertEquals(0, exported.getJSONArray(key).length())

        settings.resetActions = ResetActionOption.defaults()
        assertTrue(settings.importJson(exported.toString()))
        assertTrue(settings.resetActions.isEmpty())
    }

    @Test
    fun multipleSelectionsRoundTripAndUnknownValuesAreIgnored() {
        val selected =
            listOf(
                ResetActionOption.COOKIES,
                ResetActionOption.WEB_STORAGE,
                ResetActionOption.RECREATE_WEBVIEW,
            )
        settings.resetActions = selected
        assertEquals(selected, UserSettings(context).resetActions)
        val exported = settings.exportJson().toString()

        settings.resetActions = emptyList()
        assertTrue(settings.importJson(exported))
        assertEquals(selected, settings.resetActions)

        val input =
            JSONObject()
                .put(
                    key,
                    JSONArray(
                        listOf("CACHE", "UNKNOWN", "cache", "SSL_PREFERENCES")
                    ),
                )
        assertTrue(settings.importJson(input.toString()))
        assertEquals(
            listOf(ResetActionOption.CACHE, ResetActionOption.SSL_PREFERENCES),
            settings.resetActions,
        )
    }

    @Test
    fun reorderedActionsPersistAndRoundTripJsonInOrder() {
        settings.resetActions =
            listOf(ResetActionOption.HISTORY, ResetActionOption.CACHE)
        val reordered = listOf(ResetActionOption.CACHE, ResetActionOption.HISTORY)
        settings.resetActions = reordered

        assertEquals(reordered, UserSettings(context).resetActions)

        val exported = settings.exportJson()
        assertEquals("CACHE", exported.getJSONArray(key).getString(0))
        assertEquals("HISTORY", exported.getJSONArray(key).getString(1))

        settings.resetActions = emptyList()
        assertTrue(settings.importJson(exported.toString()))
        assertEquals(reordered, settings.resetActions)
    }

    @Test
    fun missingImportKeyDoesNotChangeExistingSelection() {
        settings.resetActions = listOf(ResetActionOption.FORM_DATA)
        assertTrue(settings.importJson("{}"))
        assertEquals(listOf(ResetActionOption.FORM_DATA), settings.resetActions)
    }

    @Test
    fun managedSelectionOverridesLocalAndRejectsWrites() {
        settings.resetActions = listOf(ResetActionOption.CACHE)
        shadowOf(restrictions)
            .setApplicationRestrictions(
                Bundle().apply { putStringArray(key, arrayOf("HISTORY", "COOKIES")) }
            )
        assertTrue(settings.isRestricted(key))
        assertEquals(
            listOf(ResetActionOption.HISTORY, ResetActionOption.COOKIES),
            settings.resetActions,
        )
        settings.resetActions = emptyList()
        assertTrue(
            settings.importJson(JSONObject().put(key, JSONArray()).toString())
        )
        assertEquals(2, settings.exportJson().getJSONArray(key).length())

        shadowOf(restrictions).setApplicationRestrictions(Bundle())
        assertFalse(settings.isRestricted(key))
        assertEquals(listOf(ResetActionOption.CACHE), settings.resetActions)
    }
}
