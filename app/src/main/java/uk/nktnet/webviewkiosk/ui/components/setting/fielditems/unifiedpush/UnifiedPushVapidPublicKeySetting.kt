package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.unifiedpush

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.TextSettingFieldItem
import uk.nktnet.webviewkiosk.utils.isValidVapidPublicKey

@Composable
fun UnifiedPushVapidPublicKeySetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.UnifiedPush.MESSAGE_FOR_DISTRIBUTOR
    val restricted = userSettings.isRestricted(settingKey)

    TextSettingFieldItem(
        label = stringResource(R.string.unifiedpush_vapid_public_key_title),
        infoText = """
            VAPID public key (RFC8292) base64url encoded of the uncompressed form (87 chars long).
        """.trimIndent(),
        placeholder = "",
        validator = {
            it.isEmpty() || isValidVapidPublicKey(it)
        },
        validationMessage = "Invalid VAPID public key.",
        initialValue = userSettings.unifiedPushVapidPublicKey,
        settingKey = settingKey,
        restricted = restricted,
        isMultiline = false,
        onSave = {
            userSettings.unifiedPushVapidPublicKey = it
        },
    )
}
