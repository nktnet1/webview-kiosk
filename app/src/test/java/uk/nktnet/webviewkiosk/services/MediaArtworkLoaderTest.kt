package uk.nktnet.webviewkiosk.services

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MediaArtworkLoaderTest {
    @Test
    fun permitsYouTubeAndSameOriginHttpsArtworkOnly() {
        assertTrue(
            MediaArtworkLoader.isEligibleUrl(
                "https://i.ytimg.com/vi/abcdefghijk/hqdefault.jpg",
                "example.com",
            )
        )
        assertTrue(
            MediaArtworkLoader.isEligibleUrl(
                "https://example.com/images/cover.png",
                "example.com",
            )
        )
        assertFalse(
            MediaArtworkLoader.isEligibleUrl(
                "https://other.example.com/private",
                "example.com",
            )
        )
        assertFalse(
            MediaArtworkLoader.isEligibleUrl(
                "http://i.ytimg.com/vi/id/default.jpg",
                "youtube.com",
            )
        )
        assertFalse(
            MediaArtworkLoader.isEligibleUrl(
                "https://user:password@example.com/a",
                "example.com",
            )
        )
        assertFalse(
            MediaArtworkLoader.isEligibleUrl(
                "https://example.com:8443/a",
                "example.com",
            )
        )
    }
}
