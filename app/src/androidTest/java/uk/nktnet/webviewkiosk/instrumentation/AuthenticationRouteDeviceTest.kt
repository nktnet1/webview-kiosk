package uk.nktnet.webviewkiosk.instrumentation

import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityWindowInfo
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
                    awaitCustomPasswordDialog()
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
                    awaitCustomPasswordDialog()
                    assertFalse(AuthenticationManager.hasValidSession())
                    assertFalse(visibleText(protectedTitle))
                }
            } finally {
                UserSettings(context).customAuthPassword = ""
            }
        }
    }

    private fun awaitCustomPasswordDialog() {
        // Distinguish a missing authentication request from an accessibility lookup failure.
        InstalledAppHarness.eventually("pending custom-password authentication") {
            AuthenticationManager.showCustomAuth.value &&
                AuthenticationManager.promptResults.value ==
                    AuthenticationManager.AuthenticationResult.Pending
        }
        InstalledAppHarness.eventually("visible custom-password dialog") {
            visibleText("Password") && visibleText("Unlock")
        }
    }

    private fun visibleText(value: String): Boolean {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val windowFlag = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        val serviceInfo = automation.serviceInfo
        if (serviceInfo.flags and windowFlag == 0) {
            serviceInfo.flags = serviceInfo.flags or windowFlag
            automation.serviceInfo = serviceInfo
        }

        // The dialog focuses its password field, which can make the IME the active window.
        // Search application windows rather than only rootInActiveWindow (often the keyboard).
        return automation.windows
            .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            .mapNotNull { it.root }
            .any { root ->
                root.findAccessibilityNodeInfosByText(value).any { node ->
                    node.packageName?.toString() == InstalledAppHarness.context.packageName &&
                        node.isVisibleToUser && node.text?.toString() == value
                }
            }
    }
}
