package uk.nktnet.webviewkiosk.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser
import uk.nktnet.webviewkiosk.R

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MediaNotificationIconTest {
    @Suppress("DEPRECATION")
    @Test
    fun disabledIconsKeepTheirGeometryAndOnlyChangeOpacity() {
        val resources = RuntimeEnvironment.getApplication().resources
        val pairs =
            listOf(
                R.drawable.media_previous to R.drawable.media_previous_disabled,
                R.drawable.media_next to R.drawable.media_next_disabled,
                R.drawable.media_volume_on to R.drawable.media_volume_disabled,
            )
        for ((enabledId, disabledId) in pairs) {
            val enabled = resources.getDrawable(enabledId, null)
            val disabled = resources.getDrawable(disabledId, null)
            assertEquals(enabled.intrinsicWidth, disabled.intrinsicWidth)
            assertEquals(enabled.intrinsicHeight, disabled.intrinsicHeight)
            fun paths(id: Int): List<Pair<String, Float>> {
                val result = mutableListOf<Pair<String, Float>>()
                resources.getXml(id).use { parser ->
                    while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                        if (
                            parser.eventType == XmlPullParser.START_TAG &&
                                parser.name == "path"
                        ) {
                            val namespace =
                                "http://schemas.android.com/apk/res/android"
                            result.add(
                                parser.getAttributeValue(namespace, "pathData") to
                                    parser.getAttributeFloatValue(
                                        namespace,
                                        "fillAlpha",
                                        1f,
                                    )
                            )
                        }
                        parser.next()
                    }
                }
                return result
            }
            val enabledPaths = paths(enabledId)
            val disabledPaths = paths(disabledId)
            assertTrue(enabledPaths.isNotEmpty())
            assertEquals(
                enabledPaths.map { it.first },
                disabledPaths.map { it.first },
            )
            assertTrue(enabledPaths.all { it.second == 1f })
            assertTrue(disabledPaths.all { it.second in 0f..<1f })
        }
    }

    @Suppress("DEPRECATION")
    @Test
    fun closeAndTransportIconsUseTheSameCanvasSize() {
        val resources = RuntimeEnvironment.getApplication().resources
        val icons =
            listOf(
                    R.drawable.media_previous,
                    R.drawable.media_next,
                    R.drawable.media_play,
                    R.drawable.media_pause,
                    R.drawable.media_close,
                    R.drawable.media_volume_on,
                    R.drawable.media_volume_off,
                )
                .map { resources.getDrawable(it, null) }
        assertTrue(icons.first().intrinsicWidth > 0)
        for (icon in icons) {
            assertEquals(icons.first().intrinsicWidth, icon.intrinsicWidth)
            assertEquals(icons.first().intrinsicHeight, icon.intrinsicHeight)
        }
    }
}
