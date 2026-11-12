package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webbrowsing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.config.option.SearchSuggestionEngineOption
import uk.nktnet.webviewkiosk.ui.components.setting.fields.DropdownSettingFieldItem
import uk.nktnet.webviewkiosk.ui.resources.displayLabelRes

@Composable
fun SearchSuggestionEngineSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.WebBrowsing.SEARCH_SUGGESTION_ENGINE

    DropdownSettingFieldItem(
        options = SearchSuggestionEngineOption.entries,
        initialValue = userSettings.searchSuggestionEngine,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.searchSuggestionEngine = it },
        itemText = { stringResource(it.displayLabelRes) },
    )
}
