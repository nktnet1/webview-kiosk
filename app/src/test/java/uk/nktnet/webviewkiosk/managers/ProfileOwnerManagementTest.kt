@file:Suppress("UsePropertyAccessSyntax")

package uk.nktnet.webviewkiosk.managers

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.UserManager
import com.rosan.dhizuku.api.DhizukuRequestPermissionListener
import java.time.Duration
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
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowDevicePolicyManager
import org.robolectric.shadows.ShadowSystemClock
import org.robolectric.util.ReflectionHelpers
import uk.nktnet.webviewkiosk.MainActivity
import uk.nktnet.webviewkiosk.WebviewKioskAdminReceiver
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.data.DeviceOwnerMode
import uk.nktnet.webviewkiosk.config.data.ManagementOwner
import uk.nktnet.webviewkiosk.config.data.ManagementUserType
import uk.nktnet.webviewkiosk.config.option.LockStateType
import uk.nktnet.webviewkiosk.config.option.UnlockAuthRequirementOption
import uk.nktnet.webviewkiosk.utils.applyLockTaskFeatures
import uk.nktnet.webviewkiosk.utils.getAppInfo
import uk.nktnet.webviewkiosk.utils.openPackage
import uk.nktnet.webviewkiosk.utils.requireAuthForUnlock
import uk.nktnet.webviewkiosk.utils.setupLockTaskPackage
import uk.nktnet.webviewkiosk.utils.tryLockTask
import uk.nktnet.webviewkiosk.utils.tryUnlockTask

