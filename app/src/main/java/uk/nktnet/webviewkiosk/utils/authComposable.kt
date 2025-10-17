package com.nktnet.webview_kiosk.utils

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.nktnet.webview_kiosk.ui.components.auth.RequireAuthWrapper

inline fun NavGraphBuilder.authComposable(
    route: String,
    crossinline content: @Composable () -> Unit
) {
    composable(route) {
        RequireAuthWrapper {
            content()
        }
    }
}
