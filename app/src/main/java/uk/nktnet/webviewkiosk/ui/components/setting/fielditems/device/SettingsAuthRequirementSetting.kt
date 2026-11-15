package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.managers.AuthenticationManager
import uk.nktnet.webviewkiosk.ui.components.common.DropdownSelector
import uk.nktnet.webviewkiosk.ui.components.setting.dialog.GenericSettingFieldDialog
import uk.nktnet.webviewkiosk.ui.components.setting.fields.GenericSettingFieldItem
import uk.nktnet.webviewkiosk.ui.screens.settingDefinition

@Composable
fun SettingsAuthRequirementSetting() {
    val context = LocalContext.current
    val userSettings = remember(context) { UserSettings(context) }
    val settingKey = UserSettingsKeys.Device.REQUIRE_AUTH_FOR_SETTINGS
    val restricted = userSettings.isRestricted(settingKey)
    val label = stringResource(settingDefinition(settingKey).titleRes)
    val infoText = stringResource(settingDefinition(settingKey).descriptionRes)
    val promptTitle = stringResource(R.string.ui_authentication_required)
    val promptDescription =
        stringResource(R.string.ui_please_reauthenticate_to_change_settings_auth)
    val requireText =
        stringResource(
            R.string.option_display_unlock_auth_requirement_option_require
        )
    val offText =
        stringResource(R.string.option_display_unlock_auth_requirement_option_off)

    var currentValue by remember {
        mutableStateOf(userSettings.requireAuthForSettings)
    }
    var draftValue by remember { mutableStateOf(currentValue) }
    var showDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<Int?>(null) }
    var pendingRequest by remember { mutableStateOf<Any?>(null) }
    val latestPendingRequest by rememberUpdatedState(pendingRequest)

    // Do not apply a pending change after navigating away from this screen.
    DisposableEffect(Unit) {
        onDispose {
            AuthenticationManager.cancelReauthentication(latestPendingRequest)
        }
    }

    fun valueText(value: Boolean) =
        if (value) {
            requireText
        } else {
            offText
        }

    GenericSettingFieldItem(
        settingKey = settingKey,
        label = label,
        value = valueText(currentValue),
        restricted = restricted,
        onClick = {
            draftValue = userSettings.requireAuthForSettings
            currentValue = draftValue
            errorMessage = null
            showDialog = true
        },
    ) {
        Text(
            text = valueText(currentValue),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (showDialog) {
        GenericSettingFieldDialog(
            title = label,
            infoText = infoText,
            onDismiss = { showDialog = false },
            settingKey = settingKey,
            restricted = restricted,
            onSave = {
                if (!restricted) {
                    if (draftValue == userSettings.requireAuthForSettings) {
                        showDialog = false
                    } else {
                        val requestedValue = draftValue
                        when (
                            val request =
                                AuthenticationManager.requestReauthentication(
                                    title = promptTitle,
                                    description = promptDescription,
                                    onAuthenticated = {
                                        // Restrictions may have changed while the
                                        // credential prompt was on screen.
                                        if (!userSettings.isRestricted(settingKey)) {
                                            userSettings.requireAuthForSettings =
                                                requestedValue
                                        }
                                        currentValue =
                                            userSettings.requireAuthForSettings
                                        pendingRequest = null
                                    },
                                )
                        ) {
                            is AuthenticationManager.ReauthenticationRequest.Started -> {
                                pendingRequest = request.token
                                showDialog = false
                            }
                            AuthenticationManager.ReauthenticationRequest.Busy -> {
                                errorMessage =
                                    R.string.ui_authentication_already_in_progress
                            }
                            AuthenticationManager.ReauthenticationRequest
                                .NoCredentials -> {
                                errorMessage =
                                    R.string.ui_authentication_not_configured
                            }
                            AuthenticationManager.ReauthenticationRequest
                                .Unavailable -> {
                                errorMessage = R.string.ui_authentication_unavailable
                            }
                        }
                    }
                }
            },
        ) {
            DropdownSelector(
                options = listOf(false, true),
                selected = draftValue,
                enabled = !restricted,
                onSelectedChange = {
                    if (!restricted) {
                        draftValue = it
                        errorMessage = null
                    }
                },
            ) { value ->
                Text(
                    text = valueText(value),
                    style = MaterialTheme.typography.bodyMedium,
                    color =
                        if (value == draftValue) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
            errorMessage?.let { error ->
                Text(
                    text = stringResource(error),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        }
    }
}
