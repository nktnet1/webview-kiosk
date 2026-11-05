package uk.nktnet.webviewkiosk.utils.webview

import android.app.Activity
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowNfcAdapter
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class NfcForegroundDispatchTest {
    private lateinit var controller: ActivityController<Activity>
    private lateinit var activity: Activity

    @Before
    fun setUp() {
        ShadowNfcAdapter.setNfcHardwareExists(true)
        controller =
            Robolectric.buildActivity(Activity::class.java).create().start().resume()
        activity = controller.get()
        shadowOf(activity.packageManager)
            .setSystemFeature(
                PackageManager.FEATURE_NFC,
                true,
            )
        shadowOf(adapter()).setEnabled(true)
    }

    @After
    fun tearDown() {
        controller.pause().stop().destroy()
    }

    @Test
    fun registersWhenResumedAndDisablesBeforePausing() {
        val dispatch = NfcForegroundDispatch(activity, { true })
        dispatch.onResume()
        assertSame(activity, shadowOf(adapter()).enabledActivity)

        dispatch.onPause()
        assertSame(activity, shadowOf(adapter()).disabledActivity)
    }

    @Test
    fun reRegistersAfterScreenWakeWithoutAnotherActivityResume() {
        val dispatch = NfcForegroundDispatch(activity, { true })
        dispatch.onResume()

        // Simulate NFC service losing the foreground registration after suspend.
        resetNfcService()
        assertNull(shadowOf(adapter()).enabledActivity)
        dispatch.onScreenOn()
        assertSame(activity, shadowOf(adapter()).enabledActivity)

        resetNfcService()
        dispatch.onWindowFocusGained()
        assertSame(activity, shadowOf(adapter()).enabledActivity)
        dispatch.onPause()
    }

    @Test
    fun replacesExistingRegistrationBeforeEachWakeRecovery() {
        val dispatch = NfcForegroundDispatch(activity, { true })
        dispatch.onResume()
        assertNull(shadowOf(adapter()).disabledActivity)

        dispatch.onScreenOn()
        assertSame(activity, shadowOf(adapter()).disabledActivity)
        assertSame(activity, shadowOf(adapter()).enabledActivity)

        ReflectionHelpers.setField(shadowOf(adapter()), "disabledActivity", null)
        dispatch.onWindowFocusGained()
        assertSame(activity, shadowOf(adapter()).disabledActivity)
        assertSame(activity, shadowOf(adapter()).enabledActivity)
        dispatch.onPause()
    }

    @Test
    fun reRegistersWhenTheRadioIsReEnabled() {
        val dispatch = NfcForegroundDispatch(activity, { true })
        shadowOf(adapter()).setEnabled(false)
        dispatch.onResume()
        assertNull(shadowOf(adapter()).enabledActivity)

        shadowOf(adapter()).setEnabled(true)
        dispatch.onAdapterStateChanged(NfcAdapter.STATE_ON)
        assertSame(activity, shadowOf(adapter()).enabledActivity)
        dispatch.onPause()
    }

    @Test
    fun doesNotRegisterDuringPauseOrWhenNfcIsDisallowed() {
        val dispatch = NfcForegroundDispatch(activity, { true })
        dispatch.onResume()
        dispatch.onPause()

        resetNfcService()
        dispatch.onScreenOn()
        dispatch.onWindowFocusGained()
        dispatch.onAdapterStateChanged(NfcAdapter.STATE_ON)
        assertNull(shadowOf(adapter()).enabledActivity)

        val disabledDispatch = NfcForegroundDispatch(activity, { false })
        disabledDispatch.onResume()
        assertNull(shadowOf(adapter()).enabledActivity)
        disabledDispatch.onPause()
    }

    private fun resetNfcService() {
        ReflectionHelpers.setField(shadowOf(adapter()), "enabledActivity", null)
    }

    private fun adapter(): NfcAdapter =
        checkNotNull(NfcAdapter.getDefaultAdapter(activity))
}
