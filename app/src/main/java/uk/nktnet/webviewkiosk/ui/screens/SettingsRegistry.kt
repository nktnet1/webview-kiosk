package uk.nktnet.webviewkiosk.ui.screens

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.Screen
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.config.mqtt.MqttQosOption
import uk.nktnet.webviewkiosk.config.mqtt.MqttRetainHandlingOption
import uk.nktnet.webviewkiosk.config.option.AddressBarModeOption
import uk.nktnet.webviewkiosk.config.option.AddressBarPositionOption
import uk.nktnet.webviewkiosk.config.option.AddressBarSizeOption
import uk.nktnet.webviewkiosk.config.option.BackButtonHoldActionOption
import uk.nktnet.webviewkiosk.config.option.CacheModeOption
import uk.nktnet.webviewkiosk.config.option.DeviceRotationOption
import uk.nktnet.webviewkiosk.config.option.FloatingToolbarModeOption
import uk.nktnet.webviewkiosk.config.option.ImmersiveModeOption
import uk.nktnet.webviewkiosk.config.option.KioskControlPanelRegionOption
import uk.nktnet.webviewkiosk.config.option.LayoutAlgorithmOption
import uk.nktnet.webviewkiosk.config.option.MixedContentModeOption
import uk.nktnet.webviewkiosk.config.option.OverScrollModeOption
import uk.nktnet.webviewkiosk.config.option.OverrideUrlLoadingBlockActionOption
import uk.nktnet.webviewkiosk.config.option.RefreshOnNetworkAvailableOption
import uk.nktnet.webviewkiosk.config.option.ResetActionOption
import uk.nktnet.webviewkiosk.config.option.SearchSuggestionEngineOption
import uk.nktnet.webviewkiosk.config.option.SslErrorModeOption
import uk.nktnet.webviewkiosk.config.option.ThemeOption
import uk.nktnet.webviewkiosk.config.option.UnlockAuthRequirementOption
import uk.nktnet.webviewkiosk.config.option.WebViewInsetOption
import uk.nktnet.webviewkiosk.config.option.WebviewControlActionOption
import uk.nktnet.webviewkiosk.ui.resources.displayLabelRes

/** Static metadata only. Never add configured values or rendered row values here. */
internal data class SettingDefinition(
    @StringRes val titleRes: Int,
    val key: String,
    @StringRes val descriptionRes: Int,
    val optionLabelRes: List<Int> = emptyList(),
    @StringRes val aliasesRes: Int? = null,
)

internal data class SettingsHierarchy(
    @StringRes val titleRes: Int,
    val parent: SettingsHierarchy? = null,
) {
    val breadcrumbTitleRes: List<Int>
        get() = parent?.breadcrumbTitleRes.orEmpty() + titleRes
}

internal data class SettingsItemDefinition(
    @StringRes val titleRes: Int,
    val kind: SettingsSearchResultKind,
    @StringRes val descriptionRes: Int? = null,
)

internal class SettingsPage(
    val screen: Screen,
    @StringRes val titleRes: Int,
    parent: SettingsHierarchy? = null,
    @StringRes val descriptionRes: Int? = null,
    @StringRes val aliasesRes: Int? = null,
    val showOnHome: Boolean = false,
    val settings: List<SettingDefinition> = emptyList(),
    val items: List<SettingsItemDefinition> = emptyList(),
    val content: @Composable (NavController) -> Unit,
) {
    val hierarchy = SettingsHierarchy(titleRes, parent)
    val route: String
        get() = screen.route

    val breadcrumbTitleRes: List<Int>
        get() = hierarchy.breadcrumbTitleRes

    fun breadcrumb(context: Context): String =
        breadcrumbTitleRes.joinToString(" \u203a ") { context.getString(it) }
}

/**
 * One registry for navigation, home-page cards, field labels/help, and search.
 * Specialised fields retain their own editors, validation, and save behaviour.
 */
