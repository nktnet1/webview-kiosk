package uk.nktnet.webviewkiosk.ui.screens

import android.content.Context
import androidx.annotation.StringRes
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.Screen

/**
 * Searchable settings and their actual navigation destinations. Keep each section's
 * field labels in sync with its Settings*Screen composables. Matching uses resource
 * IDs rather than user values or hardcoded English strings.
 */
internal data class SettingsSearchSection(
    @StringRes val titleRes: Int,
    val route: String,
    @StringRes val descriptionRes: Int? = null,
    val fields: List<Int> = emptyList(),
)

internal data class SettingsSearchResult(
    @StringRes val titleRes: Int,
    val route: String,
    @StringRes val sectionTitleRes: Int?,
    @StringRes val targetRes: Int? = null,
    @StringRes val descriptionRes: Int? = null,
)

internal val settingsSearchSections: List<SettingsSearchSection> =
    listOf(
        SettingsSearchSection(
            R.string.settings_web_content_title,
            Screen.SettingsWebContent.route,
            descriptionRes = R.string.settings_web_content_description,
            fields =
                listOf(
                    R.string.web_content_home_url_title,
                    R.string.web_content_website_blacklist_title,
                    R.string.web_content_website_whitelist_title,
                    R.string.web_content_website_bookmarks_title,
                    R.string.web_content_support_pdf_rendering_title,
                    R.string.web_content_allow_local_files_title,
                    R.string.web_content_enable_content_blocking_title,
                ),
        ),
        SettingsSearchSection(
            R.string.settings_files_title,
            Screen.SettingsWebContentFiles.route,
            descriptionRes = R.string.settings_web_content_files_description,
            fields = listOf(R.string.ui_legacy_file),
        ),
        SettingsSearchSection(
            R.string.settings_content_blocking_title,
            Screen.SettingsContentBlocking.route,
            descriptionRes = R.string.settings_content_blocking_description,
        ),
        SettingsSearchSection(
            R.string.settings_web_browsing_title,
            Screen.SettingsWebBrowsing.route,
            descriptionRes = R.string.settings_web_browsing_description,
            fields =
                listOf(
                    R.string.web_browsing_allow_refresh_title,
                    R.string.web_browsing_allow_pull_to_refresh_title,
                    R.string.web_browsing_allow_backwards_navigation_title,
                    R.string.web_browsing_allow_go_home_title,
                    R.string.web_browsing_clear_history_on_home_title,
                    R.string.web_browsing_replace_history_url_on_redirect_title,
                    R.string.web_browsing_allow_history_access_title,
                    R.string.web_browsing_allow_bookmark_access_title,
                    R.string.web_browsing_allow_other_url_schemes_title,
                    R.string.web_browsing_allow_default_long_press_title,
                    R.string.web_browsing_allow_link_long_press_context_menu_title,
                    R.string.web_browsing_override_url_loading_block_action_title,
                    R.string.web_browsing_address_bar_actions_title,
                    R.string.web_browsing_kiosk_control_panel_region_title,
                    R.string.web_browsing_kiosk_control_panel_actions_title,
                    R.string.web_browsing_search_provider_url_title,
                    R.string.web_browsing_search_suggestion_engine_title,
                ),
        ),
        SettingsSearchSection(
            R.string.settings_site_permissions_title,
            Screen.SettingsWebBrowsingSitePermissions.route,
        ),
        SettingsSearchSection(
            R.string.settings_web_engine_title,
            Screen.SettingsWebEngine.route,
            descriptionRes = R.string.settings_web_engine_description,
            fields =
                listOf(
                    R.string.web_engine_enable_javascript_title,
                    R.string.web_engine_enable_dom_storage_title,
                    R.string.web_engine_accept_cookies_title,
                    R.string.web_engine_accept_third_party_cookies_title,
                    R.string.web_engine_cache_mode_title,
                    R.string.web_engine_user_agent_title,
                    R.string.web_engine_layout_algorithm_title,
                    R.string.web_engine_use_wide_viewport_title,
                    R.string.web_engine_load_with_overview_mode_title,
                    R.string.web_engine_support_zoom_title,
                    R.string.web_engine_built_in_zoom_controls_title,
                    R.string.web_engine_display_zoom_controls_title,
                    R.string.web_engine_initial_scale_title,
                    R.string.web_engine_allow_file_access_from_file_urls_title,
                    R.string.web_engine_allow_universal_access_from_file_urls_title,
                    R.string.web_engine_allow_file_picker_title,
                    R.string.web_engine_allow_file_download_title,
                    R.string.web_engine_media_playback_requires_user_gesture_title,
                    R.string.web_engine_mutual_tls_title,
                    R.string.web_engine_ssl_error_mode_title,
                    R.string.web_engine_mixed_content_mode_title,
                    R.string.web_engine_over_scroll_mode_title,
                    R.string.web_engine_request_focus_on_page_start_title,
                ),
        ),
        SettingsSearchSection(
            R.string.settings_web_lifecycle_title,
            Screen.SettingsWebLifecycle.route,
            descriptionRes = R.string.settings_web_lifecycle_description,
            fields =
                listOf(
                    R.string.web_lifecycle_lock_on_launch_title,
                    R.string.web_lifecycle_reset_on_launch_title,
                    R.string.web_lifecycle_reset_on_inactivity_seconds_title,
                    R.string.web_lifecycle_reset_actions_title,
                    R.string.web_lifecycle_dim_screen_on_inactivity_seconds_title,
                    R.string.web_lifecycle_refresh_on_network_available_title,
                    R.string
                        .web_lifecycle_refresh_on_loading_error_interval_seconds_title,
                    R.string.web_lifecycle_refresh_on_screen_wake_title,
                ),
        ),
        SettingsSearchSection(
            R.string.settings_appearance_title,
            Screen.SettingsAppearance.route,
            descriptionRes = R.string.settings_appearance_description,
            fields =
                listOf(
                    R.string.appearance_theme_title,
                    R.string.appearance_floating_toolbar_mode_title,
                    R.string.appearance_webview_inset_title,
                    R.string.appearance_immersive_mode_title,
                    R.string.appearance_blocked_message_title,
                    R.string.appearance_custom_block_page_html_title,
                    R.string.appearance_address_bar_mode_title,
                    R.string.appearance_address_bar_size_title,
                    R.string.appearance_address_bar_position_title,
                    R.string.appearance_show_loading_progress_bar_title,
                ),
        ),
        SettingsSearchSection(
            R.string.settings_device_title,
            Screen.SettingsDevice.route,
            descriptionRes = R.string.settings_device_description,
            fields =
                listOf(
                    R.string.device_keep_screen_on_title,
                    R.string.device_rotation_title,
                    R.string.device_brightness_title,
                    R.string.device_allow_camera_title,
                    R.string.device_allow_microphone_title,
                    R.string.device_allow_location_title,
                    R.string.device_allow_nfc_title,
                    R.string.device_allow_notifications_title,
                    R.string.device_back_button_hold_action_title,
                    R.string.device_custom_unlock_shortcut_title,
                    R.string.device_custom_auth_password_title,
                    R.string.device_unlock_auth_requirement_title,
                    R.string.device_block_screen_capture_title,
                    R.string.device_block_volume_keys_title,
                ),
        ),
        SettingsSearchSection(
            R.string.settings_device_owner_title,
            Screen.SettingsDeviceOwner.route,
            fields =
                listOf(
                    R.string.device_owner_lock_task_feature_home_title,
                    R.string.device_owner_lock_task_feature_overview_title,
                    R.string.device_owner_lock_task_feature_global_actions_title,
                    R.string.device_owner_lock_task_feature_notifications_title,
                    R.string.device_owner_lock_task_feature_system_info_title,
                    R.string.device_owner_lock_task_feature_keyguard_title,
                    R.string
                        .device_owner_lock_task_feature_block_activity_start_in_task_title,
                    R.string.ui_request_permission_on_launch,
                ),
        ),
        SettingsSearchSection(
            R.string.settings_js_scripts_title,
            Screen.SettingsJsScript.route,
            descriptionRes = R.string.settings_js_scripts_description,
            fields =
                listOf(
                    R.string.js_scripts_apply_app_theme_title,
                    R.string.js_scripts_apply_desktop_viewport_width_title,
                    R.string.js_scripts_enable_battery_api_title,
                    R.string.js_scripts_enable_brightness_api_title,
                    R.string.js_scripts_enable_vibration_api_title,
                    R.string.js_scripts_enable_dark_reader_title,
                    R.string.js_scripts_enable_eruda_console_title,
                    R.string.js_scripts_custom_script_on_page_start_title,
                    R.string.js_scripts_custom_script_on_page_finish_title,
                ),
        ),
        SettingsSearchSection(
            R.string.settings_mqtt_title,
            Screen.SettingsMqtt.route,
            descriptionRes = R.string.settings_mqtt_description,
            fields =
                listOf(
                    R.string.mqtt_enabled_title,
                    R.string.mqtt_use_foreground_service_title,
                ),
        ),
        SettingsSearchSection(
            R.string.settings_mqtt_connection_title,
            Screen.SettingsMqttConnection.route,
            descriptionRes = R.string.settings_mqtt_connection_description,
            fields =
                listOf(
                    R.string.mqtt_connection_server_host_title,
                    R.string.mqtt_connection_server_port_title,
                    R.string.mqtt_connection_use_tls_title,
                    R.string.mqtt_connection_username_title,
                    R.string.mqtt_connection_password_title,
                    R.string.mqtt_connection_client_id_title,
                    R.string.mqtt_connection_clean_start_title,
                    R.string.mqtt_connection_keep_alive_title,
                    R.string.mqtt_connection_connect_timeout_title,
                    R.string.mqtt_connection_socket_connect_timeout_title,
                    R.string.mqtt_connection_automatic_reconnect_title,
                    R.string.mqtt_connection_session_expiry_interval_title,
                    R.string.mqtt_connection_use_websocket_title,
                    R.string.mqtt_connection_websocket_server_path_title,
                ),
        ),
        SettingsSearchSection(
            R.string.settings_mqtt_topics_title,
            Screen.SettingsMqttTopics.route,
            descriptionRes = R.string.settings_mqtt_topics_description,
        ),
        SettingsSearchSection(
            R.string.settings_mqtt_topics_publish_event_title,
            Screen.SettingsMqttTopicsPublishEvent.route,
            descriptionRes = R.string.settings_mqtt_topics_publish_event_description,
            fields =
                listOf(
                    R.string.mqtt_publish_event_topic_title,
                    R.string.mqtt_publish_event_qos_title,
                    R.string.mqtt_publish_event_retain_title,
                ),
        ),
        SettingsSearchSection(
            R.string.settings_mqtt_topics_publish_response_title,
            Screen.SettingsMqttTopicsPublishResponse.route,
            descriptionRes =
                R.string.settings_mqtt_topics_publish_response_description,
            fields =
                listOf(
                    R.string.mqtt_publish_response_topic_title,
                    R.string.mqtt_publish_response_qos_title,
                    R.string.mqtt_publish_response_retain_title,
                ),
        ),
        SettingsSearchSection(
            R.string.settings_mqtt_topics_subscribe_command_title,
            Screen.SettingsMqttTopicsSubscribeCommand.route,
            descriptionRes =
                R.string.settings_mqtt_topics_subscribe_command_description,
            fields =
                listOf(
                    R.string.mqtt_subscribe_command_topic_title,
                    R.string.mqtt_subscribe_command_qos_title,
                    R.string.mqtt_subscribe_command_retain_handling_title,
                    R.string.mqtt_subscribe_command_retain_as_published_title,
                ),
        ),
        SettingsSearchSection(
            R.string.settings_mqtt_topics_subscribe_settings_title,
            Screen.SettingsMqttTopicsSubscribeSettings.route,
            descriptionRes =
                R.string.settings_mqtt_topics_subscribe_settings_description,
            fields =
                listOf(
                    R.string.mqtt_subscribe_settings_topic_title,
                    R.string.mqtt_subscribe_settings_qos_title,
                    R.string.mqtt_subscribe_settings_retain_handling_title,
                    R.string.mqtt_subscribe_settings_retain_as_published_title,
                ),
        ),
        SettingsSearchSection(
            R.string.settings_mqtt_topics_subscribe_request_title,
            Screen.SettingsMqttTopicsSubscribeRequest.route,
            descriptionRes =
                R.string.settings_mqtt_topics_subscribe_request_description,
            fields =
                listOf(
                    R.string.mqtt_subscribe_request_topic_title,
                    R.string.mqtt_subscribe_request_qos_title,
                    R.string.mqtt_subscribe_request_retain_handling_title,
                    R.string.mqtt_subscribe_request_retain_as_published_title,
                ),
        ),
        SettingsSearchSection(
            R.string.settings_mqtt_home_assistant_title,
            Screen.SettingsMqttHomeAssistant.route,
            descriptionRes = R.string.settings_mqtt_home_assistant_description,
            fields =
                listOf(
                    R.string.mqtt_home_assistant_enabled_title,
                    R.string.mqtt_home_assistant_device_name_title,
                    R.string.mqtt_home_assistant_discovery_prefix_title,
                    R.string.mqtt_home_assistant_status_topic_title,
                ),
        ),
        SettingsSearchSection(
            R.string.settings_mqtt_will_title,
            Screen.SettingsMqttWill.route,
            descriptionRes = R.string.settings_mqtt_will_description,
            fields =
                listOf(
                    R.string.mqtt_will_topic_title,
                    R.string.mqtt_will_qos_title,
                    R.string.mqtt_will_payload_title,
                    R.string.mqtt_will_retain_title,
                    R.string.mqtt_will_message_expiry_interval_title,
                    R.string.mqtt_will_delay_interval_title,
                ),
        ),
        SettingsSearchSection(
            R.string.settings_mqtt_restrictions_title,
            Screen.SettingsMqttRestrictions.route,
            descriptionRes = R.string.settings_mqtt_restrictions_description,
            fields =
                listOf(
                    R.string.mqtt_restrictions_receive_maximum_title,
                    R.string.mqtt_restrictions_send_maximum_title,
                    R.string.mqtt_restrictions_maximum_packet_size_title,
                    R.string.mqtt_restrictions_send_maximum_packet_size_title,
                    R.string.mqtt_restrictions_topic_alias_maximum_title,
                    R.string.mqtt_restrictions_send_topic_alias_maximum_title,
                    R.string.mqtt_restrictions_request_problem_information_title,
                    R.string.mqtt_restrictions_request_response_information_title,
                ),
        ),
        SettingsSearchSection(
            R.string.settings_unifiedpush_title,
            Screen.SettingsUnifiedPush.route,
            descriptionRes = R.string.settings_unifiedpush_description,
            fields =
                listOf(
                    R.string.unifiedpush_enabled_title,
                    R.string.unifiedpush_distributor_title,
                    R.string.unifiedpush_instance_title,
                    R.string.unifiedpush_message_for_distributor_title,
                    R.string.unifiedpush_vapid_public_key_title,
                    R.string.unifiedpush_process_unencrypted_messages_title,
                    R.string.unifiedpush_store_endpoint_credentials_title,
                ),
        ),
        SettingsSearchSection(
            R.string.settings_about_title,
            Screen.SettingsAbout.route,
            descriptionRes = R.string.settings_about_description,
            fields =
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
                ),
        ),
        SettingsSearchSection(
            R.string.settings_more_actions_title,
            Screen.SettingsMoreActions.route,
            fields =
                listOf(
                    R.string.settings_more_action_app_info,
                    R.string.settings_more_action_device_settings,
                    R.string.settings_more_action_default_launcher,
                    R.string.settings_more_action_default_apps,
                    R.string.settings_more_action_wifi_settings,
                    R.string.settings_more_action_data_usage,
                    R.string.settings_more_action_create_shortcut,
                    R.string.settings_more_action_app_launcher,
                    R.string.settings_more_action_local_files,
                    R.string.settings_more_action_content_blocking,
                    R.string.settings_more_action_site_permissions,
                    R.string.settings_more_action_device_owner,
                    R.string.settings_more_action_clear_cookies,
                    R.string.settings_more_action_clear_cache,
                    R.string.settings_more_action_clear_form_data,
                    R.string.settings_more_action_clear_history,
                    R.string.settings_more_action_clear_ssl_preferences,
                    R.string.settings_more_action_clear_web_storage,
                ),
        ),
    )

