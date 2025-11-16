package uk.nktnet.webviewkiosk.utils

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.UserManager
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.pm.PackageInfoCompat
import androidx.webkit.WebViewCompat
import android.app.admin.DevicePolicyManager
import android.content.pm.PackageManager
import kotlinx.serialization.Serializable
import uk.nktnet.webviewkiosk.BuildConfig
import uk.nktnet.webviewkiosk.config.SystemSettings
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.option.DeviceRotationOption
import uk.nktnet.webviewkiosk.states.KeepScreenOnStateSingleton
import uk.nktnet.webviewkiosk.states.ThemeStateSingleton
import java.util.TimeZone

fun getWindowBrightness(context: Context): Int {
    val activity = context as? Activity ?: return -1
    val brightness = activity.window.attributes.screenBrightness
    return if (brightness < 0) {
        -1
    } else {
        (brightness * 100).toInt().coerceIn(0, 100)
    }
}

fun setWindowBrightness(context: Context, value: Int) {
    val activity = context as? Activity ?: return
    val window = activity.window
    val layoutParams: WindowManager.LayoutParams = window.attributes
    layoutParams.screenBrightness =
        if (value < 0) {
            WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        } else {
            (value / 100f).coerceIn(0f, 1f)
        }
    window.attributes = layoutParams
}

fun setDeviceRotation(context: Context, rotation: DeviceRotationOption) {
    val activity = context as? AppCompatActivity ?: return
    activity.requestedOrientation = when(rotation) {
        DeviceRotationOption.AUTO -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        DeviceRotationOption.ROTATION_0 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        DeviceRotationOption.ROTATION_90 -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        DeviceRotationOption.ROTATION_180 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
        DeviceRotationOption.ROTATION_270 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
    }
}

fun updateDeviceSettings(context: Context) {
    val userSettings = UserSettings(context)
    KeepScreenOnStateSingleton.setKeepScreenOn(userSettings.keepScreenOn)
    ThemeStateSingleton.setTheme(userSettings.theme)
    setDeviceRotation(context, userSettings.rotation)
    setWindowBrightness(context, userSettings.brightness)
}

@Serializable
data class AppInfo(
    val name: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val minSdk: Int,
    val targetSdk: Int,
    val isDebug: Boolean,
    val installer: String?,
    val isDeviceOwner: Boolean,
    val isLockTaskPermitted: Boolean,
    val instanceId: String
)

@Serializable
data class DeviceInfo(
    val releaseVersion: String,
    val sdkVersion: Int,
    val incrementalVersion: String,
    val webViewVersion: String?,
    val screenWidth: Int,
    val screenHeight: Int,
    val screenDensity: Float,
    val isManagedProfile: Boolean?,
    val timeZone: String,
    val locale: String,
    val totalMemory: Long,
    val totalStorage: Long,
    val manufacturer: String,
    val model: String,
    val brand: String,
    val device: String,
    val product: String,
    val hardware: String,
    val board: String,
    val bootloader: String,
    val securityPatch: String?,
    val supportedAbis: List<String>,
    val supported32BitAbis: List<String>,
    val supported64BitAbis: List<String>,
    val buildFingerprint: String
)

@Serializable
data class SystemInfo(
    val app: AppInfo,
    val device: DeviceInfo
)

fun getAppInfo(context: Context): AppInfo {
    val pm = context.packageManager
    val packageName = context.packageName
    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    val systemSettings = SystemSettings(context)

    val versionName = try {
        pm.getPackageInfo(packageName, 0).versionName ?: "N/A"
    } catch (_: PackageManager.NameNotFoundException) {
        "N/A"
    }

    val versionCode = try {
        val info = pm.getPackageInfo(packageName, 0)
        PackageInfoCompat.getLongVersionCode(info)
    } catch (_: PackageManager.NameNotFoundException) {
        -1L
    }

    val targetSdk = try {
        pm.getPackageInfo(packageName, 0).applicationInfo?.targetSdkVersion ?: -1
    } catch (_: PackageManager.NameNotFoundException) {
        -1
    }

    val isDebug = try {
        val info = pm.getApplicationInfo(packageName, 0)
        (info.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    val installer = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            pm.getInstallSourceInfo(packageName).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            pm.getInstallerPackageName(packageName)
        }
    } catch (_: Exception) {
        null
    }

    return AppInfo(
        name = context.applicationInfo.loadLabel(pm).toString(),
        packageName = packageName,
        versionName = versionName,
        versionCode = versionCode,
        minSdk = BuildConfig.MIN_SDK_VERSION,
        targetSdk = targetSdk,
        isDebug = isDebug,
        installer = installer,
        isDeviceOwner = dpm.isDeviceOwnerApp(packageName),
        isLockTaskPermitted = dpm.isLockTaskPermitted(packageName),
        instanceId = systemSettings.appInstanceId
    )
}

fun getDeviceInfo(context: Context): DeviceInfo {
    val memInfo = ActivityManager.MemoryInfo()
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    am.getMemoryInfo(memInfo)

    val stat = StatFs(Environment.getDataDirectory().path)

    val um = context.getSystemService(Context.USER_SERVICE) as UserManager
    val isManagedProfile = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        um.isManagedProfile
    } else {
        null
    }

    val securityPatch = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        Build.VERSION.SECURITY_PATCH
    } else {
        null
    }

    val webViewVersion = try {
        WebViewCompat.getCurrentWebViewPackage(context)?.versionName
    } catch (_: Exception) {
        null
    }

    val locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        context.resources.configuration.locales[0].toString()
    } else {
        @Suppress("DEPRECATION")
        context.resources.configuration.locale.toString()
    }

    return DeviceInfo(
        releaseVersion = Build.VERSION.RELEASE,
        sdkVersion = Build.VERSION.SDK_INT,
        incrementalVersion = Build.VERSION.INCREMENTAL,
        webViewVersion = webViewVersion,
        screenWidth = context.resources.displayMetrics.widthPixels,
        screenHeight = context.resources.displayMetrics.heightPixels,
        screenDensity = context.resources.displayMetrics.density,
        isManagedProfile = isManagedProfile,
        timeZone = TimeZone.getDefault().id,
        locale = locale,
        totalMemory = memInfo.totalMem,
        totalStorage = stat.blockSizeLong * stat.blockCountLong,
        manufacturer = Build.MANUFACTURER,
        model = Build.MODEL,
        brand = Build.BRAND,
        device = Build.DEVICE,
        product = Build.PRODUCT,
        hardware = Build.HARDWARE,
        board = Build.BOARD,
        bootloader = Build.BOOTLOADER,
        securityPatch = securityPatch,
        supportedAbis = Build.SUPPORTED_ABIS.toList(),
        supported32BitAbis = Build.SUPPORTED_32_BIT_ABIS.toList(),
        supported64BitAbis = Build.SUPPORTED_64_BIT_ABIS.toList(),
        buildFingerprint = Build.FINGERPRINT
    )
}

fun getSystemInfo(context: Context): SystemInfo {
    return SystemInfo(
        app = getAppInfo(context),
        device = getDeviceInfo(context)
    )
}
