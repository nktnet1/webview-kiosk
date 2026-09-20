package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.utils.rememberPermissionState

private const val ANDROID_17_API_LEVEL = 37
private const val ACCESS_LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

@Composable
fun AllowLocalNetworkAccessSetting() {
    if (Build.VERSION.SDK_INT < ANDROID_17_API_LEVEL) {
        return
    }

    val (permissionState, requestPermission) = rememberPermissionState(
        ACCESS_LOCAL_NETWORK_PERMISSION
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, start = 2.dp, end = 2.dp)
    ) {
        Text(
            text = stringResource(R.string.device_allow_local_network_access_title),
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = if (permissionState.granted) "Allowed" else "Not allowed",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            onClick = requestPermission
        ) {
            val buttonText = when {
                permissionState.granted -> "Disable in App Info"
                !permissionState.shouldShowRationale -> "Request Local Network Permission"
                else -> "Enable in App Info"
            }
            Text(
                text = buttonText,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(top = 8.dp),
            thickness = DividerDefaults.Thickness,
            color = DividerDefaults.color
        )
    }
}
