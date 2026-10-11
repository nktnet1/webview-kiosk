package uk.nktnet.webviewkiosk.instrumentation

import androidx.navigation.NavHostController
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import uk.nktnet.webviewkiosk.MainActivity
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.Screen
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.managers.AuthenticationManager

/** Integration of protected navigation, a REAL Compose window and activity recreation. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 28)
class AuthenticationRouteDeviceTest {
    @Test
    fun settingsRemainProtectedBeforeUnlockAndRequireAuthAfterRecreation() {
        LoopbackHttpServer().use { http ->
            InstalledAppHarness.resetSettings(http.url("/first"))
            val context = InstalledAppHarness.context
            UserSettings(context).customAuthPassword = "disposable-emulator-only-password"
            try {
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    InstalledAppHarness.awaitUrl(scenario, http.url("/first"))
                    scenario.onActivity { activity ->
                        // The production NavHost has no public test-only navigation hook.
                        val field = MainActivity::class.java.getDeclaredField("navController")
                            .apply { isAccessible = true }
                        (field.get(activity) as NavHostController).navigate(Screen.Settings.route)
                    }
                    val protectedTitle = context.getString(R.string.settings_web_content_title)
                    InstalledAppHarness.eventually("real custom-password window") {
                        AuthenticationManager.showCustomAuth.value &&
                            AuthenticationManager.promptResults.value ==
                                AuthenticationManager.AuthenticationResult.Pending &&
                            visibleText("Unlock")
                    }
                    assertFalse(AuthenticationManager.hasValidSession())
                    assertFalse(visibleText(protectedTitle))

                    // Complete the real manager's pending prompt without emulating a device
                    // credential or claiming the password field itself was exercised.
                    scenario.onActivity { AuthenticationManager.customAuthSuccess() }
                    InstalledAppHarness.eventually("protected settings after authentication") {
                        AuthenticationManager.hasValidSession() && visibleText(protectedTitle)
                    }
                    assertFalse(visibleText("Unlock"))

                    scenario.recreate()
                    InstalledAppHarness.eventually("authentication after activity recreation") {
                        AuthenticationManager.showCustomAuth.value &&
                            AuthenticationManager.promptResults.value ==
                                AuthenticationManager.AuthenticationResult.Pending &&
                            visibleText("Unlock")
                    }
                    assertFalse(AuthenticationManager.hasValidSession())
                    assertFalse(visibleText(protectedTitle))
                }
            } finally {
                UserSettings(context).customAuthPassword = ""
            }
        }
    }

    private fun visibleText(value: String): Boolean {
        val root = InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow
            ?: return false
        return root.findAccessibilityNodeInfosByText(value).any {
            it.isVisibleToUser && it.text?.toString() == value
        }
    }
}
