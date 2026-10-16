package uk.nktnet.webviewkiosk.ui.components.common

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class MinimumLoadingStateTest {
    @Test
    fun quickWorkRemainsVisibleUntilTheMinimumExpires() = runBlocking {
        var time = 0L
        var waited = 0L
        val release = CompletableDeferred<Unit>()
        val loading =
            MinimumLoadingState(
                now = { time },
                pause = {
                    waited = it
                    release.await()
                },
            )
        loading.show("Loading rules…")
        time = 100L
        val hide = async(start = CoroutineStart.UNDISPATCHED) { loading.hide() }
        val completion =
            async(start = CoroutineStart.UNDISPATCHED) { loading.awaitHidden() }
        assertEquals(900L, waited)
        assertEquals("Loading rules…", loading.message.value)
        assertFalse(hide.isCompleted)
        assertFalse(completion.isCompleted)
        time = 1_000L
        release.complete(Unit)
        hide.await()
        completion.await()
        assertNull(loading.message.value)
    }

    @Test
    fun slowWorkDoesNotAddAnotherSecond() = runBlocking {
        var time = 0L
        val loading =
            MinimumLoadingState(
                now = { time },
                pause = { error("No extra wait expected") },
            )
        loading.show("Downloading…")
        time = 1_500L
        loading.hide()
        assertNull(loading.message.value)
    }

    @Test
    fun phaseChangesKeepTheSameVisibleInterval() = runBlocking {
        var time = 0L
        var waited = 0L
        val loading = MinimumLoadingState(now = { time }, pause = { waited = it })
        loading.show("Reading lists…")
        time = 700L
        loading.show("Building indexes…")
        loading.hide()
        assertEquals(300L, waited)
        assertNull(loading.message.value)
    }

    @Test
    fun staleHideCannotClearNewWorkOrReleaseItsCompletionFeedback() = runBlocking {
        var time = 0L
        val release = CompletableDeferred<Unit>()
        val loading =
            MinimumLoadingState(now = { time }, pause = { release.await() })
        loading.show("Reading…")
        val oldHide = async(start = CoroutineStart.UNDISPATCHED) { loading.hide() }
        val completion =
            async(start = CoroutineStart.UNDISPATCHED) { loading.awaitHidden() }
        time = 500L
        loading.show("Compiling…")
        time = 1_000L
        release.complete(Unit)
        oldHide.await()
        assertEquals("Compiling…", loading.message.value)
        assertFalse(completion.isCompleted)
        loading.hide()
        completion.await()
        assertNull(loading.message.value)
    }

    @Test
    fun closingTheImportDialogStartsAMinimumForTheMainIndicator() = runBlocking {
        var time = 0L
        var waited = 0L
        val loading = MinimumLoadingState(now = { time }, pause = { waited = it })
        loading.show("Downloading…")
        time = 900L
        loading.show("Compiling…", restartMinimum = true)
        time = 1_100L
        loading.hide()
        assertEquals(800L, waited)
    }

    @Test
    fun cancellingAPendingHideKeepsTheMessageUntilTheNextCompletion() = runBlocking {
        var time = 0L
        val loading =
            MinimumLoadingState(
                now = { time },
                pause = { CompletableDeferred<Unit>().await() },
            )
        loading.show("Loading…")
        val pending = async(start = CoroutineStart.UNDISPATCHED) { loading.hide() }
        pending.cancel()
        pending.join()
        assertEquals("Loading…", loading.message.value)
        time = 1_000L
        loading.hide()
        assertNull(loading.message.value)
    }
}
