package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webbrowsing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.config.option.SearchSuggestionEngineOption
import uk.nktnet.webviewkiosk.ui.components.setting.fields.DropdownSettingFieldItem

@Composable
fun SearchSuggestionEngineSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }

    DropdownSettingFieldItem(
        label = "Search Suggestion Engine",
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
        restricted = userSettings.isRestricted(UserSettingsKeys.WebBrowsing.SEARCH_SUGGESTION_ENGINE),
        onSave = { userSettings.searchSuggestionEngine = it },
        itemText = { it.label }
    )
}
