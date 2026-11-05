package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.jsscript

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem

@Composable
fun EnableAppLauncherApiSetting() {
    val context = LocalContext.current
    val userSettings = UserSettings(context)
    val settingKey = UserSettingsKeys.JsScripts.ENABLE_APP_LAUNCHER_API

    BooleanSettingFieldItem(
        label = stringResource(R.string.js_scripts_enable_app_launcher_api_title),
        infoText =
            """
            Allow web pages to open other installed apps through a JavaScript
            interface. Web pages can use:

            window.WebviewKioskAppInterface.launchPackage("com.netflix.ninja")

            and also isInstalled(packageName) or getInstalled(jsonArray) to
            check what is present before offering it.

            While locked, the same lock task rules apply as for any other app
            launch: the target must be lock task permitted and the HOME lock
            task feature must be enabled. Only enable this for pages you trust,
            as it lets the page leave the kiosk page for another app.
            """
                .trimIndent(),
        initialValue = userSettings.enableAppLauncherApi,
        settingKey = settingKey,
        restricted = userSettings.isRestricted(settingKey),
        onSave = { userSettings.enableAppLauncherApi = it },
    )
}
