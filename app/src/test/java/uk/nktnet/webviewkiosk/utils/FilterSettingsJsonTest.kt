package uk.nktnet.webviewkiosk.utils

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import uk.nktnet.webviewkiosk.config.UserSettingsKeys

@RunWith(RobolectricTestRunner::class)
class FilterSettingsJsonTest {
    @Test
    fun unfilteredSettingsPreservePrimitiveTypes() {
        val settings = JSONObject().put("text", "value").put("enabled", true).put("count", 42)

        assertEquals(
            buildJsonObject {
                put("text", "value")
                put("enabled", true)
                put("count", 42)
            },
            filterSettingsJson(settings),
        )
    }

    @Test
    fun skipsUnknownKeysAndMalformedFilterElements() {
        val settings = JSONObject().put("enabled", true).put("count", 42)
        val filters = arrayOf<JsonElement>(
            JsonPrimitive("enabled"),
            buildJsonObject { put("key", "count") },
            JsonPrimitive("unknown"),
            JsonNull,
            JsonArray(emptyList()),
            buildJsonObject { put("key", JsonArray(emptyList())) },
            buildJsonObject { put("parseAsArray", true) },
        )

        assertEquals(
            buildJsonObject {
                put("enabled", true)
                put("count", 42)
            },
            filterSettingsJson(settings, filters),
        )
    }

    @Test
    fun parsesMultilineArraysOnlyForSupportedSettings() {
        val key = UserSettingsKeys.WebContent.WEBSITE_BLACKLIST
        val settings = JSONObject().put(key, "first\n\nsecond\n").put("other", "first\nsecond")
        val filters = arrayOf<JsonElement>(
            buildJsonObject {
                put("key", key)
                put("parseAsArray", true)
            },
            buildJsonObject {
                put("key", "other")
                put("parseAsArray", true)
            },
        )

        assertEquals(
            buildJsonObject {
                put(key, JsonArray(listOf(JsonPrimitive("first"), JsonPrimitive("second"))))
                put("other", "first\nsecond")
            },
            filterSettingsJson(settings, filters),
        )
    }

    @Test
    fun nonPrimitiveOptionsAreIgnoredWithoutCrashing() {
        val key = UserSettingsKeys.WebContent.WEBSITE_BOOKMARKS
        val settings = JSONObject().put(key, "first\nsecond")
        val filters = arrayOf<JsonElement>(
            buildJsonObject {
                put("key", key)
                put("parseAsArray", JsonArray(emptyList()))
                put("evaluateVariables", buildJsonObject {})
            },
        )

        assertEquals(buildJsonObject { put(key, "first\nsecond") }, filterSettingsJson(settings, filters))
    }
}
