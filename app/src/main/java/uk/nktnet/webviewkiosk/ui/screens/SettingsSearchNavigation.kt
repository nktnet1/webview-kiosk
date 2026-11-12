package uk.nktnet.webviewkiosk.ui.screens

import androidx.navigation.NavController

// A new key avoids restoring the legacy integer resource-ID payload as a String.
internal const val SETTINGS_SEARCH_TARGET_KEY = "settings_search_target_id"

/**
 * The destination owns the focus target, so returning to search leaves no stale
 * scroll request.
 */
internal fun NavController.navigateToSearchResult(result: SettingsSearchResult) {
    navigate(result.route)
    if (result.targetId != null) {
        currentBackStackEntry
            ?.savedStateHandle
            ?.set(SETTINGS_SEARCH_TARGET_KEY, result.targetId)
    }
}
