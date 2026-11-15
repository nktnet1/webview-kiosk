package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.config.option.SettingsAuthRequirementOption
import uk.nktnet.webviewkiosk.managers.AuthenticationManager
import uk.nktnet.webviewkiosk.ui.components.setting.fields.DropdownSettingFieldItem
import uk.nktnet.webviewkiosk.ui.resources.displayLabelRes
import uk.nktnet.webviewkiosk.ui.screens.settingDefinition

@Composable
fun SettingsAuthRequirementSetting() {
    val context = LocalContext.current
    val userSettings = remember(context) { UserSettings(context) }
    val settingKey = UserSettingsKeys.Device.SETTINGS_AUTH_REQUIREMENT
    val promptTitle = stringResource(R.string.ui_authentication_required)
    val promptDescription =
        stringResource(R.string.ui_please_reauthenticate_to_change_settings_auth)
    // The live request token is available to dispose even before recomposition.
    val pendingRequest = remember { mutableStateOf<Any?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            AuthenticationManager.cancelReauthentication(pendingRequest.value)
        }
    }

    DropdownSettingFieldItem(
        infoText = stringResource(settingDefinition(settingKey).descriptionRes),
        options = SettingsAuthRequirementOption.entries,
        initialValue = userSettings.settingsAuthRequirement,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.settingsAuthRequirement = it },
        requestSaveApproval = { requestedValue, approve, reportError ->
            when {
                userSettings.isRestricted(settingKey) -> {
                    reportError(R.string.ui_this_setting_is_managed_by_your_it_admin)
                }
                requestedValue == userSettings.settingsAuthRequirement -> approve()
                else -> {
                    when (
                        val request =
                            AuthenticationManager.requestReauthentication(
                                title = promptTitle,
                                description = promptDescription,
                                onAuthenticated = {
                                    if (!userSettings.isRestricted(settingKey)) {
                                        approve()
                                    } else {
                                        reportError(
                                            R.string
                                                .ui_this_setting_is_managed_by_your_it_admin
                                        )
                                    }
                                    pendingRequest.value = null
                                },
                            )
                    ) {
                        is AuthenticationManager.ReauthenticationRequest.Started -> {
                            pendingRequest.value = request.token
                        }
                        AuthenticationManager.ReauthenticationRequest.Busy -> {
                            reportError(
                                R.string.ui_authentication_already_in_progress
                            )
                        }
                        AuthenticationManager.ReauthenticationRequest
                            .NoCredentials -> {
                            reportError(R.string.ui_authentication_not_configured)
                        }
                        AuthenticationManager.ReauthenticationRequest
                            .Unavailable -> {
                            reportError(R.string.ui_authentication_unavailable)
                        }
                    }
                }
            }
        },
        onDialogDismiss = {
            AuthenticationManager.cancelReauthentication(pendingRequest.value)
            pendingRequest.value = null
        },
        itemText = { stringResource(it.displayLabelRes) },
    )
}
