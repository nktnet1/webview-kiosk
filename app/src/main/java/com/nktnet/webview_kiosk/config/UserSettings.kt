package com.nktnet.webview_kiosk.config

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.core.content.edit
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

    var blockedMessage: String
        get() = prefs.getString(BLOCKED_MESSAGE, null)
            ?: "This site is blocked by WebView Kiosk."
        set(value) = prefs.edit { putString(BLOCKED_MESSAGE, value) }

    var theme: Theme
        get() = Theme.fromString(prefs.getString(THEME, null))
        set(value) = prefs.edit { putString(THEME, value.name) }

    var addressBarMode: AddressBarMode
        get() = AddressBarMode.fromString(prefs.getString(ADDRESS_BAR_MODE, null))
        set(value) = prefs.edit { putString(ADDRESS_BAR_MODE, value.name) }

    fun exportToBase64(): String {
        val json = JSONObject().apply {
            put(HOME_URL, homeUrl)
            put(WEBSITE_BLACKLIST, websiteBlacklist)
            put(WEBSITE_WHITELIST, websiteWhitelist)
            put(BLOCKED_MESSAGE, blockedMessage)
            put(THEME, theme.name)
            put(ADDRESS_BAR_MODE, addressBarMode.name)
        }
        return Base64.encodeToString(json.toString().toByteArray(), Base64.NO_WRAP)
    }

    fun importFromBase64(base64: String): Boolean {
        return try {
            val json = JSONObject(String(Base64.decode(base64, Base64.NO_WRAP)))
            homeUrl = json.optString(HOME_URL, homeUrl)
            websiteBlacklist = json.optString(WEBSITE_BLACKLIST, websiteBlacklist)
            websiteWhitelist = json.optString(WEBSITE_WHITELIST, websiteWhitelist)
            blockedMessage = json.optString(BLOCKED_MESSAGE, blockedMessage)
            theme = Theme.fromString(json.optString(THEME, theme.name))
            addressBarMode = AddressBarMode.fromString(json.optString(ADDRESS_BAR_MODE, addressBarMode.name))
            true
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        private const val PREFS_NAME = "user_settings"
        private const val HOME_URL = "url-control.home_url"
        private const val WEBSITE_BLACKLIST = "url-control.website_blacklist"
        private const val WEBSITE_WHITELIST = "url-control.website_whitelist"
        private const val BLOCKED_MESSAGE = "appearance.blocked_message"
        private const val THEME = "appearance.theme"
        private const val ADDRESS_BAR_MODE = "appearance.address_bar_mode"
    }
}
enum class AddressBarMode {
    HIDDEN,
    HIDDEN_WHEN_LOCKED,
    SHOWN;
    companion object {
        fun fromString(value: String?): AddressBarMode {
            return entries.find { it.name == value } ?: HIDDEN_WHEN_LOCKED
        }
    }
}

enum class Theme {
    SYSTEM,
    DARK,
    LIGHT;
    companion object {
        fun fromString(value: String?): Theme {
            return entries.find { it.name == value } ?: SYSTEM
        }
    }
}