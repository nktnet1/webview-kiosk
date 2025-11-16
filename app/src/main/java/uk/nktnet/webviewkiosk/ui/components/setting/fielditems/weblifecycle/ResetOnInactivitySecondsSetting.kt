package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.weblifecycle

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.Constants
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.NumberSettingFieldItem

@Composable
fun ResetOnInactivitySecondsSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    NumberSettingFieldItem(
        label = "Reset on Inactivity (seconds)",
        infoText = """
            Number of seconds of inactivity before the app resets to the home URL.

            When there is 5 seconds left, a warning countdown will be shown on
            the screen.

            User interactions the screen will reset the timer.

            The navigation history will be cleared when resetting.

            Minimum: ${Constants.MIN_INACTIVITY_TIMEOUT_SECONDS}

            To disable, use the value 0. 
        """.trimIndent(),
        placeholder = "e.g. 3600 (for 1 hour)",
        initialValue = userSettings.resetOnInactivitySeconds,
        restricted = userSettings.isRestricted(UserSettingsKeys.WebLifecycle.RESET_ON_INACTIVITY_SECONDS),
        min = Constants.MIN_INACTIVITY_TIMEOUT_SECONDS,
        onSave = { userSettings.resetOnInactivitySeconds = it }
    )
}
