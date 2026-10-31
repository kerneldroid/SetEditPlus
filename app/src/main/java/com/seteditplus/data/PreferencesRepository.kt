package com.seteditplus.data

import android.content.Context
import android.util.Base64
import androidx.core.content.edit
import org.json.JSONObject

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
    val isDeleted: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
) {
    val settingsTable: SettingsTable
        get() = SettingsTable.fromNameOrDefault(table)

    val uniqueId: String
        get() = "${settingsTable.name}:$key"

    fun toPrefString(): String {
        return try {
            val json = JSONObject()
            json.put("table", settingsTable.name)
            json.put("key", key)
            json.put("value", value)
            json.put("hasStockValue", stockValue != null)
            if (stockValue != null) {
                json.put("stockValue", stockValue)
            }
            json.put("isDeleted", isDeleted)
            json.put("updatedAt", updatedAt)
            json.toString()
        } catch (_: Exception) {
            val encKey = Base64.encodeToString(key.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            val encValue = Base64.encodeToString(value.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            val encStock = stockValue?.let {
                Base64.encodeToString(it.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            } ?: "null"
            "b64:${settingsTable.name}:$encKey:$encValue:$encStock:$isDeleted:$updatedAt"
        }
    }

    companion object {
        fun uniqueKey(table: SettingsTable, key: String): String = "${table.name}:$key"

        fun fromPrefString(str: String): ModifiedSetting? {
            if (str.startsWith("{")) {
                return try {
                    val json = JSONObject(str)
                    val table = SettingsTable.fromNameOrDefault(json.optString("table")).name
                    val key = json.getString("key")
                    val value = json.optString("value", "")
                    val hasStock = if (json.has("hasStockValue")) {
                        json.optBoolean("hasStockValue", false)
                    } else {
                        json.has("stockValue") && !json.isNull("stockValue")
                    }
                    val stockValue = if (hasStock) json.optString("stockValue") else null
                    val isDeleted = json.optBoolean("isDeleted", false)
                    val updatedAt = json.optLong("updatedAt", 0L)
                    ModifiedSetting(
                        table = table,
                        key = key,
                        value = value,
                        stockValue = stockValue,
                        isDeleted = isDeleted,
                        updatedAt = updatedAt
                    )
                } catch (_: Exception) {
                    null
                }
            } else if (str.startsWith("b64:")) {
                return try {
                    val parts = str.split(":")
                    if (parts.size >= 6) {
                        val table = SettingsTable.fromNameOrDefault(parts[1]).name
                        val key = String(Base64.decode(parts[2], Base64.NO_WRAP), Charsets.UTF_8)
                        val value = String(Base64.decode(parts[3], Base64.NO_WRAP), Charsets.UTF_8)
                        val stock = if (parts[4] == "null") {
                            null
                        } else {
                            String(Base64.decode(parts[4], Base64.NO_WRAP), Charsets.UTF_8)
                        }
                        val deleted = parts[5].toBoolean()
                        val updatedAt = parts.getOrNull(6)?.toLongOrNull() ?: 0L
                        ModifiedSetting(table, key, value, stock, deleted, updatedAt)
                    } else {
                        null
                    }
                } catch (_: Exception) {
                    null
                }
            } else {
                val parts = str.split(":", limit = 5)
                if (parts.size == 3) {
                    val table = SettingsTable.fromNameOrDefault(parts[0]).name
                    return ModifiedSetting(table, parts[1], parts[2], null, false)
                }
                if (parts.size == 5) {
                    val table = SettingsTable.fromNameOrDefault(parts[0]).name
                    val stock = if (parts[3] == "null") null else parts[3]
                    val deleted = parts[4].toBoolean()
                    return ModifiedSetting(table, parts[1], parts[2], stock, deleted)
                }
                return null
            }
        }
    }
}

class PreferencesRepository(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getThemeMode(): ThemeMode {
        val name = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        return try {
            ThemeMode.valueOf(name)
        } catch (_: Exception) {
            ThemeMode.SYSTEM
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit { putString(KEY_THEME_MODE, mode.name) }
    }

    fun getPermissionMode(): PermissionMode? {
        val name = prefs.getString(KEY_PERMISSION_MODE, null) ?: return null
        return try {
            PermissionMode.valueOf(name)
        } catch (_: Exception) {
            null
        }
    }

    fun setPermissionMode(mode: PermissionMode?) {
        prefs.edit {
            if (mode == null) remove(KEY_PERMISSION_MODE)
            else putString(KEY_PERMISSION_MODE, mode.name)
        }
    }

    fun isAutostartEnabled(): Boolean {
        return prefs.getBoolean(KEY_AUTOSTART, false)
    }

    fun setAutostartEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_AUTOSTART, enabled) }
    }

    fun getModifiedSettings(): List<ModifiedSetting> {
        val set = prefs.getStringSet(KEY_MODIFIED_SETTINGS, emptySet()) ?: emptySet()
        return set.mapNotNull { ModifiedSetting.fromPrefString(it) }
            .sortedWith(compareBy<ModifiedSetting> { it.settingsTable.ordinal }.thenBy { it.key.lowercase() })
    }

    fun addModifiedSetting(setting: ModifiedSetting) {
        val normalizedTable = setting.settingsTable.name
        val normalized = setting.copy(table = normalizedTable)
        val current = getModifiedSettings().toMutableList()
        current.removeAll { it.settingsTable.name == normalizedTable && it.key == normalized.key }

        // If the user edited the setting back to its exact stock value and it is not deleted,
        // remove it from modified tracking instead of storing a redundant entry.
        val isRevertedToStock = !normalized.isDeleted &&
            normalized.stockValue != null &&
            normalized.value == normalized.stockValue

        if (!isRevertedToStock) {
            current.add(normalized)
        }
        prefs.edit {
            putStringSet(KEY_MODIFIED_SETTINGS, current.map { it.toPrefString() }.toSet())
        }
    }

    fun removeModifiedSetting(table: String, key: String) {
        val normalizedTable = SettingsTable.fromNameOrDefault(table).name
        val current = getModifiedSettings().toMutableList()
        current.removeAll { it.settingsTable.name == normalizedTable && it.key == key }
        prefs.edit {
            putStringSet(KEY_MODIFIED_SETTINGS, current.map { it.toPrefString() }.toSet())
        }
    }

    fun clearAllModifiedSettings() {
        prefs.edit { remove(KEY_MODIFIED_SETTINGS) }
    }

    fun resetAllPreferences() {
        prefs.edit { clear() }
    }

    companion object {
        private const val PREFS_NAME = "setedit_prefs"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_PERMISSION_MODE = "permission_mode"
        private const val KEY_AUTOSTART = "autostart"
        private const val KEY_MODIFIED_SETTINGS = "modified_settings"
    }
}
