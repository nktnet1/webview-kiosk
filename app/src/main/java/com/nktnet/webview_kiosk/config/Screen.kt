package com.nktnet.webview_kiosk.config
sealed class Screen(val route: String) {
    object WebView : Screen("webview")
    object Settings : Screen("settings")
    object SettingsAppearance : Screen("settings/appearance")
    object SettingsWebContent : Screen("settings/web-content")
    object SettingsWebBrowsing : Screen("settings/web-browsing")
    object SettingsDevice : Screen("settings/device")

    object SettingsAbout : Screen("settings/about")

}
