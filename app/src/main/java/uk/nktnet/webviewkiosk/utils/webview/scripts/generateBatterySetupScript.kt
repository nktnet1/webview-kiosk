package uk.nktnet.webviewkiosk.utils.webview.scripts

import uk.nktnet.webviewkiosk.utils.webview.wrapJsInIIFE

/**
 * Generates a JavaScript setup script that provides a convenient API for accessing
 * battery information from web content.
 *
 * This script creates a window.getBatteryStatus() function that wraps the native
 * AndroidBattery.getBatteryStatus() interface and provides error handling.
 *
 * @return A JavaScript string wrapped in an IIFE that sets up the battery API
 */
fun generateBatterySetupScript(): String {
    val innerScript = """
        // Check if the native interface is available
        if (typeof AndroidBattery !== 'undefined') {
            // Create a convenient wrapper function
            window.getBatteryStatus = function() {
                try {
                    return JSON.parse(AndroidBattery.getBatteryStatus());
                } catch (e) {
                    console.error('Failed to get battery status:', e);
                    return null;
                }
            };

            // Log availability for developers
            console.log('Battery API available: window.getBatteryStatus()');
        }
    """.trimIndent()

    return wrapJsInIIFE(innerScript)
}
