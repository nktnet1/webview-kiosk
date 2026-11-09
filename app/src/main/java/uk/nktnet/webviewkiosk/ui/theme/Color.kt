package uk.nktnet.webviewkiosk.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// Extended warning role, distinct from error. The darker light-theme tone keeps
// small warning text readable on Material surfaces.
private val WarningLight = Color(0xFF8A4B00)
private val WarningDark = Color(0xFFFFB74D)

val ColorScheme.warning: Color
    get() =
        if (surface.luminance() < 0.5f) {
            WarningDark
        } else {
            WarningLight
        }
