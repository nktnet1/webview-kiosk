package com.nktnet.webview_kiosk.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.webkit.PermissionRequest
import androidx.core.content.ContextCompat
import com.nktnet.webview_kiosk.config.Constants

fun getPermissionDisplay(resource: String): String = when (resource) {
    PermissionRequest.RESOURCE_AUDIO_CAPTURE -> "Microphone"
    PermissionRequest.RESOURCE_VIDEO_CAPTURE -> "Camera"
    Constants.GEOLOCATION_RESOURCE -> "Location"
    else -> resource
}

fun hasPermissionForResource(context: Context, resource: String): Boolean {
    val resourceToPermissions = mapOf(
        PermissionRequest.RESOURCE_AUDIO_CAPTURE to listOf(Manifest.permission.RECORD_AUDIO),
        PermissionRequest.RESOURCE_VIDEO_CAPTURE to listOf(Manifest.permission.CAMERA),
        Constants.GEOLOCATION_RESOURCE to listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )

    val permissions = resourceToPermissions[resource] ?: return false
    return permissions.any {
        ContextCompat.checkSelfPermission(
            context, it
        ) == PackageManager.PERMISSION_GRANTED
    }
}
