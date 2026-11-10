package uk.nktnet.webviewkiosk.activation

import android.annotation.SuppressLint
import android.app.admin.IDevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Process
import androidx.annotation.Keep
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.system.exitProcess
import uk.nktnet.webviewkiosk.BuildConfig
import uk.nktnet.webviewkiosk.WebviewKioskAdminReceiver

/** Instantiated by Shizuku, never registered as an exported Android service. */
@Keep
class ActivationService : IActivationService.Stub() {
    @Synchronized
    override fun activate(profileOwner: Boolean, userId: Int): Bundle {
        val result =
            if (Process.myUid() !in intArrayOf(0, 2000)) {
                ActivationResult(
                    false,
                    "Activation requires shell or root privileges",
                )
            } else {
                OwnerActivation(
                        Build.VERSION.SDK_INT,
                        ComponentName(
                                BuildConfig.APPLICATION_ID,
                                WebviewKioskAdminReceiver::class.java.name,
                            )
                            .flattenToString(),
                        ::runActivationCommand,
                        ::refreshSetupState,
                    )
                    .activate(profileOwner, userId)
            }
        return Bundle().apply {
            putBoolean("success", result.success)
            putString("output", result.output)
            putString("message", result.output)
            putString("diagnostics", result.diagnostics)
        }
    }

    // Shizuku's reserved destroy transaction. Serialise it with activation so
    // unbinding cannot interrupt restoration of the original setup setting.
    @Synchronized
    override fun destroy() {
        exitProcess(0)
    }

    @SuppressLint("PrivateApi")
    private fun refreshSetupState(userId: Int) {
        // The no-argument API on Android 8–11 is a no-op on production builds.
        // https://github.com/BinTianqi/OwnDroid/issues/371
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return
        }
        val binder =
            Class.forName("android.os.ServiceManager")
                .getMethod("getService", String::class.java)
                .invoke(null, Context.DEVICE_POLICY_SERVICE) as IBinder
        val dpm = IDevicePolicyManager.Stub.asInterface(binder)
        dpm.forceUpdateUserSetupComplete(userId)
    }
}

/** Drain output while waiting, cap Binder payloads, and bound each shell command. */
internal fun runActivationCommand(arguments: List<String>): CommandResult {
    val process = ProcessBuilder(arguments).redirectErrorStream(true).start()
    val workers = Executors.newFixedThreadPool(2)
    try {
        val output =
            workers.submit<String> {
                val saved = ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                process.inputStream.use { input ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) {
                            break
                        }
                        val retain = minOf(read, 32_768 - saved.size())
                        if (retain > 0) {
                            saved.write(buffer, 0, retain)
                        }
                    }
                }
                saved.toString("UTF-8")
            }
        // Future.get also works on API 23–25, before Process.waitFor(timeout).
        val exit =
            workers.submit<Int> { process.waitFor() }.get(30, TimeUnit.SECONDS)
        return CommandResult(exit, output.get(2, TimeUnit.SECONDS))
    } catch (e: TimeoutException) {
        throw IllegalStateException("${arguments.first()} command timed out", e)
    } finally {
        process.destroy()
        runCatching { process.inputStream.close() }
        runCatching { process.errorStream.close() }
        runCatching { process.outputStream.close() }
        workers.shutdownNow()
    }
}
