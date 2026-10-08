package uk.nktnet.webviewkiosk.utils.webview

import android.app.Activity
import android.app.Application
import android.content.ClipData
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.PathPermission
import android.content.pm.ProviderInfo
import android.net.Uri
import android.os.Environment
import android.os.PatternMatcher
import android.os.Process
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.TemporaryFolder
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
class FileChooserUtilsTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var application: Application
    private lateinit var context: Context

    @Before
    fun setUp() {
        application = RuntimeEnvironment.getApplication()
        context = contextWithReadGrant(null)
    }

    @Test
    fun cancellationAndEmptyResultsDoNotUploadAnything() {
        val capture = Uri.parse("content://${context.packageName}.provider/capture/photo.jpg")

        assertNull(parseFileChooserResult(context, Activity.RESULT_CANCELED, Intent(), capture))
        assertNull(parseFileChooserResult(context, Activity.RESULT_OK, null))
        assertNull(parseFileChooserResult(context, Activity.RESULT_OK, Intent()))
    }

    @Test
    fun acceptsAnExportedExternalProviderWithoutAReadPermission() {
        val uri = installProvider()

        assertArrayEquals(arrayOf(uri), select(uri))
    }

    @Test
    fun protectedProvidersRequireAnExplicitReadGrant() {
        val uri = installProvider(exported = false, readPermission = "example.permission.READ")

        assertNull(select(uri))
        assertArrayEquals(
            arrayOf(uri),
            parseFileChooserResult(contextWithReadGrant(uri), Activity.RESULT_OK, Intent().setData(uri)),
        )
    }

    @Test
    fun matchingPathPermissionsAlsoRequireAReadGrant() {
        val uri = installProvider(pathPermissions = arrayOf(
            PathPermission("/files", PatternMatcher.PATTERN_PREFIX, "example.permission.READ", null),
        ))

        assertNull(select(uri))
        assertArrayEquals(
            arrayOf(uri),
            parseFileChooserResult(contextWithReadGrant(uri), Activity.RESULT_OK, Intent().setData(uri)),
        )
    }

    @Test
    fun rejectsProvidersOwnedByTheAppOrSharingItsUidEvenWithGrants() {
        for ((packageName, uid) in listOf(
            context.packageName to Process.myUid(),
            "example.shared" to Process.myUid(),
        )) {
            val uri = installProvider(packageName = packageName, uid = uid)
            assertNull(parseFileChooserResult(contextWithReadGrant(uri), Activity.RESULT_OK, Intent().setData(uri)))
        }
    }

    @Test
    fun rejectsUnknownProvidersAndUnsupportedUriSchemes() {
        val values = listOf(
            "content://missing/files/report.pdf", "content:///files/report.pdf",
            "https://example.com/file", "data:text/plain,hello", "file://remote/share/report.pdf",
        )
        for (value in values) {
            assertNull(value, select(Uri.parse(value)))
        }
    }

    @Test
    fun preservesTheOrderOfMultipleSelectionsAndRejectsAnyInvalidItem() {
        val first = installProvider()
        val second = first.buildUpon().appendPath("second.pdf").build()
        val clip = ClipData("files", arrayOf("application/pdf"), ClipData.Item(first))
        clip.addItem(ClipData.Item(second))
        val intent = Intent().apply { clipData = clip }

        assertArrayEquals(arrayOf(first, second), parseFileChooserResult(context, Activity.RESULT_OK, intent))

        clip.addItem(ClipData.Item(Uri.parse("content://missing/private.txt")))
        assertNull(parseFileChooserResult(context, Activity.RESULT_OK, intent))
    }

    @Test
    fun rejectsClipItemsThatDoNotContainUris() {
        val intent = Intent().apply { clipData = ClipData.newPlainText("selection", "not a URI") }

        assertNull(parseFileChooserResult(context, Activity.RESULT_OK, intent))
    }

    @Test
    fun captureFallbackAcceptsOnlyTheExactOutputUriFromOurProvider() {
        val output = Uri.parse("content://${context.packageName}.provider/capture/photo.jpg")
        val substituted = output.buildUpon().appendPath("private.txt").build()

        assertArrayEquals(arrayOf(output), parseFileChooserResult(context, Activity.RESULT_OK, null, output))
        assertArrayEquals(
            arrayOf(output),
            parseFileChooserResult(context, Activity.RESULT_OK, Intent().setData(output), output),
        )
        assertNull(parseFileChooserResult(context, Activity.RESULT_OK, Intent().setData(substituted), output))
    }

    @Test
    fun aValidPickerUriTakesPrecedenceOverCaptureFallback() {
        val selected = installProvider()
        val output = Uri.parse("content://${context.packageName}.provider/capture/photo.jpg")

        assertArrayEquals(
            arrayOf(selected),
            parseFileChooserResult(context, Activity.RESULT_OK, Intent().setData(selected), output),
        )
    }

    @Test
    fun rejectsReadablePrivateFilesAndFilesOutsideSharedStorage() {
        val privateFile = File(context.cacheDir, "private.txt").apply { writeText("private") }
        try {
            assertNull(select(Uri.fromFile(privateFile)))
            assertNull(select(Uri.fromFile(temporaryFolder.newFile("outside.txt"))))
        } finally {
            privateFile.delete()
        }
    }

    @Test
    fun canonicalizesReadableLegacyFilesWithinSharedStorage() {
        withSharedDirectory { directory ->
            val file = File(directory, "report.txt").apply { writeText("report") }
            val link = File(directory, "alias.txt")
            Files.createSymbolicLink(link.toPath(), file.toPath())

            assertArrayEquals(arrayOf(Uri.fromFile(file.canonicalFile)), select(Uri.fromFile(link)))
            assertNull(select(Uri.fromFile(directory)))
            assertNull(select(Uri.fromFile(File(directory, "missing.txt"))))
        }
    }

    @Test
    fun rejectsLegacySymlinksThatEscapeSharedStorage() {
        withSharedDirectory { directory ->
            val target = temporaryFolder.newFile("outside.txt")
            val link = File(directory, "escape.txt")
            Files.createSymbolicLink(link.toPath(), target.toPath())

            assertNull(select(Uri.fromFile(link)))
        }
    }

    private fun select(uri: Uri) = parseFileChooserResult(context, Activity.RESULT_OK, Intent().setData(uri))

    private fun contextWithReadGrant(grantedUri: Uri?): Context = object : ContextWrapper(application) {
        override fun checkUriPermission(uri: Uri, pid: Int, uid: Int, modeFlags: Int): Int {
            assertEquals(Process.myPid(), pid)
            assertEquals(Process.myUid(), uid)
            assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, modeFlags)
            return if (uri == grantedUri) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED
        }
    }

    private fun installProvider(
        packageName: String = "example.picker",
        uid: Int = Process.myUid() + 1,
        exported: Boolean = true,
        readPermission: String? = null,
        pathPermissions: Array<PathPermission>? = null,
    ): Uri {
        val provider = ProviderInfo().apply {
            this.packageName = packageName
            name = "$packageName.TestProvider"
            authority = "$packageName.testprovider"
            this.exported = exported
            this.readPermission = readPermission
            this.pathPermissions = pathPermissions
            applicationInfo = ApplicationInfo().apply {
                this.packageName = packageName
                this.uid = uid
            }
        }
        shadowOf(context.packageManager).addOrUpdateProvider(provider)
        return Uri.parse("content://${provider.authority}/files/report.pdf")
    }

    private fun withSharedDirectory(block: (File) -> Unit) {
        val storage = Environment.getExternalStorageDirectory()
        assertTrue(storage.isDirectory || storage.mkdirs())
        val directory = Files.createTempDirectory(storage.toPath(), "wk-upload-").toFile()
        try {
            block(directory)
        } finally {
            assertTrue(directory.deleteRecursively())
        }
    }
}
