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
fun rememberPinnedState(): State<Boolean> {
    val context = LocalContext.current.applicationContext
    val activityManager = remember {
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    }
    val pinned = remember { mutableStateOf(false) }

    LaunchedEffect(activityManager) {
        while (true) {
            val isLocked = activityManager.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
            pinned.value = isLocked
            delay(1000L)
        }
    }
    return pinned
}
