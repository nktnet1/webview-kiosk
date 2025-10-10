package uk.nktnet.webviewkiosk.config

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.webkit.WebSettings
import org.json.JSONObject
import androidx.core.content.edit
import uk.nktnet.webviewkiosk.config.option.*
import uk.nktnet.webviewkiosk.utils.booleanPref
import uk.nktnet.webviewkiosk.utils.intPref
import uk.nktnet.webviewkiosk.utils.stringPref
import uk.nktnet.webviewkiosk.utils.stringPrefOptional

class UserSettings(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Web Content
    var homeUrl by stringPref(prefs, HOME_URL, Constants.WEBSITE_URL)
    var websiteBlacklist by stringPrefOptional(prefs, WEBSITE_BLACKLIST)
    var websiteWhitelist by stringPrefOptional(prefs, WEBSITE_WHITELIST)
    var websiteBookmarks by stringPrefOptional(prefs, WEBSITE_BOOKMARKS)
    var allowLocalFiles by booleanPref(prefs, ALLOW_LOCAL_FILES, true)

    // Web Browsing
    var allowRefresh by booleanPref(prefs, ALLOW_REFRESH, true)
    var allowBackwardsNavigation by booleanPref(prefs, ALLOW_BACKWARDS_NAVIGATION, true)
    var allowGoHome by booleanPref(prefs, ALLOW_GO_HOME, true)
    var clearHistoryOnHome by booleanPref(prefs, CLEAR_HISTORY_ON_HOME, false)
    var allowHistoryAccess by booleanPref(prefs, ALLOW_HISTORY_ACCESS, true)
    var allowBookmarkAccess by booleanPref(prefs, ALLOW_BOOKMARK_ACCESS, true)
    var allowOtherUrlSchemes by booleanPref(prefs, ALLOW_OTHER_URL_SCHEMES, false)
    var searchProviderUrl by stringPref(prefs, SEARCH_PROVIDER_URL, Constants.DEFAULT_SEARCH_PROVIDER_URL)

    // Web Engine
    var enableJavaScript by booleanPref(prefs, ENABLE_JAVASCRIPT, true)
    var enableDomStorage by booleanPref(prefs, ENABLE_DOM_STORAGE, true)
    var acceptCookies by booleanPref(prefs, ACCEPT_COOKIES, true)
    var acceptThirdPartyCookies by booleanPref(prefs, ACCEPT_THIRD_PARTY_COOKIES, false)
    var cacheMode: CacheModeOption
        get() = CacheModeOption.fromInt(prefs.getInt(CACHE_MODE, WebSettings.LOAD_DEFAULT))
        set(value) = prefs.edit { putInt(CACHE_MODE, value.mode) }
    var layoutAlgorithm: LayoutAlgorithmOption
        get() = LayoutAlgorithmOption.fromAlgorithm(
            when (val value = prefs.getString(LAYOUT_ALGORITHM, null)) {
                null -> WebSettings.LayoutAlgorithm.NORMAL
                else -> WebSettings.LayoutAlgorithm.valueOf(value)
            }
        )
        set(value) = prefs.edit { putString(LAYOUT_ALGORITHM, value.algorithm.name) }
    var userAgent by stringPrefOptional(prefs, USER_AGENT)
    var useWideViewPort by booleanPref(prefs, USE_WIDE_VIEWPORT, true)
    var loadWithOverviewMode by booleanPref(prefs, LOAD_WITH_OVERVIEW_MODE, true)
    var enableZoom by booleanPref(prefs, ENABLE_ZOOM, true)
    var displayZoomControls by booleanPref(prefs, DISPLAY_ZOOM_CONTROLS, false)
    var allowFileAccessFromFileURLs by booleanPref(prefs, ALLOW_FILE_ACCESS_FROM_FILE_URLS, false)
    var allowUniversalAccessFromFileURLs by booleanPref(prefs, ALLOW_UNIVERSAL_ACCESS_FROM_FILE_URLS, false)
    var mediaPlaybackRequiresUserGesture by booleanPref(prefs, MEDIA_PLAYBACK_REQUIRES_USER_GESTURE, true)

    // Web Lifecycle
    var lockOnLaunch by booleanPref(prefs, LOCK_ON_LAUNCH, false)
    var resetOnLaunch by booleanPref(prefs, RESET_ON_LAUNCH, false)
    var resetOnInactivitySeconds by intPref(prefs, RESET_ON_INACTIVITY_SECONDS, 0)

    // Appearance
    var theme: ThemeOption
        get() = ThemeOption.fromString(prefs.getString(THEME, null))
        set(value) = prefs.edit { putString(THEME, value.name) }
    var addressBarMode: AddressBarOption
        get() = AddressBarOption.fromString(prefs.getString(ADDRESS_BAR_MODE, null))
        set(value) = prefs.edit { putString(ADDRESS_BAR_MODE, value.name) }
    var webViewInset: WebViewInset
        get() = WebViewInset.fromString(prefs.getString(WEBVIEW_INSET, null))
        set(value) = prefs.edit { putString(WEBVIEW_INSET, value.name) }
    var blockedMessage by stringPref(prefs, BLOCKED_MESSAGE, "This site is blocked by Webview Kiosk.")

    // Device
    var keepScreenOn by booleanPref(prefs, KEEP_SCREEN_ON, false)
    var deviceRotation: DeviceRotationOption
        get() = DeviceRotationOption.fromString(prefs.getString(DEVICE_ROTATION, null))
        set(value) = prefs.edit { putString(DEVICE_ROTATION, value.degrees) }
    var allowCamera by booleanPref(prefs, ALLOW_CAMERA, false)
    var allowMicrophone by booleanPref(prefs, ALLOW_MICROPHONE, false)
    var allowLocation by booleanPref(prefs, ALLOW_LOCATION, false)
    var customUnlockShortcut by stringPrefOptional(prefs, CUSTOM_UNLOCK_SHORTCUT)

    // JS Scripts
    var applyAppTheme by booleanPref(prefs, JS_APPLY_APP_THEME, true)
    var applyDesktopViewportWidth by intPref(prefs, JS_APPLY_DESKTOP_VIEWPORT_WIDTH, 0)
    var customScriptOnPageStart by stringPrefOptional(prefs, JS_CUSTOM_SCRIPT_ON_PAGE_START)
    var customScriptOnPageFinish by stringPrefOptional(prefs, JS_CUSTOM_SCRIPT_ON_PAGE_FINISH)

    fun exportToBase64(): String {
        val json = JSONObject().apply {
            put(HOME_URL, homeUrl)
            put(WEBSITE_BLACKLIST, websiteBlacklist)
            put(WEBSITE_WHITELIST, websiteWhitelist)
            put(WEBSITE_BOOKMARKS, websiteBookmarks)
            put(ALLOW_LOCAL_FILES, allowLocalFiles)

            put(ALLOW_REFRESH, allowRefresh)
            put(ALLOW_BACKWARDS_NAVIGATION, allowBackwardsNavigation)
            put(ALLOW_GO_HOME, allowGoHome)
            put(CLEAR_HISTORY_ON_HOME, clearHistoryOnHome)
            put(ALLOW_HISTORY_ACCESS, allowHistoryAccess)
            put(ALLOW_BOOKMARK_ACCESS, allowBookmarkAccess)
            put(ALLOW_OTHER_URL_SCHEMES, allowOtherUrlSchemes)
            put(SEARCH_PROVIDER_URL, searchProviderUrl)

            put(ENABLE_JAVASCRIPT, enableJavaScript)
            put(ENABLE_DOM_STORAGE, enableDomStorage)
            put(ACCEPT_COOKIES, acceptCookies)
            put(ACCEPT_THIRD_PARTY_COOKIES, acceptThirdPartyCookies)
            put(CACHE_MODE, cacheMode.mode)
            put(LAYOUT_ALGORITHM, layoutAlgorithm.algorithm.name)
            put(USER_AGENT, userAgent)
            put(USE_WIDE_VIEWPORT, useWideViewPort)
            put(LOAD_WITH_OVERVIEW_MODE, loadWithOverviewMode)
            put(ENABLE_ZOOM, enableZoom)
            put(DISPLAY_ZOOM_CONTROLS, displayZoomControls)
            put(ALLOW_FILE_ACCESS_FROM_FILE_URLS, allowFileAccessFromFileURLs)
            put(ALLOW_UNIVERSAL_ACCESS_FROM_FILE_URLS, allowUniversalAccessFromFileURLs)
            put(MEDIA_PLAYBACK_REQUIRES_USER_GESTURE, mediaPlaybackRequiresUserGesture)

            put(LOCK_ON_LAUNCH, lockOnLaunch)
            put(RESET_ON_LAUNCH, resetOnLaunch)
            put(RESET_ON_INACTIVITY_SECONDS, resetOnInactivitySeconds)

            put(BLOCKED_MESSAGE, blockedMessage)
            put(THEME, theme.name)
            put(ADDRESS_BAR_MODE, addressBarMode.name)
            put(WEBVIEW_INSET, webViewInset.name)

            put(KEEP_SCREEN_ON, keepScreenOn)
            put(DEVICE_ROTATION, deviceRotation.degrees)
            put(ALLOW_CAMERA, allowCamera)
            put(ALLOW_MICROPHONE, allowMicrophone)
            put(ALLOW_LOCATION, allowLocation)
            put(CUSTOM_UNLOCK_SHORTCUT, customUnlockShortcut)

            put(JS_APPLY_APP_THEME, applyAppTheme)
            put(JS_APPLY_DESKTOP_VIEWPORT_WIDTH, applyDesktopViewportWidth)
            put(JS_CUSTOM_SCRIPT_ON_PAGE_START, customScriptOnPageStart)
            put(JS_CUSTOM_SCRIPT_ON_PAGE_FINISH, customScriptOnPageFinish)
        }
        return Base64.encodeToString(json.toString().toByteArray(), Base64.NO_WRAP)
    }

    fun importFromBase64(base64: String): Boolean {
        return try {
            val json = JSONObject(String(Base64.decode(base64, Base64.NO_WRAP)))

            homeUrl = json.optString(HOME_URL, homeUrl)
            websiteBlacklist = json.optString(WEBSITE_BLACKLIST, websiteBlacklist)
            websiteWhitelist = json.optString(WEBSITE_WHITELIST, websiteWhitelist)
            websiteBookmarks = json.optString(WEBSITE_BOOKMARKS, websiteBookmarks)
            allowLocalFiles = json.optBoolean(ALLOW_LOCAL_FILES, allowLocalFiles)

            allowRefresh = json.optBoolean(ALLOW_REFRESH, allowRefresh)
            allowBackwardsNavigation = json.optBoolean(ALLOW_BACKWARDS_NAVIGATION, allowBackwardsNavigation)
            allowGoHome = json.optBoolean(ALLOW_GO_HOME, allowGoHome)
            clearHistoryOnHome = json.optBoolean(CLEAR_HISTORY_ON_HOME, clearHistoryOnHome)
            allowHistoryAccess = json.optBoolean(ALLOW_HISTORY_ACCESS, allowHistoryAccess)
            allowBookmarkAccess = json.optBoolean(ALLOW_BOOKMARK_ACCESS, allowBookmarkAccess)
            allowOtherUrlSchemes = json.optBoolean(ALLOW_OTHER_URL_SCHEMES, allowOtherUrlSchemes)
            searchProviderUrl = json.optString(SEARCH_PROVIDER_URL, searchProviderUrl)

            enableJavaScript = json.optBoolean(ENABLE_JAVASCRIPT, enableJavaScript)
            enableDomStorage = json.optBoolean(ENABLE_DOM_STORAGE, enableDomStorage)
            acceptCookies = json.optBoolean(ACCEPT_COOKIES, acceptCookies)
            acceptThirdPartyCookies = json.optBoolean(ACCEPT_THIRD_PARTY_COOKIES, acceptThirdPartyCookies)
            cacheMode = CacheModeOption.fromInt(json.optInt(CACHE_MODE, cacheMode.mode))
            layoutAlgorithm = LayoutAlgorithmOption.fromAlgorithm(
                WebSettings.LayoutAlgorithm.valueOf(json.optString(LAYOUT_ALGORITHM, layoutAlgorithm.algorithm.name))
            )
            userAgent = json.optString(USER_AGENT, userAgent)
            useWideViewPort = json.optBoolean(USE_WIDE_VIEWPORT, useWideViewPort)
            loadWithOverviewMode = json.optBoolean(LOAD_WITH_OVERVIEW_MODE, loadWithOverviewMode)
            enableZoom = json.optBoolean(ENABLE_ZOOM, enableZoom)
            displayZoomControls = json.optBoolean(DISPLAY_ZOOM_CONTROLS, displayZoomControls)
            allowFileAccessFromFileURLs = json.optBoolean(ALLOW_FILE_ACCESS_FROM_FILE_URLS, allowFileAccessFromFileURLs)
            allowUniversalAccessFromFileURLs = json.optBoolean(ALLOW_UNIVERSAL_ACCESS_FROM_FILE_URLS, allowUniversalAccessFromFileURLs)
            mediaPlaybackRequiresUserGesture = json.optBoolean(MEDIA_PLAYBACK_REQUIRES_USER_GESTURE, mediaPlaybackRequiresUserGesture)

            lockOnLaunch = json.optBoolean(LOCK_ON_LAUNCH, lockOnLaunch)
            resetOnLaunch = json.optBoolean(RESET_ON_LAUNCH, resetOnLaunch)
            resetOnInactivitySeconds = json.optInt(RESET_ON_INACTIVITY_SECONDS, resetOnInactivitySeconds)

            theme = ThemeOption.fromString(json.optString(THEME, theme.name))
            addressBarMode = AddressBarOption.fromString(json.optString(ADDRESS_BAR_MODE, addressBarMode.name))
            webViewInset = WebViewInset.fromString(json.optString(WEBVIEW_INSET, webViewInset.name))
            blockedMessage = json.optString(BLOCKED_MESSAGE, blockedMessage)

            keepScreenOn = json.optBoolean(KEEP_SCREEN_ON, keepScreenOn)
            deviceRotation = DeviceRotationOption.fromString(json.optString(DEVICE_ROTATION, deviceRotation.degrees))
            allowCamera = json.optBoolean(ALLOW_CAMERA, allowCamera)
            allowMicrophone = json.optBoolean(ALLOW_MICROPHONE, allowMicrophone)
            allowLocation = json.optBoolean(ALLOW_LOCATION, allowLocation)
            customUnlockShortcut = json.optString(CUSTOM_UNLOCK_SHORTCUT, customUnlockShortcut)

            applyAppTheme = json.optBoolean(JS_APPLY_APP_THEME, applyAppTheme)
            applyDesktopViewportWidth = json.optInt(JS_APPLY_DESKTOP_VIEWPORT_WIDTH, applyDesktopViewportWidth)
            customScriptOnPageStart = json.optString(JS_CUSTOM_SCRIPT_ON_PAGE_START, customScriptOnPageStart)
            customScriptOnPageFinish = json.optString(JS_CUSTOM_SCRIPT_ON_PAGE_FINISH, customScriptOnPageFinish)
            true
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        private const val PREFS_NAME = "user_settings"

        private const val HOME_URL = "web_content.home_url"
        private const val WEBSITE_BLACKLIST = "web_content.website_blacklist"
        private const val WEBSITE_WHITELIST = "web_content.website_whitelist"
        private const val WEBSITE_BOOKMARKS = "web_content.website_bookmarks"
        private const val ALLOW_LOCAL_FILES = "web_content.allow_local_files"

        private const val ALLOW_REFRESH = "web_browsing.allow_refresh"
        private const val ALLOW_BACKWARDS_NAVIGATION = "web_browsing.allow_backwards_navigation"
        private const val ALLOW_GO_HOME = "web_browsing.allow_go_home"
        private const val CLEAR_HISTORY_ON_HOME = "web_browsing.clear_history_on_home"
        private const val ALLOW_HISTORY_ACCESS = "web_browsing.allow_history_access"
        private const val ALLOW_BOOKMARK_ACCESS = "web_browsing.allow_bookmark_access"
        private const val ALLOW_OTHER_URL_SCHEMES = "web_browsing.allow_other_url_schemes"
        private const val SEARCH_PROVIDER_URL = "web_browsing.search_provider_url"

        private const val ENABLE_JAVASCRIPT = "web_engine.enable_javascript"
        private const val ENABLE_DOM_STORAGE = "web_engine.enable_dom_storage"
        private const val CACHE_MODE = "web_engine.cache_mode"
        private const val LAYOUT_ALGORITHM = "web_engine.layout_algorithm"
        private const val ACCEPT_COOKIES = "web_engine.accept_cookies"
        private const val ACCEPT_THIRD_PARTY_COOKIES = "web_engine.accept_third_party_cookies"
        private const val USER_AGENT = "web_engine.user_agent"
        private const val USE_WIDE_VIEWPORT = "web_engine.use_wide_viewport"
        private const val LOAD_WITH_OVERVIEW_MODE = "web_engine.load_with_overview_mode"
        private const val ENABLE_ZOOM = "web_engine.enable_zoom"
        private const val DISPLAY_ZOOM_CONTROLS = "web_engine.display_zoom_controls"
        private const val ALLOW_FILE_ACCESS_FROM_FILE_URLS = "web_engine.allow_file_access_from_file_urls"
        private const val ALLOW_UNIVERSAL_ACCESS_FROM_FILE_URLS = "web_engine.allow_universal_access_from_file_urls"
        private const val MEDIA_PLAYBACK_REQUIRES_USER_GESTURE = "web_engine.media_playback_requires_user_gesture"

        private const val LOCK_ON_LAUNCH = "web_lifecycle.lock_on_launch"
        private const val RESET_ON_LAUNCH = "web_lifecycle.reset_on_launch"
        private const val RESET_ON_INACTIVITY_SECONDS = "web_lifecycle.reset_on_inactivity_seconds"

        private const val THEME = "appearance.theme"
        private const val ADDRESS_BAR_MODE = "appearance.address_bar_mode"
        private const val WEBVIEW_INSET = "appearance.webview_inset"
        private const val BLOCKED_MESSAGE = "appearance.blocked_message"

        private const val KEEP_SCREEN_ON = "device.keep_screen_on"
        private const val DEVICE_ROTATION = "device.rotation"
        private const val ALLOW_CAMERA = "device.allow_camera"
        private const val ALLOW_MICROPHONE = "device.allow_microphone"
        private const val ALLOW_LOCATION = "device.allow_location"
        private const val CUSTOM_UNLOCK_SHORTCUT = "device.custom_unlock_shortcut"

        private const val JS_APPLY_APP_THEME = "js_scripts.apply_app_theme"
        private const val JS_APPLY_DESKTOP_VIEWPORT_WIDTH = "js_scripts.apply_desktop_viewport_width"
        private const val JS_CUSTOM_SCRIPT_ON_PAGE_START = "js_scripts.custom_script_on_start"
        private const val JS_CUSTOM_SCRIPT_ON_PAGE_FINISH = "js_scripts.custom_script_on_finish"
    }
}
