package uk.nktnet.webviewkiosk.ui.screens

import androidx.navigation.NavController

internal const val SETTINGS_SEARCH_TARGET_KEY = "settings_search_target"

/**
 * The destination owns the focus target, so returning to search leaves no stale
 * scroll request.
 */
internal fun NavController.navigateToSearchResult(result: SettingsSearchResult) {
    navigate(result.route)
    if (result.targetRes != null) {
        currentBackStackEntry
            ?.savedStateHandle
            ?.set(SETTINGS_SEARCH_TARGET_KEY, result.targetRes)
    }
}
