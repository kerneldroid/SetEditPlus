package com.seteditplus.data

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.util.concurrent.TimeUnit

/**
 * Checks availability of Root and Shizuku permission modes with bounded timeouts.
 */
object PermissionChecker {

    private const val ROOT_CHECK_TIMEOUT_SECONDS = 8L

    /**
     * Check if device has root access without hanging indefinitely on unresponsive su binaries.
     */
    fun isRootAvailable(): Boolean {
        var process: Process? = null
        return try {
            process = ProcessBuilder("su", "-c", "id")
                .redirectErrorStream(true)
                .start()
            val completed = process.waitFor(ROOT_CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!completed) {
                process.destroyForcibly()
                return false
            }
            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.exitValue() == 0 && output.contains("uid=0")
        } catch (_: Exception) {
            false
        } finally {
            process?.destroy()
        }
    }

    /**
     * Check if Shizuku service is running and accessible.
     */
    fun isShizukuAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Check if Shizuku permission is granted.
     */
    fun isShizukuPermissionGranted(): Boolean {
        return try {
            if (!isShizukuAvailable()) return false
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) {
            false
        }
    }
}
