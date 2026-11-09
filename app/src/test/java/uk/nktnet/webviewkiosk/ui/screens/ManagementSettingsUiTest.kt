package uk.nktnet.webviewkiosk.ui.screens

import android.content.RestrictionsManager
import android.os.Bundle
import android.os.Looper
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import uk.nktnet.webviewkiosk.R
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.config.data.DeviceOwnerMode
import uk.nktnet.webviewkiosk.config.data.ManagementCapabilities
import uk.nktnet.webviewkiosk.config.data.ManagementOwner
import uk.nktnet.webviewkiosk.config.data.ManagementUserType
import uk.nktnet.webviewkiosk.managers.DeviceOwnerManager
import uk.nktnet.webviewkiosk.ui.components.setting.ManagementStatusCard
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device.owner.locktaskfeature.LockTaskFeatureBlockActivityStartInTaskSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device.owner.locktaskfeature.LockTaskFeatureGlobalActionsSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device.owner.locktaskfeature.LockTaskFeatureHomeSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device.owner.locktaskfeature.LockTaskFeatureKeyguardSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device.owner.locktaskfeature.LockTaskFeatureNotificationsSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device.owner.locktaskfeature.LockTaskFeatureOverviewSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fielditems.device.owner.locktaskfeature.LockTaskFeatureSystemInfoSetting
import uk.nktnet.webviewkiosk.ui.components.setting.fields.BooleanSettingFieldItem

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], qualifiers = "w480dp-h1000dp")
@LooperMode(LooperMode.Mode.PAUSED)
class ManagementSettingsUiTest {
    private lateinit var controller: ActivityController<AppCompatActivity>
    private lateinit var view: ComposeView
    private lateinit var recomposer: Recomposer
    private val roots = mutableListOf<ViewRootForTest>()
    private val clock = BroadcastFrameClock()
    private val scope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + clock)
    private var frame = 0L
    private val profileOwner =
        DeviceOwnerManager.Status(
            DeviceOwnerMode.ProfileOwner,
            ManagementOwner.ProfileOwner,
            ManagementUserType.FullUser,
        )

    @Before
    fun setUp() {
        ViewRootForTest.onViewCreatedCallback = { roots += it }
        controller =
            Robolectric.buildActivity(AppCompatActivity::class.java)
                .setup()
                .visible()
        recomposer = Recomposer(scope.coroutineContext)
        scope.launch { recomposer.runRecomposeAndApplyChanges() }
        view = ComposeView(controller.get())
        view.setParentCompositionContext(recomposer)
        controller.get().setContentView(view)
    }

    @After
    fun tearDown() {
        view.disposeComposition()
        recomposer.cancel()
        scope.cancel()
        controller.pause().stop().destroy()
        ViewRootForTest.onViewCreatedCallback = null
    }

    @Test
    fun cardSeparatesOwnerSourceAndScopeForNativeAndDhizukuModes() {
        for ((mode, owner) in
            listOf(
                DeviceOwnerMode.DeviceOwner to ManagementOwner.DeviceOwner,
                DeviceOwnerMode.ProfileOwner to ManagementOwner.ProfileOwner,
                DeviceOwnerMode.Dhizuku to ManagementOwner.DeviceOwner,
                DeviceOwnerMode.Dhizuku to ManagementOwner.ProfileOwner,
            )) {
            render {
                ManagementStatusCard(
                    profileOwner.copy(mode = mode, owner = owner),
                    true,
                    ManagementCapabilities(
                        lockTaskPackages = true,
                        lockTaskFeatures = true,
                    ),
                )
            }
            assertText(
                if (owner == ManagementOwner.ProfileOwner) {
                    "Profile Owner"
                } else {
                    "Device Owner"
                }
            )
            assertText(
                if (mode == DeviceOwnerMode.Dhizuku) {
                    "Dhizuku"
                } else {
                    "Native"
                }
            )
            assertText("Full User")
            assertText(message(R.string.ui_management_lock_task_available))
        }
    }

    @Test
    fun workProfileCardDoesNotClaimLockTaskPolicyAccess() {
        render {
            ManagementStatusCard(
                profileOwner.copy(userType = ManagementUserType.ManagedProfile),
                true,
                ManagementCapabilities(),
            )
        }
        assertText("Profile Owner")
        assertText("Managed Work Profile")
        assertText(message(R.string.ui_lock_task_work_profile_unavailable))
        assertNoText(message(R.string.ui_management_lock_task_available))
        assertNoText(message(R.string.ui_restricted))
    }

    @Test
    fun ungrantedDhizukuCardReportsUnknownOwnerAndPermissionReason() {
        render {
            ManagementStatusCard(
                profileOwner.copy(
                    mode = DeviceOwnerMode.Dhizuku,
                    owner = ManagementOwner.Unknown,
                ),
                false,
                ManagementCapabilities(),
            )
        }
        assertText("Dhizuku")
        assertText("Unknown")
        assertText(message(R.string.ui_lock_task_dhizuku_permission_required))
        assertNoText("Profile Owner")
        assertNoText(message(R.string.ui_restricted))
    }

    @Test
    fun everyUnavailableLockTaskFeatureExplainsItsReadOnlyState() {
        val reason = message(R.string.ui_lock_task_owner_access_required)
        render {
            Column {
                LockTaskFeatureHomeSetting(reason)
                LockTaskFeatureOverviewSetting(reason)
                LockTaskFeatureGlobalActionsSetting(reason)
                LockTaskFeatureNotificationsSetting(reason)
                LockTaskFeatureSystemInfoSetting(reason)
                LockTaskFeatureKeyguardSetting(reason)
                LockTaskFeatureBlockActivityStartInTaskSetting(reason)
            }
        }
        assertEquals(7, nodes().count { reason in text(it) })
        assertNoText(message(R.string.ui_restricted))
        openField(message(R.string.device_owner_lock_task_feature_home_title))
        assertEquals(8, nodes().count { reason in text(it) })
        assertReadOnlyDialog()
        assertNoText(message(R.string.ui_this_setting_is_managed_by_your_it_admin))
    }

    @Test
    fun actualAppRestrictionIsLabelledRestrictedAndPreventsEditing() {
        restrictHome()
        render { LockTaskFeatureHomeSetting(null) }
        assertText(message(R.string.ui_restricted))
        openField(message(R.string.device_owner_lock_task_feature_home_title))
        assertReadOnlyDialog()
        assertText(message(R.string.ui_this_setting_is_managed_by_your_it_admin))
    }

    @Test
    fun appRestrictionAndUnavailablePermissionCanBeExplainedTogether() {
        restrictHome()
        val reason = message(R.string.ui_lock_task_work_profile_unavailable)
        render { LockTaskFeatureHomeSetting(reason) }
        assertText(message(R.string.ui_restricted))
        assertText(reason)
        openField(message(R.string.device_owner_lock_task_feature_home_title))
        assertReadOnlyDialog()
        assertText(reason)
        assertText(message(R.string.ui_this_setting_is_managed_by_your_it_admin))
    }

    @Test
    fun permissionRevocationChangesAnOpenDialogToReadOnly() {
        val reason = mutableStateOf<String?>(null)
        render { LockTaskFeatureHomeSetting(reason.value) }
        openField(message(R.string.device_owner_lock_task_feature_home_title))
        assertText("Save")
        reason.value = message(R.string.ui_lock_task_dhizuku_permission_required)
        frames()
        assertReadOnlyDialog()
        assertText(reason.value!!)
        assertNoText(message(R.string.ui_restricted))
        reason.value = null
        frames()
        assertText("Save")
    }

    @Test
    fun availableBooleanFieldRetainsItsSaveBehaviour() {
        var saved: Boolean? = null
        render {
            BooleanSettingFieldItem(
                label = "Editable feature",
                infoText = "Feature information",
                initialValue = true,
                settingKey = "feature",
                restricted = false,
                onSave = { saved = it },
            )
        }
        openField("Editable feature")
        click("Save")
        assertEquals(true, saved)
        assertNoText("Save")
    }

    private fun restrictHome() {
        shadowOf(controller.get().getSystemService(RestrictionsManager::class.java))
            .setApplicationRestrictions(
                Bundle().apply {
                    putBoolean(
                        UserSettingsKeys.Device.Owner.LockTaskFeature.HOME,
                        false,
                    )
                }
            )
    }

    private fun assertReadOnlyDialog() {
        assertNoText("Save")
        assertText("Close")
        assertTrue(
            nodes().any { it.config.getOrNull(SemanticsProperties.Disabled) != null }
        )
    }

    private fun message(id: Int) = controller.get().getString(id)

    private fun render(content: @Composable () -> Unit) {
        view.setContent { MaterialTheme { content() } }
        frames()
    }

    private fun openField(label: String) = click(label)

    private fun click(label: String) {
        val node =
            nodes(merged = true).first {
                label in text(it) &&
                    it.config.getOrNull(SemanticsActions.OnClick) != null
            }
        assertTrue(node.config[SemanticsActions.OnClick].action!!.invoke())
        frames()
    }

    private fun frames() {
        repeat(8) {
            Snapshot.sendApplyNotifications()
            shadowOf(Looper.getMainLooper()).idle()
            clock.sendFrame(++frame * 16_000_000L)
            shadowOf(Looper.getMainLooper()).idle()
            view.measure(
                View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
            )
            view.layout(0, 0, 480, 1000)
            roots
                .filter { it.view.isAttachedToWindow }
                .forEach { it.measureAndLayoutForTest() }
        }
    }

    private fun nodes(merged: Boolean = false) =
        roots
            .filter { it.view.isAttachedToWindow }
            .flatMap {
                it.semanticsOwner.getAllSemanticsNodes(mergingEnabled = merged)
            }

    private fun text(node: SemanticsNode) =
        node.config.getOrNull(SemanticsProperties.Text)?.map { it.text }.orEmpty()

    private fun assertText(value: String) =
        assertTrue(value, nodes().any { value in text(it) })

    private fun assertNoText(value: String) =
        assertFalse(value, nodes().any { value in text(it) })
}
