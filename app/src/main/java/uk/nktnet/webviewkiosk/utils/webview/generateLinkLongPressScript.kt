package uk.nktnet.webviewkiosk.utils.webview

fun generateLinkLongPressScript(): String {
    val rawScript = """
        document.querySelectorAll('a').forEach(function(a) {
            a.addEventListener('contextmenu', function(e) {
                e.preventDefault();
                window.AndroidInterface.jsOnLinkLongPress(a.href);
            });
        });
    """.trimIndent()

    return wrapJsInIIFE(rawScript)
}
