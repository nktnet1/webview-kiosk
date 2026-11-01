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
import uk.nktnet.webviewkiosk.utils.isPackageInstalled

@Composable
fun UnifiedPushDistributorSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val settingKey = UserSettingsKeys.UnifiedPush.DISTRIBUTOR
    val restricted = userSettings.isRestricted(settingKey)

    var showSelectDialog by remember { mutableStateOf(false) }

    TextSettingFieldItem(
        label = stringResource(R.string.unifiedpush_distributor_title),
        infoText =
            stringResource(
                R.string.setting_info_unified_push_distributor,
                stringResource(R.string.app_name),
            ),
        placeholder = stringResource(R.string.ui_e_g_io_heckel_ntfy),
        initialValue = userSettings.unifiedPushDistributor,
        settingKey = settingKey,
        restricted = restricted,
        isMultiline = false,
        validator = {
            it.isEmpty() || isPackageInstalled(context, it)
        },
        validationMessage = stringResource(R.string.ui_package_is_not_installed),
        onSave = {
            userSettings.unifiedPushDistributor = it
        },
        extraContent = { _, setValue ->
            if (restricted) {
                return@TextSettingFieldItem
            }
            Button(
                onClick = { showSelectDialog = true },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.buttonColors(),
            ) {
                Text(
                    text = stringResource(R.string.ui_select_a_distributor),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            UnifiedPushSelectorDialog(
                showDialog = showSelectDialog,
                onDismiss = { showSelectDialog = false },
                onSelectedApp = { app ->
                    setValue(app.packageName)
                    showSelectDialog = false
                },
            )
        },
    )
}
