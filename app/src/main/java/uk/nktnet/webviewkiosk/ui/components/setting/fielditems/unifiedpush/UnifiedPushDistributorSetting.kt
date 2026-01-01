package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.unifiedpush

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.dialog.UnifiedPushSelectorDialog
import uk.nktnet.webviewkiosk.ui.components.setting.fields.TextSettingFieldItem

@Composable
fun UnifiedPushDistributorSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.UnifiedPush.DISTRIBUTOR
    val restricted = userSettings.isRestricted(settingKey)

    var showSelectDialog by remember { mutableStateOf(false) }

    TextSettingFieldItem(
        label = stringResource(R.string.unifiedpush_distributor_title),
        infoText = """
            A distributor app serves as the middle-man that receives the app notification
            from the push server and forwarding it to ${stringResource(R.string.app_name)}.
        """.trimIndent(),
        placeholder = "e.g. io.heckel.ntfy",
        initialValue = userSettings.unifiedPushDistributor,
        settingKey = settingKey,
        restricted = restricted,
        isMultiline = false,
        onSave = { userSettings.unifiedPushDistributor = it },
        extraContent = { setValue: (String) -> Unit ->
            if (restricted) {
                return@TextSettingFieldItem
            }
            Button(
                onClick = { showSelectDialog = true },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.buttonColors()
            ) {
                Text(
                    text = "Select a Distributor",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            UnifiedPushSelectorDialog(
                showDialog = showSelectDialog,
                onDismiss = { showSelectDialog = false },
                onSelectedApp = { app ->
                    setValue(app.packageName)
                    showSelectDialog = false
                }
            )
        }
    )
}
