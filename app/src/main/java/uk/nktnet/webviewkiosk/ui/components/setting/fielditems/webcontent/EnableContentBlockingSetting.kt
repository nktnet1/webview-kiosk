package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webcontent

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun EnableContentBlockingSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.WebContent.ENABLE_CONTENT_BLOCKING

    BooleanSettingFieldItem(
        label = stringResource(R.string.web_content_enable_content_blocking_title),
        infoText =
            """
            Block ads and trackers using the enabled lists in Content Blocking.

            Disabling keeps your lists and their individual enabled states.

            To add lists, open Content Blocking and select Add list. Choose a
            preset, enter a URL, or use Custom to paste rules or import a file.
            """
                .trimIndent(),
        initialValue = userSettings.enableContentBlocking,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.enableContentBlocking = it },
    )
}
