package com.nktnet.webview_kiosk.config

import android.content.Context
import android.content.RestrictionsManager
import android.content.SharedPreferences
import android.util.Base64
import org.json.JSONObject
import com.nktnet.webview_kiosk.config.option.*
import com.nktnet.webview_kiosk.utils.booleanPref
import com.nktnet.webview_kiosk.utils.intEnumPref
import com.nktnet.webview_kiosk.utils.stringEnumPref
import com.nktnet.webview_kiosk.utils.intPref
import com.nktnet.webview_kiosk.utils.stringPref
import com.nktnet.webview_kiosk.utils.stringPrefOptional

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
    var allowLinkLongPressContextMenu by booleanPref(
        restrictions,
        prefs,
        UserSettingsKeys.WebBrowsing.ALLOW_LINK_LONG_PRESS_CONTEXT_MENU,
        true
    )
    var allowKioskControlPanel by stringEnumPref(
        restrictions,
        prefs,
        UserSettingsKeys.WebBrowsing.ALLOW_KIOSK_CONTROL_PANEL,
        KioskControlPanelOption.TOP_LEFT.name,
        fromString = KioskControlPanelOption::fromString
    )

    var searchProviderUrl by stringPref(
        restrictions,
        prefs,
        UserSettingsKeys.WebBrowsing.SEARCH_PROVIDER_URL,
        Constants.DEFAULT_SEARCH_PROVIDER_URL
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
    var customUnlockShortcut by stringPrefOptional(
        restrictions,
        prefs,
        UserSettingsKeys.Device.CUSTOM_UNLOCK_SHORTCUT
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

    fun exportToBase64(): String {
        val json = JSONObject().apply {
            put(UserSettingsKeys.WebContent.HOME_URL, homeUrl)
            put(UserSettingsKeys.WebContent.WEBSITE_BLACKLIST, websiteBlacklist)
            put(UserSettingsKeys.WebContent.WEBSITE_WHITELIST, websiteWhitelist)
            put(UserSettingsKeys.WebContent.WEBSITE_BOOKMARKS, websiteBookmarks)
            put(UserSettingsKeys.WebContent.ALLOW_LOCAL_FILES, allowLocalFiles)

            put(UserSettingsKeys.WebBrowsing.ALLOW_REFRESH, allowRefresh)
            put(UserSettingsKeys.WebBrowsing.ALLOW_BACKWARDS_NAVIGATION, allowBackwardsNavigation)
            put(UserSettingsKeys.WebBrowsing.ALLOW_GO_HOME, allowGoHome)
            put(UserSettingsKeys.WebBrowsing.CLEAR_HISTORY_ON_HOME, clearHistoryOnHome)
            put(UserSettingsKeys.WebBrowsing.ALLOW_HISTORY_ACCESS, allowHistoryAccess)
            put(UserSettingsKeys.WebBrowsing.ALLOW_BOOKMARK_ACCESS, allowBookmarkAccess)
            put(UserSettingsKeys.WebBrowsing.ALLOW_OTHER_URL_SCHEMES, allowOtherUrlSchemes)
            put(UserSettingsKeys.WebBrowsing.ALLOW_LINK_LONG_PRESS_CONTEXT_MENU, allowLinkLongPressContextMenu)
            put(UserSettingsKeys.WebBrowsing.ALLOW_KIOSK_CONTROL_PANEL, allowKioskControlPanel.name)
            put(UserSettingsKeys.WebBrowsing.SEARCH_PROVIDER_URL, searchProviderUrl)

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

            put(UserSettingsKeys.WebLifecycle.LOCK_ON_LAUNCH, lockOnLaunch)
            put(UserSettingsKeys.WebLifecycle.RESET_ON_LAUNCH, resetOnLaunch)
            put(UserSettingsKeys.WebLifecycle.RESET_ON_INACTIVITY_SECONDS, resetOnInactivitySeconds)

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
            put(UserSettingsKeys.Device.CUSTOM_UNLOCK_SHORTCUT, customUnlockShortcut)

            put(UserSettingsKeys.JsScripts.APPLY_APP_THEME, applyAppTheme)
            put(UserSettingsKeys.JsScripts.APPLY_DESKTOP_VIEWPORT_WIDTH, applyDesktopViewportWidth)
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
            allowBackwardsNavigation = json.optBoolean(UserSettingsKeys.WebBrowsing.ALLOW_BACKWARDS_NAVIGATION, allowBackwardsNavigation)
            allowGoHome = json.optBoolean(UserSettingsKeys.WebBrowsing.ALLOW_GO_HOME, allowGoHome)
            clearHistoryOnHome = json.optBoolean(UserSettingsKeys.WebBrowsing.CLEAR_HISTORY_ON_HOME, clearHistoryOnHome)
            allowHistoryAccess = json.optBoolean(UserSettingsKeys.WebBrowsing.ALLOW_HISTORY_ACCESS, allowHistoryAccess)
            allowBookmarkAccess = json.optBoolean(UserSettingsKeys.WebBrowsing.ALLOW_BOOKMARK_ACCESS, allowBookmarkAccess)
            allowOtherUrlSchemes = json.optBoolean(UserSettingsKeys.WebBrowsing.ALLOW_OTHER_URL_SCHEMES, allowOtherUrlSchemes)
            allowLinkLongPressContextMenu = json.optBoolean(UserSettingsKeys.WebBrowsing.ALLOW_LINK_LONG_PRESS_CONTEXT_MENU, allowLinkLongPressContextMenu)
            json.optString(UserSettingsKeys.WebBrowsing.ALLOW_KIOSK_CONTROL_PANEL, allowKioskControlPanel.name)
            searchProviderUrl = json.optString(UserSettingsKeys.WebBrowsing.SEARCH_PROVIDER_URL, searchProviderUrl)

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

            lockOnLaunch = json.optBoolean(UserSettingsKeys.WebLifecycle.LOCK_ON_LAUNCH, lockOnLaunch)
            resetOnLaunch = json.optBoolean(UserSettingsKeys.WebLifecycle.RESET_ON_LAUNCH, resetOnLaunch)
            resetOnInactivitySeconds = json.optInt(UserSettingsKeys.WebLifecycle.RESET_ON_INACTIVITY_SECONDS, resetOnInactivitySeconds)

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
            customUnlockShortcut = json.optString(UserSettingsKeys.Device.CUSTOM_UNLOCK_SHORTCUT, customUnlockShortcut)

            applyAppTheme = json.optBoolean(UserSettingsKeys.JsScripts.APPLY_APP_THEME, applyAppTheme)
            applyDesktopViewportWidth = json.optInt(UserSettingsKeys.JsScripts.APPLY_DESKTOP_VIEWPORT_WIDTH, applyDesktopViewportWidth)
            customScriptOnPageStart = json.optString(UserSettingsKeys.JsScripts.CUSTOM_SCRIPT_ON_PAGE_START, customScriptOnPageStart)
            customScriptOnPageFinish = json.optString(UserSettingsKeys.JsScripts.CUSTOM_SCRIPT_ON_PAGE_FINISH, customScriptOnPageFinish)
            true
        } catch (_: Exception) {
            false
        }
    }
}
