package uk.nktnet.webviewkiosk.blocking

import java.util.ArrayDeque
import java.util.concurrent.Executor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import uk.nktnet.webviewkiosk.adblock.KotlinFilterEngine

@RunWith(RobolectricTestRunner::class)
class ScriptletServiceTest {
    @Test
    fun androidCompilationPublishesEntityRoutingUsingPackagedSuffixData() {
        val context = RuntimeEnvironment.getApplication()
        okhttp3.OkHttp.initialize(context)
        context
            .getSharedPreferences("content_blocking_lists", 0)
            .edit()
            .clear()
            .commit()
        java.io.File(context.filesDir, "content-blocking-lists").deleteRecursively()
        val store = FilterListStore(context)
        store.importFrom(
            "entities.txt",
            ("ticketmaster.*,~safe.ticketmaster.*##+js(set, ads, false)\n" +
                    "ticketmaster.co.uk#@#+js()")
                .byteInputStream(),
        )
        val engine = KotlinFilterEngine()
        val service = ContentBlockingService(store, engine) { it.run() }
        service.start()
        assertTrue(service.hasActiveRules)
        assertNotNull(service.scriptletInjection.value)
        assertEquals(setOf("*"), service.scriptletInjection.value!!.allowedOrigins)
        assertEquals(1, engine.scriptlets("https://ticketmaster.github.io/").size)
        assertTrue(engine.scriptlets("https://safe.ticketmaster.fr/").isEmpty())
        assertTrue(engine.scriptlets("https://ticketmaster.co.uk/").isEmpty())
    }

    @Test
    fun publishesScriptletsOnlyAfterDeferredCompilationAndClearsThemWhenDisabled() {
        val context = RuntimeEnvironment.getApplication()
        context
            .getSharedPreferences("content_blocking_lists", 0)
            .edit()
            .clear()
            .commit()
        java.io.File(context.filesDir, "content-blocking-lists").deleteRecursively()
        val store = FilterListStore(context)
        val tasks = ArrayDeque<Runnable>()
        val engine = KotlinFilterEngine()
        val service = ContentBlockingService(store, engine, Executor(tasks::addLast))
        val first =
            store.importFrom(
                "one.txt",
                "site.example##+js(set, ads, false)".byteInputStream(),
            )
        assertNull(service.scriptletInjection.value)
        service.start()
        assertNull(service.scriptletInjection.value)
        tasks.removeFirst().run()
        assertNotNull(service.scriptletInjection.value)
        assertTrue(service.initialLoadComplete.value)
        val original = service.scriptletInjection.value
        store.setEnabled(first.id, false)
        store.importFrom("two.txt", "other.example##+js(nowoif)".byteInputStream())
        service.markRulesChanged()
        assertTrue(service.rulesPending.value)
        assertTrue(original === service.scriptletInjection.value)
        service.prepareForWebView()
        assertFalse(service.rulesPending.value)
        assertTrue(original === service.scriptletInjection.value)
        tasks.removeFirst().run()
        assertFalse(original === service.scriptletInjection.value)
        assertTrue(engine.scriptlets("https://site.example").isEmpty())
        assertFalse(engine.scriptlets("https://other.example").isEmpty())
        service.setEnabled(false)
        assertNull(service.scriptletInjection.value)
        assertFalse(service.hasActiveRules)
        service.setEnabled(true)
        assertNull(service.scriptletInjection.value)
        tasks.removeFirst().run()
        assertNotNull(service.scriptletInjection.value)
    }
}
