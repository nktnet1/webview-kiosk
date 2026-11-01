package uk.nktnet.webviewkiosk.ui.resources

import androidx.annotation.StringRes
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.adblock.FilterSkipReason
import uk.nktnet.webviewkiosk.adblock.RuleLoadStage
import uk.nktnet.webviewkiosk.config.data.AppType
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

/**
 * Display-only translations for option values.
 *
 * Do not use these labels for config parsing, JSON serialization, or persistence.
 * Exhaustive when expressions make newly added enum entries a compile-time error
 * until the corresponding resource is provided.
 */
@get:StringRes
val ThemeOption.displayLabelRes: Int
    get() =
        when (this) {
            ThemeOption.SYSTEM -> R.string.option_display_theme_option_system
            ThemeOption.DARK -> R.string.option_display_theme_option_dark
            ThemeOption.LIGHT -> R.string.option_display_theme_option_light
        }

@get:StringRes
val AddressBarModeOption.displayLabelRes: Int
    get() =
        when (this) {
            AddressBarModeOption.HIDDEN ->
                R.string.option_display_address_bar_mode_option_hidden
            AddressBarModeOption.HIDDEN_WHEN_LOCKED ->
                R.string.option_display_address_bar_mode_option_hidden_when_locked
            AddressBarModeOption.SHOWN ->
                R.string.option_display_address_bar_mode_option_shown
        }

@get:StringRes
val AddressBarSizeOption.displayLabelRes: Int
    get() =
        when (this) {
            AddressBarSizeOption.EXTRA_SMALL ->
                R.string.option_display_address_bar_size_option_extra_small
            AddressBarSizeOption.SMALL ->
                R.string.option_display_address_bar_size_option_small
            AddressBarSizeOption.MEDIUM ->
                R.string.option_display_address_bar_size_option_medium
            AddressBarSizeOption.LARGE ->
                R.string.option_display_address_bar_size_option_large
            AddressBarSizeOption.EXTRA_LARGE ->
                R.string.option_display_address_bar_size_option_extra_large
        }

@get:StringRes
val AddressBarPositionOption.displayLabelRes: Int
    get() =
        when (this) {
            AddressBarPositionOption.TOP ->
                R.string.option_display_address_bar_position_option_top
            AddressBarPositionOption.BOTTOM ->
                R.string.option_display_address_bar_position_option_bottom
        }

@get:StringRes
val FloatingToolbarModeOption.displayLabelRes: Int
    get() =
        when (this) {
            FloatingToolbarModeOption.HIDDEN ->
                R.string.option_display_floating_toolbar_mode_option_hidden
            FloatingToolbarModeOption.HIDDEN_WHEN_LOCKED ->
                R.string
                    .option_display_floating_toolbar_mode_option_hidden_when_locked
            FloatingToolbarModeOption.SHOWN ->
                R.string.option_display_floating_toolbar_mode_option_shown
        }

@get:StringRes
val ImmersiveModeOption.displayLabelRes: Int
    get() =
        when (this) {
            ImmersiveModeOption.ALWAYS_ON ->
                R.string.option_display_immersive_mode_option_always_on
            ImmersiveModeOption.ALWAYS_OFF ->
                R.string.option_display_immersive_mode_option_always_off
            ImmersiveModeOption.ONLY_WHEN_LOCKED ->
                R.string.option_display_immersive_mode_option_only_when_locked
        }

@get:StringRes
val WebViewInsetOption.displayLabelRes: Int
    get() =
        when (this) {
            WebViewInsetOption.NONE ->
                R.string.option_display_webview_inset_option_none
            WebViewInsetOption.STATUS_BARS ->
                R.string.option_display_webview_inset_option_status_bars
            WebViewInsetOption.NAVIGATION_BARS ->
                R.string.option_display_webview_inset_option_navigation_bars
            WebViewInsetOption.SYSTEM_BARS ->
                R.string.option_display_webview_inset_option_system_bars
            WebViewInsetOption.DISPLAY_CUTOUT ->
                R.string.option_display_webview_inset_option_display_cutout
            WebViewInsetOption.SAFE_DRAWING ->
                R.string.option_display_webview_inset_option_safe_drawing
            WebViewInsetOption.SAFE_GESTURES ->
                R.string.option_display_webview_inset_option_safe_gestures
            WebViewInsetOption.SAFE_CONTENT ->
                R.string.option_display_webview_inset_option_safe_content
        }

