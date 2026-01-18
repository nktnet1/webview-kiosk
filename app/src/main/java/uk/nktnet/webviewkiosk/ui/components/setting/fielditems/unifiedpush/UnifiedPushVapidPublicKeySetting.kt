package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.unifiedpush

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.TextSettingFieldItem
import uk.nktnet.webviewkiosk.utils.isValidVapidPublicKey

@Composable
fun UnifiedPushVapidPublicKeySetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.UnifiedPush.MESSAGE_FOR_DISTRIBUTOR
    val restricted = userSettings.isRestricted(settingKey)

    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    TextSettingFieldItem(
        label = stringResource(R.string.unifiedpush_vapid_public_key_title),
        infoText = """
            VAPID public key (RFC8292), base64url, in uncompressed form (87 chars long).

            For more details, see:
            - https://www.rfc-editor.org/rfc/rfc8292
        """.trimIndent(),
        placeholder = "",
        validator = {
            it.isEmpty() || isValidVapidPublicKey(it)
        },
        validationMessage = "Invalid VAPID public key.",
        initialValue = userSettings.unifiedPushVapidPublicKey,
        settingKey = settingKey,
        restricted = restricted,
        isMultiline = false,
        onSave = {
            userSettings.unifiedPushVapidPublicKey = it
        },
        extraContent = { _, setValue ->
            if (restricted) {
                return@TextSettingFieldItem
            }
            Button(
                onClick = {
                    scope.launch {
                        val clipEntry = clipboard.getClipEntry()
                        val pasteData = clipEntry
                            ?.clipData?.getItemAt(0)?.text.toString()
                        setValue(pasteData)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.buttonColors()
            ) {
                Text(
                    text = "Paste Clipboard",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    )
}
