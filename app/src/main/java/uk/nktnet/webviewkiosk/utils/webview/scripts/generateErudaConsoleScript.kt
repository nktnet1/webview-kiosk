package uk.nktnet.webviewkiosk.utils.webview.scripts

import uk.nktnet.webviewkiosk.utils.webview.wrapJsInIIFE

fun generateErudaConsoleScript(): String {
    val rawScript = """
        if (window.eruda || window.__erudaLoading) {
            return;
        }

        window.__erudaLoading = true;

        const originalDefine = window.define;

        if (originalDefine) {
            window.define = null;
        }

        const script = document.createElement('script');
        script.src = 'https://cdn.jsdelivr.net/npm/eruda';

        script.onload = function() {
            eruda.init();

            if (originalDefine) {
                window.define = originalDefine;
            }

            delete window.__erudaLoading;
        };

        script.onerror = function() {
            if (originalDefine) {
                window.define = originalDefine;
            }

            delete window.__erudaLoading;
        };

        document.body.appendChild(script);
    """.trimIndent()

    return wrapJsInIIFE(rawScript)
}
