package uk.nktnet.webviewkiosk.utils.webview

fun wrapJsInIIFE(js: String): String {
    return """
        (function() {
            $js
        })();
    """.trimIndent()
}