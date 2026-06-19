package uk.nktnet.webviewkiosk.utils.webview.scripts

import uk.nktnet.webviewkiosk.utils.webview.wrapJsInIIFE

fun generateErudaConsoleScript(appName: String): String {
    val rawScript = """
        if (window.eruda || document.getElementById('__eruda_loader')) {
            return;
        }

        const originalDefine = window.define;

        if (originalDefine) {
            window.define = undefined;
        }

        const script = document.createElement('script');
        script.src = 'https://cdn.jsdelivr.net/npm/eruda';
        script.id = '__eruda_loader';

        script.onload = function() {
            try {
                eruda.init();
                console.log("Eruda console loaded by $appName");
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
