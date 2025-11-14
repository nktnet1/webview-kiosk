package uk.nktnet.webviewkiosk.config

import android.content.Context
import android.content.RestrictionsManager
import android.content.SharedPreferences
import android.util.Base64
import org.json.JSONObject
import uk.nktnet.webviewkiosk.config.option.*
import uk.nktnet.webviewkiosk.utils.booleanPref
import uk.nktnet.webviewkiosk.utils.stringEnumPref
import uk.nktnet.webviewkiosk.utils.intPref
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
    var kioskControlPanelRegion by stringEnumPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.WebBrowsing.KIOSK_CONTROL_PANEL_REGION,
        KioskControlPanelRegionOption.TOP_LEFT.name,
        fromString = KioskControlPanelRegionOption::fromString
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
    var useWideViewPort by booleanPref(
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
        0
    )
    var refreshOnLoadingErrorIntervalSeconds by intPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.WebLifecycle.REFRESH_ON_LOADING_ERROR_INTERVAL_SECONDS,
        0
    )

    // Appearance
    var theme by stringEnumPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.Appearance.THEME,
        ThemeOption.SYSTEM.name,
        fromString = ThemeOption::fromString
    )
    var addressBarMode by stringEnumPref(
        getRestrictions,
        prefs,
        UserSettingsKeys.Appearance.ADDRESS_BAR_MODE,
        AddressBarModeOption.HIDDEN_WHEN_LOCKED.name,
        fromString = AddressBarModeOption::fromString
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
        WebViewInset.SystemBars.name,
        fromString = WebViewInset::fromString
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
        "This site is blocked by ${Constants.APP_NAME}."
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
        0
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

    fun exportToBase64(): String {
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
            put(UserSettingsKeys.WebEngine.CACHE_MODE, cacheMode.name)
            put(UserSettingsKeys.WebEngine.LAYOUT_ALGORITHM, layoutAlgorithm.algorithm.name)
            put(UserSettingsKeys.WebEngine.USER_AGENT, userAgent)
            put(UserSettingsKeys.WebEngine.USE_WIDE_VIEWPORT, useWideViewPort)
            put(UserSettingsKeys.WebEngine.LOAD_WITH_OVERVIEW_MODE, loadWithOverviewMode)
            put(UserSettingsKeys.WebEngine.BUILT_IN_ZOOM_CONTROLS, builtInZoomControls)
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
            put(UserSettingsKeys.Appearance.FLOATING_TOOLBAR_MODE, floatingToolbarMode.name)
            put(UserSettingsKeys.Appearance.WEBVIEW_INSET, webViewInset.name)
            put(UserSettingsKeys.Appearance.IMMERSIVE_MODE, immersiveMode.name)
            put(UserSettingsKeys.Appearance.BLOCKED_MESSAGE, blockedMessage)

            put(UserSettingsKeys.Device.KEEP_SCREEN_ON, keepScreenOn)
            put(UserSettingsKeys.Device.DEVICE_ROTATION, rotation.label)
            put(UserSettingsKeys.Device.ALLOW_CAMERA, allowCamera)
            put(UserSettingsKeys.Device.ALLOW_MICROPHONE, allowMicrophone)
            put(UserSettingsKeys.Device.ALLOW_LOCATION, allowLocation)
            put(UserSettingsKeys.Device.BACK_BUTTON_HOLD_ACTION, backButtonHoldAction.name)
            put(UserSettingsKeys.Device.CUSTOM_UNLOCK_SHORTCUT, customUnlockShortcut)
            // put(UserSettingsKeys.Device.CUSTOM_AUTH_PASSWORD, customAuthPassword)
            put(UserSettingsKeys.Device.UNLOCK_AUTH_REQUIREMENT, unlockAuthRequirement.name)

            put(UserSettingsKeys.JsScripts.APPLY_APP_THEME, applyAppTheme)
            put(UserSettingsKeys.JsScripts.APPLY_DESKTOP_VIEWPORT_WIDTH, applyDesktopViewportWidth)
            put(UserSettingsKeys.JsScripts.ENABLE_BATTERY_API, enableBatteryApi)
            put(UserSettingsKeys.JsScripts.ENABLE_BRIGHTNESS_API, enableBrightnessApi)
            put(UserSettingsKeys.JsScripts.CUSTOM_SCRIPT_ON_PAGE_START, customScriptOnPageStart)
            put(UserSettingsKeys.JsScripts.CUSTOM_SCRIPT_ON_PAGE_FINISH, customScriptOnPageFinish)
        }
        return Base64.encodeToString(json.toString().toByteArray(), Base64.NO_WRAP)
    }

    fun importFromBase64(base64: String): Boolean {
        return try {
            val json = JSONObject(String(Base64.decode(base64, Base64.NO_WRAP)))

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
            cacheMode = CacheModeOption.fromString(
                json.optString(UserSettingsKeys.WebEngine.CACHE_MODE,  cacheMode.name)
            )
            layoutAlgorithm = LayoutAlgorithmOption.fromString(
                json.optString(UserSettingsKeys.WebEngine.LAYOUT_ALGORITHM, layoutAlgorithm.algorithm.name)
            )
            userAgent = json.optString(UserSettingsKeys.WebEngine.USER_AGENT, userAgent)
            useWideViewPort = json.optBoolean(UserSettingsKeys.WebEngine.USE_WIDE_VIEWPORT, useWideViewPort)
            loadWithOverviewMode = json.optBoolean(UserSettingsKeys.WebEngine.LOAD_WITH_OVERVIEW_MODE, loadWithOverviewMode)
            builtInZoomControls = json.optBoolean(UserSettingsKeys.WebEngine.BUILT_IN_ZOOM_CONTROLS, builtInZoomControls)
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
            addressBarMode = AddressBarModeOption.fromString(json.optString(UserSettingsKeys.Appearance.ADDRESS_BAR_MODE, addressBarMode.name))
            floatingToolbarMode = FloatingToolbarModeOption.fromString(json.optString(UserSettingsKeys.Appearance.FLOATING_TOOLBAR_MODE, floatingToolbarMode.name))
            webViewInset = WebViewInset.fromString(json.optString(UserSettingsKeys.Appearance.WEBVIEW_INSET, webViewInset.name))
            immersiveMode = ImmersiveModeOption.fromString(
                json.optString(UserSettingsKeys.Appearance.IMMERSIVE_MODE, immersiveMode.name)
            )
            blockedMessage = json.optString(UserSettingsKeys.Appearance.BLOCKED_MESSAGE, blockedMessage)

            keepScreenOn = json.optBoolean(UserSettingsKeys.Device.KEEP_SCREEN_ON, keepScreenOn)
            rotation = DeviceRotationOption.fromString(json.optString(UserSettingsKeys.Device.DEVICE_ROTATION, rotation.label))
            allowCamera = json.optBoolean(UserSettingsKeys.Device.ALLOW_CAMERA, allowCamera)
            allowMicrophone = json.optBoolean(UserSettingsKeys.Device.ALLOW_MICROPHONE, allowMicrophone)
            allowLocation = json.optBoolean(UserSettingsKeys.Device.ALLOW_LOCATION, allowLocation)
            backButtonHoldAction = BackButtonHoldActionOption.fromString(
                json.optString(UserSettingsKeys.Device.BACK_BUTTON_HOLD_ACTION, backButtonHoldAction.name)
            )
            customUnlockShortcut = json.optString(UserSettingsKeys.Device.CUSTOM_UNLOCK_SHORTCUT, customUnlockShortcut)
            customAuthPassword = json.optString(UserSettingsKeys.Device.CUSTOM_AUTH_PASSWORD, customAuthPassword)
            unlockAuthRequirement = UnlockAuthRequirementOption.fromString(
                json.optString(UserSettingsKeys.Device.UNLOCK_AUTH_REQUIREMENT, unlockAuthRequirement.name)
            )

            applyAppTheme = json.optBoolean(UserSettingsKeys.JsScripts.APPLY_APP_THEME, applyAppTheme)
            applyDesktopViewportWidth = json.optInt(UserSettingsKeys.JsScripts.APPLY_DESKTOP_VIEWPORT_WIDTH, applyDesktopViewportWidth)
            enableBatteryApi = json.optBoolean(UserSettingsKeys.JsScripts.ENABLE_BATTERY_API, enableBatteryApi)
            enableBrightnessApi = json.optBoolean(UserSettingsKeys.JsScripts.ENABLE_BRIGHTNESS_API, enableBrightnessApi)
            customScriptOnPageStart = json.optString(UserSettingsKeys.JsScripts.CUSTOM_SCRIPT_ON_PAGE_START, customScriptOnPageStart)
            customScriptOnPageFinish = json.optString(UserSettingsKeys.JsScripts.CUSTOM_SCRIPT_ON_PAGE_FINISH, customScriptOnPageFinish)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
