package uk.nktnet.webviewkiosk.config

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.webkit.WebSettings
import androidx.core.content.edit
import org.json.JSONObject
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import uk.nktnet.webviewkiosk.config.option.*

class UserSettings(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun stringPref(key: String, default: String) = object : ReadWriteProperty<Any?, String> {
        override fun getValue(thisRef: Any?, property: KProperty<*>) =
            prefs.getString(key, null)?.takeIf { it.isNotBlank() } ?: default
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) =
            prefs.edit { putString(key, value) }
    }

    private fun stringPrefOptional(key: String) = object : ReadWriteProperty<Any?, String> {
        override fun getValue(thisRef: Any?, property: KProperty<*>) = prefs.getString(key, null) ?: ""
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) =
            prefs.edit { putString(key, value) }
    }

    private fun booleanPref(key: String, default: Boolean) = object : ReadWriteProperty<Any?, Boolean> {
        override fun getValue(thisRef: Any?, property: KProperty<*>) = prefs.getBoolean(key, default)
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) =
            prefs.edit { putBoolean(key, value) }
    }

    var homeUrl by stringPref(HOME_URL, Constants.WEBSITE_URL)
    var websiteBlacklist by stringPrefOptional(WEBSITE_BLACKLIST)
    var websiteWhitelist by stringPrefOptional(WEBSITE_WHITELIST)
    var websiteBookmarks by stringPrefOptional(WEBSITE_BOOKMARKS)

    var allowRefresh by booleanPref(ALLOW_REFRESH, true)
    var allowBackwardsNavigation by booleanPref(ALLOW_BACKWARDS_NAVIGATION, true)
    var allowGoHome by booleanPref(ALLOW_GO_HOME, true)
    var clearHistoryOnHome by booleanPref(CLEAR_HISTORY_ON_HOME, false)
    var allowHistoryAccess by booleanPref(ALLOW_HISTORY_ACCESS, true)
    var allowBookmarkAccess by booleanPref(ALLOW_BOOKMARK_ACCESS, true)
    var allowOtherUrlSchemes by booleanPref(ALLOW_OTHER_URL_SCHEMES, false)
    var searchProviderUrl by stringPref(SEARCH_PROVIDER_URL, Constants.DEFAULT_SEARCH_PROVIDER_URL)

    var enableJavaScript by booleanPref(ENABLE_JAVASCRIPT, true)
    var enableDomStorage by booleanPref(ENABLE_DOM_STORAGE, true)
    var acceptCookies by booleanPref(ACCEPT_COOKIES, true)
    var acceptThirdPartyCookies by booleanPref(ACCEPT_THIRD_PARTY_COOKIES, false)
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

    var userAgent by stringPrefOptional(USER_AGENT)
    var useWideViewPort by booleanPref(USE_WIDE_VIEWPORT, false)
    var loadWithOverviewMode by booleanPref(LOAD_WITH_OVERVIEW_MODE, false)
    var enableZoom by booleanPref(ENABLE_ZOOM, true)
    var displayZoomControls by booleanPref(DISPLAY_ZOOM_CONTROLS, false)

    var blockedMessage by stringPref(BLOCKED_MESSAGE, "This site is blocked by Webview Kiosk.")
    var theme: ThemeOption
        get() = ThemeOption.fromString(prefs.getString(THEME, null))
        set(value) = prefs.edit { putString(THEME, value.name) }

    var addressBarMode: AddressBarOption
        get() = AddressBarOption.fromString(prefs.getString(ADDRESS_BAR_MODE, null))
        set(value) = prefs.edit { putString(ADDRESS_BAR_MODE, value.name) }

    var webViewInset: WebViewInset
        get() = WebViewInset.fromString(prefs.getString(WEBVIEW_INSET, null))
        set(value) = prefs.edit { putString(WEBVIEW_INSET, value.name) }

    var keepScreenOn by booleanPref(KEEP_SCREEN_ON, false)
    var deviceRotation: DeviceRotationOption
        get() = DeviceRotationOption.fromString(prefs.getString(DEVICE_ROTATION, null))
        set(value) = prefs.edit { putString(DEVICE_ROTATION, value.degrees) }

    var applyAppTheme by booleanPref(JS_APPLY_APP_THEME, true)
    var applyDesktopViewport by booleanPref(JS_APPLY_DESKTOP_VIEWPORT, false)
    var customScriptOnPageStart by stringPrefOptional(JS_CUSTOM_SCRIPT_ON_PAGE_START)
    var customScriptOnPageFinish by stringPrefOptional(JS_CUSTOM_SCRIPT_ON_PAGE_FINISH)

    fun exportToBase64(): String {
        val json = JSONObject().apply {
            put(HOME_URL, homeUrl)
            put(WEBSITE_BLACKLIST, websiteBlacklist)
            put(WEBSITE_WHITELIST, websiteWhitelist)
            put(WEBSITE_BOOKMARKS, websiteBookmarks)
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
            put(BLOCKED_MESSAGE, blockedMessage)
            put(THEME, theme.name)
            put(ADDRESS_BAR_MODE, addressBarMode.name)
            put(WEBVIEW_INSET, webViewInset.name)
            put(KEEP_SCREEN_ON, keepScreenOn)
            put(DEVICE_ROTATION, deviceRotation.degrees)
            put(JS_APPLY_APP_THEME, applyAppTheme)
            put(JS_APPLY_DESKTOP_VIEWPORT, applyDesktopViewport)
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
            blockedMessage = json.optString(BLOCKED_MESSAGE, blockedMessage)
            theme = ThemeOption.fromString(json.optString(THEME, theme.name))
            addressBarMode = AddressBarOption.fromString(json.optString(ADDRESS_BAR_MODE, addressBarMode.name))
            webViewInset = WebViewInset.fromString(json.optString(WEBVIEW_INSET, webViewInset.name))
            keepScreenOn = json.optBoolean(KEEP_SCREEN_ON, keepScreenOn)
            deviceRotation = DeviceRotationOption.fromString(json.optString(DEVICE_ROTATION, deviceRotation.degrees))
            applyAppTheme = json.optBoolean(JS_APPLY_APP_THEME, applyAppTheme)
            applyDesktopViewport = json.optBoolean(JS_APPLY_DESKTOP_VIEWPORT, applyDesktopViewport)
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

        private const val BLOCKED_MESSAGE = "appearance.blocked_message"
        private const val THEME = "appearance.theme"
        private const val ADDRESS_BAR_MODE = "appearance.address_bar_mode"
        private const val WEBVIEW_INSET = "appearance.webview_inset"

        private const val KEEP_SCREEN_ON = "device.keep_screen_on"
        private const val DEVICE_ROTATION = "device.rotation"

        private const val JS_APPLY_APP_THEME = "js_scripts.apply_app_theme"
        private const val JS_APPLY_DESKTOP_VIEWPORT = "js_scripts.apply_desktop_viewport"
        private const val JS_CUSTOM_SCRIPT_ON_PAGE_START = "js_scripts.custom_script_on_start"
        private const val JS_CUSTOM_SCRIPT_ON_PAGE_FINISH = "js_scripts.custom_script_on_finish"
    }
}
