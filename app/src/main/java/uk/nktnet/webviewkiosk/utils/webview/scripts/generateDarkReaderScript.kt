package uk.nktnet.webviewkiosk.utils.webview.scripts

import uk.nktnet.webviewkiosk.utils.webview.wrapJsInIIFE

fun generateDarkReaderScript(appName: String): String {
    val rawScript = """
        if (window.DarkReader || document.getElementById('__darkreader_loader')) {
            return;
        }

        const originalDefine = window.define;

        if (originalDefine) {
            window.define = undefined;
        }

        const script = document.createElement('script');
        script.src = 'https://cdn.jsdelivr.net/npm/darkreader/darkreader.min.js';
        script.id = '__darkreader_loader';

        script.onload = function() {
            try {
                DarkReader.enable();
                console.log("Dark Reader loaded by $appName");
            } finally {
                if (originalDefine) {
                    window.define = originalDefine;
                }
            }
        };

        script.onerror = function() {
            if (originalDefine) {
                window.define = originalDefine;
            }
        };

        const target = document.body ?? document.head ?? document.documentElement;

        if (target) {
            target.appendChild(script);
        }
    """.trimIndent()

    return wrapJsInIIFE(rawScript)
}
