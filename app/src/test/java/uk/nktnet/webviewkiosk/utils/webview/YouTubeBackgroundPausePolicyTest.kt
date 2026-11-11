package uk.nktnet.webviewkiosk.utils.webview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YouTubeBackgroundPausePolicyTest {
    @Test
    fun youtubePauseBeforeHomeGetsShortGraceInsteadOfImmediateSessionStop() {
        assertEquals(
            YouTubeBackgroundPausePolicy.FOREGROUND_GRACE_MS,
            YouTubeBackgroundPausePolicy.gracePeriod(
                wasYouTubePlaying = true,
                serviceRequested = true,
                userStopped = false,
                background = false,
            ),
        )
    }

    @Test
    fun youtubePauseAfterHomeKeepsServiceLongEnoughToRetry() {
        assertEquals(
            YouTubeBackgroundPausePolicy.BACKGROUND_GRACE_MS,
            YouTubeBackgroundPausePolicy.gracePeriod(
                wasYouTubePlaying = true,
                serviceRequested = true,
                userStopped = false,
                background = true,
            ),
        )
    }

    @Test
    fun noSessionOrUserStopNeverTriggersAutomaticRecovery() {
        assertNull(
            YouTubeBackgroundPausePolicy.gracePeriod(
                wasYouTubePlaying = true,
                serviceRequested = false,
                userStopped = false,
                background = true,
            )
        )
        assertNull(
            YouTubeBackgroundPausePolicy.gracePeriod(
                wasYouTubePlaying = true,
                serviceRequested = true,
                userStopped = true,
                background = true,
            )
        )
        assertNull(
            YouTubeBackgroundPausePolicy.gracePeriod(
                wasYouTubePlaying = false,
                serviceRequested = true,
                userStopped = false,
                background = true,
            )
        )
    }
}
