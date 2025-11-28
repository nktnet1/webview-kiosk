package com.nktnet.webview_kiosk.states

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.nktnet.webview_kiosk.config.option.ThemeOption

object ThemeStateSingleton {
    val currentTheme: MutableState<ThemeOption> = mutableStateOf(ThemeOption.SYSTEM)

    fun setTheme(theme: ThemeOption) {
        currentTheme.value = theme
    }
}
