package uk.nktnet.webviewkiosk.activation

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.Process
import android.util.Log
import androidx.annotation.RequiresApi
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import rikka.shizuku.Shizuku
import uk.nktnet.webviewkiosk.BuildConfig
import uk.nktnet.webviewkiosk.managers.DeviceOwnerManager
import uk.nktnet.webviewkiosk.utils.setupLockTaskPackage

internal interface ShizukuAccess {
    fun checkAvailable()

    fun permissionGranted(): Boolean

    fun permissionDeniedPermanently(): Boolean

    fun addPermissionListener(listener: Shizuku.OnRequestPermissionResultListener)

    fun removePermissionListener(listener: Shizuku.OnRequestPermissionResultListener)

    fun requestPermission(code: Int)

    fun addDeathListener(listener: Shizuku.OnBinderDeadListener)

    fun removeDeathListener(listener: Shizuku.OnBinderDeadListener)

    fun bind(args: Shizuku.UserServiceArgs, connection: ServiceConnection)

    fun unbind(args: Shizuku.UserServiceArgs, connection: ServiceConnection)
}

@RequiresApi(Build.VERSION_CODES.M)
private object PlatformShizukuAccess : ShizukuAccess {
    override fun checkAvailable() {
        checkActivation(Shizuku.pingBinder()) {
            "Shizuku is not running. Open Shizuku, start its service, then try again."
        }
        checkActivation(Shizuku.getVersion() >= 11) {
            "Update Shizuku, restart its service, then try again."
        }
    }

    override fun permissionGranted() =
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED

    override fun permissionDeniedPermanently() =
        Shizuku.shouldShowRequestPermissionRationale()

    override fun addPermissionListener(
        listener: Shizuku.OnRequestPermissionResultListener
    ) = Shizuku.addRequestPermissionResultListener(listener)

    override fun removePermissionListener(
        listener: Shizuku.OnRequestPermissionResultListener
    ) {
        Shizuku.removeRequestPermissionResultListener(listener)
    }

    override fun requestPermission(code: Int) = Shizuku.requestPermission(code)

    override fun addDeathListener(listener: Shizuku.OnBinderDeadListener) =
        Shizuku.addBinderDeadListener(listener)

    override fun removeDeathListener(listener: Shizuku.OnBinderDeadListener) {
        Shizuku.removeBinderDeadListener(listener)
    }

    override fun bind(args: Shizuku.UserServiceArgs, connection: ServiceConnection) =
        Shizuku.bindUserService(args, connection)

    override fun unbind(
        args: Shizuku.UserServiceArgs,
        connection: ServiceConnection,
    ) = Shizuku.unbindUserService(args, connection, true)
}

/** One activation at a time, including permission prompts and setup restoration. */
internal object ShizukuActivation {
    private val mutex = Mutex()
    private val permissionRequests = AtomicInteger(7241)

    @RequiresApi(Build.VERSION_CODES.M)
    suspend fun activate(
        context: Context,
        profileOwner: Boolean,
        access: ShizukuAccess = PlatformShizukuAccess,
    ): ActivationResult = mutex.withLock {
        withContext(Dispatchers.Main.immediate) {
            try {
                checkActivation(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    "Shizuku activation requires Android 6 or later."
                }
                val dpm =
                    context.getSystemService(Context.DEVICE_POLICY_SERVICE)
                        as DevicePolicyManager
                checkActivation(
                    !dpm.isDeviceOwnerApp(context.packageName) &&
                        !dpm.isProfileOwnerApp(context.packageName)
                ) {
                    "This app already has native ownership. Check Device Policies " +
                        "before trying to activate another role."
                }
                access.checkAvailable()
                awaitPermission(access)
                val result = activateService(context, profileOwner, access)
                // Activation grants native ownership, not a Shizuku policy mode.
                DeviceOwnerManager.init(context)
                val verified =
                    if (profileOwner) {
                        dpm.isProfileOwnerApp(context.packageName)
                    } else {
                        dpm.isDeviceOwnerApp(context.packageName)
                    }
                if (result.success && !verified) {
                    result.copy(
                        success = false,
                        output =
                            result.output +
                                "\n\nAndroid did not confirm the requested ownership. " +
                                "Check Device Policies and try again.",
                    )
                } else {
                    if (verified) {
                        setupLockTaskPackage(context)
                    }
                    result
                }
            } catch (e: TimeoutCancellationException) {
                activationFailure(e)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                activationFailure(e).also {
                    Log.w("OwnerActivation", it.diagnostics)
                }
            }
        }
    }

