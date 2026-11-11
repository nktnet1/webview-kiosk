@file:Suppress("UsePropertyAccessSyntax")

package uk.nktnet.webviewkiosk.ui.screens

import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.os.Looper
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.navigation.NavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
import uk.nktnet.webviewkiosk.WebviewKioskAdminReceiver
import uk.nktnet.webviewkiosk.managers.DeviceOwnerManager
import uk.nktnet.webviewkiosk.utils.BaseJson
import uk.nktnet.webviewkiosk.utils.getSystemInfo

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], qualifiers = "w480dp-h1000dp")
@LooperMode(LooperMode.Mode.PAUSED)
class SettingsAboutScreenTest {
    private lateinit var controller: ActivityController<AppCompatActivity>
    private lateinit var view: ComposeView
    private lateinit var recomposer: Recomposer
    private val roots = mutableListOf<ViewRootForTest>()
    private val clock = BroadcastFrameClock()
    private val scope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + clock)

    @Before
    fun setUp() {
        ViewRootForTest.onViewCreatedCallback = { roots += it }
        controller =
            Robolectric.buildActivity(AppCompatActivity::class.java)
                .setup()
                .visible()
        val activity = controller.get()
        shadowOf(activity.getSystemService(DevicePolicyManager::class.java))
            .setProfileOwner(
                ComponentName(activity, WebviewKioskAdminReceiver::class.java)
            )
        shadowOf(activity.getSystemService(ActivityManager::class.java))
            .setLockTaskModeState(ActivityManager.LOCK_TASK_MODE_PINNED)
        recomposer = Recomposer(scope.coroutineContext)
        scope.launch { recomposer.runRecomposeAndApplyChanges() }
        view = ComposeView(activity)
        view.setParentCompositionContext(recomposer)
        activity.setContentView(view)
        view.setContent {
            MaterialTheme { SettingsAboutScreen(NavController(activity)) }
        }
        repeat(8) { frame ->
            Snapshot.sendApplyNotifications()
            shadowOf(Looper.getMainLooper()).idle()
            clock.sendFrame((frame + 1) * 16_000_000L)
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

    @After
    fun tearDown() {
        view.disposeComposition()
        recomposer.cancel()
        scope.cancel()
        controller.pause().stop().destroy()
        ViewRootForTest.onViewCreatedCallback = null
        DeviceOwnerManager.status.value = DeviceOwnerManager.Status()
    }

    @Test
    fun ownerAndKioskFieldsHaveTheirOwnAccessibleSection() {
        val headings =
            nodes()
                .filter { it.config.getOrNull(SemanticsProperties.Heading) != null }
                .sortedBy { it.positionInRoot.y }
        assertEquals(
            listOf(
                message(R.string.about_section_app),
                message(R.string.about_section_management),
                message(R.string.about_section_device),
            ),
            headings.map { text(it).single() },
        )
        val appTop = headings[0].positionInRoot.y
        val managementTop = headings[1].positionInRoot.y
        val deviceTop = headings[2].positionInRoot.y

        for (label in
            listOf(
                message(R.string.about_app_instance_id),
                message(R.string.about_source_code_url),
            )) {
            val y = node(label).positionInRoot.y
            assertTrue(label, y > appTop && y < managementTop)
        }
        for (label in
            listOf(
                message(R.string.about_app_device_owner),
                message(R.string.about_app_profile_owner),
                message(R.string.about_app_management_mode),
                message(R.string.about_app_management_owner),
                message(R.string.about_app_management_user_type),
                message(R.string.about_device_managed_profile),
                message(R.string.about_app_lock_task_permitted),
                message(R.string.about_app_dhizuku_permission_granted),
                message(R.string.about_app_manage_lock_task_packages),
                message(R.string.about_app_manage_lock_task_features),
                message(R.string.about_app_lock_device),
            )) {
            val y = node(label).positionInRoot.y
            assertTrue(label, y > managementTop && y < deviceTop)
        }
        assertTrue(
            node(message(R.string.about_device_android_version)).positionInRoot.y >
                deviceTop
        )
    }

    @Test
    fun managementValuesAreReadableWithoutChangingTheRemoteApi() {
        assertFalse(
            nodes().any { "ProfileOwner" in text(it) || "FullUser" in text(it) }
        )
        assertTrue(
            nodes().any { message(R.string.ui_management_full_user) in text(it) }
        )
        assertEquals(
            3,
            nodes().count { message(R.string.about_app_profile_owner) in text(it) },
        )
        val app =
            BaseJson.encodeToJsonElement(getSystemInfo(controller.get()))
                .jsonObject["app"]!!
                .jsonObject
        assertEquals("ProfileOwner", app["managementMode"]!!.jsonPrimitive.content)
        assertEquals("ProfileOwner", app["managementOwner"]!!.jsonPrimitive.content)
        assertEquals("FullUser", app["managementUserType"]!!.jsonPrimitive.content)
    }

    @Test
    fun lockStateIsAbsentFromAboutWithoutRemovingRemoteTelemetry() {
        assertFalse(nodes().any { "Lock State" in text(it) })
        val payload = BaseJson.encodeToJsonElement(getSystemInfo(controller.get()))
        assertEquals(
            "SCREEN_PINNING",
            payload.jsonObject["app"]!!
                .jsonObject["lockState"]!!
                .jsonPrimitive
                .content,
        )
    }

    private fun message(id: Int) = controller.get().getString(id)

    private fun nodes() =
        roots
            .filter { it.view.isAttachedToWindow }
            .flatMap {
                it.semanticsOwner.getAllSemanticsNodes(mergingEnabled = false)
            }

    private fun text(node: SemanticsNode) =
        node.config.getOrNull(SemanticsProperties.Text)?.map { it.text }.orEmpty()

    private fun node(label: String) = nodes().first { label in text(it) }
}
