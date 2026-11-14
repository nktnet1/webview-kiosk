package uk.nktnet.webviewkiosk.states

import android.app.ActivityManager
import android.app.Application
import android.os.Build
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import uk.nktnet.webviewkiosk.config.data.DeviceOwnerMode
import uk.nktnet.webviewkiosk.config.data.ManagementOwner
import uk.nktnet.webviewkiosk.config.option.LockStateType
import uk.nktnet.webviewkiosk.managers.DeviceOwnerManager
import uk.nktnet.webviewkiosk.managers.MqttManager
import uk.nktnet.webviewkiosk.utils.getIsLocked

object LockStateSingleton {
    private val _isLocked = mutableStateOf(false)
    val isLocked: State<Boolean> = _isLocked

    private var monitoringJob: Job? = null
    private var isStarted = false
    private lateinit var activityManager: ActivityManager

    // Dhizuku requires API 23+. Do not load its listener on Android 5.x.
    private val dhizukuMonitor by lazy {
        DhizukuLockTaskModeMonitor(onChanged = ::onLockTaskModeChanged)
    }

    /** Called by the native DPC receiver or Dhizuku's forwarded DPC callback. */
    fun onLockTaskModeChanged(entering: Boolean) {
        setLockState(
            entering,
            if (entering) {
                LockStateType.LOCK_TASK
            } else {
                LockStateType.NONE
            },
        )
    }

    @Synchronized
    private fun setLockState(locked: Boolean, type: LockStateType) {
        if (_isLocked.value == locked) {
            return
        }
        _isLocked.value = locked
        if (MqttManager.isConnected()) {
            if (locked) {
                MqttManager.publishLockEvent(type)
            } else {
                MqttManager.publishUnlockEvent()
            }
        }
    }

    internal fun refreshFromActivityManager(manager: ActivityManager) {
        setLockState(
            getIsLocked(manager),
            LockStateType.fromActivityManager(manager),
        )
    }

    fun startMonitoring(application: Application) {
        if (isStarted) {
            return
        }
        isStarted = true
        activityManager =
            application.getSystemService(Application.ACTIVITY_SERVICE)
                as ActivityManager

        val scope = CoroutineScope(Dispatchers.Default)
        monitoringJob = scope.launch {
            // Keep this independent of Dhizuku registration and callbacks.
            while (isActive) {
                refreshFromActivityManager(activityManager)
                delay(1000.milliseconds)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            scope.launch(Dispatchers.IO) {
                while (isActive) {
                    val status = DeviceOwnerManager.status.value
                    dhizukuMonitor.refresh(
                        status.mode == DeviceOwnerMode.Dhizuku &&
                            (status.owner == ManagementOwner.DeviceOwner ||
                                status.owner == ManagementOwner.ProfileOwner)
                    )
                    delay(1000.milliseconds)
                }
            }
        }
    }
}
