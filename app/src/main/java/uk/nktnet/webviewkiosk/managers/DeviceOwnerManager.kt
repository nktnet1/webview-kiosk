package uk.nktnet.webviewkiosk.managers

import android.annotation.SuppressLint
import android.app.admin.DeviceAdminInfo
import android.app.admin.DevicePolicyManager
import android.app.admin.IDevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.os.RemoteException
import android.os.SystemClock
import android.os.UserManager
import android.util.Log
import com.rosan.dhizuku.api.Dhizuku
import com.rosan.dhizuku.api.DhizukuBinderWrapper
import com.rosan.dhizuku.api.DhizukuRequestPermissionListener
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import org.lsposed.hiddenapibypass.HiddenApiBypass
import uk.nktnet.webviewkiosk.WebviewKioskAdminReceiver
import uk.nktnet.webviewkiosk.config.data.DeviceOwnerMode
import uk.nktnet.webviewkiosk.config.data.ManagementCapabilities
import uk.nktnet.webviewkiosk.config.data.ManagementOwner
import uk.nktnet.webviewkiosk.config.data.ManagementUserType

object DeviceOwnerManager {
    private const val DHIZUKU_MIN_SDK = Build.VERSION_CODES.M
    private const val DHIZUKU_SETTLE_TIME_MS = 1000L
    private val DHIZUKU_RETRY_DELAYS_MS = longArrayOf(100L, 250L, 650L, 1000L, 1000L)

    lateinit var DPM: DevicePolicyManager
        private set

    lateinit var DAR: ComponentName
        private set

    private var dhizukuBinderAvailableSince = 0L
    private val pendingPermissionRequests = PendingDhizukuPermissionRequests()

    internal interface SharedOwnerBridge {
        fun init(context: Context): Boolean

        fun ownerComponent(): ComponentName

        fun permissionGranted(): Boolean

        fun requestPermission(listener: DhizukuRequestPermissionListener)

        fun policyManager(context: Context): DevicePolicyManager?
    }

    internal var sharedOwnerBridge: SharedOwnerBridge =
        object : SharedOwnerBridge {
            override fun init(context: Context): Boolean {
                if (Build.VERSION.SDK_INT < DHIZUKU_MIN_SDK) {
                    return false
                }
                // The API supports Android 6. Hidden API exemptions are only
                // needed from Android 9, when non-SDK restrictions were added.
                if (
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                        !HiddenApiBypass.setHiddenApiExemptions("")
                ) {
                    return false
                }
                return Dhizuku.init(context)
            }

            override fun ownerComponent() = Dhizuku.getOwnerComponent()

            override fun permissionGranted() = Dhizuku.isPermissionGranted()

            override fun requestPermission(
                listener: DhizukuRequestPermissionListener
            ) = Dhizuku.requestPermission(listener)

            override fun policyManager(context: Context) =
                binderWrapperDevicePolicyManager(context)
        }

    // Keep transport (native/Dhizuku) separate from the authority it provides.
    data class Status(
        val mode: DeviceOwnerMode = DeviceOwnerMode.None,
        val owner: ManagementOwner = ManagementOwner.None,
        val userType: ManagementUserType = ManagementUserType.Unknown,
    )

    val status = MutableStateFlow(Status())

