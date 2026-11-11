package uk.nktnet.webviewkiosk.utils.webview

import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundMediaWindowVisibilityTest {
    @Test
    fun activeSessionDefersInvisibleAndGoneUntilItEnds() {
        val guard = BackgroundMediaWindowVisibility()
        val delivered = mutableListOf<Int>()

        assertTrue(guard.dispatch(View.INVISIBLE, true, delivered::add))
        assertTrue(guard.dispatch(View.GONE, true, delivered::add))
        assertTrue(delivered.isEmpty())

        assertTrue(guard.release(delivered::add))
        assertEquals(listOf(View.GONE), delivered)
        assertFalse(guard.release(delivered::add))
    }

    @Test
    fun foregroundReturnClearsPendingWindowHide() {
        val guard = BackgroundMediaWindowVisibility()
        val delivered = mutableListOf<Int>()

        assertTrue(guard.dispatch(View.GONE, true, delivered::add))
        assertFalse(guard.dispatch(View.VISIBLE, true, delivered::add))
        assertFalse(guard.release(delivered::add))
        assertEquals(listOf(View.VISIBLE), delivered)
    }

    @Test
    fun fullscreenAndInlineViewsHaveIndependentDeferredStates() {
        val webView = BackgroundMediaWindowVisibility()
        val fullscreen = BackgroundMediaWindowVisibility()
        val webViewUpdates = mutableListOf<Int>()
        val fullscreenUpdates = mutableListOf<Int>()

        assertTrue(webView.dispatch(View.GONE, true, webViewUpdates::add))
        assertTrue(fullscreen.dispatch(View.GONE, true, fullscreenUpdates::add))
        assertTrue(webView.release(webViewUpdates::add))
        assertTrue(fullscreenUpdates.isEmpty())
        assertTrue(fullscreen.release(fullscreenUpdates::add))
        assertEquals(listOf(View.GONE), webViewUpdates)
        assertEquals(listOf(View.GONE), fullscreenUpdates)
    }

    @Test
    fun disabledOrIdlePlaybackStillNotifiesChromium() {
        val guard = BackgroundMediaWindowVisibility()
        val delivered = mutableListOf<Int>()

        assertFalse(guard.dispatch(View.GONE, false, delivered::add))
        assertFalse(guard.dispatch(View.INVISIBLE, false, delivered::add))
        assertEquals(listOf(View.GONE, View.INVISIBLE), delivered)
    }

    @Test
    fun userStopReleasesPendingHideAndFutureHidesAreForwarded() {
        val guard = BackgroundMediaWindowVisibility()
        val delivered = mutableListOf<Int>()

        assertTrue(guard.dispatch(View.GONE, true, delivered::add))
        assertTrue(guard.release(delivered::add))
        assertFalse(guard.dispatch(View.GONE, false, delivered::add))
        assertEquals(listOf(View.GONE, View.GONE), delivered)
    }
}
