package uk.nktnet.webviewkiosk.states

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uk.nktnet.webviewkiosk.utils.getIsLocked

class LockStateViewModel(application: Application) : AndroidViewModel(application) {
    private val _isLocked = mutableStateOf(false)
    val isLocked: State<Boolean> = _isLocked

    init {
        val activityManager = application.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        viewModelScope.launch {
            while (true) {
                _isLocked.value = getIsLocked(activityManager)
                delay(1000L)
            }
        }
    }
}
