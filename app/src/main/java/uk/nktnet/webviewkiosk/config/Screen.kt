package com.nktnet.webview_kiosk.config
sealed class Screen(val route: String) {
    object AdminRestrictionsChanged : Screen("admin/restrictions_changed")

    object WebView : Screen("webview")
    object Settings : Screen("settings")

    object SettingsMoreActions : Screen("settings/more-actions")

    object SettingsWebContent : Screen("settings/web-content")
    object SettingsWebContentFiles : Screen("settings/web-content/files")
    object SettingsWebBrowsing : Screen("settings/web-browsing")
    object SettingsWebBrowsingSitePermissions : Screen("settings/web-browsing/site-permissions")

    object SettingsWebEngine : Screen("settings/web-engine")
    object SettingsWebLifecycle : Screen("settings/web-lifecycle")

    object SettingsAppearance : Screen("settings/appearance")
    object SettingsDevice : Screen("settings/device")
    object SettingsDeviceOwner : Screen("settings/device/owner")

    object SettingsJsScript : Screen("settings/js-script")
    object SettingsMqtt : Screen("settings/mqtt")
    object SettingsMqttConnection : Screen("settings/mqtt/connection")
    object SettingsMqttWill : Screen("settings/mqtt/will")
    object SettingsMqttRestrictions : Screen("settings/mqtt/restrictions")
    object SettingsMqttDebug : Screen("settings/mqtt/debug")
    object SettingsMqttTopics : Screen("settings/mqtt/topics")
    object SettingsMqttTopicsPublishEvent : Screen("settings/mqtt/topics/subscribe/event")
    object SettingsMqttTopicsPublishResponse : Screen("settings/mqtt/topics/subscribe/response")
    object SettingsMqttTopicsSubscribeCommand : Screen("settings/mqtt/topics/subscribe/command")
    object SettingsMqttTopicsSubscribeSettings : Screen("settings/mqtt/topics/subscribe/settings")
    object SettingsMqttTopicsSubscribeRequest : Screen("settings/mqtt/topics/subscribe/request")

    object SettingsAbout : Screen("settings/about")
}
