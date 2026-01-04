package uk.nktnet.webviewkiosk.config

object UserSettingsKeys {
    const val PREFS_NAME = "user_settings"

    object WebContent {
        const val HOME_URL = "web_content.home_url"
        const val WEBSITE_BLACKLIST = "web_content.website_blacklist"
        const val WEBSITE_WHITELIST = "web_content.website_whitelist"
        const val WEBSITE_BOOKMARKS = "web_content.website_bookmarks"
        const val ALLOW_LOCAL_FILES = "web_content.allow_local_files"
    }

    object WebBrowsing {
        const val ALLOW_REFRESH = "web_browsing.allow_refresh"
        const val ALLOW_PULL_TO_REFRESH = "web_browsing.allow_pull_to_refresh"
        const val ALLOW_BACKWARDS_NAVIGATION = "web_browsing.allow_backwards_navigation"
        const val ALLOW_GO_HOME = "web_browsing.allow_go_home"
        const val CLEAR_HISTORY_ON_HOME = "web_browsing.clear_history_on_home"
        const val REPLACE_HISTORY_URL_ON_REDIRECT = "web_browsing.replace_history_url_on_redirect"
        const val ALLOW_HISTORY_ACCESS = "web_browsing.allow_history_access"
        const val ALLOW_BOOKMARK_ACCESS = "web_browsing.allow_bookmark_access"
        const val ALLOW_OTHER_URL_SCHEMES = "web_browsing.allow_other_url_schemes"
        const val ALLOW_DEFAULT_LONG_PRESS = "web_browsing.allow_default_long_press"
        const val ALLOW_LINK_LONG_PRESS_CONTEXT_MENU = "web_browsing.allow_link_long_press_context_menu"
        const val OVERRIDE_URL_LOADING_BLOCK_ACTION = "web_browsing.override_url_loading_block_action"
        const val ADDRESS_BAR_ACTIONS = "web_browsing.address_bar_actions"
        const val KIOSK_CONTROL_PANEL_REGION = "web_browsing.kiosk_control_panel_region"
        const val KIOSK_CONTROL_PANEL_ACTIONS = "web_browsing.kiosk_control_panel_actions"
        const val SEARCH_PROVIDER_URL = "web_browsing.search_provider_url"
        const val SEARCH_SUGGESTION_ENGINE = "web_browsing.search_suggestion_engine"
    }

    object WebEngine {
        const val ENABLE_JAVASCRIPT = "web_engine.enable_javascript"
        const val ENABLE_DOM_STORAGE = "web_engine.enable_dom_storage"
        const val CACHE_MODE = "web_engine.cache_mode"
        const val LAYOUT_ALGORITHM = "web_engine.layout_algorithm"
        const val ACCEPT_COOKIES = "web_engine.accept_cookies"
        const val ACCEPT_THIRD_PARTY_COOKIES = "web_engine.accept_third_party_cookies"
        const val USER_AGENT = "web_engine.user_agent"
        const val USE_WIDE_VIEWPORT = "web_engine.use_wide_viewport"
        const val LOAD_WITH_OVERVIEW_MODE = "web_engine.load_with_overview_mode"
        const val SUPPORT_ZOOM = "web_engine.support_zoom"
        const val BUILT_IN_ZOOM_CONTROLS = "web_engine.built_in_zoom_controls"
        const val DISPLAY_ZOOM_CONTROLS = "web_engine.display_zoom_controls"
        const val INITIAL_SCALE = "web_engine.initial_scale"
        const val ALLOW_FILE_ACCESS_FROM_FILE_URLS = "web_engine.allow_file_access_from_file_urls"
        const val ALLOW_UNIVERSAL_ACCESS_FROM_FILE_URLS = "web_engine.allow_universal_access_from_file_urls"
        const val ALLOW_FILE_PICKER = "web_engine.allow_file_picker"
        const val MEDIA_PLAYBACK_REQUIRES_USER_GESTURE = "web_engine.media_playback_requires_user_gesture"
        const val SSL_ERROR_MODE = "web_engine.ssl_error_mode"
        const val MIXED_CONTENT_MODE = "web_engine.mixed_content_mode"
        const val OVER_SCROLL_MODE = "web_engine.over_scroll_mode"
        const val REQUEST_FOCUS_ON_PAGE_START = "web_engine.request_focus_on_page_start"
    }

