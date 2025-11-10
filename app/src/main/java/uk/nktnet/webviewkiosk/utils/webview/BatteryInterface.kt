package uk.nktnet.webviewkiosk.utils.webview

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.webkit.JavascriptInterface
import org.json.JSONObject

/**
 * JavaScript interface that exposes battery information to web content.
 *
 * When enabled, this interface allows web pages to access device battery status
 * through the window.AndroidBattery.getBatteryStatus() method.
 *
 * @param context The Android context used to access battery information.
 */
class BatteryInterface(private val context: Context) {

    /**
     * Returns the current battery status as a JSON string.
     *
     * The returned JSON object contains the following fields:
     * - level: Battery level as a decimal between 0.0 and 1.0
     * - percentage: Battery percentage between 0 and 100
     * - charging: Boolean indicating if the device is currently charging
     * - chargingType: String indicating the charging method ("none", "usb", "ac", "wireless")
     * - voltage: Battery voltage in volts
     * - temperature: Battery temperature in degrees Celsius
     * - health: Battery health status ("unknown", "good", "overheat", "dead", "overvoltage", "cold")
     *
     * @return JSON string containing battery information
     */
    @JavascriptInterface
    fun getBatteryStatus(): String {
        val batteryStatus = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )

        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level >= 0 && scale > 0) {
            level * 100 / scale.toFloat()
        } else {
            -1f
        }

        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING
            || status == BatteryManager.BATTERY_STATUS_FULL

        val chargePlug = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        val isUsbCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_USB
        val isAcCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_AC
        val isWirelessCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_WIRELESS

        val voltage = batteryStatus?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
        val temperature = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        val health = batteryStatus?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1

        val json = JSONObject()
        json.put("level", batteryPct / 100.0)  // 0.0 to 1.0
        json.put("percentage", batteryPct)      // 0 to 100
        json.put("charging", isCharging)
        json.put("chargingType", when {
            isUsbCharge -> "usb"
            isAcCharge -> "ac"
            isWirelessCharge -> "wireless"
            else -> "none"
        })
        json.put("voltage", voltage / 1000.0)  // Convert to volts
        json.put("temperature", temperature / 10.0)  // Convert to Celsius
        json.put("health", getHealthString(health))

        return json.toString()
    }

    /**
     * Converts the battery health integer constant to a human-readable string.
     *
     * @param health The battery health constant from BatteryManager
     * @return A string representation of the battery health
     */
    private fun getHealthString(health: Int): String {
        return when (health) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "overheat"
            BatteryManager.BATTERY_HEALTH_DEAD -> "dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "overvoltage"
            BatteryManager.BATTERY_HEALTH_COLD -> "cold"
            else -> "unknown"
        }
    }
}
