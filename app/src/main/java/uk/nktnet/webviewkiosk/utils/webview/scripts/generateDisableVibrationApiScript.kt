package uk.nktnet.webviewkiosk.utils.webview.scripts

import uk.nktnet.webviewkiosk.utils.webview.wrapJsInIIFE

fun generateDisableVibrationApiScript(): String {
    val rawScript = """
        const disabledVibrate = function() { return false; };
        const disableVibrateOn = function(target) {
            if (!target) return;
            try {
                Object.defineProperty(target, 'vibrate', {
                    configurable: false,
                    enumerable: false,
                    value: disabledVibrate,
                    writable: false
                });
            } catch (_) {
                try {
                    target.vibrate = disabledVibrate;
                } catch (_) {}
            }
        };

        disableVibrateOn(window.navigator);
        disableVibrateOn(Object.getPrototypeOf(window.navigator));
    """.trimIndent()

    return wrapJsInIIFE(rawScript)
}
