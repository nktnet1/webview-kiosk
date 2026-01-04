package uk.nktnet.webviewkiosk.config

import android.content.Context
import android.content.RestrictionsManager
import android.content.SharedPreferences
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.mqtt.MqttQosOption
import uk.nktnet.webviewkiosk.config.mqtt.MqttRetainHandlingOption
import uk.nktnet.webviewkiosk.config.mqtt.MqttVariableName
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
import uk.nktnet.webviewkiosk.config.option.SearchSuggestionEngineOption
import uk.nktnet.webviewkiosk.config.option.SslErrorModeOption
import uk.nktnet.webviewkiosk.config.option.ThemeOption
import uk.nktnet.webviewkiosk.config.option.UnlockAuthRequirementOption
import uk.nktnet.webviewkiosk.config.option.WebViewInsetOption
import uk.nktnet.webviewkiosk.config.option.WebviewControlActionOption
import uk.nktnet.webviewkiosk.utils.booleanPref
import uk.nktnet.webviewkiosk.utils.enumListPref
import uk.nktnet.webviewkiosk.utils.intPref
import uk.nktnet.webviewkiosk.utils.stringEnumPref
import uk.nktnet.webviewkiosk.utils.stringPref
import uk.nktnet.webviewkiosk.utils.stringPrefOptional

