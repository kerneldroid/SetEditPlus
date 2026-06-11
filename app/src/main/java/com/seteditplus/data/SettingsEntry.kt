package com.seteditplus.data

/**
 * Represents a single system setting entry.
 */
data class SettingsEntry(
    val key: String,
    val value: String
)

/**
 * The three tables in Android's SettingsProvider.
 */
enum class SettingsTable(val displayName: String, val contentUri: String) {
    SYSTEM("System", "content://settings/system"),
    SECURE("Secure", "content://settings/secure"),
    GLOBAL("Global", "content://settings/global")
}
