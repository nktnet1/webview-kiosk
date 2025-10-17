package com.nktnet.webview_kiosk.config.option

enum class DeviceRotationOption(val degrees: String) {
    AUTO("Auto"),
    ROTATION_0("0"),
    ROTATION_90("90"),
    ROTATION_180("180"),
    ROTATION_270("270");

    companion object {
        fun fromString(value: String?): DeviceRotationOption {
            return entries.find { it.degrees == value } ?: AUTO
        }
    }
}