    object WebLifecycle {
        const val LOCK_ON_LAUNCH = "web_lifecycle.lock_on_launch"
        const val RESET_ON_LAUNCH = "web_lifecycle.reset_on_launch"
        const val RESET_ON_INACTIVITY_SECONDS = "web_lifecycle.reset_on_inactivity_seconds"
        const val DIM_SCREEN_ON_INACTIVITY_SECONDS = "web_lifecycle.dim_screen_on_inactivity_seconds"
        const val REFRESH_ON_LOADING_ERROR_INTERVAL_SECONDS = "web_lifecycle.refresh_on_loading_error_interval_seconds"
    }

    object Appearance {
        const val THEME = "appearance.theme"
        const val FLOATING_TOOLBAR_MODE = "appearance.floating_toolbar_mode"
        const val WEBVIEW_INSET = "appearance.webview_inset"
        const val IMMERSIVE_MODE = "appearance.immersive_mode"
        const val BLOCKED_MESSAGE = "appearance.blocked_message"
        const val CUSTOM_BLOCK_PAGE_HTML = "appearance.custom_block_page_html"
        const val ADDRESS_BAR_MODE = "appearance.address_bar_mode"
        const val ADDRESS_BAR_SIZE = "appearance.address_bar_size"
        const val ADDRESS_BAR_POSITION = "appearance.address_bar_position"
    }

    object Device {
        const val KEEP_SCREEN_ON = "device.keep_screen_on"
        const val DEVICE_ROTATION = "device.rotation"
        const val BRIGHTNESS = "device.brightness"
        const val ALLOW_CAMERA = "device.allow_camera"
        const val ALLOW_MICROPHONE = "device.allow_microphone"
        const val ALLOW_LOCATION = "device.allow_location"
        const val ALLOW_NOTIFICATIONS = "device.allow_notifications"
        const val BACK_BUTTON_HOLD_ACTION = "device.back_button_hold_action"
        const val CUSTOM_UNLOCK_SHORTCUT = "device.custom_unlock_shortcut"
        const val CUSTOM_AUTH_PASSWORD = "device.custom_auth_password"
        const val UNLOCK_AUTH_REQUIREMENT = "device.unlock_auth_requirement"
        const val BLOCK_SCREEN_CAPTURE = "device.block_screen_capture"
        const val BLOCK_VOLUME_KEYS = "device.block_volume_keys"

        object Owner {
            object LockTaskFeature {
                const val HOME = "device.owner.lock_task_feature.home"
                const val OVERVIEW = "device.owner.lock_task_feature.overview"
                const val GLOBAL_ACTIONS = "device.owner.lock_task_feature.global_actions"
                const val NOTIFICATIONS = "device.owner.lock_task_feature.notifications"
                const val SYSTEM_INFO = "device.owner.lock_task_feature.system_info"
                const val KEYGUARD = "device.owner.lock_task_feature.keyguard"
                const val BLOCK_ACTIVITY_START_IN_TASK = "device.owner.lock_task_feature.block_activity_start_in_task"
            }

            object Dhizuku {
                const val REQUEST_PERMISSION_ON_LAUNCH = "device.owner.dhizuku.request_permission_on_launch"
            }
        }
    }

    object JsScripts {
        const val APPLY_APP_THEME = "js_scripts.apply_app_theme"
        const val APPLY_DESKTOP_VIEWPORT_WIDTH = "js_scripts.apply_desktop_viewport_width"
        const val ENABLE_BATTERY_API = "js_scripts.enable_battery_api"
        const val ENABLE_BRIGHTNESS_API = "js_scripts.enable_brightness_api"
        const val CUSTOM_SCRIPT_ON_PAGE_START = "js_scripts.custom_script_on_page_start"
        const val CUSTOM_SCRIPT_ON_PAGE_FINISH = "js_scripts.custom_script_on_page_finish"
    }

