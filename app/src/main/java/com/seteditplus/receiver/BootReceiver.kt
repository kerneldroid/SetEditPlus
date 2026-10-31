package com.seteditplus.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.seteditplus.data.PermissionChecker
import com.seteditplus.data.PermissionMode
import com.seteditplus.data.PreferencesRepository
import com.seteditplus.data.SettingsRepository
import com.seteditplus.data.SettingsTable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }

        val prefs = PreferencesRepository(context)
        if (!prefs.isAutostartEnabled()) return

        val mode = prefs.getPermissionMode() ?: return
        val modified = prefs.getModifiedSettings()
        if (modified.isEmpty()) return

        Log.d(TAG, "Applying ${modified.size} modified settings via $mode (trigger=$action)")

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val available = waitForPermissionAvailability(mode)
                if (!available) {
                    Log.e(TAG, "Permission mode $mode not available on boot")
                    return@launch
                }

                val repo = SettingsRepository(context, mode)
                for (setting in modified) {
                    try {
                        val table = SettingsTable.fromNameOrDefault(setting.table)
                        if (setting.isDeleted) {
                            val success = repo.delete(table, setting.key)
                            Log.d(TAG, "Re-deleted ${table.name}/${setting.key} ($success)")
                        } else {
                            val success = repo.put(table, setting.key, setting.value)
                            Log.d(TAG, "Restored ${table.name}/${setting.key}=${setting.value} ($success)")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to restore ${setting.key}", e)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun waitForPermissionAvailability(mode: PermissionMode): Boolean {
        return when (mode) {
            PermissionMode.ROOT -> PermissionChecker.isRootAvailable()
            PermissionMode.SHIZUKU -> {
                repeat(SHIZUKU_RETRY_ATTEMPTS) { attempt ->
                    if (PermissionChecker.isShizukuAvailable() && PermissionChecker.isShizukuPermissionGranted()) {
                        return true
                    }
                    if (attempt < SHIZUKU_RETRY_ATTEMPTS - 1) {
                        delay(SHIZUKU_RETRY_DELAY_MS)
                    }
                }
                false
            }
        }
    }

    companion object {
        private const val TAG = "SetEditPlusBoot"
        private const val SHIZUKU_RETRY_ATTEMPTS = 5
        private const val SHIZUKU_RETRY_DELAY_MS = 1000L
    }
}
