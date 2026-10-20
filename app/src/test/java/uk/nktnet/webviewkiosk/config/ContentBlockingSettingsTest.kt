@file:Suppress("UsePropertyAccessSyntax")

package uk.nktnet.webviewkiosk.config

import android.content.Context
import android.content.Intent
import android.content.RestrictionsManager
import android.os.Bundle
import android.os.Looper
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import uk.nktnet.webviewkiosk.adblock.KotlinFilterEngine
import uk.nktnet.webviewkiosk.blocking.BlockingStatus
import uk.nktnet.webviewkiosk.blocking.ContentBlockingService
import uk.nktnet.webviewkiosk.blocking.FilterListStore

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ContentBlockingSettingsTest {
    private lateinit var context: Context
    private lateinit var settings: UserSettings
    private lateinit var restrictions: RestrictionsManager
    private val key = UserSettingsKeys.WebContent.ENABLE_CONTENT_BLOCKING

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context
            .getSharedPreferences(UserSettingsKeys.PREFS_NAME, 0)
            .edit()
            .clear()
            .commit()
        context
            .getSharedPreferences("content_blocking_lists", 0)
            .edit()
            .clear()
            .commit()
        context.filesDir.resolve("content-blocking").deleteRecursively()
        restrictions =
            context.getSystemService(Context.RESTRICTIONS_SERVICE)
                as RestrictionsManager
        shadowOf(restrictions).setApplicationRestrictions(Bundle())
        settings = UserSettings(context)
        okhttp3.OkHttp.initialize(context)
    }

    @Test
    fun defaultsToEnabledAndRoundTripsWithoutChangingLegacyImports() {
        assertTrue(settings.enableContentBlocking)
        settings.enableContentBlocking = false
        assertFalse(UserSettings(context).enableContentBlocking)
        val json = settings.exportJson().toString()
        val base64 = settings.exportBase64()
        assertFalse(JSONObject(json).getBoolean(key))

        settings.enableContentBlocking = true
        assertTrue(settings.importJson(json))
        assertFalse(settings.enableContentBlocking)
        settings.enableContentBlocking = true
        assertTrue(settings.importBase64(base64))
        assertFalse(settings.enableContentBlocking)
        assertTrue(settings.importJson("{}"))
        assertFalse(settings.enableContentBlocking)
    }

    @Test
    fun managedValuesOverrideLocalValuesAndPreventWrites() {
        val preferences =
            context.getSharedPreferences(UserSettingsKeys.PREFS_NAME, 0)
        for (managed in listOf(true, false)) {
            preferences.edit().putBoolean(key, !managed).commit()
            shadowOf(restrictions)
                .setApplicationRestrictions(
                    Bundle().apply { putBoolean(key, managed) }
                )
            assertTrue(settings.isRestricted(key))
            assertEquals(managed, settings.enableContentBlocking)
            settings.enableContentBlocking = !managed
            assertTrue(
                settings.importJson(JSONObject().put(key, !managed).toString())
            )
            assertEquals(managed, settings.enableContentBlocking)
            assertEquals(managed, settings.exportJson().getBoolean(key))
            assertEquals(!managed, preferences.getBoolean(key, managed))

            shadowOf(restrictions).setApplicationRestrictions(Bundle())
            assertFalse(settings.isRestricted(key))
            assertEquals(!managed, settings.enableContentBlocking)
        }
    }

    @Test
    fun localRemoteAndManagedChangesUpdateTheRunningBlocker() = runBlocking {
        settings.enableContentBlocking = false
        val store = FilterListStore(context)
        val list =
            store.importFrom(
                "test.txt",
                "||ads.example^\nsite.example##.sponsor".byteInputStream(),
            )
        val service =
            ContentBlockingService(store, KotlinFilterEngine()) { it.run() }
        service.bindSettings(context).use {
            service.start()
            assertFalse(service.enabled.value)
            assertEquals(BlockingStatus.DISABLED, service.status.value)
            assertTrue(service.initialLoadComplete.value)
            service.awaitReload(service.reload())

            settings.enableContentBlocking = true
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(service.enabled.value)
            assertEquals(BlockingStatus.ACTIVE, service.status.value)
            assertTrue(service.hasActiveCosmeticRules)
            assertTrue(
                service.shouldBlock(
                    "https://ads.example/pixel",
                    "https://site.example",
                    "other",
                    "GET",
                )
            )

            settings.enableContentBlocking = false
            shadowOf(Looper.getMainLooper()).idle()
            assertFalse(service.enabled.value)
            assertEquals(BlockingStatus.DISABLED, service.status.value)
            assertFalse(service.hasActiveRules)
            assertFalse(service.hasActiveCosmeticRules)
            assertFalse(
                service.shouldBlock(
                    "https://ads.example/pixel",
                    "https://site.example",
                    "other",
                    "GET",
                )
            )
            assertTrue(
                service
                    .cosmeticSelectors(
                        "https://site.example",
                        listOf("sponsor"),
                        emptyList(),
                    )
                    .isEmpty()
            )
            assertTrue(store.list().single { it.id == list.id }.enabled)

            assertTrue(settings.importJson(JSONObject().put(key, true).toString()))
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(service.enabled.value)
            assertEquals(BlockingStatus.ACTIVE, service.status.value)

            shadowOf(restrictions)
                .setApplicationRestrictions(
                    Bundle().apply { putBoolean(key, false) }
                )
            context.sendBroadcast(
                Intent(Intent.ACTION_APPLICATION_RESTRICTIONS_CHANGED)
            )
            shadowOf(Looper.getMainLooper()).idle()
            assertFalse(service.enabled.value)
            assertEquals(BlockingStatus.DISABLED, service.status.value)
            assertFalse(service.hasActiveRules)

            shadowOf(restrictions).setApplicationRestrictions(Bundle())
            context.sendBroadcast(
                Intent(Intent.ACTION_APPLICATION_RESTRICTIONS_CHANGED)
            )
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(service.enabled.value)
            assertEquals(BlockingStatus.ACTIVE, service.status.value)
            assertTrue(service.hasActiveRules)
        }
    }
}
