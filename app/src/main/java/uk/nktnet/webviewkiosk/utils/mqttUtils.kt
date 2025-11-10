package uk.nktnet.webviewkiosk.utils

fun isValidMqttPublishTopic(topic: String): Boolean {
    return topic.matches(Regex("^[^\\u0000+#]+$"))
}

fun isValidMqttSubscribeTopic(topic: String): Boolean {
    if (topic.isEmpty()) {
        return false
    }
    if (topic.contains('\u0000')) {
        return false
    }

    val levels = topic.split('/')
    levels.forEachIndexed { i, level ->
        if (level.contains('#') && i != levels.lastIndex) {
            return false
        }
        if (level != "#" && level.contains('#')) {
            return false
        }
        if (level != "+" && level.contains('+')) {
            return false
        }
    }
    return true
}
