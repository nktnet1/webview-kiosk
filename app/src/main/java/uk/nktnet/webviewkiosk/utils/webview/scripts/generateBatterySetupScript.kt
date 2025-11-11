package uk.nktnet.webviewkiosk.utils.webview.scripts

import uk.nktnet.webviewkiosk.utils.webview.wrapJsInIIFE

fun generateBatterySetupScript(): String {
    val innerScript = """
        if (typeof AndroidBattery !== 'undefined') {
            window.getBatteryStatus = function() {
                try {
                    return JSON.parse(AndroidBattery.getBatteryStatus());
                } catch (e) {
                    console.error('Failed to get battery status:', e);
                    return null;
                }
            };

            console.log('Battery API available: window.getBatteryStatus()');
        }
    """.trimIndent()

    return wrapJsInIIFE(innerScript)
}
