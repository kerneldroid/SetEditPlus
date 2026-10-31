package com.seteditplus.data

import android.content.Context
import android.util.Log
import rikka.shizuku.Shizuku
import java.util.concurrent.TimeUnit

/**
 * Repository that reads/writes Android Settings via Root or Shizuku.
 */
class SettingsRepository(
    @Suppress("unused") private val context: Context,
    private val mode: PermissionMode
) {

    data class CommandResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String
    ) {
        val isSuccess: Boolean
            get() = exitCode == 0 && !hasSecurityOrCommandError(stdout) && !hasSecurityOrCommandError(stderr)

        val errorMessage: String?
            get() = when {
                isSuccess -> null
                stderr.isNotBlank() -> stderr.lineSequence().firstOrNull { it.isNotBlank() }?.trim()
                stdout.isNotBlank() && hasSecurityOrCommandError(stdout) ->
                    stdout.lineSequence().firstOrNull { it.isNotBlank() }?.trim()
                else -> "Command exited with code $exitCode"
            }

        companion object {
            private val ERROR_MARKERS = listOf(
                "SecurityException",
                "Permission denial",
                "Permission Denial",
                "IllegalArgumentException",
                "Exception occurred",
                "Failure calling service",
                "Error:",
                "Invalid table"
            )

            fun hasSecurityOrCommandError(text: String): Boolean {
                if (text.isBlank()) return false
                return ERROR_MARKERS.any { marker -> text.contains(marker, ignoreCase = true) }
            }
        }
    }

    /**
     * Read all settings from the given table.
     */
    fun getAll(table: SettingsTable): List<SettingsEntry> {
        val result = executeSettingsCommand(arrayOf("settings", "list", table.cliName))
        if (!result.isSuccess && result.stdout.isBlank()) {
            throw IllegalStateException(result.errorMessage ?: "Failed to read ${table.displayName} table")
        }
        return parseSettingsList(result.stdout, table)
    }

    /**
     * Read a single setting.
     */
    fun get(table: SettingsTable, key: String): String? {
        if (!isValidKey(key)) return null
        val result = executeSettingsCommand(arrayOf("settings", "get", table.cliName, key))
        if (!result.isSuccess) return null
        val trimmed = result.stdout.trimEnd('\r', '\n')
        return if (trimmed == "null" || trimmed.isEmpty()) null else trimmed
    }

    /**
     * Write (put) a setting and verify command success.
     */
    fun put(table: SettingsTable, key: String, value: String): Boolean {
        if (!isValidKey(key)) return false
        val result = executeSettingsCommand(buildPutArgs(table, key, value))
        return result.isSuccess
    }

    /**
     * Delete a setting and verify command success.
     */
    fun delete(table: SettingsTable, key: String): Boolean {
        if (!isValidKey(key)) return false
        val result = executeSettingsCommand(arrayOf("settings", "delete", table.cliName, key))
        return result.isSuccess
    }

    private fun buildPutArgs(table: SettingsTable, key: String, value: String): Array<String> {
        // Android's `settings` command treats values starting with '-' as options unless handled
        return arrayOf("settings", "put", table.cliName, key, value)
    }

    private fun executeSettingsCommand(args: Array<String>): CommandResult {
        return when (mode) {
            PermissionMode.ROOT -> executeRootCommand(args)
            PermissionMode.SHIZUKU -> executeShizukuCommand(args)
        }
    }

    private fun executeRootCommand(args: Array<String>): CommandResult {
        val escapedCommand = args.joinToString(" ") { arg -> escapeShellArg(arg) }
        var process: Process? = null
        return try {
            process = Runtime.getRuntime().exec(arrayOf("su", "-c", escapedCommand))
            collectProcessOutput(process)
        } catch (e: Exception) {
            Log.e(TAG, "Root command execution failed", e)
            CommandResult(exitCode = -1, stdout = "", stderr = e.message ?: "Root execution error")
        } finally {
            process?.destroy()
        }
    }

    private fun executeShizukuCommand(args: Array<String>): CommandResult {
        var process: Process? = null
        return try {
            val escapedCommand = args.joinToString(" ") { arg -> escapeShellArg(arg) }
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            process = method.invoke(null, arrayOf("sh", "-c", escapedCommand), null, null) as Process
            val result = collectProcessOutput(process)
            if (!result.isSuccess) {
                Log.w(TAG, "Shizuku command warning/error: ${result.errorMessage}")
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Shizuku command failed", e)
            CommandResult(exitCode = -1, stdout = "", stderr = e.message ?: "Shizuku execution error")
        } finally {
            process?.destroy()
        }
    }

    private fun collectProcessOutput(process: Process): CommandResult {
        var stderrText = ""
        val stderrThread = Thread {
            stderrText = try {
                process.errorStream.bufferedReader().use { it.readText() }
            } catch (_: Exception) {
                ""
            }
        }
        stderrThread.start()
        val stdoutText = try {
            process.inputStream.bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            ""
        }
        val finished = process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            stderrThread.join(500)
            return CommandResult(exitCode = -1, stdout = stdoutText, stderr = "Command timed out")
        }
        stderrThread.join(2000)
        return CommandResult(
            exitCode = process.exitValue(),
            stdout = stdoutText,
            stderr = stderrText
        )
    }

    companion object {
        private const val TAG = "SettingsRepository"
        private const val COMMAND_TIMEOUT_SECONDS = 15L
        private val VALID_KEY_REGEX = Regex("^[A-Za-z0-9_.:-]+$")

        fun isValidKey(key: String): Boolean {
            return key.isNotBlank() && VALID_KEY_REGEX.matches(key)
        }

        fun escapeShellArg(arg: String): String {
            return "'" + arg.replace("'", "'\\''") + "'"
        }

        /**
         * Parses `settings list <table>` output without corrupting the final item with a trailing newline
         * and without splitting multi-line values that happen to contain `=` on continuation lines.
         */
        fun parseSettingsList(raw: String, table: SettingsTable = SettingsTable.SYSTEM): List<SettingsEntry> {
            val cleaned = raw.trimEnd('\r', '\n')
            if (cleaned.isEmpty()) return emptyList()

            val result = mutableListOf<SettingsEntry>()
            val lines = cleaned.lines()
            var currentKey: String? = null
            var currentValue: StringBuilder? = null

            for (line in lines) {
                val idx = line.indexOf('=')
                val candidateKey = if (idx > 0) line.substring(0, idx) else null
                if (candidateKey != null && isValidKey(candidateKey)) {
                    if (currentKey != null && currentValue != null) {
                        result.add(SettingsEntry(currentKey, currentValue.toString(), table))
                    }
                    currentKey = candidateKey
                    currentValue = StringBuilder(line.substring(idx + 1))
                } else if (currentValue != null) {
                    currentValue.append("\n").append(line)
                }
            }
            if (currentKey != null && currentValue != null) {
                result.add(SettingsEntry(currentKey, currentValue.toString(), table))
            }

            return result.sortedBy { it.key.lowercase() }
        }
    }
}
