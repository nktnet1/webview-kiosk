package com.nktnet.webview_kiosk.utils

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
import android.os.PowerManager
import com.nktnet.webview_kiosk.BuildConfig
import com.nktnet.webview_kiosk.config.data.DeviceOwnerMode
import com.nktnet.webview_kiosk.config.SystemSettings
import com.nktnet.webview_kiosk.config.UserSettings
import com.nktnet.webview_kiosk.config.data.SystemAppInfo
import com.nktnet.webview_kiosk.config.data.SystemDeviceInfo
import com.nktnet.webview_kiosk.config.data.SystemInfo
import com.nktnet.webview_kiosk.config.option.DeviceRotationOption
import com.nktnet.webview_kiosk.managers.DeviceOwnerManager
import com.nktnet.webview_kiosk.states.KeepScreenOnStateSingleton
import com.nktnet.webview_kiosk.states.ThemeStateSingleton
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

fun applyBlockScreenCapture(context: Context, shouldBlock: Boolean) {
    if (context is Activity) {
        if (shouldBlock) {
            context.window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        } else {
            context.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}

fun updateDeviceSettings(context: Context) {
    val userSettings = UserSettings(context)
    KeepScreenOnStateSingleton.setKeepScreenOn(userSettings.keepScreenOn)
    ThemeStateSingleton.setTheme(userSettings.theme)
    setDeviceRotation(context, userSettings.rotation)
    setWindowBrightness(context, userSettings.brightness)
    applyBlockScreenCapture(context, userSettings.blockScreenCapture)
    applyLockTaskFeatures(context)
    initMqttForegroundService(
        context,
        userSettings.mqttEnabled && userSettings.mqttUseForegroundService,
    )
}

fun getAppInfo(context: Context): SystemAppInfo {
    val pm = context.packageManager
    val packageName = context.packageName
    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    val dhizukuPermissionGranted = (
        DeviceOwnerManager.status.value.mode == DeviceOwnerMode.Dhizuku
        && DeviceOwnerManager.hasOwnerPermission(context)
    )
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

    return SystemAppInfo(
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
        dhizukuPermissionGranted = dhizukuPermissionGranted,
        instanceId = systemSettings.appInstanceId
    )
}

fun getDeviceInfo(context: Context): SystemDeviceInfo {
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

    return SystemDeviceInfo(
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

/**
 * Modelled after Home Assistant COMMAND_SCREEN_ON implementation:
 * https://github.com/home-assistant/android/blob/875b2d948823616cbb73da819255ab7f34f23f16/app/src/main/kotlin/io/homeassistant/companion/android/notifications/MessagingManager.kt#L780-L796
 */
fun wakeScreen(context: Context) {
    try {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        val wakeLock = pm.newWakeLock(
            PowerManager.FULL_WAKE_LOCK
                or PowerManager.ACQUIRE_CAUSES_WAKEUP
                or PowerManager.ON_AFTER_RELEASE,
            "${context.packageName}::WakeScreen"
        )
        wakeLock.acquire(30_000L)
        wakeLock.release()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
