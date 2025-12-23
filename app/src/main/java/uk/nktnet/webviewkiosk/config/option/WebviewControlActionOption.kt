package uk.nktnet.webviewkiosk.config.option

import org.json.JSONArray

enum class WebviewControlActionOption(val label: String) {
    NAVIGATION("Navigation"),
    BACK("Back"),
    FORWARD("Forward"),
    REFRESH("Refresh"),
    HOME("Home"),
    HISTORY("History"),
    BOOKMARK("Bookmark"),
    FILES("Files"),
    FIND("Find"),
    SCROLL_TOP("Scroll Top"),
    SCROLL_BOT("Scroll Bot"),
    APPS("Apps"),
    SETTINGS("Settings"),
    LOCK("Lock"),
    UNLOCK("Unlock");

    companion object {
        fun itemFromString(value: String?): WebviewControlActionOption? {
            return entries.find {
                it.name.equals(value, ignoreCase = true)
                || it.label.equals(value, ignoreCase = true)
            }
        }

        fun getDefaultAddressBarOptions(): List<WebviewControlActionOption> = listOf(
            BACK,
            FORWARD,
            REFRESH,
            HOME,
            HISTORY,
            BOOKMARK,
            FILES,
            FIND,
            SCROLL_TOP,
        )

        fun getDefaultKioskControlPanelOptions(): List<WebviewControlActionOption> = listOf(
            NAVIGATION,
            HOME,
            REFRESH,
            HISTORY,
            BOOKMARK,
            FILES,
            LOCK,
            UNLOCK,
        )

        fun parseFromJsonArray(jsonArray: JSONArray?): List<WebviewControlActionOption> {
            if (jsonArray == null) {
                return WebviewControlActionOption.getDefaultKioskControlPanelOptions()
            }
            return List(jsonArray.length()) { idx ->
                itemFromString(jsonArray.optString(idx))
            }.filterNotNull()
        }
    }
}
