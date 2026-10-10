package uk.nktnet.webviewkiosk.managers

import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.LooperMode
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundGoBackCommand
import uk.nktnet.webviewkiosk.managers.RemoteMessageManager.RemoteMessage.Source

@RunWith(RobolectricTestRunner::class)
@LooperMode(LooperMode.Mode.PAUSED)
class RemoteMessageDispatchTest {
    @Test
    fun commandsRunInSubmissionOrderAndFlowObservesCompletedUiWork() = runBlocking {
        val events = mutableListOf<String>()
        val unregister = RemoteMessageManager.registerWebViewCommandHandler(
            canHandle = { true },
            handle = { command -> events.add("handled:${command.messageId}") },
        )
        val collector = launch(Dispatchers.Unconfined) {
            RemoteMessageManager.commandsFlow.take(3).collect { message ->
                events.add("observed:${message.message.messageId}")
            }
        }

        try {
            for (id in listOf("first", "second", "third")) {
                RemoteMessageManager.emitCommand(
                    InboundGoBackCommand(messageId = id, interact = false),
                    Source.UNIFIEDPUSH,
                )
            }
            awaitDispatch(collector)

            assertEquals(
                listOf(
                    "handled:first", "observed:first",
                    "handled:second", "observed:second",
                    "handled:third", "observed:third",
                ),
                events,
            )
        } finally {
            unregister()
            collector.cancel()
        }
    }

    @Test
    fun handlerFailureReportsErrorWithoutDroppingSubsequentCommands() = runBlocking {
        val events = mutableListOf<String>()
        val unregister = RemoteMessageManager.registerWebViewCommandHandler(
            canHandle = { true },
            handle = { command ->
                if (command.messageId == "broken") {
                    throw IllegalStateException("simulated failure")
                }
                events.add("handled:${command.messageId}")
            },
        )
        val collector = launch(Dispatchers.Unconfined) {
            RemoteMessageManager.commandsFlow.take(2).collect { message ->
                events.add("observed:${message.message.messageId}")
            }
        }

        try {
            RemoteMessageManager.emitCommand(
                InboundGoBackCommand(messageId = "broken", interact = false),
                Source.UNIFIEDPUSH,
                onFailure = { failure -> events.add("failed:${failure.message}") },
            )
            RemoteMessageManager.emitCommand(
                InboundGoBackCommand(messageId = "next", interact = false),
                Source.UNIFIEDPUSH,
            )
            awaitDispatch(collector)

            assertEquals(
                listOf(
                    "failed:simulated failure", "observed:broken",
                    "handled:next", "observed:next",
                ),
                events,
            )
        } finally {
            unregister()
            collector.cancel()
        }
    }

    @Test
    fun activeScreenIsResolvedForEveryCommandNotAtRegistrationTime() = runBlocking {
        val handledBy = mutableListOf<String>()
        var firstIsActive = true
        val unregisterFirst = RemoteMessageManager.registerWebViewCommandHandler(
            canHandle = { firstIsActive },
            handle = {
                handledBy.add("first:${it.messageId}")
                firstIsActive = false
            },
        )
        val unregisterSecond = RemoteMessageManager.registerWebViewCommandHandler(
            canHandle = { true },
            handle = { handledBy.add("second:${it.messageId}") },
        )
        val collector = launch(Dispatchers.Unconfined) {
            RemoteMessageManager.commandsFlow.take(2).collect { }
        }

        try {
            RemoteMessageManager.emitCommand(
                InboundGoBackCommand(messageId = "one", interact = false),
                Source.UNIFIEDPUSH,
            )
            RemoteMessageManager.emitCommand(
                InboundGoBackCommand(messageId = "two", interact = false),
                Source.UNIFIEDPUSH,
            )
            awaitDispatch(collector)

            assertEquals(listOf("first:one", "second:two"), handledBy)
        } finally {
            unregisterFirst()
            unregisterSecond()
            collector.cancel()
        }
    }

    private suspend fun awaitDispatch(collector: Job) {
        withTimeout(5_000) {
            // Each command may resume the next mutex waiter on a background dispatcher.
            // Pump the main looper repeatedly so queued follow-up commands cannot stall.
            while (!collector.isCompleted) {
                shadowOf(Looper.getMainLooper()).idle()
                delay(10)
            }
            collector.join()
        }
    }
}
