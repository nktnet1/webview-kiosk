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
        if (restrictions?.containsKey(key) == true) {
            restrictions.getString(key)?.takeIf { it.isNotBlank() } ?: default
        } else {
            prefs.getString(key, null)?.takeIf { it.isNotBlank() } ?: default
        }

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
        if (restrictions?.containsKey(key) == true) {
            restrictions.getString(key) ?: ""
        } else {
            prefs.getString(key, null) ?: ""
        }

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
        if (restrictions?.containsKey(key) == true) {
            restrictions.getBoolean(key)
        } else {
            prefs.getBoolean(key, default)
        }

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
        if (restrictions?.containsKey(key) == true) {
            restrictions.getInt(key)
        } else {
            prefs.getInt(key, default)
        }

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
        if (restrictions?.containsKey(key) == true) {
            restrictions.getFloat(key)
        } else {
            prefs.getFloat(key, default)
        }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: Float) {
        if (restrictions?.containsKey(key) != true) prefs.edit { putFloat(key, value) }
    }
}

fun <T : Enum<T>> stringEnumPref(
    prefs: SharedPreferences,
    key: String,
    restrictions: Bundle? = null,
    default: String,
    fromString: ((String?) -> T)
) = object : ReadWriteProperty<Any?, T> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        val value = if (restrictions?.containsKey(key) == true) {
            restrictions.getString(key)
        } else {
            prefs.getString(key, default)
        }
        return fromString(value)
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        if (restrictions?.containsKey(key) != true) {
            prefs.edit { putString(key, value.name) }
        }
    }
}

fun <T : Enum<T>> intEnumPref(
    prefs: SharedPreferences,
    key: String,
    restrictions: Bundle? = null,
    default: Int,
    fromInt: (Int?) -> T
) = object : ReadWriteProperty<Any?, T> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        val value: Int? = if (restrictions?.containsKey(key) == true) {
            restrictions.getInt(key)
        } else {
            prefs.getInt(key, default)
        }
        return fromInt(value)
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        if (restrictions?.containsKey(key) != true) {
            prefs.edit {
                putInt(key, default)
            }
        }
    }
}
