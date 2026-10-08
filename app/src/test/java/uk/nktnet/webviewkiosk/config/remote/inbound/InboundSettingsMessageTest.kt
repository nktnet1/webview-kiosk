package uk.nktnet.webviewkiosk.config.remote.inbound

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import uk.nktnet.webviewkiosk.utils.BaseJson

class InboundSettingsMessageTest {
    @Test
    fun missingOptionalFieldsRetainTheirDefaults() {
        assertEquals(InboundSettingsMessage(), BaseJson.decodeFromString<InboundSettingsMessage>("{}"))
    }

    @Test
    fun preservesSettingsValueTypesAndExplicitBehaviorFlags() {
        val message = BaseJson.decodeFromString<InboundSettingsMessage>(
            """
                {
                    "showToast": false,
                    "reloadActivity": false,
                    "data": {
                        "settings": {
                            "web_content.home_url": "https://example.com",
                            "device.brightness": 50,
                            "web_content.allow_local_files": true
                        }
                    },
                    "futureField": true
                }
            """.trimIndent(),
        )

        assertFalse(message.showToast)
        assertFalse(message.reloadActivity)
        assertEquals(
            buildJsonObject {
                put("web_content.home_url", "https://example.com")
                put("device.brightness", 50)
                put("web_content.allow_local_files", true)
            },
            message.data.settings,
        )
    }

    @Test
    fun rejectsNonObjectSettingsPayloads() {
        assertThrows(SerializationException::class.java) {
            BaseJson.decodeFromString<InboundSettingsMessage>("""{"data":{"settings":[]}}""")
        }
    }
}
