package uk.nktnet.webviewkiosk.utils.webview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaPlaybackInfoTest {
    @Test
    fun parsesPlayableMetadataAndTransportCapabilities() {
        val info =
            MediaPlaybackInfo.parse(
                """{"title":"A video","artist":"A channel","artworkUrl":"https://i.ytimg.com/vi/abcdefghijk/hqdefault.jpg","durationMs":200000,"positionMs":125000,"canNext":true,"canPrevious":false,"canSeek":true,"canChangeSpeed":true,"speed":1.25}"""
            )!!
        assertEquals("A video", info.title)
        assertEquals("A channel", info.artist)
        assertEquals(200_000, info.durationMs)
        assertEquals(125_000, info.positionMs)
        assertTrue(info.canNext)
        assertFalse(info.canPrevious)
        assertTrue(info.canSeek)
        assertEquals(1.25f, info.speed)
    }

    @Test
    fun limitsUntrustedInputAndDisablesUnavailableSeeking() {
        val info =
            MediaPlaybackInfo.parse(
                """{"title":"${"A".repeat(300)}","durationMs":null,"positionMs":-500,"canSeek":true,"speed":999}"""
            )!!
        assertEquals(200, info.title.length)
        assertEquals(-1L, info.durationMs)
        assertEquals(-1L, info.positionMs)
        assertFalse(info.canSeek)
        assertEquals(1f, info.speed)
        assertNull(MediaPlaybackInfo.parse("x".repeat(8_193)))
        assertNull(MediaPlaybackInfo.parse("not json"))
    }

    @Test
    fun progressOnlyDoesNotRequireNotificationRebuild() {
        val first = MediaPlaybackInfo(title = "Video", positionMs = 1_000)
        assertFalse(first.presentationChanged(first.copy(positionMs = 2_000)))
        assertTrue(first.presentationChanged(first.copy(canNext = true)))
        assertTrue(first.presentationChanged(first.copy(title = "Another video")))
    }
}