internal fun searchSettings(
    context: Context,
    query: String,
): List<SettingsSearchResult> {
    val words = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) {
        return emptyList()
    }

    return settingsSearchSections.flatMap { section ->
        val heading = context.getString(section.titleRes)
        val description = section.descriptionRes?.let(context::getString).orEmpty()
        val sectionResult =
            if (
                words.all {
                    heading.contains(it, ignoreCase = true) ||
                        description.contains(it, ignoreCase = true)
                }
            ) {
                listOf(
                    SettingsSearchResult(
                        titleRes = section.titleRes,
                        route = section.route,
                        sectionTitleRes = null,
                        descriptionRes = section.descriptionRes,
                    )
                )
            } else {
                emptyList()
            }
        val fieldResults =
            section.fields.mapNotNull { titleRes ->
                val label = context.getString(titleRes)
                val matches =
                    words.all { word ->
                        label.contains(word, ignoreCase = true) ||
                            heading.contains(word, ignoreCase = true)
                    } && words.any { label.contains(it, ignoreCase = true) }
                if (matches) {
                    SettingsSearchResult(
                        titleRes = titleRes,
                        route = section.route,
                        sectionTitleRes = section.titleRes,
                        targetRes = titleRes,
                    )
                } else {
                    null
                }
            }
        sectionResult + fieldResults
    }
}
