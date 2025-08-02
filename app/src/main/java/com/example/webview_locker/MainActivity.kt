package com.example.webview_locker

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.webview_locker.auth.BiometricPromptManager
import com.example.webview_locker.ui.theme.WebviewlockerTheme
import com.example.webview_locker.ui.view.SettingsScreen
import com.example.webview_locker.ui.view.WebView

class MainActivity : AppCompatActivity() {
    private val promptManager by lazy {
        BiometricPromptManager(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WebviewlockerTheme {
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
