package uk.nktnet.webviewkiosk.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MqttUtilsTest {
    @Test
    fun acceptsLiteralPublishTopicsIncludingEmptyLevelsAndUnicode() {
        for (topic in listOf("kiosk/status", "/", "kiosk//status", "kiosk/", "\$SYS/status", "设备/状态")) {
            assertTrue(topic, isValidMqttPublishTopic(topic))
        }
    }

    @Test
    fun rejectsEmptyPublishTopicsNullCharactersAndWildcards() {
        val topics = listOf(
            "", "kiosk/\u0000/status", "+", "#", "kiosk/+", "kiosk/#", "kiosk+status", "kiosk#status",
        )
        for (topic in topics) {
            assertFalse(topic, isValidMqttPublishTopic(topic))
        }
    }

    @Test
    fun acceptsSubscribeWildcardsOnlyAsWholeLevels() {
        val topics = listOf(
            "kiosk/status", "/", "kiosk//status", "+", "#", "+/status",
            "kiosk/+/#", "kiosk/#", "kiosk/+/", "设备/+",
        )
        for (topic in topics) {
            assertTrue(topic, isValidMqttSubscribeTopic(topic))
        }
    }

    @Test
    fun rejectsMisplacedSubscribeWildcardsAndNullCharacters() {
        val topics = listOf(
            "", "kiosk/\u0000", "#/status", "kiosk/#/status", "kiosk/+suffix",
            "prefix+/status", "kiosk/##", "kiosk/++",
        )
        for (topic in topics) {
            assertFalse(topic, isValidMqttSubscribeTopic(topic))
        }
    }
}
