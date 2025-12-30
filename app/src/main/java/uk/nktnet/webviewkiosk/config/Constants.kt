package uk.nktnet.webviewkiosk.config

object Constants {
    const val WEBSITE_URL = "https://webviewkiosk.nktnet.uk"
    const val SOURCE_CODE_URL = "https://github.com/nktnet1/webview-kiosk"
    const val DEFAULT_SEARCH_PROVIDER_URL = "https://duckduckgo.com?q="

    const val MIN_INACTIVITY_TIMEOUT_SECONDS = 10
    const val INACTIVITY_COUNTDOWN_SECONDS = 5
    const val MIN_REFRESH_ON_LOADING_ERROR_INTERVAL_SECONDS = 5
    const val MIN_DESKTOP_WIDTH = 640

    const val WEB_CONTENT_FILES_DIR = "web-content-files"

    const val GEOLOCATION_RESOURCE = "webviewkiosk.custom-permission.geolocation"

    const val INTENT_NAVIGATE_TO_WEBVIEW_SCREEN = "intent_navigate_to_webview_screen"

    const val MQTT_AUTO_RECONNECT_INTERVAL_SECONDS = 3
    const val REQUEST_CODE_LOLLIPOP_DEVICE_CREDENTIAL = 9999

}
