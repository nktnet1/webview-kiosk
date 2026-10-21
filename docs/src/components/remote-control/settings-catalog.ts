/**
 * Setting keys and value types mirror app/src/main/res/xml/app_restrictions.xml.
 * Choice values mirror app/src/main/res/values/arrays.xml.
 * Keep in sync when Android restrictions change (verified in tests).
 */

export type SettingKind =
  | "boolean"
  | "string"
  | "integer"
  | "choice"
  | "multi-select";

export type SettingDefinition = {
  key: string;
  label: string;
  group: string;
  kind: SettingKind;
  multiline?: boolean;
  options?: readonly string[];
};

const KEYS = {
  boolean: [
    "web_content.support_pdf_rendering",
    "web_content.allow_local_files",
    "web_content.enable_content_blocking",
    "web_browsing.allow_refresh",
    "web_browsing.allow_pull_to_refresh",
    "web_browsing.allow_backwards_navigation",
    "web_browsing.allow_go_home",
    "web_browsing.clear_history_on_home",
    "web_browsing.replace_history_url_on_redirect",
    "web_browsing.allow_history_access",
    "web_browsing.allow_bookmark_access",
    "web_browsing.allow_other_url_schemes",
    "web_browsing.allow_default_long_press",
    "web_browsing.allow_link_long_press_context_menu",
    "web_engine.enable_javascript",
    "web_engine.enable_dom_storage",
    "web_engine.accept_cookies",
    "web_engine.accept_third_party_cookies",
    "web_engine.use_wide_viewport",
    "web_engine.load_with_overview_mode",
    "web_engine.support_zoom",
    "web_engine.built_in_zoom_controls",
    "web_engine.display_zoom_controls",
    "web_engine.allow_file_access_from_file_urls",
    "web_engine.allow_universal_access_from_file_urls",
    "web_engine.allow_file_picker",
    "web_engine.allow_file_download",
    "web_engine.media_playback_requires_user_gesture",
    "web_engine.request_focus_on_page_start",
    "web_lifecycle.lock_on_launch",
    "web_lifecycle.reset_on_launch",
    "web_lifecycle.refresh_on_screen_wake",
    "appearance.show_loading_progress_bar",
    "device.keep_screen_on",
    "device.allow_camera",
    "device.allow_microphone",
    "device.allow_location",
    "device.allow_nfc",
    "device.allow_notifications",
    "device.block_screen_capture",
    "device.block_volume_keys",
    "device.owner.lock_task_feature.home",
    "device.owner.lock_task_feature.overview",
    "device.owner.lock_task_feature.global_actions",
    "device.owner.lock_task_feature.notifications",
    "device.owner.lock_task_feature.system_info",
    "device.owner.lock_task_feature.keyguard",
    "device.owner.lock_task_feature.block_activity_start_in_task",
    "device.owner.dhizuku.request_permission_on_launch",
    "js_scripts.apply_app_theme",
    "js_scripts.enable_battery_api",
    "js_scripts.enable_brightness_api",
    "js_scripts.enable_vibration_api",
    "js_scripts.enable_dark_reader",
    "js_scripts.enable_eruda_console",
    "mqtt.enabled",
    "mqtt.home_assistant.enabled",
    "mqtt.use_foreground_service",
    "mqtt.connection.use_tls",
    "mqtt.connection.clean_start",
    "mqtt.connection.automatic_reconnect",
    "mqtt.connection.use_websocket",
    "mqtt.will.retain",
    "mqtt.restrictions.request_problem_information",
    "mqtt.restrictions.request_response_information",
    "mqtt.publish.event.retain",
    "mqtt.publish.response.retain",
    "mqtt.subscribe.command.retain_as_published",
    "mqtt.subscribe.settings.retain_as_published",
    "mqtt.subscribe.request.retain_as_published",
    "unifiedpush.enabled",
    "unifiedpush.process_unencrypted_messages",
    "unifiedpush.store_endpoint_credentials",
  ],
  string: [
    "mqtt.home_assistant.discovery_prefix",
    "mqtt.home_assistant.device_name",
    "mqtt.home_assistant.status_topic",
    "web_content.home_url",
    "web_content.website_blacklist",
    "web_content.website_whitelist",
    "web_content.website_bookmarks",
    "web_browsing.search_provider_url",
    "web_engine.user_agent",
    "web_engine.mutual_tls",
    "appearance.blocked_message",
    "appearance.custom_block_page_html",
    "device.custom_unlock_shortcut",
    "device.custom_auth_password",
    "js_scripts.custom_script_on_page_start",
    "js_scripts.custom_script_on_page_finish",
    "mqtt.connection.server_host",
    "mqtt.connection.client_id",
    "mqtt.connection.username",
    "mqtt.connection.password",
    "mqtt.connection.websocket_server_path",
    "mqtt.will.topic",
    "mqtt.will.payload",
    "mqtt.publish.event.topic",
    "mqtt.publish.response.topic",
    "mqtt.subscribe.command.topic",
    "mqtt.subscribe.settings.topic",
    "mqtt.subscribe.request.topic",
    "unifiedpush.distributor",
    "unifiedpush.instance",
    "unifiedpush.message_for_distributor",
    "unifiedpush.vapid_public_key",
  ],
  integer: [
    "web_engine.initial_scale",
    "web_lifecycle.reset_on_inactivity_seconds",
    "web_lifecycle.dim_screen_on_inactivity_seconds",
    "web_lifecycle.refresh_on_loading_error_interval_seconds",
    "device.brightness",
    "js_scripts.apply_desktop_viewport_width",
    "mqtt.connection.server_port",
    "mqtt.connection.keep_alive",
    "mqtt.connection.connect_timeout",
    "mqtt.connection.socket_connect_timeout",
    "mqtt.connection.session_expiry_interval",
    "mqtt.will.message_expiry_interval",
    "mqtt.will.delay_interval",
    "mqtt.restrictions.receive_maximum",
    "mqtt.restrictions.send_maximum",
    "mqtt.restrictions.maximum_packet_size",
    "mqtt.restrictions.send_maximum_packet_size",
    "mqtt.restrictions.topic_alias_maximum",
    "mqtt.restrictions.send_topic_alias_maximum",
  ],
} as const;

