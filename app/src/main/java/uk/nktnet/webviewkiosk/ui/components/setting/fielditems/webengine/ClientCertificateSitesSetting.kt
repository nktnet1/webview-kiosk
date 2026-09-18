package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webengine

import android.app.Activity
import android.security.KeyChain
import android.webkit.WebView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.SystemSettings
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.managers.ToastManager
import uk.nktnet.webviewkiosk.ui.components.setting.fields.TextSettingFieldItem
import uk.nktnet.webviewkiosk.utils.webview.parseClientCertificateSiteRules
import uk.nktnet.webviewkiosk.utils.webview.validateClientCertificateSites

@Composable
fun ClientCertificateSitesSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val systemSettings = remember { SystemSettings(context) }
    val scope = rememberCoroutineScope()
    val settingKey = UserSettingsKeys.WebEngine.CLIENT_CERTIFICATE_SITES
    val certificateInstallerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            ToastManager.show(context, "Client certificate installed.")
        }
    }
    val certificateFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val pkcs12 = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            ?: error("Unable to open selected certificate file.")
                    }
                    certificateInstallerLauncher.launch(
                        KeyChain.createInstallIntent().apply {
                            putExtra(KeyChain.EXTRA_PKCS12, pkcs12)
                        }
                    )
                } catch (e: Exception) {
                    ToastManager.show(
                        context,
                        "Unable to install client certificate: ${e.message ?: "unknown error"}"
                    )
                }
            }
        }
    }

    TextSettingFieldItem(
        label = stringResource(R.string.web_engine_client_certificate_sites_title),
        infoText = """
            Configure sites that may use a client certificate for Mutual TLS,
            one exact host per line. An optional port may be specified; otherwise 443 is used.

            Format:
                host[:port][=KeyChain alias]

            If the KeyChain alias is omitted, Android will ask which installed client
            certificate to use on the first Mutual TLS request and remember that choice on this
            device. Use the "Install client certificate" button below to choose a PKCS#12
            (.p12/.pfx/.bin) client identity and pass it to Android's credential installer.
            Use "Clear certificate preferences" to reset locally remembered certificate choices
            as well as WebView's remembered accept/deny decisions.

            Supplying an alias is useful for managed devices where certificate access has
            already been granted to the app. In device/profile owner mode, an explicit alias
            can be approved automatically for this app's matching site.

            Wildcards and URL paths are not supported. Certificates are never exported with
            app settings; only an explicitly configured alias string is exported.
        """.trimIndent(),
        placeholder = """
            secure.example.com
            api.example.com:8443
            managed.example.com=work-client-cert
        """.trimIndent(),
        initialValue = userSettings.clientCertificateSites,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        isMultiline = true,
        validator = ::validateClientCertificateSites,
        validationMessage = "Use one unique host[:port][=KeyChain alias] entry per line.",
        descriptionFormatter = { value ->
            val count = parseClientCertificateSiteRules(value)?.size ?: 0
            when (count) {
                0 -> "(none)"
                1 -> "1 site"
                else -> "$count sites"
            }
        },
        onSave = { userSettings.clientCertificateSites = it },
        extraContent = { _, _ ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    onClick = {
                        systemSettings.clearClientCertificateAliases()
                        try {
                            WebView.clearClientCertPreferences {
                                ToastManager.show(
                                    context,
                                    "Client certificate preferences cleared."
                                )
                            }
                        } catch (_: Exception) {
                            ToastManager.show(
                                context,
                                "Unable to clear client certificate preferences."
                            )
                        }
                    }
                ) {
                    Text(
                        text = "Clear certificate preferences",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                Button(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    onClick = {
                        try {
                            certificateFileLauncher.launch(arrayOf("*/*"))
                        } catch (_: Exception) {
                            ToastManager.show(context, "Unable to open certificate file picker.")
                        }
                    }
                ) {
                    Text(
                        text = "Install client certificate",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    )
}
