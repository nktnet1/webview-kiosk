package com.nktnet.webview_kiosk.config

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.core.content.edit
import com.nktnet.webview_kiosk.config.option.AddressBarOption
import com.nktnet.webview_kiosk.config.option.ThemeOption
import org.json.JSONObject

class UserSettings(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var homeUrl: String
        get() = prefs.getString(HOME_URL, null) ?: "https://google.com"
        set(value) = prefs.edit { putString(HOME_URL, value) }

    var websiteBlacklist: String
        get() = prefs.getString(WEBSITE_BLACKLIST, null) ?: ""
        set(value) = prefs.edit { putString(WEBSITE_BLACKLIST, value) }

    var websiteWhitelist: String
        get() = prefs.getString(WEBSITE_WHITELIST, null) ?: ""
        set(value) = prefs.edit { putString(WEBSITE_WHITELIST, value) }

    var allowRefresh: Boolean
        get() = prefs.getBoolean(ALLOW_REFRESH, true)
        set(value) = prefs.edit { putBoolean(ALLOW_REFRESH, value) }

    var allowBackwardsNavigation: Boolean
        get() = prefs.getBoolean(ALLOW_BACKWARDS_NAVIGATION, true)
        set(value) = prefs.edit { putBoolean(ALLOW_BACKWARDS_NAVIGATION, value) }

    var allowGoHome: Boolean
        get() = prefs.getBoolean(ALLOW_GO_HOME, true)
        set(value) = prefs.edit { putBoolean(ALLOW_GO_HOME, value) }

    var searchProviderUrl: String
        get() = prefs.getString(SEARCH_PROVIDER_URL, null) ?: "https://www.google.com/search?q="
        set(value) = prefs.edit { putString(SEARCH_PROVIDER_URL, value) }

    var blockedMessage: String
        get() = prefs.getString(BLOCKED_MESSAGE, null)
            ?: "This site is blocked by Webview Kiosk."
        set(value) = prefs.edit { putString(BLOCKED_MESSAGE, value) }

    var theme: ThemeOption
        get() = ThemeOption.fromString(prefs.getString(THEME, null))
        set(value) = prefs.edit { putString(THEME, value.name) }

    var addressBarMode: AddressBarOption
        get() = AddressBarOption.fromString(prefs.getString(ADDRESS_BAR_MODE, null))
        set(value) = prefs.edit { putString(ADDRESS_BAR_MODE, value.name) }

    var keepScreenOn: Boolean
        get() = prefs.getBoolean(KEEP_SCREEN_ON, false)
        set(value) = prefs.edit { putBoolean(KEEP_SCREEN_ON, value) }

    fun exportToBase64(): String {
        val json = JSONObject().apply {
            put(HOME_URL, homeUrl)
            put(WEBSITE_BLACKLIST, websiteBlacklist)
            put(WEBSITE_WHITELIST, websiteWhitelist)
            put(ALLOW_REFRESH, allowRefresh)
            put(ALLOW_BACKWARDS_NAVIGATION, allowBackwardsNavigation)
            put(ALLOW_GO_HOME, allowGoHome)
            put(SEARCH_PROVIDER_URL, searchProviderUrl)
            put(BLOCKED_MESSAGE, blockedMessage)
            put(THEME, theme.name)
            put(ADDRESS_BAR_MODE, addressBarMode.name)
            put(KEEP_SCREEN_ON, keepScreenOn)
        }
        return Base64.encodeToString(json.toString().toByteArray(), Base64.NO_WRAP)
    }

    fun importFromBase64(base64: String): Boolean {
        return try {
            val json = JSONObject(String(Base64.decode(base64, Base64.NO_WRAP)))
            homeUrl = json.optString(HOME_URL, homeUrl)
            websiteBlacklist = json.optString(WEBSITE_BLACKLIST, websiteBlacklist)
            websiteWhitelist = json.optString(WEBSITE_WHITELIST, websiteWhitelist)
            allowRefresh = json.optBoolean(ALLOW_REFRESH, allowRefresh)
            allowBackwardsNavigation = json.optBoolean(ALLOW_BACKWARDS_NAVIGATION, allowBackwardsNavigation)
            allowGoHome = json.optBoolean(ALLOW_GO_HOME, allowGoHome)
            searchProviderUrl = json.optString(SEARCH_PROVIDER_URL, searchProviderUrl)
            blockedMessage = json.optString(BLOCKED_MESSAGE, blockedMessage)
            theme = ThemeOption.fromString(json.optString(THEME, theme.name))
            addressBarMode = AddressBarOption.fromString(json.optString(ADDRESS_BAR_MODE, addressBarMode.name))
            keepScreenOn = json.optBoolean(KEEP_SCREEN_ON, keepScreenOn)
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
        private const val ALLOW_REFRESH = "web_browsing.allow_refresh"
        private const val ALLOW_BACKWARDS_NAVIGATION = "web_browsing.allow_backwards_navigation"
        private const val ALLOW_GO_HOME = "web_browsing.allow_go_home"
        private const val SEARCH_PROVIDER_URL = "web_browsing.search_provider_url"
        private const val BLOCKED_MESSAGE = "appearance.blocked_message"
        private const val THEME = "appearance.theme"
        private const val ADDRESS_BAR_MODE = "appearance.address_bar_mode"
        private const val KEEP_SCREEN_ON = "device.keep_screen_on"
    }
}
