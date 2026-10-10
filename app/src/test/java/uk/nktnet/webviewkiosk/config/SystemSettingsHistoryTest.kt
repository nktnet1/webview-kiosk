package uk.nktnet.webviewkiosk.config

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SystemSettingsHistoryTest {
    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var settings: SystemSettings

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        prefs = context.getSharedPreferences("system_settings", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        settings = SystemSettings(context)
    }

    @Test
    fun missingHistoryHasNoSelectedEntry() {
        assertTrue(settings.historyStack.isEmpty())
        assertEquals(-1, settings.historyIndex)
        assertEquals("", settings.currentUrl)
    }

    @Test
    fun unselectedCursorPersistsWithoutSelectingTheFirstEntry() {
        settings.historyStack = entries()
        settings.historyIndex = 1
        settings.historyIndex = -1

        val restored = SystemSettings(context)
        assertEquals(-1, prefs.getInt("history_index", 100))
        assertEquals(-1, restored.historyIndex)
        assertEquals(entries(), restored.historyStack)
        assertEquals("", restored.currentUrl)
    }

    @Test
    fun selectedCursorAndCurrentUrlSurviveAnotherSettingsWrapper() {
        settings.historyStack = entries()
        for (index in settings.historyStack.indices) {
            settings.historyIndex = index

            val restored = SystemSettings(context)
            assertEquals(index, prefs.getInt("history_index", -1))
            assertEquals(index, restored.historyIndex)
            assertEquals(entries()[index].url, restored.currentUrl)
        }
    }

    @Test
    fun completeHistoryResetPersistsTheEmptyCursor() {
        settings.historyStack = entries()
        settings.historyIndex = 1

        settings.clearHistory()

        val restored = SystemSettings(context)
        assertTrue(restored.historyStack.isEmpty())
        assertEquals(-1, prefs.getInt("history_index", 100))
        assertEquals(-1, restored.historyIndex)
        assertEquals("", restored.currentUrl)
    }

    @Test
    fun corruptNegativeStoredCursorDoesNotSelectAnEntry() {
        settings.historyStack = entries()
        for (index in listOf(Int.MIN_VALUE, -2, -1)) {
            prefs.edit().putInt("history_index", index).commit()

            val restored = SystemSettings(context)
            assertEquals(-1, restored.historyIndex)
            assertEquals("", restored.currentUrl)
            assertEquals(entries(), restored.historyStack)
        }
    }

    @Test
    fun wrongTypeStoredCursorRepairsToTheEmptySentinel() {
        settings.historyStack = entries()
        prefs.edit().putString("history_index", "invalid").commit()

        val restored = SystemSettings(context)
        assertEquals(-1, restored.historyIndex)
        assertEquals(-1, prefs.getInt("history_index", 100))
        assertEquals("", restored.currentUrl)
        assertEquals(entries(), restored.historyStack)
    }

    private fun entries() = listOf(
        HistoryEntry("first-entry", "https://example.com/first", 100),
        HistoryEntry("second-entry", "https://example.com/second", 200),
    )
}
