package uk.nktnet.webviewkiosk.utils

import android.content.SharedPreferences
import android.os.Bundle
import androidx.core.content.edit
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

fun stringPref(
    prefs: SharedPreferences,
    key: String,
    default: String,
    restrictions: Bundle? = null
) = object : ReadWriteProperty<Any?, String> {
    override fun getValue(thisRef: Any?, property: KProperty<*>) =
        restrictions?.getString(key)?.takeIf { it.isNotBlank() }
            ?: prefs.getString(key, null)?.takeIf { it.isNotBlank() } ?: default

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {
        if (restrictions?.containsKey(key) != true) prefs.edit { putString(key, value) }
    }
}

fun stringPrefOptional(
    prefs: SharedPreferences,
    key: String,
    restrictions: Bundle? = null
) = object : ReadWriteProperty<Any?, String> {
    override fun getValue(thisRef: Any?, property: KProperty<*>) =
        restrictions?.getString(key) ?: prefs.getString(key, null) ?: ""

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {
        if (restrictions?.containsKey(key) != true) prefs.edit { putString(key, value) }
    }
}

fun booleanPref(
    prefs: SharedPreferences,
    key: String,
    default: Boolean,
    restrictions: Bundle? = null
) = object : ReadWriteProperty<Any?, Boolean> {
    override fun getValue(thisRef: Any?, property: KProperty<*>) =
        restrictions?.getBoolean(key) ?: prefs.getBoolean(key, default)

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
        if (restrictions?.containsKey(key) != true) prefs.edit { putBoolean(key, value) }
    }
}

fun intPref(
    prefs: SharedPreferences,
    key: String,
    default: Int,
    restrictions: Bundle? = null
) = object : ReadWriteProperty<Any?, Int> {
    override fun getValue(thisRef: Any?, property: KProperty<*>) =
        restrictions?.getInt(key) ?: prefs.getInt(key, default)

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
        if (restrictions?.containsKey(key) != true) prefs.edit { putInt(key, value) }
    }
}

fun floatPref(
    prefs: SharedPreferences,
    key: String,
    default: Float,
    restrictions: Bundle? = null
) = object : ReadWriteProperty<Any?, Float> {
    override fun getValue(thisRef: Any?, property: KProperty<*>) =
        restrictions?.getFloat(key) ?: prefs.getFloat(key, default)

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: Float) {
        if (restrictions?.containsKey(key) != true) prefs.edit { putFloat(key, value) }
    }
}
