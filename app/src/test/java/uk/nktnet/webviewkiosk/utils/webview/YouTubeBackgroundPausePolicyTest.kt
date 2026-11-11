package uk.nktnet.webviewkiosk.utils.webview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YouTubeBackgroundPausePolicyTest {
    @Test
    fun youtubePauseBeforeHomeGetsShortGraceInsteadOfImmediateSessionStop() {
        assertEquals(
            YouTubeBackgroundPausePolicy.FOREGROUND_GRACE_MS,
            YouTubeBackgroundPausePolicy.gracePeriod(true, true, false, false),
        )
    }

    @Test
    fun youtubePauseAfterHomeKeepsServiceLongEnoughToRetry() {
        assertEquals(
            YouTubeBackgroundPausePolicy.BACKGROUND_GRACE_MS,
            YouTubeBackgroundPausePolicy.gracePeriod(true, true, false, true),
        )
    }

    @Test
    fun noSessionOrUserStopNeverTriggersAutomaticRecovery() {
        assertNull(
            YouTubeBackgroundPausePolicy.gracePeriod(true, false, false, true)
        )
        assertNull(YouTubeBackgroundPausePolicy.gracePeriod(true, true, true, true))
        assertNull(
            YouTubeBackgroundPausePolicy.gracePeriod(false, true, false, true)
        )
    }
}
