package com.seteditplus.data

/**
 * Represents a single system setting entry bound to a specific [SettingsTable].
 */
data class SettingsEntry(
    val key: String,
    val value: String,
    val table: SettingsTable = SettingsTable.SYSTEM
) {
    val uniqueId: String
        get() = "${table.name}:$key"
}

/**
 * The three tables in Android's SettingsProvider.
 */
enum class SettingsTable(val displayName: String, val cliName: String, val contentUri: String) {
    SYSTEM("System", "system", "content://settings/system"),
    SECURE("Secure", "secure", "content://settings/secure"),
    GLOBAL("Global", "global", "content://settings/global");

    companion object {
        fun fromNameOrDefault(name: String?, default: SettingsTable = SYSTEM): SettingsTable {
            if (name.isNullOrBlank()) return default
            return entries.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) } ?: default
        }
    }
}
