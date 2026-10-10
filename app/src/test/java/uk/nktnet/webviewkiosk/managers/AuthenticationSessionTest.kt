package uk.nktnet.webviewkiosk.managers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthenticationSessionTest {
    private var time = 0L
    private val session = AuthenticationSession { time }

    @Test
    fun aNewSessionIsInvalidEvenAtClockZero() {
        assertFalse(session.isValid())
        assertFalse(session.refreshIfValid())
    }

    @Test
    fun authenticationAtClockZeroIsValidUntilTheExactTimeout() {
        session.authenticate()
        assertTrue(session.isValid())

        time = 299_999
        assertTrue(session.isValid())
        time = 300_000
        assertFalse(session.isValid())
        assertFalse(session.refreshIfValid())
    }

    @Test
    fun refreshingAnActiveSessionStartsAnotherFiveMinuteInterval() {
        session.authenticate()
        time = 299_999
        assertTrue(session.refreshIfValid())

        time = 599_998
        assertTrue(session.isValid())
        time = 599_999
        assertFalse(session.isValid())
    }

    @Test
    fun anExpiredSessionCannotBeRestoredByOpeningAnExternalActivity() {
        session.authenticate()
        time = 300_000
        session.preserveForExternalActivity(60_000)
        session.reset(preserveExternalActivitySession = true)

        assertFalse(session.isValid())
    }

    @Test
    fun anUnauthenticatedExternalRoundTripCannotEstablishASession() {
        session.preserveForExternalActivity(60_000)
        session.reset(preserveExternalActivitySession = true)

        assertFalse(session.isValid())
        assertFalse(session.refreshIfValid())
    }

    @Test
    fun backgroundingWithoutAnExternalRoundTripClearsTheSession() {
        session.authenticate()
        session.reset(preserveExternalActivitySession = true)

        assertFalse(session.isValid())
    }

    @Test
    fun anExternalRoundTripOnlySurvivesBeforeItsExactDeadline() {
        session.authenticate()
        time = 100
        session.preserveForExternalActivity(5_000)
        session.reset(preserveExternalActivitySession = true)

        time = 5_099
        assertTrue(session.isValid())
        time = 5_100
        assertFalse(session.isValid())
    }

    @Test
    fun anExpiredExternalWindowIsNotExtendedByAStillValidOriginalSession() {
        session.authenticate()
        session.preserveForExternalActivity(5_000)
        time = 5_000
        session.reset(preserveExternalActivitySession = true)

        assertFalse(session.isValid())
    }

    @Test
    fun returningFromAnExternalActivityConsumesTheWindowAndRefreshesTheSession() {
        session.authenticate()
        session.preserveForExternalActivity(60_000)
        session.reset(preserveExternalActivitySession = true)
        time = 59_999
        assertTrue(session.refreshIfValid())

        time = 60_000
        assertTrue(session.isValid())
        session.reset(preserveExternalActivitySession = true)
        assertFalse(session.isValid())
    }

    @Test
    fun anExternalWindowCanOnlySurviveOneBackgroundTransition() {
        session.authenticate()
        session.preserveForExternalActivity(60_000)
        session.reset(preserveExternalActivitySession = true)
        assertTrue(session.isValid())

        session.reset(preserveExternalActivitySession = true)
        assertFalse(session.isValid())
    }

    @Test
    fun anExplicitLockClearsEvenAnActiveExternalWindow() {
        session.authenticate()
        session.preserveForExternalActivity(60_000)
        session.reset()

        assertFalse(session.isValid())
        assertFalse(session.refreshIfValid())
    }

    @Test
    fun zeroAndNegativeWindowsCannotPreserveASession() {
        for (duration in listOf(0L, -1L, Long.MIN_VALUE)) {
            session.authenticate()
            session.preserveForExternalActivity(duration)
            session.reset(preserveExternalActivitySession = true)

            assertFalse(session.isValid())
        }
    }

    @Test
    fun largeExternalDurationsDoNotOverflowIntoExpiredDeadlines() {
        time = 1_000
        session.authenticate()
        session.preserveForExternalActivity(Long.MAX_VALUE)
        session.reset(preserveExternalActivitySession = true)

        time = 301_000
        assertTrue(session.isValid())
        session.reset()
        assertFalse(session.isValid())
    }

    @Test
    fun aClockBeforeAuthenticationDoesNotValidateOrRefreshTheSession() {
        time = 1_000
        session.authenticate()
        session.preserveForExternalActivity(5_000)
        time = 999

        assertFalse(session.isValid())
        assertFalse(session.refreshIfValid())
    }

    @Test
    fun freshAuthenticationDiscardsTheOldExternalWindow() {
        session.authenticate()
        session.preserveForExternalActivity(60_000)
        session.reset(preserveExternalActivitySession = true)
        time = 100
        session.authenticate()
        session.reset(preserveExternalActivitySession = true)

        assertFalse(session.isValid())
    }
}
