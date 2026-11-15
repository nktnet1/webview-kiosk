@file:Suppress("UsePropertyAccessSyntax")

package uk.nktnet.webviewkiosk.config

import android.app.KeyguardManager
import android.content.Context
import android.content.RestrictionsManager
import android.os.Bundle
import android.util.Base64
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
    fun optionParsingAcceptsNamesAndLabelsCaseInsensitively() {
        for (value in listOf("OFF", "Off", "off", "oFf")) {
            assertEquals(
                SettingsAuthRequirementOption.OFF,
                SettingsAuthRequirementOption.fromString(value),
            )
        }
        for (value in listOf("REQUIRE", "Require", "require", "rEqUiRe")) {
            assertEquals(
                SettingsAuthRequirementOption.REQUIRE,
                SettingsAuthRequirementOption.fromString(value),
            )
        }
        for (value in listOf(null, "", "DEFAULT", "false", "true", "UNKNOWN")) {
            assertEquals(
                SettingsAuthRequirementOption.REQUIRE,
                SettingsAuthRequirementOption.fromString(value),
            )
        }
    }

    @Test
    fun defaultsOnAndPersistsBothValues() {
        assertEquals("device.settings_auth_requirement", key)
        assertEquals(
            SettingsAuthRequirementOption.REQUIRE,
            settings.settingsAuthRequirement,
        )
        assertEquals("REQUIRE", settings.exportJson().getString(key))

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
        assertEquals("OFF", settings.exportJson().getString(key))

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
    fun directImportsAllowRemoteUpdatesInBothDirections() {
        assertTrue(settings.importJson(JSONObject().put(key, "OFF").toString()))
        assertEquals(
            SettingsAuthRequirementOption.OFF,
            settings.settingsAuthRequirement,
        )
        assertTrue(settings.importJson(JSONObject().put(key, "REQUIRE").toString()))
        assertEquals(
            SettingsAuthRequirementOption.REQUIRE,
            settings.settingsAuthRequirement,
        )
        assertTrue(
            settings.importBase64(
                Base64.encodeToString(
                    JSONObject().put(key, "Off").toString().toByteArray(),
                    Base64.NO_WRAP,
                )
            )
        )
        assertEquals(
            SettingsAuthRequirementOption.OFF,
            settings.settingsAuthRequirement,
        )
    }

    @Test
    fun manualImportRequiresVerificationOnlyWhenDisablingProtection() {
        val off = JSONObject().put(key, "OFF").toString()
        val require = JSONObject().put(key, "REQUIRE").toString()
        val offBase64 = Base64.encodeToString(off.toByteArray(), Base64.NO_WRAP)
        val requireBase64 =
            Base64.encodeToString(require.toByteArray(), Base64.NO_WRAP)

        assertTrue(settings.manualImportNeedsSettingsReauthentication(off))
        assertTrue(
            settings.manualImportNeedsSettingsReauthentication(offBase64, true)
        )
        assertFalse(settings.manualImportNeedsSettingsReauthentication(require))
        assertFalse(settings.manualImportNeedsSettingsReauthentication("{}"))
        assertFalse(settings.manualImportNeedsSettingsReauthentication("not json"))
        assertFalse(
            settings.manualImportNeedsSettingsReauthentication("not base64", true)
        )
        assertFalse(settings.manualImportNeedsSettingsReauthentication(""))
        assertFalse(
            settings.manualImportNeedsSettingsReauthentication(
                JSONObject().put(key, "false").toString()
            )
        )

        settings.settingsAuthRequirement = SettingsAuthRequirementOption.OFF
        assertFalse(settings.manualImportNeedsSettingsReauthentication(off))
        assertFalse(settings.manualImportNeedsSettingsReauthentication(require))
        assertFalse(
            settings.manualImportNeedsSettingsReauthentication(requireBase64, true)
        )
        assertFalse(
            settings.manualImportNeedsSettingsReauthentication(offBase64, true)
        )
    }

    @Test
    fun manualImportsCannotReplaceOrRemoveTheCurrentCustomPasswordWithoutVerification() {
        val passwordKey = UserSettingsKeys.Device.CUSTOM_AUTH_PASSWORD
        settings.customAuthPassword = "existing password"

        val changed = JSONObject().put(passwordKey, "replacement").toString()
        val removed = JSONObject().put(passwordKey, "").toString()
        val unchanged = JSONObject().put(passwordKey, "existing password").toString()

        assertTrue(settings.manualImportNeedsSettingsReauthentication(changed))
        assertTrue(settings.manualImportNeedsSettingsReauthentication(removed))
        assertTrue(
            settings.manualImportNeedsSettingsReauthentication(
                Base64.encodeToString(changed.toByteArray(), Base64.NO_WRAP),
                isBase64 = true,
            )
        )
        assertFalse(settings.manualImportNeedsSettingsReauthentication(unchanged))
        assertFalse(settings.manualImportNeedsSettingsReauthentication("{}"))

        settings.settingsAuthRequirement = SettingsAuthRequirementOption.OFF
        assertFalse(settings.manualImportNeedsSettingsReauthentication(removed))
    }

    @Test
    fun replacingDeviceAuthenticationAlsoRequiresFreshVerification() {
        val keyguard =
            context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val addPassword =
            JSONObject()
                .put(UserSettingsKeys.Device.CUSTOM_AUTH_PASSWORD, "new password")
                .toString()

        shadowOf(keyguard).setIsDeviceSecure(true)
        assertTrue(settings.manualImportNeedsSettingsReauthentication(addPassword))
        assertTrue(
            settings.requiresReauthenticationToChangeCustomAuthPassword(
                "new password"
            )
        )

        // Bootstrapping security on an unprotected device cannot authenticate
        // against credentials that do not yet exist.
        shadowOf(keyguard).setIsDeviceSecure(false)
        assertFalse(settings.manualImportNeedsSettingsReauthentication(addPassword))
        assertFalse(
            settings.requiresReauthenticationToChangeCustomAuthPassword(
                "new password"
            )
        )
    }

    @Test
    fun managedSettingsRequireKeepsCredentialChangesGuarded() {
        val passwordKey = UserSettingsKeys.Device.CUSTOM_AUTH_PASSWORD
        settings.customAuthPassword = "original"
        shadowOf(restrictions)
            .setApplicationRestrictions(Bundle().apply { putString(key, "REQUIRE") })

        assertTrue(
            settings.manualImportNeedsSettingsReauthentication(
                JSONObject().put(passwordKey, "").toString()
            )
        )

        shadowOf(restrictions)
            .setApplicationRestrictions(
                Bundle().apply {
                    putString(key, "REQUIRE")
                    putString(passwordKey, "managed")
                }
            )
        assertFalse(
            settings.manualImportNeedsSettingsReauthentication(
                JSONObject().put(passwordKey, "").toString()
            )
        )
    }

    @Test
    fun managedSettingsNeverNeedManualImportVerification() {
        shadowOf(restrictions)
            .setApplicationRestrictions(Bundle().apply { putString(key, "REQUIRE") })
        assertFalse(
            settings.manualImportNeedsSettingsReauthentication(
                JSONObject().put(key, "OFF").toString()
            )
        )
        assertTrue(settings.importJson(JSONObject().put(key, "OFF").toString()))
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
    fun managedChoiceNamesAndLabelsAreCaseInsensitive() {
        settings.settingsAuthRequirement = SettingsAuthRequirementOption.REQUIRE
        for (value in listOf("OFF", "Off", "off", "oFf")) {
            shadowOf(restrictions)
                .setApplicationRestrictions(Bundle().apply { putString(key, value) })
            assertEquals(
                SettingsAuthRequirementOption.OFF,
                settings.settingsAuthRequirement,
            )
        }
    }

    @Test
    fun invalidManagedChoiceFailsClosedToRequire() {
        settings.settingsAuthRequirement = SettingsAuthRequirementOption.OFF
        for (invalidValue in listOf("", "false", "DEFAULT", "UNKNOWN")) {
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
