package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device

import android.content.Intent
import android.nfc.NfcAdapter
import android.provider.Settings
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem
import uk.nktnet.webviewkiosk.utils.safeStartActivity

@Composable
fun AllowNfcSetting() {
    val context = LocalContext.current
    val userSettings = UserSettings(context)
    val settingKey = UserSettingsKeys.Device.ALLOW_NFC

    val nfcAdapter = NfcAdapter.getDefaultAdapter(context)
    val nfcSupported = nfcAdapter != null
    val nfcEnabled = nfcAdapter?.isEnabled == true

    BooleanSettingFieldItem(
        label = stringResource(R.string.device_allow_nfc_title),
        infoText = """
            Set to true to enable NFC support via a Web NFC compatibility bridge.

            This allows websites to read and write NFC tags using the device's NFC hardware.

            Please note that this does not use Web NFC directly, as Web NFC is not yet
            available in Android System WebView. Instead, a compatibility layer is provided
            between native NFC and JavaScript. As a result, it is not subject to the same
            security limitations as Web NFC:

            - https://developer.chrome.com/docs/capabilities/nfc#security-and-permissions

            and can also operate on insecure (non-HTTPS) websites.
        """.trimIndent(),
        initialValue = userSettings.allowNfc,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.allowNfc = it },
        itemText = { value ->
            val supportText = if (nfcSupported) {
                if (nfcEnabled) {
                    "(NFC enabled)"
                } else {
                    "(NFC disabled)"
                }
            } else {
                "(NFC not supported)"
            }
            if (value) {
                "True $supportText"
            } else {
                "False $supportText"
            }
        },
        extraContent = {
            if (nfcSupported) {
                Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    onClick = {
                        safeStartActivity(context, Intent(Settings.ACTION_NFC_SETTINGS))
                    }
                ) {
                    Text(
                        text = "Open NFC Settings",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            } else {
                Text(
                    text = "NFC is not supported on this device.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    )
}
