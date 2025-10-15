package uk.nktnet.webviewkiosk.config

import android.content.Context
import android.content.RestrictionsManager
import android.content.SharedPreferences
import android.util.Base64
import android.webkit.WebSettings
import org.json.JSONObject
import androidx.core.content.edit
import uk.nktnet.webviewkiosk.config.option.*
import uk.nktnet.webviewkiosk.utils.booleanPref
import uk.nktnet.webviewkiosk.utils.intPref
import uk.nktnet.webviewkiosk.utils.stringPref
import uk.nktnet.webviewkiosk.utils.stringPrefOptional

class UserSettings(val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(UserSettingsKeys.PREFS_NAME, Context.MODE_PRIVATE)
    val restrictions = (context.getSystemService(Context.RESTRICTIONS_SERVICE) as? RestrictionsManager)
        ?.applicationRestrictions

    // Web Content
    var homeUrl by stringPref(prefs, UserSettingsKeys.WebContent.HOME_URL, Constants.WEBSITE_URL, restrictions)
    var websiteBlacklist by stringPrefOptional(prefs, UserSettingsKeys.WebContent.WEBSITE_BLACKLIST, restrictions)
    var websiteWhitelist by stringPrefOptional(prefs, UserSettingsKeys.WebContent.WEBSITE_WHITELIST, restrictions)
    var websiteBookmarks by stringPrefOptional(prefs, UserSettingsKeys.WebContent.WEBSITE_BOOKMARKS, restrictions)
    var allowLocalFiles by booleanPref(prefs, UserSettingsKeys.WebContent.ALLOW_LOCAL_FILES, true, restrictions)

    // Web Browsing
    var allowRefresh by booleanPref(prefs, UserSettingsKeys.WebBrowsing.ALLOW_REFRESH, true, restrictions)
    var allowBackwardsNavigation by booleanPref(prefs, UserSettingsKeys.WebBrowsing.ALLOW_BACKWARDS_NAVIGATION, true, restrictions)
    var allowGoHome by booleanPref(prefs, UserSettingsKeys.WebBrowsing.ALLOW_GO_HOME, true, restrictions)
    var clearHistoryOnHome by booleanPref(prefs, UserSettingsKeys.WebBrowsing.CLEAR_HISTORY_ON_HOME, false, restrictions)
    var allowHistoryAccess by booleanPref(prefs, UserSettingsKeys.WebBrowsing.ALLOW_HISTORY_ACCESS, true, restrictions)
    var allowBookmarkAccess by booleanPref(prefs, UserSettingsKeys.WebBrowsing.ALLOW_BOOKMARK_ACCESS, true, restrictions)
    var allowOtherUrlSchemes by booleanPref(prefs, UserSettingsKeys.WebBrowsing.ALLOW_OTHER_URL_SCHEMES, false, restrictions)
    var allowLinkLongPressContextMenu by booleanPref(prefs, UserSettingsKeys.WebBrowsing.ALLOW_LINK_LONG_PRESS_CONTEXT_MENU, true, restrictions)
    var allowKioskControlPanel: KioskControlPanelOption
        get() = restrictions?.getString(UserSettingsKeys.WebBrowsing.ALLOW_KIOSK_CONTROL_PANEL)
            ?.let { KioskControlPanelOption.fromString(it) }
            ?: KioskControlPanelOption.fromString(prefs.getString(UserSettingsKeys.WebBrowsing.ALLOW_KIOSK_CONTROL_PANEL, null))
        set(value) {
            if (restrictions?.containsKey(UserSettingsKeys.WebBrowsing.ALLOW_KIOSK_CONTROL_PANEL) != true) {
                prefs.edit { putString(UserSettingsKeys.WebBrowsing.ALLOW_KIOSK_CONTROL_PANEL, value.name) }
            }
        }
    var searchProviderUrl by stringPref(prefs, UserSettingsKeys.WebBrowsing.SEARCH_PROVIDER_URL, Constants.DEFAULT_SEARCH_PROVIDER_URL, restrictions)

    // Web Engine
    var enableJavaScript by booleanPref(prefs, UserSettingsKeys.WebEngine.ENABLE_JAVASCRIPT, true, restrictions)
    var enableDomStorage by booleanPref(prefs, UserSettingsKeys.WebEngine.ENABLE_DOM_STORAGE, true, restrictions)
    var acceptCookies by booleanPref(prefs, UserSettingsKeys.WebEngine.ACCEPT_COOKIES, true, restrictions)
    var acceptThirdPartyCookies by booleanPref(prefs, UserSettingsKeys.WebEngine.ACCEPT_THIRD_PARTY_COOKIES, false, restrictions)
    var cacheMode: CacheModeOption
        get() = CacheModeOption.fromInt(prefs.getInt(UserSettingsKeys.WebEngine.CACHE_MODE, WebSettings.LOAD_DEFAULT))
        set(value) {
            if (restrictions?.containsKey(UserSettingsKeys.WebEngine.CACHE_MODE) != true) {
                prefs.edit { putInt(UserSettingsKeys.WebEngine.CACHE_MODE, value.mode) }
            }
        }
    var layoutAlgorithm: LayoutAlgorithmOption
        get() = LayoutAlgorithmOption.fromAlgorithm(
            when (val value = prefs.getString(UserSettingsKeys.WebEngine.LAYOUT_ALGORITHM, null)) {
                null -> WebSettings.LayoutAlgorithm.NORMAL
                else -> WebSettings.LayoutAlgorithm.valueOf(value)
            }
        )
        set(value) {
            if (restrictions?.containsKey(UserSettingsKeys.WebEngine.LAYOUT_ALGORITHM) != true) {
                prefs.edit { putString(UserSettingsKeys.WebEngine.LAYOUT_ALGORITHM, value.algorithm.name) }
            }
        }
    var userAgent by stringPrefOptional(prefs, UserSettingsKeys.WebEngine.USER_AGENT, restrictions)
    var useWideViewPort by booleanPref(prefs, UserSettingsKeys.WebEngine.USE_WIDE_VIEWPORT, true, restrictions)
    var loadWithOverviewMode by booleanPref(prefs, UserSettingsKeys.WebEngine.LOAD_WITH_OVERVIEW_MODE, true, restrictions)
    var enableZoom by booleanPref(prefs, UserSettingsKeys.WebEngine.ENABLE_ZOOM, true, restrictions)
    var displayZoomControls by booleanPref(prefs, UserSettingsKeys.WebEngine.DISPLAY_ZOOM_CONTROLS, false, restrictions)
    var allowFileAccessFromFileURLs by booleanPref(prefs, UserSettingsKeys.WebEngine.ALLOW_FILE_ACCESS_FROM_FILE_URLS, false, restrictions)
    var allowUniversalAccessFromFileURLs by booleanPref(prefs, UserSettingsKeys.WebEngine.ALLOW_UNIVERSAL_ACCESS_FROM_FILE_URLS, false, restrictions)
    var mediaPlaybackRequiresUserGesture by booleanPref(prefs, UserSettingsKeys.WebEngine.MEDIA_PLAYBACK_REQUIRES_USER_GESTURE, true, restrictions)

    // Web Lifecycle
    var lockOnLaunch by booleanPref(prefs, UserSettingsKeys.WebLifecycle.LOCK_ON_LAUNCH, false, restrictions)
    var resetOnLaunch by booleanPref(prefs, UserSettingsKeys.WebLifecycle.RESET_ON_LAUNCH, false, restrictions)
    var resetOnInactivitySeconds by intPref(prefs, UserSettingsKeys.WebLifecycle.RESET_ON_INACTIVITY_SECONDS, 0, restrictions)

    // Appearance
    var theme: ThemeOption
        get() = ThemeOption.fromString(prefs.getString(UserSettingsKeys.Appearance.THEME, null))
        set(value) {
            if (restrictions?.containsKey(UserSettingsKeys.Appearance.THEME) != true) {
                prefs.edit { putString(UserSettingsKeys.Appearance.THEME, value.name) }
            }
        }
    var addressBarMode: AddressBarOption
        get() = AddressBarOption.fromString(prefs.getString(UserSettingsKeys.Appearance.ADDRESS_BAR_MODE, null))
        set(value) {
            if (restrictions?.containsKey(UserSettingsKeys.Appearance.ADDRESS_BAR_MODE) != true) {
                prefs.edit { putString(UserSettingsKeys.Appearance.ADDRESS_BAR_MODE, value.name) }
            }
        }
    var webViewInset: WebViewInset
        get() = WebViewInset.fromString(prefs.getString(UserSettingsKeys.Appearance.WEBVIEW_INSET, null))
        set(value) {
            if (restrictions?.containsKey(UserSettingsKeys.Appearance.WEBVIEW_INSET) != true) {
                prefs.edit { putString(UserSettingsKeys.Appearance.WEBVIEW_INSET, value.name) }
            }
        }
    var blockedMessage by stringPref(prefs, UserSettingsKeys.Appearance.BLOCKED_MESSAGE, "This site is blocked by ${Constants.APP_NAME}.", restrictions)

    // Device
    var keepScreenOn by booleanPref(prefs, UserSettingsKeys.Device.KEEP_SCREEN_ON, false, restrictions)
    var deviceRotation: DeviceRotationOption
        get() = DeviceRotationOption.fromString(prefs.getString(UserSettingsKeys.Device.DEVICE_ROTATION, null))
        set(value) {
            if (restrictions?.containsKey(UserSettingsKeys.Device.DEVICE_ROTATION) != true) {
                prefs.edit { putString(UserSettingsKeys.Device.DEVICE_ROTATION, value.degrees) }
            }
        }
    var allowCamera by booleanPref(prefs, UserSettingsKeys.Device.ALLOW_CAMERA, false, restrictions)
    var allowMicrophone by booleanPref(prefs, UserSettingsKeys.Device.ALLOW_MICROPHONE, false, restrictions)
    var allowLocation by booleanPref(prefs, UserSettingsKeys.Device.ALLOW_LOCATION, false, restrictions)
    var customUnlockShortcut by stringPrefOptional(prefs, UserSettingsKeys.Device.CUSTOM_UNLOCK_SHORTCUT, restrictions)

    // JS Scripts
    var applyAppTheme by booleanPref(prefs, UserSettingsKeys.JsScripts.APPLY_APP_THEME, true, restrictions)
    var applyDesktopViewportWidth by intPref(prefs, UserSettingsKeys.JsScripts.APPLY_DESKTOP_VIEWPORT_WIDTH, 0, restrictions)
    var customScriptOnPageStart by stringPrefOptional(prefs, UserSettingsKeys.JsScripts.CUSTOM_SCRIPT_ON_PAGE_START, restrictions)
    var customScriptOnPageFinish by stringPrefOptional(prefs, UserSettingsKeys.JsScripts.CUSTOM_SCRIPT_ON_PAGE_FINISH, restrictions)

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

            put(UserSettingsKeys.Appearance.BLOCKED_MESSAGE, blockedMessage)
            put(UserSettingsKeys.Appearance.THEME, theme.name)
            put(UserSettingsKeys.Appearance.ADDRESS_BAR_MODE, addressBarMode.name)
            put(UserSettingsKeys.Appearance.WEBVIEW_INSET, webViewInset.name)

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
            layoutAlgorithm = LayoutAlgorithmOption.fromAlgorithm(
                WebSettings.LayoutAlgorithm.valueOf(json.optString(UserSettingsKeys.WebEngine.LAYOUT_ALGORITHM, layoutAlgorithm.algorithm.name))
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
