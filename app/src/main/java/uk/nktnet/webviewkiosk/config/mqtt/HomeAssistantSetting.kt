package uk.nktnet.webviewkiosk.config.mqtt

import android.content.Context
import android.content.RestrictionEntry
import org.xmlpull.v1.XmlPullParser
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.Constants
import uk.nktnet.webviewkiosk.config.UserSettingsKeys

/** Use the same setting keys, types, and enum values as managed configuration. */
data class HomeAssistantSetting(
    val key: String,
    val name: String,
    val type: Int,
    val options: List<String> = emptyList(),
) {
    val componentId = "setting_${key.replace('.', '_')}"
    val isPassword = key in passwordKeys

    val range: IntRange
        get() =
            when (key) {
                UserSettingsKeys.Device.BRIGHTNESS -> -1..100
                UserSettingsKeys.Mqtt.Connection.SERVER_PORT -> 1..65535
                UserSettingsKeys.Mqtt.Connection.KEEP_ALIVE,
                UserSettingsKeys.Mqtt.Restrictions.RECEIVE_MAXIMUM,
                UserSettingsKeys.Mqtt.Restrictions.SEND_MAXIMUM,
                UserSettingsKeys.Mqtt.Restrictions.TOPIC_ALIAS_MAXIMUM,
                UserSettingsKeys.Mqtt.Restrictions.SEND_TOPIC_ALIAS_MAXIMUM ->
                    0..65535
                UserSettingsKeys.Mqtt.Restrictions.MAXIMUM_PACKET_SIZE,
                UserSettingsKeys.Mqtt.Restrictions.SEND_MAXIMUM_PACKET_SIZE ->
                    0..268435460
                else -> 0..Constants.MAX_INT_SETTING
            }

    companion object {
        val passwordKeys =
            setOf(
                UserSettingsKeys.Device.CUSTOM_AUTH_PASSWORD,
                UserSettingsKeys.Mqtt.Connection.PASSWORD,
            )
        private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

        fun load(context: Context): List<HomeAssistantSetting> = buildList {
            context.resources.getXml(R.xml.app_restrictions).use { xml ->
                while (xml.next() != XmlPullParser.END_DOCUMENT) {
                    if (
                        xml.eventType != XmlPullParser.START_TAG ||
                            xml.name != "restriction"
                    ) {
                        continue
                    }
                    val key = xml.getAttributeValue(ANDROID_NS, "key") ?: continue
                    val title = xml.getAttributeResourceValue(ANDROID_NS, "title", 0)
                    val values =
                        xml.getAttributeResourceValue(ANDROID_NS, "entryValues", 0)
                    add(
                        HomeAssistantSetting(
                            key,
                            "${key.substringBeforeLast('.').replace('.', ' ').replace('_', ' ')}: ${context.getString(title)}",
                            xml.getAttributeIntValue(
                                ANDROID_NS,
                                "restrictionType",
                                RestrictionEntry.TYPE_NULL,
                            ),
                            if (values == 0) {
                                emptyList()
                            } else {
                                context.resources.getStringArray(values).toList()
                            },
                        )
                    )
                }
            }
        }
    }
}
