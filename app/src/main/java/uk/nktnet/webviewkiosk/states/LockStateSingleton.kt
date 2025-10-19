package uk.nktnet.webviewkiosk.states

import android.app.ActivityManager
import android.app.Application
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uk.nktnet.webviewkiosk.utils.getIsLocked

object LockStateSingleton {
    private val _isLocked = mutableStateOf(false)
    val isLocked: State<Boolean> = _isLocked

    private var monitoringJob: Job? = null
    private var isStarted = false
    private lateinit var activityManager: ActivityManager

    fun startMonitoring(application: Application) {
        if (isStarted) {
            return
        }
        isStarted = true
        activityManager =
            application.getSystemService(Application.ACTIVITY_SERVICE) as ActivityManager

        monitoringJob = CoroutineScope(Dispatchers.Default).launch {
            while (true) {
                _isLocked.value = getIsLocked(activityManager)
                delay(1000L)
            }
        }
    }
}
