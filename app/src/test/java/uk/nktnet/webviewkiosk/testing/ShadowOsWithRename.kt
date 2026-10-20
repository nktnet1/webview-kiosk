package uk.nktnet.webviewkiosk.testing

import android.system.Os
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

// Robolectric does not implement Os.rename, which publishes fully copied files.
@Implements(Os::class)
class ShadowOsWithRename {
    companion object {
        // Robolectric invokes this implementation reflectively.
        @Suppress("unused")
        @JvmStatic
        @Implementation
        fun rename(oldPath: String, newPath: String) {
            Files.move(
                File(oldPath).toPath(),
                File(newPath).toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }
}
