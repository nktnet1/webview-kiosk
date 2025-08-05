package com.nktnet.webview_kiosk

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nktnet.webview_kiosk.auth.BiometricPromptManager
import com.nktnet.webview_kiosk.ui.theme.WebviewKioskTheme
import com.nktnet.webview_kiosk.ui.view.SettingsScreen
import com.nktnet.webview_kiosk.ui.view.WebView

class MainActivity : AppCompatActivity() {
    private val promptManager by lazy {
        BiometricPromptManager(this)
    }

    override fun onStop() {
        super.onStop()
        promptManager.resetAuthentication()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WebviewKioskTheme {
                val navController = rememberNavController()

                NavHost(navController, startDestination = "webview") {
                    composable("webview") {
                        WebView(onOpenSettings = { navController.navigate("settings") })
                    }
                    composable("settings") {
                        SettingsScreen(onClose = { navController.popBackStack() }, promptManager)
                    }
                }
            }
        }
    }
}
