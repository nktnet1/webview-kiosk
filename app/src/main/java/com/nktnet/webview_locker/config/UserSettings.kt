package com.nktnet.webview_locker.config

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class UserSettings(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var homeUrl: String
        get() = prefs.getString(HOME_URL, "https://duckduckgo.com") ?: "https://duckduckgo.com"
        set(value) = prefs.edit { putString(HOME_URL, value) }

    var websiteBlacklist: String
        get() = prefs.getString(WEBSITE_BLACKLIST, "") ?: ""
        set(value) = prefs.edit { putString(WEBSITE_BLACKLIST, value) }

    var websiteWhitelist: String
        get() = prefs.getString(WEBSITE_WHITELIST, "") ?: ""
        set(value) = prefs.edit { putString(WEBSITE_WHITELIST, value) }

    var blockedMessage: String
        get() = prefs.getString(BLOCKED_MESSAGE, "This site is blocked by WebView Locker.") ?: "This site is blocked by WebView Locker."
        set(value) = prefs.edit { putString(BLOCKED_MESSAGE, value) }

    companion object {
        private const val PREFS_NAME = "user_settings"
        private const val HOME_URL = "home_url"
        private const val WEBSITE_BLACKLIST = "website_blacklist"
        private const val WEBSITE_WHITELIST = "website_whitelist"
        private const val BLOCKED_MESSAGE = "blocked_message"
    }
}
