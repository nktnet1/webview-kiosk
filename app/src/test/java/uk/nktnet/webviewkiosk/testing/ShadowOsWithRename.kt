package uk.nktnet.webviewkiosk.testing

import android.system.Os
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

// Robolectric does not implement Os.rename, which publishes fully copied files.
@Implements(Os::class)
class ShadowOsWithRename {
    companion object {
        @JvmStatic
        @Implementation
        protected fun rename(oldPath: String, newPath: String) {
            Files.move(
                File(oldPath).toPath(),
                File(newPath).toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }
}
