package com.nktnet.webview_kiosk.utils.webview.scripts

import com.nktnet.webview_kiosk.utils.webview.wrapJsInIIFE

fun generateDesktopViewportScript(widthPx: Int = 1024): String {
    val rawScript = """
        function applyViewportFix() {
            var meta = document.querySelector('meta[name=viewport]');
            if (!meta) {
                meta = document.createElement('meta');
                meta.name = 'viewport';
                document.head.appendChild(meta);
            }
            meta.content = 'width=$widthPx';
        }

        applyViewportFix();

        const pushState = history.pushState;
        history.pushState = function() {
            pushState.apply(history, arguments);
            setTimeout(applyViewportFix, 0);
        };
        const replaceState = history.replaceState;
        history.replaceState = function() {
            replaceState.apply(history, arguments);
            setTimeout(applyViewportFix, 0);
        };
        window.addEventListener('hashchange', applyViewportFix);
    """.trimIndent()

    return wrapJsInIIFE(rawScript)
}
