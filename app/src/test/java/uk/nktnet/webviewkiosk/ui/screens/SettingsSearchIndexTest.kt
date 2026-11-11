package uk.nktnet.webviewkiosk.ui.screens

import android.content.Context
import androidx.lifecycle.ViewModelStore
import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.Screen

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SettingsSearchIndexTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun searchesIndividualSettingsInNestedScreens() {
        val title =
            context.getString(R.string.mqtt_subscribe_settings_retain_handling_title)
        val result =
            searchSettings(context, title).single {
                it.titleRes == R.string.mqtt_subscribe_settings_retain_handling_title
            }

        assertEquals(Screen.SettingsMqttTopicsSubscribeSettings.route, result.route)
        assertEquals(
            R.string.settings_mqtt_topics_subscribe_settings_title,
            result.sectionTitleRes,
        )
        assertEquals(result.titleRes, result.targetRes)
    }

    @Test
    fun searchesByWordsWithoutDependingOnCaseOrExtraSpaces() {
        val title =
            context.getString(R.string.appearance_show_loading_progress_bar_title)
        val terms =
            title
                .trim()
                .split(Regex("\\s+"))
                .takeLast(2)
                .joinToString("   ")
                .uppercase()
        val result =
            searchSettings(context, "   $terms  ").first {
                it.titleRes == R.string.appearance_show_loading_progress_bar_title
            }

        assertEquals(Screen.SettingsAppearance.route, result.route)
        assertEquals(
            R.string.appearance_show_loading_progress_bar_title,
            result.targetRes,
        )
    }

    @Test
    fun categoriesCanBeFoundWithoutSelectingAnArbitraryField() {
        val result =
            searchSettings(
                    context,
                    context.getString(R.string.settings_web_engine_title),
                )
                .single { it.titleRes == R.string.settings_web_engine_title }
        assertEquals(Screen.SettingsWebEngine.route, result.route)
        assertEquals(null, result.targetRes)
        assertEquals(null, result.sectionTitleRes)
    }

    @Test
    fun includesMoreActionsAndAboutEntriesAndHandlesEmptyQueries() {
        assertTrue(searchSettings(context, " ").isEmpty())
        assertTrue(
            searchSettings(context, "unlikely-setting-query-123456").isEmpty()
        )
        assertTrue(
            searchSettings(
                    context,
                    context.getString(R.string.settings_more_action_clear_cache),
                )
                .any {
                    it.route == Screen.SettingsMoreActions.route &&
                        it.targetRes == R.string.settings_more_action_clear_cache
                }
        )
        assertTrue(
            searchSettings(
                    context,
                    context.getString(R.string.about_app_instance_id),
                )
                .any {
                    it.route == Screen.SettingsAbout.route &&
                        it.targetRes == R.string.about_app_instance_id
                }
        )
    }

    @Test
    fun searchesLegacyFileSettingOnItsActualScreen() {
        val result =
            searchSettings(context, context.getString(R.string.ui_legacy_file))
                .single { it.targetRes == R.string.ui_legacy_file }
        assertEquals(Screen.SettingsWebContentFiles.route, result.route)
    }

    @Test
    fun everyIndexedFieldHasAUniqueDestinationAndNonEmptyResourceTitle() {
        assertFalse(settingsSearchSections.isEmpty())
        settingsSearchSections.forEach { section ->
            assertTrue(context.getString(section.titleRes).isNotBlank())
            assertEquals(section.fields.size, section.fields.distinct().size)
            section.fields.forEach { assertTrue(context.getString(it).isNotBlank()) }
        }
    }

    @Test
    fun selectingAResultStoresItsTargetOnTheDestinationOnly() {
        val controller = NavHostController(context)
        controller.navigatorProvider.addNavigator(ComposeNavigator())
        controller.setViewModelStore(ViewModelStore())
        controller.graph =
            controller.createGraph(startDestination = Screen.Settings.route) {
                composable(Screen.Settings.route) {}
                composable(Screen.SettingsWebEngine.route) {}
            }
        val selected =
            searchSettings(
                    context,
                    context.getString(
                        R.string.web_engine_request_focus_on_page_start_title
                    ),
                )
                .single {
                    it.targetRes ==
                        R.string.web_engine_request_focus_on_page_start_title
                }

        controller.navigateToSearchResult(selected)

        assertEquals(
            Screen.SettingsWebEngine.route,
            controller.currentDestination?.route,
        )
        assertEquals(
            selected.targetRes,
            controller.currentBackStackEntry
                ?.savedStateHandle
                ?.get<Int>(SETTINGS_SEARCH_TARGET_KEY),
        )
        controller.popBackStack()
        assertEquals(
            null,
            controller.currentBackStackEntry
                ?.savedStateHandle
                ?.get<Int>(SETTINGS_SEARCH_TARGET_KEY),
        )
    }
}
