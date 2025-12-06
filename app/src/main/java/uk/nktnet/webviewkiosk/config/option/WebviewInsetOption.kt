package uk.nktnet.webviewkiosk.config.option

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
    None("None"),
    StatusBars("Status Bars"),
    NavigationBars("Navigation Bars"),
    SystemBars("System Bars"),
    DisplayCutout("Display Cutout"),
    SafeDrawing("Safe Drawing"),
    SafeGestures("Safe Gestures"),
    SafeContent("Safe Content");

    @Composable
    fun toWindowInsets(): WindowInsets = when (this) {
        None -> WindowInsets()
        StatusBars -> WindowInsets.statusBars
        NavigationBars -> WindowInsets.navigationBars
        SystemBars -> WindowInsets.systemBars
        DisplayCutout -> WindowInsets.displayCutout
        SafeDrawing -> WindowInsets.safeDrawing
        SafeGestures -> WindowInsets.safeGestures
        SafeContent -> WindowInsets.safeContent
    }

    companion object {
        fun fromString(value: String?): WebViewInsetOption =
            entries.find {
                it.name.equals(value, ignoreCase = true)
                || it.label.equals(value, ignoreCase = true)
            } ?: SystemBars
    }
}
