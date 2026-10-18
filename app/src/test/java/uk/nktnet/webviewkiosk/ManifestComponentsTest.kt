package uk.nktnet.webviewkiosk

import android.app.Activity
import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.core.content.FileProvider
import java.io.File
import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ManifestComponentsTest {
    @Test
    fun registeredKioskActivitiesArePublicConcreteActivityClasses() {
        val context = RuntimeEnvironment.getApplication()
        listOf(MainActivity::class.java, Android6KioskActivity::class.java)
            .forEach { type ->
                val info =
                    context.packageManager.getActivityInfo(
                        ComponentName(context, type),
                        0,
                    )
                val registered = Class.forName(info.name)
                assertEquals(type, registered)
                assertTrue(Activity::class.java.isAssignableFrom(registered))
                assertTrue(Modifier.isPublic(registered.modifiers))
                assertFalse(Modifier.isAbstract(registered.modifiers))
                assertTrue(Modifier.isPublic(registered.getConstructor().modifiers))
            }
    }

    @Test
    fun appFileProviderKeepsTheExistingAuthorityAndSharingPaths() {
        val context = RuntimeEnvironment.getApplication()
        val authority = "${context.packageName}.provider"
        val info =
            context.packageManager.getProviderInfo(
                ComponentName(context, WebviewKioskFileProvider::class.java),
                PackageManager.GET_META_DATA,
            )
        assertEquals(authority, info.authority)
        assertFalse(info.exported)
        assertTrue(info.grantUriPermissions)
        val file =
            File(context.cacheDir, "capture/shared.txt").apply {
                parentFile!!.mkdirs()
                writeText("Shared contents")
            }
        val uri = FileProvider.getUriForFile(context, authority, file)
        assertEquals("content", uri.scheme)
        assertEquals(authority, uri.authority)
        assertEquals("/captures/shared.txt", uri.path)
        assertEquals(
            "Shared contents",
            context.contentResolver.openInputStream(uri)!!.bufferedReader().use {
                it.readText()
            },
        )
    }
}
