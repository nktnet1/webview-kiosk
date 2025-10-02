package uk.nktnet.webviewkiosk.utils

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

fun stringPref(prefs: SharedPreferences, key: String, default: String) = object : ReadWriteProperty<Any?, String> {
    override fun getValue(thisRef: Any?, property: KProperty<*>) =
        prefs.getString(key, null)?.takeIf { it.isNotBlank() } ?: default
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) =
        prefs.edit { putString(key, value) }
}

fun stringPrefOptional(prefs: SharedPreferences, key: String) = object : ReadWriteProperty<Any?, String> {
    override fun getValue(thisRef: Any?, property: KProperty<*>) = prefs.getString(key, null) ?: ""
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) =
        prefs.edit { putString(key, value) }
}

fun booleanPref(prefs: SharedPreferences, key: String, default: Boolean) = object : ReadWriteProperty<Any?, Boolean> {
    override fun getValue(thisRef: Any?, property: KProperty<*>) = prefs.getBoolean(key, default)
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) =
        prefs.edit { putBoolean(key, value) }
}

fun intPref(prefs: SharedPreferences, key: String, default: Int) = object : ReadWriteProperty<Any?, Int> {
    override fun getValue(thisRef: Any?, property: KProperty<*>) = prefs.getInt(key, default)
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) =
        prefs.edit { putInt(key, value) }
}

fun floatPref(prefs: SharedPreferences, key: String, default: Float) = object : ReadWriteProperty<Any?, Float> {
    override fun getValue(thisRef: Any?, property: KProperty<*>) = prefs.getFloat(key, default)
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: Float) =
        prefs.edit { putFloat(key, value) }
}
