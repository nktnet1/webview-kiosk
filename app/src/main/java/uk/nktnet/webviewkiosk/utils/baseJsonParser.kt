package uk.nktnet.webviewkiosk.utils

import kotlinx.serialization.json.Json

val BaseJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    isLenient = true
    allowTrailingComma = true
    allowComments = true
}
