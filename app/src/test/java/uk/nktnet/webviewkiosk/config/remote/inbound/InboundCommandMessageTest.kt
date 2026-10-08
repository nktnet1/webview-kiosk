package uk.nktnet.webviewkiosk.config.remote.inbound

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.nktnet.webviewkiosk.config.remote.RemoteNotifyCommandPriority

class InboundCommandMessageTest {
    @Test
    fun dispatchesByCommandAndPreservesTargetingAndBehaviorFlags() {
        val command = InboundCommandJsonParser.decodeFromString<InboundCommandMessage>(
            """
                {
                    "command": "go_to_url",
                    "messageId": "message-1",
                    "targetInstances": ["one", "one", "two"],
                    "targetUsernames": ["operator"],
                    "interact": false,
                    "wakeScreen": true,
                    "data": {"url": "https://example.com"},
                    "futureField": true
                }
            """.trimIndent(),
        )

        assertTrue(command is InboundGoToUrlCommand)
        command as InboundGoToUrlCommand
        assertEquals("https://example.com", command.data.url)
        assertEquals("message-1", command.messageId)
        assertEquals(setOf("one", "two"), command.targetInstances)
        assertEquals(setOf("operator"), command.targetUsernames)
        assertFalse(command.interact)
        assertTrue(command.wakeScreen)
    }

    @Test
    fun acceptsCommentsAndTrailingCommasUsingTheSharedParserPolicy() {
        val command = InboundCommandJsonParser.decodeFromString<InboundCommandMessage>(
            """
                {
                    // Optional fields retain their defaults.
                    "command": "refresh",
                }
            """.trimIndent(),
        )

        assertTrue(command is InboundRefreshCommand)
        assertTrue(command.interact)
        assertFalse(command.wakeScreen)
    }

    @Test
    fun rejectsUnknownCommandsMissingDiscriminatorsAndRequiredData() {
        for (payload in listOf(
            """{"command":"unknown"}""",
            """{"command":"go_to_url"}""",
            """{"command":"go_to_url","data":{}}""",
            """{"command":"launch_package","data":{}}""",
            """{}""",
        )) {
            assertThrows(SerializationException::class.java) {
                InboundCommandJsonParser.decodeFromString<InboundCommandMessage>(payload)
            }
        }
    }

    @Test
    fun notifyDefaultsAndPriorityAliasesRemainCompatible() {
        val defaultCommand = InboundCommandJsonParser.decodeFromString<InboundCommandMessage>(
            """{"command":"notify"}""",
        ) as InboundNotifyCommand
        assertEquals(InboundNotifyCommand.NotifyData(), defaultCommand.data)

        for (priority in listOf("HIGH", "high", "High", "1")) {
            val command = InboundCommandJsonParser.decodeFromString<InboundCommandMessage>(
                """{"command":"notify","data":{"priority":"$priority"}}""",
            ) as InboundNotifyCommand
            assertEquals(priority, RemoteNotifyCommandPriority.HIGH, command.data.priority)
        }
    }

    @Test
    fun roundTripsUsingTheWireDiscriminatorInsteadOfTheKotlinClassName() {
        val original: InboundCommandMessage = InboundSearchCommand(
            messageId = "search-1",
            data = InboundSearchCommand.QueryData("coffee & tea"),
        )
        val encoded = InboundCommandJsonParser.encodeToString<InboundCommandMessage>(original)

        assertEquals(
            "search",
            InboundCommandJsonParser.parseToJsonElement(encoded).jsonObject["command"]?.jsonPrimitive?.content,
        )
        assertEquals(original, InboundCommandJsonParser.decodeFromString<InboundCommandMessage>(encoded))
    }
}
