package uk.nktnet.webviewkiosk.ui.components.setting.fielditems.webengine

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.security.KeyChain
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ClientCertificateInstallerTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val owner = TestOwner()
    private val registry = RecordingRegistry()
    private val messages = mutableListOf<String>()
    // Models MainActivity.lifecycleScope: independent of any settings composition
    // scope.
    private val activityScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val uri = Uri.parse("content://test.invalid/client-identity.p12")
    private val bytes = byteArrayOf(1, 2, 3, 4)

    @After
    fun tearDown() {
        owner.send(Lifecycle.Event.ON_DESTROY)
        activityScope.cancel()
    }

    @Test
    fun filePickerResultAfterSettingsScopeIsCancelledStillOpensKeychain() {
        val deferred = CompletableDeferred<ByteArray>()
        val installer = newInstaller { deferred.await() }
        startActivity()
        val settingsScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

        installer.openPicker()
        registry.fileResult(uri)
        assertEquals(1, registry.launches.size)
        settingsScope
            .cancel() // Navigate away: the original rememberCoroutineScope would die
        // here.
        deferred.complete(bytes)

        assertEquals(2, registry.launches.size)
        assertArrayEquals(
            bytes,
            registry.installerIntent().getByteArrayExtra(KeyChain.EXTRA_PKCS12),
        )
        assertTrue(messages.isEmpty())
    }

    @Test
    fun pickerResultDuringActivityStopIsDeliveredAfterReturnToForeground() {
        val installer = newInstaller { bytes }
        startActivity()
        installer.openPicker()
        owner.send(Lifecycle.Event.ON_STOP)

        registry.fileResult(uri)
        assertEquals(
            "Activity results wait for the activity lifecycle",
            1,
            registry.launches.size,
        )

        owner.send(Lifecycle.Event.ON_START)
        assertEquals(2, registry.launches.size)
        assertArrayEquals(
            bytes,
            registry.installerIntent().getByteArrayExtra(KeyChain.EXTRA_PKCS12),
        )
    }

    @Test
    fun installerResultAfterLeavingSettingsIsStillReported() {
        val installer = newInstaller { bytes }
        startActivity()
        installer.openPicker()
        registry.fileResult(uri)
        owner.send(Lifecycle.Event.ON_STOP)
        registry.installerResult(Activity.RESULT_OK)
        assertTrue(messages.isEmpty())

        owner.send(Lifecycle.Event.ON_START)
        assertEquals(listOf("Client certificate installed."), messages)
        // A completed transaction is not left permanently locked against new
        // requests.
        installer.openPicker()
        assertEquals(3, registry.launches.size)
    }

    @Test
    fun cancelledPickerDoesNotReadOrReportFailure() {
        var readCount = 0
        val installer = newInstaller {
            readCount++
            bytes
        }
        startActivity()
        installer.openPicker()
        registry.fileResult(null)

        assertEquals(0, readCount)
        assertEquals(1, registry.launches.size)
        assertTrue(messages.isEmpty())
        installer.openPicker()
        assertEquals(2, registry.launches.size)
    }

    @Test
    fun cancellationOfReadDoesNotShowAnInstallFailureToast() {
        val installer = newInstaller {
            throw CancellationException("composition cancelled")
        }
        startActivity()
        installer.openPicker()
        registry.fileResult(uri)

        assertTrue("cancellation is not a certificate error", messages.isEmpty())
        assertEquals(1, registry.launches.size)
        installer.openPicker()
        assertEquals(2, registry.launches.size)
    }

    @Test
    fun genuineReadFailureIsReportedAndAllowsRetry() {
        var failures = 1
        val installer = newInstaller {
            if (failures-- > 0) {
                error("provider unavailable")
            }
            bytes
        }
        startActivity()
        installer.openPicker()
        registry.fileResult(uri)
        assertEquals(
            listOf("Unable to install client certificate: provider unavailable"),
            messages,
        )
        assertEquals(1, registry.launches.size)

        installer.openPicker()
        registry.fileResult(uri)
        assertEquals(3, registry.launches.size)
        assertArrayEquals(
            bytes,
            registry.installerIntent().getByteArrayExtra(KeyChain.EXTRA_PKCS12),
        )
    }

    @Test
    fun installerLaunchFailureIsReportedAndAllowsRetry() {
        registry.failInstallLaunch = true
        val installer = newInstaller { bytes }
        startActivity()
        installer.openPicker()
        registry.fileResult(uri)
        assertEquals(
            listOf("Unable to install client certificate: no KeyChain handler"),
            messages,
        )

        registry.failInstallLaunch = false
        installer.openPicker()
        registry.fileResult(uri)
        assertArrayEquals(
            bytes,
            registry.installerIntent().getByteArrayExtra(KeyChain.EXTRA_PKCS12),
        )
    }

    @Test
    fun filePickerLaunchFailureIsReportedAndAllowsRetry() {
        registry.failPickerLaunch = true
        val installer = newInstaller { bytes }
        startActivity()
        installer.openPicker()
        assertEquals(listOf("Unable to open certificate file picker."), messages)
        assertTrue(registry.launches.isEmpty())

        registry.failPickerLaunch = false
        installer.openPicker()
        assertEquals(1, registry.launches.size)
    }

    @Test
    fun duplicateButtonPressesCannotStartOverlappingCertificateTransactions() {
        val pendingRead = CompletableDeferred<ByteArray>()
        val installer = newInstaller { pendingRead.await() }
        startActivity()
        installer.openPicker()
        installer.openPicker()
        assertEquals(1, registry.launches.size)

        registry.fileResult(uri)
        installer.openPicker() // Reading the selected file is still in progress.
        assertEquals(1, registry.launches.size)
        pendingRead.complete(bytes)
        installer.openPicker() // KeyChain is still showing its installer.
        assertEquals(2, registry.launches.size)
        registry.installerResult(Activity.RESULT_CANCELED)
        installer.openPicker()
        assertEquals(3, registry.launches.size)
        assertTrue(messages.isEmpty())
    }

    private fun newInstaller(reader: suspend (Uri) -> ByteArray) =
        ClientCertificateInstaller(
            context = context,
            lifecycleOwner = owner,
            activityResultRegistry = registry,
            activityScope = activityScope,
            readFile = reader,
            installIntent = { content ->
                Intent("test.install").putExtra(KeyChain.EXTRA_PKCS12, content)
            },
            notify = messages::add,
        )

    private fun startActivity() {
        owner.send(Lifecycle.Event.ON_CREATE)
        owner.send(Lifecycle.Event.ON_START)
    }

    private class TestOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle = registry

        fun send(event: Lifecycle.Event) = registry.handleLifecycleEvent(event)
    }

    private class RecordingRegistry : ActivityResultRegistry() {
        data class Launch(
            val code: Int,
            val contract: ActivityResultContract<*, *>,
            val input: Any?,
        )

        val launches = mutableListOf<Launch>()
        var failPickerLaunch = false
        var failInstallLaunch = false

        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?,
        ) {
            if (
                failPickerLaunch && contract is ActivityResultContracts.OpenDocument
            ) {
                error("no file picker")
            }
            if (
                failInstallLaunch &&
                    contract is ActivityResultContracts.StartActivityForResult
            ) {
                error("no KeyChain handler")
            }
            launches += Launch(requestCode, contract, input)
        }

        fun fileResult(uri: Uri?) {
            val launch = launches.last {
                it.contract is ActivityResultContracts.OpenDocument
            }
            val resultCode =
                if (uri == null) {
                    Activity.RESULT_CANCELED
                } else {
                    Activity.RESULT_OK
                }
            dispatchResult(launch.code, resultCode, Intent().setData(uri))
        }

        fun installerResult(resultCode: Int) {
            val launch = launches.last {
                it.contract is ActivityResultContracts.StartActivityForResult
            }
            dispatchResult(launch.code, resultCode, Intent())
        }

        fun installerIntent(): Intent =
            launches
                .last {
                    it.contract is ActivityResultContracts.StartActivityForResult
                }
                .input as Intent
    }
}
