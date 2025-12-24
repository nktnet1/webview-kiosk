package com.nktnet.webview_kiosk.config.data

import kotlinx.serialization.Serializable

@Serializable
data class SystemAppInfo(
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
    val dhizukuPermissionGranted: Boolean,
    val instanceId: String
)

@Serializable
data class SystemDeviceInfo(
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
    val app: SystemAppInfo,
    val device: SystemDeviceInfo
)
