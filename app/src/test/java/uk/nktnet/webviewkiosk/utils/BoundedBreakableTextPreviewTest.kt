package uk.nktnet.webviewkiosk.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedBreakableTextPreviewTest {
    @Test
    fun shortLabelsStillWrapBetweenCharacters() {
        assertEquals("", boundedBreakableTextPreview(""))
        assertEquals("a\u200Bb\u200Bc", boundedBreakableTextPreview("abc"))
        assertEquals("a\u200B/\u200Bb", boundedBreakableTextPreview("a/b"))
    }

    @Test
    fun hugeUnbrokenInputsAreBoundedBeforeTextShaping() {
        val hugeUrl = "https://example.invalid/" + "x".repeat(100_000)
        val result = boundedBreakableTextPreview(hugeUrl)
        assertTrue(result.endsWith("\u2026"))
        assertTrue(result.length <= 512)
        assertFalse(result.contains("x".repeat(256)))
    }

    @Test
    fun surrogatePairsAreNotSeparatedByZeroWidthSpaces() {
        assertEquals(
            "A\u200B\uD83D\uDE00\u200BB",
            boundedBreakableTextPreview("A\uD83D\uDE00B"),
        )
        assertEquals(
            "A\u200B\u2026",
            boundedBreakableTextPreview("A\uD83D\uDE00BC", 3),
        )
        assertEquals(
            "\uD83D\uDE00\u200B\u2026",
            boundedBreakableTextPreview("\uD83D\uDE00abc", 3),
        )
    }
}
