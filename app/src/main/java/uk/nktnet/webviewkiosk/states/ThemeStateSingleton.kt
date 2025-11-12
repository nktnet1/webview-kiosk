package uk.nktnet.webviewkiosk.states

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import uk.nktnet.webviewkiosk.config.option.ThemeOption

object ThemeStateSingleton {
    val currentTheme: MutableState<ThemeOption> = mutableStateOf(ThemeOption.SYSTEM)

    fun setTheme(theme: ThemeOption) {
        currentTheme.value = theme
    }
}
