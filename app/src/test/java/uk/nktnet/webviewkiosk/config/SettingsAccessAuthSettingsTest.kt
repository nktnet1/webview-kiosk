@file:Suppress("UsePropertyAccessSyntax")

package uk.nktnet.webviewkiosk.config

import android.content.Context
import android.content.RestrictionsManager
import android.os.Bundle
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
import uk.nktnet.webviewkiosk.config.option.SettingsAuthRequirementOption

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SettingsAccessAuthSettingsTest {
    private lateinit var context: Context
    private lateinit var settings: UserSettings
    private lateinit var restrictions: RestrictionsManager
    private val key = UserSettingsKeys.Device.SETTINGS_AUTH_REQUIREMENT

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
        assertEquals("device.settings_auth_requirement", key)
        assertEquals(
            SettingsAuthRequirementOption.REQUIRE,
            settings.settingsAuthRequirement,
        )
        assertFalse(settings.exportJson().has(key))

        settings.settingsAuthRequirement = SettingsAuthRequirementOption.OFF
        assertEquals(
            "OFF",
            context
                .getSharedPreferences(
                    UserSettingsKeys.PREFS_NAME,
                    Context.MODE_PRIVATE,
                )
                .getString(key, null),
        )
        assertEquals(
            SettingsAuthRequirementOption.OFF,
            UserSettings(context).settingsAuthRequirement,
        )
        assertFalse(settings.exportJson().has(key))

        settings.settingsAuthRequirement = SettingsAuthRequirementOption.REQUIRE
        assertEquals(
            "REQUIRE",
            context
                .getSharedPreferences(
                    UserSettingsKeys.PREFS_NAME,
                    Context.MODE_PRIVATE,
                )
                .getString(key, null),
        )
        assertEquals(
            SettingsAuthRequirementOption.REQUIRE,
            UserSettings(context).settingsAuthRequirement,
        )
    }

    @Test
    fun importsCannotChangeSettingsAuthenticationRequirement() {
        assertTrue(settings.importJson(JSONObject().put(key, "OFF").toString()))
        assertEquals(
            SettingsAuthRequirementOption.REQUIRE,
            settings.settingsAuthRequirement,
        )

        settings.settingsAuthRequirement = SettingsAuthRequirementOption.OFF
        assertTrue(settings.importJson(JSONObject().put(key, "REQUIRE").toString()))
        assertEquals(
            SettingsAuthRequirementOption.OFF,
            settings.settingsAuthRequirement,
        )
    }

    @Test
    fun invalidManagedValueCannotSilentlyDisableSettingsAuthentication() {
        settings.settingsAuthRequirement = SettingsAuthRequirementOption.OFF
        shadowOf(restrictions)
            .setApplicationRestrictions(Bundle().apply { putString(key, "false") })
        assertTrue(settings.isRestricted(key))
        assertEquals(
            SettingsAuthRequirementOption.REQUIRE,
            settings.settingsAuthRequirement,
        )
    }

    @Test
    fun managedChoiceOffAndRequireOverrideLocalEnumWithoutOverwritingIt() {
        settings.settingsAuthRequirement = SettingsAuthRequirementOption.REQUIRE
        shadowOf(restrictions)
            .setApplicationRestrictions(Bundle().apply { putString(key, "OFF") })
        assertTrue(settings.isRestricted(key))
        assertEquals(
            SettingsAuthRequirementOption.OFF,
            settings.settingsAuthRequirement,
        )

        // Managed settings are read-only, including while a policy is active.
        settings.settingsAuthRequirement = SettingsAuthRequirementOption.OFF
        shadowOf(restrictions)
            .setApplicationRestrictions(Bundle().apply { putString(key, "REQUIRE") })
        assertEquals(
            SettingsAuthRequirementOption.REQUIRE,
            settings.settingsAuthRequirement,
        )

        shadowOf(restrictions).setApplicationRestrictions(Bundle())
        assertEquals(
            SettingsAuthRequirementOption.REQUIRE,
            settings.settingsAuthRequirement,
        )
    }

    @Test
    fun invalidManagedChoiceFailsClosedToRequire() {
        settings.settingsAuthRequirement = SettingsAuthRequirementOption.OFF
        for (invalidValue in listOf("", "off", "false", "DEFAULT", "UNKNOWN")) {
            shadowOf(restrictions)
                .setApplicationRestrictions(
                    Bundle().apply { putString(key, invalidValue) }
                )
            assertEquals(
                "Value $invalidValue must not disable access auth",
                SettingsAuthRequirementOption.REQUIRE,
                settings.settingsAuthRequirement,
            )
        }
        // Boolean restrictions are not supported: the MDM contract is a choice.
        for (value in listOf(false, true)) {
            shadowOf(restrictions)
                .setApplicationRestrictions(
                    Bundle().apply { putBoolean(key, value) }
                )
            assertEquals(
                SettingsAuthRequirementOption.REQUIRE,
                settings.settingsAuthRequirement,
            )
        }
        shadowOf(restrictions)
            .setApplicationRestrictions(Bundle().apply { putInt(key, 0) })
        assertEquals(
            SettingsAuthRequirementOption.REQUIRE,
            settings.settingsAuthRequirement,
        )
        shadowOf(restrictions).setApplicationRestrictions(Bundle())
        assertEquals(
            SettingsAuthRequirementOption.OFF,
            settings.settingsAuthRequirement,
        )
    }

    @Test
    fun managedRestrictionsOverrideButDoNotOverwriteLocalPreference() {
        settings.settingsAuthRequirement = SettingsAuthRequirementOption.REQUIRE
        shadowOf(restrictions)
            .setApplicationRestrictions(Bundle().apply { putString(key, "OFF") })
        assertTrue(settings.isRestricted(key))
        assertEquals(
            SettingsAuthRequirementOption.OFF,
            settings.settingsAuthRequirement,
        )
        settings.settingsAuthRequirement = SettingsAuthRequirementOption.OFF
        assertTrue(settings.importJson(JSONObject().put(key, "REQUIRE").toString()))
        assertEquals(
            SettingsAuthRequirementOption.OFF,
            settings.settingsAuthRequirement,
        )

        shadowOf(restrictions).setApplicationRestrictions(Bundle())
        assertEquals(
            SettingsAuthRequirementOption.REQUIRE,
            settings.settingsAuthRequirement,
        )
    }
}
