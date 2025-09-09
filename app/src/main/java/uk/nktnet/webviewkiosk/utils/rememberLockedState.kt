package uk.nktnet.webviewkiosk.utils

import android.app.ActivityManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

@Composable
fun rememberLockedState(): State<Boolean> {
    val context = LocalContext.current.applicationContext
    val activityManager = remember {
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    }
    val isLocked = remember { mutableStateOf(false) }

    LaunchedEffect(activityManager) {
        while (true) {
            isLocked.value = activityManager.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
            delay(1000L)
        }
    }
    return isLocked
}
