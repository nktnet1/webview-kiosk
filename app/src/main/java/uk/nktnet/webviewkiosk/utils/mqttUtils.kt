package uk.nktnet.webviewkiosk.utils

fun isValidMqttTopic(topic: String): Boolean {
    val regex = Regex("^[a-z0-9/_-]{1,65535}$")
    return regex.matches(topic)
}

fun isValidMqttSubscriptionTopic(topic: String): Boolean {
    if (topic.isEmpty() || topic.length > 65535) return false
    val levels = topic.split('/')
    for ((i, level) in levels.withIndex()) {
        if (level == "+") continue
        if (level == "#" && i == levels.lastIndex) continue
        if (!level.matches(Regex("^[a-z0-9_-]+$"))) return false
    }
    return true
}
