package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webengine

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.config.option.MixedContentModeOption
import uk.nktnet.webviewkiosk.ui.components.setting.fields.DropdownSettingFieldItem

@Composable
fun MixedContentModeSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    DropdownSettingFieldItem(
        label = "Mixed Content Mode",
        infoText = """
            Configures the WebView's behavior when a secure origin attempts to load
            a resource from an insecure origin.

            The preferred and most secure mode of operation is "Never Allow".

            The use of "Always Allow" is strongly discouraged and could compromise
            your security. With "Compatibility Mode", use it with caution.
        """.trimIndent(),
        options = MixedContentModeOption.entries,
        initialValue = userSettings.mixedContentMode,
        restricted = userSettings.isRestricted(UserSettingsKeys.WebEngine.MIXED_CONTENT_MODE),
        onSave = { userSettings.mixedContentMode = it },
        itemText = { it.label },
    )
}
