package uk.nktnet.webviewkiosk.utils

fun replaceVariables(
    value: String,
    replacementMap: Map<String, String> = emptyMap()
): String {
    val regex = "\\$\\{([^}]+)\\}".toRegex()
    return regex.replace(value) { matchResult ->
        val key = matchResult.groupValues[1]
        replacementMap[key] ?: matchResult.value
    }.trim()
}
