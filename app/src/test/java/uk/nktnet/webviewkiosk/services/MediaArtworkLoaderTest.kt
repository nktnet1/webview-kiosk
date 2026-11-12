package uk.nktnet.webviewkiosk.services

import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MediaArtworkLoaderTest {
    @Test
    fun supersededArtworkDoesNotDelayTheLatestTrack() {
        val firstStarted = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val latestStarted = CountDownLatch(1)
        val requested = Collections.synchronizedList(mutableListOf<String>())
        val first = "https://example.com/first.jpg"
        val obsolete = "https://example.com/obsolete.jpg"
        val latest = "https://example.com/latest.jpg"
        val loader = MediaArtworkLoader { url ->
            requested.add(url)
            if (url == first) {
                firstStarted.countDown()
                releaseFirst.await(5, TimeUnit.SECONDS)
            }
            if (url == latest) {
                latestStarted.countDown()
            }
            null
        }
        try {
            loader.load(first, "example.com") {}
            assertTrue(firstStarted.await(5, TimeUnit.SECONDS))
            loader.load(obsolete, "example.com") {}
            loader.load(latest, "example.com") {}
            releaseFirst.countDown()
            assertTrue(latestStarted.await(5, TimeUnit.SECONDS))
            assertEquals(listOf(first, latest), requested.toList())
        } finally {
            releaseFirst.countDown()
            loader.close()
        }
    }

    @Test
    fun malformedArtworkDoesNotEscapeTheWorker() {
        val retrieve =
            MediaArtworkLoader.Companion::class
                .java
                .getDeclaredMethod("retrieve", String::class.java)
                .apply { isAccessible = true }
        assertNull(
            retrieve.invoke(
                MediaArtworkLoader.Companion,
                "https://i.ytimg.com:invalid/cover.jpg",
            )
        )
    }

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
