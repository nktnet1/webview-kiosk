package uk.nktnet.webviewkiosk.config.option

import org.json.JSONArray

enum class AddressBarActionOption(val label: String) {
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
        fun itemFromString(value: String?): AddressBarActionOption? {
            return AddressBarActionOption.entries.find {
                it.name.equals(value, ignoreCase = true)
                || it.label.equals(value, ignoreCase = true)
            }
        }
        fun getDefault(): List<AddressBarActionOption> = listOf(
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

        fun parseFromJsonArray(jsonArray: JSONArray?): List<AddressBarActionOption> {
            if (jsonArray == null) {
                return AddressBarActionOption.getDefault()
            }
            return List(jsonArray.length()) { idx ->
                AddressBarActionOption.itemFromString(jsonArray.optString(idx))
            }.filterNotNull()
        }
    }
}