@get:StringRes
val DeviceRotationOption.displayLabelRes: Int
    get() =
        when (this) {
            DeviceRotationOption.AUTO ->
                R.string.option_display_device_rotation_option_auto
            DeviceRotationOption.ROTATION_0 ->
                R.string.option_display_device_rotation_option_rotation_0
            DeviceRotationOption.ROTATION_90 ->
                R.string.option_display_device_rotation_option_rotation_90
            DeviceRotationOption.ROTATION_180 ->
                R.string.option_display_device_rotation_option_rotation_180
            DeviceRotationOption.ROTATION_270 ->
                R.string.option_display_device_rotation_option_rotation_270
        }

@get:StringRes
val BackButtonHoldActionOption.displayLabelRes: Int
    get() =
        when (this) {
            BackButtonHoldActionOption.OPEN_KIOSK_CONTROL_PANEL ->
                R.string
                    .option_display_back_button_hold_action_option_open_kiosk_control_panel
            BackButtonHoldActionOption.GO_HOME ->
                R.string.option_display_back_button_hold_action_option_go_home
            BackButtonHoldActionOption.DISABLED ->
                R.string.option_display_back_button_hold_action_option_disabled
        }

@get:StringRes
val UnlockAuthRequirementOption.displayLabelRes: Int
    get() =
        when (this) {
            UnlockAuthRequirementOption.DEFAULT ->
                R.string.option_display_unlock_auth_requirement_option_default
            UnlockAuthRequirementOption.OFF ->
                R.string.option_display_unlock_auth_requirement_option_off
            UnlockAuthRequirementOption.REQUIRE ->
                R.string.option_display_unlock_auth_requirement_option_require
        }

@get:StringRes
val KioskControlPanelRegionOption.displayLabelRes: Int
    get() =
        when (this) {
            KioskControlPanelRegionOption.TOP_LEFT ->
                R.string.option_display_kiosk_control_panel_region_option_top_left
            KioskControlPanelRegionOption.TOP_RIGHT ->
                R.string.option_display_kiosk_control_panel_region_option_top_right
            KioskControlPanelRegionOption.BOTTOM_LEFT ->
                R.string.option_display_kiosk_control_panel_region_option_bottom_left
            KioskControlPanelRegionOption.BOTTOM_RIGHT ->
                R.string
                    .option_display_kiosk_control_panel_region_option_bottom_right
            KioskControlPanelRegionOption.TOP ->
                R.string.option_display_kiosk_control_panel_region_option_top
            KioskControlPanelRegionOption.BOTTOM ->
                R.string.option_display_kiosk_control_panel_region_option_bottom
            KioskControlPanelRegionOption.FULL ->
                R.string.option_display_kiosk_control_panel_region_option_full
            KioskControlPanelRegionOption.DISABLED ->
                R.string.option_display_kiosk_control_panel_region_option_disabled
        }

@get:StringRes
val WebviewControlActionOption.displayLabelRes: Int
    get() =
        when (this) {
            WebviewControlActionOption.NAVIGATION ->
                R.string.option_display_webview_control_action_option_navigation
            WebviewControlActionOption.BACK ->
                R.string.option_display_webview_control_action_option_back
            WebviewControlActionOption.FORWARD ->
                R.string.option_display_webview_control_action_option_forward
            WebviewControlActionOption.REFRESH ->
                R.string.option_display_webview_control_action_option_refresh
            WebviewControlActionOption.HOME ->
                R.string.option_display_webview_control_action_option_home
            WebviewControlActionOption.HISTORY ->
                R.string.option_display_webview_control_action_option_history
            WebviewControlActionOption.BOOKMARK ->
                R.string.option_display_webview_control_action_option_bookmark
            WebviewControlActionOption.FILES ->
                R.string.option_display_webview_control_action_option_files
            WebviewControlActionOption.FIND ->
                R.string.option_display_webview_control_action_option_find
            WebviewControlActionOption.SCROLL_TOP ->
                R.string.option_display_webview_control_action_option_scroll_top
            WebviewControlActionOption.SCROLL_BOT ->
                R.string.option_display_webview_control_action_option_scroll_bot
            WebviewControlActionOption.APPS ->
                R.string.option_display_webview_control_action_option_apps
            WebviewControlActionOption.SETTINGS ->
                R.string.option_display_webview_control_action_option_settings
            WebviewControlActionOption.LOCK ->
                R.string.option_display_webview_control_action_option_lock
            WebviewControlActionOption.UNLOCK ->
                R.string.option_display_webview_control_action_option_unlock
        }

