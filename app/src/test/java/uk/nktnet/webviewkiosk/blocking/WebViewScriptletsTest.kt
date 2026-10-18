package uk.nktnet.webviewkiosk.blocking

import org.junit.Assert.assertEquals
import org.junit.Test
import uk.nktnet.webviewkiosk.adblock.ScriptletInjection

class WebViewScriptletsTest {
    @Test
    fun emptySnapshotsDoNotInitializeOrProbeTheProvider() {
        val binding =
            DocumentStartScriptBinding({ error("Must not probe") }) {
                error("Must not register")
            }
        binding.replace(null)
        binding.dispose()
    }

    @Test
    fun replacesRegistrationsBeforeSubsequentLoadsAndDisposesExactlyOnce() {
        val calls = mutableListOf<String>()
        val binding =
            DocumentStartScriptBinding(true) { injection ->
                calls += "add ${injection.source}"
                val remove: () -> Unit = { calls += "remove ${injection.source}" }
                remove
            }
        val first = ScriptletInjection("first", setOf("*"))
        binding.replace(first)
        binding.replace(first)
        binding.replace(ScriptletInjection("second", first.allowedOrigins))
        binding.replace(null)
        binding.replace(null)
        binding.replace(first)
        binding.dispose()
        binding.dispose()
        binding.replace(first)
        assertEquals(
            listOf(
                "add first",
                "remove first",
                "add second",
                "remove second",
                "add first",
                "remove first",
            ),
            calls,
        )
    }

    @Test
    fun unsupportedDocumentStartDoesNotAttemptLateOrPartialInjection() {
        val binding =
            DocumentStartScriptBinding(false) { error("Must not register") }
        binding.replace(ScriptletInjection("test", setOf("*")))
        binding.replace(null)
        binding.dispose()
    }

    @Test
    fun failedRegistrationCanBeRetriedWithoutRetainingAnActiveSnapshot() {
        var calls = 0
        val binding =
            DocumentStartScriptBinding(true) {
                calls++
                if (calls == 1) {
                    throw UnsupportedOperationException("Provider unavailable")
                }
                val remove: () -> Unit = {}
                remove
            }
        val source = ScriptletInjection("test", setOf("*"))
        runCatching { binding.replace(source) }
        binding.replace(source)
        binding.replace(source)
        assertEquals(2, calls)
    }
}
