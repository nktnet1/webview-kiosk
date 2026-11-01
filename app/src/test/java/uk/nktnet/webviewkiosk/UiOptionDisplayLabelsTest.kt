package uk.nktnet.webviewkiosk

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import uk.nktnet.webviewkiosk.adblock.FilterSkipReason
import uk.nktnet.webviewkiosk.adblock.RuleLoadStage
import uk.nktnet.webviewkiosk.config.data.AppType
import uk.nktnet.webviewkiosk.config.mqtt.MqttQosOption
import uk.nktnet.webviewkiosk.config.mqtt.MqttRetainHandlingOption
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
import uk.nktnet.webviewkiosk.config.option.RefreshOnNetworkAvailableOption
import uk.nktnet.webviewkiosk.config.option.ResetActionOption
import uk.nktnet.webviewkiosk.config.option.SearchSuggestionEngineOption
import uk.nktnet.webviewkiosk.config.option.SslErrorModeOption
import uk.nktnet.webviewkiosk.config.option.ThemeOption
import uk.nktnet.webviewkiosk.config.option.UnlockAuthRequirementOption
import uk.nktnet.webviewkiosk.config.option.WebViewInsetOption
import uk.nktnet.webviewkiosk.config.option.WebviewControlActionOption
import uk.nktnet.webviewkiosk.ui.resources.displayLabelRes

/** UI labels can be translated without changing any stable configuration labels. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class UiOptionDisplayLabelsTest {
    private val context = RuntimeEnvironment.getApplication()

    private fun assertMatches(actual: List<String>, originals: List<String>) {
        assertEquals(originals, actual)
    }

    @Test
    fun settingDropdownLabelsMatchTheExistingVisibleEnglishText() {
        assertMatches(
            ThemeOption.entries.map { context.getString(it.displayLabelRes) },
            ThemeOption.entries.map { it.label },
        )
        assertMatches(
            AddressBarModeOption.entries.map {
                context.getString(it.displayLabelRes)
            },
            AddressBarModeOption.entries.map { it.label },
        )
        assertMatches(
            AddressBarPositionOption.entries.map {
                context.getString(it.displayLabelRes)
            },
            AddressBarPositionOption.entries.map { it.label },
        )
        assertMatches(
            AddressBarSizeOption.entries.map {
                context.getString(it.displayLabelRes)
            },
            AddressBarSizeOption.entries.map { it.label },
        )
        assertMatches(
            FloatingToolbarModeOption.entries.map {
                context.getString(it.displayLabelRes)
            },
            FloatingToolbarModeOption.entries.map { it.label },
        )
        assertMatches(
            ImmersiveModeOption.entries.map {
                context.getString(it.displayLabelRes)
            },
            ImmersiveModeOption.entries.map { it.label },
        )
        assertMatches(
            DeviceRotationOption.entries.map {
                context.getString(it.displayLabelRes)
            },
            DeviceRotationOption.entries.map { it.label },
        )
        assertMatches(
            KioskControlPanelRegionOption.entries.map {
                context.getString(it.displayLabelRes)
            },
            KioskControlPanelRegionOption.entries.map { it.label },
        )
        assertMatches(
            OverrideUrlLoadingBlockActionOption.entries.map {
                context.getString(it.displayLabelRes)
            },
            OverrideUrlLoadingBlockActionOption.entries.map { it.label },
        )
        assertMatches(
            RefreshOnNetworkAvailableOption.entries.map {
                context.getString(it.displayLabelRes)
            },
            RefreshOnNetworkAvailableOption.entries.map { it.label },
        )
        assertMatches(
            ResetActionOption.entries.map { context.getString(it.displayLabelRes) },
            ResetActionOption.entries.map { it.label },
        )
        assertMatches(
            SearchSuggestionEngineOption.entries.map {
                context.getString(it.displayLabelRes)
            },
            SearchSuggestionEngineOption.entries.map { it.label },
        )
        assertMatches(
            SslErrorModeOption.entries.map { context.getString(it.displayLabelRes) },
            SslErrorModeOption.entries.map { it.label },
        )
        assertMatches(
            UnlockAuthRequirementOption.entries.map {
                context.getString(it.displayLabelRes)
            },
            UnlockAuthRequirementOption.entries.map { it.label },
        )
        assertMatches(
            WebviewControlActionOption.entries.map {
                context.getString(it.displayLabelRes)
            },
            WebviewControlActionOption.entries.map { it.label },
        )
        assertMatches(
            WebViewInsetOption.entries.map { context.getString(it.displayLabelRes) },
            WebViewInsetOption.entries.map { it.label },
        )
        assertMatches(
            CacheModeOption.entries.map { context.getString(it.displayLabelRes) },
            CacheModeOption.entries.map { it.label },
        )
        assertMatches(
            LayoutAlgorithmOption.entries.map {
                context.getString(it.displayLabelRes)
            },
            LayoutAlgorithmOption.entries.map { it.label },
        )
        assertMatches(
            MixedContentModeOption.entries.map {
                context.getString(it.displayLabelRes)
            },
            MixedContentModeOption.entries.map { it.label },
        )
        assertMatches(
            OverScrollModeOption.entries.map {
                context.getString(it.displayLabelRes)
            },
            OverScrollModeOption.entries.map { it.label },
        )
        assertMatches(
            BackButtonHoldActionOption.entries.map {
                context.getString(it.displayLabelRes)
            },
            BackButtonHoldActionOption.entries.map { it.label },
        )
        assertMatches(
            MqttQosOption.entries.map { context.getString(it.displayLabelRes) },
            MqttQosOption.entries.map { it.getSettingLabel() },
        )
        assertMatches(
            MqttRetainHandlingOption.entries.map {
                context.getString(it.displayLabelRes)
            },
            MqttRetainHandlingOption.entries.map { it.getSettingLabel() },
        )
    }

    @Test
    fun existingShortLabelsAreNotReplacedWithOldVerboseResourceLabels() {
        assertEquals("System", context.getString(ThemeOption.SYSTEM.displayLabelRes))
        assertEquals(
            "History",
            context.getString(ResetActionOption.HISTORY.displayLabelRes),
        )
        assertEquals(
            "Web Storage",
            context.getString(ResetActionOption.WEB_STORAGE.displayLabelRes),
        )
        assertEquals(
            "90",
            context.getString(DeviceRotationOption.ROTATION_90.displayLabelRes),
        )
    }

    @Test
    fun appTypesDiagnosticsAndLoadingStagesKeepTheirExactText() {
        assertMatches(
            AppType.entries.map { context.getString(it.displayLabelRes) },
            AppType.entries.map { it.label },
        )
        assertMatches(
            FilterSkipReason.entries.map { context.getString(it.displayLabelRes) },
            FilterSkipReason.entries.map { it.label },
        )
        assertMatches(
            RuleLoadStage.entries.map { context.getString(it.displayLabelRes) },
            RuleLoadStage.entries.map { it.description },
        )
        assertEquals(
            "Starting WebView…",
            context.getString(R.string.ui_starting_webview),
        )
        assertEquals(
            "Preparing content blocking…",
            context.getString(R.string.ui_preparing_content_blocking),
        )
    }
}