@get:StringRes
val OverrideUrlLoadingBlockActionOption.displayLabelRes: Int
    get() =
        when (this) {
            OverrideUrlLoadingBlockActionOption.SHOW_BLOCK_PAGE ->
                R.string
                    .option_display_override_url_loading_block_action_option_show_block_page
            OverrideUrlLoadingBlockActionOption.PREVENT_NAVIGATION ->
                R.string
                    .option_display_override_url_loading_block_action_option_prevent_navigation
            OverrideUrlLoadingBlockActionOption.SHOW_TOAST ->
                R.string
                    .option_display_override_url_loading_block_action_option_show_toast
        }

@get:StringRes
val SearchSuggestionEngineOption.displayLabelRes: Int
    get() =
        when (this) {
            SearchSuggestionEngineOption.NONE ->
                R.string.option_display_search_suggestion_engine_option_none
            SearchSuggestionEngineOption.GOOGLE ->
                R.string.option_display_search_suggestion_engine_option_google
            SearchSuggestionEngineOption.DUCKDUCKGO ->
                R.string.option_display_search_suggestion_engine_option_duckduckgo
            SearchSuggestionEngineOption.YAHOO ->
                R.string.option_display_search_suggestion_engine_option_yahoo
        }

@get:StringRes
val CacheModeOption.displayLabelRes: Int
    get() =
        when (this) {
            CacheModeOption.DEFAULT ->
                R.string.option_display_cache_mode_option_default
            CacheModeOption.CACHE_ELSE_NETWORK ->
                R.string.option_display_cache_mode_option_cache_else_network
            CacheModeOption.NO_CACHE ->
                R.string.option_display_cache_mode_option_no_cache
            CacheModeOption.CACHE_ONLY ->
                R.string.option_display_cache_mode_option_cache_only
        }

@get:StringRes
val LayoutAlgorithmOption.displayLabelRes: Int
    get() =
        when (this) {
            LayoutAlgorithmOption.NORMAL ->
                R.string.option_display_layout_algorithm_option_normal
            LayoutAlgorithmOption.SINGLE_COLUMN ->
                R.string.option_display_layout_algorithm_option_single_column
            LayoutAlgorithmOption.NARROW_COLUMNS ->
                R.string.option_display_layout_algorithm_option_narrow_columns
            LayoutAlgorithmOption.TEXT_AUTOSIZING ->
                R.string.option_display_layout_algorithm_option_text_autosizing
        }

@get:StringRes
val SslErrorModeOption.displayLabelRes: Int
    get() =
        when (this) {
            SslErrorModeOption.BLOCK ->
                R.string.option_display_ssl_error_mode_option_block
            SslErrorModeOption.PROMPT ->
                R.string.option_display_ssl_error_mode_option_prompt
            SslErrorModeOption.PROCEED ->
                R.string.option_display_ssl_error_mode_option_proceed
        }

@get:StringRes
val MixedContentModeOption.displayLabelRes: Int
    get() =
        when (this) {
            MixedContentModeOption.NEVER_ALLOW ->
                R.string.option_display_mixed_content_mode_option_never_allow
            MixedContentModeOption.COMPATIBILITY_MODE ->
                R.string.option_display_mixed_content_mode_option_compatibility_mode
            MixedContentModeOption.ALWAYS_ALLOW ->
                R.string.option_display_mixed_content_mode_option_always_allow
        }

@get:StringRes
val OverScrollModeOption.displayLabelRes: Int
    get() =
        when (this) {
            OverScrollModeOption.NEVER ->
                R.string.option_display_over_scroll_mode_option_never
            OverScrollModeOption.ALWAYS ->
                R.string.option_display_over_scroll_mode_option_always
            OverScrollModeOption.IF_CONTENT_SCROLLS ->
                R.string.option_display_over_scroll_mode_option_if_content_scrolls
        }

@get:StringRes
val ResetActionOption.displayLabelRes: Int
    get() =
        when (this) {
            ResetActionOption.HISTORY ->
                R.string.option_display_reset_action_option_history
            ResetActionOption.COOKIES ->
                R.string.option_display_reset_action_option_cookies
            ResetActionOption.WEB_STORAGE ->
                R.string.option_display_reset_action_option_web_storage
            ResetActionOption.CACHE ->
                R.string.option_display_reset_action_option_cache
            ResetActionOption.FORM_DATA ->
                R.string.option_display_reset_action_option_form_data
            ResetActionOption.SSL_PREFERENCES ->
                R.string.option_display_reset_action_option_ssl_preferences
            ResetActionOption.RECREATE_WEBVIEW ->
                R.string.option_display_reset_action_option_recreate_webview
        }

