package uk.nktnet.webviewkiosk.managers

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundRequestMessage
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundSettingsMessage
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundStatusRequest
import uk.nktnet.webviewkiosk.managers.RemoteMessageManager.RemoteMessage
import uk.nktnet.webviewkiosk.managers.RemoteMessageManager.RemoteMessage.Source
import java.util.Collections

class RemoteSettingsRequestDispatchTest {
    @Test
    fun settingsKeepSubmissionOrderWhenASubscriberBlocksBeyondTheBufferCapacity() {
        assertOrdered(RemoteMessageManager.settingsFlow, { it.messageId }) { index ->
            RemoteMessageManager.emitSettings(
                InboundSettingsMessage(messageId = index.toString()),
                Source.MQTT,
            )
        }
    }

    @Test
    fun requestsKeepSubmissionOrderWhenASubscriberBlocksBeyondTheBufferCapacity() {
        assertOrdered(RemoteMessageManager.requestsFlow, { it.messageId }) { index ->
            RemoteMessageManager.emitRequest(
                InboundStatusRequest(messageId = index.toString()),
                Source.MQTT,
            )
        }
    }

    @Test
    fun overlappingSettingsCollectorsShareOneClaimForEachDelivery() {
        assertClaimedOnce(RemoteMessageManager.settingsFlow, { it.messageId }) { index ->
            RemoteMessageManager.emitSettings(
                InboundSettingsMessage(messageId = index.toString()),
                Source.MQTT,
            )
        }
    }

    @Test
    fun overlappingRequestCollectorsShareOneClaimForEachDelivery() {
        assertClaimedOnce(RemoteMessageManager.requestsFlow, { it.messageId }) { index ->
            RemoteMessageManager.emitRequest(
                InboundStatusRequest(messageId = index.toString()),
                Source.MQTT,
            )
        }
    }

    @Test
    fun blockedSettingsCannotDelayRequestsOrAppliedSettingsNotifications() = runBlocking {
        val settingsStarted = CompletableDeferred<Unit>()
        val releaseSettings = CompletableDeferred<Unit>()
        val requests = mutableListOf<RemoteMessage<InboundRequestMessage>>()
        val applied = mutableListOf<RemoteMessage<InboundSettingsMessage>>()
        val settingsCollector = launch(start = CoroutineStart.UNDISPATCHED) {
            RemoteMessageManager.settingsFlow.take(1).collect {
                settingsStarted.complete(Unit)
                releaseSettings.await()
            }
        }
        val requestCollector = launch(start = CoroutineStart.UNDISPATCHED) {
            RemoteMessageManager.requestsFlow.take(1).collect { requests.add(it) }
        }
        val appliedCollector = launch(start = CoroutineStart.UNDISPATCHED) {
            RemoteMessageManager.settingsAppliedFlow.take(1).collect { applied.add(it) }
        }
        try {
            RemoteMessageManager.emitSettings(
                InboundSettingsMessage(messageId = "blocked"),
                Source.MQTT,
            )
            withTimeout(5_000) { settingsStarted.await() }
            RemoteMessageManager.emitRequest(
                InboundStatusRequest(messageId = "request"),
                Source.MQTT,
            )
            RemoteMessageManager.emitSettingsApplied(
                InboundSettingsMessage(messageId = "applied"),
                Source.UNIFIEDPUSH,
            )
            withTimeout(5_000) {
                requestCollector.join()
                appliedCollector.join()
            }

            assertFalse(releaseSettings.isCompleted)
            assertEquals("request", requests.single().message.messageId)
            assertEquals(Source.MQTT, requests.single().source)
            assertEquals("applied", applied.single().message.messageId)
            assertEquals(Source.UNIFIEDPUSH, applied.single().source)
        } finally {
            releaseSettings.complete(Unit)
            withTimeout(5_000) { settingsCollector.join() }
            settingsCollector.cancelAndJoin()
            requestCollector.cancelAndJoin()
            appliedCollector.cancelAndJoin()
        }
    }

    private fun <T> assertOrdered(
        flow: SharedFlow<RemoteMessage<T>>,
        messageId: (T) -> String?,
        emit: (Int) -> Unit,
    ) = runBlocking {
        val count = 256
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val observed = mutableListOf<String?>()
        val collector = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
            flow.take(count).collect {
                if (observed.isEmpty()) {
                    firstStarted.complete(Unit)
                    releaseFirst.await()
                }
                observed.add(messageId(it.message))
            }
        }
        try {
            emit(0)
            withTimeout(5_000) { firstStarted.await() }
            // More than 100 deliveries forces the producer's FIFO queue to suspend.
            for (index in 1 until count) emit(index)
            releaseFirst.complete(Unit)
            withTimeout(5_000) { collector.join() }

            assertEquals((0 until count).map { it.toString() }, observed)
        } finally {
            releaseFirst.complete(Unit)
            collector.cancelAndJoin()
        }
    }

    private fun <T> assertClaimedOnce(
        flow: SharedFlow<RemoteMessage<T>>,
        messageId: (T) -> String?,
        emit: (Int) -> Unit,
    ) = runBlocking {
        val count = 128
        val first = mutableListOf<RemoteMessage<T>>()
        val second = mutableListOf<RemoteMessage<T>>()
        val claimed = Collections.synchronizedList(mutableListOf<String?>())
        val collectors = listOf(first, second).map { observed ->
            launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                flow.take(count).collect {
                    observed.add(it)
                    if (it.tryClaim()) claimed.add(messageId(it.message))
                }
            }
        }
        try {
            for (index in 0 until count) emit(index)
            withTimeout(5_000) { collectors.forEach { it.join() } }

            assertEquals(count, claimed.size)
            assertEquals((0 until count).map { it.toString() }.toSet(), claimed.toSet())
            assertEquals((0 until count).map { it.toString() }, first.map { messageId(it.message) })
            assertEquals((0 until count).map { it.toString() }, second.map { messageId(it.message) })
            for (index in 0 until count) assertSame(first[index], second[index])
        } finally {
            collectors.forEach { it.cancelAndJoin() }
        }
    }
}
