package uk.nktnet.webviewkiosk.config

object UserSettingsKeys {
    const val PREFS_NAME = "user_settings"

    object WebContent {
        const val HOME_URL = "web_content.home_url"
        const val WEBSITE_BLACKLIST = "web_content.website_blacklist"
        const val WEBSITE_WHITELIST = "web_content.website_whitelist"
        const val WEBSITE_BOOKMARKS = "web_content.website_bookmarks"
        const val ALLOW_LOCAL_FILES = "web_content.allow_local_files"
    }

    object WebBrowsing {
        const val ALLOW_REFRESH = "web_browsing.allow_refresh"
        const val ALLOW_PULL_TO_REFRESH = "web_browsing.allow_pull_to_refresh"
        const val ALLOW_BACKWARDS_NAVIGATION = "web_browsing.allow_backwards_navigation"
        const val ALLOW_GO_HOME = "web_browsing.allow_go_home"
        const val CLEAR_HISTORY_ON_HOME = "web_browsing.clear_history_on_home"
        const val REPLACE_HISTORY_URL_ON_REDIRECT = "web_browsing.replace_history_url_on_redirect"
        const val ALLOW_HISTORY_ACCESS = "web_browsing.allow_history_access"
        const val ALLOW_BOOKMARK_ACCESS = "web_browsing.allow_bookmark_access"
        const val ALLOW_OTHER_URL_SCHEMES = "web_browsing.allow_other_url_schemes"
        const val ALLOW_DEFAULT_LONG_PRESS = "web_browsing.allow_default_long_press"
        const val ALLOW_LINK_LONG_PRESS_CONTEXT_MENU = "web_browsing.allow_link_long_press_context_menu"
        const val KIOSK_CONTROL_PANEL_REGION = "web_browsing.kiosk_control_panel_region"
        const val SEARCH_PROVIDER_URL = "web_browsing.search_provider_url"
        const val SEARCH_SUGGESTION_ENGINE = "web_browsing.search_suggestion_engine"
    }

    object WebEngine {
        const val ENABLE_JAVASCRIPT = "web_engine.enable_javascript"
        const val ENABLE_DOM_STORAGE = "web_engine.enable_dom_storage"
        const val CACHE_MODE = "web_engine.cache_mode"
        const val LAYOUT_ALGORITHM = "web_engine.layout_algorithm"
        const val ACCEPT_COOKIES = "web_engine.accept_cookies"
        const val ACCEPT_THIRD_PARTY_COOKIES = "web_engine.accept_third_party_cookies"
        const val USER_AGENT = "web_engine.user_agent"
        const val USE_WIDE_VIEWPORT = "web_engine.use_wide_viewport"
        const val LOAD_WITH_OVERVIEW_MODE = "web_engine.load_with_overview_mode"
        const val ENABLE_ZOOM = "web_engine.enable_zoom"
        const val DISPLAY_ZOOM_CONTROLS = "web_engine.display_zoom_controls"
        const val ALLOW_FILE_ACCESS_FROM_FILE_URLS = "web_engine.allow_file_access_from_file_urls"
        const val ALLOW_UNIVERSAL_ACCESS_FROM_FILE_URLS = "web_engine.allow_universal_access_from_file_urls"
        const val MEDIA_PLAYBACK_REQUIRES_USER_GESTURE = "web_engine.media_playback_requires_user_gesture"
        const val SSL_ERROR_MODE = "web_engine.ssl_error_mode"
        const val ENABLE_BATTERY_API = "web_engine.enable_battery_api"
    }

    object WebLifecycle {
        const val LOCK_ON_LAUNCH = "web_lifecycle.lock_on_launch"
        const val RESET_ON_LAUNCH = "web_lifecycle.reset_on_launch"
        const val RESET_ON_INACTIVITY_SECONDS = "web_lifecycle.reset_on_inactivity_seconds"
        const val REFRESH_ON_LOADING_ERROR_INTERVAL_SECONDS = "web_lifecycle.refresh_on_loading_error_interval_seconds"
    }

    object Appearance {
        const val THEME = "appearance.theme"
        const val ADDRESS_BAR_MODE = "appearance.address_bar_mode"
        const val FLOATING_TOOLBAR_MODE = "appearance.floating_toolbar_mode"
        const val WEBVIEW_INSET = "appearance.webview_inset"
        const val IMMERSIVE_MODE = "appearance.immersive_mode"
        const val BLOCKED_MESSAGE = "appearance.blocked_message"
    }

    object Device {
        const val KEEP_SCREEN_ON = "device.keep_screen_on"
        const val DEVICE_ROTATION = "device.rotation"
        const val ALLOW_CAMERA = "device.allow_camera"
        const val ALLOW_MICROPHONE = "device.allow_microphone"
        const val ALLOW_LOCATION = "device.allow_location"
        const val BACK_BUTTON_HOLD_ACTION = "device.back_button_hold_action"
        const val CUSTOM_UNLOCK_SHORTCUT = "device.custom_unlock_shortcut"
        const val UNLOCK_AUTH_REQUIREMENT = "device.unlock_auth_requirement"
    }

    object JsScripts {
        const val APPLY_APP_THEME = "js_scripts.apply_app_theme"
        const val APPLY_DESKTOP_VIEWPORT_WIDTH = "js_scripts.apply_desktop_viewport_width"
        const val CUSTOM_SCRIPT_ON_PAGE_START = "js_scripts.custom_script_on_page_start"
        const val CUSTOM_SCRIPT_ON_PAGE_FINISH = "js_scripts.custom_script_on_page_finish"
    }
}
