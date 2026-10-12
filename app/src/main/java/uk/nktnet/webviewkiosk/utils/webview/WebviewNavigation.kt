package uk.nktnet.webviewkiosk.utils.webview

import android.webkit.URLUtil.isValidUrl
import uk.nktnet.webviewkiosk.config.HistoryEntry
import uk.nktnet.webviewkiosk.config.SystemSettings
import uk.nktnet.webviewkiosk.config.UserSettings

object WebViewNavigation {
    fun goBack(customLoadUrl: (newUrl: String) -> Unit, systemSettings: SystemSettings) {
        val index = systemSettings.historyIndex
        if (index in 1..systemSettings.historyStack.lastIndex) {
            navigateToIndex(customLoadUrl, systemSettings, index - 1)
        }
    }

    fun goForward(customLoadUrl: (newUrl: String) -> Unit, systemSettings: SystemSettings) {
        val index = systemSettings.historyIndex
        if (index !in systemSettings.historyStack.indices) return
        val newIndex = index + 1
        navigateToIndex(customLoadUrl, systemSettings, newIndex)
    }

    fun goHome(
        customLoadUrl: (newUrl: String) -> Unit,
        systemSettings: SystemSettings,
        userSettings: UserSettings,
    ) {
        if (userSettings.clearHistoryOnHome) {
            systemSettings.clearHistory()
        }

        if (systemSettings.currentUrl != userSettings.homeUrl) {
            customLoadUrl(userSettings.homeUrl)
        }
    }

    fun refresh(
        customLoadUrl: (newUrl: String) -> Unit,
        systemSettings: SystemSettings,
        userSettings: UserSettings,
    ) {
        if (userSettings.allowRefresh) {
            customLoadUrl(systemSettings.currentUrl)
        }
    }

    fun navigateToIndex(
        customLoadUrl: (newUrl: String) -> Unit,
        systemSettings: SystemSettings,
        index: Int,
    ) {
        val entry = systemSettings.historyStack.getOrNull(index) ?: return
        val previousIndex = systemSettings.historyIndex
        systemSettings.historyIndex = index
        try {
            customLoadUrl(entry.url)
        } catch (error: Throwable) {
            if (
                systemSettings.historyIndex == index
                && systemSettings.historyStack.getOrNull(index)?.id == entry.id
            ) {
                systemSettings.historyIndex = previousIndex
            }
            throw error
        }
    }

    fun appendWebviewHistory(
        systemSettings: SystemSettings,
        url: String,
        originalUrl: String?,
        shouldReplaceRedirect: Boolean,
        traversalEntryId: String? = null,
    ) {
        val newUrl = url.trimEnd('/')
        val stack = systemSettings.historyStack.toMutableList()
        val currentIndex = systemSettings.historyIndex.coerceIn(-1, stack.lastIndex)
        val currentEntry = stack.getOrNull(currentIndex)
        val currentUrl = currentEntry?.url?.trimEnd('/')

        val replace = (
            shouldReplaceRedirect
            && (
                (traversalEntryId != null && traversalEntryId == currentEntry?.id)
                || (
                    !originalUrl.isNullOrEmpty()
                    && isValidUrl(originalUrl)
                    && originalUrl.trimEnd('/') != newUrl
                    && systemSettings.urlBeforeNavigation != currentUrl
                )
            )
        )
        if (replace && currentEntry != null) {
            stack[currentIndex] = currentEntry.copy(url = newUrl)
            systemSettings.historyStack = stack
            systemSettings.historyIndex = currentIndex
            return
        }

        if (currentUrl != newUrl) {
            val updatedStack = if (currentIndex < stack.lastIndex) {
                stack.subList(0, currentIndex + 1).toMutableList()
            } else {
                stack
            }

            updatedStack.add(HistoryEntry(url = newUrl))
            systemSettings.historyStack = updatedStack
            systemSettings.historyIndex = updatedStack.lastIndex
        } else {
            systemSettings.historyIndex = currentIndex
        }
    }

    fun clearHistory(systemSettings: SystemSettings) {
        val stack = systemSettings.historyStack
        if (stack.isEmpty()) {
            systemSettings.historyIndex = -1
            return
        }
        val currentIndex = systemSettings.historyIndex.coerceIn(
            0,
            stack.lastIndex
        )
        val currentEntry = stack.getOrNull(currentIndex)
        if (currentEntry != null) {
            systemSettings.historyStack = listOf(currentEntry)
            systemSettings.historyIndex = 0
        } else {
            systemSettings.clearHistory()
        }
    }

    fun removeHistoryAtIndex(systemSettings: SystemSettings, index: Int) {
        val currentIndex = systemSettings.historyIndex
        val stack = systemSettings.historyStack.toMutableList()
        if (index in stack.indices && index != currentIndex) {
            stack.removeAt(index)
            if (index < currentIndex) {
                systemSettings.historyIndex -= 1
            }
            systemSettings.historyStack = stack
        }
    }
}
