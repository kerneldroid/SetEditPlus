package com.seteditplus.data

import android.content.Context
import android.util.Log
import rikka.shizuku.Shizuku
import java.util.concurrent.TimeUnit

/**
 * Repository that reads/writes Android Settings via Root or Shizuku.
 * Supports both single commands and atomic single-shell batch execution.
 */
class SettingsRepository(
    @Suppress("unused") private val context: Context,
    private val mode: PermissionMode
) {

    sealed interface BatchOperation {
        val table: SettingsTable
        val key: String

        data class Put(
            override val table: SettingsTable,
            override val key: String,
            val value: String
        ) : BatchOperation

        data class Delete(
            override val table: SettingsTable,
            override val key: String
        ) : BatchOperation
    }

    data class BatchExecutionSummary(
        val totalCount: Int,
        val successCount: Int,
        val failureCount: Int,
        val firstErrorMessage: String? = null
    ) {
        val isAllSuccess: Boolean
            get() = failureCount == 0 && totalCount > 0
    }

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
        return putWithResult(table, key, value).isSuccess
    }

    fun putWithResult(table: SettingsTable, key: String, value: String): CommandResult {
        if (!isValidKey(key)) {
            return CommandResult(-1, "", "Invalid setting key: $key")
        }
        return executeSettingsCommand(arrayOf("settings", "put", table.cliName, key, value))
    }

    /**
     * Delete a setting and verify command success.
     */
    fun delete(table: SettingsTable, key: String): Boolean {
        return deleteWithResult(table, key).isSuccess
    }

    fun deleteWithResult(table: SettingsTable, key: String): CommandResult {
        if (!isValidKey(key)) {
            return CommandResult(-1, "", "Invalid setting key: $key")
        }
        return executeSettingsCommand(arrayOf("settings", "delete", table.cliName, key))
    }

    /**
     * Executes a batch of Put/Delete operations inside a single shell invocation
     * (`su -c` or Shizuku `sh -c`) to avoid O(N) process creation overhead on boot or bulk restore.
     */
    fun executeBatch(operations: List<BatchOperation>): BatchExecutionSummary {
        val validOps = operations.filter { isValidKey(it.key) }
        if (validOps.isEmpty()) {
            return BatchExecutionSummary(0, 0, operations.size, "No valid operations to execute")
        }

        val scriptBuilder = StringBuilder()
        validOps.forEachIndexed { index, op ->
            val cmd = when (op) {
                is BatchOperation.Put ->
                    "settings put ${escapeShellArg(op.table.cliName)} ${escapeShellArg(op.key)} ${escapeShellArg(op.value)}"
                is BatchOperation.Delete ->
                    "settings delete ${escapeShellArg(op.table.cliName)} ${escapeShellArg(op.key)}"
            }
            scriptBuilder.append("if ").append(cmd).append("; then echo \"__OK_$index\"; else echo \"__ERR_$index\"; fi\n")
        }

        val result = executeRawShellScript(scriptBuilder.toString(), timeoutSeconds = 30L)
        val lines = result.stdout.lines()
        val okCount = lines.count { it.startsWith("__OK_") }
        val errCount = (validOps.size - okCount).coerceAtLeast(0) + (operations.size - validOps.size)

        return BatchExecutionSummary(
            totalCount = operations.size,
            successCount = okCount,
            failureCount = errCount,
            firstErrorMessage = if (errCount > 0) result.errorMessage else null
        )
    }

    private fun executeSettingsCommand(args: Array<String>): CommandResult {
        val escapedCommand = args.joinToString(" ") { arg -> escapeShellArg(arg) }
        return executeRawShellScript(escapedCommand, COMMAND_TIMEOUT_SECONDS)
    }

    private fun executeRawShellScript(script: String, timeoutSeconds: Long): CommandResult {
        return when (mode) {
            PermissionMode.ROOT -> executeRootScript(script, timeoutSeconds)
            PermissionMode.SHIZUKU -> executeShizukuScript(script, timeoutSeconds)
        }
    }

    private fun executeRootScript(script: String, timeoutSeconds: Long): CommandResult {
        var process: Process? = null
        return try {
            process = Runtime.getRuntime().exec(arrayOf("su", "-c", script))
            collectProcessOutput(process, timeoutSeconds)
        } catch (e: Exception) {
            Log.e(TAG, "Root command execution failed", e)
            CommandResult(exitCode = -1, stdout = "", stderr = e.message ?: "Root execution error")
        } finally {
            process?.destroy()
        }
    }

    private fun executeShizukuScript(script: String, timeoutSeconds: Long): CommandResult {
        var process: Process? = null
        return try {
            val method = shizukuNewProcessMethod
            process = method.invoke(null, arrayOf("sh", "-c", script), null, null) as Process
            val result = collectProcessOutput(process, timeoutSeconds)
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

    private fun collectProcessOutput(process: Process, timeoutSeconds: Long): CommandResult {
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
        val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
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

        private val shizukuNewProcessMethod by lazy {
            Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            ).apply {
                isAccessible = true
            }
        }

        fun isValidKey(key: String): Boolean {
            return key.isNotBlank() && VALID_KEY_REGEX.matches(key)
        }

        fun escapeShellArg(arg: String): String {
            return "'" + arg.replace("'", "'\\''") + "'"
        }

        /**
         * Generates a standalone POSIX / ADB shell script for the given modified settings.
         */
        fun buildShellScript(modifiedSettings: List<ModifiedSetting>): String {
            val sb = StringBuilder()
            sb.appendLine("#!/system/bin/sh")
            sb.appendLine("# Generated by SetEdit+ (${modifiedSettings.size} operations)")
            for (mod in modifiedSettings) {
                val cliTable = mod.settingsTable.cliName
                if (mod.isDeleted) {
                    sb.appendLine("settings delete $cliTable ${escapeShellArg(mod.key)}")
                } else {
                    sb.appendLine("settings put $cliTable ${escapeShellArg(mod.key)} ${escapeShellArg(mod.value)}")
                }
            }
            return sb.toString()
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
