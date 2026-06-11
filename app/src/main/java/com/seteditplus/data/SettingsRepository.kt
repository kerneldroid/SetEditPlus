package com.seteditplus.data

import android.content.Context
import android.util.Log
import rikka.shizuku.Shizuku

/**
 * Repository that reads/writes Android Settings via Root or Shizuku.
 *
 * Root mode uses `settings` CLI commands via su.
 * Shizuku mode uses content resolver after granting Shizuku permission.
 */
class SettingsRepository(
    private val context: Context,
    private val mode: PermissionMode
) {

    /**
     * Read all settings from the given table.
     */
    fun getAll(table: SettingsTable): List<SettingsEntry> {
        return when (mode) {
            PermissionMode.ROOT -> getAllViaRoot(table)
            PermissionMode.SHIZUKU -> getAllViaShizuku(table)
        }
    }

    /**
     * Read a single setting.
     */
    fun get(table: SettingsTable, key: String): String? {
        return when (mode) {
            PermissionMode.ROOT -> getViaRoot(table, key)
            PermissionMode.SHIZUKU -> getViaShizuku(table, key)
        }
    }

    /**
     * Write (put) a setting.
     */
    fun put(table: SettingsTable, key: String, value: String): Boolean {
        return when (mode) {
            PermissionMode.ROOT -> putViaRoot(table, key, value)
            PermissionMode.SHIZUKU -> putViaShizuku(table, key, value)
        }
    }

    /**
     * Delete a setting.
     */
    fun delete(table: SettingsTable, key: String): Boolean {
        return when (mode) {
            PermissionMode.ROOT -> deleteViaRoot(table, key)
            PermissionMode.SHIZUKU -> deleteViaShizuku(table, key)
        }
    }

    private fun getAllViaRoot(table: SettingsTable): List<SettingsEntry> {
        val tableName = table.name.lowercase()
        val result = executeRootCommand(arrayOf("settings", "list", tableName))
        return parseSettingsList(result)
    }

    private fun getViaRoot(table: SettingsTable, key: String): String? {
        val tableName = table.name.lowercase()
        val result = executeRootCommand(arrayOf("settings", "get", tableName, key)).trim()
        return if (result == "null" || result.isEmpty()) null else result
    }

    private fun putViaRoot(table: SettingsTable, key: String, value: String): Boolean {
        val tableName = table.name.lowercase()
        return try {
            executeRootCommand(arrayOf("settings", "put", tableName, key, value))
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun deleteViaRoot(table: SettingsTable, key: String): Boolean {
        val tableName = table.name.lowercase()
        return try {
            executeRootCommand(arrayOf("settings", "delete", tableName, key))
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun executeRootCommand(args: Array<String>): String {
        /**
         * 'su -c' evaluates the command in a shell. 
         * Arguments are properly quoted to prevent command injection.
         */
        val escapedCommand = args.joinToString(" ") { arg ->
            "'" + arg.replace("'", "'\\''") + "'"
        }

        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", escapedCommand))
        return try {
            val stderrThread = Thread { process.errorStream.use { it.readBytes() } }
            stderrThread.start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
            stderrThread.join(3000)
            output
        } finally {
            process.destroy()
        }
    }

    private fun getAllViaShizuku(table: SettingsTable): List<SettingsEntry> {
        return try {
            val result = executeShizukuCommand(arrayOf("settings", "list", table.name.lowercase()))
            parseSettingsList(result)
        } catch (e: Exception) {
            Log.e("SettingsRepository", "Failed to list via Shizuku", e)
            emptyList()
        }
    }

    private fun getViaShizuku(table: SettingsTable, key: String): String? {
        return try {
            val result = executeShizukuCommand(arrayOf("settings", "get", table.name.lowercase(), key)).trim()
            if (result == "null" || result.isEmpty()) null else result
        } catch (e: Exception) {
            Log.e("SettingsRepository", "Failed to get via Shizuku", e)
            null
        }
    }

    private fun putViaShizuku(table: SettingsTable, key: String, value: String): Boolean {
        return try {
            executeShizukuCommand(arrayOf("settings", "put", table.name.lowercase(), key, value))
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun deleteViaShizuku(table: SettingsTable, key: String): Boolean {
        return try {
            executeShizukuCommand(arrayOf("settings", "delete", table.name.lowercase(), key))
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun executeShizukuCommand(args: Array<String>): String {
        return try {
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            val process = method.invoke(null, args, null, null) as Process
            try {
                val stderrThread = Thread { 
                    val errorOutput = process.errorStream.bufferedReader().use { it.readText() }
                    if (errorOutput.contains("SecurityException", ignoreCase = true) || 
                        errorOutput.contains("Permission Denial", ignoreCase = true)) {
                        Log.e("SettingsRepository", "Android 17+ Security Restriction: $errorOutput")
                    }
                }
                stderrThread.start()
                val output = process.inputStream.bufferedReader().use { it.readText() }
                process.waitFor()
                stderrThread.join(1000)
                output
            } finally {
                process.destroy()
            }
        } catch (e: Exception) {
            Log.e("SettingsRepository", "Shizuku command failed", e)
            ""
        }
    }

    private fun parseSettingsList(raw: String): List<SettingsEntry> {
        val result = mutableListOf<SettingsEntry>()
        val lines = raw.lines()
        var currentKey: String? = null
        var currentValue: StringBuilder? = null

        for (line in lines) {
            val idx = line.indexOf('=')
            if (idx != -1) {
                if (currentKey != null && currentValue != null) {
                    result.add(SettingsEntry(currentKey, currentValue.toString()))
                }
                currentKey = line.substring(0, idx)
                currentValue = StringBuilder(line.substring(idx + 1))
            } else {
                if (currentValue != null) {
                    currentValue.append("\n").append(line)
                }
            }
        }
        if (currentKey != null && currentValue != null) {
            result.add(SettingsEntry(currentKey, currentValue.toString()))
        }
        
        return result.sortedBy { it.key.lowercase() }
    }
}
