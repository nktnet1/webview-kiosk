package uk.nktnet.webviewkiosk.config

object Constants {
    const val APP_NAME = "Webview Kiosk"
    const val WEBSITE_URL = "https://webviewkiosk.nktnet.uk"
    const val GITHUB_URL = "https://github.com/nktnet1/webview-kiosk"
    const val DEFAULT_SEARCH_PROVIDER_URL = "https://google.com/search?q="

    const val MIN_INACTIVITY_TIMEOUT_SECONDS = 15
    const val INACTIVITY_COUNTDOWN_SECONDS = 5
    const val MIN_REFRESH_ON_LOADING_ERROR_INTERVAL_SECONDS = 5

    const val MIN_DESKTOP_WIDTH = 640

    const val WEB_CONTENT_FILES_DIR = "web-content-files"

    const val GEOLOCATION_RESOURCE = "webviewkiosk.custom-permission.geolocation"

    const val REQUEST_CODE_LOLLIPOP_DEVICE_CREDENTIAL = 9999
}
