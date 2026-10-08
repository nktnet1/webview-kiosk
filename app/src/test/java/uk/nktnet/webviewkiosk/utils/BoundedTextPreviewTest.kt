package uk.nktnet.webviewkiosk.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BoundedTextPreviewTest {
    @Test
    fun leavesTextAtOrBelowTheLimitUnchanged() {
        assertEquals("", boundedTextPreview("", 4))
        assertEquals("abc", boundedTextPreview("abc", 4))
        assertEquals("abcd", boundedTextPreview("abcd", 4))
    }

    @Test
    fun includesTheEllipsisWithinTheLimit() {
        assertEquals("abc\u2026", boundedTextPreview("abcde", 4))
        assertEquals("\u2026", boundedTextPreview("ab", 1))
        assertEquals("a".repeat(511) + "\u2026", boundedTextPreview("a".repeat(513)))
    }

    @Test
    fun neverSplitsASurrogatePairAtTheTruncationBoundary() {
        val text = "A\uD83D\uDE00BC"
        assertEquals("A\u2026", boundedTextPreview(text, 3))
        assertEquals("A\uD83D\uDE00\u2026", boundedTextPreview(text, 4))
        assertEquals("\u2026", boundedTextPreview("\uD83D\uDE00AB", 2))
    }

    @Test
    fun rejectsNonPositiveLimitsEvenForEmptyText() {
        for (limit in listOf(0, -1, Int.MIN_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) {
                boundedTextPreview("", limit)
            }
        }
    }
}
