package com.nktnet.webview_kiosk.config

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class SystemSettings(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var lastUrl: String
        get() = prefs.getString(LAST_URL, "") ?: ""
        set(value) = prefs.edit { putString(LAST_URL, value) }

    var menuOffsetX: Float
        get() = prefs.getFloat(MENU_OFFSET_X, -1f)
        set(value) = prefs.edit { putFloat(MENU_OFFSET_X, value) }

    var menuOffsetY: Float
        get() = prefs.getFloat(MENU_OFFSET_Y, -1f)
        set(value) = prefs.edit { putFloat(MENU_OFFSET_Y, value) }

    companion object {
        private const val PREFS_NAME = "system_settings"
        private const val LAST_URL = "last_url"
        private const val MENU_OFFSET_X = "menu_offset_x"
        private const val MENU_OFFSET_Y = "menu_offset_y"
    }
}