package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webengine

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.config.option.OverScrollModeOption
import uk.nktnet.webviewkiosk.ui.components.setting.fields.DropdownSettingFieldItem

@Composable
fun OverScrollModeSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    DropdownSettingFieldItem(
        label = stringResource(id = R.string.web_engine_over_scroll_mode_title),
        infoText = """
            Configures the WebView's overscroll behavior.

            Options:
            - Always: Shows the overscroll effect whenever the content is scrolled.
            - If Content Scrolls: allow over-scrolling only if the view content is larger than the container
            - Never: Disables the overscroll effect entirely.
        """.trimIndent(),
        options = OverScrollModeOption.entries,
        initialValue = userSettings.overScrollMode,
        restricted = userSettings.isRestricted(UserSettingsKeys.WebEngine.OVER_SCROLL_MODE),
        onSave = { userSettings.overScrollMode = it },
        itemText = { it.label },
    )
}
