package com.example.webview_locker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.webview_locker.ui.theme.WebviewlockerTheme
import com.example.webview_locker.ui.view.WebView

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WebviewlockerTheme {
                WebView()
            }
        }
    }
}
