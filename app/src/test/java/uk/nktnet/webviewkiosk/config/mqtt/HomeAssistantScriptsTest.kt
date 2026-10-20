package uk.nktnet.webviewkiosk.config.mqtt

import java.io.File
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeAssistantScriptsTest {
    private val discovery = HomeAssistantDiscovery("instance-a", "custom/discovery")

    @Test
    fun scriptsUseDedicatedTopicsAndAlwaysReplaceTheTarget() {
        val scripts = HomeAssistantScripts.create(discovery, "Kitchen")
        listOf(
                "command" to discovery.commandTopic,
                "settings" to discovery.settingsCommandTopic,
                "request" to discovery.requestTopic,
            )
            .forEach { (operation, topic) ->
                assertTrue(scripts.contains("webview_kiosk_instance_a_$operation:"))
                assertTrue(scripts.contains("topic: ${JsonPrimitive(topic)}"))
            }
        assertEquals(3, scripts.lines().count { it == "        qos: 1" })
        assertEquals(3, scripts.lines().count { it == "        retain: false" })
        assertEquals(
            3,
            scripts.lines().count {
                it.contains("dict(wk_payload, targetInstances=[\"instance-a\"])")
            },
        )
        assertFalse(scripts.contains("notify.send_message"))
        assertFalse(scripts.contains("custom/discovery"))
        assertFalse(scripts.contains("targetInstances=[kiosk_instance_id]"))
        System.getProperty("haScriptsReport")?.let { path ->
            File(path).apply {
                parentFile?.mkdirs()
                writeText(scripts)
            }
        }
    }

    @Test
    fun namesAreQuotedAndScriptIdsSurviveRenaming() {
        val name = "Kitchen \"tablet\"\n雪: # [] {{ example }}"
        val first = HomeAssistantScripts.create(discovery, name)
        val renamed = HomeAssistantScripts.create(discovery, "Hallway")
        assertTrue(first.contains("alias: ${JsonPrimitive("$name: Send command")}"))
        assertEquals(
            first.lines().filter { it.startsWith("webview_kiosk_") },
            renamed.lines().filter { it.startsWith("webview_kiosk_") },
        )
        assertFalse(first.contains("\n雪"))
        System.getProperty("haScriptsReport")?.let { path ->
            File("$path.quoted").writeText(first)
        }
    }

    @Test
    fun scriptIdsAreUniqueAcrossAppInstancesAndUseTheCompleteId() {
        val first = HomeAssistantScripts.create(discovery, "")
        val second =
            HomeAssistantScripts.create(
                HomeAssistantDiscovery("instance-b", "homeassistant"),
                "",
            )
        assertTrue(first.contains("Webview Kiosk instance-a: Send command"))
        assertTrue(second.contains("webview_kiosk_instance_b_command:"))
        assertFalse(second.contains("instance-a"))
    }

    @Test
    fun allThreeOperationsRejectMissingOrMalformedEnvelopesBeforePublishing() {
        val scripts = HomeAssistantScripts.create(discovery, "Kitchen")
        assertEquals(3, scripts.lines().count { it == "          error: true" })
        assertEquals(
            3,
            scripts.lines().count { it.contains("wk_payload is mapping") },
        )
        assertEquals(
            3,
            scripts.lines().count { it.contains("payload | from_json if") },
        )
        assertFalse(scripts.contains("\"error\""))
        // Fields are required at runtime, not only in HA's visual editor.
        assertTrue(scripts.contains("payload | default(none)"))
        assertTrue(scripts.contains("wk_payload['data'].get('settings') is mapping"))
    }
}
