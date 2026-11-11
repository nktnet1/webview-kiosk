package uk.nktnet.webviewkiosk.ui.screens

import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettingsKeys

/**
 * Displayed field labels mapped to their persisted user setting keys. Use the same
 * constants as the corresponding fielditems to keep the searchable key and the
 * detail shown in results consistent with storage. About information and actions are
 * intentionally excluded: they have no editable user setting key.
 */
internal val settingsSearchKeysByLabel: Map<Int, String> =
    mapOf(
        R.string.web_content_home_url_title to UserSettingsKeys.WebContent.HOME_URL,
        R.string.web_content_website_blacklist_title to
            UserSettingsKeys.WebContent.WEBSITE_BLACKLIST,
        R.string.web_content_website_whitelist_title to
            UserSettingsKeys.WebContent.WEBSITE_WHITELIST,
        R.string.web_content_website_bookmarks_title to
            UserSettingsKeys.WebContent.WEBSITE_BOOKMARKS,
        R.string.web_content_support_pdf_rendering_title to
            UserSettingsKeys.WebContent.SUPPORT_PDF_RENDERING,
        R.string.web_content_allow_local_files_title to
            UserSettingsKeys.WebContent.ALLOW_LOCAL_FILES,
        R.string.web_content_enable_content_blocking_title to
            UserSettingsKeys.WebContent.ENABLE_CONTENT_BLOCKING,
        R.string.web_browsing_allow_refresh_title to
            UserSettingsKeys.WebBrowsing.ALLOW_REFRESH,
        R.string.web_browsing_allow_pull_to_refresh_title to
            UserSettingsKeys.WebBrowsing.ALLOW_PULL_TO_REFRESH,
        R.string.web_browsing_allow_backwards_navigation_title to
            UserSettingsKeys.WebBrowsing.ALLOW_BACKWARDS_NAVIGATION,
        R.string.web_browsing_allow_go_home_title to
            UserSettingsKeys.WebBrowsing.ALLOW_GO_HOME,
        R.string.web_browsing_clear_history_on_home_title to
            UserSettingsKeys.WebBrowsing.CLEAR_HISTORY_ON_HOME,
        R.string.web_browsing_replace_history_url_on_redirect_title to
            UserSettingsKeys.WebBrowsing.REPLACE_HISTORY_URL_ON_REDIRECT,
        R.string.web_browsing_allow_history_access_title to
            UserSettingsKeys.WebBrowsing.ALLOW_HISTORY_ACCESS,
        R.string.web_browsing_allow_bookmark_access_title to
            UserSettingsKeys.WebBrowsing.ALLOW_BOOKMARK_ACCESS,
        R.string.web_browsing_allow_other_url_schemes_title to
            UserSettingsKeys.WebBrowsing.ALLOW_OTHER_URL_SCHEMES,
        R.string.web_browsing_allow_default_long_press_title to
            UserSettingsKeys.WebBrowsing.ALLOW_DEFAULT_LONG_PRESS,
        R.string.web_browsing_allow_link_long_press_context_menu_title to
            UserSettingsKeys.WebBrowsing.ALLOW_LINK_LONG_PRESS_CONTEXT_MENU,
        R.string.web_browsing_override_url_loading_block_action_title to
            UserSettingsKeys.WebBrowsing.OVERRIDE_URL_LOADING_BLOCK_ACTION,
        R.string.web_browsing_address_bar_actions_title to
            UserSettingsKeys.WebBrowsing.ADDRESS_BAR_ACTIONS,
        R.string.web_browsing_kiosk_control_panel_region_title to
            UserSettingsKeys.WebBrowsing.KIOSK_CONTROL_PANEL_REGION,
        R.string.web_browsing_kiosk_control_panel_actions_title to
            UserSettingsKeys.WebBrowsing.KIOSK_CONTROL_PANEL_ACTIONS,
        R.string.web_browsing_search_provider_url_title to
            UserSettingsKeys.WebBrowsing.SEARCH_PROVIDER_URL,
        R.string.web_browsing_search_suggestion_engine_title to
            UserSettingsKeys.WebBrowsing.SEARCH_SUGGESTION_ENGINE,
        R.string.web_engine_enable_javascript_title to
            UserSettingsKeys.WebEngine.ENABLE_JAVASCRIPT,
        R.string.web_engine_enable_dom_storage_title to
            UserSettingsKeys.WebEngine.ENABLE_DOM_STORAGE,
        R.string.web_engine_accept_cookies_title to
            UserSettingsKeys.WebEngine.ACCEPT_COOKIES,
        R.string.web_engine_accept_third_party_cookies_title to
            UserSettingsKeys.WebEngine.ACCEPT_THIRD_PARTY_COOKIES,
        R.string.web_engine_cache_mode_title to
            UserSettingsKeys.WebEngine.CACHE_MODE,
        R.string.web_engine_user_agent_title to
            UserSettingsKeys.WebEngine.USER_AGENT,
        R.string.web_engine_layout_algorithm_title to
            UserSettingsKeys.WebEngine.LAYOUT_ALGORITHM,
        R.string.web_engine_use_wide_viewport_title to
            UserSettingsKeys.WebEngine.USE_WIDE_VIEWPORT,
        R.string.web_engine_load_with_overview_mode_title to
            UserSettingsKeys.WebEngine.LOAD_WITH_OVERVIEW_MODE,
        R.string.web_engine_support_zoom_title to
            UserSettingsKeys.WebEngine.SUPPORT_ZOOM,
        R.string.web_engine_built_in_zoom_controls_title to
            UserSettingsKeys.WebEngine.BUILT_IN_ZOOM_CONTROLS,
        R.string.web_engine_display_zoom_controls_title to
            UserSettingsKeys.WebEngine.DISPLAY_ZOOM_CONTROLS,
        R.string.web_engine_initial_scale_title to
            UserSettingsKeys.WebEngine.INITIAL_SCALE,
        R.string.web_engine_allow_file_access_from_file_urls_title to
            UserSettingsKeys.WebEngine.ALLOW_FILE_ACCESS_FROM_FILE_URLS,
        R.string.web_engine_allow_universal_access_from_file_urls_title to
            UserSettingsKeys.WebEngine.ALLOW_UNIVERSAL_ACCESS_FROM_FILE_URLS,
        R.string.web_engine_allow_file_picker_title to
            UserSettingsKeys.WebEngine.ALLOW_FILE_PICKER,
        R.string.web_engine_allow_file_download_title to
            UserSettingsKeys.WebEngine.ALLOW_FILE_DOWNLOAD,
        R.string.web_engine_media_playback_requires_user_gesture_title to
            UserSettingsKeys.WebEngine.MEDIA_PLAYBACK_REQUIRES_USER_GESTURE,
        R.string.web_engine_mutual_tls_title to
            UserSettingsKeys.WebEngine.MUTUAL_TLS,
        R.string.web_engine_ssl_error_mode_title to
            UserSettingsKeys.WebEngine.SSL_ERROR_MODE,
        R.string.web_engine_mixed_content_mode_title to
            UserSettingsKeys.WebEngine.MIXED_CONTENT_MODE,
        R.string.web_engine_over_scroll_mode_title to
            UserSettingsKeys.WebEngine.OVER_SCROLL_MODE,
        R.string.web_engine_request_focus_on_page_start_title to
            UserSettingsKeys.WebEngine.REQUEST_FOCUS_ON_PAGE_START,
        R.string.web_lifecycle_lock_on_launch_title to
            UserSettingsKeys.WebLifecycle.LOCK_ON_LAUNCH,
        R.string.web_lifecycle_reset_on_launch_title to
            UserSettingsKeys.WebLifecycle.RESET_ON_LAUNCH,
        R.string.web_lifecycle_reset_on_inactivity_seconds_title to
            UserSettingsKeys.WebLifecycle.RESET_ON_INACTIVITY_SECONDS,
        R.string.web_lifecycle_reset_actions_title to
            UserSettingsKeys.WebLifecycle.RESET_ACTIONS,
        R.string.web_lifecycle_dim_screen_on_inactivity_seconds_title to
            UserSettingsKeys.WebLifecycle.DIM_SCREEN_ON_INACTIVITY_SECONDS,
        R.string.web_lifecycle_refresh_on_network_available_title to
            UserSettingsKeys.WebLifecycle.REFRESH_ON_NETWORK_AVAILABLE,
        R.string.web_lifecycle_refresh_on_loading_error_interval_seconds_title to
            UserSettingsKeys.WebLifecycle.REFRESH_ON_LOADING_ERROR_INTERVAL_SECONDS,
        R.string.web_lifecycle_refresh_on_screen_wake_title to
            UserSettingsKeys.WebLifecycle.REFRESH_ON_SCREEN_WAKE,
        R.string.appearance_theme_title to UserSettingsKeys.Appearance.THEME,
        R.string.appearance_floating_toolbar_mode_title to
            UserSettingsKeys.Appearance.FLOATING_TOOLBAR_MODE,
        R.string.appearance_webview_inset_title to
            UserSettingsKeys.Appearance.WEBVIEW_INSET,
        R.string.appearance_immersive_mode_title to
            UserSettingsKeys.Appearance.IMMERSIVE_MODE,
        R.string.appearance_blocked_message_title to
            UserSettingsKeys.Appearance.BLOCKED_MESSAGE,
        R.string.appearance_custom_block_page_html_title to
            UserSettingsKeys.Appearance.CUSTOM_BLOCK_PAGE_HTML,
        R.string.appearance_address_bar_mode_title to
            UserSettingsKeys.Appearance.ADDRESS_BAR_MODE,
        R.string.appearance_address_bar_size_title to
            UserSettingsKeys.Appearance.ADDRESS_BAR_SIZE,
        R.string.appearance_address_bar_position_title to
            UserSettingsKeys.Appearance.ADDRESS_BAR_POSITION,
        R.string.appearance_show_loading_progress_bar_title to
            UserSettingsKeys.Appearance.SHOW_LOADING_PROGRESS_BAR,
        R.string.device_keep_screen_on_title to
            UserSettingsKeys.Device.KEEP_SCREEN_ON,
        R.string.device_rotation_title to UserSettingsKeys.Device.DEVICE_ROTATION,
        R.string.device_brightness_title to UserSettingsKeys.Device.BRIGHTNESS,
        R.string.device_allow_camera_title to UserSettingsKeys.Device.ALLOW_CAMERA,
        R.string.device_allow_microphone_title to
            UserSettingsKeys.Device.ALLOW_MICROPHONE,
        R.string.device_allow_location_title to
            UserSettingsKeys.Device.ALLOW_LOCATION,
        R.string.device_allow_nfc_title to UserSettingsKeys.Device.ALLOW_NFC,
        R.string.device_allow_notifications_title to
            UserSettingsKeys.Device.ALLOW_NOTIFICATIONS,
        R.string.device_back_button_hold_action_title to
            UserSettingsKeys.Device.BACK_BUTTON_HOLD_ACTION,
        R.string.device_custom_unlock_shortcut_title to
            UserSettingsKeys.Device.CUSTOM_UNLOCK_SHORTCUT,
        R.string.device_custom_auth_password_title to
            UserSettingsKeys.Device.CUSTOM_AUTH_PASSWORD,
        R.string.device_unlock_auth_requirement_title to
            UserSettingsKeys.Device.UNLOCK_AUTH_REQUIREMENT,
        R.string.device_block_screen_capture_title to
            UserSettingsKeys.Device.BLOCK_SCREEN_CAPTURE,
        R.string.device_block_volume_keys_title to
            UserSettingsKeys.Device.BLOCK_VOLUME_KEYS,
        R.string.device_owner_lock_task_feature_home_title to
            UserSettingsKeys.Device.Owner.LockTaskFeature.HOME,
        R.string.device_owner_lock_task_feature_overview_title to
            UserSettingsKeys.Device.Owner.LockTaskFeature.OVERVIEW,
        R.string.device_owner_lock_task_feature_global_actions_title to
            UserSettingsKeys.Device.Owner.LockTaskFeature.GLOBAL_ACTIONS,
        R.string.device_owner_lock_task_feature_notifications_title to
            UserSettingsKeys.Device.Owner.LockTaskFeature.NOTIFICATIONS,
        R.string.device_owner_lock_task_feature_system_info_title to
            UserSettingsKeys.Device.Owner.LockTaskFeature.SYSTEM_INFO,
        R.string.device_owner_lock_task_feature_keyguard_title to
            UserSettingsKeys.Device.Owner.LockTaskFeature.KEYGUARD,
        R.string.device_owner_lock_task_feature_block_activity_start_in_task_title to
            UserSettingsKeys.Device.Owner.LockTaskFeature
                .BLOCK_ACTIVITY_START_IN_TASK,
        R.string.ui_request_permission_on_launch to
            UserSettingsKeys.Device.Owner.Dhizuku.REQUEST_PERMISSION_ON_LAUNCH,
        R.string.js_scripts_apply_app_theme_title to
            UserSettingsKeys.JsScripts.APPLY_APP_THEME,
        R.string.js_scripts_apply_desktop_viewport_width_title to
            UserSettingsKeys.JsScripts.APPLY_DESKTOP_VIEWPORT_WIDTH,
        R.string.js_scripts_enable_battery_api_title to
            UserSettingsKeys.JsScripts.ENABLE_BATTERY_API,
        R.string.js_scripts_enable_brightness_api_title to
            UserSettingsKeys.JsScripts.ENABLE_BRIGHTNESS_API,
        R.string.js_scripts_enable_vibration_api_title to
            UserSettingsKeys.JsScripts.ENABLE_VIBRATION_API,
        R.string.js_scripts_enable_dark_reader_title to
            UserSettingsKeys.JsScripts.ENABLE_DARK_READER,
        R.string.js_scripts_enable_eruda_console_title to
            UserSettingsKeys.JsScripts.ENABLE_ERUDA_CONSOLE,
        R.string.js_scripts_custom_script_on_page_start_title to
            UserSettingsKeys.JsScripts.CUSTOM_SCRIPT_ON_PAGE_START,
        R.string.js_scripts_custom_script_on_page_finish_title to
            UserSettingsKeys.JsScripts.CUSTOM_SCRIPT_ON_PAGE_FINISH,
        R.string.mqtt_enabled_title to UserSettingsKeys.Mqtt.ENABLED,
        R.string.mqtt_use_foreground_service_title to
            UserSettingsKeys.Mqtt.USE_FOREGROUND_SERVICE,
        R.string.mqtt_connection_server_host_title to
            UserSettingsKeys.Mqtt.Connection.SERVER_HOST,
        R.string.mqtt_connection_server_port_title to
            UserSettingsKeys.Mqtt.Connection.SERVER_PORT,
        R.string.mqtt_connection_use_tls_title to
            UserSettingsKeys.Mqtt.Connection.USE_TLS,
        R.string.mqtt_connection_username_title to
            UserSettingsKeys.Mqtt.Connection.USERNAME,
        R.string.mqtt_connection_password_title to
            UserSettingsKeys.Mqtt.Connection.PASSWORD,
        R.string.mqtt_connection_client_id_title to
            UserSettingsKeys.Mqtt.Connection.CLIENT_ID,
        R.string.mqtt_connection_clean_start_title to
            UserSettingsKeys.Mqtt.Connection.CLEAN_START,
        R.string.mqtt_connection_keep_alive_title to
            UserSettingsKeys.Mqtt.Connection.KEEP_ALIVE,
        R.string.mqtt_connection_connect_timeout_title to
            UserSettingsKeys.Mqtt.Connection.CONNECT_TIMEOUT,
        R.string.mqtt_connection_socket_connect_timeout_title to
            UserSettingsKeys.Mqtt.Connection.SOCKET_CONNECT_TIMEOUT,
        R.string.mqtt_connection_automatic_reconnect_title to
            UserSettingsKeys.Mqtt.Connection.AUTOMATIC_RECONNECT,
        R.string.mqtt_connection_session_expiry_interval_title to
            UserSettingsKeys.Mqtt.Connection.SESSION_EXPIRY_INTERVAL,
        R.string.mqtt_connection_use_websocket_title to
            UserSettingsKeys.Mqtt.Connection.USE_WEBSOCKET,
        R.string.mqtt_connection_websocket_server_path_title to
            UserSettingsKeys.Mqtt.Connection.WEBSOCKET_SERVER_PATH,
        R.string.mqtt_publish_event_topic_title to
            UserSettingsKeys.Mqtt.Topics.Publish.Event.TOPIC,
        R.string.mqtt_publish_event_qos_title to
            UserSettingsKeys.Mqtt.Topics.Publish.Event.QOS,
        R.string.mqtt_publish_event_retain_title to
            UserSettingsKeys.Mqtt.Topics.Publish.Event.RETAIN,
        R.string.mqtt_publish_response_topic_title to
            UserSettingsKeys.Mqtt.Topics.Publish.Response.TOPIC,
        R.string.mqtt_publish_response_qos_title to
            UserSettingsKeys.Mqtt.Topics.Publish.Response.QOS,
        R.string.mqtt_publish_response_retain_title to
            UserSettingsKeys.Mqtt.Topics.Publish.Response.RETAIN,
        R.string.mqtt_subscribe_command_topic_title to
            UserSettingsKeys.Mqtt.Topics.Subscribe.Command.TOPIC,
        R.string.mqtt_subscribe_command_qos_title to
            UserSettingsKeys.Mqtt.Topics.Subscribe.Command.QOS,
        R.string.mqtt_subscribe_command_retain_handling_title to
            UserSettingsKeys.Mqtt.Topics.Subscribe.Command.RETAIN_HANDLING,
        R.string.mqtt_subscribe_command_retain_as_published_title to
            UserSettingsKeys.Mqtt.Topics.Subscribe.Command.RETAIN_AS_PUBLISHED,
        R.string.mqtt_subscribe_settings_topic_title to
            UserSettingsKeys.Mqtt.Topics.Subscribe.Settings.TOPIC,
        R.string.mqtt_subscribe_settings_qos_title to
            UserSettingsKeys.Mqtt.Topics.Subscribe.Settings.QOS,
        R.string.mqtt_subscribe_settings_retain_handling_title to
            UserSettingsKeys.Mqtt.Topics.Subscribe.Settings.RETAIN_HANDLING,
        R.string.mqtt_subscribe_settings_retain_as_published_title to
            UserSettingsKeys.Mqtt.Topics.Subscribe.Settings.RETAIN_AS_PUBLISHED,
        R.string.mqtt_subscribe_request_topic_title to
            UserSettingsKeys.Mqtt.Topics.Subscribe.Request.TOPIC,
        R.string.mqtt_subscribe_request_qos_title to
            UserSettingsKeys.Mqtt.Topics.Subscribe.Request.QOS,
        R.string.mqtt_subscribe_request_retain_handling_title to
            UserSettingsKeys.Mqtt.Topics.Subscribe.Request.RETAIN_HANDLING,
        R.string.mqtt_subscribe_request_retain_as_published_title to
            UserSettingsKeys.Mqtt.Topics.Subscribe.Request.RETAIN_AS_PUBLISHED,
        R.string.mqtt_home_assistant_enabled_title to
            UserSettingsKeys.Mqtt.HomeAssistant.ENABLED,
        R.string.mqtt_home_assistant_device_name_title to
            UserSettingsKeys.Mqtt.HomeAssistant.DEVICE_NAME,
        R.string.mqtt_home_assistant_discovery_prefix_title to
            UserSettingsKeys.Mqtt.HomeAssistant.DISCOVERY_PREFIX,
        R.string.mqtt_home_assistant_status_topic_title to
            UserSettingsKeys.Mqtt.HomeAssistant.STATUS_TOPIC,
        R.string.mqtt_will_topic_title to UserSettingsKeys.Mqtt.Will.TOPIC,
        R.string.mqtt_will_qos_title to UserSettingsKeys.Mqtt.Will.QOS,
        R.string.mqtt_will_payload_title to UserSettingsKeys.Mqtt.Will.PAYLOAD,
        R.string.mqtt_will_retain_title to UserSettingsKeys.Mqtt.Will.RETAIN,
        R.string.mqtt_will_message_expiry_interval_title to
            UserSettingsKeys.Mqtt.Will.MESSAGE_EXPIRY_INTERVAL,
        R.string.mqtt_will_delay_interval_title to
            UserSettingsKeys.Mqtt.Will.DELAY_INTERVAL,
        R.string.mqtt_restrictions_receive_maximum_title to
            UserSettingsKeys.Mqtt.Restrictions.RECEIVE_MAXIMUM,
        R.string.mqtt_restrictions_send_maximum_title to
            UserSettingsKeys.Mqtt.Restrictions.SEND_MAXIMUM,
        R.string.mqtt_restrictions_maximum_packet_size_title to
            UserSettingsKeys.Mqtt.Restrictions.MAXIMUM_PACKET_SIZE,
        R.string.mqtt_restrictions_send_maximum_packet_size_title to
            UserSettingsKeys.Mqtt.Restrictions.SEND_MAXIMUM_PACKET_SIZE,
        R.string.mqtt_restrictions_topic_alias_maximum_title to
            UserSettingsKeys.Mqtt.Restrictions.TOPIC_ALIAS_MAXIMUM,
        R.string.mqtt_restrictions_send_topic_alias_maximum_title to
            UserSettingsKeys.Mqtt.Restrictions.SEND_TOPIC_ALIAS_MAXIMUM,
        R.string.mqtt_restrictions_request_problem_information_title to
            UserSettingsKeys.Mqtt.Restrictions.REQUEST_PROBLEM_INFORMATION,
        R.string.mqtt_restrictions_request_response_information_title to
            UserSettingsKeys.Mqtt.Restrictions.REQUEST_RESPONSE_INFORMATION,
        R.string.unifiedpush_enabled_title to UserSettingsKeys.UnifiedPush.ENABLED,
        R.string.unifiedpush_distributor_title to
            UserSettingsKeys.UnifiedPush.DISTRIBUTOR,
        R.string.unifiedpush_instance_title to UserSettingsKeys.UnifiedPush.INSTANCE,
        R.string.unifiedpush_message_for_distributor_title to
            UserSettingsKeys.UnifiedPush.MESSAGE_FOR_DISTRIBUTOR,
        R.string.unifiedpush_vapid_public_key_title to
            UserSettingsKeys.UnifiedPush.VAPID_PUBLIC_KEY,
        R.string.unifiedpush_process_unencrypted_messages_title to
            UserSettingsKeys.UnifiedPush.PROCESS_UNENCRYPTED_MESSAGES,
        R.string.unifiedpush_store_endpoint_credentials_title to
            UserSettingsKeys.UnifiedPush.STORE_ENDPOINT_CREDENTIALS,
    )
