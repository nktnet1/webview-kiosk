package uk.nktnet.webviewkiosk.utils

import android.content.Context
import android.media.AudioManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class MediaVolumeTest {
    @Test
    fun percentagesMapToTheNearestStreamIndex() {
        assertEquals(0, volumeIndexFromPercentage(0, 0, 15))
        assertEquals(8, volumeIndexFromPercentage(50, 0, 15))
        assertEquals(15, volumeIndexFromPercentage(100, 0, 15))
        assertEquals(10, volumeIndexFromPercentage(40, 0, 25))
    }

    @Test
    fun outOfRangePercentagesAreClampedToTheStreamLimits() {
        assertEquals(0, volumeIndexFromPercentage(-20, 0, 15))
        assertEquals(15, volumeIndexFromPercentage(250, 0, 15))
        // Some devices do not allow the media stream to go below index 1.
        assertEquals(1, volumeIndexFromPercentage(0, 1, 15))
        assertEquals(0, volumeIndexFromPercentage(50, 0, 0))
    }

    @Test
    fun indexesReportTheirPercentageOfTheMaximum() {
        assertEquals(0, volumePercentageFromIndex(0, 15))
        assertEquals(53, volumePercentageFromIndex(8, 15))
        assertEquals(100, volumePercentageFromIndex(15, 15))
        assertEquals(40, volumePercentageFromIndex(10, 25))
        assertEquals(0, volumePercentageFromIndex(3, 0))
    }

    @Test
    fun setsAndReadsTheMediaStreamVolume() {
        val context = RuntimeEnvironment.getApplication()
        val audioManager =
            context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

        assertTrue(setMediaVolumePercentage(context, 100))
        assertEquals(max, audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
        assertEquals(100, getMediaVolumePercentage(context))

        assertTrue(setMediaVolumePercentage(context, 0))
        assertEquals(0, audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
        assertEquals(0, getMediaVolumePercentage(context))
    }
}
