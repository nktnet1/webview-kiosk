package uk.nktnet.webviewkiosk.ui.screens

import android.content.Context
import android.content.res.Configuration
import androidx.lifecycle.ViewModelStore
import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import java.lang.reflect.Modifier
import java.util.Locale
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
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SettingsSearchIndexTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun searchesIndividualSettingsInNestedScreens() {
        val key = UserSettingsKeys.Mqtt.Topics.Subscribe.Settings.RETAIN_HANDLING
        val result =
            searchSettings(context, "mqtt topics subscribe settings retain handling")
                .single { it.settingKey == key }
        assertEquals(Screen.SettingsMqttTopicsSubscribeSettings.route, result.route)
        assertEquals(key, result.targetId)
        assertEquals(SettingsSearchResultKind.SETTING, result.kind)
    }

    @Test
    fun normalisesCaseWhitespaceAccentsAndSeparators() {
        val key = UserSettingsKeys.WebBrowsing.ALLOW_PULL_TO_REFRESH
        for (query in
            listOf(
                " pull-to-refresh ",
                "PULL   TO   REFRESH",
                "pull_to_refresh",
                "púll.to.refresh",
            )) {
            assertTrue(
                "Missing setting for $query",
                searchSettings(context, query).any { it.settingKey == key },
            )
        }
        assertTrue(searchSettings(context, " ").isEmpty())
        assertTrue(searchSettings(context, "---_.").isEmpty())
        assertTrue(
            searchSettings(context, "unlikely-setting-query-123456").isEmpty()
        )
    }

    @Test
    fun pageResultsOpenDestinationsDirectlyWithoutDuplicateMoreActions() {
        val destinations =
            listOf(
                Screen.SettingsWebContentFiles,
                Screen.SettingsContentBlocking,
                Screen.SettingsWebBrowsingSitePermissions,
                Screen.SettingsMqttTopics,
            )
        for (screen in destinations) {
            val page = settingsPages.single { it.screen == screen }
            val results = searchSettings(context, context.getString(page.titleRes))
            val result = results.single {
                it.kind == SettingsSearchResultKind.PAGE && it.route == screen.route
            }
            assertEquals(null, result.targetId)
            assertEquals(null, result.settingKey)
            assertFalse(
                results.any {
                    it.kind == SettingsSearchResultKind.ACTION &&
                        it.titleRes in
                            setOf(
                                R.string.settings_more_action_local_files,
                                R.string.settings_more_action_content_blocking,
                                R.string.settings_more_action_site_permissions,
                                R.string.settings_more_action_device_owner,
                            )
                }
            )
        }
        assertEquals(
            Screen.SettingsMqttDebug.route,
            searchSettings(context, "mqtt debug logs")
                .single { it.kind == SettingsSearchResultKind.PAGE }
                .route,
        )
        assertEquals(
            Screen.SettingsUnifiedPushDebug.route,
            searchSettings(context, "unifiedpush diagnostic logs")
                .single { it.kind == SettingsSearchResultKind.PAGE }
                .route,
        )
    }

    @Test
    fun findsDescriptionsAliasesAndOptionLabels() {
        assertTrue(
            searchSettings(context, "keep device awake").any {
                it.settingKey == UserSettingsKeys.Device.KEEP_SCREEN_ON
            }
        )
        assertTrue(
            searchSettings(context, "fullscreen").any {
                it.settingKey == UserSettingsKeys.Appearance.IMMERSIVE_MODE
            }
        )
        assertTrue(
            searchSettings(context, "allowlist").any {
                it.settingKey == UserSettingsKeys.WebContent.WEBSITE_WHITELIST
            }
        )
        assertTrue(
            searchSettings(context, "text autosizing").any {
                it.settingKey == UserSettingsKeys.WebEngine.LAYOUT_ALGORITHM
            }
        )
        // A description on a page must reveal that destination, not every field.
        val page = settingsPages.single { it.screen == Screen.SettingsMqttWill }
        val query = context.getString(requireNotNull(page.descriptionRes))
        assertTrue(
            searchSettings(context, query).any {
                it.route == page.route && it.kind == SettingsSearchResultKind.PAGE
            }
        )
        assertTrue(
            searchSettings(context, "legacy file://").none {
                it.kind == SettingsSearchResultKind.SETTING
            }
        )
    }

    @Test
    fun findsInformationActionsAndImportExportToolsWithStableIds() {
        val items =
            listOf(
                Triple(
                    R.string.about_app_instance_id,
                    Screen.SettingsAbout.route,
                    SettingsSearchResultKind.INFORMATION,
                ),
                Triple(
                    R.string.settings_more_action_clear_cache,
                    Screen.SettingsMoreActions.route,
                    SettingsSearchResultKind.ACTION,
                ),
                Triple(
                    R.string.ui_import,
                    Screen.Settings.route,
                    SettingsSearchResultKind.ACTION,
                ),
                Triple(
                    R.string.ui_export,
                    Screen.Settings.route,
                    SettingsSearchResultKind.ACTION,
                ),
                Triple(
                    R.string.ui_help,
                    Screen.Settings.route,
                    SettingsSearchResultKind.ACTION,
                ),
            )
        for ((title, route, kind) in items) {
            val result =
                searchSettings(context, context.getString(title)).single {
                    it.titleRes == title && it.kind == kind
                }
            assertEquals(route, result.route)
            assertEquals(settingsItemTargetId(context, kind, title), result.targetId)
        }
        assertTrue(
            searchSettings(context, "restore backup").any {
                it.titleRes == R.string.ui_import
            }
        )
        assertTrue(
            searchSettings(context, "back up configuration").any {
                it.titleRes == R.string.ui_export
            }
        )
        assertEquals(
            setOf(R.string.ui_import, R.string.ui_export),
            searchSettings(context, "backup")
                .filter { it.route == Screen.Settings.route }
                .map { it.titleRes }
                .toSet(),
        )
    }

    @Test
    fun strongMatchesPrecedeDescriptionsAndGroupsRemainTogether() {
        val results = searchSettings(context, "cache")
        assertEquals(
            R.string.settings_more_action_clear_cache,
            results.first().titleRes,
        )
        val titleMatch = results.indexOfFirst {
            it.settingKey == UserSettingsKeys.WebEngine.CACHE_MODE
        }
        val optionMatch = results.indexOfFirst {
            it.settingKey == UserSettingsKeys.WebLifecycle.RESET_ACTIONS
        }
        assertTrue(titleMatch >= 0 && optionMatch > titleMatch)
        val grouped = groupSettingsSearchResults(results)
        assertTrue(grouped.size > 1)
        assertEquals(results, grouped.flatMap { it.second })
        grouped.forEach { (page, entries) ->
            assertTrue(entries.all { it.route == page.route })
        }
    }

    @Test
    fun retainsTheActualKeyForLabelPartialKeyAndFullKeyQueries() {
        val key = UserSettingsKeys.WebBrowsing.ALLOW_PULL_TO_REFRESH
        val labelled =
            searchSettings(context, "Pull to refresh").single {
                it.settingKey == key
            }
        assertEquals(key, labelled.settingKey)
        assertEquals(
            labelled,
            searchSettings(context, "allow_pull_to_refresh").single {
                it.settingKey == key
            },
        )
        val byKey = searchSettings(context, key).first()
        assertEquals(key, byKey.settingKey)
        assertEquals(key, byKey.targetId)
        assertEquals(labelled, byKey)
        assertEquals(
            UserSettingsKeys.UnifiedPush.VAPID_PUBLIC_KEY,
            searchSettings(context, UserSettingsKeys.UnifiedPush.VAPID_PUBLIC_KEY)
                .first()
                .settingKey,
        )
    }

    @Test
    fun backgroundMediaPlaybackUsesSharedMetadataAndIsSearchable() {
        val key = UserSettingsKeys.WebEngine.BACKGROUND_MEDIA_PLAYBACK
        val titleRes = R.string.web_engine_background_media_playback_title
        val descriptionRes = R.string.setting_info_background_media_playback
        val definition = settingDefinition(key)
        assertEquals(titleRes, definition.titleRes)
        assertEquals(descriptionRes, definition.descriptionRes)

        val expected =
            SettingsSearchResult(
                titleRes = titleRes,
                route = Screen.SettingsWebEngine.route,
                kind = SettingsSearchResultKind.SETTING,
                targetId = key,
                settingKey = key,
            )
        assertEquals(expected, searchSettings(context, key).single())
        assertEquals(
            expected,
            searchSettings(context, context.getString(titleRes)).single {
                it.settingKey == key
            },
        )
    }

    @Test
    fun derivesNestedBreadcrumbsFromTheActualHierarchy() {
        val publish = settingsPages.single {
            it.screen == Screen.SettingsMqttTopicsPublishResponse
        }
        assertEquals(
            listOf(
                R.string.settings_mqtt_title,
                R.string.settings_mqtt_topics_title,
                R.string.settings_mqtt_topics_publish_title,
                R.string.settings_mqtt_topics_publish_response_title,
            ),
            publish.breadcrumbTitleRes,
        )
        assertEquals(
            "MQTT \u203a Topics \u203a Publish \u203a Response",
            publish.breadcrumb(context),
        )
        val subscribe = settingsPages.single {
            it.screen == Screen.SettingsMqttTopicsSubscribeRequest
        }
        assertEquals(
            listOf(
                R.string.settings_mqtt_title,
                R.string.settings_mqtt_topics_title,
                R.string.settings_mqtt_topics_subscribe_title,
                R.string.settings_mqtt_topics_subscribe_request_title,
            ),
            subscribe.breadcrumbTitleRes,
        )
    }

    @Test
    fun coversPersistedSettingsAndDestinationsFromIndependentDeclarations() {
        // Independent of the search registry: omissions from both the old index
        // and label-to-key map must fail. PREFS_NAME is a file, not a setting.
        fun persistedKeys(type: Class<*>): List<String> =
            type.declaredFields
                .filter {
                    it.type == String::class.java &&
                        Modifier.isStatic(it.modifiers) &&
                        it.name != "PREFS_NAME"
                }
                .map { it.get(null) as String } +
                type.declaredClasses.flatMap { persistedKeys(it) }
        val definitions = settingsPages.flatMap { it.settings }
        assertEquals(
            persistedKeys(UserSettingsKeys::class.java).toSet(),
            definitions.map { it.key }.toSet(),
        )
        assertEquals(definitions.size, definitions.map { it.key }.distinct().size)
        for (definition in definitions) {
            assertTrue(context.getString(definition.titleRes).isNotBlank())
            assertTrue(context.getString(definition.descriptionRes).isNotBlank())
            assertTrue(
                searchSettings(context, definition.key).any {
                    it.settingKey == definition.key && it.targetId == definition.key
                }
            )
        }
        val screens =
            Screen::class
                .java
                .declaredClasses
                .mapNotNull {
                    runCatching { it.getField("INSTANCE").get(null) as Screen }
                        .getOrNull()
                }
                .filter { it.route.startsWith("settings") }
        assertEquals(
            screens.map { it.route }.toSet(),
            settingsPages.map { it.route }.toSet(),
        )
        assertEquals(
            settingsPages.size,
            settingsPages.map { it.route }.distinct().size,
        )
    }

    @Test
    fun neverSearchesConfiguredSecretsUrlsOrDeviceValues() {
        val settings = UserSettings(context)
        settings.homeUrl = "https://unique-private-site-739625.example"
        settings.customAuthPassword = "secret-password-937461"
        settings.mqttPassword = "secret-mqtt-539182"
        val index = SettingsSearchIndex(context)
        for (query in
            listOf(
                "unique-private-site-739625",
                "secret-password-937461",
                "secret-mqtt-539182",
            )) {
            assertTrue(index.search(query).isEmpty())
        }
    }

    @Test
    fun itemIdentityDoesNotDependOnLocale() {
        val config =
            Configuration(context.resources.configuration).apply {
                setLocale(Locale.forLanguageTag("tr"))
            }
        val translatedContext = context.createConfigurationContext(config)
        assertEquals(
            settingsItemTargetId(
                context,
                SettingsSearchResultKind.INFORMATION,
                R.string.about_app_instance_id,
            ),
            settingsItemTargetId(
                translatedContext,
                SettingsSearchResultKind.INFORMATION,
                R.string.about_app_instance_id,
            ),
        )
    }

    @Test
    fun selectingAResultStoresItsStringTargetOnTheDestinationOnly() {
        val controller = NavHostController(context)
        controller.navigatorProvider.addNavigator(ComposeNavigator())
        controller.setViewModelStore(ViewModelStore())
        controller.graph =
            controller.createGraph(startDestination = Screen.Settings.route) {
                composable(Screen.Settings.route) {}
                composable(Screen.SettingsWebEngine.route) {}
            }
        val key = UserSettingsKeys.WebEngine.REQUEST_FOCUS_ON_PAGE_START
        val selected = searchSettings(context, key).first()
        controller.navigateToSearchResult(selected)
        assertEquals(
            Screen.SettingsWebEngine.route,
            controller.currentDestination?.route,
        )
        val entry = requireNotNull(controller.currentBackStackEntry)
        assertEquals(
            key,
            entry.savedStateHandle.get<String>(SETTINGS_SEARCH_TARGET_KEY),
        )
        val target =
            entry.savedStateHandle.getStateFlow<String?>(
                SETTINGS_SEARCH_TARGET_KEY,
                null,
            )
        entry.savedStateHandle.set<String?>(SETTINGS_SEARCH_TARGET_KEY, null)
        assertEquals(null, target.value)
        controller.popBackStack()
        assertEquals(
            null,
            controller.currentBackStackEntry
                ?.savedStateHandle
                ?.get<String>(SETTINGS_SEARCH_TARGET_KEY),
        )
        val page =
            searchSettings(context, "Web engine").first {
                it.kind == SettingsSearchResultKind.PAGE
            }
        controller.navigateToSearchResult(page)
        assertEquals(
            null,
            controller.currentBackStackEntry
                ?.savedStateHandle
                ?.get<String>(SETTINGS_SEARCH_TARGET_KEY),
        )
    }
}
