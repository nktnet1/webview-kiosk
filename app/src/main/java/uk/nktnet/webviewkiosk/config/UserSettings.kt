package uk.nktnet.webviewkiosk.config

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.webkit.WebSettings
import androidx.core.content.edit
import org.json.JSONObject
import uk.nktnet.webviewkiosk.config.option.*

class UserSettings(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Web Content
    var homeUrl: String
        get() = prefs.getString(HOME_URL, null) ?: Constants.WEBSITE_URL
        set(value) = prefs.edit { putString(HOME_URL, value) }

    var websiteBlacklist: String
        get() = prefs.getString(WEBSITE_BLACKLIST, null) ?: ""
        set(value) = prefs.edit { putString(WEBSITE_BLACKLIST, value) }

    var websiteWhitelist: String
        get() = prefs.getString(WEBSITE_WHITELIST, null) ?: ""
        set(value) = prefs.edit { putString(WEBSITE_WHITELIST, value) }

    var websiteBookmarks: String
        get() = prefs.getString(WEBSITE_BOOKMARKS, null) ?: ""
        set(value) = prefs.edit { putString(WEBSITE_BOOKMARKS, value) }

    // Browsing
    var allowRefresh: Boolean
        get() = prefs.getBoolean(ALLOW_REFRESH, true)
        set(value) = prefs.edit { putBoolean(ALLOW_REFRESH, value) }

    var allowBackwardsNavigation: Boolean
        get() = prefs.getBoolean(ALLOW_BACKWARDS_NAVIGATION, true)
        set(value) = prefs.edit { putBoolean(ALLOW_BACKWARDS_NAVIGATION, value) }

    var allowGoHome: Boolean
        get() = prefs.getBoolean(ALLOW_GO_HOME, true)
        set(value) = prefs.edit { putBoolean(ALLOW_GO_HOME, value) }

    var clearHistoryOnHome: Boolean
        get() = prefs.getBoolean(CLEAR_HISTORY_ON_HOME, false)
        set(value) = prefs.edit { putBoolean(CLEAR_HISTORY_ON_HOME, value) }

    var allowHistoryAccess: Boolean
        get() = prefs.getBoolean(ALLOW_HISTORY_ACCESS, true)
        set(value) = prefs.edit { putBoolean(ALLOW_HISTORY_ACCESS, value) }

    var allowBookmarkAccess: Boolean
        get() = prefs.getBoolean(ALLOW_BOOKMARK_ACCESS, true)
        set(value) = prefs.edit { putBoolean(ALLOW_BOOKMARK_ACCESS, value) }

    var allowOtherUrlSchemes: Boolean
        get() = prefs.getBoolean(ALLOW_OTHER_URL_SCHEMES, false)
        set(value) = prefs.edit { putBoolean(ALLOW_OTHER_URL_SCHEMES, value) }

    var searchProviderUrl: String
        get() = prefs.getString(SEARCH_PROVIDER_URL, null) ?: Constants.DEFAULT_SEARCH_PROVIDER_URL
        set(value) = prefs.edit { putString(SEARCH_PROVIDER_URL, value) }

    // Web Engine
    var enableJavaScript: Boolean
        get() = prefs.getBoolean(ENABLE_JAVASCRIPT, true)
        set(value) = prefs.edit { putBoolean(ENABLE_JAVASCRIPT, value) }

    var enableDomStorage: Boolean
        get() = prefs.getBoolean(ENABLE_DOM_STORAGE, true)
        set(value) = prefs.edit { putBoolean(ENABLE_DOM_STORAGE, value) }

    var acceptCookies: Boolean
        get() = prefs.getBoolean(ACCEPT_COOKIES, true)
        set(value) = prefs.edit { putBoolean(ACCEPT_COOKIES, value) }

    var acceptThirdPartyCookies: Boolean
        get() = prefs.getBoolean(ACCEPT_THIRD_PARTY_COOKIES, false)
        set(value) = prefs.edit { putBoolean(ACCEPT_THIRD_PARTY_COOKIES, value) }

    var cacheMode: Int
        get() {
            val value = prefs.getInt(CACHE_MODE, WebSettings.LOAD_DEFAULT)
            return if (value in ValidCacheModes) value else WebSettings.LOAD_DEFAULT
        }
        set(value) {
            val validValue = if (value in ValidCacheModes) value else WebSettings.LOAD_DEFAULT
            prefs.edit { putInt(CACHE_MODE, validValue) }
        }

    // Appearance
    var blockedMessage: String
        get() = prefs.getString(BLOCKED_MESSAGE, null) ?: "This site is blocked by Webview Kiosk."
        set(value) = prefs.edit { putString(BLOCKED_MESSAGE, value) }

    var theme: ThemeOption
        get() = ThemeOption.fromString(prefs.getString(THEME, null))
        set(value) = prefs.edit { putString(THEME, value.name) }

    var addressBarMode: AddressBarOption
        get() = AddressBarOption.fromString(prefs.getString(ADDRESS_BAR_MODE, null))
        set(value) = prefs.edit { putString(ADDRESS_BAR_MODE, value.name) }

    var webViewInset: WebViewInset
        get() = WebViewInset.fromString(prefs.getString(WEBVIEW_INSET, null))
        set(value) = prefs.edit { putString(WEBVIEW_INSET, value.name) }

    // Device
    var keepScreenOn: Boolean
        get() = prefs.getBoolean(KEEP_SCREEN_ON, false)
        set(value) = prefs.edit { putBoolean(KEEP_SCREEN_ON, value) }

    var deviceRotation: DeviceRotationOption
        get() = DeviceRotationOption.fromString(prefs.getString(DEVICE_ROTATION, null))
        set(value) = prefs.edit { putString(DEVICE_ROTATION, value.degrees) }

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
            put(CACHE_MODE, cacheMode)
            put(BLOCKED_MESSAGE, blockedMessage)
            put(THEME, theme.name)
            put(ADDRESS_BAR_MODE, addressBarMode.name)
            put(WEBVIEW_INSET, webViewInset.name)
            put(KEEP_SCREEN_ON, keepScreenOn)
            put(DEVICE_ROTATION, deviceRotation.degrees)
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
            cacheMode = json.optInt(CACHE_MODE, cacheMode)
            blockedMessage = json.optString(BLOCKED_MESSAGE, blockedMessage)
            theme = ThemeOption.fromString(json.optString(THEME, theme.name))
            addressBarMode = AddressBarOption.fromString(json.optString(ADDRESS_BAR_MODE, addressBarMode.name))
            webViewInset = WebViewInset.fromString(json.optString(WEBVIEW_INSET, webViewInset.name))
            keepScreenOn = json.optBoolean(KEEP_SCREEN_ON, keepScreenOn)
            deviceRotation = DeviceRotationOption.fromString(json.optString(DEVICE_ROTATION, deviceRotation.degrees))
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
        private const val ACCEPT_COOKIES = "web_engine.accept_cookies"
        private const val ACCEPT_THIRD_PARTY_COOKIES = "web_engine.accept_third_party_cookies"

        private const val BLOCKED_MESSAGE = "appearance.blocked_message"
        private const val THEME = "appearance.theme"
        private const val ADDRESS_BAR_MODE = "appearance.address_bar_mode"
        private const val WEBVIEW_INSET = "appearance.webview_inset"

        private const val KEEP_SCREEN_ON = "device.keep_screen_on"
        private const val DEVICE_ROTATION = "device.rotation"
    }
}
