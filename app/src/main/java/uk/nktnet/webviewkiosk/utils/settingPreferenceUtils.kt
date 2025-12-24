package com.nktnet.webview_kiosk.utils

import android.content.SharedPreferences
import android.os.Bundle
import androidx.core.content.edit
import org.json.JSONArray
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

fun stringPref(
    getRestrictions: () -> Bundle? = { null },
    prefs: SharedPreferences,
    key: String,
    default: String
) = object : ReadWriteProperty<Any?, String> {
    override fun getValue(thisRef: Any?, property: KProperty<*>) =
        if (getRestrictions()?.containsKey(key) == true) {
            try {
                getRestrictions()?.getString(key)?.takeIf { it.isNotBlank() } ?: default
            } catch (_: Exception) {
                prefs.edit { putString(key, default) }
                default
            }
        } else {
            prefs.getString(key, null)?.takeIf { it.isNotBlank() } ?: default
        }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {
        if (getRestrictions()?.containsKey(key) != true) {
            prefs.edit { putString(key, value) }
        }
    }
}

fun stringPrefOptional(
    getRestrictions: () -> Bundle? = { null },
    prefs: SharedPreferences,
    key: String
) = object : ReadWriteProperty<Any?, String> {
    override fun getValue(thisRef: Any?, property: KProperty<*>) =
        if (getRestrictions()?.containsKey(key) == true) {
            getRestrictions()?.getString(key) ?: ""
        } else {
            try {
                prefs.getString(key, null) ?: ""
            } catch (_: Exception) {
                prefs.edit { putString(key, "") }
                ""
            }
        }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {
        if (getRestrictions()?.containsKey(key) != true) prefs.edit { putString(key, value) }
    }
}

fun booleanPref(
    getRestrictions: () -> Bundle? = { null },
    prefs: SharedPreferences,
    key: String,
    default: Boolean
) = object : ReadWriteProperty<Any?, Boolean> {
    override fun getValue(thisRef: Any?, property: KProperty<*>) =
        if (getRestrictions()?.containsKey(key) == true) {
            getRestrictions()?.getBoolean(key) ?: default
        } else {
            try {
                prefs.getBoolean(key, default)
            } catch (_: Exception) {
                prefs.edit { putBoolean(key, default) }
                default
            }
        }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
        if (getRestrictions()?.containsKey(key) != true) prefs.edit { putBoolean(key, value) }
    }
}

fun intPref(
    getRestrictions: () -> Bundle? = { null },
    prefs: SharedPreferences,
    key: String,
    default: Int,
    min: Int = 0,
    max: Int = Int.MAX_VALUE
) = object : ReadWriteProperty<Any?, Int> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): Int {
        val raw = if (getRestrictions()?.containsKey(key) == true) {
            getRestrictions()?.getInt(key) ?: default
        } else {
            try {
                prefs.getInt(key, default)
            } catch (_: Exception) {
                prefs.edit { putInt(key, default) }
                default
            }
        }
        return raw.coerceIn(min, max)
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
        if (getRestrictions()?.containsKey(key) != true) {
            val coerced = value.coerceIn(min, max)
            prefs.edit { putInt(key, coerced) }
        }
    }
}

fun floatPref(
    getRestrictions: () -> Bundle? = { null },
    prefs: SharedPreferences,
    key: String,
    default: Float
) = object : ReadWriteProperty<Any?, Float> {
    override fun getValue(thisRef: Any?, property: KProperty<*>) =
        if (getRestrictions()?.containsKey(key) == true) {
            getRestrictions()?.getFloat(key) ?: default
        } else {
            try {
                prefs.getFloat(key, default)
            } catch (_: Exception) {
                prefs.edit { putFloat(key, default) }
                default
            }
        }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: Float) {
        if (getRestrictions()?.containsKey(key) != true) {
            prefs.edit { putFloat(key, value) }
        }
    }
}

fun <T : Enum<T>> stringEnumPref(
    getRestrictions: () -> Bundle? = { null },
    prefs: SharedPreferences,
    key: String,
    default: String,
    fromString: ((String?) -> T)
) = object : ReadWriteProperty<Any?, T> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        val value = if (getRestrictions()?.containsKey(key) == true) {
            getRestrictions()?.getString(key)
        } else {
            try {
                prefs.getString(key, default)
            } catch (_: Exception) {
                prefs.edit { putString(key, default) }
                default
            }
        }
        return fromString(value)
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        if (getRestrictions()?.containsKey(key) != true) {
            prefs.edit { putString(key, value.name) }
        }
    }
}

fun <T : Enum<T>> enumListPref(
    getRestrictions: () -> Bundle? = { null },
    prefs: SharedPreferences,
    key: String,
    default: List<T>,
    itemFromString: (String?) -> T?
) = object : ReadWriteProperty<Any?, List<T>> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): List<T> {
        return try {
            val jsonArray = if (getRestrictions()?.containsKey(key) == true) {
                JSONArray(getRestrictions()?.getStringArray(key))
            } else {
                val stringValue = prefs.getString(key, null) ?: return default
                JSONArray(stringValue)
            }
            LinkedHashSet(
                List(jsonArray.length()) { idx -> jsonArray.getString(idx) }
                    .mapNotNull { itemFromString(it) }
            ).toList()
        } catch (e: Exception) {
            e.printStackTrace()
            val uniqueDefault = LinkedHashSet(default).toList()
            prefs.edit {
                putString(key, JSONArray(
                    uniqueDefault.map { it.name }).toString()
                )
            }
            uniqueDefault
        }
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: List<T>) {
        if (getRestrictions()?.containsKey(key) != true) {
            val uniqueValue = LinkedHashSet(value).toList()
            val savedValue = JSONArray(uniqueValue.map { it.name }).toString()
            prefs.edit {
                putString(key, savedValue)
            }
        }
    }
}
