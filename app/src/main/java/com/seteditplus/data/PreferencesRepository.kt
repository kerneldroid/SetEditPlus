package com.seteditplus.data

import android.content.Context
import androidx.core.content.edit

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    OLED
}

data class ModifiedSetting(
    val table: String,
    val key: String,
    val value: String,
    val stockValue: String? = null,
    val isDeleted: Boolean = false
) {
    fun toPrefString(): String {
        return try {
            val json = org.json.JSONObject()
            json.put("table", table)
            json.put("key", key)
            json.put("value", value)
            json.put("stockValue", stockValue)
            json.put("isDeleted", isDeleted)
            json.toString()
        } catch (e: Exception) {
            // Unlikely to happen, but provide a safe fallback using Base64
            val encKey = android.util.Base64.encodeToString(key.toByteArray(), android.util.Base64.NO_WRAP)
            val encValue = android.util.Base64.encodeToString(value.toByteArray(), android.util.Base64.NO_WRAP)
            val encStock = stockValue?.let { android.util.Base64.encodeToString(it.toByteArray(), android.util.Base64.NO_WRAP) } ?: "null"
            "b64:$table:$encKey:$encValue:$encStock:$isDeleted"
        }
    }

    companion object {
        fun fromPrefString(str: String): ModifiedSetting? {
            if (str.startsWith("{")) {
                try {
                    val json = org.json.JSONObject(str)
                    return ModifiedSetting(
                        table = json.getString("table"),
                        key = json.getString("key"),
                        value = json.getString("value"),
                        stockValue = if (json.has("stockValue") && !json.isNull("stockValue")) json.getString("stockValue") else null,
                        isDeleted = json.optBoolean("isDeleted", false)
                    )
                } catch (e: Exception) {
                    return null
                }
            } else if (str.startsWith("b64:")) {
                try {
                    val parts = str.split(":")
                    if (parts.size >= 6) {
                        val key = String(android.util.Base64.decode(parts[2], android.util.Base64.NO_WRAP))
                        val value = String(android.util.Base64.decode(parts[3], android.util.Base64.NO_WRAP))
                        val stock = if (parts[4] == "null") null else String(android.util.Base64.decode(parts[4], android.util.Base64.NO_WRAP))
                        val deleted = parts[5] == "true"
                        return ModifiedSetting(parts[1], key, value, stock, deleted)
                    }
                    return null
                } catch (e: Exception) {
                    return null
                }
            } else {
                // Legacy insecure format fallback
                val parts = str.split(":", limit = 5)
                if (parts.size == 3) {
                    return ModifiedSetting(parts[0], parts[1], parts[2], null, false)
                }
                if (parts.size == 5) {
                    val stock = if (parts[3] == "null") null else parts[3]
                    val deleted = parts[4] == "true"
                    return ModifiedSetting(parts[0], parts[1], parts[2], stock, deleted)
                }
                return null
            }
        }
    }
}

class PreferencesRepository(context: Context) {
    private val prefs = context.getSharedPreferences("setedit_prefs", Context.MODE_PRIVATE)

    fun getThemeMode(): ThemeMode {
        val name = prefs.getString("theme_mode", ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        return try { ThemeMode.valueOf(name) } catch (e: Exception) { ThemeMode.SYSTEM }
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit { putString("theme_mode", mode.name) }
    }

    fun getPermissionMode(): PermissionMode? {
        val name = prefs.getString("permission_mode", null) ?: return null
        return try { PermissionMode.valueOf(name) } catch (e: Exception) { null }
    }

    fun setPermissionMode(mode: PermissionMode?) {
        prefs.edit { putString("permission_mode", mode?.name) }
    }

    fun isAutostartEnabled(): Boolean {
        return prefs.getBoolean("autostart", false)
    }

    fun setAutostartEnabled(enabled: Boolean) {
        prefs.edit { putBoolean("autostart", enabled) }
    }

    fun getModifiedSettings(): List<ModifiedSetting> {
        val set = prefs.getStringSet("modified_settings", emptySet()) ?: emptySet()
        return set.mapNotNull { ModifiedSetting.fromPrefString(it) }
    }

    fun addModifiedSetting(setting: ModifiedSetting) {
        val current = getModifiedSettings().toMutableList()
        // Remove existing entry for same key/table
        current.removeAll { it.table == setting.table && it.key == setting.key }
        current.add(setting)
        prefs.edit { putStringSet("modified_settings", current.map { it.toPrefString() }.toSet()) }
    }

    fun removeModifiedSetting(table: String, key: String) {
        val current = getModifiedSettings().toMutableList()
        current.removeAll { it.table == table && it.key == key }
        prefs.edit { putStringSet("modified_settings", current.map { it.toPrefString() }.toSet()) }
    }

    fun clearAllModifiedSettings() {
        prefs.edit { remove("modified_settings") }
    }

    fun resetAllPreferences() {
        prefs.edit { clear() }
    }
}
