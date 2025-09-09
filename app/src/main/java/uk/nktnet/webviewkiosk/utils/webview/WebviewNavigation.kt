package uk.nktnet.webviewkiosk.utils.webview

import uk.nktnet.webviewkiosk.config.HistoryEntry
import uk.nktnet.webviewkiosk.config.SystemSettings
import uk.nktnet.webviewkiosk.config.UserSettings

object WebViewNavigation {
    private var isProgrammaticNavigation = false

    fun goBack(customLoadUrl: (newUrl: String) -> Unit, systemSettings: SystemSettings) {
        val index = systemSettings.historyIndex
        if (index > 0) {
            val newIndex = index - 1
            systemSettings.historyIndex = newIndex
            isProgrammaticNavigation = true
            customLoadUrl(systemSettings.historyStack[newIndex].url)
        }
    }

    fun goForward(customLoadUrl: (newUrl: String) -> Unit, systemSettings: SystemSettings) {
        val index = systemSettings.historyIndex
        if (index < systemSettings.historyStack.lastIndex) {
            val newIndex = index + 1
            systemSettings.historyIndex = newIndex
            isProgrammaticNavigation = true
            customLoadUrl(systemSettings.historyStack[newIndex].url)
        }
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

    fun navigateToIndex(customLoadUrl: (newUrl: String) -> Unit, systemSettings: SystemSettings, index: Int) {
        if (index in 0..systemSettings.historyStack.lastIndex) {
            isProgrammaticNavigation = true
            systemSettings.historyIndex = index
            customLoadUrl(systemSettings.historyStack[index].url)
        }
    }

    fun appendWebviewHistory(systemSettings: SystemSettings, url: String) {
        if (isProgrammaticNavigation) {
            isProgrammaticNavigation = false
            return
        }

        val newUrl = url.trimEnd('/')
        val stack = systemSettings.historyStack.toMutableList()
        val currentIndex = systemSettings.historyIndex
        val currentUrl = stack.getOrNull(currentIndex)?.url?.trimEnd('/')

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
        val currentIndex = systemSettings.historyIndex.coerceIn(0, systemSettings.historyStack.lastIndex)
        val currentEntry = systemSettings.historyStack.getOrNull(currentIndex)
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
