package uk.nktnet.webviewkiosk.managers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.nktnet.webviewkiosk.managers.RemoteMessageManager.RemoteMessage
import uk.nktnet.webviewkiosk.testing.runConcurrently

class RemoteMessageManagerTest {
    @Test
    fun aMessageCanOnlyBeClaimedOnce() {
        val message = RemoteMessage("settings", RemoteMessage.Source.MQTT)

        assertTrue(message.tryClaim())
        assertFalse(message.tryClaim())
    }

    @Test
    fun separateDeliveriesHaveIndependentClaimsEvenForIdenticalPayloads() {
        val first = RemoteMessage("settings", RemoteMessage.Source.UNIFIEDPUSH)
        val second = RemoteMessage("settings", RemoteMessage.Source.UNIFIEDPUSH)

        assertTrue(first.tryClaim())
        assertTrue(second.tryClaim())
        assertFalse(first.tryClaim())
        assertFalse(second.tryClaim())
    }

    @Test
    fun competingCollectorsHaveExactlyOneWinner() {
        val message = RemoteMessage("settings", RemoteMessage.Source.MQTT)

        val claims = runConcurrently({ message.tryClaim() }, { message.tryClaim() }, { message.tryClaim() })

        assertEquals(1, claims.count { it })
        assertFalse(message.tryClaim())
    }
}
