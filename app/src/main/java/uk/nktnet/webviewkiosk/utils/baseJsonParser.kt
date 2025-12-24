package com.nktnet.webview_kiosk.utils

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

@OptIn(ExperimentalSerializationApi::class)
val BaseJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    isLenient = true
    allowTrailingComma = true
    allowComments = true
}
