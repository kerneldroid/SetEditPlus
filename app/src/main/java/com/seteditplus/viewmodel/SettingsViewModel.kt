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
import com.seteditplus.domain.SettingAnalyzer
import com.seteditplus.domain.SettingInspection
import com.seteditplus.domain.SettingValidationResult
import com.seteditplus.ui.screens.ExtendedTab
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.util.concurrent.ConcurrentHashMap

enum class FilterMode {
    ALL,
    FAVORITES_ONLY,
    MODIFIED_IN_TABLE
}

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    val preferences = PreferencesRepository(application)

    val permissionMode = mutableStateOf<PermissionMode?>(preferences.getPermissionMode())
    val isPermissionGranted = mutableStateOf(false)
    val permissionError = mutableStateOf<String?>(null)

    val themeMode = mutableStateOf(preferences.getThemeMode())
    val isAutostartEnabled = mutableStateOf(preferences.isAutostartEnabled())
    val isSafeBootGuardEnabled = mutableStateOf(preferences.isSafeBootGuardEnabled())

    val currentTab = mutableStateOf(ExtendedTab.SYSTEM)
    var currentTable: SettingsTable = SettingsTable.SYSTEM

    val filterMode = mutableStateOf(FilterMode.ALL)

    private val tableCache = ConcurrentHashMap<SettingsTable, List<SettingsEntry>>()
    private val _allSettings = mutableStateListOf<SettingsEntry>()
    val filteredSettings = mutableStateListOf<SettingsEntry>()

    /**
     * Cached modified settings map keyed by composite "${table}:${key}" to prevent cross-table collisions.
     */
    val modifiedSettingsMap = mutableStateMapOf<String, ModifiedSetting>()

    /**
     * Set of favorite composite IDs ("${table}:${key}").
     */
    val favoriteIds = mutableStateOf(preferences.getFavoriteIds())

    val searchQuery = mutableStateOf("")
    val isSearchActive = mutableStateOf(false)
    val isLoading = mutableStateOf(false)
    val isRefreshing = mutableStateOf(false)
    val operationMessage = mutableStateOf<String?>(null)

    private var repository: SettingsRepository? = null
    private var loadJob: Job? = null
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

        if (preferences.consumeBootGuardTrippedFlag()) {
            isAutostartEnabled.value = false
            operationMessage.value = "⚠ Safe Boot Guard disabled Autostart after detecting rapid reboots"
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

    fun isFavorite(table: SettingsTable, key: String): Boolean {
        return favoriteIds.value.contains(ModifiedSetting.uniqueKey(table, key))
    }

    fun toggleFavorite(table: SettingsTable, key: String) {
        val added = preferences.toggleFavorite(table, key)
        favoriteIds.value = preferences.getFavoriteIds()
        operationMessage.value = if (added) "★ Pinned '$key' to favorites" else "Removed '$key' from favorites"
        viewModelScope.launch {
            applyFilter()
        }
    }

    fun setFilterMode(mode: FilterMode) {
        filterMode.value = mode
        viewModelScope.launch {
            applyFilter()
        }
    }

    fun inspectSetting(table: SettingsTable, key: String, currentValue: String): SettingInspection {
        return SettingAnalyzer.inspect(table, key, currentValue)
    }

    fun validateSetting(table: SettingsTable, key: String, newValue: String, currentValue: String? = null): SettingValidationResult {
        return SettingAnalyzer.validate(table, key, newValue, currentValue)
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
        tableCache.clear()
        repository = SettingsRepository(getApplication(), mode)
        loadSettings(forceRefresh = true)
    }

    fun setTheme(mode: ThemeMode) {
        themeMode.value = mode
        preferences.setThemeMode(mode)
    }

    fun setAutostart(enabled: Boolean) {
        isAutostartEnabled.value = enabled
        preferences.setAutostartEnabled(enabled)
    }

    fun setSafeBootGuard(enabled: Boolean) {
        isSafeBootGuardEnabled.value = enabled
        preferences.setSafeBootGuardEnabled(enabled)
    }

    fun resetPreferences() {
        val repo = repository
        val mods = preferences.getModifiedSettings()

        viewModelScope.launch {
            if (repo != null && mods.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    val batchOps = mods.map { mod ->
                        val table = mod.settingsTable
                        if (mod.stockValue == null) {
                            SettingsRepository.BatchOperation.Delete(table, mod.key)
                        } else {
                            SettingsRepository.BatchOperation.Put(table, mod.key, mod.stockValue)
                        }
                    }
                    repo.executeBatch(batchOps)
                }
            }
            withContext(Dispatchers.Main) {
                preferences.resetAllPreferences()
                themeMode.value = ThemeMode.SYSTEM
                isAutostartEnabled.value = false
                isSafeBootGuardEnabled.value = true
                permissionMode.value = null
                isPermissionGranted.value = false
                repository = null
                tableCache.clear()
                _allSettings.clear()
                filteredSettings.clear()
                modifiedSettingsMap.clear()
                favoriteIds.value = emptySet()
                operationMessage.value = "Preferences reset and modified settings restored to stock"
            }
        }
    }

    fun restoreAllModifiedSettings() {
        val repo = repository ?: return
        val mods = preferences.getModifiedSettings()
        if (mods.isEmpty()) return

        viewModelScope.launch {
            isLoading.value = true
            val summary = withContext(Dispatchers.IO) {
                val batchOps = mods.map { mod ->
                    val table = mod.settingsTable
                    if (mod.stockValue == null) {
                        SettingsRepository.BatchOperation.Delete(table, mod.key)
                    } else {
                        SettingsRepository.BatchOperation.Put(table, mod.key, mod.stockValue)
                    }
                }
                repo.executeBatch(batchOps)
            }
            withContext(Dispatchers.Main) {
                if (summary.successCount > 0) {
                    preferences.clearAllModifiedSettings()
                    modifiedSettingsMap.clear()
                    tableCache.clear()
                }
                operationMessage.value = if (summary.isAllSuccess) {
                    "✓ Restored all ${summary.successCount} settings to stock"
                } else {
                    "Restored ${summary.successCount}/${summary.totalCount} settings (${summary.failureCount} failed)"
                }
                loadSettings(forceRefresh = true)
            }
        }
    }

    fun refreshSettings() {
        if (repository == null) return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            isRefreshing.value = true
            try {
                loadSettingsInternal(forceRefresh = true)
            } finally {
                delay(250)
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
        loadSettings(forceRefresh = false)
    }

    fun loadSettings(forceRefresh: Boolean = false) {
        if (repository == null) return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            isLoading.value = true
            try {
                loadSettingsInternal(forceRefresh = forceRefresh)
            } finally {
                isLoading.value = false
            }
        }
    }

    private suspend fun loadSettingsInternal(forceRefresh: Boolean) {
        val repo = repository ?: return
        try {
            val modified = withContext(Dispatchers.IO) { preferences.getModifiedSettings() }
            withContext(Dispatchers.Main) {
                modifiedSettingsMap.clear()
                modified.forEach { modifiedSettingsMap[it.uniqueId] = it }
                favoriteIds.value = preferences.getFavoriteIds()
            }

            if (currentTab.value == ExtendedTab.MODIFIED) {
                // Group by table to avoid spawning N shell processes for N modified items
                val liveEntries = withContext(Dispatchers.IO) {
                    val tablesNeeded = modified.filter { !it.isDeleted }.map { it.settingsTable }.distinct()
                    val tableLookup = tablesNeeded.associateWith { table ->
                        val entries = if (!forceRefresh && tableCache.containsKey(table)) {
                            tableCache[table]!!
                        } else {
                            repo.getAll(table).also { tableCache[table] = it }
                        }
                        entries.associateBy({ it.key }, { it.value })
                    }

                    modified.map { mod ->
                        val table = mod.settingsTable
                        if (mod.isDeleted) {
                            SettingsEntry(mod.key, "", table)
                        } else {
                            val liveValue = tableLookup[table]?.get(mod.key) ?: mod.value
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
                    if (!forceRefresh && tableCache.containsKey(tableToLoad)) {
                        tableCache[tableToLoad]!!
                    } else {
                        repo.getAll(tableToLoad).also { tableCache[tableToLoad] = it }
                    }
                }
                withContext(Dispatchers.Main) {
                    _allSettings.clear()
                    _allSettings.addAll(entries)
                }
            }

            applyFilter()
        } catch (ce: CancellationException) {
            throw ce
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
            delay(120)
            applyFilter()
        }
    }

    private suspend fun applyFilter() {
        val query = searchQuery.value.trim().lowercase()
        val activeFilter = filterMode.value
        val favs = favoriteIds.value
        val modsKeys = modifiedSettingsMap.keys.toSet()
        val isModifiedTab = currentTab.value == ExtendedTab.MODIFIED
        val snapshot = _allSettings.toList()

        val result = withContext(Dispatchers.Default) {
            snapshot.asSequence()
                .filter { entry ->
                    when (activeFilter) {
                        FilterMode.ALL -> true
                        FilterMode.FAVORITES_ONLY -> favs.contains(entry.uniqueId)
                        FilterMode.MODIFIED_IN_TABLE -> isModifiedTab || modsKeys.contains(entry.uniqueId)
                    }
                }
                .filter { entry ->
                    if (query.isEmpty()) {
                        true
                    } else {
                        entry.key.lowercase().contains(query) ||
                            entry.value.lowercase().contains(query) ||
                            entry.table.displayName.lowercase().contains(query)
                    }
                }
                .sortedWith(
                    compareByDescending<SettingsEntry> { favs.contains(it.uniqueId) }
                        .thenBy { if (isModifiedTab) it.table.ordinal else 0 }
                        .thenBy { it.key.lowercase() }
                )
                .toList()
        }

        withContext(Dispatchers.Main) {
            filteredSettings.clear()
            filteredSettings.addAll(result)
        }
    }

    fun putSetting(key: String, value: String, table: SettingsTable = currentTable) {
        val repo = repository ?: return
        val trimmedKey = key.trim()
        val validation = SettingAnalyzer.validate(table, trimmedKey, value)
        if (!validation.isValid) {
            operationMessage.value = validation.errorMessage ?: "Invalid setting value"
            return
        }

        val existingMod = preferences.getModifiedSettings()
            .find { it.settingsTable == table && it.key == trimmedKey }

        viewModelScope.launch {
            val stockValue = withContext(Dispatchers.IO) {
                when {
                    existingMod != null -> existingMod.stockValue
                    tableCache[table] != null ->
                        tableCache[table]?.find { it.key == trimmedKey }?.value ?: repo.get(table, trimmedKey)
                    else -> repo.get(table, trimmedKey)
                }
            }

            val result = withContext(Dispatchers.IO) {
                repo.putWithResult(table, trimmedKey, value)
            }
            if (result.isSuccess) {
                withContext(Dispatchers.Main) {
                    val mod = ModifiedSetting(table.name, trimmedKey, value, stockValue, false)
                    preferences.addModifiedSetting(mod)
                    val refreshedMods = preferences.getModifiedSettings()
                    modifiedSettingsMap.clear()
                    refreshedMods.forEach { modifiedSettingsMap[it.uniqueId] = it }

                    // Update in-memory table cache immediately
                    tableCache[table]?.let { cached ->
                        val updated = cached.filterNot { it.key == trimmedKey } +
                            SettingsEntry(trimmedKey, value, table)
                        tableCache[table] = updated.sortedBy { it.key.lowercase() }
                    }

                    operationMessage.value = "✓ Saved ${table.displayName}: $trimmedKey"
                    loadSettings(forceRefresh = false)
                }
            } else {
                withContext(Dispatchers.Main) {
                    operationMessage.value = "✗ Write failed: ${result.errorMessage ?: "System rejected change"}"
                }
            }
        }
    }

    fun deleteSetting(key: String, table: SettingsTable = currentTable) {
        val repo = repository ?: return
        val existingMod = preferences.getModifiedSettings()
            .find { it.settingsTable == table && it.key == key }
        val stockValue = existingMod?.stockValue
            ?: tableCache[table]?.find { it.key == key }?.value
            ?: _allSettings.find { it.table == table && it.key == key }?.value

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                repo.deleteWithResult(table, key)
            }
            if (result.isSuccess) {
                withContext(Dispatchers.Main) {
                    val mod = ModifiedSetting(table.name, key, "", stockValue, true)
                    preferences.addModifiedSetting(mod)
                    modifiedSettingsMap[mod.uniqueId] = mod

                    tableCache[table]?.let { cached ->
                        tableCache[table] = cached.filterNot { it.key == key }
                    }

                    operationMessage.value = "✓ Deleted ${table.displayName}: $key"
                    loadSettings(forceRefresh = false)
                }
            } else {
                withContext(Dispatchers.Main) {
                    operationMessage.value = "✗ Delete failed: ${result.errorMessage ?: "System rejected deletion"}"
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
            val result = withContext(Dispatchers.IO) {
                if (mod.stockValue == null) {
                    repo.deleteWithResult(targetTable, key)
                } else {
                    repo.putWithResult(targetTable, key, mod.stockValue)
                }
            }
            if (result.isSuccess) {
                withContext(Dispatchers.Main) {
                    preferences.removeModifiedSetting(targetTable.name, key)
                    modifiedSettingsMap.remove(mod.uniqueId)

                    tableCache[targetTable]?.let { cached ->
                        val withoutKey = cached.filterNot { it.key == key }
                        tableCache[targetTable] = if (mod.stockValue == null) {
                            withoutKey
                        } else {
                            (withoutKey + SettingsEntry(key, mod.stockValue, targetTable))
                                .sortedBy { it.key.lowercase() }
                        }
                    }

                    operationMessage.value = "✓ Restored $key to stock"
                    loadSettings(forceRefresh = false)
                }
            } else {
                withContext(Dispatchers.Main) {
                    operationMessage.value = "✗ Restore failed: ${result.errorMessage ?: "Unknown error"}"
                }
            }
        }
    }

    fun addSetting(key: String, value: String, table: SettingsTable = currentTable) {
        putSetting(key, value, table)
    }

    fun exportSnapshotJson(): String {
        return preferences.exportSnapshotJson()
    }

    fun exportShellScript(): String {
        return SettingsRepository.buildShellScript(preferences.getModifiedSettings())
    }

    fun importSnapshotJson(rawJson: String, onComplete: (Boolean) -> Unit = {}) {
        val repo = repository
        if (repo == null) {
            operationMessage.value = "✗ Grant Root or Shizuku access before importing a snapshot"
            onComplete(false)
            return
        }

        viewModelScope.launch {
            isLoading.value = true
            try {
                val payload = withContext(Dispatchers.Default) {
                    preferences.parseSnapshotJson(rawJson)
                }
                if (payload.modifiedSettings.isEmpty() && payload.favorites.isEmpty()) {
                    operationMessage.value = "Snapshot contains no modified settings or favorites"
                    onComplete(false)
                    return@launch
                }

                val summary = withContext(Dispatchers.IO) {
                    val ops = payload.modifiedSettings.map { mod ->
                        if (mod.isDeleted) {
                            SettingsRepository.BatchOperation.Delete(mod.settingsTable, mod.key)
                        } else {
                            SettingsRepository.BatchOperation.Put(mod.settingsTable, mod.key, mod.value)
                        }
                    }
                    if (ops.isNotEmpty()) {
                        repo.executeBatch(ops)
                    } else {
                        SettingsRepository.BatchExecutionSummary(0, 0, 0)
                    }
                }

                withContext(Dispatchers.Main) {
                    if (payload.modifiedSettings.isNotEmpty()) {
                        preferences.saveModifiedSettingsBatch(payload.modifiedSettings)
                    }
                    if (payload.favorites.isNotEmpty()) {
                        val mergedFavs = preferences.getFavoriteIds() + payload.favorites
                        preferences.setFavoriteIds(mergedFavs)
                        favoriteIds.value = mergedFavs
                    }
                    tableCache.clear()
                    operationMessage.value = "✓ Imported ${summary.successCount} settings & ${payload.favorites.size} favorites"
                    onComplete(true)
                }
                loadSettingsInternal(forceRefresh = true)
            } catch (e: Exception) {
                Log.e(TAG, "Snapshot import failed", e)
                withContext(Dispatchers.Main) {
                    operationMessage.value = "✗ Invalid snapshot JSON: ${e.message}"
                    onComplete(false)
                }
            } finally {
                isLoading.value = false
            }
        }
    }

    fun clearMessage() {
        operationMessage.value = null
    }

    companion object {
        private const val TAG = "SettingsViewModel"
        private const val SHIZUKU_PERMISSION_REQUEST_CODE = 101
    }
}
