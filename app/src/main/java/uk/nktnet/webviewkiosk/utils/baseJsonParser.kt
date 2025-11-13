package uk.nktnet.webviewkiosk.utils

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
