package uk.nktnet.webviewkiosk.utils.webview

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayingMediaFramesTest {
    @Test
    fun aPlayingDocumentKeepsSessionActiveUntilItPauses() {
        val frames = PlayingMediaFrames()
        assertFalse(frames.isPlaying)
        assertTrue(frames.update("main", true))
        assertTrue(frames.update("main", true))
        assertFalse(frames.update("main", false))
    }

    @Test
    fun oneIframePausingDoesNotStopAnotherPlayingFrame() {
        val frames = PlayingMediaFrames()
        assertTrue(frames.update("main", true))
        assertTrue(frames.update("iframe", true))
        assertTrue(frames.update("main", false))
        assertFalse(frames.update("iframe", false))
    }

    @Test
    fun invalidFramesAndNavigationResetCannotKeepServiceRunning() {
        val frames = PlayingMediaFrames()
        assertFalse(frames.update("", true))
        assertFalse(frames.update("x".repeat(129), true))
        assertTrue(frames.update("previous-page", true))
        frames.clear()
        assertFalse(frames.isPlaying)
        assertFalse(frames.update("previous-page", false))
    }
}
