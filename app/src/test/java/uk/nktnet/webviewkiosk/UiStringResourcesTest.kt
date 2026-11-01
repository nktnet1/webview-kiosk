package uk.nktnet.webviewkiosk

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Regression checks for exact on-screen text after moving Kotlin literals to
 * resources.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class UiStringResourcesTest {
    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun multilineSettingDescriptionsRetainIndentationAndLineBreaks() {
        assertEquals(
            "Set to true to give WebView access to your device's camera.\n\n" +
                "You will need to grant the CAMERA permission, which is required for the\n" +
                "WebView's RESOURCE_VIDEO_CAPTURE feature.",
            context.getString(R.string.setting_info_allow_camera),
        )
    }

    @Test
    fun visibleWhitespaceAndPunctuationArePreserved() {
        assertEquals(
            "Returning automatically in ",
            context.getString(R.string.ui_returning_automatically_in),
        )
        assertEquals(
            "Content blocking is disabled. The settings below will not take effect.",
            context.getString(
                R.string.ui_content_blocking_is_disabled_the_settings_below_will_n
            ),
        )
        assertEquals(
            "This list is too large to edit inline. Choose a replacement text file instead.",
            context.getString(
                R.string.ui_this_list_is_too_large_to_edit_inline_choose_a_replace
            ),
        )
        assertEquals(
            "Open file://",
            context.getString(R.string.ui_open_file),
        )
    }

    @Test
    fun formattedStringsRenderTheSameWordsAndSymbols() {
        assertEquals("55%", context.getString(R.string.ui_value_3, 55))
        assertEquals("True ", context.getString(R.string.ui_true_value, ""))
        assertEquals(
            "True (no permission)",
            context.getString(R.string.ui_true_value, "(no permission)"),
        )
        assertEquals(
            "Sticky mode enabled.",
            context.getString(R.string.ui_sticky_mode_value, "enabled."),
        )
    }

    @Test
    fun numericValuesAndListCountsKeepTheirOriginalDisplay() {
        assertEquals(
            "Max 10 characters",
            context.getString(R.string.ui_max_value_characters, 10.toString()),
        )
        assertEquals(
            "Tap 3 more times to open the Kiosk Control Panel",
            context.getString(
                R.string.ui_tap_value_more_times_to_open_the_kiosk_control_panel,
                3.toString(),
            ),
        )
        assertEquals(
            "2/5 enabled",
            context.getString(
                R.string.ui_lists_enabled_count,
                2.toString(),
                5.toString(),
            ),
        )
        assertEquals(
            "Error: kiosk launch requires Android API 28+ (current: 27).",
            context.getString(
                R.string.ui_error_kiosk_launch_requires_android_api_value_current,
                28.toString(),
                27.toString(),
            ),
        )
    }

    @Test
    fun multilineDialogMessagesRetainWhitespaceAndDynamicFields() {
        assertEquals(
            "Are you sure you want to delete this file?\n\n  notes.txt\n\n" +
                "This will only remove the app's copy and not the original file on your device.",
            context.getString(R.string.ui_confirm_delete_local_file, "notes.txt"),
        )
        assertEquals(
            "Host requested access to your location.\n\n" +
                "However, location permission is either disabled in settings or\n" +
                "not yet granted to Webview Kiosk.",
            context.getString(
                R.string.ui_location_permission_not_available,
                "Host",
                "Webview Kiosk",
            ),
        )
        assertEquals(
            "Expired\n\nURL:\n    https://example.com",
            context.getString(
                R.string.ui_ssl_error_details,
                "Expired",
                "https://example.com",
            ),
        )
    }

    @Test
    fun codeAndRegexExamplesRemainExactlyTheSame() {
        assertEquals(
            "e.g.\n    ^https://trusted\\.org(/.*)?$\n    ^https://.*\\.trusted\\.org(/.*)?$",
            context.getString(R.string.ui_website_whitelist_example),
        )
        assertEquals(
            "{\n  \"message\": \"Client has disconnected.\",\n" +
                "  \"appInstanceId\": \"${'$'}{APP_INSTANCE_ID}\"\n}",
            context.getString(
                R.string.ui_mqtt_will_payload_example,
                "APP_INSTANCE_ID",
            ),
        )
        assertEquals(
            "e.g.\n    <div style=\"text-align:center; margin-top:10%;\">\n" +
                "      <h1 style=\"font-size:4rem;\">Blocked</h1>\n" +
                "      <p style=\"font-size:3rem;\">This site is not accessible.</p>\n" +
                "    </div>\n\nor as a redirect:\n" +
                "    <meta http-equiv=\"refresh\" content=\"0; url=https://example.org\">",
            context.getString(
                R.string.ui_custom_block_page_html_example,
                "https://example.org",
            ),
        )
    }

    @Test
    fun mqttVariableExamplesKeepTheirLiteralDollarBraces() {
        assertEquals(
            "The MQTT topic to publish event messages.\n\n" +
                "Supported variables:\n- EVENT_TYPE\n- APP_INSTANCE_ID\n- USERNAME\n\n" +
                "Example:\n- wk/event/\${EVENT_TYPE}",
            context.getString(
                R.string.setting_info_mqtt_publish_event_topic,
                "EVENT_TYPE",
                "APP_INSTANCE_ID",
                "USERNAME",
            ),
        )
        assertEquals(
            "The MQTT payload to send for the last will message if the client\n" +
                "disconnects unexpectedly.\n\nFor example,\n  {\n" +
                "    \"message\": \"Client has disconnected.\",\n" +
                "    \"username\": \"\${USERNAME}\",\n" +
                "    \"appInstanceId\": \"\${APP_INSTANCE_ID}\"\n  }",
            context.getString(
                R.string.setting_info_mqtt_will_payload,
                "USERNAME",
                "APP_INSTANCE_ID",
            ),
        )
    }

    @Test
    fun immutableTechnicalResourcesKeepTheirExactText() {
        assertEquals("homeassistant", context.getString(R.string.ui_homeassistant))
        assertEquals(
            "homeassistant/status",
            context.getString(R.string.ui_homeassistant_status),
        )
        assertEquals("/mqtt", context.getString(R.string.ui_mqtt))
        assertEquals(
            "SYSTEM",
            context.resources.getStringArray(R.array.entry_values_theme_option)[0],
        )
        assertEquals(
            "https://webviewkiosk.nktnet.uk/docs/settings/mqtt/",
            context.getString(R.string.mqtt_enabled_description),
        )
        assertEquals(
            "e.g. document.body.style.backgroundColor = 'green';",
            context.getString(
                R.string.ui_e_g_document_body_style_backgroundcolor_green
            ),
        )
    }

    @Test
    fun contextualStatusAndAccessibilityStringsKeepTheirFormatting() {
        assertEquals(
            "Sticky mode enabled.",
            context.getString(
                R.string.ui_sticky_mode_value,
                context.getString(R.string.ui_enabled_2),
            ),
        )
        assertEquals(
            "Sticky mode disabled.",
            context.getString(
                R.string.ui_sticky_mode_value,
                context.getString(R.string.ui_disabled_2),
            ),
        )
        assertEquals(
            "Distributor (registered)",
            context.getString(R.string.ui_distributorvalue, " (registered)"),
        )
        assertEquals(
            "Filter example",
            context.getString(R.string.ui_filter_value, "example"),
        )
        assertEquals(
            "Host: example.com",
            context.getString(R.string.ui_host_value, "example.com"),
        )
        assertEquals(
            "25 min ago",
            context.getString(R.string.ui_value_min_ago, "25"),
        )
        assertEquals(
            "1080 x 1920 px, density: 2.5",
            context.getString(
                R.string.ui_value_x_value_px_density_value,
                1080,
                1920,
                2.5,
            ),
        )
    }
}
