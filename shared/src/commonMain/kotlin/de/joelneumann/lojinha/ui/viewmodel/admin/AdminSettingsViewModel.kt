package de.joelneumann.lojinha.ui.viewmodel.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.joelneumann.lojinha.data.service.BackupFileInfo
import de.joelneumann.lojinha.data.service.BackupRestoreService
import de.joelneumann.lojinha.data.service.CsvImportResult
import de.joelneumann.lojinha.domain.model.BackupRoutine
import de.joelneumann.lojinha.domain.model.SystemSettings
import de.joelneumann.lojinha.domain.repository.BackupRepository
import de.joelneumann.lojinha.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class AdminSettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val backupRestoreService: BackupRestoreService? = null,
    private val backupRepository: BackupRepository? = null
) : ViewModel() {

    private val _settings = MutableStateFlow(SystemSettings())
    val settings: StateFlow<SystemSettings> = _settings.asStateFlow()

    private val _routines = MutableStateFlow<List<BackupRoutine>>(emptyList())
    val routines: StateFlow<List<BackupRoutine>> = _routines.asStateFlow()

    private val _editingRoutine = MutableStateFlow<BackupRoutine?>(null)
    val editingRoutine: StateFlow<BackupRoutine?> = _editingRoutine.asStateFlow()

    private val _showRoutineDialog = MutableStateFlow(false)
    val showRoutineDialog: StateFlow<Boolean> = _showRoutineDialog.asStateFlow()

    private val _routineToDelete = MutableStateFlow<BackupRoutine?>(null)
    val routineToDelete: StateFlow<BackupRoutine?> = _routineToDelete.asStateFlow()

    private val _routineToToggle = MutableStateFlow<Pair<BackupRoutine, Boolean>?>(null)
    val routineToToggle: StateFlow<Pair<BackupRoutine, Boolean>?> = _routineToToggle.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _activeRestoreDbFile = MutableStateFlow<File?>(null)
    val activeRestoreDbFile: StateFlow<File?> = _activeRestoreDbFile.asStateFlow()

    private val _showWipeDataDialog = MutableStateFlow(false)
    val showWipeDataDialog: StateFlow<Boolean> = _showWipeDataDialog.asStateFlow()

    private val _csvImportPreview = MutableStateFlow<Pair<File, CsvImportResult>?>(null)
    val csvImportPreview: StateFlow<Pair<File, CsvImportResult>?> = _csvImportPreview.asStateFlow()

    private val _csvImportType = MutableStateFlow("Products") // "Products" or "Users"
    val csvImportType: StateFlow<String> = _csvImportType.asStateFlow()

    private val _detectedBackups = MutableStateFlow<List<BackupFileInfo>>(emptyList())
    val detectedBackups: StateFlow<List<BackupFileInfo>> = _detectedBackups.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            settingsRepository.getSettingsFlow().collect {
                _settings.value = it
                refreshDetectedBackups(it.backupLocationPath)
            }
        }
        viewModelScope.launch {
            backupRepository?.getBackupsFlow()?.collect {
                _routines.value = it
            }
        }
    }

    fun openCreateRoutineDialog() {
        _editingRoutine.value = null
        _showRoutineDialog.value = true
    }

    fun openEditRoutineDialog(routine: BackupRoutine) {
        _editingRoutine.value = routine
        _showRoutineDialog.value = true
    }

    fun closeRoutineDialog() {
        _showRoutineDialog.value = false
        _editingRoutine.value = null
    }

    fun saveBackupRoutine(routine: BackupRoutine) {
        viewModelScope.launch {
            try {
                if (backupRepository != null) {
                    backupRepository.saveBackupRoutine(routine)
                    _showRoutineDialog.value = false
                    _editingRoutine.value = null
                    _statusMessage.value = "✓ Backup routine '${routine.name}' saved successfully."
                    refreshDetectedBackups(routine.backupLocationPath)
                }
            } catch (e: Exception) {
                _errorMessage.value = "Error saving backup routine: ${e.message}"
            }
        }
    }

    fun requestDeleteRoutine(routine: BackupRoutine) {
        _routineToDelete.value = routine
    }

    fun cancelDeleteRoutine() {
        _routineToDelete.value = null
    }

    fun confirmDeleteRoutine() {
        val routine = _routineToDelete.value ?: return
        viewModelScope.launch {
            try {
                if (backupRepository != null) {
                    backupRepository.deleteBackupRoutine(routine.id)
                    _routineToDelete.value = null
                    _statusMessage.value = "✓ Deleted backup routine '${routine.name}'."
                }
            } catch (e: Exception) {
                _errorMessage.value = "Error deleting routine: ${e.message}"
            }
        }
    }

    fun requestToggleRoutine(routine: BackupRoutine, targetState: Boolean) {
        _routineToToggle.value = routine to targetState
    }

    fun cancelToggleRoutine() {
        _routineToToggle.value = null
    }

    fun confirmToggleRoutine() {
        val pair = _routineToToggle.value ?: return
        val routine = pair.first
        val enabled = pair.second

        viewModelScope.launch {
            try {
                if (backupRepository != null) {
                    val updated = routine.copy(isEnabled = enabled)
                    backupRepository.saveBackupRoutine(updated)
                    _routineToToggle.value = null
                    val stateAction = if (enabled) "activated" else "deactivated"
                    _statusMessage.value = "✓ Routine '${routine.name}' $stateAction."
                }
            } catch (e: Exception) {
                _errorMessage.value = "Error updating routine state: ${e.message}"
            }
        }
    }

    fun runRoutineNow(routine: BackupRoutine) {
        viewModelScope.launch {
            try {
                if (backupRestoreService == null || backupRepository == null) {
                    _errorMessage.value = "Backup service is unavailable."
                    return@launch
                }
                val resultFile = backupRestoreService.executeRoutineBackup(routine)
                val updated = routine.copy(lastBackupTimestamp = System.currentTimeMillis())
                backupRepository.saveBackupRoutine(updated)
                _statusMessage.value = "✓ Backup routine '${routine.name}' executed successfully: ${resultFile.name}"
                refreshDetectedBackups(routine.backupLocationPath)
            } catch (e: Exception) {
                _errorMessage.value = "Error executing routine '${routine.name}': ${e.message}"
            }
        }
    }

    fun refreshDetectedBackups(locationPath: String? = null) {
        val path = locationPath ?: _settings.value.backupLocationPath
        viewModelScope.launch {
            if (backupRestoreService != null && path.isNotBlank()) {
                _detectedBackups.value = backupRestoreService.listBackupsInDirectory(path)
            } else {
                _detectedBackups.value = emptyList()
            }
        }
    }

    fun deleteBackupFile(file: File) {
        viewModelScope.launch {
            try {
                if (backupRestoreService != null) {
                    backupRestoreService.deleteBackup(file)
                    _statusMessage.value = "✓ Deleted backup file: ${file.name}"
                    refreshDetectedBackups()
                }
            } catch (e: Exception) {
                _errorMessage.value = "Error deleting backup: ${e.message}"
            }
        }
    }

    fun updateSystemSettings(newSettings: SystemSettings) {
        viewModelScope.launch {
            settingsRepository.updateSettings(newSettings)
            _statusMessage.value = "✓ System settings updated successfully."
        }
    }

    fun clearStatusMessages() {
        _statusMessage.value = null
        _errorMessage.value = null
    }

    fun setRestoreDbFile(file: File?) {
        _activeRestoreDbFile.value = file
    }

    fun setShowWipeDataDialog(show: Boolean) {
        _showWipeDataDialog.value = show
    }

    fun clearCsvImportPreview() {
        _csvImportPreview.value = null
    }

    fun performManualDbBackup(destinationDir: File) {
        viewModelScope.launch {
            try {
                if (backupRestoreService == null) {
                    _errorMessage.value = "Backup service is unavailable."
                    return@launch
                }
                val resultFile = backupRestoreService.performDbBackup(destinationDir)
                val updated = _settings.value.copy(lastBackupTimestamp = System.currentTimeMillis())
                settingsRepository.updateSettings(updated)
                _statusMessage.value = "✓ Database backup created successfully: ${resultFile.name}"
                refreshDetectedBackups(destinationDir.absolutePath)
            } catch (e: Exception) {
                _errorMessage.value = "Error creating .db backup: ${e.message}"
            }
        }
    }

    fun performManualCsvBackup(destinationDir: File) {
        viewModelScope.launch {
            try {
                if (backupRestoreService == null) {
                    _errorMessage.value = "Backup service is unavailable."
                    return@launch
                }
                val resultFolder = backupRestoreService.performCsvBackup(destinationDir)
                val updated = _settings.value.copy(lastBackupTimestamp = System.currentTimeMillis())
                settingsRepository.updateSettings(updated)
                _statusMessage.value = "✓ CSV backup exported successfully: ${resultFolder.name}"
                refreshDetectedBackups(destinationDir.absolutePath)
            } catch (e: Exception) {
                _errorMessage.value = "Error exporting CSV backup: ${e.message}"
            }
        }
    }

    fun executeDbRestore(file: File) {
        viewModelScope.launch {
            try {
                if (backupRestoreService == null) {
                    _errorMessage.value = "Backup service is unavailable."
                    return@launch
                }
                backupRestoreService.restoreDbFromBackup(file)
                _activeRestoreDbFile.value = null
                _statusMessage.value = "✓ Database restored successfully! Reloading application data..."
                loadData()
            } catch (e: Exception) {
                _errorMessage.value = "Error restoring database: ${e.message}"
            }
        }
    }

    fun executeWipeData() {
        viewModelScope.launch {
            try {
                if (backupRestoreService == null) {
                    _errorMessage.value = "Backup service is unavailable."
                    return@launch
                }
                backupRestoreService.wipeAllData()
                _showWipeDataDialog.value = false
                _statusMessage.value = "✓ Factory Reset completed: All database data wiped clean."
                loadData()
            } catch (e: Exception) {
                _errorMessage.value = "Error wiping database: ${e.message}"
            }
        }
    }

    fun prepareCsvImport(file: File, type: String) {
        viewModelScope.launch {
            try {
                if (backupRestoreService == null) {
                    _errorMessage.value = "Backup service is unavailable."
                    return@launch
                }
                _csvImportType.value = type
                val result = if (type == "Products") {
                    backupRestoreService.importProductsFromCsv(file)
                } else {
                    backupRestoreService.importUsersFromCsv(file)
                }
                if (result.errors.isNotEmpty()) {
                    _errorMessage.value = "CSV Parse Error: ${result.errors.joinToString(", ")}"
                } else {
                    _csvImportPreview.value = file to result
                }
            } catch (e: Exception) {
                _errorMessage.value = "Error preparing CSV import: ${e.message}"
            }
        }
    }

    fun executeCsvImport() {
        val preview = _csvImportPreview.value ?: return
        val file = preview.first
        val type = _csvImportType.value

        viewModelScope.launch {
            try {
                if (backupRestoreService == null) {
                    _errorMessage.value = "Backup service is unavailable."
                    return@launch
                }
                val result = if (type == "Products") {
                    backupRestoreService.importProductsFromCsv(file)
                } else {
                    backupRestoreService.importUsersFromCsv(file)
                }
                _csvImportPreview.value = null
                _statusMessage.value = "✓ $type CSV import completed: ${result.addedCount} added, ${result.updatedCount} updated."
            } catch (e: Exception) {
                _errorMessage.value = "Error executing CSV import: ${e.message}"
            }
        }
    }
}
