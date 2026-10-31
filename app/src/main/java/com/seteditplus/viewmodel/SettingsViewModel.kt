package com.seteditplus.viewmodel

import android.app.Application
import android.content.pm.PackageManager
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.seteditplus.data.ModifiedSetting
import com.seteditplus.data.PermissionChecker
import com.seteditplus.data.PermissionMode
import com.seteditplus.data.PreferencesRepository
import com.seteditplus.data.SettingsEntry
import com.seteditplus.data.SettingsRepository
import com.seteditplus.data.SettingsTable
import com.seteditplus.data.ThemeMode
import com.seteditplus.ui.screens.ExtendedTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    val preferences = PreferencesRepository(application)

    val permissionMode = mutableStateOf<PermissionMode?>(preferences.getPermissionMode())
    val isPermissionGranted = mutableStateOf(false)
    val permissionError = mutableStateOf<String?>(null)

    val themeMode = mutableStateOf(preferences.getThemeMode())
    val isAutostartEnabled = mutableStateOf(preferences.isAutostartEnabled())

    val currentTab = mutableStateOf(ExtendedTab.SYSTEM)
    var currentTable: SettingsTable = SettingsTable.SYSTEM

    private val _allSettings = mutableStateListOf<SettingsEntry>()
    val filteredSettings = mutableStateListOf<SettingsEntry>()

    /**
     * Cached modified settings map keyed by composite "${table}:${key}" to prevent cross-table collisions.
     */
    val modifiedSettingsMap = mutableStateMapOf<String, ModifiedSetting>()

    val searchQuery = mutableStateOf("")
    val isSearchActive = mutableStateOf(false)
    val isLoading = mutableStateOf(false)
    val isRefreshing = mutableStateOf(false)
    val operationMessage = mutableStateOf<String?>(null)

    private var repository: SettingsRepository? = null
    private var searchJob: Job? = null

    private val shizukuListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == SHIZUKU_PERMISSION_REQUEST_CODE) {
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                viewModelScope.launch {
                    finalizePermissionSuccess(PermissionMode.SHIZUKU)
                }
            } else {
                permissionError.value = "Shizuku access denied."
                isLoading.value = false
            }
        }
    }

    init {
        try {
            Shizuku.addRequestPermissionResultListener(shizukuListener)
        } catch (e: Exception) {
            Log.w(TAG, "Shizuku listener registration failed", e)
        }

        permissionMode.value?.let { mode ->
            selectPermissionMode(mode)
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            Shizuku.removeRequestPermissionResultListener(shizukuListener)
        } catch (e: Exception) {
            Log.w(TAG, "Shizuku listener removal failed", e)
        }
    }

    fun getModifiedSetting(table: SettingsTable, key: String): ModifiedSetting? {
        return modifiedSettingsMap[ModifiedSetting.uniqueKey(table, key)]
    }

    fun selectPermissionMode(mode: PermissionMode) {
        viewModelScope.launch {
            isLoading.value = true
            permissionError.value = null

            when (mode) {
                PermissionMode.ROOT -> {
                    val available = withContext(Dispatchers.IO) { PermissionChecker.isRootAvailable() }
                    if (available) {
                        finalizePermissionSuccess(mode)
                    } else {
                        permissionError.value = "Root access not detected. Try using Shizuku."
                        if (repository == null) {
                            isPermissionGranted.value = false
                        }
                        isLoading.value = false
                    }
                }
                PermissionMode.SHIZUKU -> {
                    val available = withContext(Dispatchers.IO) { PermissionChecker.isShizukuAvailable() }
                    if (!available) {
                        permissionError.value = "Shizuku is not running."
                        if (repository == null) {
                            isPermissionGranted.value = false
                        }
                        isLoading.value = false
                        return@launch
                    }

                    val granted = withContext(Dispatchers.IO) { PermissionChecker.isShizukuPermissionGranted() }
                    if (granted) {
                        finalizePermissionSuccess(mode)
                    } else {
                        try {
                            Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
                        } catch (e: Exception) {
                            permissionError.value = "Shizuku request error: ${e.message}"
                            isLoading.value = false
                        }
                    }
                }
            }
        }
    }

    private fun finalizePermissionSuccess(mode: PermissionMode) {
        permissionMode.value = mode
        preferences.setPermissionMode(mode)
        isPermissionGranted.value = true
        repository = SettingsRepository(getApplication(), mode)
        loadSettings()
        isLoading.value = false
    }

    fun setTheme(mode: ThemeMode) {
        themeMode.value = mode
        preferences.setThemeMode(mode)
    }

    fun setAutostart(enabled: Boolean) {
        isAutostartEnabled.value = enabled
        preferences.setAutostartEnabled(enabled)
    }

    fun resetPreferences() {
        val repo = repository
        val mods = preferences.getModifiedSettings()

        viewModelScope.launch {
            if (repo != null && mods.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    mods.forEach { mod ->
                        try {
                            val table = mod.settingsTable
                            if (mod.stockValue == null) {
                                repo.delete(table, mod.key)
                            } else {
                                repo.put(table, mod.key, mod.stockValue)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to restore ${mod.key}", e)
                        }
                    }
                }
            }
            withContext(Dispatchers.Main) {
                preferences.resetAllPreferences()
                themeMode.value = ThemeMode.SYSTEM
                isAutostartEnabled.value = false
                permissionMode.value = null
                isPermissionGranted.value = false
                repository = null
                _allSettings.clear()
                filteredSettings.clear()
                modifiedSettingsMap.clear()
                operationMessage.value = "Preferences reset to stock"
            }
        }
    }

    fun refreshSettings() {
        if (repository == null) return
        viewModelScope.launch {
            isRefreshing.value = true
            loadSettingsInternal()
            withContext(Dispatchers.Main) {
                delay(300)
                isRefreshing.value = false
            }
        }
    }

    fun switchTab(tab: ExtendedTab) {
        currentTab.value = tab
        if (tab != ExtendedTab.MODIFIED) {
            currentTable = when (tab) {
                ExtendedTab.SYSTEM -> SettingsTable.SYSTEM
                ExtendedTab.SECURE -> SettingsTable.SECURE
                ExtendedTab.GLOBAL -> SettingsTable.GLOBAL
                ExtendedTab.MODIFIED -> currentTable
            }
        }
        loadSettings()
    }

    fun loadSettings() {
        if (repository == null) return
        viewModelScope.launch {
            isLoading.value = true
            loadSettingsInternal()
            isLoading.value = false
        }
    }

    private suspend fun loadSettingsInternal() {
        val repo = repository ?: return
        try {
            val modified = withContext(Dispatchers.IO) { preferences.getModifiedSettings() }
            withContext(Dispatchers.Main) {
                modifiedSettingsMap.clear()
                modified.forEach { modifiedSettingsMap[it.uniqueId] = it }
            }

            if (currentTab.value == ExtendedTab.MODIFIED) {
                val liveEntries = withContext(Dispatchers.IO) {
                    modified.map { mod ->
                        val table = mod.settingsTable
                        if (mod.isDeleted) {
                            SettingsEntry(mod.key, "", table)
                        } else {
                            val liveValue = repo.get(table, mod.key) ?: mod.value
                            SettingsEntry(mod.key, liveValue, table)
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    _allSettings.clear()
                    _allSettings.addAll(liveEntries)
                }
            } else {
                val tableToLoad = currentTable
                val entries = withContext(Dispatchers.IO) {
                    repo.getAll(tableToLoad)
                }
                withContext(Dispatchers.Main) {
                    _allSettings.clear()
                    _allSettings.addAll(entries)
                }
            }

            applyFilter()
        } catch (e: Exception) {
            Log.e(TAG, "Error loading settings", e)
            withContext(Dispatchers.Main) {
                operationMessage.value = "Load error: ${e.message}"
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        searchQuery.value = query
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(150)
            applyFilter()
        }
    }

    private suspend fun applyFilter() {
        val query = searchQuery.value.trim().lowercase()
        val snapshot = _allSettings.toList()
        val result = withContext(Dispatchers.Default) {
            if (query.isEmpty()) {
                snapshot
            } else {
                snapshot.filter {
                    it.key.lowercase().contains(query) ||
                        it.value.lowercase().contains(query) ||
                        it.table.displayName.lowercase().contains(query)
                }
            }
        }

        withContext(Dispatchers.Main) {
            filteredSettings.clear()
            filteredSettings.addAll(result)
        }
    }

    fun putSetting(key: String, value: String, table: SettingsTable = currentTable) {
        val repo = repository ?: return
        val trimmedKey = key.trim()
        if (!SettingsRepository.isValidKey(trimmedKey)) {
            operationMessage.value = "Error: Invalid setting key format"
            return
        }

        val existingMod = preferences.getModifiedSettings()
            .find { it.settingsTable == table && it.key == trimmedKey }

        viewModelScope.launch {
            val stockValue = withContext(Dispatchers.IO) {
                when {
                    existingMod != null -> existingMod.stockValue
                    currentTab.value != ExtendedTab.MODIFIED && table == currentTable ->
                        _allSettings.find { it.table == table && it.key == trimmedKey }?.value
                            ?: repo.get(table, trimmedKey)
                    else -> repo.get(table, trimmedKey)
                }
            }

            val success = withContext(Dispatchers.IO) {
                repo.put(table, trimmedKey, value)
            }
            if (success) {
                withContext(Dispatchers.Main) {
                    val mod = ModifiedSetting(table.name, trimmedKey, value, stockValue, false)
                    preferences.addModifiedSetting(mod)
                    val refreshedMods = preferences.getModifiedSettings()
                    modifiedSettingsMap.clear()
                    refreshedMods.forEach { modifiedSettingsMap[it.uniqueId] = it }
                    operationMessage.value = "✓ Value updated"
                    loadSettings()
                }
            } else {
                withContext(Dispatchers.Main) {
                    operationMessage.value = "✗ Error writing setting"
                }
            }
        }
    }

    fun deleteSetting(key: String, table: SettingsTable = currentTable) {
        val repo = repository ?: return
        val existingMod = preferences.getModifiedSettings()
            .find { it.settingsTable == table && it.key == key }
        val stockValue = existingMod?.stockValue
            ?: _allSettings.find { it.table == table && it.key == key }?.value

        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                repo.delete(table, key)
            }
            if (success) {
                withContext(Dispatchers.Main) {
                    val mod = ModifiedSetting(table.name, key, "", stockValue, true)
                    preferences.addModifiedSetting(mod)
                    modifiedSettingsMap[mod.uniqueId] = mod
                    operationMessage.value = "✓ Setting deleted"
                    loadSettings()
                }
            } else {
                withContext(Dispatchers.Main) {
                    operationMessage.value = "✗ Error deleting setting"
                }
            }
        }
    }

    fun restoreSetting(key: String, table: SettingsTable = currentTable) {
        val repo = repository ?: return
        val mod = preferences.getModifiedSettings()
            .find { it.settingsTable == table && it.key == key } ?: return
        val targetTable = mod.settingsTable

        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                if (mod.stockValue == null) {
                    repo.delete(targetTable, key)
                } else {
                    repo.put(targetTable, key, mod.stockValue)
                }
            }
            if (success) {
                withContext(Dispatchers.Main) {
                    preferences.removeModifiedSetting(targetTable.name, key)
                    modifiedSettingsMap.remove(mod.uniqueId)
                    operationMessage.value = "✓ Setting restored to stock"
                    loadSettings()
                }
            } else {
                withContext(Dispatchers.Main) {
                    operationMessage.value = "Error: Failed to restore setting"
                }
            }
        }
    }

    fun addSetting(key: String, value: String, table: SettingsTable = currentTable) {
        putSetting(key, value, table)
    }

    fun clearMessage() {
        operationMessage.value = null
    }

    companion object {
        private const val TAG = "SettingsViewModel"
        private const val SHIZUKU_PERMISSION_REQUEST_CODE = 101
    }
}
