package uk.nktnet.webviewkiosk.utils

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser
import uk.nktnet.webviewkiosk.R

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DeviceOwnerPolicyBackupRulesTest {
    private fun excludedPreferences(resourceId: Int): List<String> {
        val context = RuntimeEnvironment.getApplication() as Context
        val paths = mutableListOf<String>()
        context.resources.getXml(resourceId).use { parser ->
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (
                    parser.eventType == XmlPullParser.START_TAG &&
                        parser.name == "exclude" &&
                        parser.getAttributeValue(null, "domain") == "sharedpref"
                ) {
                    paths.add(parser.getAttributeValue(null, "path"))
                }
            }
        }
        return paths
    }

    @Test
    fun internalDevicePolicyMarkerIsNeverBackedUpOrTransferred() {
        val marker = "device_owner_status_bar_policy.xml"
        assertEquals(listOf(marker), excludedPreferences(R.xml.backup_rules))
        assertEquals(
            listOf(marker, marker),
            excludedPreferences(R.xml.data_extraction_rules),
        )
    }
}
