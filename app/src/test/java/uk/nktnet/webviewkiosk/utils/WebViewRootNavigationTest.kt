package uk.nktnet.webviewkiosk.utils

import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.createGraph
import androidx.lifecycle.ViewModelStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import uk.nktnet.webviewkiosk.config.Screen

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WebViewRootNavigationTest {
    private fun controller(): NavHostController {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        return NavHostController(activity).apply {
            setLifecycleOwner(activity)
            setViewModelStore(ViewModelStore())
            navigatorProvider.addNavigator(ComposeNavigator())
            graph = createGraph(startDestination = Screen.WebView.route) {
                composable(Screen.WebView.route) {}
                composable(Screen.AdminRestrictionsChanged.route) {}
                navigation(startDestination = Screen.Settings.route, route = "settings_list") {
                    composable(Screen.Settings.route) {}
                    composable(Screen.SettingsAbout.route) {}
                }
            }
        }
    }

    @Test
    fun returningFromNestedSettingsKeepsRootIdentityAndClearsSettingsStack() {
        val navController = controller()
        val root = navController.getBackStackEntry(Screen.WebView.route)
        val refreshCount = root.savedStateHandle.getStateFlow(WEBVIEW_RECREATION_REQUEST_KEY, 0)
        navController.navigate(Screen.Settings.route)
        navController.navigate(Screen.SettingsAbout.route)

        navigateToWebViewScreen(navController)

        assertSame(root, navController.currentBackStackEntry)
        assertSame(root, navController.getBackStackEntry(Screen.WebView.route))
        assertEquals(1, refreshCount.value)
        assertFalse("settings must no longer be on the back stack", navController.currentBackStack.value.any {
            it.destination.route == Screen.Settings.route || it.destination.route == Screen.SettingsAbout.route
        })
    }

    @Test
    fun refreshingWebviewRootDoesNotReplaceOrDuplicateItsEntry() {
        val navController = controller()
        val root = navController.currentBackStackEntry!!
        val refreshCount = root.savedStateHandle.getStateFlow(WEBVIEW_RECREATION_REQUEST_KEY, 0)

        repeat(3) { navigateToWebViewScreen(navController) }

        assertSame(root, navController.currentBackStackEntry)
        assertEquals(3, refreshCount.value)
        assertEquals(1, navController.currentBackStack.value.count {
            it.destination.route == Screen.WebView.route
        })
    }

    @Test
    fun administratorNoticeReturnsToTheSameRootEntry() {
        val navController = controller()
        val root = navController.currentBackStackEntry!!
        navController.navigate(Screen.AdminRestrictionsChanged.route)

        navigateToWebViewScreen(navController)

        assertSame(root, navController.currentBackStackEntry)
        assertEquals(1, root.savedStateHandle.get<Int>(WEBVIEW_RECREATION_REQUEST_KEY))
        assertFalse(navController.currentBackStack.value.any {
            it.destination.route == Screen.AdminRestrictionsChanged.route
        })
    }

    @Test
    fun settingsOnlyGraphCanStillExitToAWebviewThatWasNotInItsBackStack() {
        val navController = controller()
        navController.graph = navController.createGraph(startDestination = Screen.Settings.route) {
            composable(Screen.Settings.route) {}
            composable(Screen.WebView.route) {}
        }

        navigateToWebViewScreen(navController)

        assertEquals(Screen.WebView.route, navController.currentDestination?.route)
        assertEquals(1, navController.currentBackStack.value.count {
            it.destination.route == Screen.WebView.route
        })
    }

    @Test
    fun repeatedReturnsFromSettingsReuseTheRootAndIncrementRecreationSignal() {
        val navController = controller()
        val root = navController.currentBackStackEntry!!
        repeat(2) {
            navController.navigate(Screen.Settings.route)
            navigateToWebViewScreen(navController)
            assertSame(root, navController.currentBackStackEntry)
        }

        assertEquals(2, root.savedStateHandle.get<Int>(WEBVIEW_RECREATION_REQUEST_KEY))
    }
}
