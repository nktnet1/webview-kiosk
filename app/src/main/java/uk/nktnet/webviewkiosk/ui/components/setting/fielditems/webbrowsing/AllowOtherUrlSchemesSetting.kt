package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webbrowsing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun AllowOtherUrlSchemesSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    BooleanSettingFieldItem(
        label = "Allow Other URL Schemes",
        infoText = "Allow opening of non-http/https URL schemes such as 'mailto:', 'sms:', 'tel:', 'intent:', 'spotify:', 'whatsapp:', etc in other apps.\n\nNOTE: This only works when in unlocked/unpinned mode.",
        initialValue = userSettings.allowOtherUrlSchemes,
        onSave = { userSettings.allowOtherUrlSchemes = it }
    )
}