const OPTIONS = {
  "web_browsing.override_url_loading_block_action": [
    "SHOW_BLOCK_PAGE",
    "PREVENT_NAVIGATION",
    "SHOW_TOAST",
  ],
  "web_browsing.kiosk_control_panel_region": [
    "TOP_LEFT",
    "TOP_RIGHT",
    "BOTTOM_LEFT",
    "BOTTOM_RIGHT",
    "TOP",
    "BOTTOM",
    "FULL",
    "DISABLED",
  ],
  "web_browsing.search_suggestion_engine": [
    "NONE",
    "GOOGLE",
    "DUCKDUCKGO",
    "YAHOO",
  ],
  "web_engine.cache_mode": [
    "DEFAULT",
    "CACHE_ELSE_NETWORK",
    "NO_CACHE",
    "CACHE_ONLY",
  ],
  "web_engine.layout_algorithm": [
    "NORMAL",
    "SINGLE_COLUMN",
    "NARROW_COLUMNS",
    "TEXT_AUTOSIZING",
  ],
  "web_engine.ssl_error_mode": ["BLOCK", "PROMPT", "PROCEED"],
  "web_engine.mixed_content_mode": [
    "NEVER_ALLOW",
    "COMPATIBILITY_MODE",
    "ALWAYS_ALLOW",
  ],
  "web_engine.over_scroll_mode": ["NEVER", "ALWAYS", "IF_CONTENT_SCROLLS"],
  "web_lifecycle.refresh_on_network_available": [
    "ALWAYS",
    "ON_PAGE_ERROR",
    "NEVER",
  ],
  "appearance.theme": ["SYSTEM", "DARK", "LIGHT"],
  "appearance.floating_toolbar_mode": ["HIDDEN", "HIDDEN_WHEN_LOCKED", "SHOWN"],
  "appearance.webview_inset": [
    "NONE",
    "STATUS_BARS",
    "NAVIGATION_BARS",
    "SYSTEM_BARS",
    "DISPLAY_CUTOUT",
    "SAFE_DRAWING",
    "SAFE_GESTURES",
    "SAFE_CONTENT",
  ],
  "appearance.immersive_mode": ["ALWAYS_ON", "ALWAYS_OFF", "ONLY_WHEN_LOCKED"],
  "appearance.address_bar_mode": ["HIDDEN", "HIDDEN_WHEN_LOCKED", "SHOWN"],
  "appearance.address_bar_size": [
    "EXTRA_SMALL",
    "SMALL",
    "MEDIUM",
    "LARGE",
    "EXTRA_LARGE",
  ],
  "appearance.address_bar_position": ["TOP", "BOTTOM"],
  "device.rotation": [
    "AUTO",
    "ROTATION_0",
    "ROTATION_90",
    "ROTATION_180",
    "ROTATION_270",
  ],
  "device.back_button_hold_action": [
    "OPEN_KIOSK_CONTROL_PANEL",
    "GO_HOME",
    "DISABLED",
  ],
  "device.unlock_auth_requirement": ["DEFAULT", "OFF", "REQUIRE"],
  "mqtt.will.qos": ["AT_MOST_ONCE", "AT_LEAST_ONCE", "EXACTLY_ONCE"],
  "mqtt.publish.event.qos": ["AT_MOST_ONCE", "AT_LEAST_ONCE", "EXACTLY_ONCE"],
  "mqtt.publish.response.qos": [
    "AT_MOST_ONCE",
    "AT_LEAST_ONCE",
    "EXACTLY_ONCE",
  ],
  "mqtt.subscribe.command.qos": [
    "AT_MOST_ONCE",
    "AT_LEAST_ONCE",
    "EXACTLY_ONCE",
  ],
  "mqtt.subscribe.command.retain_handling": [
    "SEND",
    "SEND_IF_SUBSCRIPTION_DOES_NOT_EXIST",
    "DO_NOT_SEND",
  ],
  "mqtt.subscribe.settings.qos": [
    "AT_MOST_ONCE",
    "AT_LEAST_ONCE",
    "EXACTLY_ONCE",
  ],
  "mqtt.subscribe.settings.retain_handling": [
    "SEND",
    "SEND_IF_SUBSCRIPTION_DOES_NOT_EXIST",
    "DO_NOT_SEND",
  ],
  "mqtt.subscribe.request.qos": [
    "AT_MOST_ONCE",
    "AT_LEAST_ONCE",
    "EXACTLY_ONCE",
  ],
  "mqtt.subscribe.request.retain_handling": [
    "SEND",
    "SEND_IF_SUBSCRIPTION_DOES_NOT_EXIST",
    "DO_NOT_SEND",
  ],
  "web_browsing.address_bar_actions": [
    "NAVIGATION",
    "BACK",
    "FORWARD",
    "REFRESH",
    "HOME",
    "HISTORY",
    "BOOKMARK",
    "FILES",
    "FIND",
    "SCROLL_TOP",
    "SCROLL_BOT",
    "APPS",
    "SETTINGS",
    "LOCK",
    "UNLOCK",
  ],
  "web_browsing.kiosk_control_panel_actions": [
    "NAVIGATION",
    "BACK",
    "FORWARD",
    "REFRESH",
    "HOME",
    "HISTORY",
    "BOOKMARK",
    "FILES",
    "FIND",
    "SCROLL_TOP",
    "SCROLL_BOT",
    "APPS",
    "SETTINGS",
    "LOCK",
    "UNLOCK",
  ],
  "web_lifecycle.reset_actions": [
    "HISTORY",
    "COOKIES",
    "WEB_STORAGE",
    "CACHE",
    "FORM_DATA",
    "SSL_PREFERENCES",
    "RECREATE_WEBVIEW",
  ],
} as const;

