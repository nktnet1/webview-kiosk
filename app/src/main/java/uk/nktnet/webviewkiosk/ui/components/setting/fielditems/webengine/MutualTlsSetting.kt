package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webengine

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
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
import uk.nktnet.webviewkiosk.utils.webview.parseMutualTlsRules
import uk.nktnet.webviewkiosk.utils.webview.validateMutualTls
import java.io.ByteArrayOutputStream

private const val MAX_PKCS12_FILE_BYTES = 10 * 1024 * 1024

private fun readPkcs12File(context: Context, uri: Uri): ByteArray {
    val contentResolver = context.contentResolver
    val reportedSize = try {
        contentResolver.query(
            uri,
            arrayOf(OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (sizeIndex >= 0 && cursor.moveToFirst() && !cursor.isNull(sizeIndex)) {
                cursor.getLong(sizeIndex).takeIf { it >= 0 }
            } else {
                null
            }
        }
    } catch (_: Exception) {
        null
    }

    require(reportedSize == null || reportedSize <= MAX_PKCS12_FILE_BYTES) {
        "Client certificate file exceeds the 10 MiB limit."
    }

    return contentResolver.openInputStream(uri)?.use { input ->
        val output = ByteArrayOutputStream(
            reportedSize?.coerceAtMost(MAX_PKCS12_FILE_BYTES.toLong())?.toInt() ?: 8192
        )
        val buffer = ByteArray(8192)
        var totalBytes = 0

        while (true) {
            val bytesRead = input.read(buffer)
            if (bytesRead < 0) {
                break
            }

            totalBytes += bytesRead
            require(totalBytes <= MAX_PKCS12_FILE_BYTES) {
                "Client certificate file exceeds the 10 MiB limit."
            }
            output.write(buffer, 0, bytesRead)
        }

        output.toByteArray()
    } ?: error("Unable to open selected certificate file.")
}

@Composable
fun MutualTlsSetting() {
    val context = LocalContext.current
    val userSettings = remember { UserSettings(context) }
    val systemSettings = remember { SystemSettings(context) }
    val scope = rememberCoroutineScope()
    val settingKey = UserSettingsKeys.WebEngine.MUTUAL_TLS
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
                        readPkcs12File(context, uri)
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
        label = stringResource(R.string.web_engine_mutual_tls_title),
        infoText = """
            Configure exact sites that may use a client certificate for Mutual TLS (mTLS).

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
        """.trimIndent(),
        placeholder = """
            secure.example.com
            api.example.com:8443
            managed.example.com=work-client-cert
        """.trimIndent(),
        initialValue = userSettings.mutualTls,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        isMultiline = true,
        validator = ::validateMutualTls,
        validationMessage = "Use one unique host[:port][=KeyChain alias] entry per line.",
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
                        systemSettings.clearMutualTlsAliases()
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
