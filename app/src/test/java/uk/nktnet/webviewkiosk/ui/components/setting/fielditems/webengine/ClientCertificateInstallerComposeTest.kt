package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webengine

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Looper
import android.security.KeyChain
import android.widget.FrameLayout
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
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

/** Exercises an actual disposed Compose scope while the activity-owned KeyChain flow survives. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class ClientCertificateInstallerComposeTest {
    private lateinit var activityController: ActivityController<AppCompatActivity>
    private lateinit var activity: AppCompatActivity
    private lateinit var view: ComposeView
    private lateinit var recomposer: Recomposer
    private val clock = BroadcastFrameClock()
    private val composeHostScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + clock)
    private var frameNanos = 0L
    private var settingsScope: CoroutineScope? = null
    private val registry = RecordingRegistry()
    private val certificateBytes = byteArrayOf(42, 43, 44)
    private val uri = Uri.parse("content://test.invalid/user-certificate.p12")

    @Before
    fun setUp() {
        activityController = Robolectric.buildActivity(AppCompatActivity::class.java).create()
        activity = activityController.get()
        recomposer = Recomposer(composeHostScope.coroutineContext)
        composeHostScope.launch { recomposer.runRecomposeAndApplyChanges() }
        view = ComposeView(activity)
        view.setParentCompositionContext(recomposer)
        activity.setContentView(view)
        view.setContent {
            settingsScope = rememberCoroutineScope()
            Text("Certificate settings")
        }
    }

    @After
    fun tearDown() {
        view.disposeComposition()
        recomposer.cancel()
        composeHostScope.cancel()
        activityController.pause().stop().destroy()
    }

    @Test
    fun pickerResponseAfterSettingsDisposalStillStartsCertificateInstallation() {
        val installer = createInstaller { certificateBytes }
        startActivity()
        installer.openPicker()
        disposeSettings()

        registry.fileResult(uri)
        frames()
        assertEquals(2, registry.requests.size)
        assertArrayEquals(certificateBytes, registry.lastInstallIntent().getByteArrayExtra(KeyChain.EXTRA_PKCS12))
    }

    @Test
    fun readingCertificateContinuesWhenSettingsCompositionIsDisposed() {
        val readStarted = CompletableDeferred<Unit>()
        val finishRead = CompletableDeferred<ByteArray>()
        val installer = createInstaller {
            readStarted.complete(Unit)
            finishRead.await()
        }
        startActivity()
        installer.openPicker()
        registry.fileResult(uri)
        frames()
        assertTrue(readStarted.isCompleted)
        disposeSettings()
        finishRead.complete(certificateBytes)
        frames()
        assertEquals(2, registry.requests.size)
        assertArrayEquals(certificateBytes, registry.lastInstallIntent().getByteArrayExtra(KeyChain.EXTRA_PKCS12))
    }

    private fun startActivity() {
        activityController.start().resume().visible()
        frames()
        assertTrue(settingsScope != null)
    }

    private fun disposeSettings() {
        val departingScope = requireNotNull(settingsScope)
        // Removing the Compose view models navigating away; avoid auto-remounting it.
        activity.setContentView(FrameLayout(activity))
        view.disposeComposition()
        frames()
        assertTrue(
            "the departing rememberCoroutineScope must be cancelled",
            departingScope.coroutineContext[Job]?.isCancelled == true,
        )
    }

    private fun createInstaller(readFile: suspend (Uri) -> ByteArray): ClientCertificateInstaller =
        ClientCertificateInstaller(
            context = activity.applicationContext,
            lifecycleOwner = activity,
            activityResultRegistry = registry,
            activityScope = activity.lifecycleScope,
            readFile = readFile,
            installIntent = { content -> Intent("test.install").putExtra(KeyChain.EXTRA_PKCS12, content) },
            notify = { error("Unexpected certificate error: $it") },
        )

    private fun frames() {
        repeat(8) {
            Snapshot.sendApplyNotifications()
            shadowOf(Looper.getMainLooper()).idle()
            frameNanos += 16_000_000L
            clock.sendFrame(frameNanos)
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    private class RecordingRegistry : ActivityResultRegistry() {
        data class Launch(val code: Int, val contract: ActivityResultContract<*, *>, val input: Any?)
        val requests = mutableListOf<Launch>()

        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?,
        ) {
            requests += Launch(requestCode, contract, input)
        }

        fun fileResult(uri: Uri) {
            val request = requests.single { it.contract is ActivityResultContracts.OpenDocument }
            dispatchResult(request.code, Activity.RESULT_OK, Intent().setData(uri))
        }

        fun lastInstallIntent(): Intent = requests.last {
            it.contract is ActivityResultContracts.StartActivityForResult
        }.input as Intent
    }
}
