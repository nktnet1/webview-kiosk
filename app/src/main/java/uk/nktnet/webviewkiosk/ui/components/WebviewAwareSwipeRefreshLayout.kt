package uk.nktnet.webviewkiosk.ui.components

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.webkit.WebView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class WebviewAwareSwipeRefreshLayout : SwipeRefreshLayout {

    private var webview: WebView? = null
    private var initialY = 0f
    private var canRefresh = false

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, webview: WebView) : super(context) {
        this.webview = webview
        webview.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            isEnabled = scrollY == 0
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                initialY = ev.y
                val wv = webview
                canRefresh = wv != null && wv.scrollY == 0 && initialY <= height / 2
            }
        }

        if (!canRefresh) return false

        return super.onInterceptTouchEvent(ev)
    }
}
