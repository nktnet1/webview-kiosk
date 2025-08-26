package com.nktnet.webview_kiosk.config

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class SystemSettings(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var menuOffsetX: Float
        get() = prefs.getFloat(MENU_OFFSET_X, -1f)
        set(value) = prefs.edit { putFloat(MENU_OFFSET_X, value) }

    var menuOffsetY: Float
        get() = prefs.getFloat(MENU_OFFSET_Y, -1f)
        set(value) = prefs.edit { putFloat(MENU_OFFSET_Y, value) }

    var historyStack: List<String>
        get() {
            val stack = prefs.getString(HISTORY_STACK, null)?.split("|") ?: emptyList()
            return stack
        }
        set(value) {
            prefs.edit { putString(HISTORY_STACK, value.joinToString("|")) }
        }

    var historyIndex: Int
        get() {
            val idx = prefs.getInt(HISTORY_INDEX, -1)
            return idx
        }
        set(value) {
            prefs.edit { putInt(HISTORY_INDEX, value) }
        }

    val lastUrl: String
        get() = historyStack.getOrNull(historyIndex) ?: ""

    companion object {
        private const val PREFS_NAME = "system_settings"
        private const val MENU_OFFSET_X = "menu_offset_x"
        private const val MENU_OFFSET_Y = "menu_offset_y"
        private const val HISTORY_STACK = "history_stack"
        private const val HISTORY_INDEX = "history_index"
    }
}
