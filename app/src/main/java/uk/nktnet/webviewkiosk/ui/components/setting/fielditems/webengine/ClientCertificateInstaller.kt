package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webengine

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.security.KeyChain
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.nktnet.webviewkiosk.managers.ToastManager
import java.io.ByteArrayOutputStream

private const val MAX_PKCS12_FILE_BYTES = 10 * 1024 * 1024

/**
 * Owned by the activity rather than MutualTlsSetting's composition. Both activity-result
 * callbacks must stay registered while a picker or KeyChain's installer covers the settings UI.
 * Registration must happen before the activity reaches STARTED, with the same keys each time.
 */
internal class ClientCertificateInstaller(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    activityResultRegistry: ActivityResultRegistry,
    private val activityScope: CoroutineScope,
    private val readFile: suspend (Uri) -> ByteArray = { uri ->
        withContext(Dispatchers.IO) { readPkcs12File(context.applicationContext, uri) }
    },
    private val installIntent: (ByteArray) -> Intent = { pkcs12 ->
        KeyChain.createInstallIntent().apply { putExtra(KeyChain.EXTRA_PKCS12, pkcs12) }
    },
    private val notify: (String) -> Unit = { message ->
        ToastManager.show(context.applicationContext, message)
    },
) {
    private enum class Phase { IDLE, PICKING, READING, INSTALLING }

    private var phase = Phase.IDLE

    private val installerLauncher = activityResultRegistry.register(
        "clientCertificateInstaller.install",
        lifecycleOwner,
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        phase = Phase.IDLE
        if (result.resultCode == Activity.RESULT_OK) {
            notify("Client certificate installed.")
        }
    }

    private val fileLauncher = activityResultRegistry.register(
        "clientCertificateInstaller.file",
        lifecycleOwner,
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) {
            phase = Phase.IDLE
        } else if (phase != Phase.READING && phase != Phase.INSTALLING) {
            phase = Phase.READING
            activityScope.launch {
                try {
                    val pkcs12 = readFile(uri)
                    val intent = installIntent(pkcs12)
                    phase = Phase.INSTALLING
                    installerLauncher.launch(intent)
                } catch (e: CancellationException) {
                    // Activity destruction may cancel the handoff. That is not an install error.
                    phase = Phase.IDLE
                    throw e
                } catch (e: Exception) {
                    phase = Phase.IDLE
                    notify("Unable to install client certificate: ${e.message ?: "unknown error"}")
                }
            }
        }
    }

    fun openPicker() {
        if (phase != Phase.IDLE) return
        phase = Phase.PICKING
        try {
            fileLauncher.launch(arrayOf("*/*"))
        } catch (_: Exception) {
            phase = Phase.IDLE
            notify("Unable to open certificate file picker.")
        }
    }
}

internal fun readPkcs12File(context: Context, uri: Uri): ByteArray {
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
    } catch (e: CancellationException) {
        throw e
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
            if (bytesRead < 0) break

            totalBytes += bytesRead
            require(totalBytes <= MAX_PKCS12_FILE_BYTES) {
                "Client certificate file exceeds the 10 MiB limit."
            }
            output.write(buffer, 0, bytesRead)
        }

        output.toByteArray()
    } ?: error("Unable to open selected certificate file.")
}