@get:StringRes
val RefreshOnNetworkAvailableOption.displayLabelRes: Int
    get() =
        when (this) {
            RefreshOnNetworkAvailableOption.ALWAYS ->
                R.string.option_display_refresh_on_network_available_option_always
            RefreshOnNetworkAvailableOption.ON_PAGE_ERROR ->
                R.string
                    .option_display_refresh_on_network_available_option_on_page_error
            RefreshOnNetworkAvailableOption.NEVER ->
                R.string.option_display_refresh_on_network_available_option_never
        }

@get:StringRes
val MqttQosOption.displayLabelRes: Int
    get() =
        when (this) {
            MqttQosOption.AT_MOST_ONCE ->
                R.string.option_display_mqtt_qos_option_at_most_once
            MqttQosOption.AT_LEAST_ONCE ->
                R.string.option_display_mqtt_qos_option_at_least_once
            MqttQosOption.EXACTLY_ONCE ->
                R.string.option_display_mqtt_qos_option_exactly_once
        }

@get:StringRes
val MqttRetainHandlingOption.displayLabelRes: Int
    get() =
        when (this) {
            MqttRetainHandlingOption.SEND ->
                R.string.option_display_mqtt_retain_handling_option_send
            MqttRetainHandlingOption.SEND_IF_SUBSCRIPTION_DOES_NOT_EXIST ->
                R.string
                    .option_display_mqtt_retain_handling_option_send_if_subscription_does_not_exist
            MqttRetainHandlingOption.DO_NOT_SEND ->
                R.string.option_display_mqtt_retain_handling_option_do_not_send
        }

@get:StringRes
val AppType.displayLabelRes: Int
    get() =
        when (this) {
            AppType.USER_APPS -> R.string.option_display_app_type_user_apps
            AppType.SYSTEM_APPS -> R.string.option_display_app_type_system_apps
            AppType.ALL_APPS -> R.string.option_display_app_type_all_apps
        }

@get:StringRes
val FilterSkipReason.displayLabelRes: Int
    get() =
        when (this) {
            FilterSkipReason.POPUP ->
                R.string.option_display_filter_skip_reason_popup
            FilterSkipReason.CSP -> R.string.option_display_filter_skip_reason_csp
            FilterSkipReason.HTML -> R.string.option_display_filter_skip_reason_html
            FilterSkipReason.PROCEDURAL ->
                R.string.option_display_filter_skip_reason_procedural
            FilterSkipReason.SCRIPTLET_SCOPE ->
                R.string.option_display_filter_skip_reason_scriptlet_scope
            FilterSkipReason.SCRIPTLET_NAME ->
                R.string.option_display_filter_skip_reason_scriptlet_name
            FilterSkipReason.SCRIPTLET_ARGUMENTS ->
                R.string.option_display_filter_skip_reason_scriptlet_arguments
            FilterSkipReason.SCRIPTLET_TRUST ->
                R.string.option_display_filter_skip_reason_scriptlet_trust
            FilterSkipReason.NETWORK ->
                R.string.option_display_filter_skip_reason_network
            FilterSkipReason.LIMIT ->
                R.string.option_display_filter_skip_reason_limit
            FilterSkipReason.OTHER ->
                R.string.option_display_filter_skip_reason_other
            FilterSkipReason.INVALID ->
                R.string.option_display_filter_skip_reason_invalid
        }

@get:StringRes
val RuleLoadStage.displayLabelRes: Int
    get() =
        when (this) {
            RuleLoadStage.READING_LISTS ->
                R.string.option_display_rule_load_stage_reading_lists
            RuleLoadStage.CHECKING_CACHE ->
                R.string.option_display_rule_load_stage_checking_cache
            RuleLoadStage.RESTORING ->
                R.string.option_display_rule_load_stage_restoring
            RuleLoadStage.COMPILING ->
                R.string.option_display_rule_load_stage_compiling
            RuleLoadStage.INDEXING ->
                R.string.option_display_rule_load_stage_indexing
            RuleLoadStage.SAVING_CACHE ->
                R.string.option_display_rule_load_stage_saving_cache
            RuleLoadStage.CLEARING ->
                R.string.option_display_rule_load_stage_clearing
        }
