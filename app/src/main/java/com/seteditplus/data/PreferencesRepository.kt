package com.seteditplus.data

import android.content.Context
import android.util.Base64
import androidx.core.content.edit
import org.json.JSONArray
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

    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("table", settingsTable.name)
            put("key", key)
            put("value", value)
            put("hasStockValue", stockValue != null)
            if (stockValue != null) {
                put("stockValue", stockValue)
            }
            put("isDeleted", isDeleted)
            put("updatedAt", updatedAt)
        }
    }

    fun toPrefString(): String {
        return try {
            toJsonObject().toString()
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

        fun fromJsonObject(json: JSONObject): ModifiedSetting? {
            return try {
                val table = SettingsTable.fromNameOrDefault(json.optString("table")).name
                val key = json.getString("key").trim()
                if (!SettingsRepository.isValidKey(key)) return null
                val value = json.optString("value", "")
                val hasStock = if (json.has("hasStockValue")) {
                    json.optBoolean("hasStockValue", false)
                } else {
                    json.has("stockValue") && !json.isNull("stockValue")
                }
                val stockValue = if (hasStock) json.optString("stockValue") else null
                val isDeleted = json.optBoolean("isDeleted", false)
                val updatedAt = json.optLong("updatedAt", System.currentTimeMillis())
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
        }

        fun fromPrefString(str: String): ModifiedSetting? {
            if (str.startsWith("{")) {
                return try {
                    fromJsonObject(JSONObject(str))
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
    private val lock = Any()

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

    fun isSafeBootGuardEnabled(): Boolean {
        return prefs.getBoolean(KEY_SAFE_BOOT_GUARD, true)
    }

    fun setSafeBootGuardEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_SAFE_BOOT_GUARD, enabled) }
    }

    fun recordBootAttemptAndCheckSafe(): Boolean = synchronized(lock) {
        if (!isSafeBootGuardEnabled()) return true
        val now = System.currentTimeMillis()
        val lastBootTs = prefs.getLong(KEY_LAST_BOOT_TS, 0L)
        val rapidBootCount = if (lastBootTs > 0L && (now - lastBootTs) < RAPID_REBOOT_WINDOW_MS) {
            prefs.getInt(KEY_RAPID_BOOT_COUNT, 0) + 1
        } else {
            1
        }
        prefs.edit {
            putLong(KEY_LAST_BOOT_TS, now)
            putInt(KEY_RAPID_BOOT_COUNT, rapidBootCount)
        }
        if (rapidBootCount >= MAX_RAPID_REBOOTS_BEFORE_TRIP) {
            prefs.edit {
                putBoolean(KEY_AUTOSTART, false)
                putBoolean(KEY_BOOT_GUARD_TRIPPED, true)
                putInt(KEY_RAPID_BOOT_COUNT, 0)
            }
            return false
        }
        return true
    }

    fun consumeBootGuardTrippedFlag(): Boolean = synchronized(lock) {
        val tripped = prefs.getBoolean(KEY_BOOT_GUARD_TRIPPED, false)
        if (tripped) {
            prefs.edit {
                putBoolean(KEY_BOOT_GUARD_TRIPPED, false)
                putInt(KEY_RAPID_BOOT_COUNT, 0)
            }
        }
        tripped
    }

    fun getFavoriteIds(): Set<String> = synchronized(lock) {
        (prefs.getStringSet(KEY_FAVORITE_SETTINGS, emptySet()) ?: emptySet()).toSet()
    }

    fun toggleFavorite(table: SettingsTable, key: String): Boolean = synchronized(lock) {
        val id = ModifiedSetting.uniqueKey(table, key)
        val current = getFavoriteIds().toMutableSet()
        val added = if (current.contains(id)) {
            current.remove(id)
            false
        } else {
            current.add(id)
            true
        }
        prefs.edit { putStringSet(KEY_FAVORITE_SETTINGS, current.toSet()) }
        added
    }

    fun setFavoriteIds(ids: Set<String>) = synchronized(lock) {
        prefs.edit { putStringSet(KEY_FAVORITE_SETTINGS, ids.toSet()) }
    }

    fun getModifiedSettings(): List<ModifiedSetting> = synchronized(lock) {
        val set = (prefs.getStringSet(KEY_MODIFIED_SETTINGS, emptySet()) ?: emptySet()).toSet()
        set.mapNotNull { ModifiedSetting.fromPrefString(it) }
            .sortedWith(compareBy<ModifiedSetting> { it.settingsTable.ordinal }.thenBy { it.key.lowercase() })
    }

    fun addModifiedSetting(setting: ModifiedSetting) = synchronized(lock) {
        val normalizedTable = setting.settingsTable.name
        val normalized = setting.copy(table = normalizedTable)
        val current = getModifiedSettings().toMutableList()
        current.removeAll { it.settingsTable.name == normalizedTable && it.key == normalized.key }

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

    fun saveModifiedSettingsBatch(settings: List<ModifiedSetting>) = synchronized(lock) {
        val merged = getModifiedSettings().associateBy { it.uniqueId }.toMutableMap()
        for (item in settings) {
            val normalized = item.copy(table = item.settingsTable.name)
            val isRevertedToStock = !normalized.isDeleted &&
                normalized.stockValue != null &&
                normalized.value == normalized.stockValue
            if (isRevertedToStock) {
                merged.remove(normalized.uniqueId)
            } else {
                merged[normalized.uniqueId] = normalized
            }
        }
        prefs.edit {
            putStringSet(KEY_MODIFIED_SETTINGS, merged.values.map { it.toPrefString() }.toSet())
        }
    }

    fun removeModifiedSetting(table: String, key: String) = synchronized(lock) {
        val normalizedTable = SettingsTable.fromNameOrDefault(table).name
        val current = getModifiedSettings().toMutableList()
        current.removeAll { it.settingsTable.name == normalizedTable && it.key == key }
        prefs.edit {
            putStringSet(KEY_MODIFIED_SETTINGS, current.map { it.toPrefString() }.toSet())
        }
    }

    fun clearAllModifiedSettings() = synchronized(lock) {
        prefs.edit { remove(KEY_MODIFIED_SETTINGS) }
    }

    fun resetAllPreferences() = synchronized(lock) {
        prefs.edit { clear() }
    }

    /**
     * Exports all modified settings and favorites into a structured JSON snapshot string.
     */
    fun exportSnapshotJson(): String {
        val root = JSONObject()
        root.put("format", "seteditplus-snapshot")
        root.put("version", 2)
        root.put("exportedAt", System.currentTimeMillis())

        val modsArray = JSONArray()
        getModifiedSettings().forEach { mod ->
            modsArray.put(mod.toJsonObject())
        }
        root.put("modifiedSettings", modsArray)

        val favArray = JSONArray()
        getFavoriteIds().sorted().forEach { fav ->
            favArray.put(fav)
        }
        root.put("favorites", favArray)
        return root.toString(2)
    }

    data class SnapshotPayload(
        val modifiedSettings: List<ModifiedSetting>,
        val favorites: Set<String>
    )

    fun parseSnapshotJson(rawJson: String): SnapshotPayload {
        val root = JSONObject(rawJson.trim())
        val modsArray = root.optJSONArray("modifiedSettings") ?: JSONArray()
        val parsedMods = mutableListOf<ModifiedSetting>()
        for (i in 0 until modsArray.length()) {
            val obj = modsArray.optJSONObject(i) ?: continue
            ModifiedSetting.fromJsonObject(obj)?.let { parsedMods.add(it) }
        }

        val favArray = root.optJSONArray("favorites") ?: JSONArray()
        val parsedFavs = mutableSetOf<String>()
        for (i in 0 until favArray.length()) {
            val item = favArray.optString(i).trim()
            if (item.contains(":")) {
                parsedFavs.add(item)
            }
        }
        return SnapshotPayload(parsedMods, parsedFavs)
    }

    companion object {
        private const val PREFS_NAME = "setedit_prefs"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_PERMISSION_MODE = "permission_mode"
        private const val KEY_AUTOSTART = "autostart"
        private const val KEY_SAFE_BOOT_GUARD = "safe_boot_guard"
        private const val KEY_LAST_BOOT_TS = "last_boot_ts"
        private const val KEY_RAPID_BOOT_COUNT = "rapid_boot_count"
        private const val KEY_BOOT_GUARD_TRIPPED = "boot_guard_tripped"
        private const val KEY_MODIFIED_SETTINGS = "modified_settings"
        private const val KEY_FAVORITE_SETTINGS = "favorite_settings"

        private const val RAPID_REBOOT_WINDOW_MS = 180_000L // 3 minutes
        private const val MAX_RAPID_REBOOTS_BEFORE_TRIP = 3
    }
}
