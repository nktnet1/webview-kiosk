package uk.nktnet.webviewkiosk.utils

import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import java.util.concurrent.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SystemSafeClipboardTest {
    private class TestSystemRestart : RuntimeException()

    private class FakeClipboard : Clipboard {
        var failure: RuntimeException? = null
        var reads = 0
        var writes = 0

        @Suppress("OVERRIDE_DEPRECATION")
        override val nativeClipboard: android.content.ClipboardManager
            get() = error("Native clipboard is not used by these tests")

        override suspend fun getClipEntry(): ClipEntry? {
            reads++
            failure?.let { throw it }
            return null
        }

        override suspend fun setClipEntry(clipEntry: ClipEntry?) {
            writes++
            failure?.let { throw it }
        }
    }

    private fun guarded(
        delegate: Clipboard,
        failures: MutableList<RuntimeException>,
    ) = SystemSafeClipboard(
        delegate = delegate,
        onSystemRestart = { failures.add(it) },
        isSystemRestart = { hasCauseNamed(it, setOf(TestSystemRestart::class.java.name)) },
    )

    @Test
    fun delegatesSuccessfulReadsAndWrites() = runBlocking {
        val delegate = FakeClipboard()
        val failures = mutableListOf<RuntimeException>()
        val clipboard = guarded(delegate, failures)

        assertNull(clipboard.getClipEntry())
        clipboard.setClipEntry(null)

        assertEquals(1, delegate.reads)
        assertEquals(1, delegate.writes)
        assertTrue(failures.isEmpty())
    }

    @Test
    fun containsRestartFailuresOnBothReadsAndWrites() = runBlocking {
        val failure = RuntimeException("Binder call failed", TestSystemRestart())
        val delegate = FakeClipboard().apply { this.failure = failure }
        val failures = mutableListOf<RuntimeException>()
        val clipboard = guarded(delegate, failures)

        assertNull(clipboard.getClipEntry())
        clipboard.setClipEntry(null)

        assertEquals(2, failures.size)
        assertSame(failure, failures[0])
        assertSame(failure, failures[1])
    }

    @Test
    fun propagatesUnrelatedReadAndWriteFailures() = runBlocking {
        val failure = IllegalStateException("Clipboard bug")
        val delegate = FakeClipboard().apply { this.failure = failure }
        val failures = mutableListOf<RuntimeException>()
        val clipboard = guarded(delegate, failures)

        assertRethrown(failure) { clipboard.getClipEntry() }
        assertRethrown(failure) { clipboard.setClipEntry(null) }
        assertTrue(failures.isEmpty())
    }

    @Test
    fun preservesCancellationEvenWhenItsCauseIsASystemRestart() = runBlocking {
        val failure = CancellationException("Cancelled").apply { initCause(TestSystemRestart()) }
        val delegate = FakeClipboard().apply { this.failure = failure }
        val failures = mutableListOf<RuntimeException>()
        val clipboard = guarded(delegate, failures)

        assertRethrown(failure) { clipboard.getClipEntry() }
        assertRethrown(failure) { clipboard.setClipEntry(null) }
        assertTrue(failures.isEmpty())
    }

    @Test
    fun findsRestartTypesDeepInTheCauseChain() {
        var failure: Throwable = TestSystemRestart()
        repeat(20) { failure = RuntimeException("Wrapper", failure) }

        assertTrue(hasCauseNamed(failure, setOf(TestSystemRestart::class.java.name)))
    }

    @Test
    fun handlesCyclicCausesWithoutLooping() {
        val first = RuntimeException("First")
        val second = RuntimeException("Second", first)
        first.initCause(second)

        assertFalse(hasCauseNamed(first, setOf(TestSystemRestart::class.java.name)))
    }

    @Test
    fun ignoresExceptionNamesInMessages() {
        assertFalse(isSystemRestartFailure(RuntimeException("android.os.DeadSystemException")))
        assertFalse(isSystemRestartFailure(IllegalStateException("Normal failure")))
    }

    private suspend fun assertRethrown(expected: RuntimeException, operation: suspend () -> Unit) {
        try {
            operation()
            fail("Expected the original exception")
        } catch (actual: RuntimeException) {
            assertSame(expected, actual)
        }
    }
}
