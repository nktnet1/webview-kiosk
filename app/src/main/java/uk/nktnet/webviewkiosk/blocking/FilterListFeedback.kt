package uk.nktnet.webviewkiosk.blocking

import java.io.IOException

/** Only fixed, actionable messages cross from diagnostics into the settings UI. */
internal fun filterListFailureMessage(error: Throwable): String =
    when (error) {
        is IOException -> "Couldn't read or download the list. Please try again."
        is IllegalArgumentException ->
            "This file or URL couldn't be used as a filter list."
        else -> "Couldn't update the list. Please try again."
    }

internal enum class FilterListBatchAction {
    ADD_DEFAULTS,
    REFRESH,
}

internal fun FilterRefreshResult.feedback(action: FilterListBatchAction): String =
    when {
        total == 0 && action == FilterListBatchAction.ADD_DEFAULTS ->
            "The default lists are already added."
        total == 0 -> "There are no downloaded lists to refresh."
        action == FilterListBatchAction.ADD_DEFAULTS && failures.isEmpty() ->
            "Added $updated default lists."
        action == FilterListBatchAction.ADD_DEFAULTS ->
            "Added $updated of $total lists. Retry to add the remaining lists."
        failures.isEmpty() -> "Refreshed $updated lists."
        else ->
            "Refreshed $updated of $total lists. Existing lists were kept for the others."
    }
