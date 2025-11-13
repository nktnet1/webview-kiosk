package uk.nktnet.webviewkiosk.utils

import android.content.SharedPreferences
import android.os.Bundle
import androidx.core.content.edit
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

fun stringPref(
    restrictions: Bundle? = null,
    prefs: SharedPreferences,
    key: String,
    default: String
) = object : ReadWriteProperty<Any?, String> {
    override fun getValue(thisRef: Any?, property: KProperty<*>) =
        if (restrictions?.containsKey(key) == true) {
            try {
                restrictions.getString(key)?.takeIf { it.isNotBlank() } ?: default
            } catch (_: Exception) {
                prefs.edit { putString(key, default) }
                default
            }
        } else {
            prefs.getString(key, null)?.takeIf { it.isNotBlank() } ?: default
        }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {
        if (restrictions?.containsKey(key) != true) {
            prefs.edit { putString(key, value) }
        }
    }
}

fun stringPrefOptional(
    restrictions: Bundle? = null,
    prefs: SharedPreferences,
    key: String
) = object : ReadWriteProperty<Any?, String> {
    override fun getValue(thisRef: Any?, property: KProperty<*>) =
        if (restrictions?.containsKey(key) == true) {
            restrictions.getString(key) ?: ""
        } else {
            try {
                prefs.getString(key, null) ?: ""
            } catch (_: Exception) {
                prefs.edit { putString(key, "") }
                ""
            }
        }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {
        if (restrictions?.containsKey(key) != true) prefs.edit { putString(key, value) }
    }
}

fun booleanPref(
    restrictions: Bundle? = null,
    prefs: SharedPreferences,
    key: String,
    default: Boolean
) = object : ReadWriteProperty<Any?, Boolean> {
    override fun getValue(thisRef: Any?, property: KProperty<*>) =
        if (restrictions?.containsKey(key) == true) {
            restrictions.getBoolean(key)
        } else {
            try {
                prefs.getBoolean(key, default)
            } catch (_: Exception) {
                prefs.edit { putBoolean(key, default) }
                default
            }
        }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
        if (restrictions?.containsKey(key) != true) prefs.edit { putBoolean(key, value) }
    }
}

fun intPref(
    restrictions: Bundle? = null,
    prefs: SharedPreferences,
    key: String,
    default: Int,
    min: Int = 0,
    max: Int = Int.MAX_VALUE
) = object : ReadWriteProperty<Any?, Int> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): Int {
        val raw = if (restrictions?.containsKey(key) == true) {
            restrictions.getInt(key)
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
        if (restrictions?.containsKey(key) != true) {
            val coerced = value.coerceIn(min, max)
            prefs.edit { putInt(key, coerced) }
        }
    }
}

fun floatPref(
    restrictions: Bundle? = null,
    prefs: SharedPreferences,
    key: String,
    default: Float
) = object : ReadWriteProperty<Any?, Float> {
    override fun getValue(thisRef: Any?, property: KProperty<*>) =
        if (restrictions?.containsKey(key) == true) {
            restrictions.getFloat(key)
        } else {
            try {
                prefs.getFloat(key, default)
            } catch (_: Exception) {
                prefs.edit { putFloat(key, default) }
                default
            }
        }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: Float) {
        if (restrictions?.containsKey(key) != true) {
            prefs.edit { putFloat(key, value) }
        }
    }
}

fun <T : Enum<T>> stringEnumPref(
    restrictions: Bundle? = null,
    prefs: SharedPreferences,
    key: String,
    default: String,
    fromString: ((String?) -> T)
) = object : ReadWriteProperty<Any?, T> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        val value = if (restrictions?.containsKey(key) == true) {
            restrictions.getString(key)
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
        if (restrictions?.containsKey(key) != true) {
            prefs.edit { putString(key, value.name) }
        }
    }
}
