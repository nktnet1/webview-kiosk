package com.nktnet.webview_kiosk.utils.webview.interfaces

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.webkit.JavascriptInterface
import org.json.JSONObject

class BatteryInterface(private val context: Context) {
    val name = "WebviewKioskBatteryInterface"

    @Suppress("unused")
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
        json.put("level", batteryPct / 100.0)
        json.put("percentage", batteryPct)
        json.put("charging", isCharging)
        json.put("chargingType", when {
            isUsbCharge -> "usb"
            isAcCharge -> "ac"
            isWirelessCharge -> "wireless"
            else -> "none"
        })
        json.put("voltage", voltage / 1000.0)
        json.put("temperature", temperature / 10.0)
        json.put("health", getHealthString(health))

        return json.toString()
    }

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
