package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device

import android.Manifest
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem
import uk.nktnet.webviewkiosk.utils.rememberPermissionState

@Composable
fun AllowLocationSetting() {
    val context = LocalContext.current
    val userSettings = UserSettings(context)
    val settingKey = UserSettingsKeys.Device.ALLOW_LOCATION

    var requestFine by remember { mutableStateOf(false) }

    val (fineState, requestFinePermission) = rememberPermissionState(
        Manifest.permission.ACCESS_FINE_LOCATION
    )
    val (coarseState, requestCoarsePermission) = rememberPermissionState(
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    val permissionState = if (requestFine) fineState else coarseState
    val requestPermission = if (requestFine) requestFinePermission else requestCoarsePermission

    val statusText = when {
        fineState.granted -> "(precise)"
        coarseState.granted -> "(approximate)"
        else -> "(no permission)"
    }

    BooleanSettingFieldItem(
        label = stringResource(R.string.device_allow_location_title),
        infoText = """
            When enabled, websites can request the device's location.

            You can choose to request precise location (FINE) or approximate location (COARSE).

            You will need to grant either ACCESS_FINE_LOCATION or ACCESS_COARSE_LOCATION,
            required for the WebView's GeolocationPermissions.
        """.trimIndent(),
        initialValue = userSettings.allowLocation,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.allowLocation = it },
        itemText = { v ->
            if (v) "True $statusText" else "False $statusText"
        },
        extraContent = {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Request precise location",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .weight(1f)
                            .combinedClickable(
                                onClick = { requestFine = !requestFine },
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            )
                    )
                    Switch(
                        checked = requestFine,
                        onCheckedChange = { requestFine = it },
                    )
                }

                Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    onClick = requestPermission
                ) {
                    val buttonText = if (permissionState.granted) {
                        "Disable in App Info"
                    } else if (!permissionState.shouldShowRationale) {
                        if (requestFine) {
                            "Request Fine Location Permission"
                        } else {
                            "Request Coarse Location Permission"
                        }
                    } else {
                        "Enable in App Info"
                    }

                    Text(
                        text = buttonText,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    )
}
