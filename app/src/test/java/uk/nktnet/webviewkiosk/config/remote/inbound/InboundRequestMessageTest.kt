package uk.nktnet.webviewkiosk.config.remote.inbound

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class InboundRequestMessageTest {
    @Test
    fun preservesMqttResponseRoutingMetadata() {
        val request = InboundRequestJsonParser.decodeFromString<InboundRequestMessage>(
            """
                {
                    "requestType": "get_status",
                    "responseTopic": "kiosk/response",
                    "correlationData": "request-1",
                    "futureField": true
                }
            """.trimIndent(),
        )

        assertTrue(request is InboundStatusRequest)
        assertEquals("kiosk/response", request.responseTopic)
        assertEquals("request-1", request.correlationData)
    }

    @Test
    fun settingsRequestsDefaultToRequestingAllSettings() {
        val request = InboundRequestJsonParser.decodeFromString<InboundRequestMessage>(
            """{"requestType":"get_settings"}""",
        ) as InboundSettingsRequest

        assertTrue(request.data.settings.isEmpty())
    }

    @Test
    fun preservesStructuredAndUntrustedSettingsFiltersForSafeFiltering() {
        val request = InboundRequestJsonParser.decodeFromString<InboundRequestMessage>(
            """
                {
                    "requestType": "get_settings",
                    "data": {
                        "settings": [
                            "web_content.home_url",
                            {"key": "web_content.website_blacklist", "parseAsArray": true},
                            null,
                            []
                        ]
                    }
                }
            """.trimIndent(),
        ) as InboundSettingsRequest

        assertArrayEquals(
            arrayOf(
                JsonPrimitive("web_content.home_url"),
                buildJsonObject {
                    put("key", "web_content.website_blacklist")
                    put("parseAsArray", true)
                },
                JsonNull,
                JsonArray(emptyList()),
            ),
            request.data.settings,
        )
    }

    @Test
    fun requestDataUsesValueEqualityAcrossSerializationRoundTrips() {
        val original: InboundRequestMessage = InboundSettingsRequest(
            data = InboundSettingsRequest.SettingsRequestData(arrayOf(JsonPrimitive("web_content.home_url"))),
        )
        val encoded = InboundRequestJsonParser.encodeToString<InboundRequestMessage>(original)
        val decoded = InboundRequestJsonParser.decodeFromString<InboundRequestMessage>(encoded)

        assertEquals(original, decoded)
        assertEquals(original.hashCode(), decoded.hashCode())
    }

    @Test
    fun rejectsUnknownRequestTypesAndNonArraySettingsFilters() {
        val payloads = listOf(
            """{"requestType":"unknown"}""",
            """{"requestType":"get_settings","data":{"settings":{}}}""",
        )
        for (payload in payloads) {
            assertThrows(SerializationException::class.java) {
                InboundRequestJsonParser.decodeFromString<InboundRequestMessage>(payload)
            }
        }
    }
}
