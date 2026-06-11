package com.seteditplus.data

import rikka.shizuku.Shizuku

/**
 * Checks availability of Root and Shizuku permission modes.
 */
object PermissionChecker {

    /**
     * Check if device has root access.
     */
    fun isRootAvailable(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check if Shizuku service is running and accessible.
     */
    fun isShizukuAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check if Shizuku permission is granted.
     */
    fun isShizukuPermissionGranted(): Boolean {
        return try {
            Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }
}
