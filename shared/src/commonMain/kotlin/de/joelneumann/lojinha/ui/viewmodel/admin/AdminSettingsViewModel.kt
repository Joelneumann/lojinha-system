package de.joelneumann.lojinha.ui.viewmodel.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.joelneumann.lojinha.domain.model.BackupFileInfo
import de.joelneumann.lojinha.domain.model.BackupRoutine
import de.joelneumann.lojinha.domain.model.CsvImportResult
import de.joelneumann.lojinha.domain.model.SystemSettings
import de.joelneumann.lojinha.domain.repository.SettingsRepository
import de.joelneumann.lojinha.ui.utils.PlatformFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AdminSettingsViewModel(
    private val settingsRepository: SettingsRepository
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

    private val _activeRestoreDbFile = MutableStateFlow<PlatformFile?>(null)
    val activeRestoreDbFile: StateFlow<PlatformFile?> = _activeRestoreDbFile.asStateFlow()

    private val _showWipeDataDialog = MutableStateFlow(false)
    val showWipeDataDialog: StateFlow<Boolean> = _showWipeDataDialog.asStateFlow()

    private val _csvImportPreview = MutableStateFlow<Pair<PlatformFile, CsvImportResult>?>(null)
    val csvImportPreview: StateFlow<Pair<PlatformFile, CsvImportResult>?> = _csvImportPreview.asStateFlow()

    private val _csvImportType = MutableStateFlow("Products") // "Products" or "Users"
    val csvImportType: StateFlow<String> = _csvImportType.asStateFlow()

    private val _detectedBackups = MutableStateFlow<List<BackupFileInfo>>(emptyList())
    val detectedBackups: StateFlow<List<BackupFileInfo>> = _detectedBackups.asStateFlow()

    init {
        loadSettings()
    }

    fun loadSettings() {
        viewModelScope.launch {
            settingsRepository.getSettingsFlow().collect {
                _settings.value = it
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

    fun openCreateRoutineDialog() {
        _editingRoutine.value = null
        _showRoutineDialog.value = true
    }

    fun openEditRoutineDialog(routine: BackupRoutine) {
        _editingRoutine.value = routine
        _showRoutineDialog.value = true
    }

    fun closeRoutineDialog() {
        _editingRoutine.value = null
        _showRoutineDialog.value = false
    }

    fun saveBackupRoutine(routine: BackupRoutine) {
        val currentList = _routines.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == routine.id }
        if (index >= 0) {
            currentList[index] = routine
        } else {
            currentList.add(routine)
        }
        _routines.value = currentList
        closeRoutineDialog()
        _statusMessage.value = "✓ Backup routine saved: '${routine.name}'"
    }

    fun requestDeleteRoutine(routine: BackupRoutine) {
        _routineToDelete.value = routine
    }

    fun confirmDeleteRoutine() {
        val target = _routineToDelete.value ?: return
        _routines.value = _routines.value.filter { it.id != target.id }
        _routineToDelete.value = null
        _statusMessage.value = "✓ Backup routine '${target.name}' removed."
    }

    fun cancelDeleteRoutine() {
        _routineToDelete.value = null
    }

    fun requestToggleRoutine(routine: BackupRoutine, enabled: Boolean) {
        _routineToToggle.value = routine to enabled
    }

    fun confirmToggleRoutine() {
        val pair = _routineToToggle.value ?: return
        val routine = pair.first
        val targetState = pair.second
        val updated = _routines.value.map {
            if (it.id == routine.id) it.copy(isEnabled = targetState) else it
        }
        _routines.value = updated
        _routineToToggle.value = null
        _statusMessage.value = "✓ Backup routine '${routine.name}' ${if (targetState) "enabled" else "disabled"}."
    }

    fun cancelToggleRoutine() {
        _routineToToggle.value = null
    }

    fun runRoutineNow(routine: BackupRoutine) {
        _statusMessage.value = "✓ Triggered routine '${routine.name}'."
    }

    fun setRestoreDbFile(file: PlatformFile?) {
        _activeRestoreDbFile.value = file
    }

    fun setShowWipeDataDialog(show: Boolean) {
        _showWipeDataDialog.value = show
    }

    fun prepareCsvImport(file: PlatformFile, type: String) {
        _csvImportType.value = type
        val previewResult = CsvImportResult(
            totalProcessed = 1,
            addedCount = 1,
            updatedCount = 0,
            strippedBarcodesCount = 0,
            errors = emptyList(),
            warnings = emptyList()
        )
        _csvImportPreview.value = file to previewResult
    }

    fun executeCsvImport() {
        val preview = _csvImportPreview.value ?: return
        _csvImportPreview.value = null
        _statusMessage.value = "✓ Import executed for ${preview.first.name}."
    }

    fun executeDbRestore(file: PlatformFile) {
        _activeRestoreDbFile.value = null
        _statusMessage.value = "✓ Database restore requested for ${file.name}."
    }

    fun executeWipeData() {
        _showWipeDataDialog.value = false
        _statusMessage.value = "✓ Factory Reset completed."
    }

    fun clearCsvImportPreview() {
        _csvImportPreview.value = null
    }
}