/**
 * Real production manager/utilities on Android 9–11, 13, and 17 Robolectric
 * runtimes. The policy shadow injects service permission failures that stock
 * Robolectric does not model. It does not emulate Android's provisioning service or
 * Binder UID forwarding; those require the device matrix in
 * docs/testing/profile-owner.md.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 29, 30, 33, 37], shadows = [LockTaskAuthorityShadow::class])
@LooperMode(LooperMode.Mode.PAUSED)
class ProfileOwnerManagementTest {
    private lateinit var app: Application
    private lateinit var dpm: DevicePolicyManager
    private lateinit var admin: ComponentName
    private lateinit var bridge: FakeSharedOwnerBridge
    private lateinit var originalBridge: DeviceOwnerManager.SharedOwnerBridge
    private var originalSdk = 0

    @Before
    fun setUp() {
        app = RuntimeEnvironment.getApplication()
        dpm =
            app.getSystemService(Context.DEVICE_POLICY_SERVICE)
                as DevicePolicyManager
        admin = ComponentName(app, WebviewKioskAdminReceiver::class.java)
        originalSdk = Build.VERSION.SDK_INT
        originalBridge = DeviceOwnerManager.sharedOwnerBridge
        bridge = FakeSharedOwnerBridge(dpm)
        DeviceOwnerManager.sharedOwnerBridge = bridge
        LockTaskAuthorityShadow.resetPolicy()
        DeviceOwnerManager.init(app)
        bridge.initCalled = false
    }

    @After
    fun tearDown() {
        ReflectionHelpers.setStaticField(
            Build.VERSION::class.java,
            "SDK_INT",
            originalSdk,
        )
        DeviceOwnerManager.sharedOwnerBridge = originalBridge
        DeviceOwnerManager.status.value = DeviceOwnerManager.Status()
    }

    @Test
    fun nativeFullUserProfileOwnerGetsLockTaskButNotDeviceOwnerIdentity() {
        shadowOf(dpm).setProfileOwner(admin)
        DeviceOwnerManager.init(app)

        assertEquals(
            DeviceOwnerMode.ProfileOwner,
            DeviceOwnerManager.status.value.mode,
        )
        assertEquals(
            ManagementOwner.ProfileOwner,
            DeviceOwnerManager.status.value.owner,
        )
        assertEquals(
            ManagementUserType.FullUser,
            DeviceOwnerManager.status.value.userType,
        )
        val capabilities = DeviceOwnerManager.getCapabilities(app)
        assertTrue(capabilities.lockTaskPackages)
        assertTrue(capabilities.lockTaskFeatures)
        assertTrue(capabilities.lockDevice)
        assertTrue(capabilities.transferOwnership)
        assertTrue(capabilities.removeOwner)
        assertFalse(bridge.initCalled)

        val info = getAppInfo(app)
        assertTrue(info.isProfileOwner)
        assertFalse(info.isDeviceOwner)
        assertFalse(info.dhizukuPermissionGranted)
        assertEquals("ProfileOwner", info.managementOwner)
    }

    @Test
    fun nativeDeviceOwnerKeepsExistingPackagesAndFeatureControls() {
        shadowOf(dpm).setDeviceOwner(admin)
        dpm.setLockTaskPackages(
            admin,
            arrayOf("existing.player", "uninstalled.package"),
        )
        DeviceOwnerManager.init(app)

        assertEquals(
            DeviceOwnerMode.DeviceOwner,
            DeviceOwnerManager.status.value.mode,
        )
        assertTrue(setupLockTaskPackage(app))
        assertEquals(
            setOf("existing.player", "uninstalled.package", app.packageName),
            dpm.getLockTaskPackages(admin).toSet(),
        )
        assertTrue(DeviceOwnerManager.getCapabilities(app).lockTaskFeatures)
    }

    @Test
    fun sharedFullUserProfileOwnerIsIdentifiedAfterPermissionGrant() {
        prepareSharedOwner(profileOwner = true, granted = false)
        assertEquals(DeviceOwnerMode.Dhizuku, DeviceOwnerManager.status.value.mode)
        assertEquals(ManagementOwner.Unknown, DeviceOwnerManager.status.value.owner)
        assertFalse(DeviceOwnerManager.getCapabilities(app).lockTaskPackages)

        var granted = 0
        DeviceOwnerManager.requestDhizukuPermission(app, onGranted = { granted++ })
        bridge.granted = true
        bridge.listener!!.onRequestPermission(PackageManager.PERMISSION_GRANTED)

        assertEquals(1, granted)
        assertEquals(
            ManagementOwner.ProfileOwner,
            DeviceOwnerManager.status.value.owner,
        )
        assertTrue(DeviceOwnerManager.getCapabilities(app).lockTaskPackages)
        assertTrue(DeviceOwnerManager.getCapabilities(app).lockTaskFeatures)
        assertFalse(DeviceOwnerManager.getCapabilities(app).removeOwner)
        assertFalse(DeviceOwnerManager.getCapabilities(app).transferOwnership)
        assertTrue(setupLockTaskPackage(app))
        assertTrue(getAppInfo(app).dhizukuPermissionGranted)
        assertFalse(getAppInfo(app).isProfileOwner)
    }

    @Test
    fun sharedDeviceOwnerKeepsItsRoleAndLockTaskAccess() {
        prepareSharedOwner(profileOwner = false)
        assertEquals(
            ManagementOwner.DeviceOwner,
            DeviceOwnerManager.status.value.owner,
        )
        assertTrue(DeviceOwnerManager.hasOwnerPermission(app))
        assertTrue(DeviceOwnerManager.getCapabilities(app).lockTaskFeatures)
        assertTrue(setupLockTaskPackage(app))
        assertFalse(DeviceOwnerManager.getCapabilities(app).removeOwner)
        assertFalse(getAppInfo(app).isDeviceOwner)
    }

    @Test
    fun ordinaryNativeWorkProfileCannotEditLockTaskOrLockWholeDevice() {
        shadowOf(dpm).setProfileOwner(admin)
        setWorkProfile()
        DeviceOwnerManager.init(app)
        assertTrue(DeviceOwnerManager.hasOwnerPermission(app))
        assertEquals(
            ManagementUserType.ManagedProfile,
            DeviceOwnerManager.status.value.userType,
        )
        assertUnsupportedWorkProfile()
        assertFalse(DeviceOwnerManager.getCapabilities(app).removeOwner)
        // Transfer is allowed for a native PO, without promoting its scope.
        assertTrue(DeviceOwnerManager.getCapabilities(app).transferOwnership)
    }

    @Test
    fun ordinarySharedWorkProfileCannotEditLockTaskOrLockWholeDevice() {
        setWorkProfile()
        prepareSharedOwner(profileOwner = true)
        assertEquals(
            ManagementOwner.ProfileOwner,
            DeviceOwnerManager.status.value.owner,
        )
        assertEquals(
            ManagementUserType.ManagedProfile,
            DeviceOwnerManager.status.value.userType,
        )
        assertUnsupportedWorkProfile()
    }

    @Test
    fun fullUserWithoutAffiliationCanBeDeniedEvenThoughItIsProfileOwner() {
        shadowOf(dpm).setProfileOwner(admin)
        DeviceOwnerManager.init(app)
        LockTaskAuthorityShadow.packagesAllowed = false
        LockTaskAuthorityShadow.featuresAllowed = false
        assertTrue(DeviceOwnerManager.hasOwnerPermission(app))
        assertEquals(
            ManagementUserType.FullUser,
            DeviceOwnerManager.status.value.userType,
        )
        assertFalse(DeviceOwnerManager.getCapabilities(app).lockTaskPackages)
        assertFalse(DeviceOwnerManager.editLockTaskPackage(app, "player.app", true))
        assertEquals(0, LockTaskAuthorityShadow.packageWrites)
    }

    @Test
    fun explicitPlatformEligibilityIsRespectedForAnAffiliatedProfile() {
        shadowOf(dpm).setProfileOwner(admin)
        LockTaskAuthorityShadow.managedProfile = true
        shadowOf(app.getSystemService(UserManager::class.java))
            .setManagedProfile(true)
        DeviceOwnerManager.init(app)
        assertTrue(DeviceOwnerManager.getCapabilities(app).lockTaskPackages)
        assertTrue(DeviceOwnerManager.getCapabilities(app).lockTaskFeatures)
        assertFalse(DeviceOwnerManager.getCapabilities(app).lockDevice)
    }

    @Test
    @Config(sdk = [33, 37])
    fun otherProfileTypesAreNotReportedAsFullAndroidUsers() {
        shadowOf(dpm).setProfileOwner(admin)
        val users = shadowOf(app.getSystemService(UserManager::class.java))
        val info =
            ReflectionHelpers.callInstanceMethod<Any>(
                users,
                "getUserInfo",
                ReflectionHelpers.ClassParameter.from(
                    Int::class.javaPrimitiveType!!,
                    ReflectionHelpers.callInstanceMethod(app, "getUserId"),
                ),
            )
        val profileFlag =
            ReflectionHelpers.getStaticField<Int>(
                Class.forName("android.content.pm.UserInfo"),
                "FLAG_PROFILE",
            )
        ReflectionHelpers.setField(
            info,
            "flags",
            ReflectionHelpers.getField<Int>(info, "flags") or profileFlag,
        )
        DeviceOwnerManager.init(app)
        assertEquals(
            ManagementUserType.Profile,
            DeviceOwnerManager.status.value.userType,
        )
        assertFalse(DeviceOwnerManager.getCapabilities(app).lockDevice)
        assertFalse(DeviceOwnerManager.getCapabilities(app).removeOwner)
    }

    @Test
    fun revokedSharedPermissionDisablesWritesAndReportsUnknownAuthority() {
        prepareSharedOwner(profileOwner = true)
        bridge.granted = false
        assertFalse(DeviceOwnerManager.hasOwnerPermission(app))
        assertEquals(ManagementOwner.Unknown, DeviceOwnerManager.status.value.owner)
        assertFalse(DeviceOwnerManager.editLockTaskPackage(app, "player.app", true))
        applyLockTaskFeatures(app)
        assertEquals(0, LockTaskAuthorityShadow.packageWrites)
        assertEquals(0, LockTaskAuthorityShadow.featureWrites)
    }

    @Test
    fun permissionResultDuringOwnerRefreshIsNotDiscardedAsANoneMode() {
        prepareSharedOwner(profileOwner = true, granted = false)
        var callbacks = 0
        DeviceOwnerManager.requestDhizukuPermission(app, onGranted = { callbacks++ })
        bridge.granted = true
        bridge.onInit = {
            bridge.listener!!.onRequestPermission(PackageManager.PERMISSION_GRANTED)
        }
        DeviceOwnerManager.init(app)
        assertEquals(1, callbacks)
        assertEquals(
            ManagementOwner.ProfileOwner,
            DeviceOwnerManager.status.value.owner,
        )
    }

    @Test
    fun deniedPermissionAndUnavailableSharedServiceNeverGrantAuthority() {
        prepareSharedOwner(profileOwner = true, granted = false)
        var denied = 0
        DeviceOwnerManager.requestDhizukuPermission(app, onDenied = { denied++ })
        bridge.listener!!.onRequestPermission(PackageManager.PERMISSION_DENIED)
        assertEquals(1, denied)
        assertFalse(DeviceOwnerManager.getCapabilities(app).lockTaskPackages)
        bridge.available = false
        DeviceOwnerManager.init(app)
        assertEquals(DeviceOwnerMode.None, DeviceOwnerManager.status.value.mode)
    }

    @Test
    fun deadSharedConnectionInvalidatesOwnerAndAnOutstandingPermissionResult() {
        prepareSharedOwner(profileOwner = true, granted = false)
        var callbacks = 0
        DeviceOwnerManager.requestDhizukuPermission(
            app,
            onGranted = { callbacks++ },
            onDenied = { callbacks++ },
        )
        bridge.failPermissionCheck = true
        assertFalse(DeviceOwnerManager.hasOwnerPermission(app))
        assertEquals(DeviceOwnerMode.None, DeviceOwnerManager.status.value.mode)
        bridge.listener!!.onRequestPermission(PackageManager.PERMISSION_GRANTED)
        assertEquals(0, callbacks)
    }

    @Test
    fun packageEditsUseTheFreshCompleteAllowlistRatherThanVisibleApps() {
        shadowOf(dpm).setProfileOwner(admin)
        DeviceOwnerManager.init(app)
        dpm.setLockTaskPackages(admin, arrayOf("not.installed", "original.app"))
        assertTrue(DeviceOwnerManager.editLockTaskPackage(app, "new.app", true))
        // Simulate another policy change while the UI dialog was open.
        dpm.setLockTaskPackages(admin, dpm.getLockTaskPackages(admin) + "late.entry")
        assertTrue(
            DeviceOwnerManager.editLockTaskPackage(app, "original.app", false)
        )
        assertEquals(
            setOf("not.installed", "new.app", "late.entry"),
            dpm.getLockTaskPackages(admin).toSet(),
        )
    }

    @Test
    fun writeRejectionDoesNotTurnAKioskLockRequestIntoScreenPinning() {
        shadowOf(dpm).setProfileOwner(admin)
        DeviceOwnerManager.init(app)
        LockTaskAuthorityShadow.rejectWrites = true
        val controller =
            Robolectric.buildActivity(RecordingLockActivity::class.java).setup()
        try {
            assertFalse(tryLockTask(controller.get()))
            assertEquals(0, controller.get().starts)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun externalAllowlistingStartsLockTaskWithoutOwnerPermissionOrPolicyWrites() {
        shadowOf(dpm).setDeviceOwner(bridge.component)
        dpm.setLockTaskPackages(
            bridge.component,
            arrayOf(app.packageName, "other.app"),
        )
        LockTaskAuthorityShadow.packageWrites = 0
        val controller =
            Robolectric.buildActivity(RecordingLockActivity::class.java).setup()
        try {
            assertFalse(DeviceOwnerManager.hasOwnerPermission(app))
            assertTrue(tryLockTask(controller.get()))
            assertEquals(
                LockStateType.LOCK_TASK,
                LockStateType.fromActivityManager(
                    app.getSystemService(ActivityManager::class.java)
                ),
            )
            assertTrue(tryUnlockTask(controller.get()))
            assertEquals(0, LockTaskAuthorityShadow.packageWrites)
            assertEquals(0, LockTaskAuthorityShadow.featureWrites)
            assertEquals(
                setOf(app.packageName, "other.app"),
                dpm.getLockTaskPackages(bridge.component).toSet(),
            )
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun externalDpcAllowlistingCanLaunchAppsWithoutLocalHomePreference() {
        shadowOf(dpm).setDeviceOwner(bridge.component)
        dpm.setLockTaskPackages(bridge.component, arrayOf(app.packageName))
        UserSettings(app).lockTaskFeatureHome = false
        shadowOf(app.getSystemService(ActivityManager::class.java))
            .setLockTaskModeState(ActivityManager.LOCK_TASK_MODE_LOCKED)
        LockTaskAuthorityShadow.packageWrites = 0

        openPackage(
            app,
            app.packageName,
            intent = Intent(app, MainActivity::class.java),
        )

        assertNotNull(shadowOf(app).nextStartedActivity)
        assertEquals(0, LockTaskAuthorityShadow.packageWrites)
        assertEquals(0, LockTaskAuthorityShadow.featureWrites)
    }

    @Test
    fun pinningCannotLaunchAnotherTaskEvenIfTheAppBecomesAllowlisted() {
        shadowOf(dpm).setDeviceOwner(bridge.component)
        dpm.setLockTaskPackages(bridge.component, arrayOf(app.packageName))
        shadowOf(app.getSystemService(ActivityManager::class.java))
            .setLockTaskModeState(ActivityManager.LOCK_TASK_MODE_PINNED)

        openPackage(
            app,
            app.packageName,
            intent = Intent(app, MainActivity::class.java),
        )

        assertNull(shadowOf(app).nextStartedActivity)
        assertEquals(0, LockTaskAuthorityShadow.featureWrites)
    }

    @Test
    fun defaultUnlockAuthenticationUsesActualLockStateRatherThanOwnerIdentity() {
        val settings = UserSettings(app)
        settings.unlockAuthRequirement = UnlockAuthRequirementOption.DEFAULT
        val manager = app.getSystemService(ActivityManager::class.java)
        shadowOf(manager).setLockTaskModeState(ActivityManager.LOCK_TASK_MODE_LOCKED)
        assertTrue(requireAuthForUnlock(app, settings))
        shadowOf(dpm).setDeviceOwner(bridge.component)
        dpm.setLockTaskPackages(bridge.component, arrayOf(app.packageName))
        shadowOf(manager).setLockTaskModeState(ActivityManager.LOCK_TASK_MODE_PINNED)
        assertFalse(requireAuthForUnlock(app, settings))
        settings.unlockAuthRequirement = UnlockAuthRequirementOption.REQUIRE
        assertTrue(requireAuthForUnlock(app, settings))
        settings.unlockAuthRequirement = UnlockAuthRequirementOption.OFF
        shadowOf(manager).setLockTaskModeState(ActivityManager.LOCK_TASK_MODE_LOCKED)
        assertFalse(requireAuthForUnlock(app, settings))
    }

    @Test
    fun nativeProfileOwnerRemovalUsesTheProfileOwnerApi() {
        shadowOf(dpm).setProfileOwner(admin)
        DeviceOwnerManager.init(app)
        DeviceOwnerManager.removeNativeOwner(app)
        assertFalse(dpm.isProfileOwnerApp(app.packageName))
        assertEquals(DeviceOwnerMode.None, DeviceOwnerManager.status.value.mode)
    }

    @Test
    fun nonAllowlistedWorkProfileUsesPinningAndReportsItSeparately() {
        shadowOf(dpm).setProfileOwner(admin)
        setWorkProfile()
        DeviceOwnerManager.init(app)
        val controller =
            Robolectric.buildActivity(RecordingLockActivity::class.java).setup()
        try {
            assertTrue(tryLockTask(controller.get()))
            assertEquals(LockStateType.SCREEN_PINNING, getAppInfo(app).lockState)
            assertFalse(getAppInfo(app).isLockTaskPermitted)
            assertTrue(tryUnlockTask(controller.get()))
            assertEquals(LockStateType.NONE, getAppInfo(app).lockState)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun unlockRestoresPackagesAndFeaturesForEligibleProfileOwner() {
        shadowOf(dpm).setProfileOwner(admin)
        DeviceOwnerManager.init(app)
        val packages = arrayOf(app.packageName, "other.app", "not.installed")
        dpm.setLockTaskPackages(admin, packages)
        dpm.setLockTaskFeatures(admin, DevicePolicyManager.LOCK_TASK_FEATURE_HOME)
        val controller =
            Robolectric.buildActivity(RecordingLockActivity::class.java).setup()
        try {
            assertTrue(tryUnlockTask(controller.get()))
            assertEquals(packages.toSet(), dpm.getLockTaskPackages(admin).toSet())
            assertEquals(
                DevicePolicyManager.LOCK_TASK_FEATURE_HOME,
                dpm.getLockTaskFeatures(admin),
            )
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun unlockRestoresTheAllowlistAfterAPartiallyAppliedWriteFails() {
        shadowOf(dpm).setProfileOwner(admin)
        DeviceOwnerManager.init(app)
        val packages = arrayOf(app.packageName, "external.player", "not.installed")
        dpm.setLockTaskPackages(admin, packages)
        LockTaskAuthorityShadow.failAfterNextPackageWrite = true
        val controller =
            Robolectric.buildActivity(RecordingLockActivity::class.java).setup()
        try {
            assertTrue(tryUnlockTask(controller.get()))
            assertEquals(packages.toSet(), dpm.getLockTaskPackages(admin).toSet())
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun packagePolicyUnlockDoesNotRequireFeaturePolicyPermission() {
        shadowOf(dpm).setProfileOwner(admin)
        DeviceOwnerManager.init(app)
        val packages = arrayOf(app.packageName, "external.player")
        dpm.setLockTaskPackages(admin, packages)
        LockTaskAuthorityShadow.featuresAllowed = false
        LockTaskAuthorityShadow.packageWrites = 0
        val controller =
            Robolectric.buildActivity(RecordingLockActivity::class.java).setup()
        try {
            assertTrue(tryUnlockTask(controller.get()))
            assertEquals(2, LockTaskAuthorityShadow.packageWrites)
            assertEquals(0, LockTaskAuthorityShadow.featureWrites)
            assertEquals(packages.toSet(), dpm.getLockTaskPackages(admin).toSet())
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun featureDependenciesAndApi30FlagAreKeptForProfileOwners() {
        shadowOf(dpm).setProfileOwner(admin)
        DeviceOwnerManager.init(app)
        UserSettings(app).apply {
            lockTaskFeatureHome = false
            lockTaskFeatureOverview = true
            lockTaskFeatureNotifications = true
            lockTaskFeatureBlockActivityStartInTask = true
        }
        applyLockTaskFeatures(app)
        val flags = dpm.getLockTaskFeatures(admin)
        assertEquals(0, flags and DevicePolicyManager.LOCK_TASK_FEATURE_OVERVIEW)
        assertEquals(
            0,
            flags and DevicePolicyManager.LOCK_TASK_FEATURE_NOTIFICATIONS,
        )
        assertEquals(
            Build.VERSION.SDK_INT >= 30,
            flags and
                DevicePolicyManager.LOCK_TASK_FEATURE_BLOCK_ACTIVITY_START_IN_TASK !=
                0,
        )
    }

    @Test
    fun legacyApiGatesPreserveDeviceOwnerAllowlistingWithoutFeatureCalls() {
        shadowOf(dpm).setDeviceOwner(admin)
        dpm.setLockTaskPackages(admin, arrayOf("existing.app"))
        // Exercise production version guards using the API 28+ runtime, not an
        // assertion that Robolectric is emulating Android 5/6/8 here.
        for (sdk in listOf(21, 23, 26, 27)) {
            ReflectionHelpers.setStaticField(
                Build.VERSION::class.java,
                "SDK_INT",
                sdk,
            )
            DeviceOwnerManager.init(app)
            assertTrue(setupLockTaskPackage(app))
            assertTrue("existing.app" in DeviceOwnerManager.getLockTaskPackages(app))
            assertFalse(DeviceOwnerManager.getCapabilities(app).lockTaskFeatures)
        }
        assertEquals(0, LockTaskAuthorityShadow.featureWrites)
    }

    private fun prepareSharedOwner(profileOwner: Boolean, granted: Boolean = true) {
        if (profileOwner) {
            shadowOf(dpm).setProfileOwner(bridge.component)
        } else {
            shadowOf(dpm).setDeviceOwner(bridge.component)
        }
        bridge.available = true
        bridge.granted = granted
        ShadowSystemClock.advanceBy(Duration.ofMillis(1))
        DeviceOwnerManager.init(app)
        ShadowSystemClock.advanceBy(Duration.ofMillis(1100))
        DeviceOwnerManager.init(app)
    }

    private fun setWorkProfile() {
        LockTaskAuthorityShadow.managedProfile = true
        LockTaskAuthorityShadow.packagesAllowed = false
        LockTaskAuthorityShadow.featuresAllowed = false
        shadowOf(app.getSystemService(UserManager::class.java))
            .setManagedProfile(true)
    }

    private fun assertUnsupportedWorkProfile() {
        val capabilities = DeviceOwnerManager.getCapabilities(app)
        assertFalse(capabilities.lockTaskPackages)
        assertFalse(capabilities.lockTaskFeatures)
        assertFalse(capabilities.lockDevice)
        assertFalse(setupLockTaskPackage(app))
        applyLockTaskFeatures(app)
        assertEquals(0, LockTaskAuthorityShadow.packageWrites)
        assertEquals(0, LockTaskAuthorityShadow.featureWrites)
    }

    private class FakeSharedOwnerBridge(private val dpm: DevicePolicyManager) :
        DeviceOwnerManager.SharedOwnerBridge {
        val component = ComponentName("external.dpc", "external.dpc.Admin")
        var available = false
        var granted = true
        var failPermissionCheck = false
        var initCalled = false
        var listener: DhizukuRequestPermissionListener? = null
        var onInit: (() -> Unit)? = null

        override fun init(context: Context): Boolean {
            initCalled = true
            val callback = onInit
            onInit = null
            callback?.invoke()
            return available
        }

        override fun ownerComponent() = component

        override fun permissionGranted(): Boolean {
            if (failPermissionCheck) {
                throw SecurityException("binder unavailable")
            }
            return granted
        }

        override fun requestPermission(listener: DhizukuRequestPermissionListener) {
            this.listener = listener
        }

        override fun policyManager(context: Context) = dpm
    }
}

@Implements(DevicePolicyManager::class)
class LockTaskAuthorityShadow : ShadowDevicePolicyManager() {
    companion object {
        var managedProfile = false
        var packagesAllowed = true
        var featuresAllowed = true
        var rejectWrites = false
        var failAfterNextPackageWrite = false
        var packageWrites = 0
        var featureWrites = 0

        fun resetPolicy() {
            managedProfile = false
            packagesAllowed = true
            featuresAllowed = true
            rejectWrites = false
            failAfterNextPackageWrite = false
            packageWrites = 0
            featureWrites = 0
        }
    }

    // Robolectric invokes this hook reflectively. Stock Robolectric does not
    // model this owner query on API 24–29; keep it even if an IDE reports no uses.
    @Suppress("UNUSED_PARAMETER")
    @Implementation(minSdk = 24)
    fun isManagedProfile(admin: ComponentName): Boolean = managedProfile

    @Implementation
    public override fun getLockTaskPackages(admin: ComponentName): Array<String> {
        if (!packagesAllowed) {
            throw SecurityException("Lock Task is unavailable for this user")
        }
        return super.getLockTaskPackages(admin)
    }

    @Implementation
    public override fun setLockTaskPackages(
        admin: ComponentName,
        packages: Array<String>,
    ) {
        if (!packagesAllowed || rejectWrites) {
            throw SecurityException("Lock Task permission denied")
        }
        packageWrites++
        super.setLockTaskPackages(admin, packages)
        if (failAfterNextPackageWrite) {
            failAfterNextPackageWrite = false
            throw SecurityException("Failure after applying the temporary policy")
        }
    }

    @Implementation(minSdk = 28)
    public override fun getLockTaskFeatures(admin: ComponentName): Int {
        if (!featuresAllowed) {
            throw SecurityException("Lock Task features unavailable")
        }
        return super.getLockTaskFeatures(admin)
    }

    @Implementation(minSdk = 28)
    public override fun setLockTaskFeatures(admin: ComponentName, flags: Int) {
        if (!featuresAllowed || rejectWrites) {
            throw SecurityException("Lock Task features denied")
        }
        featureWrites++
        super.setLockTaskFeatures(admin, flags)
    }
}

/** Records the policy decision; does not claim to emulate ActivityTaskManager. */
class RecordingLockActivity : Activity() {
    var starts = 0

    override fun startLockTask() {
        starts++
        val permitted =
            getSystemService(DevicePolicyManager::class.java)
                .isLockTaskPermitted(packageName)
        shadowOf(getSystemService(ActivityManager::class.java))
            .setLockTaskModeState(
                if (permitted) {
                    ActivityManager.LOCK_TASK_MODE_LOCKED
                } else {
                    ActivityManager.LOCK_TASK_MODE_PINNED
                }
            )
    }

    override fun stopLockTask() {
        shadowOf(getSystemService(ActivityManager::class.java))
            .setLockTaskModeState(ActivityManager.LOCK_TASK_MODE_NONE)
    }
}
