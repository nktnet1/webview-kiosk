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
import uk.nktnet.webviewkiosk.config.UserSettingsKeys

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
    fun resultsIdentifySettingsPagesAndOtherItemsWithDistinctKinds() {
        fun resultFor(
            @androidx.annotation.StringRes titleRes: Int
        ): SettingsSearchResult =
            searchSettings(context, context.getString(titleRes)).single {
                it.titleRes == titleRes
            }

        assertEquals(
            SettingsSearchResultKind.SETTING,
            resultFor(R.string.web_engine_ssl_error_mode_title).kind,
        )
        assertEquals(
            SettingsSearchResultKind.PAGE,
            resultFor(R.string.settings_web_engine_title).kind,
        )
        assertEquals(
            SettingsSearchResultKind.INFORMATION,
            resultFor(R.string.about_app_instance_id).kind,
        )
        assertEquals(
            SettingsSearchResultKind.ACTION,
            resultFor(R.string.settings_more_action_clear_cache).kind,
        )
        assertEquals(
            SettingsSearchResultKind.FILE,
            resultFor(R.string.ui_legacy_file).kind,
        )
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
    fun findsSettingByFullOrPartialKeyAndShowsTheActualKey() {
        val key = UserSettingsKeys.WebEngine.SSL_ERROR_MODE
        val matches = searchSettings(context, "  ${key.uppercase()}  ")
        val result = matches.single()

        assertEquals(R.string.web_engine_ssl_error_mode_title, result.titleRes)
        assertEquals(Screen.SettingsWebEngine.route, result.route)
        assertEquals(key, result.settingKey)
        assertEquals(R.string.web_engine_ssl_error_mode_title, result.targetRes)
        assertEquals(
            result,
            searchSettings(context, "ssl_error_mode").single {
                it.targetRes == R.string.web_engine_ssl_error_mode_title
            },
        )
    }

    @Test
    fun vapidKeyIsNotConfusedWithDistributorMessage() {
        val result =
            searchSettings(context, UserSettingsKeys.UnifiedPush.VAPID_PUBLIC_KEY)
                .single()
        assertEquals(R.string.unifiedpush_vapid_public_key_title, result.titleRes)
        assertEquals(
            UserSettingsKeys.UnifiedPush.VAPID_PUBLIC_KEY,
            result.settingKey,
        )
    }

    @Test
    fun searchesNestedKeysUsingWordsAcrossLabelsSectionsAndKeys() {
        val key = UserSettingsKeys.Mqtt.Topics.Subscribe.Request.RETAIN_HANDLING
        val result = searchSettings(context, key).single()

        assertEquals(Screen.SettingsMqttTopicsSubscribeRequest.route, result.route)
        assertEquals(
            R.string.mqtt_subscribe_request_retain_handling_title,
            result.targetRes,
        )
        assertEquals(key, result.settingKey)
        assertEquals(
            result,
            searchSettings(context, "MQTT  request  retain_handling").single {
                it.targetRes == result.targetRes
            },
        )
    }

    @Test
    fun showsFullBreadcrumbForNestedPublishAndSubscribeSettings() {
        val publishSection = settingsSearchSections.single {
            it.route == Screen.SettingsMqttTopicsPublishResponse.route
        }
        assertEquals(
            listOf(
                R.string.settings_mqtt_title,
                R.string.settings_mqtt_topics_title,
                R.string.settings_mqtt_topics_publish_title,
                R.string.settings_mqtt_topics_publish_response_title,
            ),
            publishSection.breadcrumbTitleRes,
        )
        assertEquals(
            "MQTT \u203a Topics \u203a Publish \u203a Response",
            publishSection.breadcrumb(context),
        )

        val subscribeSection = settingsSearchSections.single {
            it.route == Screen.SettingsMqttTopicsSubscribeRequest.route
        }
        assertEquals(
            listOf(
                R.string.settings_mqtt_title,
                R.string.settings_mqtt_topics_title,
                R.string.settings_mqtt_topics_subscribe_title,
                R.string.settings_mqtt_topics_subscribe_request_title,
            ),
            subscribeSection.breadcrumbTitleRes,
        )
    }

    @Test
    fun nestedSectionsUseActualNavigationParents() {
        val expectedParents =
            mapOf(
                Screen.SettingsWebContentFiles.route to
                    listOf(R.string.settings_web_content_title),
                Screen.SettingsContentBlocking.route to
                    listOf(R.string.settings_web_content_title),
                Screen.SettingsWebBrowsingSitePermissions.route to
                    listOf(R.string.settings_web_browsing_title),
                Screen.SettingsDeviceOwner.route to
                    listOf(R.string.settings_device_title),
                Screen.SettingsMqttConnection.route to
                    listOf(R.string.settings_mqtt_title),
                Screen.SettingsMqttTopics.route to
                    listOf(R.string.settings_mqtt_title),
                Screen.SettingsMqttHomeAssistant.route to
                    listOf(R.string.settings_mqtt_title),
                Screen.SettingsMqttWill.route to
                    listOf(R.string.settings_mqtt_title),
                Screen.SettingsMqttRestrictions.route to
                    listOf(R.string.settings_mqtt_title),
            )

        expectedParents.forEach { (route, parents) ->
            val section = settingsSearchSections.single { it.route == route }
            assertEquals(
                "Incorrect hierarchy for $route",
                parents,
                section.parentTitleRes,
            )
        }
    }

    @Test
    fun searchesAncestorsAsWellAsSettingLabelsAndKeys() {
        val matches = searchSettings(context, "web content files legacy")
        assertEquals(
            Screen.SettingsWebContentFiles.route,
            matches.single { it.targetRes == R.string.ui_legacy_file }.route,
        )
        val mqttResult =
            searchSettings(context, "mqtt topics publish response qos").single {
                it.targetRes == R.string.mqtt_publish_response_qos_title
            }
        assertEquals(
            Screen.SettingsMqttTopicsPublishResponse.route,
            mqttResult.route,
        )
    }

    @Test
    fun everyIndexedUserSettingHasARealKeyAndOnlyNonSettingsLackOne() {
        val nonUserSettings =
            setOf(
                Screen.SettingsAbout.route,
                Screen.SettingsMoreActions.route,
                Screen.SettingsWebContentFiles.route,
            )
        val expectedKeys =
            settingsSearchSections
                .filterNot { it.route in nonUserSettings }
                .flatMap { it.fields }
                .toSet()
        assertEquals(expectedKeys, settingsSearchKeysByLabel.keys)
        assertEquals(
            settingsSearchKeysByLabel.size,
            settingsSearchKeysByLabel.values.toSet().size,
        )
        settingsSearchSections
            .filter { it.route in nonUserSettings }
            .flatMap { it.fields }
            .forEach { assertFalse(it in settingsSearchKeysByLabel) }
    }

    @Test
    fun searchResultsAreGroupedInSettingsOrder() {
        val results = searchSettings(context, "enabled")
        val grouped = groupSettingsSearchResults(results)

        assertTrue(grouped.size > 1)
        assertEquals(results, grouped.flatMap { it.second })
        grouped.forEach { (section, entries) ->
            assertTrue(entries.isNotEmpty())
            assertTrue(entries.all { it.route == section.route })
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
        val entry = requireNotNull(controller.currentBackStackEntry)
        val selectedTarget =
            entry.savedStateHandle.getStateFlow<Int?>(
                SETTINGS_SEARCH_TARGET_KEY,
                null,
            )
        entry.savedStateHandle.set<Int?>(SETTINGS_SEARCH_TARGET_KEY, null)
        assertEquals(null, selectedTarget.value)
        controller.popBackStack()
        assertEquals(
            null,
            controller.currentBackStackEntry
                ?.savedStateHandle
                ?.get<Int>(SETTINGS_SEARCH_TARGET_KEY),
        )
    }
}
