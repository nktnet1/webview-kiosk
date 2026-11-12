package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webengine

import android.webkit.WebView
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import uk.nktnet.webviewkiosk.MainActivity
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.SystemSettings
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.managers.ToastManager
import uk.nktnet.webviewkiosk.ui.components.setting.fields.TextSettingFieldItem
import uk.nktnet.webviewkiosk.ui.screens.settingDefinition
import uk.nktnet.webviewkiosk.utils.webview.parseMutualTlsRules
import uk.nktnet.webviewkiosk.utils.webview.validateMutualTls

@Composable
fun MutualTlsSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val systemSettings = remember { SystemSettings(context) }
    val certificateInstallerActivity = LocalActivity.current as? MainActivity
    val settingKey = UserSettingsKeys.WebEngine.MUTUAL_TLS
    TextSettingFieldItem(
        infoText =
            stringResource(
                settingDefinition(settingKey).descriptionRes,
                stringResource(R.string.app_name),
            ),
        placeholder = stringResource(R.string.ui_mutual_tls_host_placeholder),
        initialValue = userSettings.mutualTls,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        isMultiline = true,
        validator = ::validateMutualTls,
        validationMessage =
            stringResource(
                R.string.ui_use_one_unique_host_port_keychain_alias_entry_per_line
            ),
        descriptionFormatter = { value ->
            val count = parseMutualTlsRules(value)?.size ?: 0
            when (count) {
                0 -> "(none)"
                1 -> "1 site"
                else -> "$count sites"
            }
        },
        onSave = { userSettings.mutualTls = it },
        extraContent = { _, _ ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    contentPadding =
                        PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    onClick = {
                        systemSettings.clearMutualTlsAliases()
                        try {
                            WebView.clearClientCertPreferences {
                                ToastManager.show(
                                    context,
                                    context.getString(
                                        R.string
                                            .ui_client_certificate_preferences_cleared
                                    ),
                                )
                            }
                        } catch (_: Exception) {
                            ToastManager.show(
                                context,
                                context.getString(
                                    R.string
                                        .ui_unable_to_clear_client_certificate_preferences
                                ),
                            )
                        }
                    },
                ) {
                    Text(
                        text =
                            stringResource(
                                R.string.ui_clear_certificate_preferences
                            ),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }

                Button(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding =
                        PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    onClick = {
                        try {
                            if (certificateInstallerActivity != null) {
                                certificateInstallerActivity
                                    .openClientCertificatePicker()
                            } else {
                                ToastManager.show(
                                    context,
                                    context.getString(
                                        R.string
                                            .ui_unable_to_open_certificate_file_picker
                                    ),
                                )
                            }
                        } catch (_: Exception) {
                            ToastManager.show(
                                context,
                                context.getString(
                                    R.string
                                        .ui_unable_to_open_certificate_file_picker
                                ),
                            )
                        }
                    },
                ) {
                    Text(
                        text =
                            stringResource(R.string.ui_install_client_certificate),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        },
    )
}
