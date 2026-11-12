package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem
import uk.nktnet.webviewkiosk.utils.safeStartActivity
import uk.nktnet.webviewkiosk.utils.webview.getNfcAdapterOrNull
import uk.nktnet.webviewkiosk.utils.webview.isNfcEnabled

@Composable
fun AllowNfcSetting() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val userSettings = UserSettings(context)
    val settingKey = UserSettingsKeys.Device.ALLOW_NFC

    var nfcSupported by remember { mutableStateOf(false) }
    var nfcEnabled by remember { mutableStateOf(false) }

    fun refreshNfcState() {
        val adapter = getNfcAdapterOrNull(context)
        nfcSupported = adapter != null
        nfcEnabled = adapter != null && isNfcEnabled(context)
    }

    LaunchedEffect(Unit) {
        refreshNfcState()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshNfcState()
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    BooleanSettingFieldItem(
        initialValue = userSettings.allowNfc,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.allowNfc = it },
        itemText = { value ->
            val supportText =
                if (nfcSupported) {
                    if (nfcEnabled) {
                        ""
                    } else {
                        context.getString(R.string.ui_nfc_disabled)
                    }
                } else {
                    context.getString(R.string.ui_nfc_not_supported)
                }
            if (value) {
                context.getString(R.string.ui_true_value, supportText)
            } else {
                context.getString(R.string.ui_false_value, supportText)
            }
        },
        extraContent = {
            if (nfcSupported) {
                Button(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    onClick = {
                        safeStartActivity(
                            context,
                            Intent(Settings.ACTION_NFC_SETTINGS),
                        )
                    },
                ) {
                    Text(
                        text = stringResource(R.string.ui_open_nfc_settings),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            } else {
                Text(
                    text =
                        stringResource(
                            R.string.ui_nfc_is_not_supported_on_this_device
                        ),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
    )
}