    @Synchronized
    fun init(context: Context) {
        // Rechecking a live shared owner must not briefly publish None: a
        // concurrent permission callback would otherwise be discarded.
        resetToPlatformDpm(context, publishStatus = false)

        val nativeMode =
            when {
                DPM.isDeviceOwnerApp(context.packageName) ->
                    DeviceOwnerMode.DeviceOwner
                DPM.isProfileOwnerApp(context.packageName) ->
                    DeviceOwnerMode.ProfileOwner
                else -> DeviceOwnerMode.None
            }
        if (nativeMode != DeviceOwnerMode.None) {
            pendingPermissionRequests.invalidate()
            dhizukuBinderAvailableSince = 0L
            updateStatus(nativeMode)
            hasOwnerPermission(context)
            return
        }

        if (Build.VERSION.SDK_INT < DHIZUKU_MIN_SDK) {
            updateStatus(DeviceOwnerMode.None)
            return
        }

        try {
            if (!sharedOwnerBridge.init(context)) {
                dhizukuBinderAvailableSince = 0L
                updateStatus(DeviceOwnerMode.None)
                return
            }

            val now = SystemClock.elapsedRealtime()
            if (dhizukuBinderAvailableSince == 0L) {
                dhizukuBinderAvailableSince = now
                updateStatus(DeviceOwnerMode.None)
                return
            }
            if (now - dhizukuBinderAvailableSince < DHIZUKU_SETTLE_TIME_MS) {
                updateStatus(DeviceOwnerMode.None)
                return
            }

            val dpm = sharedOwnerBridge.policyManager(context)
            if (dpm == null) {
                updateStatus(DeviceOwnerMode.None)
                return
            }
            val dar = sharedOwnerBridge.ownerComponent()

            DPM = dpm
            DAR = dar
            updateStatus(DeviceOwnerMode.Dhizuku)
            hasOwnerPermission(context)
        } catch (e: Throwable) {
            invalidateDhizuku(context)
            Log.w(javaClass.simpleName, "Dhizuku is not ready", e)
        }
    }

    suspend fun initWithDhizukuRetry(context: Context): DeviceOwnerMode {
        init(context)
        if (
            status.value.mode != DeviceOwnerMode.None ||
                Build.VERSION.SDK_INT < DHIZUKU_MIN_SDK
        ) {
            return status.value.mode
        }

        for (retryDelay in DHIZUKU_RETRY_DELAYS_MS) {
            delay(retryDelay.milliseconds)
            init(context)
            if (status.value.mode != DeviceOwnerMode.None) {
                break
            }
        }

        return status.value.mode
    }

    private fun resetToPlatformDpm(context: Context, publishStatus: Boolean = true) {
        DPM =
            context.getSystemService(Context.DEVICE_POLICY_SERVICE)
                as DevicePolicyManager
        DAR =
            ComponentName(
                context.packageName,
                WebviewKioskAdminReceiver::class.java.name,
            )
        if (publishStatus) {
            updateStatus(DeviceOwnerMode.None)
        }
    }

    private fun invalidateDhizuku(context: Context) {
        pendingPermissionRequests.invalidate()
        dhizukuBinderAvailableSince = 0L
        resetToPlatformDpm(context)
    }

    @Synchronized
    fun hasOwnerPermission(context: Context): Boolean {
        return try {
            val mode = status.value.mode
            val granted =
                when (mode) {
                    DeviceOwnerMode.DeviceOwner -> {
                        DPM.isDeviceOwnerApp(context.packageName)
                    }
                    DeviceOwnerMode.ProfileOwner ->
                        DPM.isProfileOwnerApp(context.packageName)
                    DeviceOwnerMode.Dhizuku -> {
                        Build.VERSION.SDK_INT >= DHIZUKU_MIN_SDK &&
                            sharedOwnerBridge.permissionGranted()
                    }
                    else -> {
                        false
                    }
                }
            val owner =
                if (granted) {
                    when {
                        DPM.isDeviceOwnerApp(DAR.packageName) ->
                            ManagementOwner.DeviceOwner
                        DPM.isProfileOwnerApp(DAR.packageName) ->
                            ManagementOwner.ProfileOwner
                        else -> ManagementOwner.None
                    }
                } else if (mode == DeviceOwnerMode.Dhizuku) {
                    ManagementOwner.Unknown
                } else {
                    ManagementOwner.None
                }
            status.value = Status(mode, owner, getUserType(context, owner))
            granted && owner != ManagementOwner.None
        } catch (e: Exception) {
            if (status.value.mode == DeviceOwnerMode.Dhizuku) {
                invalidateDhizuku(context)
            }
            Log.w(javaClass.simpleName, "Failed to check owner permission", e)
            false
        }
    }

