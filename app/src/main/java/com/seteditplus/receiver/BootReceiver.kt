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
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = PreferencesRepository(context)
            if (!prefs.isAutostartEnabled()) return

            val mode = prefs.getPermissionMode() ?: return
            
            Log.d("SetEditPlusBoot", "Applying modified settings via $mode")

            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val available = when (mode) {
                        PermissionMode.ROOT -> PermissionChecker.isRootAvailable()
                        PermissionMode.SHIZUKU -> {
                            PermissionChecker.isShizukuAvailable() && PermissionChecker.isShizukuPermissionGranted()
                        }
                    }

                    if (available) {
                        val repo = SettingsRepository(context, mode)
                        val modified = prefs.getModifiedSettings()
                        for (setting in modified) {
                            try {
                                val table = SettingsTable.valueOf(setting.table.uppercase())
                                if (setting.isDeleted) {
                                    // Setting was deliberately deleted — re-delete it after boot
                                    val success = repo.delete(table, setting.key)
                                    Log.d("SetEditPlusBoot", "Re-deleted ${setting.key} ($success)")
                                } else {
                                    val success = repo.put(table, setting.key, setting.value)
                                    Log.d("SetEditPlusBoot", "Restored ${setting.key}=${setting.value} ($success)")
                                }
                            } catch (e: Exception) {
                                Log.e("SetEditPlusBoot", "Failed to restore ${setting.key}", e)
                            }
                        }
                    } else {
                        Log.e("SetEditPlusBoot", "Permission mode $mode not available on boot")
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
