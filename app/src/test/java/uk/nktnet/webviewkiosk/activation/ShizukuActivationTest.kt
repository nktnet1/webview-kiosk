package uk.nktnet.webviewkiosk.activation

import android.app.Application
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Looper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import rikka.shizuku.Shizuku
import uk.nktnet.webviewkiosk.WebviewKioskAdminReceiver
import uk.nktnet.webviewkiosk.config.data.DeviceOwnerMode
import uk.nktnet.webviewkiosk.managers.DeviceOwnerManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
@LooperMode(LooperMode.Mode.PAUSED)
class ShizukuActivationTest {
    private lateinit var app: Application
    private lateinit var access: FakeAccess
    private lateinit var scope: CoroutineScope
    private lateinit var dpm: DevicePolicyManager
    private var grantNativeOwner = true
    private var serviceFailure = false
    private var remoteMessage: String? = "Success"
    private var remoteOutput = "Success"
    private var remoteSuccess = true
    private var serviceStarted: CountDownLatch? = null
    private var serviceRelease: CountDownLatch? = null
    private val calls = mutableListOf<Pair<Boolean, Int>>()

    @Before
    fun setUp() {
        app = RuntimeEnvironment.getApplication()
        dpm =
            app.getSystemService(Context.DEVICE_POLICY_SERVICE)
                as DevicePolicyManager
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        access = FakeAccess()
    }

    @After
    fun tearDown() {
        serviceRelease?.countDown()
        scope.cancel()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun unavailableServerDoesNotPromptOrBind() {
        access.available = false
        val result = complete(start())
        assertFalse(result.success)
        assertTrue(result.output.contains("not running"))
        assertEquals(0, access.requests)
        assertEquals(0, access.binds)
        assertClean()
    }

    @Test
    fun deniedPermissionCleansListenersWithoutBinding() {
        access.permission = false
        access.request = { access.reply(it, PackageManager.PERMISSION_DENIED) }
        assertFalse(complete(start()).success)
        assertEquals(0, access.binds)
        assertClean()
    }

    @Test
    fun permanentlyDeniedPermissionDoesNotPromptAgain() {
        access.permission = false
        access.permanentlyDenied = true
        assertFalse(complete(start()).success)
        assertEquals(0, access.requests)
        assertClean()
    }

    @Test
    fun unrelatedPermissionReplyCannotStartActivation() {
        access.permission = false
        access.request = { access.reply(it + 1, PackageManager.PERMISSION_GRANTED) }
        val pending = start()
        idleUntil { access.requests == 1 }
        assertFalse(pending.isCompleted)
        assertEquals(0, access.binds)
        access.reply(access.lastRequest, PackageManager.PERMISSION_GRANTED)
        assertTrue(complete(pending).success)
        assertEquals(1, access.binds)
        assertClean()
    }

    @Test
    fun serverDeathWhileWaitingForPermissionTerminatesRequest() {
        access.permission = false
        access.request = { access.die() }
        assertFalse(complete(start()).success)
        assertEquals(0, access.binds)
        assertClean()
    }

    @Test
    fun serviceBindingFailureStillUnbindsAndCleansListeners() {
        access.binding = { error("Binding failed") }
        val result = complete(start())
        assertEquals("Binding failed", result.output)
        assertTrue(result.diagnostics.contains("Binding failed"))
        assertEquals(1, access.unbinds)
        assertClean()
    }

    @Test
    fun serverDeathWhileBindingDoesNotLeaveRequestPending() {
        access.binding = { access.die() }
        assertFalse(complete(start()).success)
        assertEquals(1, access.unbinds)
        assertClean()
    }

    @Test
    fun deviceOwnerSuccessIsVerifiedAndNative() {
        assertTrue(complete(start()).success)
        assertEquals(listOf(false to 0), calls)
        assertEquals(
            DeviceOwnerMode.DeviceOwner,
            DeviceOwnerManager.status.value.mode,
        )
        assertEquals(1, access.unbinds)
        assertClean()
    }

    @Test
    @Config(sdk = [23, 30])
    fun android6And11CanStartShizukuActivation() {
        val result = complete(start())
        assertTrue(result.success)
        assertEquals(0, access.requests)
        assertEquals(1, access.binds)
        assertEquals(listOf(false to 0), calls)
        assertTrue(
            app.resources.getBoolean(uk.nktnet.webviewkiosk.R.bool.shizuku_supported)
        )
        assertClean()
    }

    @Test
    fun repeatedActivationDoesNotStartAnotherPrivilegedTransaction() {
        assertTrue(complete(start()).success)
        val result = complete(start(true))
        assertFalse(result.success)
        assertTrue(result.output.contains("already has native ownership"))
        assertEquals(1, access.binds)
        assertEquals(1, calls.size)
        assertClean()
    }

    @Test
    fun profileOwnerSuccessIsVerifiedAndNative() {
        assertTrue(complete(start(true)).success)
        assertEquals(listOf(true to 0), calls)
        assertEquals(
            DeviceOwnerMode.ProfileOwner,
            DeviceOwnerManager.status.value.mode,
        )
        assertClean()
    }

    @Test
    fun successfulExitWithoutNativeOwnershipIsNotPresentedAsSuccess() {
        grantNativeOwner = false
        val result = complete(start(true))
        assertFalse(result.success)
        assertTrue(result.output.contains("did not confirm"))
        assertClean()
    }

    @Test
    fun remoteExceptionPreservesErrorAndUnbinds() {
        serviceFailure = true
        val result = complete(start())
        assertFalse(result.success)
        assertEquals("Service failed", result.output)
        assertTrue(result.diagnostics.contains("Service failed"))
        assertEquals(1, access.unbinds)
        assertClean()
    }

    @Test
    fun legacyServiceCannotDumpAnAndroidStacktraceIntoTheResultDialog() {
        grantNativeOwner = false
        remoteSuccess = false
        remoteMessage = null
        remoteOutput =
            "java.lang.IllegalStateException: accounts on the device\n" +
                "    at android.app.admin.DevicePolicyManager.setDeviceOwner(DPM.java:12)"
        val result = complete(start())
        assertFalse(result.success)
        assertEquals(
            "java.lang.IllegalStateException: accounts on the device",
            result.output,
        )
        assertFalse(result.output.contains("DPM.java"))
        assertTrue(result.diagnostics.contains("DPM.java"))
        assertClean()
    }

    @Test
    fun navigationWhileWaitingForPermissionRemovesListeners() {
        access.permission = false
        access.request = {}
        val pending = start()
        idleUntil { access.requests == 1 }
        pending.cancel()
        idleUntil { pending.isCompleted }
        assertEquals(0, access.binds)
        assertClean()
    }

    @Test
    fun latePermissionReplyFromCancelledAttemptCannotCompleteANewRequest() {
        access.permission = false
        access.request = {}
        val first = start()
        idleUntil { access.requests == 1 }
        val oldCode = access.lastRequest
        first.cancel()
        idleUntil { first.isCompleted }
        val second = start()
        idleUntil { access.requests == 2 }
        access.reply(oldCode, PackageManager.PERMISSION_GRANTED)
        assertEquals(0, access.binds)
        assertFalse(second.isCompleted)
        access.reply(access.lastRequest, PackageManager.PERMISSION_GRANTED)
        assertTrue(complete(second).success)
        assertClean()
    }

    @Test
    fun navigationDuringActivationWaitsForRemoteCleanupBeforeUnbinding() {
        serviceStarted = CountDownLatch(1)
        serviceRelease = CountDownLatch(1)
        val pending = start()
        idleUntil { serviceStarted!!.count == 0L }
        pending.cancel()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, access.unbinds)
        assertFalse(pending.isCompleted)
        serviceRelease!!.countDown()
        idleUntil { pending.isCompleted }
        assertEquals(1, access.unbinds)
        assertClean()
    }