    private fun getUserType(
        context: Context,
        owner: ManagementOwner,
    ): ManagementUserType {
        return try {
            val users = context.getSystemService(Context.USER_SERVICE) as UserManager
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                    users.isManagedProfile -> ManagementUserType.ManagedProfile
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    users.isProfile -> ManagementUserType.Profile
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                    ManagementUserType.FullUser
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                    owner == ManagementOwner.ProfileOwner ->
                    if (DPM.isManagedProfile(DAR)) {
                        ManagementUserType.ManagedProfile
                    } else {
                        ManagementUserType.FullUser
                    }
                owner == ManagementOwner.DeviceOwner -> ManagementUserType.FullUser
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                    users.isSystemUser -> ManagementUserType.FullUser
                else -> ManagementUserType.Unknown
            }
        } catch (e: Exception) {
            Log.w(
                javaClass.simpleName,
                "Unable to determine management user type",
                e,
            )
            ManagementUserType.Unknown
        }
    }

    @Synchronized
    fun getCapabilities(context: Context): ManagementCapabilities {
        if (!hasOwnerPermission(context)) {
            return ManagementCapabilities()
        }
        // These queries enforce Android's Lock Task authority/affiliation checks.
        // A successful owner check alone does not authorise Lock Task policies.
        val packages =
            try {
                readLockTaskPackages()
                true
            } catch (_: Exception) {
                false
            }
        val features =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                try {
                    DPM.getLockTaskFeatures(DAR)
                    true
                } catch (_: Exception) {
                    false
                }
        val native =
            status.value.mode == DeviceOwnerMode.DeviceOwner ||
                status.value.mode == DeviceOwnerMode.ProfileOwner
        val fullUser = status.value.userType == ManagementUserType.FullUser
        val forceLock =
            fullUser &&
                try {
                    val receiver =
                        context.packageManager.getReceiverInfo(
                            DAR,
                            PackageManager.GET_META_DATA,
                        )
                    DeviceAdminInfo(
                            context,
                            ResolveInfo().apply { activityInfo = receiver },
                        )
                        .usesPolicy(DeviceAdminInfo.USES_POLICY_FORCE_LOCK)
                } catch (_: Exception) {
                    false
                }
        return ManagementCapabilities(
            lockTaskPackages = packages,
            lockTaskFeatures = features,
            lockDevice = forceLock,
            transferOwnership =
                native && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P,
            removeOwner =
                native &&
                    (status.value.owner == ManagementOwner.DeviceOwner ||
                        (fullUser &&
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)),
        )
    }

    @SuppressLint("PrivateApi")
    private fun readLockTaskPackages(): Array<String> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return DPM.getLockTaskPackages(DAR)
        }
        // AOSP supplied this getter on API 21–25, but only made it public in 26.
        // Those releases predate non-SDK enforcement. Fail closed on OEMs without
        // it: replacing an unreadable allowlist would discard another admin's apps.
        @Suppress("UNCHECKED_CAST")
        return DPM.javaClass
            .getMethod("getLockTaskPackages", ComponentName::class.java)
            .invoke(DPM, DAR) as Array<String>
    }

    @Synchronized
    fun getLockTaskPackages(context: Context): List<String> {
        if (!hasOwnerPermission(context)) {
            return emptyList()
        }
        return try {
            readLockTaskPackages().toList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun editLockTaskPackage(
        context: Context,
        packageName: String,
        allowed: Boolean,
    ): Boolean {
        return hasOwnerPermission(context) &&
            try {
                val current = readLockTaskPackages().toSet()
                val updated =
                    if (allowed) {
                        current + packageName
                    } else {
                        current - packageName
                    }
                if (current != updated) {
                    DPM.setLockTaskPackages(DAR, updated.toTypedArray())
                }
                // Policies can conflict or become unaffiliated while the UI is open.
                (packageName in readLockTaskPackages()) == allowed
            } catch (e: Exception) {
                Log.w(javaClass.simpleName, "Unable to update lock task package", e)
                false
            }
    }

    @Synchronized
    fun removeNativeOwner(context: Context) {
        check(getCapabilities(context).removeOwner) {
            "Owner removal is unavailable"
        }
        when (status.value.mode) {
            DeviceOwnerMode.DeviceOwner -> {
                @Suppress("DEPRECATION") DPM.clearDeviceOwnerApp(context.packageName)
            }
            DeviceOwnerMode.ProfileOwner -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    @Suppress("DEPRECATION") DPM.clearProfileOwner(DAR)
                }
            }
            else -> error("Cannot remove a shared owner")
        }
        init(context)
    }

    fun requestDhizukuPermission(
        context: Context,
        onGranted: () -> Unit = {},
        onDenied: () -> Unit = {},
    ) {
        // Claim ownership before any Dhizuku preflight can reenter or suspend.
        val request = pendingPermissionRequests.replace()
        if (
            Build.VERSION.SDK_INT < DHIZUKU_MIN_SDK ||
                status.value.mode != DeviceOwnerMode.Dhizuku
        ) {
            if (pendingPermissionRequests.consume(request)) {
                deliverDhizukuPermissionResult(context, false, onGranted, onDenied)
            }
            return
        }

        val alreadyGranted =
            try {
                sharedOwnerBridge.permissionGranted()
            } catch (e: Throwable) {
                if (pendingPermissionRequests.consume(request)) {
                    invalidateDhizuku(context)
                    deliverDhizukuPermissionResult(
                        context,
                        false,
                        onGranted,
                        onDenied,
                    )
                }
                Log.e(javaClass.simpleName, "Failed to check Dhizuku permission", e)
                return
            }
        if (alreadyGranted) {
            if (pendingPermissionRequests.consume(request)) {
                deliverDhizukuPermissionResult(context, true, onGranted, onDenied)
            }
            return
        }

        // Do not launch a prompt if a newer request won during preflight.
        if (!pendingPermissionRequests.isCurrent(request)) {
            return
        }
        try {
            sharedOwnerBridge.requestPermission(
                object : DhizukuRequestPermissionListener() {
                    @Throws(RemoteException::class)
                    override fun onRequestPermission(grantResult: Int) {
                        if (!pendingPermissionRequests.consume(request)) {
                            return
                        }
                        // The owner or its permission may have changed while the
                        // dialog was open.
                        if (status.value.mode != DeviceOwnerMode.Dhizuku) {
                            return
                        }
                        deliverDhizukuPermissionResult(
                            context,
                            grantResult == PackageManager.PERMISSION_GRANTED,
                            onGranted,
                            onDenied,
                        )
                    }
                }
            )
        } catch (e: Throwable) {
            // A replaced or already completed request must not invalidate its
            // successor.
            if (pendingPermissionRequests.consume(request)) {
                if (status.value.mode == DeviceOwnerMode.Dhizuku) {
                    invalidateDhizuku(context)
                }
                deliverDhizukuPermissionResult(context, false, onGranted, onDenied)
            }
            Log.e(javaClass.simpleName, "Failed to request Dhizuku permission", e)
        }
    }

    private fun deliverDhizukuPermissionResult(
        context: Context,
        granted: Boolean,
        onGranted: () -> Unit,
        onDenied: () -> Unit,
    ) {
        try {
            if (granted) {
                // Refresh the real owner type after the user grants shared access.
                init(context)
            }
            if (granted && hasOwnerPermission(context)) {
                onGranted()
            } else {
                onDenied()
            }
        } catch (e: Exception) {
            // A consumer failure must not trigger the opposite callback or escape a
            // binder thread.
            Log.e(javaClass.simpleName, "Dhizuku permission callback failed", e)
        }
    }

    private fun updateStatus(mode: DeviceOwnerMode) {
        status.value = Status(mode)
    }

    @SuppressLint("PrivateApi")
    private fun binderWrapperDevicePolicyManager(
        appContext: Context
    ): DevicePolicyManager? {
        if (Build.VERSION.SDK_INT < DHIZUKU_MIN_SDK) {
            return null
        }
        try {
            val context =
                appContext.createPackageContext(
                    Dhizuku.getOwnerComponent().packageName,
                    Context.CONTEXT_IGNORE_SECURITY,
                )
            val manager =
                context.getSystemService(Context.DEVICE_POLICY_SERVICE)
                    as DevicePolicyManager
            val field = manager.javaClass.getDeclaredField("mService")
            field.isAccessible = true
            val oldInterface = field[manager] as IDevicePolicyManager
            if (oldInterface is DhizukuBinderWrapper) {
                return manager
            }
            val oldBinder = oldInterface.asBinder()
            val newBinder = Dhizuku.binderWrapper(oldBinder)
            val newInterface = IDevicePolicyManager.Stub.asInterface(newBinder)
            field[manager] = newInterface
            return manager
        } catch (e: Exception) {
            Log.e(
                javaClass.simpleName,
                "Failed to create Dhizuku binder wrapper",
                e,
            )
        }
        return null
    }
}