internal val settingsPages: List<SettingsPage> = buildList {
    val settings =
        SettingsPage(
            screen = Screen.Settings,
            titleRes = R.string.settings_title,
            items =
                listOf(
                    SettingsItemDefinition(
                        R.string.ui_import,
                        SettingsSearchResultKind.ACTION,
                        R.string.settings_search_import_description,
                    ),
                    SettingsItemDefinition(
                        R.string.ui_export,
                        SettingsSearchResultKind.ACTION,
                        R.string.settings_search_export_description,
                    ),
                    SettingsItemDefinition(
                        R.string.ui_help,
                        SettingsSearchResultKind.ACTION,
                        R.string.settings_search_help_description,
                    ),
                ),
        ) {
            SettingsListScreen(it)
        }
    add(settings)
    val settingsWebContent =
        SettingsPage(
            screen = Screen.SettingsWebContent,
            titleRes = R.string.settings_web_content_title,
            descriptionRes = R.string.settings_web_content_description,
            showOnHome = true,
            settings =
                listOf(
                    SettingDefinition(
                        R.string.web_content_home_url_title,
                        UserSettingsKeys.WebContent.HOME_URL,
                        R.string.setting_info_home_url,
                    ),
                    SettingDefinition(
                        R.string.web_content_website_blacklist_title,
                        UserSettingsKeys.WebContent.WEBSITE_BLACKLIST,
                        R.string.setting_info_website_blacklist,
                        aliasesRes = R.string.settings_search_alias_blocklist,
                    ),
                    SettingDefinition(
                        R.string.web_content_website_whitelist_title,
                        UserSettingsKeys.WebContent.WEBSITE_WHITELIST,
                        R.string.setting_info_website_whitelist,
                        aliasesRes = R.string.settings_search_alias_allowlist,
                    ),
                    SettingDefinition(
                        R.string.web_content_website_bookmarks_title,
                        UserSettingsKeys.WebContent.WEBSITE_BOOKMARKS,
                        R.string.setting_info_website_bookmarks,
                    ),
                    SettingDefinition(
                        R.string.web_content_support_pdf_rendering_title,
                        UserSettingsKeys.WebContent.SUPPORT_PDF_RENDERING,
                        R.string.setting_info_support_pdf_rendering,
                    ),
                    SettingDefinition(
                        R.string.web_content_allow_local_files_title,
                        UserSettingsKeys.WebContent.ALLOW_LOCAL_FILES,
                        R.string.setting_info_allow_local_files,
                    ),
                    SettingDefinition(
                        R.string.web_content_enable_content_blocking_title,
                        UserSettingsKeys.WebContent.ENABLE_CONTENT_BLOCKING,
                        R.string.setting_info_enable_content_blocking,
                    ),
                ),
        ) {
            SettingsWebContentScreen(it)
        }
    add(settingsWebContent)
    val settingsWebContentFiles =
        SettingsPage(
            screen = Screen.SettingsWebContentFiles,
            titleRes = R.string.settings_files_title,
            parent = settingsWebContent.hierarchy,
            descriptionRes = R.string.settings_web_content_files_description,
        ) {
            SettingsWebContentFilesScreen(it)
        }
    add(settingsWebContentFiles)
    val settingsContentBlocking =
        SettingsPage(
            screen = Screen.SettingsContentBlocking,
            titleRes = R.string.settings_content_blocking_title,
            parent = settingsWebContent.hierarchy,
            descriptionRes = R.string.settings_content_blocking_description,
        ) {
            SettingsContentBlockingScreen(it)
        }
    add(settingsContentBlocking)
    val settingsWebBrowsing =
        SettingsPage(
            screen = Screen.SettingsWebBrowsing,
            titleRes = R.string.settings_web_browsing_title,
            descriptionRes = R.string.settings_web_browsing_description,
            showOnHome = true,
            settings =
                listOf(
                    SettingDefinition(
                        R.string.web_browsing_allow_refresh_title,
                        UserSettingsKeys.WebBrowsing.ALLOW_REFRESH,
                        R.string.setting_info_allow_refresh,
                    ),
                    SettingDefinition(
                        R.string.web_browsing_allow_pull_to_refresh_title,
                        UserSettingsKeys.WebBrowsing.ALLOW_PULL_TO_REFRESH,
                        R.string.setting_info_allow_pull_to_refresh,
                    ),
                    SettingDefinition(
                        R.string.web_browsing_allow_backwards_navigation_title,
                        UserSettingsKeys.WebBrowsing.ALLOW_BACKWARDS_NAVIGATION,
                        R.string.setting_info_allow_backwards_navigation,
                    ),
                    SettingDefinition(
                        R.string.web_browsing_allow_go_home_title,
                        UserSettingsKeys.WebBrowsing.ALLOW_GO_HOME,
                        R.string.setting_info_allow_go_home,
                    ),
                    SettingDefinition(
                        R.string.web_browsing_clear_history_on_home_title,
                        UserSettingsKeys.WebBrowsing.CLEAR_HISTORY_ON_HOME,
                        R.string.setting_info_clear_history_on_home,
                    ),
                    SettingDefinition(
                        R.string.web_browsing_replace_history_url_on_redirect_title,
                        UserSettingsKeys.WebBrowsing.REPLACE_HISTORY_URL_ON_REDIRECT,
                        R.string.setting_info_replace_history_url_on_redirect,
                    ),
                    SettingDefinition(
                        R.string.web_browsing_allow_history_access_title,
                        UserSettingsKeys.WebBrowsing.ALLOW_HISTORY_ACCESS,
                        R.string.setting_info_allow_history_access,
                    ),
                    SettingDefinition(
                        R.string.web_browsing_allow_bookmark_access_title,
                        UserSettingsKeys.WebBrowsing.ALLOW_BOOKMARK_ACCESS,
                        R.string.setting_info_allow_bookmark_access,
                    ),
                    SettingDefinition(
                        R.string.web_browsing_allow_other_url_schemes_title,
                        UserSettingsKeys.WebBrowsing.ALLOW_OTHER_URL_SCHEMES,
                        R.string.setting_info_allow_other_url_schemes,
                    ),
                    SettingDefinition(
                        R.string.web_browsing_allow_default_long_press_title,
                        UserSettingsKeys.WebBrowsing.ALLOW_DEFAULT_LONG_PRESS,
                        R.string.setting_info_allow_default_long_press,
                    ),
                    SettingDefinition(
                        R.string
                            .web_browsing_allow_link_long_press_context_menu_title,
                        UserSettingsKeys.WebBrowsing
                            .ALLOW_LINK_LONG_PRESS_CONTEXT_MENU,
                        R.string.setting_info_allow_link_long_press_context_menu,
                    ),
                    SettingDefinition(
                        R.string
                            .web_browsing_override_url_loading_block_action_title,
                        UserSettingsKeys.WebBrowsing
                            .OVERRIDE_URL_LOADING_BLOCK_ACTION,
                        R.string.setting_info_override_url_loading_block_action,
                        optionLabelRes =
                            OverrideUrlLoadingBlockActionOption.entries.map {
                                it.displayLabelRes
                            },
                    ),
                    SettingDefinition(
                        R.string.web_browsing_address_bar_actions_title,
                        UserSettingsKeys.WebBrowsing.ADDRESS_BAR_ACTIONS,
                        R.string.setting_info_address_bar_actions,
                        optionLabelRes =
                            WebviewControlActionOption.entries.map {
                                it.displayLabelRes
                            },
                    ),
                    SettingDefinition(
                        R.string.web_browsing_kiosk_control_panel_region_title,
                        UserSettingsKeys.WebBrowsing.KIOSK_CONTROL_PANEL_REGION,
                        R.string.setting_info_kiosk_control_panel_region,
                        optionLabelRes =
                            KioskControlPanelRegionOption.entries.map {
                                it.displayLabelRes
                            },
                    ),
                    SettingDefinition(
                        R.string.web_browsing_kiosk_control_panel_actions_title,
                        UserSettingsKeys.WebBrowsing.KIOSK_CONTROL_PANEL_ACTIONS,
                        R.string.setting_info_kiosk_control_panel_actions,
                        optionLabelRes =
                            WebviewControlActionOption.entries.map {
                                it.displayLabelRes
                            },
                    ),
                    SettingDefinition(
                        R.string.web_browsing_search_provider_url_title,
                        UserSettingsKeys.WebBrowsing.SEARCH_PROVIDER_URL,
                        R.string.setting_info_search_provider_url,
                    ),
                    SettingDefinition(
                        R.string.web_browsing_search_suggestion_engine_title,
                        UserSettingsKeys.WebBrowsing.SEARCH_SUGGESTION_ENGINE,
                        R.string.setting_info_search_suggestion_engine,
                        optionLabelRes =
                            SearchSuggestionEngineOption.entries.map {
                                it.displayLabelRes
                            },
                    ),
                ),
        ) {
            SettingsWebBrowsingScreen(it)
        }
    add(settingsWebBrowsing)
    val settingsWebBrowsingSitePermissions =
        SettingsPage(
            screen = Screen.SettingsWebBrowsingSitePermissions,
            titleRes = R.string.settings_site_permissions_title,
            parent = settingsWebBrowsing.hierarchy,
        ) {
            SettingsWebBrowsingSitePermissionsScreen(it)
        }
    add(settingsWebBrowsingSitePermissions)
    val settingsWebEngine =
        SettingsPage(
            screen = Screen.SettingsWebEngine,
            titleRes = R.string.settings_web_engine_title,
            descriptionRes = R.string.settings_web_engine_description,
            showOnHome = true,
            settings =
                listOf(
                    SettingDefinition(
                        R.string.web_engine_enable_javascript_title,
                        UserSettingsKeys.WebEngine.ENABLE_JAVASCRIPT,
                        R.string.setting_info_enable_java_script,
                    ),
                    SettingDefinition(
                        R.string.web_engine_enable_dom_storage_title,
                        UserSettingsKeys.WebEngine.ENABLE_DOM_STORAGE,
                        R.string.setting_info_enable_dom_storage,
                    ),
                    SettingDefinition(
                        R.string.web_engine_accept_cookies_title,
                        UserSettingsKeys.WebEngine.ACCEPT_COOKIES,
                        R.string.setting_info_accept_cookies,
                    ),
                    SettingDefinition(
                        R.string.web_engine_accept_third_party_cookies_title,
                        UserSettingsKeys.WebEngine.ACCEPT_THIRD_PARTY_COOKIES,
                        R.string.setting_info_accept_third_party_cookies,
                    ),
                    SettingDefinition(
                        R.string.web_engine_cache_mode_title,
                        UserSettingsKeys.WebEngine.CACHE_MODE,
                        R.string.setting_info_cache_mode,
                        optionLabelRes =
                            CacheModeOption.entries.map { it.displayLabelRes },
                    ),
                    SettingDefinition(
                        R.string.web_engine_user_agent_title,
                        UserSettingsKeys.WebEngine.USER_AGENT,
                        R.string.setting_info_user_agent,
                    ),
                    SettingDefinition(
                        R.string.web_engine_layout_algorithm_title,
                        UserSettingsKeys.WebEngine.LAYOUT_ALGORITHM,
                        R.string.setting_info_layout_algorithm,
                        optionLabelRes =
                            LayoutAlgorithmOption.entries.map { it.displayLabelRes },
                    ),
                    SettingDefinition(
                        R.string.web_engine_use_wide_viewport_title,
                        UserSettingsKeys.WebEngine.USE_WIDE_VIEWPORT,
                        R.string.setting_info_use_wide_view_port,
                    ),
                    SettingDefinition(
                        R.string.web_engine_load_with_overview_mode_title,
                        UserSettingsKeys.WebEngine.LOAD_WITH_OVERVIEW_MODE,
                        R.string.setting_info_load_with_overview_mode,
                    ),
                    SettingDefinition(
                        R.string.web_engine_support_zoom_title,
                        UserSettingsKeys.WebEngine.SUPPORT_ZOOM,
                        R.string.setting_info_support_zoom,
                    ),
                    SettingDefinition(
                        R.string.web_engine_built_in_zoom_controls_title,
                        UserSettingsKeys.WebEngine.BUILT_IN_ZOOM_CONTROLS,
                        R.string.setting_info_built_in_zoom_controls,
                    ),
                    SettingDefinition(
                        R.string.web_engine_display_zoom_controls_title,
                        UserSettingsKeys.WebEngine.DISPLAY_ZOOM_CONTROLS,
                        R.string.setting_info_display_zoom_controls,
                    ),
                    SettingDefinition(
                        R.string.web_engine_initial_scale_title,
                        UserSettingsKeys.WebEngine.INITIAL_SCALE,
                        R.string.setting_info_initial_scale,
                    ),
                    SettingDefinition(
                        R.string.web_engine_allow_file_access_from_file_urls_title,
                        UserSettingsKeys.WebEngine.ALLOW_FILE_ACCESS_FROM_FILE_URLS,
                        R.string.setting_info_allow_file_access_from_file_url,
                    ),
                    SettingDefinition(
                        R.string
                            .web_engine_allow_universal_access_from_file_urls_title,
                        UserSettingsKeys.WebEngine
                            .ALLOW_UNIVERSAL_ACCESS_FROM_FILE_URLS,
                        R.string.setting_info_allow_universal_access_from_file_urls,
                    ),
                    SettingDefinition(
                        R.string.web_engine_allow_file_picker_title,
                        UserSettingsKeys.WebEngine.ALLOW_FILE_PICKER,
                        R.string.setting_info_allow_file_picker,
                    ),
                    SettingDefinition(
                        R.string.web_engine_allow_file_download_title,
                        UserSettingsKeys.WebEngine.ALLOW_FILE_DOWNLOAD,
                        R.string.setting_info_allow_file_download,
                    ),
                    SettingDefinition(
                        R.string
                            .web_engine_media_playback_requires_user_gesture_title,
                        UserSettingsKeys.WebEngine
                            .MEDIA_PLAYBACK_REQUIRES_USER_GESTURE,
                        R.string.setting_info_media_playback_requires_user_gesture,
                    ),
                    SettingDefinition(
                        R.string.web_engine_background_media_playback_title,
                        UserSettingsKeys.WebEngine.BACKGROUND_MEDIA_PLAYBACK,
                        R.string.setting_info_background_media_playback,
                    ),
                    SettingDefinition(
                        R.string.web_engine_mutual_tls_title,
                        UserSettingsKeys.WebEngine.MUTUAL_TLS,
                        R.string.setting_info_mutual_tls,
                    ),
                    SettingDefinition(
                        R.string.web_engine_ssl_error_mode_title,
                        UserSettingsKeys.WebEngine.SSL_ERROR_MODE,
                        R.string.setting_info_ssl_error_mode,
                        optionLabelRes =
                            SslErrorModeOption.entries.map { it.displayLabelRes },
                    ),
                    SettingDefinition(
                        R.string.web_engine_mixed_content_mode_title,
                        UserSettingsKeys.WebEngine.MIXED_CONTENT_MODE,
                        R.string.setting_info_mixed_content_mode,
                        optionLabelRes =
                            MixedContentModeOption.entries.map {
                                it.displayLabelRes
                            },
                    ),
                    SettingDefinition(
                        R.string.web_engine_over_scroll_mode_title,
                        UserSettingsKeys.WebEngine.OVER_SCROLL_MODE,
                        R.string.setting_info_over_scroll_mode,
                        optionLabelRes =
                            OverScrollModeOption.entries.map { it.displayLabelRes },
                    ),
                    SettingDefinition(
                        R.string.web_engine_request_focus_on_page_start_title,
                        UserSettingsKeys.WebEngine.REQUEST_FOCUS_ON_PAGE_START,
                        R.string.setting_info_request_focus_on_page_start,
                    ),
                ),
        ) {
            SettingsWebEngineScreen(it)
        }
    add(settingsWebEngine)
    val settingsWebLifecycle =
        SettingsPage(
            screen = Screen.SettingsWebLifecycle,
            titleRes = R.string.settings_web_lifecycle_title,
            descriptionRes = R.string.settings_web_lifecycle_description,
            showOnHome = true,
            settings =
                listOf(
                    SettingDefinition(
                        R.string.web_lifecycle_lock_on_launch_title,
                        UserSettingsKeys.WebLifecycle.LOCK_ON_LAUNCH,
                        R.string.setting_info_lock_on_launch,
                    ),
                    SettingDefinition(
                        R.string.web_lifecycle_reset_on_launch_title,
                        UserSettingsKeys.WebLifecycle.RESET_ON_LAUNCH,
                        R.string.setting_info_reset_on_launch,
                    ),
                    SettingDefinition(
                        R.string.web_lifecycle_reset_on_inactivity_seconds_title,
                        UserSettingsKeys.WebLifecycle.RESET_ON_INACTIVITY_SECONDS,
                        R.string.setting_info_reset_on_inactivity_seconds,
                    ),
                    SettingDefinition(
                        R.string.web_lifecycle_reset_actions_title,
                        UserSettingsKeys.WebLifecycle.RESET_ACTIONS,
                        R.string.setting_info_reset_actions,
                        optionLabelRes =
                            ResetActionOption.entries.map { it.displayLabelRes },
                    ),
                    SettingDefinition(
                        R.string
                            .web_lifecycle_dim_screen_on_inactivity_seconds_title,
                        UserSettingsKeys.WebLifecycle
                            .DIM_SCREEN_ON_INACTIVITY_SECONDS,
                        R.string.setting_info_dim_screen_on_inactivity_seconds,
                    ),
                    SettingDefinition(
                        R.string.web_lifecycle_refresh_on_network_available_title,
                        UserSettingsKeys.WebLifecycle.REFRESH_ON_NETWORK_AVAILABLE,
                        R.string.setting_info_refresh_on_network_available,
                        optionLabelRes =
                            RefreshOnNetworkAvailableOption.entries.map {
                                it.displayLabelRes
                            },
                    ),
                    SettingDefinition(
                        R.string
                            .web_lifecycle_refresh_on_loading_error_interval_seconds_title,
                        UserSettingsKeys.WebLifecycle
                            .REFRESH_ON_LOADING_ERROR_INTERVAL_SECONDS,
                        R.string
                            .setting_info_refresh_on_loading_error_interval_seconds,
                    ),
                    SettingDefinition(
                        R.string.web_lifecycle_refresh_on_screen_wake_title,
                        UserSettingsKeys.WebLifecycle.REFRESH_ON_SCREEN_WAKE,
                        R.string.setting_info_refresh_on_screen_wake,
                    ),
                ),
        ) {
            SettingsWebLifecycleScreen(it)
        }
    add(settingsWebLifecycle)
    val settingsAppearance =
        SettingsPage(
            screen = Screen.SettingsAppearance,
            titleRes = R.string.settings_appearance_title,
            descriptionRes = R.string.settings_appearance_description,
            showOnHome = true,
            settings =
                listOf(
                    SettingDefinition(
                        R.string.appearance_theme_title,
                        UserSettingsKeys.Appearance.THEME,
                        R.string.setting_info_theme,
                        optionLabelRes =
                            ThemeOption.entries.map { it.displayLabelRes },
                    ),
                    SettingDefinition(
                        R.string.appearance_floating_toolbar_mode_title,
                        UserSettingsKeys.Appearance.FLOATING_TOOLBAR_MODE,
                        R.string.setting_info_floating_toolbar_mode,
                        optionLabelRes =
                            FloatingToolbarModeOption.entries.map {
                                it.displayLabelRes
                            },
                    ),
                    SettingDefinition(
                        R.string.appearance_webview_inset_title,
                        UserSettingsKeys.Appearance.WEBVIEW_INSET,
                        R.string.setting_info_web_view_inset,
                        optionLabelRes =
                            WebViewInsetOption.entries.map { it.displayLabelRes },
                    ),
                    SettingDefinition(
                        R.string.appearance_immersive_mode_title,
                        UserSettingsKeys.Appearance.IMMERSIVE_MODE,
                        R.string.setting_info_immersive_mode,
                        optionLabelRes =
                            ImmersiveModeOption.entries.map { it.displayLabelRes },
                        aliasesRes = R.string.settings_search_alias_fullscreen,
                    ),
                    SettingDefinition(
                        R.string.appearance_blocked_message_title,
                        UserSettingsKeys.Appearance.BLOCKED_MESSAGE,
                        R.string.setting_info_blocked_message,
                    ),
                    SettingDefinition(
                        R.string.appearance_custom_block_page_html_title,
                        UserSettingsKeys.Appearance.CUSTOM_BLOCK_PAGE_HTML,
                        R.string.setting_info_custom_block_page_html,
                    ),
                    SettingDefinition(
                        R.string.appearance_address_bar_mode_title,
                        UserSettingsKeys.Appearance.ADDRESS_BAR_MODE,
                        R.string.setting_info_address_bar_mode,
                        optionLabelRes =
                            AddressBarModeOption.entries.map { it.displayLabelRes },
                    ),
                    SettingDefinition(
                        R.string.appearance_address_bar_size_title,
                        UserSettingsKeys.Appearance.ADDRESS_BAR_SIZE,
                        R.string.setting_info_address_bar_size,
                        optionLabelRes =
                            AddressBarSizeOption.entries.map { it.displayLabelRes },
                    ),
                    SettingDefinition(
                        R.string.appearance_address_bar_position_title,
                        UserSettingsKeys.Appearance.ADDRESS_BAR_POSITION,
                        R.string.setting_info_address_bar_position,
                        optionLabelRes =
                            AddressBarPositionOption.entries.map {
                                it.displayLabelRes
                            },
                    ),
                    SettingDefinition(
                        R.string.appearance_show_loading_progress_bar_title,
                        UserSettingsKeys.Appearance.SHOW_LOADING_PROGRESS_BAR,
                        R.string.setting_info_show_loading_progress_bar,
                    ),
                ),
        ) {
            SettingsAppearanceScreen(it)
        }
    add(settingsAppearance)
    val settingsDevice =
        SettingsPage(
            screen = Screen.SettingsDevice,
            titleRes = R.string.settings_device_title,
            descriptionRes = R.string.settings_device_description,
            showOnHome = true,
            settings =
                listOf(
                    SettingDefinition(
                        R.string.device_keep_screen_on_title,
                        UserSettingsKeys.Device.KEEP_SCREEN_ON,
                        R.string.setting_info_keep_screen_on,
                    ),
                    SettingDefinition(
                        R.string.device_rotation_title,
                        UserSettingsKeys.Device.DEVICE_ROTATION,
                        R.string.setting_info_device_rotation,
                        optionLabelRes =
                            DeviceRotationOption.entries.map { it.displayLabelRes },
                    ),
                    SettingDefinition(
                        R.string.device_brightness_title,
                        UserSettingsKeys.Device.BRIGHTNESS,
                        R.string.setting_info_brightness,
                    ),
                    SettingDefinition(
                        R.string.device_allow_camera_title,
                        UserSettingsKeys.Device.ALLOW_CAMERA,
                        R.string.setting_info_allow_camera,
                    ),
                    SettingDefinition(
                        R.string.device_allow_microphone_title,
                        UserSettingsKeys.Device.ALLOW_MICROPHONE,
                        R.string.setting_info_allow_microphone,
                    ),
                    SettingDefinition(
                        R.string.device_allow_location_title,
                        UserSettingsKeys.Device.ALLOW_LOCATION,
                        R.string.setting_info_allow_location,
                    ),
                    SettingDefinition(
                        R.string.device_allow_nfc_title,
                        UserSettingsKeys.Device.ALLOW_NFC,
                        R.string.setting_info_allow_nfc,
                    ),
                    SettingDefinition(
                        R.string.device_allow_notifications_title,
                        UserSettingsKeys.Device.ALLOW_NOTIFICATIONS,
                        R.string.setting_info_allow_notifications,
                    ),
                    SettingDefinition(
                        R.string.device_back_button_hold_action_title,
                        UserSettingsKeys.Device.BACK_BUTTON_HOLD_ACTION,
                        R.string.setting_info_back_button_hold_action,
                        optionLabelRes =
                            BackButtonHoldActionOption.entries.map {
                                it.displayLabelRes
                            },
                    ),
                    SettingDefinition(
                        R.string.device_custom_unlock_shortcut_title,
                        UserSettingsKeys.Device.CUSTOM_UNLOCK_SHORTCUT,
                        R.string.setting_info_custom_unlock_shortcut,
                    ),
                    SettingDefinition(
                        R.string.device_custom_auth_password_title,
                        UserSettingsKeys.Device.CUSTOM_AUTH_PASSWORD,
                        R.string.setting_info_custom_auth_password,
                    ),
                    SettingDefinition(
                        R.string.device_unlock_auth_requirement_title,
                        UserSettingsKeys.Device.UNLOCK_AUTH_REQUIREMENT,
                        R.string.setting_info_unlock_auth_requirement,
                        optionLabelRes =
                            UnlockAuthRequirementOption.entries.map {
                                it.displayLabelRes
                            },
                    ),
                    SettingDefinition(
                        R.string.device_block_screen_capture_title,
                        UserSettingsKeys.Device.BLOCK_SCREEN_CAPTURE,
                        R.string.setting_info_block_screen_capture,
                    ),
                    SettingDefinition(
                        R.string.device_block_volume_keys_title,
                        UserSettingsKeys.Device.BLOCK_VOLUME_KEYS,
                        R.string.setting_info_block_volume_keys,
                    ),
                ),
        ) {
            SettingsDeviceScreen(it)
        }
    add(settingsDevice)
    val settingsDeviceOwner =
        SettingsPage(
            screen = Screen.SettingsDeviceOwner,
            titleRes = R.string.settings_device_owner_title,
            parent = settingsDevice.hierarchy,
            settings =
                listOf(
                    SettingDefinition(
                        R.string.device_owner_lock_task_feature_home_title,
                        UserSettingsKeys.Device.Owner.LockTaskFeature.HOME,
                        R.string.setting_info_lock_task_feature_home,
                    ),
                    SettingDefinition(
                        R.string.device_owner_lock_task_feature_overview_title,
                        UserSettingsKeys.Device.Owner.LockTaskFeature.OVERVIEW,
                        R.string.setting_info_lock_task_feature_overview,
                    ),
                    SettingDefinition(
                        R.string.device_owner_lock_task_feature_global_actions_title,
                        UserSettingsKeys.Device.Owner.LockTaskFeature.GLOBAL_ACTIONS,
                        R.string.setting_info_lock_task_feature_global_actions,
                    ),
                    SettingDefinition(
                        R.string.device_owner_lock_task_feature_notifications_title,
                        UserSettingsKeys.Device.Owner.LockTaskFeature.NOTIFICATIONS,
                        R.string.setting_info_lock_task_feature_notifications,
                    ),
                    SettingDefinition(
                        R.string.device_owner_lock_task_feature_system_info_title,
                        UserSettingsKeys.Device.Owner.LockTaskFeature.SYSTEM_INFO,
                        R.string.setting_info_lock_task_feature_system_info,
                    ),
                    SettingDefinition(
                        R.string.device_owner_lock_task_feature_keyguard_title,
                        UserSettingsKeys.Device.Owner.LockTaskFeature.KEYGUARD,
                        R.string.setting_info_lock_task_feature_keyguard,
                    ),
                    SettingDefinition(
                        R.string
                            .device_owner_lock_task_feature_block_activity_start_in_task_title,
                        UserSettingsKeys.Device.Owner.LockTaskFeature
                            .BLOCK_ACTIVITY_START_IN_TASK,
                        R.string
                            .setting_info_lock_task_feature_block_activity_start_in_task,
                    ),
                    SettingDefinition(
                        R.string.ui_request_permission_on_launch,
                        UserSettingsKeys.Device.Owner.Dhizuku
                            .REQUEST_PERMISSION_ON_LAUNCH,
                        R.string.setting_info_dhizuku_request_permission_on_launch,
                    ),
                ),
        ) {
            SettingsDeviceOwnerScreen(it)
        }
    add(settingsDeviceOwner)
    val settingsJsScript =
        SettingsPage(
            screen = Screen.SettingsJsScript,
            titleRes = R.string.settings_js_scripts_title,
            descriptionRes = R.string.settings_js_scripts_description,
            showOnHome = true,
            settings =
                listOf(
                    SettingDefinition(
                        R.string.js_scripts_apply_app_theme_title,
                        UserSettingsKeys.JsScripts.APPLY_APP_THEME,
                        R.string.setting_info_apply_app_theme,
                    ),
                    SettingDefinition(
                        R.string.js_scripts_apply_desktop_viewport_width_title,
                        UserSettingsKeys.JsScripts.APPLY_DESKTOP_VIEWPORT_WIDTH,
                        R.string.setting_info_apply_desktop_viewport_width,
                    ),
                    SettingDefinition(
                        R.string.js_scripts_enable_battery_api_title,
                        UserSettingsKeys.JsScripts.ENABLE_BATTERY_API,
                        R.string.setting_info_enable_battery_api,
                    ),
                    SettingDefinition(
                        R.string.js_scripts_enable_brightness_api_title,
                        UserSettingsKeys.JsScripts.ENABLE_BRIGHTNESS_API,
                        R.string.setting_info_enable_brightness_api,
                    ),
                    SettingDefinition(
                        R.string.js_scripts_enable_vibration_api_title,
                        UserSettingsKeys.JsScripts.ENABLE_VIBRATION_API,
                        R.string.setting_info_enable_vibration_api,
                    ),
                    SettingDefinition(
                        R.string.js_scripts_enable_dark_reader_title,
                        UserSettingsKeys.JsScripts.ENABLE_DARK_READER,
                        R.string.setting_info_enable_dark_reader,
                    ),
                    SettingDefinition(
                        R.string.js_scripts_enable_eruda_console_title,
                        UserSettingsKeys.JsScripts.ENABLE_ERUDA_CONSOLE,
                        R.string.setting_info_enable_eruda_console,
                    ),
                    SettingDefinition(
                        R.string.js_scripts_custom_script_on_page_start_title,
                        UserSettingsKeys.JsScripts.CUSTOM_SCRIPT_ON_PAGE_START,
                        R.string.setting_info_custom_script_on_page_start,
                        aliasesRes = R.string.settings_search_alias_userscript,
                    ),
                    SettingDefinition(
                        R.string.js_scripts_custom_script_on_page_finish_title,
                        UserSettingsKeys.JsScripts.CUSTOM_SCRIPT_ON_PAGE_FINISH,
                        R.string.setting_info_custom_script_on_page_finish,
                        aliasesRes = R.string.settings_search_alias_userscript,
                    ),
                ),
        ) {
            SettingsJsScriptsScreen(it)
        }
    add(settingsJsScript)
    val settingsMqtt =
        SettingsPage(
            screen = Screen.SettingsMqtt,
            titleRes = R.string.settings_mqtt_title,
            descriptionRes = R.string.settings_mqtt_description,
            showOnHome = true,
            settings =
                listOf(
                    SettingDefinition(
                        R.string.mqtt_enabled_title,
                        UserSettingsKeys.Mqtt.ENABLED,
                        R.string.setting_info_mqtt_enabled,
                    ),
                    SettingDefinition(
                        R.string.mqtt_use_foreground_service_title,
                        UserSettingsKeys.Mqtt.USE_FOREGROUND_SERVICE,
                        R.string.setting_info_mqtt_use_foreground_service,
                    ),
                ),
        ) {
            SettingsMqttScreen(it)
        }
    add(settingsMqtt)
    val settingsMqttDebug =
        SettingsPage(
            screen = Screen.SettingsMqttDebug,
            aliasesRes = R.string.settings_search_alias_debug_logs,
            titleRes = R.string.settings_debug_log_title,
            parent = settingsMqtt.hierarchy,
        ) {
            SettingsMqttDebugScreen(it)
        }
    add(settingsMqttDebug)
    val settingsMqttConnection =
        SettingsPage(
            screen = Screen.SettingsMqttConnection,
            titleRes = R.string.settings_mqtt_connection_title,
            parent = settingsMqtt.hierarchy,
            descriptionRes = R.string.settings_mqtt_connection_description,
            settings =
                listOf(
                    SettingDefinition(
                        R.string.mqtt_connection_server_host_title,
                        UserSettingsKeys.Mqtt.Connection.SERVER_HOST,
                        R.string.setting_info_mqtt_server_host,
                    ),
                    SettingDefinition(
                        R.string.mqtt_connection_server_port_title,
                        UserSettingsKeys.Mqtt.Connection.SERVER_PORT,
                        R.string.setting_info_mqtt_server_port,
                    ),
                    SettingDefinition(
                        R.string.mqtt_connection_use_tls_title,
                        UserSettingsKeys.Mqtt.Connection.USE_TLS,
                        R.string.setting_info_mqtt_use_tls,
                    ),
                    SettingDefinition(
                        R.string.mqtt_connection_username_title,
                        UserSettingsKeys.Mqtt.Connection.USERNAME,
                        R.string.setting_info_mqtt_username,
                    ),
                    SettingDefinition(
                        R.string.mqtt_connection_password_title,
                        UserSettingsKeys.Mqtt.Connection.PASSWORD,
                        R.string.setting_info_mqtt_password,
                    ),
                    SettingDefinition(
                        R.string.mqtt_connection_client_id_title,
                        UserSettingsKeys.Mqtt.Connection.CLIENT_ID,
                        R.string.setting_info_mqtt_client_id,
                    ),
                    SettingDefinition(
                        R.string.mqtt_connection_clean_start_title,
                        UserSettingsKeys.Mqtt.Connection.CLEAN_START,
                        R.string.setting_info_mqtt_clean_start,
                    ),
                    SettingDefinition(
                        R.string.mqtt_connection_keep_alive_title,
                        UserSettingsKeys.Mqtt.Connection.KEEP_ALIVE,
                        R.string.setting_info_mqtt_keep_alive,
                    ),
                    SettingDefinition(
                        R.string.mqtt_connection_connect_timeout_title,
                        UserSettingsKeys.Mqtt.Connection.CONNECT_TIMEOUT,
                        R.string.setting_info_mqtt_connect_timeout,
                    ),
                    SettingDefinition(
                        R.string.mqtt_connection_socket_connect_timeout_title,
                        UserSettingsKeys.Mqtt.Connection.SOCKET_CONNECT_TIMEOUT,
                        R.string.setting_info_mqtt_socket_connect_timeout,
                    ),
                    SettingDefinition(
                        R.string.mqtt_connection_automatic_reconnect_title,
                        UserSettingsKeys.Mqtt.Connection.AUTOMATIC_RECONNECT,
                        R.string.setting_info_mqtt_automatic_reconnect,
                    ),
                    SettingDefinition(
                        R.string.mqtt_connection_session_expiry_interval_title,
                        UserSettingsKeys.Mqtt.Connection.SESSION_EXPIRY_INTERVAL,
                        R.string.setting_info_mqtt_session_expiry_interval,
                    ),
                    SettingDefinition(
                        R.string.mqtt_connection_use_websocket_title,
                        UserSettingsKeys.Mqtt.Connection.USE_WEBSOCKET,
                        R.string.setting_info_mqtt_use_web_socket,
                    ),
                    SettingDefinition(
                        R.string.mqtt_connection_websocket_server_path_title,
                        UserSettingsKeys.Mqtt.Connection.WEBSOCKET_SERVER_PATH,
                        R.string.setting_info_mqtt_web_socket_server_path,
                    ),
                ),
        ) {
            SettingsMqttConnectionScreen(it)
        }
    add(settingsMqttConnection)
    val settingsMqttTopics =
        SettingsPage(
            screen = Screen.SettingsMqttTopics,
            titleRes = R.string.settings_mqtt_topics_title,
            parent = settingsMqtt.hierarchy,
            descriptionRes = R.string.settings_mqtt_topics_description,
        ) {
            SettingsMqttTopicsScreen(it)
        }
    add(settingsMqttTopics)
    val mqttPublish =
        SettingsHierarchy(
            R.string.settings_mqtt_topics_publish_title,
            settingsMqttTopics.hierarchy,
        )
    val settingsMqttTopicsPublishEvent =
        SettingsPage(
            screen = Screen.SettingsMqttTopicsPublishEvent,
            titleRes = R.string.settings_mqtt_topics_publish_event_title,
            parent = mqttPublish,
            descriptionRes = R.string.settings_mqtt_topics_publish_event_description,
            settings =
                listOf(
                    SettingDefinition(
                        R.string.mqtt_publish_event_topic_title,
                        UserSettingsKeys.Mqtt.Topics.Publish.Event.TOPIC,
                        R.string.setting_info_mqtt_publish_event_topic,
                    ),
                    SettingDefinition(
                        R.string.mqtt_publish_event_qos_title,
                        UserSettingsKeys.Mqtt.Topics.Publish.Event.QOS,
                        R.string.setting_info_mqtt_publish_event_qos,
                        optionLabelRes =
                            MqttQosOption.entries.map { it.displayLabelRes },
                    ),
                    SettingDefinition(
                        R.string.mqtt_publish_event_retain_title,
                        UserSettingsKeys.Mqtt.Topics.Publish.Event.RETAIN,
                        R.string.setting_info_mqtt_publish_event_retain,
                    ),
                ),
        ) {
            SettingsMqttTopicsPublishEventScreen(it)
        }
    add(settingsMqttTopicsPublishEvent)
    val settingsMqttTopicsPublishResponse =
        SettingsPage(
            screen = Screen.SettingsMqttTopicsPublishResponse,
            titleRes = R.string.settings_mqtt_topics_publish_response_title,
            parent = mqttPublish,
            descriptionRes =
                R.string.settings_mqtt_topics_publish_response_description,
            settings =
                listOf(
                    SettingDefinition(
                        R.string.mqtt_publish_response_topic_title,
                        UserSettingsKeys.Mqtt.Topics.Publish.Response.TOPIC,
                        R.string.setting_info_mqtt_publish_response_topic,
                    ),
                    SettingDefinition(
                        R.string.mqtt_publish_response_qos_title,
                        UserSettingsKeys.Mqtt.Topics.Publish.Response.QOS,
                        R.string.setting_info_mqtt_publish_response_qos,
                        optionLabelRes =
                            MqttQosOption.entries.map { it.displayLabelRes },
                    ),
                    SettingDefinition(
                        R.string.mqtt_publish_response_retain_title,
                        UserSettingsKeys.Mqtt.Topics.Publish.Response.RETAIN,
                        R.string.setting_info_mqtt_publish_response_retain,
                    ),
                ),
        ) {
            SettingsMqttTopicsPublishResponseScreen(it)
        }
    add(settingsMqttTopicsPublishResponse)
    val mqttSubscribe =
        SettingsHierarchy(
            R.string.settings_mqtt_topics_subscribe_title,
            settingsMqttTopics.hierarchy,
        )
    val settingsMqttTopicsSubscribeCommand =
        SettingsPage(
            screen = Screen.SettingsMqttTopicsSubscribeCommand,
            titleRes = R.string.settings_mqtt_topics_subscribe_command_title,
            parent = mqttSubscribe,
            descriptionRes =
                R.string.settings_mqtt_topics_subscribe_command_description,
            settings =
                listOf(
                    SettingDefinition(
                        R.string.mqtt_subscribe_command_topic_title,
                        UserSettingsKeys.Mqtt.Topics.Subscribe.Command.TOPIC,
                        R.string.setting_info_mqtt_subscribe_command_topic,
                    ),
                    SettingDefinition(
                        R.string.mqtt_subscribe_command_qos_title,
                        UserSettingsKeys.Mqtt.Topics.Subscribe.Command.QOS,
                        R.string.setting_info_mqtt_subscribe_command_qos,
                        optionLabelRes =
                            MqttQosOption.entries.map { it.displayLabelRes },
                    ),
                    SettingDefinition(
                        R.string.mqtt_subscribe_command_retain_handling_title,
                        UserSettingsKeys.Mqtt.Topics.Subscribe.Command
                            .RETAIN_HANDLING,
                        R.string.setting_info_mqtt_subscribe_command_retain_handling,
                        optionLabelRes =
                            MqttRetainHandlingOption.entries.map {
                                it.displayLabelRes
                            },
                    ),
                    SettingDefinition(
                        R.string.mqtt_subscribe_command_retain_as_published_title,
                        UserSettingsKeys.Mqtt.Topics.Subscribe.Command
                            .RETAIN_AS_PUBLISHED,
                        R.string
                            .setting_info_mqtt_subscribe_command_retain_as_published,
                    ),
                ),
        ) {
            SettingsMqttTopicsSubscribeCommandScreen(it)
        }
    add(settingsMqttTopicsSubscribeCommand)
    val settingsMqttTopicsSubscribeSettings =
        SettingsPage(
            screen = Screen.SettingsMqttTopicsSubscribeSettings,
            titleRes = R.string.settings_mqtt_topics_subscribe_settings_title,
            parent = mqttSubscribe,
            descriptionRes =
                R.string.settings_mqtt_topics_subscribe_settings_description,
            settings =
                listOf(
                    SettingDefinition(
                        R.string.mqtt_subscribe_settings_topic_title,
                        UserSettingsKeys.Mqtt.Topics.Subscribe.Settings.TOPIC,
                        R.string.setting_info_mqtt_subscribe_settings_topic,
                    ),
                    SettingDefinition(
                        R.string.mqtt_subscribe_settings_qos_title,
                        UserSettingsKeys.Mqtt.Topics.Subscribe.Settings.QOS,
                        R.string.setting_info_mqtt_subscribe_settings_qos,
                        optionLabelRes =
                            MqttQosOption.entries.map { it.displayLabelRes },
                    ),
                    SettingDefinition(
                        R.string.mqtt_subscribe_settings_retain_handling_title,
                        UserSettingsKeys.Mqtt.Topics.Subscribe.Settings
                            .RETAIN_HANDLING,
                        R.string
                            .setting_info_mqtt_subscribe_settings_retain_handling,
                        optionLabelRes =
                            MqttRetainHandlingOption.entries.map {
                                it.displayLabelRes
                            },
                    ),
                    SettingDefinition(
                        R.string.mqtt_subscribe_settings_retain_as_published_title,
                        UserSettingsKeys.Mqtt.Topics.Subscribe.Settings
                            .RETAIN_AS_PUBLISHED,
                        R.string
                            .setting_info_mqtt_subscribe_settings_retain_as_published,
                    ),
                ),
        ) {
            SettingsMqttTopicsSubscribeSettingsScreen(it)
        }
    add(settingsMqttTopicsSubscribeSettings)
    val settingsMqttTopicsSubscribeRequest =
        SettingsPage(
            screen = Screen.SettingsMqttTopicsSubscribeRequest,
            titleRes = R.string.settings_mqtt_topics_subscribe_request_title,
            parent = mqttSubscribe,
            descriptionRes =
                R.string.settings_mqtt_topics_subscribe_request_description,
            settings =
                listOf(
                    SettingDefinition(
                        R.string.mqtt_subscribe_request_topic_title,
                        UserSettingsKeys.Mqtt.Topics.Subscribe.Request.TOPIC,
                        R.string.setting_info_mqtt_subscribe_request_topic,
                    ),
                    SettingDefinition(
                        R.string.mqtt_subscribe_request_qos_title,
                        UserSettingsKeys.Mqtt.Topics.Subscribe.Request.QOS,
                        R.string.setting_info_mqtt_subscribe_request_qos,
                        optionLabelRes =
                            MqttQosOption.entries.map { it.displayLabelRes },
                    ),
                    SettingDefinition(
                        R.string.mqtt_subscribe_request_retain_handling_title,
                        UserSettingsKeys.Mqtt.Topics.Subscribe.Request
                            .RETAIN_HANDLING,
                        R.string.setting_info_mqtt_subscribe_request_retain_handling,
                        optionLabelRes =
                            MqttRetainHandlingOption.entries.map {
                                it.displayLabelRes
                            },
                    ),
                    SettingDefinition(
                        R.string.mqtt_subscribe_request_retain_as_published_title,
                        UserSettingsKeys.Mqtt.Topics.Subscribe.Request
                            .RETAIN_AS_PUBLISHED,
                        R.string
                            .setting_info_mqtt_subscribe_request_retain_as_published,
                    ),
                ),
        ) {
            SettingsMqttTopicsSubscribeRequestScreen(it)
        }
    add(settingsMqttTopicsSubscribeRequest)
    val settingsMqttHomeAssistant =
        SettingsPage(
            screen = Screen.SettingsMqttHomeAssistant,
            titleRes = R.string.settings_mqtt_home_assistant_title,
            parent = settingsMqtt.hierarchy,
            descriptionRes = R.string.settings_mqtt_home_assistant_description,
            settings =
                listOf(
                    SettingDefinition(
                        R.string.mqtt_home_assistant_enabled_title,
                        UserSettingsKeys.Mqtt.HomeAssistant.ENABLED,
                        R.string.setting_info_mqtt_home_assistant_enable_discovery,
                    ),
                    SettingDefinition(
                        R.string.mqtt_home_assistant_device_name_title,
                        UserSettingsKeys.Mqtt.HomeAssistant.DEVICE_NAME,
                        R.string.setting_info_mqtt_home_assistant_device_name,
                    ),
                    SettingDefinition(
                        R.string.mqtt_home_assistant_discovery_prefix_title,
                        UserSettingsKeys.Mqtt.HomeAssistant.DISCOVERY_PREFIX,
                        R.string.setting_info_mqtt_home_assistant_discovery_prefix,
                    ),
                    SettingDefinition(
                        R.string.mqtt_home_assistant_status_topic_title,
                        UserSettingsKeys.Mqtt.HomeAssistant.STATUS_TOPIC,
                        R.string.setting_info_mqtt_home_assistant_status_topic,
                    ),
                ),
        ) {
            SettingsMqttHomeAssistantScreen(it)
        }
    add(settingsMqttHomeAssistant)
    val settingsMqttWill =
        SettingsPage(
            screen = Screen.SettingsMqttWill,
            titleRes = R.string.settings_mqtt_will_title,
            parent = settingsMqtt.hierarchy,
            descriptionRes = R.string.settings_mqtt_will_description,
            settings =
                listOf(
                    SettingDefinition(
                        R.string.mqtt_will_topic_title,
                        UserSettingsKeys.Mqtt.Will.TOPIC,
                        R.string.setting_info_mqtt_will_topic,
                    ),
                    SettingDefinition(
                        R.string.mqtt_will_qos_title,
                        UserSettingsKeys.Mqtt.Will.QOS,
                        R.string.setting_info_mqtt_will_qos,
                        optionLabelRes =
                            MqttQosOption.entries.map { it.displayLabelRes },
                    ),
                    SettingDefinition(
                        R.string.mqtt_will_payload_title,
                        UserSettingsKeys.Mqtt.Will.PAYLOAD,
                        R.string.setting_info_mqtt_will_payload,
                    ),
                    SettingDefinition(
                        R.string.mqtt_will_retain_title,
                        UserSettingsKeys.Mqtt.Will.RETAIN,
                        R.string.setting_info_mqtt_will_retain,
                    ),
                    SettingDefinition(
                        R.string.mqtt_will_message_expiry_interval_title,
                        UserSettingsKeys.Mqtt.Will.MESSAGE_EXPIRY_INTERVAL,
                        R.string.setting_info_mqtt_will_message_expiry_interval,
                    ),
                    SettingDefinition(
                        R.string.mqtt_will_delay_interval_title,
                        UserSettingsKeys.Mqtt.Will.DELAY_INTERVAL,
                        R.string.setting_info_mqtt_will_delay_interval,
                    ),
                ),
        ) {
            SettingsMqttWillScreen(it)
        }
    add(settingsMqttWill)
    val settingsMqttRestrictions =
        SettingsPage(
            screen = Screen.SettingsMqttRestrictions,
            titleRes = R.string.settings_mqtt_restrictions_title,
            parent = settingsMqtt.hierarchy,
            descriptionRes = R.string.settings_mqtt_restrictions_description,
            settings =
                listOf(
                    SettingDefinition(
                        R.string.mqtt_restrictions_receive_maximum_title,
                        UserSettingsKeys.Mqtt.Restrictions.RECEIVE_MAXIMUM,
                        R.string.setting_info_mqtt_restrictions_receive_maximum,
                    ),
                    SettingDefinition(
                        R.string.mqtt_restrictions_send_maximum_title,
                        UserSettingsKeys.Mqtt.Restrictions.SEND_MAXIMUM,
                        R.string.setting_info_mqtt_restrictions_send_maximum,
                    ),
                    SettingDefinition(
                        R.string.mqtt_restrictions_maximum_packet_size_title,
                        UserSettingsKeys.Mqtt.Restrictions.MAXIMUM_PACKET_SIZE,
                        R.string.setting_info_mqtt_restrictions_maximum_packet_size,
                    ),
                    SettingDefinition(
                        R.string.mqtt_restrictions_send_maximum_packet_size_title,
                        UserSettingsKeys.Mqtt.Restrictions.SEND_MAXIMUM_PACKET_SIZE,
                        R.string
                            .setting_info_mqtt_restrictions_send_maximum_packet_size,
                    ),
                    SettingDefinition(
                        R.string.mqtt_restrictions_topic_alias_maximum_title,
                        UserSettingsKeys.Mqtt.Restrictions.TOPIC_ALIAS_MAXIMUM,
                        R.string.setting_info_mqtt_restrictions_topic_alias_maximum,
                    ),
                    SettingDefinition(
                        R.string.mqtt_restrictions_send_topic_alias_maximum_title,
                        UserSettingsKeys.Mqtt.Restrictions.SEND_TOPIC_ALIAS_MAXIMUM,
                        R.string
                            .setting_info_mqtt_restrictions_send_topic_alias_maximum,
                    ),
                    SettingDefinition(
                        R.string.mqtt_restrictions_request_problem_information_title,
                        UserSettingsKeys.Mqtt.Restrictions
                            .REQUEST_PROBLEM_INFORMATION,
                        R.string
                            .setting_info_mqtt_restrictions_request_problem_information,
                    ),
                    SettingDefinition(
                        R.string
                            .mqtt_restrictions_request_response_information_title,
                        UserSettingsKeys.Mqtt.Restrictions
                            .REQUEST_RESPONSE_INFORMATION,
                        R.string
                            .setting_info_mqtt_restrictions_request_response_information,
                    ),
                ),
        ) {
            SettingsMqttRestrictionsScreen(it)
        }
    add(settingsMqttRestrictions)
    val settingsUnifiedPush =
        SettingsPage(
            screen = Screen.SettingsUnifiedPush,
            titleRes = R.string.settings_unifiedpush_title,
            descriptionRes = R.string.settings_unifiedpush_description,
            showOnHome = true,
            settings =
                listOf(
                    SettingDefinition(
                        R.string.unifiedpush_enabled_title,
                        UserSettingsKeys.UnifiedPush.ENABLED,
                        R.string.setting_info_unified_push_enabled,
                    ),
                    SettingDefinition(
                        R.string.unifiedpush_distributor_title,
                        UserSettingsKeys.UnifiedPush.DISTRIBUTOR,
                        R.string.setting_info_unified_push_distributor,
                    ),
                    SettingDefinition(
                        R.string.unifiedpush_instance_title,
                        UserSettingsKeys.UnifiedPush.INSTANCE,
                        R.string.setting_info_unified_push_instance,
                    ),
                    SettingDefinition(
                        R.string.unifiedpush_message_for_distributor_title,
                        UserSettingsKeys.UnifiedPush.MESSAGE_FOR_DISTRIBUTOR,
                        R.string.setting_info_unified_push_message_for_distributor,
                    ),
                    SettingDefinition(
                        R.string.unifiedpush_vapid_public_key_title,
                        UserSettingsKeys.UnifiedPush.VAPID_PUBLIC_KEY,
                        R.string.setting_info_unified_push_vapid_public_key,
                    ),
                    SettingDefinition(
                        R.string.unifiedpush_process_unencrypted_messages_title,
                        UserSettingsKeys.UnifiedPush.PROCESS_UNENCRYPTED_MESSAGES,
                        R.string
                            .setting_info_unified_push_process_unencrypted_messages,
                    ),
                    SettingDefinition(
                        R.string.unifiedpush_store_endpoint_credentials_title,
                        UserSettingsKeys.UnifiedPush.STORE_ENDPOINT_CREDENTIALS,
                        R.string
                            .setting_info_unified_push_store_endpoint_credentials,
                    ),
                ),
        ) {
            SettingsUnifiedPushScreen(it)
        }
    add(settingsUnifiedPush)
    val settingsUnifiedPushDebug =
        SettingsPage(
            screen = Screen.SettingsUnifiedPushDebug,
            aliasesRes = R.string.settings_search_alias_debug_logs,
            titleRes = R.string.settings_debug_log_title,
            parent = settingsUnifiedPush.hierarchy,
        ) {
            SettingsUnifiedPushDebugScreen(it)
        }
    add(settingsUnifiedPushDebug)
    val settingsAbout =
        SettingsPage(
            screen = Screen.SettingsAbout,
            titleRes = R.string.settings_about_title,
            descriptionRes = R.string.settings_about_description,
            showOnHome = true,
            items =
                listOf(
                        R.string.about_app_name,
                        R.string.about_app_package,
                        R.string.about_app_version,
                        R.string.about_app_min_sdk,
                        R.string.about_app_target_sdk,
                        R.string.about_app_debug_build,
                        R.string.about_app_installer,
                        R.string.about_app_instance_id,
                        R.string.about_source_code_url,
                        R.string.about_app_device_owner,
                        R.string.about_app_profile_owner,
                        R.string.about_app_management_mode,
                        R.string.about_app_management_owner,
                        R.string.about_app_management_user_type,
                        R.string.about_device_managed_profile,
                        R.string.about_app_lock_task_permitted,
                        R.string.about_app_dhizuku_permission_granted,
                        R.string.about_app_manage_lock_task_packages,
                        R.string.about_app_manage_lock_task_features,
                        R.string.about_app_lock_device,
                        R.string.about_device_android_version,
                        R.string.about_device_webview_version,
                        R.string.about_device_screen_display,
                        R.string.about_device_time_zone,
                        R.string.about_device_locale,
                        R.string.about_device_total_ram,
                        R.string.about_device_total_storage,
                        R.string.about_device_model,
                        R.string.about_device_manufacturer,
                        R.string.about_device_brand,
                        R.string.about_device_device,
                        R.string.about_device_product,
                        R.string.about_device_hardware,
                        R.string.about_device_board,
                        R.string.about_device_bootloader,
                        R.string.about_device_security_patch,
                        R.string.about_device_supported_abis,
                        R.string.about_device_supported_32bit_abis,
                        R.string.about_device_supported_64bit_abis,
                        R.string.about_device_build_fingerprint,
                    )
                    .map {
                        SettingsItemDefinition(
                            it,
                            SettingsSearchResultKind.INFORMATION,
                        )
                    },
        ) {
            SettingsAboutScreen(it)
        }
    add(settingsAbout)
    val settingsMoreActions =
        SettingsPage(
            screen = Screen.SettingsMoreActions,
            titleRes = R.string.settings_more_actions_title,
            items =
                listOf(
                        R.string.settings_more_action_app_info,
                        R.string.settings_more_action_device_settings,
                        R.string.settings_more_action_default_launcher,
                        R.string.settings_more_action_default_apps,
                        R.string.settings_more_action_wifi_settings,
                        R.string.settings_more_action_data_usage,
                        R.string.settings_more_action_create_shortcut,
                        R.string.settings_more_action_app_launcher,
                        R.string.settings_more_action_clear_cookies,
                        R.string.settings_more_action_clear_cache,
                        R.string.settings_more_action_clear_form_data,
                        R.string.settings_more_action_clear_history,
                        R.string.settings_more_action_clear_ssl_preferences,
                        R.string.settings_more_action_clear_web_storage,
                    )
                    .map {
                        SettingsItemDefinition(it, SettingsSearchResultKind.ACTION)
                    },
        ) {
            SettingsMoreActionsScreen(it)
        }
    add(settingsMoreActions)
}

internal val settingsDefinitionsByKey: Map<String, SettingDefinition> =
    settingsPages.flatMap { it.settings }.associateBy { it.key }

internal fun settingDefinition(key: String): SettingDefinition =
    requireNotNull(settingsDefinitionsByKey[key]) { "No settings metadata for $key" }
