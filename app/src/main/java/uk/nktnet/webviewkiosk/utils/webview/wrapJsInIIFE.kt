package com.nktnet.webview_kiosk.utils.webview

fun wrapJsInIIFE(js: String): String {
    return """
        (function() {
            $js
        })();
    """.trimIndent()
}
