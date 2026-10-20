package uk.nktnet.webviewkiosk.config.option

/** Actions that run before returning to the home URL on an inactivity reset. */
enum class ResetActionOption(val label: String) {
    HISTORY("Browsing History"),
    COOKIES("Cookies"),
    WEB_STORAGE("Website Storage (localStorage, IndexedDB)"),
    CACHE("Cache"),
    FORM_DATA("Form Data"),
    SSL_PREFERENCES("SSL Preferences"),
    RECREATE_WEBVIEW("Recreate WebView");

    companion object {
        fun fromString(value: String?): ResetActionOption? = entries.find {
            it.name.equals(value, ignoreCase = true)
        }

        fun defaults(): List<ResetActionOption> = listOf(HISTORY)
    }
}
