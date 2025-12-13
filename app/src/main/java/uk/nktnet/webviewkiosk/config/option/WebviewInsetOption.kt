package com.nktnet.webview_kiosk.config.option

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeGestures
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable

enum class WebViewInsetOption(val label: String) {
    NONE("None"),
    STATUS_BARS("Status Bars"),
    NAVIGATION_BARS("Navigation Bars"),
    SYSTEM_BARS("System Bars"),
    DISPLAY_CUTOUT("Display Cutout"),
    SAFE_DRAWING("Safe Drawing"),
    SAFE_GESTURES("Safe Gestures"),
    SAFE_CONTENT("Safe Content");

    @Composable
    fun toWindowInsets(): WindowInsets = when (this) {
        NONE -> WindowInsets()
        STATUS_BARS -> WindowInsets.statusBars
        NAVIGATION_BARS -> WindowInsets.navigationBars
        SYSTEM_BARS -> WindowInsets.systemBars
        DISPLAY_CUTOUT -> WindowInsets.displayCutout
        SAFE_DRAWING -> WindowInsets.safeDrawing
        SAFE_GESTURES -> WindowInsets.safeGestures
        SAFE_CONTENT -> WindowInsets.safeContent
    }

    companion object {
        fun fromString(value: String?): WebViewInsetOption =
            entries.find {
                it.name.equals(value, ignoreCase = true)
                || it.label.equals(value, ignoreCase = true)
            } ?: SYSTEM_BARS
    }
}
