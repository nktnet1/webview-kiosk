@file:Suppress("UsePropertyAccessSyntax")

package uk.nktnet.webviewkiosk.managers

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.UserManager
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
import uk.nktnet.webviewkiosk.WebviewKioskAdminReceiver
import uk.nktnet.webviewkiosk.config.data.DeviceOwnerMode
import uk.nktnet.webviewkiosk.config.data.ManagementUserType
import uk.nktnet.webviewkiosk.utils.applyLockTaskFeatures
import uk.nktnet.webviewkiosk.utils.setupLockTaskPackage

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 24, 26, 27], shadows = [LockTaskAuthorityShadow::class])
@Suppress("DEPRECATION") // Configure the system-user query on old Android runtimes.
class ManagementVersionCompatibilityTest {
    private val app = RuntimeEnvironment.getApplication()
    private val dpm =
        app.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val admin = ComponentName(app, WebviewKioskAdminReceiver::class.java)

    @Before
    fun setUp() {
        LockTaskAuthorityShadow.resetPolicy()
    }

    @Test
    fun deviceOwnerPreservesLegacyAllowlistAndNeverCallsFeatureApi() {
        shadowOf(dpm).setDeviceOwner(admin)
        // Seed the service shadow directly: the getter only became public in 26.
        val policy = shadowOf(dpm) as LockTaskAuthorityShadow
        policy.setLockTaskPackages(
            admin,
            arrayOf("existing.player", "uninstalled.package"),
        )
        DeviceOwnerManager.init(app)
        assertTrue(setupLockTaskPackage(app))
        assertEquals(
            setOf("existing.player", "uninstalled.package", app.packageName),
            policy.getLockTaskPackages(admin).toSet(),
        )
        assertEquals(
            DeviceOwnerMode.DeviceOwner,
            DeviceOwnerManager.status.value.mode,
        )
        assertFalse(DeviceOwnerManager.getCapabilities(app).lockTaskFeatures)
        assertEquals(0, LockTaskAuthorityShadow.featureWrites)
    }

    @Test
    fun unreadableLegacyPolicyIsNeverReplacedWithJustTheKioskPackage() {
        shadowOf(dpm).setDeviceOwner(admin)
        val policy = shadowOf(dpm) as LockTaskAuthorityShadow
        policy.setLockTaskPackages(admin, arrayOf("existing.player"))
        LockTaskAuthorityShadow.packageWrites = 0
        LockTaskAuthorityShadow.packagesAllowed = false
        DeviceOwnerManager.init(app)
        assertFalse(setupLockTaskPackage(app))
        assertEquals(0, LockTaskAuthorityShadow.packageWrites)
        LockTaskAuthorityShadow.packagesAllowed = true
        assertEquals(
            listOf("existing.player"),
            policy.getLockTaskPackages(admin).toList(),
        )
    }

    @Test
    fun workProfileScopeIsQueriedWhenAvailableAndOtherwiseRemainsUnknown() {
        shadowOf(dpm).setProfileOwner(admin)
        shadowOf(app.getSystemService(UserManager::class.java))
            .setIsSystemUser(false)
        LockTaskAuthorityShadow.managedProfile = true
        LockTaskAuthorityShadow.packagesAllowed = false
        LockTaskAuthorityShadow.featuresAllowed = false
        DeviceOwnerManager.init(app)
        assertTrue(DeviceOwnerManager.hasOwnerPermission(app))
        assertEquals(
            if (Build.VERSION.SDK_INT >= 24) {
                ManagementUserType.ManagedProfile
            } else {
                ManagementUserType.Unknown
            },
            DeviceOwnerManager.status.value.userType,
        )
        assertFalse(DeviceOwnerManager.getCapabilities(app).lockTaskPackages)
        assertFalse(DeviceOwnerManager.getCapabilities(app).lockDevice)
        assertFalse(DeviceOwnerManager.getCapabilities(app).removeOwner)
        applyLockTaskFeatures(app)
        assertEquals(0, LockTaskAuthorityShadow.featureWrites)
    }

    @Test
    fun independentProfileOwnerDoesNotAcquirePolicyCapabilityFromItsRole() {
        shadowOf(dpm).setProfileOwner(admin)
        shadowOf(app.getSystemService(UserManager::class.java)).setIsSystemUser(true)
        LockTaskAuthorityShadow.packagesAllowed = false
        DeviceOwnerManager.init(app)
        assertEquals(
            DeviceOwnerMode.ProfileOwner,
            DeviceOwnerManager.status.value.mode,
        )
        assertEquals(
            ManagementUserType.FullUser,
            DeviceOwnerManager.status.value.userType,
        )
        assertFalse(DeviceOwnerManager.getCapabilities(app).lockTaskPackages)
        assertFalse(DeviceOwnerManager.getCapabilities(app).lockTaskFeatures)
    }

    @Test
    @Config(sdk = [26, 27])
    fun affiliatedProfileOwnerCanUsePackagePolicyBeforeFeaturesWereIntroduced() {
        shadowOf(dpm).setProfileOwner(admin)
        DeviceOwnerManager.init(app)
        assertTrue(DeviceOwnerManager.getCapabilities(app).lockTaskPackages)
        assertFalse(DeviceOwnerManager.getCapabilities(app).lockTaskFeatures)
        assertTrue(setupLockTaskPackage(app))
        assertTrue(dpm.isLockTaskPermitted(app.packageName))
    }
}
