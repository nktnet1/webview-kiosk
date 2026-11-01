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
    fun rendererDeathAbandonsRegistrationWithoutCallingDeadWebView() {
        val calls = mutableListOf<String>()
        val first = ScriptletInjection("first", setOf("*"))
        val binding =
            DocumentStartScriptBinding(true) { injection ->
                calls += "add ${injection.source}"
                val remove: () -> Unit = { calls += "remove ${injection.source}" }
                remove
            }

        binding.replace(first)
        binding.dispose(rendererGone = true)
        binding.dispose()
        binding.replace(ScriptletInjection("second", first.allowedOrigins))

        // The renderer no longer owns this registration; no WebView calls may
        // follow its death, even if Compose later disposes the old WebView.
        assertEquals(listOf("add first"), calls)
    }

    @Test
    fun failedUnregistrationStillPermanentlyDisposesTheBinding() {
        val first = ScriptletInjection("first", setOf("*"))
        var removals = 0
        val binding =
            DocumentStartScriptBinding(true) {
                val remove: () -> Unit = {
                    removals++
                    throw IllegalStateException("provider unavailable")
                }
                remove
            }
        binding.replace(first)

        runCatching { binding.dispose() }
        binding.dispose()
        binding.replace(first)

        assertEquals(1, removals)
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
