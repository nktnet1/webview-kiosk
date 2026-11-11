package uk.nktnet.webviewkiosk.utils.webview

import android.content.Context
import android.util.Log
import android.widget.FrameLayout

/**
 * Fullscreen HTML video is hosted outside the WebView by WebChromeClient. Prevent
 * its child media view from receiving a hidden-window signal while the WebView's
 * active foreground media session remains alive.
 */
internal class BackgroundMediaFullscreenContainer(
    context: Context,
    private val shouldRetainWindowVisibility: () -> Boolean,
) : FrameLayout(context) {
    private val windowVisibility = BackgroundMediaWindowVisibility()

    override fun dispatchWindowVisibilityChanged(visibility: Int) {
        if (
            windowVisibility.dispatch(
                visibility,
                shouldRetainWindowVisibility(),
            ) { updated ->
                super.dispatchWindowVisibilityChanged(updated)
            }
        ) {
            Log.d(
                "BackgroundMediaPlayback",
                "Deferred hidden fullscreen media window",
            )
        }
    }

    fun releaseMediaWindowVisibility() {
        windowVisibility.release { updated ->
            super.dispatchWindowVisibilityChanged(updated)
        }
    }
}
