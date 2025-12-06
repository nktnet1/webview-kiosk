package com.nktnet.webview_kiosk.ui.components.webview

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.webkit.WebView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class WebviewAwareSwipeRefreshLayout : SwipeRefreshLayout {
    private lateinit var webview: WebView
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

        if (webview.scrollY != 0 || initialY > height / 4) {
            return false
        }

        return super.onInterceptTouchEvent(ev)
    }
}
