package com.nktnet.webview_kiosk.config

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.*
import com.nktnet.webview_kiosk.utils.booleanPref
import com.nktnet.webview_kiosk.utils.floatPref
import com.nktnet.webview_kiosk.utils.intPref
import com.nktnet.webview_kiosk.utils.stringPrefOptional

@Serializable
data class HistoryEntry(
    val id: String = UUID.randomUUID().toString(),
    val url: String,
    val visitedAt: Long = System.currentTimeMillis()
)

class SystemSettings(val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    var menuOffsetX: Float by floatPref(prefs = prefs, key = MENU_OFFSET_X, default = -1f)
    var menuOffsetY: Float by floatPref(prefs = prefs, key = MENU_OFFSET_Y, default = -1f)

    var historyStack: List<HistoryEntry>
        get() {
            val raw = prefs.getString(HISTORY_STACK, null) ?: return emptyList()
            return try {
                json.decodeFromString(raw)
            } catch (_: Exception) {
                emptyList()
            }
        }
        set(value) {
            val serialized = json.encodeToString(value)
            prefs.edit { putString(HISTORY_STACK, serialized) }
        }

    var historyIndex: Int by intPref(prefs = prefs, key = HISTORY_INDEX, default = -1)

    val currentUrl: String
        get() = historyStack.getOrNull(historyIndex)?.url ?: ""

    var intentUrl: String by stringPrefOptional(prefs = prefs, key = INTENT_URL)

    var isFreshLaunch: Boolean by booleanPref(prefs = prefs, key = IS_FRESH_LAUNCH, default = true)

    fun clearHistory() {
        historyStack = emptyList()
        historyIndex = -1
    }

    companion object {
        private const val PREFS_NAME = "system_settings"
        private const val MENU_OFFSET_X = "menu_offset_x"
        private const val MENU_OFFSET_Y = "menu_offset_y"
        private const val HISTORY_STACK = "history_stack"
        private const val HISTORY_INDEX = "history_index"
        private const val INTENT_URL = "intent_url"
        private const val IS_FRESH_LAUNCH = "is_fresh_launch"
    }
}
