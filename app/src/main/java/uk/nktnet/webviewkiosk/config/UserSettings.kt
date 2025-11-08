package uk.nktnet.webviewkiosk.config

import android.content.Context
import android.content.RestrictionsManager
import android.content.SharedPreferences
import android.util.Base64
import org.json.JSONObject
import uk.nktnet.webviewkiosk.config.option.*
import uk.nktnet.webviewkiosk.utils.booleanPref
import uk.nktnet.webviewkiosk.utils.intEnumPref
import uk.nktnet.webviewkiosk.utils.stringEnumPref
import uk.nktnet.webviewkiosk.utils.intPref
import uk.nktnet.webviewkiosk.utils.stringPref
import uk.nktnet.webviewkiosk.utils.stringPrefOptional

class UserSettings(val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(UserSettingsKeys.PREFS_NAME, Context.MODE_PRIVATE)
    private val restrictions = (context.getSystemService(Context.RESTRICTIONS_SERVICE) as? RestrictionsManager)
        ?.applicationRestrictions

    fun isRestricted(key: String): Boolean =
        restrictions?.containsKey(key) == true

    // Web Content
    var homeUrl by stringPref(
        restrictions,
        prefs,
        UserSettingsKeys.WebContent.HOME_URL,
        Constants.WEBSITE_URL
    )
    var websiteBlacklist by stringPrefOptional(
        restrictions,
        prefs,
        UserSettingsKeys.WebContent.WEBSITE_BLACKLIST
    )
    var websiteWhitelist by stringPrefOptional(
        restrictions,
        prefs,
        UserSettingsKeys.WebContent.WEBSITE_WHITELIST
    )
    var websiteBookmarks by stringPrefOptional(
        restrictions,
        prefs,
        UserSettingsKeys.WebContent.WEBSITE_BOOKMARKS
    )
    var allowLocalFiles by booleanPref(
        restrictions,
        prefs,
        UserSettingsKeys.WebContent.ALLOW_LOCAL_FILES,
        true
    )

    // Web Browsing
    var allowRefresh by booleanPref(
        restrictions,
        prefs,
        UserSettingsKeys.WebBrowsing.ALLOW_REFRESH,
        true
    )
    var allowPullToRefresh by booleanPref(
        restrictions,
        prefs,
        UserSettingsKeys.WebBrowsing.ALLOW_PULL_TO_REFRESH,
        true
    )
    var allowBackwardsNavigation by booleanPref(
        restrictions,
        prefs,
        UserSettingsKeys.WebBrowsing.ALLOW_BACKWARDS_NAVIGATION,
        true
    )
    var allowGoHome by booleanPref(
        restrictions,
        prefs,
        UserSettingsKeys.WebBrowsing.ALLOW_GO_HOME,
        true
    )
    var clearHistoryOnHome by booleanPref(
        restrictions,
        prefs,
        UserSettingsKeys.WebBrowsing.CLEAR_HISTORY_ON_HOME,
        false
    )
    var replaceHistoryUrlOnRedirect by booleanPref(
        restrictions,
        prefs,
        UserSettingsKeys.WebBrowsing.REPLACE_HISTORY_URL_ON_REDIRECT,
        true
    )
    var allowHistoryAccess by booleanPref(
        restrictions,
        prefs,
        UserSettingsKeys.WebBrowsing.ALLOW_HISTORY_ACCESS,
        true
    )
    var allowBookmarkAccess by booleanPref(
        restrictions,
        prefs,
        UserSettingsKeys.WebBrowsing.ALLOW_BOOKMARK_ACCESS,
        true
    )
    var allowOtherUrlSchemes by booleanPref(
        restrictions,
        prefs,
        UserSettingsKeys.WebBrowsing.ALLOW_OTHER_URL_SCHEMES,
        false
    )
    var allowDefaultLongPress by booleanPref(
        restrictions,
        prefs,
        UserSettingsKeys.WebBrowsing.ALLOW_DEFAULT_LONG_PRESS,
        true
    )
    var allowLinkLongPressContextMenu by booleanPref(
        restrictions,
        prefs,
        UserSettingsKeys.WebBrowsing.ALLOW_LINK_LONG_PRESS_CONTEXT_MENU,
        true
    )
    var kioskControlPanelRegion by stringEnumPref(
        restrictions,
        prefs,
        UserSettingsKeys.WebBrowsing.KIOSK_CONTROL_PANEL_REGION,
        KioskControlPanelRegionOption.TOP_LEFT.name,
        fromString = KioskControlPanelRegionOption::fromString
    )
    var searchProviderUrl by stringPref(
        restrictions,
        prefs,
        UserSettingsKeys.WebBrowsing.SEARCH_PROVIDER_URL,
        Constants.DEFAULT_SEARCH_PROVIDER_URL
    )
    var searchSuggestionEngine by stringEnumPref(
        restrictions,
        prefs,
        UserSettingsKeys.WebBrowsing.SEARCH_SUGGESTION_ENGINE,
        SearchSuggestionEngineOption.NONE.name,
        fromString = SearchSuggestionEngineOption::fromString
    )

    // Web Engine
    var enableJavaScript by booleanPref(
        restrictions,
        prefs,
        UserSettingsKeys.WebEngine.ENABLE_JAVASCRIPT,
        true
    )
    var enableDomStorage by booleanPref(
        restrictions,
        prefs,
        UserSettingsKeys.WebEngine.ENABLE_DOM_STORAGE,
        true
    )
    var acceptCookies by booleanPref(
        restrictions,
        prefs,
        UserSettingsKeys.WebEngine.ACCEPT_COOKIES,
        true
    )
    var acceptThirdPartyCookies by booleanPref(
        restrictions,
        prefs,
        UserSettingsKeys.WebEngine.ACCEPT_THIRD_PARTY_COOKIES,
        false
    )
    var cacheMode by intEnumPref(
        restrictions,
        prefs,
        UserSettingsKeys.WebEngine.CACHE_MODE,
        CacheModeOption.DEFAULT.mode,
        fromInt = CacheModeOption::fromInt
    )
    var layoutAlgorithm by stringEnumPref(
        restrictions,
        prefs,
        UserSettingsKeys.WebEngine.LAYOUT_ALGORITHM,
        LayoutAlgorithmOption.NORMAL.name,
        fromString = LayoutAlgorithmOption::fromString
    )
    var userAgent by stringPrefOptional(restrictions, prefs, UserSettingsKeys.WebEngine.USER_AGENT)
    var useWideViewPort by booleanPref(
        restrictions,
        prefs,
        UserSettingsKeys.WebEngine.USE_WIDE_VIEWPORT,
        true
    )
    var loadWithOverviewMode by booleanPref(
        restrictions,
        prefs,
        UserSettingsKeys.WebEngine.LOAD_WITH_OVERVIEW_MODE,
        true
    )
    var enableZoom by booleanPref(restrictions, prefs, UserSettingsKeys.WebEngine.ENABLE_ZOOM, true)
    var displayZoomControls by booleanPref(
        restrictions,
        prefs,
        UserSettingsKeys.WebEngine.DISPLAY_ZOOM_CONTROLS,
        false
    )
    var allowFileAccessFromFileURLs by booleanPref(
        restrictions,
        prefs,
        UserSettingsKeys.WebEngine.ALLOW_FILE_ACCESS_FROM_FILE_URLS,
        false
    )
    var allowUniversalAccessFromFileURLs by booleanPref(
        restrictions,
        prefs,
        UserSettingsKeys.WebEngine.ALLOW_UNIVERSAL_ACCESS_FROM_FILE_URLS,
        false
    )
    var mediaPlaybackRequiresUserGesture by booleanPref(
        restrictions,
        prefs,
        UserSettingsKeys.WebEngine.MEDIA_PLAYBACK_REQUIRES_USER_GESTURE,
        true
    )
    var sslErrorMode by stringEnumPref(
        restrictions,
        prefs,
        UserSettingsKeys.WebEngine.SSL_ERROR_MODE,
        SslErrorModeOption.BLOCK.name,
        fromString = SslErrorModeOption::fromString
    )

    // Web Lifecycle
    var lockOnLaunch by booleanPref(
        restrictions,
        prefs,
        UserSettingsKeys.WebLifecycle.LOCK_ON_LAUNCH,
        false
    )
    var resetOnLaunch by booleanPref(
        restrictions,
        prefs,
        UserSettingsKeys.WebLifecycle.RESET_ON_LAUNCH,
        false
    )
    var resetOnInactivitySeconds by intPref(
        restrictions,
        prefs,
        UserSettingsKeys.WebLifecycle.RESET_ON_INACTIVITY_SECONDS,
        0
    )
    var refreshOnLoadingErrorIntervalSeconds by intPref(
        restrictions,
        prefs,
        UserSettingsKeys.WebLifecycle.REFRESH_ON_LOADING_ERROR_INTERVAL_SECONDS,
        0
    )

    // Appearance
    var theme by stringEnumPref(
        restrictions,
        prefs,
        UserSettingsKeys.Appearance.THEME,
        ThemeOption.SYSTEM.name,
        fromString = ThemeOption::fromString
    )
    var addressBarMode by stringEnumPref(
        restrictions,
        prefs,
        UserSettingsKeys.Appearance.ADDRESS_BAR_MODE,
        AddressBarOption.HIDDEN_WHEN_LOCKED.name,
        fromString = AddressBarOption::fromString
    )
    var webViewInset by stringEnumPref(
        restrictions,
        prefs,
        UserSettingsKeys.Appearance.WEBVIEW_INSET,
        WebViewInset.SystemBars.name,
        fromString = WebViewInset::fromString
    )
    var immersiveMode by stringEnumPref(
        restrictions,
        prefs,
        UserSettingsKeys.Appearance.IMMERSIVE_MODE,
        ImmersiveModeOption.ONLY_WHEN_LOCKED.name,
        fromString = ImmersiveModeOption::fromString
    )
    var blockedMessage by stringPref(
        restrictions,
        prefs,
        UserSettingsKeys.Appearance.BLOCKED_MESSAGE,
        "This site is blocked by ${Constants.APP_NAME}."
    )

    // Device
    var keepScreenOn by booleanPref(
        restrictions,
        prefs,
        UserSettingsKeys.Device.KEEP_SCREEN_ON,
        false
    )
    var deviceRotation by stringEnumPref(
        restrictions,
        prefs,
        UserSettingsKeys.Device.DEVICE_ROTATION,
        DeviceRotationOption.AUTO.name,
        fromString = DeviceRotationOption::fromString
    )
    var allowCamera by booleanPref(restrictions, prefs, UserSettingsKeys.Device.ALLOW_CAMERA, false)
    var allowMicrophone by booleanPref(
        restrictions,
        prefs,
        UserSettingsKeys.Device.ALLOW_MICROPHONE,
        false
    )
    var allowLocation by booleanPref(
        restrictions,
        prefs,
        UserSettingsKeys.Device.ALLOW_LOCATION,
        false
    )
    var backButtonHoldAction by stringEnumPref(
        restrictions,
        prefs,
        UserSettingsKeys.Device.BACK_BUTTON_HOLD_ACTION,
        BackButtonHoldActionOption.OPEN_KIOSK_CONTROL_PANEL.name,
        fromString = BackButtonHoldActionOption::fromString
    )
    var customUnlockShortcut by stringPrefOptional(
        restrictions,
        prefs,
        UserSettingsKeys.Device.CUSTOM_UNLOCK_SHORTCUT
    )
    var unlockAuthRequirement by stringEnumPref(
        restrictions,
        prefs,
        UserSettingsKeys.Device.UNLOCK_AUTH_REQUIREMENT,
        UnlockAuthRequirementOption.DEFAULT.name,
        fromString = UnlockAuthRequirementOption::fromString
    )

    // JS Scripts
    var applyAppTheme by booleanPref(
        restrictions,
        prefs,
        UserSettingsKeys.JsScripts.APPLY_APP_THEME,
        true
    )
    var applyDesktopViewportWidth by intPref(
        restrictions,
        prefs,
        UserSettingsKeys.JsScripts.APPLY_DESKTOP_VIEWPORT_WIDTH,
        0
    )
    var customScriptOnPageStart by stringPrefOptional(
        restrictions,
        prefs,
        UserSettingsKeys.JsScripts.CUSTOM_SCRIPT_ON_PAGE_START
    )
    var customScriptOnPageFinish by stringPrefOptional(
        restrictions,
        prefs,
        UserSettingsKeys.JsScripts.CUSTOM_SCRIPT_ON_PAGE_FINISH
    )

    // MQTT
    var mqttEnabled by booleanPref(
        restrictions,
        prefs,
        UserSettingsKeys.Mqtt.ENABLED,
        false
    )
    var mqttServerHost by stringPref(
        restrictions,
        prefs,
        UserSettingsKeys.Mqtt.Connection.SERVER_HOST,
        "broker.hivemq.com"
    )
    var mqttServerPort by intPref(
        restrictions,
        prefs,
        UserSettingsKeys.Mqtt.Connection.SERVER_PORT,
        8883
    )
    var mqttClientId by stringPrefOptional(
        restrictions,
        prefs,
        UserSettingsKeys.Mqtt.Connection.CLIENT_ID,
    )
    var mqttUsername by stringPrefOptional(
        restrictions,
        prefs,
        UserSettingsKeys.Mqtt.Connection.USERNAME
    )
    var mqttPassword by stringPrefOptional(
        restrictions,
        prefs,
        UserSettingsKeys.Mqtt.Connection.PASSWORD
    )
    var mqttUseTls by booleanPref(
        restrictions,
        prefs,
        UserSettingsKeys.Mqtt.Connection.USE_TLS,
        true
    )
    var mqttCleanStart by booleanPref(
        restrictions,
        prefs,
        UserSettingsKeys.Mqtt.Connection.CLEAN_START,
        true
    )
    var mqttKeepAlive by intPref(
        restrictions,
        prefs,
        UserSettingsKeys.Mqtt.Connection.KEEP_ALIVE,
        60
    )
    var mqttConnectTimeout by intPref(
        restrictions,
        prefs,
        UserSettingsKeys.Mqtt.Connection.CONNECT_TIMEOUT,
        30
    )
    var mqttAutomaticReconnect by booleanPref(
        restrictions,
        prefs,
        UserSettingsKeys.Mqtt.Connection.CLEAN_START,
        true
    )
    var mqttPublishEventTopic by stringPref(
        restrictions,
        prefs,
        UserSettingsKeys.Mqtt.Topics.Publish.Event.TOPIC,
        "webviewkiosk/publish/event"
    )
    var mqttPublishEventQos by intEnumPref(
        restrictions,
        prefs,
        UserSettingsKeys.Mqtt.Topics.Publish.Event.QOS,
        MqttQosOption.AT_MOST_ONCE.code,
        fromInt = MqttQosOption::fromCode
    )
    var mqttPublishEventRetain by booleanPref(
        restrictions,
        prefs,
        UserSettingsKeys.Mqtt.Topics.Publish.Event.RETAIN,
        false,
    )
    var mqttSubscribeCommandTopic by stringPref(
        restrictions,
        prefs,
        UserSettingsKeys.Mqtt.Topics.Subscribe.Command.TOPIC,
        "webviewkiosk/subscribe/command"
    )
    var mqttSubscribeCommandQos by intEnumPref(
        restrictions,
        prefs,
        UserSettingsKeys.Mqtt.Topics.Subscribe.Command.QOS,
        MqttQosOption.AT_MOST_ONCE.code,
        fromInt = MqttQosOption::fromCode
    )
    var mqttSubscribeCommandRetainHandling by intEnumPref(
        restrictions,
        prefs,
        UserSettingsKeys.Mqtt.Topics.Subscribe.Command.RETAIN_HANDLING,
        MqttRetainHandlingOption.DO_NOT_SEND.code,
        fromInt = MqttRetainHandlingOption::fromCode
    )
    var mqttSubscribeCommandRetainAsPublished by booleanPref(
        restrictions,
        prefs,
        UserSettingsKeys.Mqtt.Topics.Subscribe.Command.RETAIN_AS_PUBLISHED,
        false,
    )
    var mqttSubscribeSettingsTopic by stringPref(
        restrictions,
        prefs,
        UserSettingsKeys.Mqtt.Topics.Subscribe.Settings.TOPIC,
        "webviewkiosk/subscribe/settings"
    )
    var mqttSubscribeSettingsQos by intEnumPref(
        restrictions,
        prefs,
        UserSettingsKeys.Mqtt.Topics.Subscribe.Settings.QOS,
        MqttQosOption.AT_MOST_ONCE.code,
        fromInt = MqttQosOption::fromCode
    )
    var mqttSubscribeSettingsRetainHandling by intEnumPref(
        restrictions,
        prefs,
        UserSettingsKeys.Mqtt.Topics.Subscribe.Command.RETAIN_HANDLING,
        MqttRetainHandlingOption.DO_NOT_SEND.code,
        fromInt = MqttRetainHandlingOption::fromCode
    )
    var mqttSubscribeSettingsRetainAsPublished by booleanPref(
        restrictions,
        prefs,
        UserSettingsKeys.Mqtt.Topics.Subscribe.Settings.RETAIN_AS_PUBLISHED,
        false,
    )

    var mqttWillTopic by stringPrefOptional(
        restrictions,
        prefs,
        UserSettingsKeys.Mqtt.Will.TOPIC
    )
    var mqttWillQos by intEnumPref(
        restrictions,
        prefs,
        UserSettingsKeys.Mqtt.Will.QOS,
        MqttQosOption.AT_MOST_ONCE.code,
        fromInt = MqttQosOption::fromCode
    )
    var mqttWillPayload by stringPrefOptional(
        restrictions,
        prefs,
        UserSettingsKeys.Mqtt.Will.PAYLOAD
    )
    var mqttWillRetain by booleanPref(
        restrictions,
        prefs,
        UserSettingsKeys.Mqtt.Will.RETAIN,
        false
    )
    var mqttWillMessageExpiryInterval by intPref(
        restrictions,
        prefs,
        UserSettingsKeys.Mqtt.Will.MESSAGE_EXPIRY_INTERVAL,
        0
    )
    var mqttWillDelayInterval by intPref(
        restrictions,
        prefs,
        UserSettingsKeys.Mqtt.Will.DELAY_INTERVAL,
        0
    )

    fun exportJson(): String {
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
            put(UserSettingsKeys.WebBrowsing.KIOSK_CONTROL_PANEL_REGION, kioskControlPanelRegion.name)
            put(UserSettingsKeys.WebBrowsing.SEARCH_PROVIDER_URL, searchProviderUrl)
            put(UserSettingsKeys.WebBrowsing.SEARCH_SUGGESTION_ENGINE, searchSuggestionEngine.name)

            put(UserSettingsKeys.WebEngine.ENABLE_JAVASCRIPT, enableJavaScript)
            put(UserSettingsKeys.WebEngine.ENABLE_DOM_STORAGE, enableDomStorage)
            put(UserSettingsKeys.WebEngine.ACCEPT_COOKIES, acceptCookies)
            put(UserSettingsKeys.WebEngine.ACCEPT_THIRD_PARTY_COOKIES, acceptThirdPartyCookies)
            put(UserSettingsKeys.WebEngine.CACHE_MODE, cacheMode.mode)
            put(UserSettingsKeys.WebEngine.LAYOUT_ALGORITHM, layoutAlgorithm.algorithm.name)
            put(UserSettingsKeys.WebEngine.USER_AGENT, userAgent)
            put(UserSettingsKeys.WebEngine.USE_WIDE_VIEWPORT, useWideViewPort)
            put(UserSettingsKeys.WebEngine.LOAD_WITH_OVERVIEW_MODE, loadWithOverviewMode)
            put(UserSettingsKeys.WebEngine.ENABLE_ZOOM, enableZoom)
            put(UserSettingsKeys.WebEngine.DISPLAY_ZOOM_CONTROLS, displayZoomControls)
            put(UserSettingsKeys.WebEngine.ALLOW_FILE_ACCESS_FROM_FILE_URLS, allowFileAccessFromFileURLs)
            put(UserSettingsKeys.WebEngine.ALLOW_UNIVERSAL_ACCESS_FROM_FILE_URLS, allowUniversalAccessFromFileURLs)
            put(UserSettingsKeys.WebEngine.MEDIA_PLAYBACK_REQUIRES_USER_GESTURE, mediaPlaybackRequiresUserGesture)
            put(UserSettingsKeys.WebEngine.SSL_ERROR_MODE, sslErrorMode.name)

            put(UserSettingsKeys.WebLifecycle.LOCK_ON_LAUNCH, lockOnLaunch)
            put(UserSettingsKeys.WebLifecycle.RESET_ON_LAUNCH, resetOnLaunch)
            put(UserSettingsKeys.WebLifecycle.RESET_ON_INACTIVITY_SECONDS, resetOnInactivitySeconds)
            put(UserSettingsKeys.WebLifecycle.REFRESH_ON_LOADING_ERROR_INTERVAL_SECONDS, refreshOnLoadingErrorIntervalSeconds)

            put(UserSettingsKeys.Appearance.THEME, theme.name)
            put(UserSettingsKeys.Appearance.ADDRESS_BAR_MODE, addressBarMode.name)
            put(UserSettingsKeys.Appearance.WEBVIEW_INSET, webViewInset.name)
            put(UserSettingsKeys.Appearance.IMMERSIVE_MODE, immersiveMode.name)
            put(UserSettingsKeys.Appearance.BLOCKED_MESSAGE, blockedMessage)

            put(UserSettingsKeys.Device.KEEP_SCREEN_ON, keepScreenOn)
            put(UserSettingsKeys.Device.DEVICE_ROTATION, deviceRotation.degrees)
            put(UserSettingsKeys.Device.ALLOW_CAMERA, allowCamera)
            put(UserSettingsKeys.Device.ALLOW_MICROPHONE, allowMicrophone)
            put(UserSettingsKeys.Device.ALLOW_LOCATION, allowLocation)
            put(UserSettingsKeys.Device.BACK_BUTTON_HOLD_ACTION, backButtonHoldAction.name)
            put(UserSettingsKeys.Device.CUSTOM_UNLOCK_SHORTCUT, customUnlockShortcut)
            put(UserSettingsKeys.Device.UNLOCK_AUTH_REQUIREMENT, unlockAuthRequirement.name)

            put(UserSettingsKeys.JsScripts.APPLY_APP_THEME, applyAppTheme)
            put(UserSettingsKeys.JsScripts.APPLY_DESKTOP_VIEWPORT_WIDTH, applyDesktopViewportWidth)
            put(UserSettingsKeys.JsScripts.CUSTOM_SCRIPT_ON_PAGE_START, customScriptOnPageStart)
            put(UserSettingsKeys.JsScripts.CUSTOM_SCRIPT_ON_PAGE_FINISH, customScriptOnPageFinish)

            put(UserSettingsKeys.Mqtt.ENABLED, mqttEnabled)
            put(UserSettingsKeys.Mqtt.Connection.SERVER_HOST, mqttServerHost)
            put(UserSettingsKeys.Mqtt.Connection.SERVER_PORT, mqttServerPort)
            put(UserSettingsKeys.Mqtt.Connection.CLIENT_ID, mqttClientId)
            put(UserSettingsKeys.Mqtt.Connection.USERNAME, mqttUsername)
            // put(UserSettingsKeys.Mqtt.Connection.PASSWORD, mqttPassword)
            put(UserSettingsKeys.Mqtt.Connection.USE_TLS, mqttUseTls)
            put(UserSettingsKeys.Mqtt.Connection.CLEAN_START, mqttCleanStart)
            put(UserSettingsKeys.Mqtt.Connection.KEEP_ALIVE, mqttKeepAlive)
            put(UserSettingsKeys.Mqtt.Connection.CONNECT_TIMEOUT, mqttConnectTimeout)
            put(UserSettingsKeys.Mqtt.Connection.AUTOMATIC_RECONNECT, mqttAutomaticReconnect)
            put(UserSettingsKeys.Mqtt.Topics.Publish.Event.TOPIC, mqttPublishEventTopic)
            put(UserSettingsKeys.Mqtt.Topics.Publish.Event.QOS, mqttPublishEventQos.code)
            put(UserSettingsKeys.Mqtt.Topics.Publish.Event.RETAIN, mqttPublishEventRetain)
            put(UserSettingsKeys.Mqtt.Topics.Subscribe.Command.TOPIC, mqttSubscribeCommandTopic)
            put(UserSettingsKeys.Mqtt.Topics.Subscribe.Command.QOS, mqttSubscribeCommandQos.code)
            put(UserSettingsKeys.Mqtt.Topics.Subscribe.Command.RETAIN_HANDLING, mqttSubscribeCommandRetainHandling.code)
            put(UserSettingsKeys.Mqtt.Topics.Subscribe.Command.RETAIN_AS_PUBLISHED, mqttSubscribeCommandRetainAsPublished)
            put(UserSettingsKeys.Mqtt.Topics.Subscribe.Command.RETAIN_AS_PUBLISHED, mqttSubscribeCommandRetainAsPublished)
            put(UserSettingsKeys.Mqtt.Topics.Subscribe.Settings.TOPIC, mqttSubscribeSettingsTopic)
            put(UserSettingsKeys.Mqtt.Topics.Subscribe.Settings.QOS, mqttSubscribeSettingsQos.code)
            put(UserSettingsKeys.Mqtt.Topics.Subscribe.Settings.RETAIN_HANDLING, mqttSubscribeSettingsRetainHandling.code)
            put(UserSettingsKeys.Mqtt.Topics.Subscribe.Settings.RETAIN_AS_PUBLISHED, mqttSubscribeSettingsRetainAsPublished)
            put(UserSettingsKeys.Mqtt.Will.TOPIC, mqttWillTopic)
            put(UserSettingsKeys.Mqtt.Will.QOS, mqttWillQos.code)
            put(UserSettingsKeys.Mqtt.Will.PAYLOAD, mqttWillPayload)
            put(UserSettingsKeys.Mqtt.Will.RETAIN, mqttWillRetain)
            put(UserSettingsKeys.Mqtt.Will.MESSAGE_EXPIRY_INTERVAL, mqttWillMessageExpiryInterval)
            put(UserSettingsKeys.Mqtt.Will.DELAY_INTERVAL, mqttWillDelayInterval)
        }
        return json.toString()
    }

    fun exportBase64(): String {
        return Base64.encodeToString(exportJson().toByteArray(), Base64.NO_WRAP)
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
            kioskControlPanelRegion = KioskControlPanelRegionOption.fromString(
                json.optString(UserSettingsKeys.WebBrowsing.KIOSK_CONTROL_PANEL_REGION, kioskControlPanelRegion.name)
            )
            searchProviderUrl = json.optString(UserSettingsKeys.WebBrowsing.SEARCH_PROVIDER_URL, searchProviderUrl)
            searchSuggestionEngine = SearchSuggestionEngineOption.fromString(
                json.optString(UserSettingsKeys.WebBrowsing.SEARCH_SUGGESTION_ENGINE, searchSuggestionEngine.name)
            )

            enableJavaScript = json.optBoolean(UserSettingsKeys.WebEngine.ENABLE_JAVASCRIPT, enableJavaScript)
            enableDomStorage = json.optBoolean(UserSettingsKeys.WebEngine.ENABLE_DOM_STORAGE, enableDomStorage)
            acceptCookies = json.optBoolean(UserSettingsKeys.WebEngine.ACCEPT_COOKIES, acceptCookies)
            acceptThirdPartyCookies = json.optBoolean(UserSettingsKeys.WebEngine.ACCEPT_THIRD_PARTY_COOKIES, acceptThirdPartyCookies)
            cacheMode = CacheModeOption.fromInt(json.optInt(UserSettingsKeys.WebEngine.CACHE_MODE, cacheMode.mode))
            layoutAlgorithm = LayoutAlgorithmOption.fromString(
                json.optString(UserSettingsKeys.WebEngine.LAYOUT_ALGORITHM, layoutAlgorithm.algorithm.name)
            )
            userAgent = json.optString(UserSettingsKeys.WebEngine.USER_AGENT, userAgent)
            useWideViewPort = json.optBoolean(UserSettingsKeys.WebEngine.USE_WIDE_VIEWPORT, useWideViewPort)
            loadWithOverviewMode = json.optBoolean(UserSettingsKeys.WebEngine.LOAD_WITH_OVERVIEW_MODE, loadWithOverviewMode)
            enableZoom = json.optBoolean(UserSettingsKeys.WebEngine.ENABLE_ZOOM, enableZoom)
            displayZoomControls = json.optBoolean(UserSettingsKeys.WebEngine.DISPLAY_ZOOM_CONTROLS, displayZoomControls)
            allowFileAccessFromFileURLs = json.optBoolean(UserSettingsKeys.WebEngine.ALLOW_FILE_ACCESS_FROM_FILE_URLS, allowFileAccessFromFileURLs)
            allowUniversalAccessFromFileURLs = json.optBoolean(UserSettingsKeys.WebEngine.ALLOW_UNIVERSAL_ACCESS_FROM_FILE_URLS, allowUniversalAccessFromFileURLs)
            mediaPlaybackRequiresUserGesture = json.optBoolean(UserSettingsKeys.WebEngine.MEDIA_PLAYBACK_REQUIRES_USER_GESTURE, mediaPlaybackRequiresUserGesture)
            sslErrorMode = SslErrorModeOption.fromString(
                json.optString(UserSettingsKeys.WebEngine.SSL_ERROR_MODE, sslErrorMode.name)
            )

            lockOnLaunch = json.optBoolean(UserSettingsKeys.WebLifecycle.LOCK_ON_LAUNCH, lockOnLaunch)
            resetOnLaunch = json.optBoolean(UserSettingsKeys.WebLifecycle.RESET_ON_LAUNCH, resetOnLaunch)
            resetOnInactivitySeconds = json.optInt(UserSettingsKeys.WebLifecycle.RESET_ON_INACTIVITY_SECONDS, resetOnInactivitySeconds)
            refreshOnLoadingErrorIntervalSeconds = json.optInt(UserSettingsKeys.WebLifecycle.REFRESH_ON_LOADING_ERROR_INTERVAL_SECONDS, refreshOnLoadingErrorIntervalSeconds)

            theme = ThemeOption.fromString(json.optString(UserSettingsKeys.Appearance.THEME, theme.name))
            addressBarMode = AddressBarOption.fromString(json.optString(UserSettingsKeys.Appearance.ADDRESS_BAR_MODE, addressBarMode.name))
            webViewInset = WebViewInset.fromString(json.optString(UserSettingsKeys.Appearance.WEBVIEW_INSET, webViewInset.name))
            immersiveMode = ImmersiveModeOption.fromString(
                json.optString(UserSettingsKeys.Appearance.IMMERSIVE_MODE, immersiveMode.name)
            )
            blockedMessage = json.optString(UserSettingsKeys.Appearance.BLOCKED_MESSAGE, blockedMessage)

            keepScreenOn = json.optBoolean(UserSettingsKeys.Device.KEEP_SCREEN_ON, keepScreenOn)
            deviceRotation = DeviceRotationOption.fromString(json.optString(UserSettingsKeys.Device.DEVICE_ROTATION, deviceRotation.degrees))
            allowCamera = json.optBoolean(UserSettingsKeys.Device.ALLOW_CAMERA, allowCamera)
            allowMicrophone = json.optBoolean(UserSettingsKeys.Device.ALLOW_MICROPHONE, allowMicrophone)
            allowLocation = json.optBoolean(UserSettingsKeys.Device.ALLOW_LOCATION, allowLocation)
            backButtonHoldAction = BackButtonHoldActionOption.fromString(
                json.optString(UserSettingsKeys.Device.BACK_BUTTON_HOLD_ACTION, backButtonHoldAction.name)
            )
            customUnlockShortcut = json.optString(UserSettingsKeys.Device.CUSTOM_UNLOCK_SHORTCUT, customUnlockShortcut)
            unlockAuthRequirement = UnlockAuthRequirementOption.fromString(
                json.optString(UserSettingsKeys.Device.UNLOCK_AUTH_REQUIREMENT, unlockAuthRequirement.name)
            )

            applyAppTheme = json.optBoolean(UserSettingsKeys.JsScripts.APPLY_APP_THEME, applyAppTheme)
            applyDesktopViewportWidth = json.optInt(UserSettingsKeys.JsScripts.APPLY_DESKTOP_VIEWPORT_WIDTH, applyDesktopViewportWidth)
            customScriptOnPageStart = json.optString(UserSettingsKeys.JsScripts.CUSTOM_SCRIPT_ON_PAGE_START, customScriptOnPageStart)
            customScriptOnPageFinish = json.optString(UserSettingsKeys.JsScripts.CUSTOM_SCRIPT_ON_PAGE_FINISH, customScriptOnPageFinish)

            mqttEnabled = json.optBoolean(UserSettingsKeys.Mqtt.ENABLED, mqttEnabled)
            mqttServerHost = json.optString(UserSettingsKeys.Mqtt.Connection.SERVER_HOST, mqttServerHost)
            mqttServerPort = json.optInt(UserSettingsKeys.Mqtt.Connection.SERVER_PORT, mqttServerPort)
            mqttClientId = json.optString(UserSettingsKeys.Mqtt.Connection.CLIENT_ID, mqttClientId)
            mqttUsername = json.optString(UserSettingsKeys.Mqtt.Connection.USERNAME, mqttUsername)
            mqttPassword = json.optString(UserSettingsKeys.Mqtt.Connection.PASSWORD, mqttPassword)
            mqttUseTls = json.optBoolean(UserSettingsKeys.Mqtt.Connection.USE_TLS, mqttUseTls)
            mqttCleanStart = json.optBoolean(UserSettingsKeys.Mqtt.Connection.CLEAN_START, mqttCleanStart)
            mqttKeepAlive = json.optInt(UserSettingsKeys.Mqtt.Connection.KEEP_ALIVE, mqttKeepAlive)
            mqttConnectTimeout = json.optInt(UserSettingsKeys.Mqtt.Connection.CONNECT_TIMEOUT, mqttConnectTimeout)
            mqttAutomaticReconnect = json.optBoolean(UserSettingsKeys.Mqtt.Connection.AUTOMATIC_RECONNECT, mqttAutomaticReconnect)
            mqttPublishEventTopic = json.optString(UserSettingsKeys.Mqtt.Topics.Publish.Event.TOPIC, mqttPublishEventTopic)
            mqttPublishEventQos = MqttQosOption.fromCode(
                json.optInt(UserSettingsKeys.Mqtt.Topics.Publish.Event.QOS, mqttPublishEventQos.code)
            )
            mqttPublishEventRetain = json.optBoolean(UserSettingsKeys.Mqtt.Topics.Publish.Event.RETAIN, mqttPublishEventRetain)
            mqttSubscribeCommandTopic = json.optString(UserSettingsKeys.Mqtt.Topics.Subscribe.Command.TOPIC, mqttSubscribeCommandTopic)
            mqttSubscribeCommandQos = MqttQosOption.fromCode(
                json.optInt(UserSettingsKeys.Mqtt.Topics.Subscribe.Command.QOS, mqttSubscribeCommandQos.code)
            )
            mqttSubscribeCommandRetainHandling = MqttRetainHandlingOption.fromCode(
                json.optInt(UserSettingsKeys.Mqtt.Topics.Subscribe.Command.RETAIN_HANDLING, mqttSubscribeCommandRetainHandling.code)
            )
            mqttSubscribeCommandRetainAsPublished = json.optBoolean(UserSettingsKeys.Mqtt.Topics.Subscribe.Command.RETAIN_AS_PUBLISHED, mqttSubscribeCommandRetainAsPublished)
            mqttSubscribeSettingsTopic = json.optString(UserSettingsKeys.Mqtt.Topics.Subscribe.Settings.TOPIC, mqttSubscribeSettingsTopic)
            mqttSubscribeSettingsQos = MqttQosOption.fromCode(
                json.optInt(UserSettingsKeys.Mqtt.Topics.Subscribe.Settings.QOS, mqttSubscribeSettingsQos.code)
            )
            mqttSubscribeSettingsRetainHandling = MqttRetainHandlingOption.fromCode(
                json.optInt(UserSettingsKeys.Mqtt.Topics.Subscribe.Settings.RETAIN_HANDLING, mqttSubscribeSettingsRetainHandling.code)
            )
            mqttSubscribeSettingsRetainAsPublished = json.optBoolean(UserSettingsKeys.Mqtt.Topics.Subscribe.Settings.RETAIN_AS_PUBLISHED, mqttSubscribeSettingsRetainAsPublished)
            mqttWillTopic = json.optString(UserSettingsKeys.Mqtt.Will.TOPIC, mqttWillTopic)
            mqttWillQos = MqttQosOption.fromCode(
                json.optInt(UserSettingsKeys.Mqtt.Will.QOS, mqttWillQos.code)
            )
            mqttWillPayload = json.optString(UserSettingsKeys.Mqtt.Will.PAYLOAD, mqttWillPayload)
            mqttWillRetain = json.optBoolean(UserSettingsKeys.Mqtt.Will.RETAIN, mqttWillRetain)
            mqttWillMessageExpiryInterval = json.optInt(UserSettingsKeys.Mqtt.Will.MESSAGE_EXPIRY_INTERVAL, mqttWillMessageExpiryInterval)
            mqttWillDelayInterval = json.optInt(UserSettingsKeys.Mqtt.Will.DELAY_INTERVAL, mqttWillDelayInterval)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
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
