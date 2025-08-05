package com.nktnet.webview_kiosk.config
sealed class Screen(val route: String) {
    object WebView : Screen("webview")
    object Settings : Screen("settings")
    object SettingsAppearance : Screen("settings/appearance")
    object SettingsUrlControl : Screen("settings/url-control")
}