/** Text-based settings that use multiline editors in the Android app. */
const MULTILINE_SETTING_KEYS = new Set<string>([
  "web_content.website_blacklist",
  "web_content.website_whitelist",
  "web_content.website_bookmarks",
  "web_engine.mutual_tls",
  "appearance.blocked_message",
  "appearance.custom_block_page_html",
  "js_scripts.custom_script_on_page_start",
  "js_scripts.custom_script_on_page_finish",
  "mqtt.will.payload",
]);

const MULTI_SELECT_KEYS = new Set<string>([
  "web_browsing.address_bar_actions",
  "web_browsing.kiosk_control_panel_actions",
  "web_lifecycle.reset_actions",
]);

const ACRONYMS = new Set([
  "API",
  "DOM",
  "HTML",
  "ID",
  "JS",
  "MQTT",
  "NFC",
  "PDF",
  "QoS",
  "SSL",
  "TLS",
  "URL",
]);

export function formatSettingLabel(value: string): string {
  return value
    .replaceAll("_", " ")
    .split(" ")
    .map((word) => {
      const upper = word.toUpperCase();
      return ACRONYMS.has(upper)
        ? upper
        : `${word.charAt(0).toUpperCase()}${word.slice(1).toLowerCase()}`;
    })
    .join(" ");
}

function setting(
  key: string,
  kind: SettingKind,
  options?: readonly string[],
): SettingDefinition {
  const parts = key.split(".");
  return {
    key,
    group: formatSettingLabel(parts[0]),
    label: formatSettingLabel(parts.at(-1) ?? key),
    kind,
    ...(kind === "string" && MULTILINE_SETTING_KEYS.has(key)
      ? { multiline: true }
      : {}),
    ...(options ? { options } : {}),
  };
}

export const SETTINGS_CATALOG: readonly SettingDefinition[] = [
  ...KEYS.boolean.map((key) => setting(key, "boolean")),
  ...KEYS.string.map((key) => setting(key, "string")),
  ...KEYS.integer.map((key) => setting(key, "integer")),
  ...Object.entries(OPTIONS).map(([key, options]) =>
    setting(
      key,
      MULTI_SELECT_KEYS.has(key) ? "multi-select" : "choice",
      options,
    ),
  ),
].sort((a, b) => a.key.localeCompare(b.key));

export const SETTINGS_BY_KEY = new Map(
  SETTINGS_CATALOG.map((setting) => [setting.key, setting]),
);

export function defaultSettingValue(
  setting: SettingDefinition,
): string | number | boolean | string[] {
  switch (setting.kind) {
    case "boolean":
      return false;
    case "integer":
      return 0;
    case "string":
      return "";
    case "choice":
      return setting.options?.[0] ?? "";
    case "multi-select":
      return [];
  }
}
