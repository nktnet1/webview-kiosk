package uk.nktnet.webviewkiosk

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.Job
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
import uk.nktnet.webviewkiosk.config.Constants
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.states.LockStateSingleton
import uk.nktnet.webviewkiosk.utils.tryLockTask

/**
 * Runs the Android 6 routing and activity-owner policy against production callbacks.
 * Robolectric uses the exported API 28 runtime; overriding the SDK gate does not
 * emulate an API 23 device.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class Android6KioskLifecycleTest {
    private lateinit var app: Application
    private lateinit var settings: UserSettings
    private val activities = mutableListOf<ActivityController<out MainActivity>>()
    private var originalSdk = 28
    private var savedMonitoringJob: Job? = null
    private var savedMonitoringStarted = false

    @Before
    fun setUp() {
        app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences(UserSettingsKeys.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        app.getSharedPreferences("system_settings", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        settings =
            UserSettings(app).apply {
                mqttEnabled = false
                lockOnLaunch = false
                homeUrl = "about:blank"
                resetOnLaunch = false
                resetOnInactivitySeconds = 0
                supportPdfRendering = false
            }
        savedMonitoringJob =
            monitoringField("monitoringJob").get(LockStateSingleton) as Job?
        savedMonitoringStarted =
            monitoringField("isStarted").getBoolean(LockStateSingleton)
        originalSdk = Build.VERSION.SDK_INT
        setSdk(Build.VERSION_CODES.M)
    }

    @After
    fun tearDown() {
        try {
            activities.asReversed().forEach { controller ->
                val state = controller.get().lifecycle.currentState
                if (state.isAtLeast(Lifecycle.State.RESUMED)) {
                    controller.pause()
                }
                if (
                    controller
                        .get()
                        .lifecycle
                        .currentState
                        .isAtLeast(Lifecycle.State.STARTED)
                ) {
                    controller.stop()
                }
                controller.destroy()
            }
        } finally {
            val currentMonitoringJob =
                monitoringField("monitoringJob").get(LockStateSingleton) as Job?
            if (currentMonitoringJob !== savedMonitoringJob) {
                currentMonitoringJob?.cancel()
            }
            monitoringField("monitoringJob")
                .set(LockStateSingleton, savedMonitoringJob)
            monitoringField("isStarted")
                .setBoolean(LockStateSingleton, savedMonitoringStarted)
            setSdk(originalSdk)
        }
    }

    @Test
    fun launcherDoesNotRedirectWhenLockOnLaunchIsDisabled() {
        val controller = mainActivity(launcherIntent()).start().resume()

        assertNull(shadowOf(controller.get()).nextStartedActivity)
        assertTrue(controller.get().isApplicationEventHost())
    }

    @Test
    fun homeLaunchRedirectsOnlyAfterResumeIntoAnUnrequestedPrivateTask() {
        val controller = mainActivity(homeIntent()).start()
        assertNull(shadowOf(controller.get()).nextStartedActivity)

        controller.resume()
        assertPrivateTaskIntent(
            controller.get(),
            fromHome = true,
            lockRequested = false,
        )
        assertNull(shadowOf(controller.get()).nextStartedActivity)
    }

    @Test
    fun lockOnLaunchRedirectsLauncherButNeverLocksTheRoutingTask() {
        settings.lockOnLaunch = true
        val controller = mainActivity(launcherIntent()).start().resume()

        assertPrivateTaskIntent(
            controller.get(),
            fromHome = false,
            lockRequested = true,
        )
        assertNull(shadowOf(controller.get()).nextStartedActivity)
    }

    @Test
    fun homeLaunchTakesPrecedenceOverLockOnLaunch() {
        settings.lockOnLaunch = true
        val controller = mainActivity(homeIntent()).start().resume()

        assertPrivateTaskIntent(
            controller.get(),
            fromHome = true,
            lockRequested = false,
        )
    }

    @Test
    fun explicitLockOnRoutingActivityTargetsThePrivateTask() {
        val controller = mainActivity(launcherIntent()).start().resume()
        assertTrue(tryLockTask(controller.get()))

        assertPrivateTaskIntent(
            controller.get(),
            fromHome = false,
            lockRequested = true,
        )
    }

    @Test
    fun laterHomeIntentOverridesAnUnconsumedLaunchLockRequest() {
        settings.lockOnLaunch = true
        val controller = mainActivity(launcherIntent()).start()
        controller.newIntent(homeIntent()).resume()

        assertPrivateTaskIntent(
            controller.get(),
            fromHome = true,
            lockRequested = false,
        )
    }

    @Test
    fun repeatedHomeIntentsCoalesceIntoOneRedirectOnResume() {
        val controller = mainActivity(launcherIntent()).start().resume()
        controller.newIntent(homeIntent())
        controller.newIntent(homeIntent())
        assertNull(shadowOf(controller.get()).nextStartedActivity)

        controller.pause().resume()
        assertPrivateTaskIntent(
            controller.get(),
            fromHome = true,
            lockRequested = false,
        )
        assertNull(shadowOf(controller.get()).nextStartedActivity)
    }

    @Test
    fun returningToTheRoutingActivityDoesNotRepeatItsConsumedLockRequest() {
        settings.lockOnLaunch = true
        val controller = mainActivity(launcherIntent()).start().resume()
        assertPrivateTaskIntent(
            controller.get(),
            fromHome = false,
            lockRequested = true,
        )

        controller.pause().stop().start().resume()
        assertNull(shadowOf(controller.get()).nextStartedActivity)
    }

    @Test
    fun privateHostConsumesExplicitLockExtraBeforeASecondLaunch() {
        val launch =
            Intent(app, Android6KioskActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                putExtra(Constants.INTENT_ANDROID6_LOCK_TASK, true)
            }
        val controller = privateActivity(launch).start().resume()

        assertFalse(
            controller.get().intent.hasExtra(Constants.INTENT_ANDROID6_LOCK_TASK)
        )
        assertTrue(shadowLockTaskStarted(controller.get()))
        controller
            .get()
            .stopLockTask() // Model the user deliberately unlocking the private
        // task.
        assertFalse(shadowLockTaskStarted(controller.get()))
        assertNull(shadowOf(controller.get()).nextStartedActivity)
        controller.pause().stop().start().resume()
        assertFalse(
            "an old lock request must not replay",
            shadowLockTaskStarted(controller.get()),
        )
        assertNull(shadowOf(controller.get()).nextStartedActivity)
    }

    @Test
    fun privateHostDoesNotRedirectItselfOnHomeNewIntent() {
        val controller = privateActivity().start().resume()
        controller.newIntent(homeIntent())
        controller.pause().resume()

        assertNull(shadowOf(controller.get()).nextStartedActivity)
    }

    @Test
    fun latestStartedHostOwnsAndroid6ApplicationEvents() {
        val routing = mainActivity().start().resume()
        val private = privateActivity().start().resume()

        assertFalse(routing.get().isApplicationEventHost())
        assertTrue(private.get().isApplicationEventHost())
    }

    @Test
    fun stoppingCurrentHostTransfersOwnershipToAnotherStartedHost() {
        val routing = mainActivity().start().resume()
        val private = privateActivity().start().resume()

        private.pause().stop()

        assertTrue(
            "the remaining started host must own events",
            routing.get().isApplicationEventHost(),
        )
        assertFalse(
            "a stopped host must not keep ownership",
            private.get().isApplicationEventHost(),
        )
    }

    @Test
    fun onlyTheRemainingStartedHostCanClaimMqttAfterTheActiveHostStops() {
        val routing = mainActivity().start().resume()
        val private = privateActivity().start().resume()
        // Enable the claim gate after the starts so no transport is opened by this
        // policy test.
        settings.mqttEnabled = true
        assertFalse(canClaimMqtt(routing.get()))
        assertTrue(canClaimMqtt(private.get()))

        private.pause().stop()

        assertTrue(canClaimMqtt(routing.get()))
        assertFalse(canClaimMqtt(private.get()))
    }

    @Test
    fun stoppingAnInactiveHostCannotStealTheCurrentOwnersEvents() {
        val routing = mainActivity().start().resume()
        val private = privateActivity().start().resume()

        routing.pause().stop()

        assertFalse(routing.get().isApplicationEventHost())
        assertTrue(private.get().isApplicationEventHost())
    }

    @Test
    fun stoppedHostRemainsEventOwnerWhenNoOtherHostIsStarted() {
        val private = privateActivity().start().resume()
        private.pause().stop()

        assertTrue(private.get().isApplicationEventHost())
    }

    @Test
    fun restartingAnOlderHostRestoresItsOwnership() {
        val routing = mainActivity().start().resume()
        val private = privateActivity().start().resume()
        private.pause().stop()
        assertTrue(routing.get().isApplicationEventHost())

        private.start().resume()
        assertFalse(routing.get().isApplicationEventHost())
        assertTrue(private.get().isApplicationEventHost())
    }

    @Test
    fun destroyingCurrentHostSelectsRemainingStartedHost() {
        val routing = mainActivity().start().resume()
        val private = privateActivity().start().resume()
        private.pause().stop().destroy()
        activities.remove(private)

        assertTrue(routing.get().isApplicationEventHost())
    }

    @Test
    fun android6EventHostCheckDoesNotRestrictOtherVersions() {
        val routing = mainActivity().start().resume()
        val private = privateActivity().start().resume()
        assertFalse(routing.get().isApplicationEventHost())
        setSdk(Build.VERSION_CODES.P)
        try {
            assertTrue(routing.get().isApplicationEventHost())
            assertTrue(private.get().isApplicationEventHost())
        } finally {
            setSdk(Build.VERSION_CODES.M)
        }
    }

    private fun mainActivity(
        intent: Intent = launcherIntent()
    ): ActivityController<MainActivity> =
        Robolectric.buildActivity(MainActivity::class.java, intent).create().also {
            activities.add(it)
        }

    private fun privateActivity(
        intent: Intent = Intent(app, Android6KioskActivity::class.java)
    ): ActivityController<Android6KioskActivity> =
        Robolectric.buildActivity(Android6KioskActivity::class.java, intent)
            .create()
            .also {
                activities.add(it)
            }

    private fun launcherIntent() =
        Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setClass(app, MainActivity::class.java)
        }

    private fun homeIntent() =
        Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            setClass(app, MainActivity::class.java)
        }

    private fun assertPrivateTaskIntent(
        activity: MainActivity,
        fromHome: Boolean,
        lockRequested: Boolean,
    ) {
        val redirect = shadowOf(activity).nextStartedActivity
        assertNotNull("private kiosk redirect", redirect)
        requireNotNull(redirect)
        assertEquals(
            Android6KioskActivity::class.java.name,
            redirect.component?.className,
        )
        assertEquals(Intent.ACTION_MAIN, redirect.action)
        assertTrue(redirect.hasCategory(Intent.CATEGORY_LAUNCHER))
        assertFalse(redirect.hasCategory(Intent.CATEGORY_HOME))
        assertTrue(redirect.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertTrue(redirect.flags and Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED != 0)
        assertEquals(
            fromHome,
            redirect.getBooleanExtra(Constants.INTENT_HOME_LAUNCH, false),
        )
        assertEquals(
            lockRequested,
            redirect.getBooleanExtra(Constants.INTENT_ANDROID6_LOCK_TASK, false),
        )
    }

    private fun canClaimMqtt(activity: MainActivity): Boolean =
        ReflectionHelpers.callInstanceMethod(activity, "canHandleMqttMessages")

    @Suppress(
        "DEPRECATION"
    ) // Robolectric's lock-task flag has no non-deprecated shadow accessor.
    private fun shadowLockTaskStarted(activity: MainActivity): Boolean =
        shadowOf(activity).isLockTask

    private fun setSdk(sdk: Int) =
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", sdk)

    private fun monitoringField(name: String) =
        LockStateSingleton.javaClass.getDeclaredField(name).apply {
            isAccessible = true
        }
}
