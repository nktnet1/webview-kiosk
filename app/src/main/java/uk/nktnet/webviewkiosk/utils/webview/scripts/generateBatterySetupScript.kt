package uk.nktnet.webviewkiosk.utils.webview.scripts

import uk.nktnet.webviewkiosk.utils.webview.wrapJsInIIFE

fun generateBatterySetupScript(): String {
    val innerScript = """
        if (typeof AndroidBattery !== 'undefined') {
            if (typeof window.webviewkiosk === 'undefined') {
                window.webviewkiosk = {};
            }

            window.webviewkiosk.getBatteryStatus = function() {
                try {
                    return JSON.parse(AndroidBattery.getBatteryStatus());
                } catch (e) {
                    console.error('Failed to get battery status:', e);
                    return null;
                }
            };
        }
    """.trimIndent()

    return wrapJsInIIFE(innerScript)
}
