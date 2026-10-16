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
        label = stringResource(R.string.web_engine_mutual_tls_title),
        infoText =
            """
            Configure exact sites that may use a client certificate for Mutual TLS (mTLS).

            The client certificate installer accepts PKCS#12 credentials with a private key.
            It does not install a root CA for HTTPS server verification. To trust a private
            HTTPS server, install its CA certificate through Android Settings. WebView Kiosk
            trusts Android-installed user CAs throughout the app, not only for these sites.

            Add one entry per line in the format:

                host[:port][=KeyChain alias]

            The port defaults to `443`. Wildcards, schemes, paths, query strings and
            fragments are not supported. This prevents a client certificate from being
            sent to a site that was not explicitly configured.

            An alias can be provided explicitly for managed devices where the app already
            has access to that KeyChain entry.

            When ${stringResource(R.string.app_name)} is the device/profile owner,
            an explicitly configured alias is also returned from Android's private-key selection
            callback for ${stringResource(R.string.app_name)}'s own matching site, allowing
            Android to grant access without showing the certificate picker.
        """
                .trimIndent(),
        placeholder =
            """
            secure.example.com
            api.example.com:8443
            managed.example.com=work-client-cert
            """
                .trimIndent(),
        initialValue = userSettings.mutualTls,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        isMultiline = true,
        validator = ::validateMutualTls,
        validationMessage =
            "Use one unique host[:port][=KeyChain alias] entry per line.",
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
                                    "Client certificate preferences cleared.",
                                )
                            }
                        } catch (_: Exception) {
                            ToastManager.show(
                                context,
                                "Unable to clear client certificate preferences.",
                            )
                        }
                    },
                ) {
                    Text(
                        text = "Clear certificate preferences",
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
                                    "Unable to open certificate file picker.",
                                )
                            }
                        } catch (_: Exception) {
                            ToastManager.show(
                                context,
                                "Unable to open certificate file picker.",
                            )
                        }
                    },
                ) {
                    Text(
                        text = "Install client certificate",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        },
    )
}
