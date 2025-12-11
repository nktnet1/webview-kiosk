package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webengine

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.config.option.CacheModeOption
import uk.nktnet.webviewkiosk.ui.components.setting.fields.DropdownSettingFieldItem

@Composable
fun CacheModeSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    DropdownSettingFieldItem(
        label = stringResource(id = R.string.web_engine_cache_mode_title),
        infoText = "Control how the WebView uses its cache when loading pages.",
        options = CacheModeOption.entries,
        initialValue = userSettings.cacheMode,
        restricted = userSettings.isRestricted(UserSettingsKeys.WebEngine.CACHE_MODE),
        onSave = { userSettings.cacheMode = it },
        itemText = { it.label },
    )
}
