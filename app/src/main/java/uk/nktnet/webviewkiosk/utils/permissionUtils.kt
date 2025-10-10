package uk.nktnet.webviewkiosk.utils

import android.Manifest
import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class permissionUtils {
    @Composable
    fun rememberPermissionState(permission: String): PermissionState {
        val activity = LocalActivity.current

        var hasPermission by remember {
            mutableStateOf(
                ContextCompat.checkSelfPermission(
                    activity,
                    permission
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            )
        }

        var shouldShowRationale by remember {
            mutableStateOf(
                activity?.let {
                    ActivityCompat.shouldShowRequestPermissionRationale(it, permission)
                } ?: false
            )
        }

        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            hasPermission = granted
            shouldShowRationale = activity?.let {
                ActivityCompat.shouldShowRequestPermissionRationale(it, permission)
            } ?: false
        }

        return PermissionState(hasPermission, shouldShowRationale, launcher::launch)
    }

    class PermissionState(
        val hasPermission: Boolean,
        val shouldShowRationale: Boolean,
        private val requestPermissionInternal: (String) -> Unit
    ) {
        fun launchPermissionRequest(permission: String) {
            requestPermissionInternal(permission)
        }
    }
}
