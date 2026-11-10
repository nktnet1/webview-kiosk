package uk.nktnet.webviewkiosk.managers

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.rosan.dhizuku.api.DhizukuRequestPermissionListener
import java.time.Duration
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
import org.robolectric.shadows.ShadowSystemClock
import org.robolectric.util.ReflectionHelpers
import uk.nktnet.webviewkiosk.WebviewKioskAdminReceiver
import uk.nktnet.webviewkiosk.config.data.DeviceOwnerMode

/** Exercises production version gates with a fake Dhizuku transport. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 28], shadows = [LockTaskAuthorityShadow::class])
class DhizukuVersionCompatibilityTest {
    private val app = RuntimeEnvironment.getApplication()
    private val dpm =
        app.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private lateinit var original: DeviceOwnerManager.SharedOwnerBridge
    private lateinit var bridge: FakeBridge

    @Before
    fun setUp() {
        LockTaskAuthorityShadow.resetPolicy()
        original = DeviceOwnerManager.sharedOwnerBridge
        bridge = FakeBridge()
        DeviceOwnerManager.sharedOwnerBridge = bridge
        DeviceOwnerManager.init(app)
    }

    @After
    fun tearDown() {
        DeviceOwnerManager.sharedOwnerBridge = original
        DeviceOwnerManager.status.value = DeviceOwnerManager.Status()
    }

    @Test
    fun sharedDeviceOwnerWorksFromAndroid6AndPreservesFeatureGates() {
        connect()
        assertEquals(DeviceOwnerMode.Dhizuku, DeviceOwnerManager.status.value.mode)
        assertTrue(DeviceOwnerManager.hasOwnerPermission(app))
        assertTrue(
            DeviceOwnerManager.editLockTaskPackage(app, "existing.player", true)
        )
        assertTrue("existing.player" in DeviceOwnerManager.getLockTaskPackages(app))
        if (Build.VERSION.SDK_INT < 28) {
            assertFalse(DeviceOwnerManager.getCapabilities(app).lockTaskFeatures)
        }
    }

    @Test
    fun permissionCanBeRequestedOnAndroid6() {
        bridge.granted = false
        connect()
        var approved = 0
        DeviceOwnerManager.requestDhizukuPermission(app, onGranted = { approved++ })
        assertEquals(1, bridge.requests)
        bridge.granted = true
        bridge.listener!!.onRequestPermission(PackageManager.PERMISSION_GRANTED)
        assertEquals(1, approved)
        assertTrue(DeviceOwnerManager.hasOwnerPermission(app))
    }

    @Test
    fun nativeOwnershipStillTakesPrecedence() {
        shadowOf(dpm)
            .setDeviceOwner(
                ComponentName(app, WebviewKioskAdminReceiver::class.java)
            )
        bridge.available = true
        bridge.initializations = 0
        DeviceOwnerManager.init(app)
        assertEquals(
            DeviceOwnerMode.DeviceOwner,
            DeviceOwnerManager.status.value.mode,
        )
        assertEquals(0, bridge.initializations)
    }

    @Test
    @Config(sdk = [23])
    fun platformBridgeDoesNotNeedHiddenApiBypassOnAndroid6() {
        // No configured owner is an expected, clean false from the real API.
        assertFalse(original.init(app))
    }

    @Test
    @Config(sdk = [28])
    fun android5StillCannotUseDhizuku() {
        val sdk = Build.VERSION.SDK_INT
        try {
            ReflectionHelpers.setStaticField(
                Build.VERSION::class.java,
                "SDK_INT",
                21,
            )
            bridge.available = true
            bridge.initializations = 0
            DeviceOwnerManager.init(app)
            assertEquals(DeviceOwnerMode.None, DeviceOwnerManager.status.value.mode)
            assertEquals(0, bridge.initializations)
        } finally {
            ReflectionHelpers.setStaticField(
                Build.VERSION::class.java,
                "SDK_INT",
                sdk,
            )
        }
    }

    private fun connect() {
        shadowOf(dpm).setDeviceOwner(bridge.admin)
        bridge.available = true
        ShadowSystemClock.advanceBy(Duration.ofMillis(1))
        DeviceOwnerManager.init(app)
        ShadowSystemClock.advanceBy(Duration.ofMillis(1100))
        DeviceOwnerManager.init(app)
    }

    private inner class FakeBridge : DeviceOwnerManager.SharedOwnerBridge {
        val admin = ComponentName("external.dpc", "external.dpc.Admin")
        var available = false
        var granted = true
        var initializations = 0
        var requests = 0
        var listener: DhizukuRequestPermissionListener? = null

        override fun init(context: Context): Boolean {
            initializations++
            return available
        }

        override fun ownerComponent() = admin

        override fun permissionGranted() = granted

        override fun requestPermission(listener: DhizukuRequestPermissionListener) {
            requests++
            this.listener = listener
        }

        override fun policyManager(context: Context) = dpm
    }
}
