package uk.nktnet.webviewkiosk.utils.webview

import android.content.Context
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BackgroundMediaFullscreenContainerTest {
    @Test
    fun activePlaybackKeepsFullscreenChildrenVisibleUntilStop() {
        val context = RuntimeEnvironment.getApplication()
        var retainForPlayback = true
        val container =
            BackgroundMediaFullscreenContainer(context) { retainForPlayback }
        val observed = mutableListOf<Int>()
        container.addView(
            object : View(context) {
                override fun onWindowVisibilityChanged(visibility: Int) {
                    observed.add(visibility)
                    super.onWindowVisibilityChanged(visibility)
                }
            }
        )

        container.dispatchWindowVisibilityChanged(View.VISIBLE)
        observed.clear()
        container.dispatchWindowVisibilityChanged(View.GONE)
        assertTrue(observed.isEmpty())

        retainForPlayback = false
        container.releaseMediaWindowVisibility()
        assertEquals(listOf(View.GONE), observed)
    }

    @Test
    fun disabledPlaybackForwardsFullscreenHideImmediately() {
        val context: Context = RuntimeEnvironment.getApplication()
        val container = BackgroundMediaFullscreenContainer(context) { false }
        val observed = mutableListOf<Int>()
        container.addView(
            object : View(context) {
                override fun onWindowVisibilityChanged(visibility: Int) {
                    observed.add(visibility)
                    super.onWindowVisibilityChanged(visibility)
                }
            }
        )

        container.dispatchWindowVisibilityChanged(View.GONE)
        assertEquals(listOf(View.GONE), observed)
    }
}
