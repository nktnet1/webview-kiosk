package uk.nktnet.webviewkiosk.config.option

import org.json.JSONArray

enum class KioskControlPanelActionOption(val label: String) {
    NAVIGATION("Navigation"),
    BACK("Back"),
    FORWARD("Forward"),
    REFRESH("Refresh"),
    HOME("Home"),
    HISTORY("History"),
    BOOKMARK("Bookmark"),
    FILES("Files"),
    FIND("Find"),
    SETTINGS("Settings"),
    LOCK("Lock"),
    UNLOCK("Unlock");

    companion object {
        fun itemFromString(value: String?): KioskControlPanelActionOption? {
            return entries.find {
                it.name.equals(value, ignoreCase = true)
                || it.label.equals(value, ignoreCase = true)
            }
        }

        fun getDefault(): List<KioskControlPanelActionOption> = listOf(
            NAVIGATION,
            HOME,
            REFRESH,
            HISTORY,
            BOOKMARK,
            FILES,
            LOCK,
            UNLOCK,
        )

        fun parseFromJsonArray(jsonArray: JSONArray?): List<KioskControlPanelActionOption> {
            if (jsonArray == null) {
                return KioskControlPanelActionOption.getDefault()
            }
            return List(jsonArray.length()) { idx ->
                itemFromString(jsonArray.optString(idx))
            }.filterNotNull()
        }
    }
}
