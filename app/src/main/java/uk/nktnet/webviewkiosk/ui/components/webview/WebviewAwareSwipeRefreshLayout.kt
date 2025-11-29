package uk.nktnet.webviewkiosk.ui.components.webview

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.webkit.WebView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class WebviewAwareSwipeRefreshLayout : SwipeRefreshLayout {
    private var webview: WebView? = null
    private var initialY = 0f

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, webview: WebView) : super(context) {
        this.webview = webview
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (!isEnabled || ev.pointerCount > 1) {
            return false
        }

        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            initialY = ev.y
        }

        val currView = webview
        if (currView == null || currView.scrollY != 0 || initialY > height / 4) {
            return false
        }

        return super.onInterceptTouchEvent(ev)
    }
}
