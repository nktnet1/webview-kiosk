package uk.nktnet.webviewkiosk.utils

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.util.ReflectionHelpers
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.config.option.UnlockAuthRequirementOption
import uk.nktnet.webviewkiosk.managers.DeviceOwnerManager
import uk.nktnet.webviewkiosk.states.WaitingForUnlockStateSingleton

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class UnlockFailureNotificationTest {
    class UnlockActivity : Activity() {
        var denyUnlock = false
        var stopAttempts = 0

        override fun stopLockTask() {
            stopAttempts++
            if (denyUnlock) throw SecurityException("lock task permission denied")
        }
    }

    private lateinit var controller: ActivityController<UnlockActivity>
    private lateinit var activity: UnlockActivity
    private val observationScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private var unlockNotifications = 0

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences(UserSettingsKeys.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
        controller = Robolectric.buildActivity(UnlockActivity::class.java).create().start().resume()
        activity = controller.get()
        UserSettings(activity).unlockAuthRequirement = UnlockAuthRequirementOption.OFF
        DeviceOwnerManager.init(activity)
        observationScope.launch(start = CoroutineStart.UNDISPATCHED) {
            WaitingForUnlockStateSingleton.unlockSuccess.collect { unlockNotifications++ }
        }
    }

    @After
    fun tearDown() {
        observationScope.cancel()
        controller.pause().stop().destroy()
    }

    @Test
    fun rejectedUnlockDoesNotPublishSuccess() {
        activity.denyUnlock = true

        unlockWithAuthIfRequired(activity)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(1, activity.stopAttempts)
        assertEquals(0, unlockNotifications)
    }

    @Test
    fun android6RejectedUnlockDoesNotPublishSuccess() {
        val originalSdk = Build.VERSION.SDK_INT
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", Build.VERSION_CODES.M)
        try {
            activity.denyUnlock = true
            unlockWithAuthIfRequired(activity)
            shadowOf(Looper.getMainLooper()).idle()

            assertEquals(1, activity.stopAttempts)
            assertEquals(0, unlockNotifications)
        } finally {
            ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", originalSdk)
        }
    }

    @Test
    fun successfulUnlockPublishesExactlyOneSuccess() {
        unlockWithAuthIfRequired(activity)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(1, activity.stopAttempts)
        assertEquals(1, unlockNotifications)
    }

    @Test
    fun deniedAttemptCanBeRetriedWithoutAnObsoleteSuccess() {
        activity.denyUnlock = true
        unlockWithAuthIfRequired(activity)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, unlockNotifications)

        activity.denyUnlock = false
        unlockWithAuthIfRequired(activity)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(2, activity.stopAttempts)
        assertEquals(1, unlockNotifications)
    }
}