    object Mqtt {
        const val ENABLED = "mqtt.enabled"
        const val USE_FOREGROUND_SERVICE = "mqtt.use_foreground_service"

        object Connection {
            const val SERVER_HOST = "mqtt.connection.server_host"
            const val SERVER_PORT = "mqtt.connection.server_port"
            const val CLIENT_ID = "mqtt.connection.client_id"
            const val USE_TLS = "mqtt.connection.use_tls"
            const val USERNAME = "mqtt.connection.username"
            const val PASSWORD = "mqtt.connection.password"
            const val CLEAN_START = "mqtt.connection.clean_start"
            const val KEEP_ALIVE = "mqtt.connection.keep_alive"
            const val CONNECT_TIMEOUT = "mqtt.connection.connect_timeout"
            const val SOCKET_CONNECT_TIMEOUT = "mqtt.connection.socket_connect_timeout"
            const val AUTOMATIC_RECONNECT = "mqtt.connection.automatic_reconnect"
            const val SESSION_EXPIRY_INTERVAL = "mqtt.connection.session_expiry_interval"
            const val USE_WEBSOCKET = "mqtt.connection.use_websocket"
            const val WEBSOCKET_SERVER_PATH = "mqtt.connection.websocket_server_path"
        }

        object Topics {
            object Publish {
                object Event {
                    const val TOPIC = "mqtt.publish.event.topic"
                    const val QOS = "mqtt.publish.event.qos"
                    const val RETAIN = "mqtt.publish.event.retain"
                }

                object Response {
                    const val TOPIC = "mqtt.publish.response.topic"
                    const val QOS = "mqtt.publish.response.qos"
                    const val RETAIN = "mqtt.publish.response.retain"
                }
            }

            object Subscribe {
                object Command {
                    const val TOPIC = "mqtt.subscribe.command.topic"
                    const val QOS = "mqtt.subscribe.command.qos"
                    const val RETAIN_HANDLING = "mqtt.subscribe.command.retain_handling"
                    const val RETAIN_AS_PUBLISHED = "mqtt.subscribe.command.retain_as_published"
                }

                object Settings {
                    const val TOPIC = "mqtt.subscribe.settings.topic"
                    const val QOS = "mqtt.subscribe.settings.qos"
                    const val RETAIN_HANDLING = "mqtt.subscribe.settings.retain_handling"
                    const val RETAIN_AS_PUBLISHED = "mqtt.subscribe.settings.retain_as_published"
                }

                object Request {
                    const val TOPIC = "mqtt.subscribe.request.topic"
                    const val QOS = "mqtt.subscribe.request.qos"
                    const val RETAIN_HANDLING = "mqtt.subscribe.request.retain_handling"
                    const val RETAIN_AS_PUBLISHED = "mqtt.subscribe.request.retain_as_published"
                }
            }
        }

        object Will {
            const val TOPIC = "mqtt.will.topic"
            const val QOS = "mqtt.will.qos"
            const val PAYLOAD = "mqtt.will.payload"
            const val RETAIN = "mqtt.will.retain"
            const val MESSAGE_EXPIRY_INTERVAL = "mqtt.will.message_expiry_interval"
            const val DELAY_INTERVAL = "mqtt.will.delay_interval"
        }

        object Restrictions {
            const val RECEIVE_MAXIMUM = "mqtt.restrictions.receive_maximum"
            const val SEND_MAXIMUM = "mqtt.restrictions.send_maximum"
            const val MAXIMUM_PACKET_SIZE = "mqtt.restrictions.maximum_packet_size"
            const val SEND_MAXIMUM_PACKET_SIZE = "mqtt.restrictions.send_maximum_packet_size"
            const val TOPIC_ALIAS_MAXIMUM = "mqtt.restrictions.topic_alias_maximum"
            const val SEND_TOPIC_ALIAS_MAXIMUM = "mqtt.restrictions.send_topic_alias_maximum"
            const val REQUEST_PROBLEM_INFORMATION = "mqtt.restrictions.request_problem_information"
            const val REQUEST_RESPONSE_INFORMATION = "mqtt.restrictions.request_response_information"
        }
    }
}
