package com.nktnet.webview_kiosk.ui.components.setting.fielditems.webbrowsing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nktnet.webview_kiosk.R
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.UserSettingsKeys
import com.nktnet.webview_kiosk.config.option.SearchSuggestionEngineOption
import com.nktnet.webview_kiosk.ui.components.setting.fields.DropdownSettingFieldItem

@Composable
fun SearchSuggestionEngineSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.WebBrowsing.SEARCH_SUGGESTION_ENGINE

    DropdownSettingFieldItem(
        label = stringResource(id = R.string.web_browsing_search_suggestion_engine_title),
        infoText = """
            Configure the search engine for text suggestions (autocomplete/hints)
            when typing in the address bar.

            For example, when searching for "new", you may get suggested
            - news
            - new recipes
            - new york times
        """.trimIndent(),
        options = SearchSuggestionEngineOption.entries,
        initialValue = userSettings.searchSuggestionEngine,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.searchSuggestionEngine = it },
        itemText = { it.label }
    )
}