    private fun start(profile: Boolean = false) = scope.async {
        ShizukuActivation.activate(app, profile, access)
    }

    private fun complete(pending: Deferred<ActivationResult>): ActivationResult {
        idleUntil { pending.isCompleted }
        @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
        return pending.getCompleted()
    }

    private fun idleUntil(done: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (!done() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.yield()
        }
        assertTrue("Activation did not finish", done())
    }

    private fun assertClean() {
        assertTrue(access.permissions.isEmpty())
        assertTrue(access.deaths.isEmpty())
    }

    private inner class FakeAccess : ShizukuAccess {
        var available = true
        var permission = true
        var permanentlyDenied = false
        var requests = 0
        var lastRequest = -1
        var binds = 0
        var unbinds = 0
        val permissions = mutableListOf<Shizuku.OnRequestPermissionResultListener>()
        val deaths = mutableListOf<Shizuku.OnBinderDeadListener>()
        var request: (Int) -> Unit = { reply(it, PackageManager.PERMISSION_GRANTED) }
        var binding: (ServiceConnection) -> Unit = {
            it.onServiceConnected(
                null,
                object : IActivationService.Stub() {
                    override fun activate(
                        profileOwner: Boolean,
                        userId: Int,
                    ): Bundle {
                        calls += profileOwner to userId
                        serviceStarted?.countDown()
                        check(serviceRelease?.await(10, TimeUnit.SECONDS) != false)
                        check(!serviceFailure) { "Service failed" }
                        if (grantNativeOwner) {
                            val admin =
                                ComponentName(
                                    app,
                                    WebviewKioskAdminReceiver::class.java,
                                )
                            if (profileOwner) {
                                shadowOf(dpm).setProfileOwner(admin)
                            } else {
                                shadowOf(dpm).setDeviceOwner(admin)
                            }
                        }
                        return Bundle().apply {
                            putBoolean("success", remoteSuccess)
                            putString("output", remoteOutput)
                            remoteMessage?.let { putString("message", it) }
                        }
                    }

                    override fun destroy() = Unit
                },
            )
        }

        override fun checkAvailable() {
            check(available) { "Shizuku is not running" }
        }

        override fun permissionGranted() = permission

        override fun permissionDeniedPermanently() = permanentlyDenied

        override fun addPermissionListener(
            listener: Shizuku.OnRequestPermissionResultListener
        ) {
            permissions += listener
        }

        override fun removePermissionListener(
            listener: Shizuku.OnRequestPermissionResultListener
        ) {
            permissions -= listener
        }

        override fun addDeathListener(listener: Shizuku.OnBinderDeadListener) {
            deaths += listener
        }

        override fun removeDeathListener(listener: Shizuku.OnBinderDeadListener) {
            deaths -= listener
        }

        override fun requestPermission(code: Int) {
            requests++
            lastRequest = code
            request(code)
        }

        override fun bind(
            args: Shizuku.UserServiceArgs,
            connection: ServiceConnection,
        ) {
            binds++
            binding(connection)
        }

        override fun unbind(
            args: Shizuku.UserServiceArgs,
            connection: ServiceConnection,
        ) {
            unbinds++
        }

        fun reply(code: Int, grant: Int) {
            permissions.toList().forEach {
                it.onRequestPermissionResult(code, grant)
            }
        }

        fun die() {
            deaths.toList().forEach { it.onBinderDead() }
        }
    }
}
