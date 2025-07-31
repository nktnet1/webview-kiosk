package com.example.webview_locker.ui.view

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.webview_locker.config.UserSettingsKeys
import androidx.core.content.edit

@Composable
fun SettingsScreen(onSave: () -> Unit) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences(UserSettingsKeys.PREFS_NAME, Context.MODE_PRIVATE)
    var url by remember { mutableStateOf(prefs.getString(UserSettingsKeys.HOME_URL, "") ?: "") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Configure Start URL", style = MaterialTheme.typography.headlineSmall)

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Start URL") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            prefs.edit { putString(UserSettingsKeys.HOME_URL, url) }
            onSave()
        }) {
            Text("Save")
        }
    }
}