    private suspend fun awaitPermission(access: ShizukuAccess) {
        if (access.permissionGranted()) {
            return
        }
        checkActivation(!access.permissionDeniedPermanently()) {
            "Shizuku permission was denied. Allow this app in Shizuku and try again."
        }
        val requestCode = permissionRequests.getAndIncrement()
        var pending: CancellableContinuation<Unit>? = null
        val listener = Shizuku.OnRequestPermissionResultListener { code, grant ->
            val continuation = pending
            if (code == requestCode && continuation?.isActive == true) {
                if (grant == PackageManager.PERMISSION_GRANTED) {
                    continuation.resume(Unit)
                } else {
                    continuation.resumeWithException(
                        ActivationException(
                            "Shizuku permission was denied. Allow this app in " +
                                "Shizuku, then try again."
                        )
                    )
                }
            }
        }
        val died = Shizuku.OnBinderDeadListener {
            pending
                ?.takeIf { it.isActive }
                ?.resumeWithException(
                    ActivationException(
                        "Shizuku disconnected. Open Shizuku, restart its service, then try again."
                    )
                )
        }
        try {
            access.addPermissionListener(listener)
            access.addDeathListener(died)
            withTimeout(120_000) {
                suspendCancellableCoroutine { continuation ->
                    pending = continuation
                    access.checkAvailable()
                    access.requestPermission(requestCode)
                }
            }
        } finally {
            access.removePermissionListener(listener)
            access.removeDeathListener(died)
        }
    }

    private suspend fun activateService(
        context: Context,
        profileOwner: Boolean,
        access: ShizukuAccess,
    ): ActivationResult {
        val args =
            Shizuku.UserServiceArgs(
                    ComponentName(context, ActivationService::class.java)
                )
                .tag("owner-activation")
                .daemon(false)
                .processNameSuffix("owner_activation")
                .debuggable(BuildConfig.DEBUG)
                .version(BuildConfig.VERSION_CODE)
        var pending: CancellableContinuation<IBinder>? = null
        val connection =
            object : ServiceConnection {
                override fun onServiceConnected(
                    name: ComponentName?,
                    service: IBinder?,
                ) {
                    val continuation = pending
                    if (continuation?.isActive == true) {
                        if (service?.pingBinder() == true) {
                            continuation.resume(service)
                        } else {
                            continuation.resumeWithException(
                                ActivationException(
                                    "Could not connect to Shizuku. Open Shizuku, " +
                                        "restart its service, then try again."
                                )
                            )
                        }
                    }
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    pending
                        ?.takeIf { it.isActive }
                        ?.resumeWithException(
                            ActivationException(
                                "Shizuku disconnected. Open Shizuku, restart " +
                                    "its service, then try again."
                            )
                        )
                }
            }
        val died = Shizuku.OnBinderDeadListener {
            connection.onServiceDisconnected(null)
        }
        try {
            access.addDeathListener(died)
            val binder =
                withTimeout(20_000) {
                    suspendCancellableCoroutine { continuation ->
                        pending = continuation
                        access.checkAvailable()
                        access.bind(args, connection)
                    }
                }
            // Once started, finish the transaction before unbinding, even when
            // navigation cancels the UI coroutine. The service restores in finally.
            return withContext(NonCancellable + Dispatchers.IO) {
                val bundle =
                    IActivationService.Stub.asInterface(binder)
                        .activate(profileOwner, Process.myUserHandle().hashCode())
                ActivationResult(
                        bundle?.getBoolean("success", false) == true,
                        bundle?.getString("message")
                            ?: if (bundle?.getBoolean("success", false) == true) {
                                "Ownership is now active. Check Device Policies for available permissions."
                            } else {
                                activationFailure(
                                        IllegalStateException(
                                            bundle?.getString("output").orEmpty()
                                        )
                                    )
                                    .output
                            },
                        bundle?.getString("diagnostics")
                            ?: bundle?.getString("output").orEmpty(),
                    )
                    .also {
                        if (it.diagnostics.isNotBlank()) {
                            Log.d("OwnerActivation", it.diagnostics)
                        }
                    }
            }
        } finally {
            access.removeDeathListener(died)
            // The server may already have died. Cleanup must preserve the result.
            runCatching { access.unbind(args, connection) }
        }
    }
}
