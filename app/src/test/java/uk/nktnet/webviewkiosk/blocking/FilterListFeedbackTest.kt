package uk.nktnet.webviewkiosk.blocking

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterListFeedbackTest {
    @Test
    fun exceptionTextCannotLeakIntoUserMessages() {
        val diagnostic =
            "secret.example: Invalid filter syntax:\nLine 243: invalid domain restriction"
        listOf(
                IOException(diagnostic),
                IllegalArgumentException(diagnostic),
                RuntimeException(diagnostic),
            )
            .forEach { error ->
                val message = filterListFailureMessage(error)
                assertFalse(message.contains("secret"))
                assertFalse(message.contains("Line 243"))
                assertFalse(message.contains('\n'))
                assertTrue(message.length < 100)
            }
    }

    @Test
    fun batchFeedbackContainsOnlyCountsAndAnActionableSummary() {
        val result =
            FilterRefreshResult(
                6,
                9,
                listOf(
                    FilterRefreshFailure(
                        "untrusted.example",
                        "Line 243:\nInvalid filter syntax",
                    )
                ),
            )
        assertEquals(
            "Added 6 of 9 lists. Retry to add the remaining lists.",
            result.feedback(FilterListBatchAction.ADD_DEFAULTS),
        )
        assertEquals(
            "Refreshed 6 of 9 lists. Existing lists were kept for the others.",
            result.feedback(FilterListBatchAction.REFRESH),
        )
        assertFalse(
            result.feedback(FilterListBatchAction.REFRESH).contains("untrusted")
        )
    }

    @Test
    fun successfulAndEmptyBatchesHaveClearMessages() {
        val none = FilterRefreshResult(0, 0, emptyList())
        assertEquals(
            "The default lists are already added.",
            none.feedback(FilterListBatchAction.ADD_DEFAULTS),
        )
        assertEquals(
            "There are no downloaded lists to refresh.",
            none.feedback(FilterListBatchAction.REFRESH),
        )
        assertEquals(
            "Added 9 default lists.",
            FilterRefreshResult(9, 9, emptyList())
                .feedback(FilterListBatchAction.ADD_DEFAULTS),
        )
    }
}