class UserSettings(val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        UserSettingsKeys.PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private val getRestrictions = {
        (context.getSystemService(Context.RESTRICTIONS_SERVICE) as? RestrictionsManager)
            ?.applicationRestrictions
    }

    fun isRestricted(key: String): Boolean =
        getRestrictions()?.containsKey(key) == true

    // Web Content
    var homeUrl by stringPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.WebContent.HOME_URL,
        Constants.WEBSITE_URL
    )
    var websiteBlacklist by stringPrefOptional(
        getRestrictions,
        prefs,
        UserSettingsKeys.WebContent.WEBSITE_BLACKLIST
    )
    var websiteWhitelist by stringPrefOptional(
        getRestrictions,
        prefs,
        UserSettingsKeys.WebContent.WEBSITE_WHITELIST
    )
    var websiteBookmarks by stringPrefOptional(
        getRestrictions,
        prefs,
        UserSettingsKeys.WebContent.WEBSITE_BOOKMARKS
    )
    var allowLocalFiles by booleanPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.WebContent.ALLOW_LOCAL_FILES,
        true
    )

    // Web Browsing
    var allowRefresh by booleanPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.WebBrowsing.ALLOW_REFRESH,
        true
    )
    var allowPullToRefresh by booleanPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.WebBrowsing.ALLOW_PULL_TO_REFRESH,
        true
    )
    var allowBackwardsNavigation by booleanPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.WebBrowsing.ALLOW_BACKWARDS_NAVIGATION,
        true
    )
    var allowGoHome by booleanPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.WebBrowsing.ALLOW_GO_HOME,
        true
    )
    var clearHistoryOnHome by booleanPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.WebBrowsing.CLEAR_HISTORY_ON_HOME,
        false
    )
    var replaceHistoryUrlOnRedirect by booleanPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.WebBrowsing.REPLACE_HISTORY_URL_ON_REDIRECT,
        true
    )
    var allowHistoryAccess by booleanPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.WebBrowsing.ALLOW_HISTORY_ACCESS,
        true
    )
    var allowBookmarkAccess by booleanPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.WebBrowsing.ALLOW_BOOKMARK_ACCESS,
        true
    )
    var allowOtherUrlSchemes by booleanPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.WebBrowsing.ALLOW_OTHER_URL_SCHEMES,
        false
    )
    var allowDefaultLongPress by booleanPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.WebBrowsing.ALLOW_DEFAULT_LONG_PRESS,
        true
    )
    var allowLinkLongPressContextMenu by booleanPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.WebBrowsing.ALLOW_LINK_LONG_PRESS_CONTEXT_MENU,
        true
    )
    var overrideUrlLoadingBlockAction by stringEnumPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.WebBrowsing.OVERRIDE_URL_LOADING_BLOCK_ACTION,
        OverrideUrlLoadingBlockActionOption.SHOW_BLOCK_PAGE.name,
        fromString = OverrideUrlLoadingBlockActionOption::fromString
    )
    var addressBarActions by enumListPref(
        getRestrictions,
        prefs = prefs,
        key = UserSettingsKeys.WebBrowsing.ADDRESS_BAR_ACTIONS,
        default = WebviewControlActionOption.getDefaultAddressBarOptions(),
        itemFromString = {
            WebviewControlActionOption.itemFromString(it)
                ?: WebviewControlActionOption.BACK
        }
    )
    var kioskControlPanelRegion by stringEnumPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.WebBrowsing.KIOSK_CONTROL_PANEL_REGION,
        KioskControlPanelRegionOption.TOP_LEFT.name,
        fromString = KioskControlPanelRegionOption::fromString
    )
    var kioskControlPanelActions by enumListPref(
        getRestrictions,
        prefs = prefs,
        key = UserSettingsKeys.WebBrowsing.KIOSK_CONTROL_PANEL_ACTIONS,
        default = WebviewControlActionOption.getDefaultKioskControlPanelOptions(),
        itemFromString = {
            WebviewControlActionOption.itemFromString(it)
                ?: WebviewControlActionOption.HISTORY
        }
    )
    var searchProviderUrl by stringPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.WebBrowsing.SEARCH_PROVIDER_URL,
        Constants.DEFAULT_SEARCH_PROVIDER_URL
    )
    var searchSuggestionEngine by stringEnumPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.WebBrowsing.SEARCH_SUGGESTION_ENGINE,
        SearchSuggestionEngineOption.NONE.name,
        fromString = SearchSuggestionEngineOption::fromString
    )

    // Web Engine
    var enableJavaScript by booleanPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.WebEngine.ENABLE_JAVASCRIPT,
        true
    )
    var enableDomStorage by booleanPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.WebEngine.ENABLE_DOM_STORAGE,
        true
    )
    var acceptCookies by booleanPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.WebEngine.ACCEPT_COOKIES,
        true
    )
    var acceptThirdPartyCookies by booleanPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.WebEngine.ACCEPT_THIRD_PARTY_COOKIES,
        false
    )
    var cacheMode by stringEnumPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.WebEngine.CACHE_MODE,
        CacheModeOption.DEFAULT.name,
        fromString = CacheModeOption::fromString
    )
    var layoutAlgorithm by stringEnumPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.WebEngine.LAYOUT_ALGORITHM,
        LayoutAlgorithmOption.NORMAL.name,
        fromString = LayoutAlgorithmOption::fromString
    )
    var userAgent by stringPrefOptional(getRestrictions, prefs, UserSettingsKeys.WebEngine.USER_AGENT)
    var useWideViewport by booleanPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.WebEngine.USE_WIDE_VIEWPORT,
        true
    )
    var loadWithOverviewMode by booleanPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.WebEngine.LOAD_WITH_OVERVIEW_MODE,
        true
    )
    var supportZoom by booleanPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.WebEngine.SUPPORT_ZOOM,
        true
    )
    var builtInZoomControls by booleanPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.WebEngine.BUILT_IN_ZOOM_CONTROLS,
        true
    )
    var displayZoomControls by booleanPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.WebEngine.DISPLAY_ZOOM_CONTROLS,
        false
    )
    var initialScale by intPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.WebEngine.INITIAL_SCALE,
        0,
        min = 0,
    )
    var allowFileAccessFromFileURLs by booleanPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.WebEngine.ALLOW_FILE_ACCESS_FROM_FILE_URLS,
        false
    )
    var allowUniversalAccessFromFileURLs by booleanPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.WebEngine.ALLOW_UNIVERSAL_ACCESS_FROM_FILE_URLS,
        false
    )
    var allowFilePicker by booleanPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.WebEngine.ALLOW_FILE_PICKER,
        false
    )
    var mediaPlaybackRequiresUserGesture by booleanPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.WebEngine.MEDIA_PLAYBACK_REQUIRES_USER_GESTURE,
        true
    )
    var sslErrorMode by stringEnumPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.WebEngine.SSL_ERROR_MODE,
        SslErrorModeOption.BLOCK.name,
        fromString = SslErrorModeOption::fromString
    )
    var mixedContentMode by stringEnumPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.WebEngine.MIXED_CONTENT_MODE,
        MixedContentModeOption.NEVER_ALLOW.name,
        fromString = MixedContentModeOption::fromString
    )
    var overScrollMode by stringEnumPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.WebEngine.OVER_SCROLL_MODE,
        OverScrollModeOption.IF_CONTENT_SCROLLS.name,
        fromString = OverScrollModeOption::fromString
    )
    var requestFocusOnPageStart by booleanPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.WebEngine.REQUEST_FOCUS_ON_PAGE_START,
        true
    )

    // Web Lifecycle
    var lockOnLaunch by booleanPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.WebLifecycle.LOCK_ON_LAUNCH,
        false
    )
    var resetOnLaunch by booleanPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.WebLifecycle.RESET_ON_LAUNCH,
        false
    )
    var resetOnInactivitySeconds by intPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.WebLifecycle.RESET_ON_INACTIVITY_SECONDS,
        0,
        min = 0,
    )
    var dimScreenOnInactivitySeconds by intPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.WebLifecycle.DIM_SCREEN_ON_INACTIVITY_SECONDS,
        0,
        min = 0,
    )
    var refreshOnLoadingErrorIntervalSeconds by intPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.WebLifecycle.REFRESH_ON_LOADING_ERROR_INTERVAL_SECONDS,
        0,
        min = 0,
    )

    // Appearance
    var theme by stringEnumPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.Appearance.THEME,
        ThemeOption.SYSTEM.name,
        fromString = ThemeOption::fromString
    )
    var floatingToolbarMode by stringEnumPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.Appearance.FLOATING_TOOLBAR_MODE,
        FloatingToolbarModeOption.HIDDEN_WHEN_LOCKED.name,
        fromString = FloatingToolbarModeOption::fromString
    )
    var webViewInset by stringEnumPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.Appearance.WEBVIEW_INSET,
        WebViewInsetOption.SYSTEM_BARS.name,
        fromString = WebViewInsetOption::fromString
    )
    var immersiveMode by stringEnumPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.Appearance.IMMERSIVE_MODE,
        ImmersiveModeOption.ONLY_WHEN_LOCKED.name,
        fromString = ImmersiveModeOption::fromString
    )
    var blockedMessage by stringPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.Appearance.BLOCKED_MESSAGE,
        "This site is blocked by ${context.getString(R.string.app_name)}."
    )
    var customBlockPageHtml by stringPrefOptional(
        getRestrictions,
        prefs,
        UserSettingsKeys.Appearance.CUSTOM_BLOCK_PAGE_HTML
    )
    var addressBarMode by stringEnumPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.Appearance.ADDRESS_BAR_MODE,
        AddressBarModeOption.HIDDEN_WHEN_LOCKED.name,
        fromString = AddressBarModeOption::fromString
    )
    var addressBarSize by stringEnumPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.Appearance.ADDRESS_BAR_SIZE,
        AddressBarSizeOption.MEDIUM.name,
        fromString = AddressBarSizeOption::fromString
    )
    var addressBarPosition by stringEnumPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.Appearance.ADDRESS_BAR_POSITION,
        AddressBarPositionOption.TOP.name,
        fromString = AddressBarPositionOption::fromString
    )

    // Device
    var keepScreenOn by booleanPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.Device.KEEP_SCREEN_ON,
        false
    )
    var rotation by stringEnumPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.Device.DEVICE_ROTATION,
        DeviceRotationOption.AUTO.name,
        fromString = DeviceRotationOption::fromString
    )
    var brightness by intPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.Device.BRIGHTNESS,
        -1,
        min = -1,
        max = 100,
    )
    var allowCamera by booleanPref(getRestrictions, prefs, UserSettingsKeys.Device.ALLOW_CAMERA, false)
    var allowMicrophone by booleanPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.Device.ALLOW_MICROPHONE,
        false
    )
    var allowLocation by booleanPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.Device.ALLOW_LOCATION,
        false
    )
    var allowNotifications by booleanPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.Device.ALLOW_NOTIFICATIONS,
        false
    )
    var backButtonHoldAction by stringEnumPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.Device.BACK_BUTTON_HOLD_ACTION,
        BackButtonHoldActionOption.OPEN_KIOSK_CONTROL_PANEL.name,
        fromString = BackButtonHoldActionOption::fromString
    )
    var customUnlockShortcut by stringPrefOptional(
        getRestrictions,
        prefs,
        UserSettingsKeys.Device.CUSTOM_UNLOCK_SHORTCUT
    )
    var customAuthPassword by stringPrefOptional(
        getRestrictions,
        prefs,
        UserSettingsKeys.Device.CUSTOM_AUTH_PASSWORD
    )
    var unlockAuthRequirement by stringEnumPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.Device.UNLOCK_AUTH_REQUIREMENT,
        UnlockAuthRequirementOption.DEFAULT.name,
        fromString = UnlockAuthRequirementOption::fromString
    )
    var blockScreenCapture by booleanPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.Device.BLOCK_SCREEN_CAPTURE,
        false
    )
    var blockVolumeKeys by booleanPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.Device.BLOCK_VOLUME_KEYS,
        false
    )

    var lockTaskFeatureHome by booleanPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.Device.Owner.LockTaskFeature.HOME,
        false
    )
    var lockTaskFeatureOverview by booleanPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.Device.Owner.LockTaskFeature.OVERVIEW,
        false
    )
    var lockTaskFeatureGlobalActions by booleanPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.Device.Owner.LockTaskFeature.GLOBAL_ACTIONS,
        false
    )
    var lockTaskFeatureNotifications by booleanPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.Device.Owner.LockTaskFeature.NOTIFICATIONS,
        false
    )
    var lockTaskFeatureSystemInfo by booleanPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.Device.Owner.LockTaskFeature.SYSTEM_INFO,
        false
    )
    var lockTaskFeatureKeyguard by booleanPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.Device.Owner.LockTaskFeature.KEYGUARD,
        false
    )
    var lockTaskFeatureBlockActivityStartInTask by booleanPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.Device.Owner.LockTaskFeature.BLOCK_ACTIVITY_START_IN_TASK,
        true
    )
    var dhizukuRequestPermissionOnLaunch by booleanPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.Device.Owner.Dhizuku.REQUEST_PERMISSION_ON_LAUNCH,
        true
    )

    // JS Scripts
    var applyAppTheme by booleanPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.JsScripts.APPLY_APP_THEME,
        true
    )
    var applyDesktopViewportWidth by intPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.JsScripts.APPLY_DESKTOP_VIEWPORT_WIDTH,
        0,
        min = 0,
    )
    var enableBatteryApi by booleanPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.JsScripts.ENABLE_BATTERY_API,
        false
    )
    var enableBrightnessApi by booleanPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.JsScripts.ENABLE_BRIGHTNESS_API,
        false
    )
    var customScriptOnPageStart by stringPrefOptional(
        getRestrictions,
        prefs,
        UserSettingsKeys.JsScripts.CUSTOM_SCRIPT_ON_PAGE_START
    )
    var customScriptOnPageFinish by stringPrefOptional(
        getRestrictions,
        prefs,
        UserSettingsKeys.JsScripts.CUSTOM_SCRIPT_ON_PAGE_FINISH
    )

    // MQTT
    var mqttEnabled by booleanPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.Mqtt.ENABLED,
        false
    )
    var mqttUseForegroundService by booleanPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.Mqtt.USE_FOREGROUND_SERVICE,
        false
    )
    var mqttServerHost by stringPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.Mqtt.Connection.SERVER_HOST,
        "127.0.0.1"
    )
    var mqttServerPort by intPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.Mqtt.Connection.SERVER_PORT,
        1883
    )
    var mqttClientId by stringPrefOptional(
        getRestrictions,
        prefs,
        UserSettingsKeys.Mqtt.Connection.CLIENT_ID,
    )
    var mqttUseTls by booleanPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.Mqtt.Connection.USE_TLS,
        false
    )
    var mqttUsername by stringPrefOptional(
        getRestrictions,
        prefs,
        UserSettingsKeys.Mqtt.Connection.USERNAME
    )
    var mqttPassword by stringPrefOptional(
        getRestrictions,
        prefs,
        UserSettingsKeys.Mqtt.Connection.PASSWORD
    )
    var mqttCleanStart by booleanPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.Mqtt.Connection.CLEAN_START,
        true
    )
    var mqttKeepAlive by intPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.Mqtt.Connection.KEEP_ALIVE,
        60
    )
    var mqttConnectTimeout by intPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.Mqtt.Connection.CONNECT_TIMEOUT,
        30,
        min = 0,
        max = Int.MAX_VALUE
    )
    var mqttSocketConnectTimeout by intPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.Mqtt.Connection.SOCKET_CONNECT_TIMEOUT,
        5,
        min = 0,
        max = Int.MAX_VALUE
    )
    var mqttAutomaticReconnect by booleanPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.Mqtt.Connection.AUTOMATIC_RECONNECT,
        true
    )
    var mqttSessionExpiryInterval by intPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.Mqtt.Connection.SESSION_EXPIRY_INTERVAL,
        0,
        min = 0,
        max = Int.MAX_VALUE
    )
    var mqttUseWebSocket by booleanPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.Mqtt.Connection.USE_WEBSOCKET,
        false
    )
    var mqttWebSocketServerPath by stringPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.Mqtt.Connection.WEBSOCKET_SERVER_PATH,
        "/mqtt"
    )

    var mqttPublishEventTopic by stringPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.Mqtt.Topics.Publish.Event.TOPIC,
        $$"wk/event/${$${MqttVariableName.EVENT_TYPE.name}}"
    )
    var mqttPublishEventQos by stringEnumPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.Mqtt.Topics.Publish.Event.QOS,
        MqttQosOption.AT_MOST_ONCE.name,
        fromString = MqttQosOption::fromString
    )
    var mqttPublishEventRetain by booleanPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.Mqtt.Topics.Publish.Event.RETAIN,
        false,
    )
    var mqttPublishResponseTopic by stringPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.Mqtt.Topics.Publish.Response.TOPIC,
        $$"wk/response/${$${MqttVariableName.RESPONSE_TYPE.name}}"
    )
    var mqttPublishResponseQos by stringEnumPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.Mqtt.Topics.Publish.Response.QOS,
        MqttQosOption.AT_MOST_ONCE.name,
        fromString = MqttQosOption::fromString
    )
    var mqttPublishResponseRetain by booleanPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.Mqtt.Topics.Publish.Response.RETAIN,
        false
    )
    var mqttSubscribeCommandTopic by stringPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.Mqtt.Topics.Subscribe.Command.TOPIC,
        "wk/command"
    )
    var mqttSubscribeCommandQos by stringEnumPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.Mqtt.Topics.Subscribe.Command.QOS,
        MqttQosOption.AT_MOST_ONCE.name,
        fromString = MqttQosOption::fromString
    )
    var mqttSubscribeCommandRetainHandling by stringEnumPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.Mqtt.Topics.Subscribe.Command.RETAIN_HANDLING,
        MqttRetainHandlingOption.DO_NOT_SEND.name,
        fromString = MqttRetainHandlingOption::fromString
    )
    var mqttSubscribeCommandRetainAsPublished by booleanPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.Mqtt.Topics.Subscribe.Command.RETAIN_AS_PUBLISHED,
        false,
    )
    var mqttSubscribeSettingsTopic by stringPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.Mqtt.Topics.Subscribe.Settings.TOPIC,
        "wk/settings"
    )
    var mqttSubscribeSettingsQos by stringEnumPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.Mqtt.Topics.Subscribe.Settings.QOS,
        MqttQosOption.AT_MOST_ONCE.name,
        fromString = MqttQosOption::fromString
    )
    var mqttSubscribeSettingsRetainHandling by stringEnumPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.Mqtt.Topics.Subscribe.Settings.RETAIN_HANDLING,
        MqttRetainHandlingOption.DO_NOT_SEND.name,
        fromString = MqttRetainHandlingOption::fromString
    )

    var mqttSubscribeSettingsRetainAsPublished by booleanPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.Mqtt.Topics.Subscribe.Settings.RETAIN_AS_PUBLISHED,
        false,
    )
    var mqttSubscribeRequestTopic by stringPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.Mqtt.Topics.Subscribe.Request.TOPIC,
        "wk/request"
    )
    var mqttSubscribeRequestQos by stringEnumPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.Mqtt.Topics.Subscribe.Request.QOS,
        MqttQosOption.AT_MOST_ONCE.name,
        fromString = MqttQosOption::fromString
    )
    var mqttSubscribeRequestRetainHandling by stringEnumPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.Mqtt.Topics.Subscribe.Request.RETAIN_HANDLING,
        MqttRetainHandlingOption.DO_NOT_SEND.name,
        fromString = MqttRetainHandlingOption::fromString
    )
    var mqttSubscribeRequestRetainAsPublished by booleanPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.Mqtt.Topics.Subscribe.Request.RETAIN_AS_PUBLISHED,
        false,
    )

    var mqttWillTopic by stringPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.Mqtt.Will.TOPIC,
        "wk/will"
    )
    var mqttWillQos by stringEnumPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.Mqtt.Will.QOS,
        MqttQosOption.AT_MOST_ONCE.name,
        fromString = MqttQosOption::fromString
    )
    var mqttWillPayload by stringPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.Mqtt.Will.PAYLOAD,
        $$"""
            {
              "message": "Client has disconnected.",
              "username": "${USERNAME}",
              "appInstanceId": "${APP_INSTANCE_ID}"
            }
        """.trimIndent(),
    )
    var mqttWillRetain by booleanPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.Mqtt.Will.RETAIN,
        false
    )
    var mqttWillMessageExpiryInterval by intPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.Mqtt.Will.MESSAGE_EXPIRY_INTERVAL,
        0,
        min = 0,
        max = Int.MAX_VALUE
    )
    var mqttWillDelayInterval by intPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.Mqtt.Will.DELAY_INTERVAL,
        0,
        min = 0,
        max = Int.MAX_VALUE
    )
    var mqttRestrictionsReceiveMaximum by intPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.Mqtt.Restrictions.RECEIVE_MAXIMUM,
        65_535,
        min = 0,
        max = 65_535,
    )
    var mqttRestrictionsSendMaximum by intPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.Mqtt.Restrictions.SEND_MAXIMUM,
        65_535,
        min = 0,
        max = 65_535,
    )
    var mqttRestrictionsMaximumPacketSize by intPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.Mqtt.Restrictions.MAXIMUM_PACKET_SIZE,
        268_435_460,
        min = 0,
        max = 268_435_460,
    )
    var mqttRestrictionsSendMaximumPacketSize by intPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.Mqtt.Restrictions.SEND_MAXIMUM_PACKET_SIZE,
        268_435_460,
        min = 0,
        max = 268_435_460,
    )
    var mqttRestrictionsTopicAliasMaximum by intPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.Mqtt.Restrictions.TOPIC_ALIAS_MAXIMUM,
        0,
        min = 0,
        max = 65_535,
    )
    var mqttRestrictionsSendTopicAliasMaximum by intPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.Mqtt.Restrictions.SEND_TOPIC_ALIAS_MAXIMUM,
        16,
        min = 0,
        max = 65_535,
    )
    var mqttRestrictionsRequestProblemInformation by booleanPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.Mqtt.Restrictions.REQUEST_PROBLEM_INFORMATION,
        true
    )
    var mqttRestrictionsRequestResponseInformation by booleanPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.Mqtt.Restrictions.REQUEST_RESPONSE_INFORMATION,
        false
    )

    fun exportJson(): JSONObject {
        val json = JSONObject().apply {
            put(UserSettingsKeys.WebContent.HOME_URL, homeUrl)
            put(UserSettingsKeys.WebContent.WEBSITE_BLACKLIST, websiteBlacklist)
            put(UserSettingsKeys.WebContent.WEBSITE_WHITELIST, websiteWhitelist)
            put(UserSettingsKeys.WebContent.WEBSITE_BOOKMARKS, websiteBookmarks)
            put(UserSettingsKeys.WebContent.ALLOW_LOCAL_FILES, allowLocalFiles)

            put(UserSettingsKeys.WebBrowsing.ALLOW_REFRESH, allowRefresh)
            put(UserSettingsKeys.WebBrowsing.ALLOW_PULL_TO_REFRESH, allowPullToRefresh)
            put(UserSettingsKeys.WebBrowsing.ALLOW_BACKWARDS_NAVIGATION, allowBackwardsNavigation)
            put(UserSettingsKeys.WebBrowsing.ALLOW_GO_HOME, allowGoHome)
            put(UserSettingsKeys.WebBrowsing.CLEAR_HISTORY_ON_HOME, clearHistoryOnHome)
            put(UserSettingsKeys.WebBrowsing.REPLACE_HISTORY_URL_ON_REDIRECT, replaceHistoryUrlOnRedirect)
            put(UserSettingsKeys.WebBrowsing.ALLOW_HISTORY_ACCESS, allowHistoryAccess)
            put(UserSettingsKeys.WebBrowsing.ALLOW_BOOKMARK_ACCESS, allowBookmarkAccess)
            put(UserSettingsKeys.WebBrowsing.ALLOW_OTHER_URL_SCHEMES, allowOtherUrlSchemes)
            put(UserSettingsKeys.WebBrowsing.ALLOW_DEFAULT_LONG_PRESS, allowDefaultLongPress)
            put(UserSettingsKeys.WebBrowsing.ALLOW_LINK_LONG_PRESS_CONTEXT_MENU, allowLinkLongPressContextMenu)
            put(UserSettingsKeys.WebBrowsing.OVERRIDE_URL_LOADING_BLOCK_ACTION, overrideUrlLoadingBlockAction.name)
            put(UserSettingsKeys.WebBrowsing.ADDRESS_BAR_ACTIONS, JSONArray(addressBarActions.map { it.name }))
            put(UserSettingsKeys.WebBrowsing.KIOSK_CONTROL_PANEL_REGION, kioskControlPanelRegion.name)
            put(UserSettingsKeys.WebBrowsing.KIOSK_CONTROL_PANEL_ACTIONS, JSONArray(kioskControlPanelActions.map { it.name }))
            put(UserSettingsKeys.WebBrowsing.SEARCH_PROVIDER_URL, searchProviderUrl)
            put(UserSettingsKeys.WebBrowsing.SEARCH_SUGGESTION_ENGINE, searchSuggestionEngine.name)

            put(UserSettingsKeys.WebEngine.ENABLE_JAVASCRIPT, enableJavaScript)
            put(UserSettingsKeys.WebEngine.ENABLE_DOM_STORAGE, enableDomStorage)
            put(UserSettingsKeys.WebEngine.ACCEPT_COOKIES, acceptCookies)
            put(UserSettingsKeys.WebEngine.ACCEPT_THIRD_PARTY_COOKIES, acceptThirdPartyCookies)
            put(UserSettingsKeys.WebEngine.CACHE_MODE, cacheMode.name)
            put(UserSettingsKeys.WebEngine.LAYOUT_ALGORITHM, layoutAlgorithm.algorithm.name)
            put(UserSettingsKeys.WebEngine.USER_AGENT, userAgent)
            put(UserSettingsKeys.WebEngine.USE_WIDE_VIEWPORT, useWideViewport)
            put(UserSettingsKeys.WebEngine.LOAD_WITH_OVERVIEW_MODE, loadWithOverviewMode)
            put(UserSettingsKeys.WebEngine.SUPPORT_ZOOM, supportZoom)
            put(UserSettingsKeys.WebEngine.BUILT_IN_ZOOM_CONTROLS, builtInZoomControls)
            put(UserSettingsKeys.WebEngine.DISPLAY_ZOOM_CONTROLS, displayZoomControls)
            put(UserSettingsKeys.WebEngine.INITIAL_SCALE, initialScale)
            put(UserSettingsKeys.WebEngine.ALLOW_FILE_ACCESS_FROM_FILE_URLS, allowFileAccessFromFileURLs)
            put(UserSettingsKeys.WebEngine.ALLOW_UNIVERSAL_ACCESS_FROM_FILE_URLS, allowUniversalAccessFromFileURLs)
            put(UserSettingsKeys.WebEngine.ALLOW_FILE_PICKER, allowFilePicker)
            put(UserSettingsKeys.WebEngine.MEDIA_PLAYBACK_REQUIRES_USER_GESTURE, mediaPlaybackRequiresUserGesture)
            put(UserSettingsKeys.WebEngine.SSL_ERROR_MODE, sslErrorMode.name)
            put(UserSettingsKeys.WebEngine.MIXED_CONTENT_MODE, mixedContentMode.name)
            put(UserSettingsKeys.WebEngine.OVER_SCROLL_MODE, overScrollMode.name)
            put(UserSettingsKeys.WebEngine.REQUEST_FOCUS_ON_PAGE_START, requestFocusOnPageStart)

            put(UserSettingsKeys.WebLifecycle.LOCK_ON_LAUNCH, lockOnLaunch)
            put(UserSettingsKeys.WebLifecycle.RESET_ON_LAUNCH, resetOnLaunch)
            put(UserSettingsKeys.WebLifecycle.RESET_ON_INACTIVITY_SECONDS, resetOnInactivitySeconds)
            put(UserSettingsKeys.WebLifecycle.DIM_SCREEN_ON_INACTIVITY_SECONDS, dimScreenOnInactivitySeconds)
            put(UserSettingsKeys.WebLifecycle.REFRESH_ON_LOADING_ERROR_INTERVAL_SECONDS, refreshOnLoadingErrorIntervalSeconds)

            put(UserSettingsKeys.Appearance.THEME, theme.name)
            put(UserSettingsKeys.Appearance.FLOATING_TOOLBAR_MODE, floatingToolbarMode.name)
            put(UserSettingsKeys.Appearance.WEBVIEW_INSET, webViewInset.name)
            put(UserSettingsKeys.Appearance.IMMERSIVE_MODE, immersiveMode.name)
            put(UserSettingsKeys.Appearance.BLOCKED_MESSAGE, blockedMessage)
            put(UserSettingsKeys.Appearance.CUSTOM_BLOCK_PAGE_HTML, customBlockPageHtml)
            put(UserSettingsKeys.Appearance.ADDRESS_BAR_MODE, addressBarMode.name)
            put(UserSettingsKeys.Appearance.ADDRESS_BAR_SIZE, addressBarSize.name)
            put(UserSettingsKeys.Appearance.ADDRESS_BAR_POSITION, addressBarPosition.name)

            put(UserSettingsKeys.Device.KEEP_SCREEN_ON, keepScreenOn)
            put(UserSettingsKeys.Device.DEVICE_ROTATION, rotation.name)
            put(UserSettingsKeys.Device.BRIGHTNESS, brightness)
            put(UserSettingsKeys.Device.ALLOW_CAMERA, allowCamera)
            put(UserSettingsKeys.Device.ALLOW_MICROPHONE, allowMicrophone)
            put(UserSettingsKeys.Device.ALLOW_LOCATION, allowLocation)
            put(UserSettingsKeys.Device.ALLOW_NOTIFICATIONS, allowNotifications)
            put(UserSettingsKeys.Device.BACK_BUTTON_HOLD_ACTION, backButtonHoldAction.name)
            put(UserSettingsKeys.Device.CUSTOM_UNLOCK_SHORTCUT, customUnlockShortcut)
            // put(UserSettingsKeys.Device.CUSTOM_AUTH_PASSWORD, customAuthPassword)
            put(UserSettingsKeys.Device.UNLOCK_AUTH_REQUIREMENT, unlockAuthRequirement.name)
            put(UserSettingsKeys.Device.BLOCK_SCREEN_CAPTURE, blockScreenCapture)
            put(UserSettingsKeys.Device.BLOCK_VOLUME_KEYS, blockVolumeKeys)

            put(UserSettingsKeys.Device.Owner.LockTaskFeature.HOME, lockTaskFeatureHome)
            put(UserSettingsKeys.Device.Owner.LockTaskFeature.OVERVIEW, lockTaskFeatureOverview)
            put(UserSettingsKeys.Device.Owner.LockTaskFeature.GLOBAL_ACTIONS, lockTaskFeatureGlobalActions)
            put(UserSettingsKeys.Device.Owner.LockTaskFeature.NOTIFICATIONS, lockTaskFeatureNotifications)
            put(UserSettingsKeys.Device.Owner.LockTaskFeature.SYSTEM_INFO, lockTaskFeatureSystemInfo)
            put(UserSettingsKeys.Device.Owner.LockTaskFeature.KEYGUARD, lockTaskFeatureKeyguard)
            put(UserSettingsKeys.Device.Owner.LockTaskFeature.BLOCK_ACTIVITY_START_IN_TASK, lockTaskFeatureBlockActivityStartInTask)
            put(UserSettingsKeys.Device.Owner.Dhizuku.REQUEST_PERMISSION_ON_LAUNCH, dhizukuRequestPermissionOnLaunch)

            put(UserSettingsKeys.JsScripts.APPLY_APP_THEME, applyAppTheme)
            put(UserSettingsKeys.JsScripts.APPLY_DESKTOP_VIEWPORT_WIDTH, applyDesktopViewportWidth)
            put(UserSettingsKeys.JsScripts.ENABLE_BATTERY_API, enableBatteryApi)
            put(UserSettingsKeys.JsScripts.ENABLE_BRIGHTNESS_API, enableBrightnessApi)
            put(UserSettingsKeys.JsScripts.CUSTOM_SCRIPT_ON_PAGE_START, customScriptOnPageStart)
            put(UserSettingsKeys.JsScripts.CUSTOM_SCRIPT_ON_PAGE_FINISH, customScriptOnPageFinish)

            put(UserSettingsKeys.Mqtt.ENABLED, mqttEnabled)
            put(UserSettingsKeys.Mqtt.USE_FOREGROUND_SERVICE, mqttUseForegroundService)
            put(UserSettingsKeys.Mqtt.Connection.SERVER_HOST, mqttServerHost)
            put(UserSettingsKeys.Mqtt.Connection.SERVER_PORT, mqttServerPort)
            put(UserSettingsKeys.Mqtt.Connection.CLIENT_ID, mqttClientId)
            put(UserSettingsKeys.Mqtt.Connection.USE_TLS, mqttUseTls)
            put(UserSettingsKeys.Mqtt.Connection.USERNAME, mqttUsername)
            // put(UserSettingsKeys.Mqtt.Connection.PASSWORD, mqttPassword)
            put(UserSettingsKeys.Mqtt.Connection.CLEAN_START, mqttCleanStart)
            put(UserSettingsKeys.Mqtt.Connection.KEEP_ALIVE, mqttKeepAlive)
            put(UserSettingsKeys.Mqtt.Connection.CONNECT_TIMEOUT, mqttConnectTimeout)
            put(UserSettingsKeys.Mqtt.Connection.SOCKET_CONNECT_TIMEOUT, mqttSocketConnectTimeout)
            put(UserSettingsKeys.Mqtt.Connection.AUTOMATIC_RECONNECT, mqttAutomaticReconnect)
            put(UserSettingsKeys.Mqtt.Connection.SESSION_EXPIRY_INTERVAL, mqttSessionExpiryInterval)
            put(UserSettingsKeys.Mqtt.Connection.USE_WEBSOCKET, mqttUseWebSocket)
            put(UserSettingsKeys.Mqtt.Connection.WEBSOCKET_SERVER_PATH, mqttWebSocketServerPath)

            put(UserSettingsKeys.Mqtt.Topics.Publish.Event.TOPIC, mqttPublishEventTopic)
            put(UserSettingsKeys.Mqtt.Topics.Publish.Event.QOS, mqttPublishEventQos.name)
            put(UserSettingsKeys.Mqtt.Topics.Publish.Event.RETAIN, mqttPublishEventRetain)
            put(UserSettingsKeys.Mqtt.Topics.Publish.Response.TOPIC, mqttPublishResponseTopic)
            put(UserSettingsKeys.Mqtt.Topics.Publish.Response.QOS, mqttPublishResponseQos.name)
            put(UserSettingsKeys.Mqtt.Topics.Publish.Response.RETAIN, mqttPublishResponseRetain)
            put(UserSettingsKeys.Mqtt.Topics.Subscribe.Command.TOPIC, mqttSubscribeCommandTopic)
            put(UserSettingsKeys.Mqtt.Topics.Subscribe.Command.QOS, mqttSubscribeCommandQos.name)
            put(UserSettingsKeys.Mqtt.Topics.Subscribe.Command.RETAIN_HANDLING, mqttSubscribeCommandRetainHandling.name)
            put(UserSettingsKeys.Mqtt.Topics.Subscribe.Command.RETAIN_AS_PUBLISHED, mqttSubscribeCommandRetainAsPublished)
            put(UserSettingsKeys.Mqtt.Topics.Subscribe.Settings.TOPIC, mqttSubscribeSettingsTopic)
            put(UserSettingsKeys.Mqtt.Topics.Subscribe.Settings.QOS, mqttSubscribeSettingsQos.name)
            put(UserSettingsKeys.Mqtt.Topics.Subscribe.Settings.RETAIN_HANDLING, mqttSubscribeSettingsRetainHandling.name)
            put(UserSettingsKeys.Mqtt.Topics.Subscribe.Settings.RETAIN_AS_PUBLISHED, mqttSubscribeSettingsRetainAsPublished)
            put(UserSettingsKeys.Mqtt.Topics.Subscribe.Request.TOPIC, mqttSubscribeRequestTopic)
            put(UserSettingsKeys.Mqtt.Topics.Subscribe.Request.QOS, mqttSubscribeRequestQos.name)
            put(UserSettingsKeys.Mqtt.Topics.Subscribe.Request.RETAIN_HANDLING, mqttSubscribeRequestRetainHandling.name)
            put(UserSettingsKeys.Mqtt.Topics.Subscribe.Request.RETAIN_AS_PUBLISHED, mqttSubscribeRequestRetainAsPublished)
            put(UserSettingsKeys.Mqtt.Will.TOPIC, mqttWillTopic)
            put(UserSettingsKeys.Mqtt.Will.QOS, mqttWillQos.name)
            put(UserSettingsKeys.Mqtt.Will.PAYLOAD, mqttWillPayload)
            put(UserSettingsKeys.Mqtt.Will.RETAIN, mqttWillRetain)
            put(UserSettingsKeys.Mqtt.Will.MESSAGE_EXPIRY_INTERVAL, mqttWillMessageExpiryInterval)
            put(UserSettingsKeys.Mqtt.Will.DELAY_INTERVAL, mqttWillDelayInterval)
            put(UserSettingsKeys.Mqtt.Restrictions.RECEIVE_MAXIMUM, mqttRestrictionsReceiveMaximum)
            put(UserSettingsKeys.Mqtt.Restrictions.SEND_MAXIMUM, mqttRestrictionsSendMaximum)
            put(UserSettingsKeys.Mqtt.Restrictions.MAXIMUM_PACKET_SIZE, mqttRestrictionsMaximumPacketSize)
            put(UserSettingsKeys.Mqtt.Restrictions.SEND_MAXIMUM_PACKET_SIZE, mqttRestrictionsSendMaximumPacketSize)
            put(UserSettingsKeys.Mqtt.Restrictions.TOPIC_ALIAS_MAXIMUM, mqttRestrictionsTopicAliasMaximum)
            put(UserSettingsKeys.Mqtt.Restrictions.SEND_TOPIC_ALIAS_MAXIMUM, mqttRestrictionsSendTopicAliasMaximum)
            put(UserSettingsKeys.Mqtt.Restrictions.REQUEST_PROBLEM_INFORMATION, mqttRestrictionsRequestProblemInformation)
            put(UserSettingsKeys.Mqtt.Restrictions.REQUEST_RESPONSE_INFORMATION, mqttRestrictionsRequestResponseInformation)
        }
        return json
    }

    fun importJson(jsonStr: String): Boolean {
        return try {
            val json = JSONObject(jsonStr)

            homeUrl = json.optString(UserSettingsKeys.WebContent.HOME_URL, homeUrl)
            websiteBlacklist = json.optString(UserSettingsKeys.WebContent.WEBSITE_BLACKLIST, websiteBlacklist)
            websiteWhitelist = json.optString(UserSettingsKeys.WebContent.WEBSITE_WHITELIST, websiteWhitelist)
            websiteBookmarks = json.optString(UserSettingsKeys.WebContent.WEBSITE_BOOKMARKS, websiteBookmarks)
            allowLocalFiles = json.optBoolean(UserSettingsKeys.WebContent.ALLOW_LOCAL_FILES, allowLocalFiles)

            allowRefresh = json.optBoolean(UserSettingsKeys.WebBrowsing.ALLOW_REFRESH, allowRefresh)
            allowPullToRefresh = json.optBoolean(UserSettingsKeys.WebBrowsing.ALLOW_PULL_TO_REFRESH, allowPullToRefresh)
            allowBackwardsNavigation = json.optBoolean(UserSettingsKeys.WebBrowsing.ALLOW_BACKWARDS_NAVIGATION, allowBackwardsNavigation)
            allowGoHome = json.optBoolean(UserSettingsKeys.WebBrowsing.ALLOW_GO_HOME, allowGoHome)
            clearHistoryOnHome = json.optBoolean(UserSettingsKeys.WebBrowsing.CLEAR_HISTORY_ON_HOME, clearHistoryOnHome)
            replaceHistoryUrlOnRedirect = json.optBoolean(UserSettingsKeys.WebBrowsing.REPLACE_HISTORY_URL_ON_REDIRECT, replaceHistoryUrlOnRedirect)
            allowHistoryAccess = json.optBoolean(UserSettingsKeys.WebBrowsing.ALLOW_HISTORY_ACCESS, allowHistoryAccess)
            allowBookmarkAccess = json.optBoolean(UserSettingsKeys.WebBrowsing.ALLOW_BOOKMARK_ACCESS, allowBookmarkAccess)
            allowOtherUrlSchemes = json.optBoolean(UserSettingsKeys.WebBrowsing.ALLOW_OTHER_URL_SCHEMES, allowOtherUrlSchemes)
            allowDefaultLongPress = json.optBoolean(UserSettingsKeys.WebBrowsing.ALLOW_DEFAULT_LONG_PRESS, allowDefaultLongPress)
            allowLinkLongPressContextMenu = json.optBoolean(UserSettingsKeys.WebBrowsing.ALLOW_LINK_LONG_PRESS_CONTEXT_MENU, allowLinkLongPressContextMenu)
            overrideUrlLoadingBlockAction = OverrideUrlLoadingBlockActionOption.fromString(
                json.optString(UserSettingsKeys.WebBrowsing.OVERRIDE_URL_LOADING_BLOCK_ACTION, overrideUrlLoadingBlockAction.name)
            )
            json.optJSONArray(UserSettingsKeys.WebBrowsing.ADDRESS_BAR_ACTIONS)?.let { arr ->
                addressBarActions = WebviewControlActionOption.parseFromJsonArray(arr)
            }
            kioskControlPanelRegion = KioskControlPanelRegionOption.fromString(
                json.optString(UserSettingsKeys.WebBrowsing.KIOSK_CONTROL_PANEL_REGION, kioskControlPanelRegion.name)
            )
            json.optJSONArray(UserSettingsKeys.WebBrowsing.KIOSK_CONTROL_PANEL_ACTIONS)?.let { arr ->
                kioskControlPanelActions = WebviewControlActionOption.parseFromJsonArray(arr)
            }
            searchProviderUrl = json.optString(UserSettingsKeys.WebBrowsing.SEARCH_PROVIDER_URL, searchProviderUrl)
            searchSuggestionEngine = SearchSuggestionEngineOption.fromString(
                json.optString(UserSettingsKeys.WebBrowsing.SEARCH_SUGGESTION_ENGINE, searchSuggestionEngine.name)
            )

            enableJavaScript = json.optBoolean(UserSettingsKeys.WebEngine.ENABLE_JAVASCRIPT, enableJavaScript)
            enableDomStorage = json.optBoolean(UserSettingsKeys.WebEngine.ENABLE_DOM_STORAGE, enableDomStorage)
            acceptCookies = json.optBoolean(UserSettingsKeys.WebEngine.ACCEPT_COOKIES, acceptCookies)
            acceptThirdPartyCookies = json.optBoolean(UserSettingsKeys.WebEngine.ACCEPT_THIRD_PARTY_COOKIES, acceptThirdPartyCookies)
            cacheMode = CacheModeOption.fromString(
                json.optString(UserSettingsKeys.WebEngine.CACHE_MODE,  cacheMode.name)
            )
            layoutAlgorithm = LayoutAlgorithmOption.fromString(
                json.optString(UserSettingsKeys.WebEngine.LAYOUT_ALGORITHM, layoutAlgorithm.algorithm.name)
            )
            userAgent = json.optString(UserSettingsKeys.WebEngine.USER_AGENT, userAgent)
            useWideViewport = json.optBoolean(UserSettingsKeys.WebEngine.USE_WIDE_VIEWPORT, useWideViewport)
            loadWithOverviewMode = json.optBoolean(UserSettingsKeys.WebEngine.LOAD_WITH_OVERVIEW_MODE, loadWithOverviewMode)
            supportZoom = json.optBoolean(UserSettingsKeys.WebEngine.SUPPORT_ZOOM, supportZoom)
            builtInZoomControls = json.optBoolean(UserSettingsKeys.WebEngine.BUILT_IN_ZOOM_CONTROLS, builtInZoomControls)
            displayZoomControls = json.optBoolean(UserSettingsKeys.WebEngine.DISPLAY_ZOOM_CONTROLS, displayZoomControls)
            initialScale = json.optInt(UserSettingsKeys.WebEngine.INITIAL_SCALE, initialScale)
            allowFileAccessFromFileURLs = json.optBoolean(UserSettingsKeys.WebEngine.ALLOW_FILE_ACCESS_FROM_FILE_URLS, allowFileAccessFromFileURLs)
            allowUniversalAccessFromFileURLs = json.optBoolean(UserSettingsKeys.WebEngine.ALLOW_UNIVERSAL_ACCESS_FROM_FILE_URLS, allowUniversalAccessFromFileURLs)
            allowFilePicker = json.optBoolean(UserSettingsKeys.WebEngine.ALLOW_FILE_PICKER, allowFilePicker)
            mediaPlaybackRequiresUserGesture = json.optBoolean(UserSettingsKeys.WebEngine.MEDIA_PLAYBACK_REQUIRES_USER_GESTURE, mediaPlaybackRequiresUserGesture)
            sslErrorMode = SslErrorModeOption.fromString(
                json.optString(UserSettingsKeys.WebEngine.SSL_ERROR_MODE, sslErrorMode.name)
            )
            mixedContentMode = MixedContentModeOption.fromString(
                json.optString(UserSettingsKeys.WebEngine.MIXED_CONTENT_MODE, mixedContentMode.name)
            )
            overScrollMode = OverScrollModeOption.fromString(
                json.optString(UserSettingsKeys.WebEngine.OVER_SCROLL_MODE, overScrollMode.name)
            )
            requestFocusOnPageStart = json.optBoolean(UserSettingsKeys.WebEngine.REQUEST_FOCUS_ON_PAGE_START, requestFocusOnPageStart)

            lockOnLaunch = json.optBoolean(UserSettingsKeys.WebLifecycle.LOCK_ON_LAUNCH, lockOnLaunch)
            resetOnLaunch = json.optBoolean(UserSettingsKeys.WebLifecycle.RESET_ON_LAUNCH, resetOnLaunch)
            resetOnInactivitySeconds = json.optInt(UserSettingsKeys.WebLifecycle.RESET_ON_INACTIVITY_SECONDS, resetOnInactivitySeconds)
            dimScreenOnInactivitySeconds = json.optInt(UserSettingsKeys.WebLifecycle.DIM_SCREEN_ON_INACTIVITY_SECONDS, dimScreenOnInactivitySeconds)
            refreshOnLoadingErrorIntervalSeconds = json.optInt(UserSettingsKeys.WebLifecycle.REFRESH_ON_LOADING_ERROR_INTERVAL_SECONDS, refreshOnLoadingErrorIntervalSeconds)

            theme = ThemeOption.fromString(json.optString(UserSettingsKeys.Appearance.THEME, theme.name))
            floatingToolbarMode = FloatingToolbarModeOption.fromString(json.optString(UserSettingsKeys.Appearance.FLOATING_TOOLBAR_MODE, floatingToolbarMode.name))
            webViewInset = WebViewInsetOption.fromString(json.optString(UserSettingsKeys.Appearance.WEBVIEW_INSET, webViewInset.name))
            immersiveMode = ImmersiveModeOption.fromString(
                json.optString(UserSettingsKeys.Appearance.IMMERSIVE_MODE, immersiveMode.name)
            )
            blockedMessage = json.optString(UserSettingsKeys.Appearance.BLOCKED_MESSAGE, blockedMessage)
            customBlockPageHtml = json.optString(UserSettingsKeys.Appearance.CUSTOM_BLOCK_PAGE_HTML, customBlockPageHtml)
            addressBarMode = AddressBarModeOption.fromString(json.optString(UserSettingsKeys.Appearance.ADDRESS_BAR_MODE, addressBarMode.name))
            addressBarSize = AddressBarSizeOption.fromString(json.optString(UserSettingsKeys.Appearance.ADDRESS_BAR_SIZE, addressBarSize.name))
            addressBarPosition = AddressBarPositionOption.fromString(json.optString(UserSettingsKeys.Appearance.ADDRESS_BAR_POSITION, addressBarPosition.name))

            keepScreenOn = json.optBoolean(UserSettingsKeys.Device.KEEP_SCREEN_ON, keepScreenOn)
            rotation = DeviceRotationOption.fromString(json.optString(UserSettingsKeys.Device.DEVICE_ROTATION, rotation.name))
            brightness = json.optInt(UserSettingsKeys.Device.BRIGHTNESS, brightness)
            allowCamera = json.optBoolean(UserSettingsKeys.Device.ALLOW_CAMERA, allowCamera)
            allowMicrophone = json.optBoolean(UserSettingsKeys.Device.ALLOW_MICROPHONE, allowMicrophone)
            allowLocation = json.optBoolean(UserSettingsKeys.Device.ALLOW_LOCATION, allowLocation)
            allowNotifications = json.optBoolean(UserSettingsKeys.Device.ALLOW_NOTIFICATIONS, allowNotifications)
            backButtonHoldAction = BackButtonHoldActionOption.fromString(
                json.optString(UserSettingsKeys.Device.BACK_BUTTON_HOLD_ACTION, backButtonHoldAction.name)
            )
            customUnlockShortcut = json.optString(UserSettingsKeys.Device.CUSTOM_UNLOCK_SHORTCUT, customUnlockShortcut)
            customAuthPassword = json.optString(UserSettingsKeys.Device.CUSTOM_AUTH_PASSWORD, customAuthPassword)
            unlockAuthRequirement = UnlockAuthRequirementOption.fromString(
                json.optString(UserSettingsKeys.Device.UNLOCK_AUTH_REQUIREMENT, unlockAuthRequirement.name)
            )
            blockScreenCapture = json.optBoolean(UserSettingsKeys.Device.BLOCK_SCREEN_CAPTURE, blockScreenCapture)
            blockVolumeKeys = json.optBoolean(UserSettingsKeys.Device.BLOCK_VOLUME_KEYS, blockVolumeKeys)

            lockTaskFeatureHome = json.optBoolean(UserSettingsKeys.Device.Owner.LockTaskFeature.HOME, lockTaskFeatureHome)
            lockTaskFeatureOverview = json.optBoolean(UserSettingsKeys.Device.Owner.LockTaskFeature.OVERVIEW, lockTaskFeatureOverview)
            lockTaskFeatureGlobalActions = json.optBoolean(UserSettingsKeys.Device.Owner.LockTaskFeature.GLOBAL_ACTIONS, lockTaskFeatureGlobalActions)
            lockTaskFeatureNotifications = json.optBoolean(UserSettingsKeys.Device.Owner.LockTaskFeature.NOTIFICATIONS, lockTaskFeatureNotifications)
            lockTaskFeatureSystemInfo = json.optBoolean(UserSettingsKeys.Device.Owner.LockTaskFeature.SYSTEM_INFO, lockTaskFeatureSystemInfo)
            lockTaskFeatureKeyguard = json.optBoolean(UserSettingsKeys.Device.Owner.LockTaskFeature.KEYGUARD, lockTaskFeatureKeyguard)
            lockTaskFeatureBlockActivityStartInTask = json.optBoolean(UserSettingsKeys.Device.Owner.LockTaskFeature.BLOCK_ACTIVITY_START_IN_TASK, lockTaskFeatureBlockActivityStartInTask)
            dhizukuRequestPermissionOnLaunch = json.optBoolean(UserSettingsKeys.Device.Owner.Dhizuku.REQUEST_PERMISSION_ON_LAUNCH, dhizukuRequestPermissionOnLaunch)

            applyAppTheme = json.optBoolean(UserSettingsKeys.JsScripts.APPLY_APP_THEME, applyAppTheme)
            applyDesktopViewportWidth = json.optInt(UserSettingsKeys.JsScripts.APPLY_DESKTOP_VIEWPORT_WIDTH, applyDesktopViewportWidth)
            enableBatteryApi = json.optBoolean(UserSettingsKeys.JsScripts.ENABLE_BATTERY_API, enableBatteryApi)
            enableBrightnessApi = json.optBoolean(UserSettingsKeys.JsScripts.ENABLE_BRIGHTNESS_API, enableBrightnessApi)
            customScriptOnPageStart = json.optString(UserSettingsKeys.JsScripts.CUSTOM_SCRIPT_ON_PAGE_START, customScriptOnPageStart)
            customScriptOnPageFinish = json.optString(UserSettingsKeys.JsScripts.CUSTOM_SCRIPT_ON_PAGE_FINISH, customScriptOnPageFinish)

            mqttEnabled = json.optBoolean(UserSettingsKeys.Mqtt.ENABLED, mqttEnabled)
            mqttUseForegroundService = json.optBoolean(UserSettingsKeys.Mqtt.USE_FOREGROUND_SERVICE, mqttUseForegroundService)
            mqttServerHost = json.optString(UserSettingsKeys.Mqtt.Connection.SERVER_HOST, mqttServerHost)
            mqttServerPort = json.optInt(UserSettingsKeys.Mqtt.Connection.SERVER_PORT, mqttServerPort)
            mqttClientId = json.optString(UserSettingsKeys.Mqtt.Connection.CLIENT_ID, mqttClientId)
            mqttUseTls = json.optBoolean(UserSettingsKeys.Mqtt.Connection.USE_TLS, mqttUseTls)
            mqttUsername = json.optString(UserSettingsKeys.Mqtt.Connection.USERNAME, mqttUsername)
            mqttPassword = json.optString(UserSettingsKeys.Mqtt.Connection.PASSWORD, mqttPassword)
            mqttCleanStart = json.optBoolean(UserSettingsKeys.Mqtt.Connection.CLEAN_START, mqttCleanStart)
            mqttKeepAlive = json.optInt(UserSettingsKeys.Mqtt.Connection.KEEP_ALIVE, mqttKeepAlive)
            mqttConnectTimeout = json.optInt(UserSettingsKeys.Mqtt.Connection.CONNECT_TIMEOUT, mqttConnectTimeout)
            mqttSocketConnectTimeout = json.optInt(UserSettingsKeys.Mqtt.Connection.SOCKET_CONNECT_TIMEOUT, mqttSocketConnectTimeout)
            mqttAutomaticReconnect = json.optBoolean(UserSettingsKeys.Mqtt.Connection.AUTOMATIC_RECONNECT, mqttAutomaticReconnect)
            mqttSessionExpiryInterval = json.optInt(UserSettingsKeys.Mqtt.Connection.SESSION_EXPIRY_INTERVAL, mqttSessionExpiryInterval)
            mqttUseWebSocket = json.optBoolean(UserSettingsKeys.Mqtt.Connection.USE_WEBSOCKET, mqttUseWebSocket)
            mqttWebSocketServerPath = json.optString(UserSettingsKeys.Mqtt.Connection.WEBSOCKET_SERVER_PATH, mqttWebSocketServerPath)

            mqttPublishEventTopic = json.optString(UserSettingsKeys.Mqtt.Topics.Publish.Event.TOPIC, mqttPublishEventTopic)
            mqttPublishEventQos = MqttQosOption.fromString(
                json.optString(UserSettingsKeys.Mqtt.Topics.Publish.Event.QOS, mqttPublishEventQos.name)
            )
            mqttPublishEventRetain = json.optBoolean(UserSettingsKeys.Mqtt.Topics.Publish.Event.RETAIN, mqttPublishEventRetain)
            mqttPublishResponseTopic = json.optString(UserSettingsKeys.Mqtt.Topics.Publish.Response.TOPIC, mqttPublishResponseTopic)
            mqttPublishResponseQos = MqttQosOption.fromString(
                json.optString(UserSettingsKeys.Mqtt.Topics.Publish.Response.QOS, mqttPublishResponseQos.name)
            )
            mqttPublishResponseRetain = json.optBoolean(UserSettingsKeys.Mqtt.Topics.Publish.Response.RETAIN, mqttPublishResponseRetain)
            mqttSubscribeCommandTopic = json.optString(UserSettingsKeys.Mqtt.Topics.Subscribe.Command.TOPIC, mqttSubscribeCommandTopic)
            mqttSubscribeCommandQos = MqttQosOption.fromString(
                json.optString(UserSettingsKeys.Mqtt.Topics.Subscribe.Command.QOS, mqttSubscribeCommandQos.name)
            )
            mqttSubscribeCommandRetainHandling = MqttRetainHandlingOption.fromString(
                json.optString(UserSettingsKeys.Mqtt.Topics.Subscribe.Command.RETAIN_HANDLING, mqttSubscribeCommandRetainHandling.name)
            )
            mqttSubscribeCommandRetainAsPublished = json.optBoolean(UserSettingsKeys.Mqtt.Topics.Subscribe.Command.RETAIN_AS_PUBLISHED, mqttSubscribeCommandRetainAsPublished)
            mqttSubscribeSettingsTopic = json.optString(UserSettingsKeys.Mqtt.Topics.Subscribe.Settings.TOPIC, mqttSubscribeSettingsTopic)
            mqttSubscribeSettingsQos = MqttQosOption.fromString(
                json.optString(UserSettingsKeys.Mqtt.Topics.Subscribe.Settings.QOS, mqttSubscribeSettingsQos.name)
            )
            mqttSubscribeSettingsRetainHandling = MqttRetainHandlingOption.fromString(
                json.optString(UserSettingsKeys.Mqtt.Topics.Subscribe.Settings.RETAIN_HANDLING, mqttSubscribeSettingsRetainHandling.name)
            )
            mqttSubscribeSettingsRetainAsPublished = json.optBoolean(UserSettingsKeys.Mqtt.Topics.Subscribe.Settings.RETAIN_AS_PUBLISHED, mqttSubscribeSettingsRetainAsPublished)
            mqttSubscribeRequestTopic = json.optString(UserSettingsKeys.Mqtt.Topics.Subscribe.Request.TOPIC, mqttSubscribeRequestTopic)
            mqttSubscribeRequestQos = MqttQosOption.fromString(
                json.optString(UserSettingsKeys.Mqtt.Topics.Subscribe.Request.QOS, mqttSubscribeRequestQos.name)
            )
            mqttSubscribeRequestRetainHandling = MqttRetainHandlingOption.fromString(
                json.optString(UserSettingsKeys.Mqtt.Topics.Subscribe.Request.RETAIN_HANDLING, mqttSubscribeRequestRetainHandling.name)
            )
            mqttSubscribeRequestRetainAsPublished = json.optBoolean(UserSettingsKeys.Mqtt.Topics.Subscribe.Request.RETAIN_AS_PUBLISHED, mqttSubscribeRequestRetainAsPublished)
            mqttWillTopic = json.optString(UserSettingsKeys.Mqtt.Will.TOPIC, mqttWillTopic)
            mqttWillQos = MqttQosOption.fromString(
                json.optString(UserSettingsKeys.Mqtt.Will.QOS, mqttWillQos.name)
            )
            mqttWillPayload = json.optString(UserSettingsKeys.Mqtt.Will.PAYLOAD, mqttWillPayload)
            mqttWillRetain = json.optBoolean(UserSettingsKeys.Mqtt.Will.RETAIN, mqttWillRetain)
            mqttWillMessageExpiryInterval = json.optInt(UserSettingsKeys.Mqtt.Will.MESSAGE_EXPIRY_INTERVAL, mqttWillMessageExpiryInterval)
            mqttWillDelayInterval = json.optInt(UserSettingsKeys.Mqtt.Will.DELAY_INTERVAL, mqttWillDelayInterval)
            mqttRestrictionsReceiveMaximum = json.optInt(UserSettingsKeys.Mqtt.Restrictions.RECEIVE_MAXIMUM, mqttRestrictionsReceiveMaximum)
            mqttRestrictionsSendMaximum = json.optInt(UserSettingsKeys.Mqtt.Restrictions.SEND_MAXIMUM, mqttRestrictionsSendMaximum)
            mqttRestrictionsMaximumPacketSize = json.optInt(UserSettingsKeys.Mqtt.Restrictions.MAXIMUM_PACKET_SIZE, mqttRestrictionsMaximumPacketSize)
            mqttRestrictionsSendMaximumPacketSize = json.optInt(UserSettingsKeys.Mqtt.Restrictions.SEND_MAXIMUM_PACKET_SIZE, mqttRestrictionsSendMaximumPacketSize)
            mqttRestrictionsTopicAliasMaximum = json.optInt(UserSettingsKeys.Mqtt.Restrictions.TOPIC_ALIAS_MAXIMUM, mqttRestrictionsTopicAliasMaximum)
            mqttRestrictionsSendTopicAliasMaximum = json.optInt(UserSettingsKeys.Mqtt.Restrictions.SEND_TOPIC_ALIAS_MAXIMUM, mqttRestrictionsSendTopicAliasMaximum)
            mqttRestrictionsRequestProblemInformation = json.optBoolean(UserSettingsKeys.Mqtt.Restrictions.REQUEST_PROBLEM_INFORMATION, mqttRestrictionsRequestProblemInformation)
            mqttRestrictionsRequestResponseInformation = json.optBoolean(UserSettingsKeys.Mqtt.Restrictions.REQUEST_RESPONSE_INFORMATION, mqttRestrictionsRequestResponseInformation)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun exportBase64(): String {
        return Base64.encodeToString(exportJson().toString().toByteArray(), Base64.NO_WRAP)
    }

    fun importBase64(base64: String): Boolean {
        return try {
            val decoded = String(Base64.decode(base64, Base64.NO_WRAP))
            importJson(decoded)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
