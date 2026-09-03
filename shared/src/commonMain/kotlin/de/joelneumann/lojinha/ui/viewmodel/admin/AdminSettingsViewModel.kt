package de.joelneumann.lojinha.ui.viewmodel.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.joelneumann.lojinha.data.service.OneDriveBackupService
import de.joelneumann.lojinha.domain.model.DeviceCodeResponse
import de.joelneumann.lojinha.domain.model.BackupFileInfo
import de.joelneumann.lojinha.domain.model.BackupRoutine
import de.joelneumann.lojinha.domain.model.CsvImportResult
import de.joelneumann.lojinha.domain.model.SystemSettings
import de.joelneumann.lojinha.domain.repository.BackupRepository
import de.joelneumann.lojinha.domain.repository.SettingsRepository
import de.joelneumann.lojinha.ui.utils.PlatformFile
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AdminSettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val backupRepository: BackupRepository? = null,
    private val onRunRoutineNow: (suspend (BackupRoutine) -> Unit)? = null,
    private val oneDriveBackupService: OneDriveBackupService? = null,
    private val onPreviewCsvImport: (suspend (PlatformFile, String) -> CsvImportResult)? = null,
    private val onExecuteCsvImport: (suspend (PlatformFile, String) -> CsvImportResult)? = null,
    private val onExecuteDbRestore: (suspend (PlatformFile) -> Unit)? = null,
    private val onExecuteWipeData: (suspend () -> Unit)? = null
) : ViewModel() {

    private val _settings = MutableStateFlow(SystemSettings())
    val settings: StateFlow<SystemSettings> = _settings.asStateFlow()

    private val _showOneDriveAuthDialog = MutableStateFlow(false)
    val showOneDriveAuthDialog: StateFlow<Boolean> = _showOneDriveAuthDialog.asStateFlow()

    private val _oneDriveDeviceCodeResponse = MutableStateFlow<DeviceCodeResponse?>(null)
    val oneDriveDeviceCodeResponse: StateFlow<DeviceCodeResponse?> = _oneDriveDeviceCodeResponse.asStateFlow()

    private val _oneDriveAuthStatus = MutableStateFlow("Waiting for authorization...")
    val oneDriveAuthStatus: StateFlow<String> = _oneDriveAuthStatus.asStateFlow()

    private var oneDriveAuthJob: Job? = null

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

    private val _showOneDriveDisconnectDialog = MutableStateFlow(false)
    val showOneDriveDisconnectDialog: StateFlow<Boolean> = _showOneDriveDisconnectDialog.asStateFlow()

    private val _showOneDriveSuccessDialog = MutableStateFlow<String?>(null)
    val showOneDriveSuccessDialog: StateFlow<String?> = _showOneDriveSuccessDialog.asStateFlow()

    init {
        loadSettings()
        loadRoutines()
    }

    fun loadSettings() {
        viewModelScope.launch {
            settingsRepository.getSettingsFlow().collect {
                _settings.value = it
            }
        }
    }

    fun loadRoutines() {
        val repo = backupRepository ?: return
        viewModelScope.launch {
            repo.getBackupsFlow().collect {
                _routines.value = it
            }
        }
    }

    fun updateSystemSettings(newSettings: SystemSettings) {
        viewModelScope.launch {
            settingsRepository.updateSettings(newSettings)
            _statusMessage.value = "System settings updated successfully."
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
        viewModelScope.launch {
            if (backupRepository != null) {
                backupRepository.saveBackupRoutine(routine)
            } else {
                val currentList = _routines.value.toMutableList()
                val index = currentList.indexOfFirst { it.id == routine.id }
                if (index >= 0) {
                    currentList[index] = routine
                } else {
                    currentList.add(routine)
                }
                _routines.value = currentList
            }
            closeRoutineDialog()
            _statusMessage.value = "Backup routine saved: '${routine.name}'"
        }
    }

    fun requestDeleteRoutine(routine: BackupRoutine) {
        _routineToDelete.value = routine
    }

    fun confirmDeleteRoutine() {
        val target = _routineToDelete.value ?: return
        viewModelScope.launch {
            if (backupRepository != null) {
                backupRepository.deleteBackupRoutine(target.id)
            } else {
                _routines.value = _routines.value.filter { it.id != target.id }
            }
            _routineToDelete.value = null
            _statusMessage.value = "Backup routine '${target.name}' removed."
        }
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
        viewModelScope.launch {
            val updated = routine.copy(isEnabled = targetState)
            if (backupRepository != null) {
                backupRepository.saveBackupRoutine(updated)
            } else {
                _routines.value = _routines.value.map {
                    if (it.id == routine.id) updated else it
                }
            }
            _routineToToggle.value = null
            _statusMessage.value = "Backup routine '${routine.name}' ${if (targetState) "enabled" else "disabled"}."
        }
    }

    fun cancelToggleRoutine() {
        _routineToToggle.value = null
    }

    fun runRoutineNow(routine: BackupRoutine) {
        viewModelScope.launch {
            try {
                onRunRoutineNow?.invoke(routine)
                _statusMessage.value = "Triggered routine '${routine.name}'."
            } catch (e: Exception) {
                _errorMessage.value = "Failed to run routine '${routine.name}': ${e.message}"
            }
        }
    }

    fun setRestoreDbFile(file: PlatformFile?) {
        _activeRestoreDbFile.value = file
    }

    fun setShowWipeDataDialog(show: Boolean) {
        _showWipeDataDialog.value = show
    }

    fun prepareCsvImport(file: PlatformFile, type: String) {
        _csvImportType.value = type
        viewModelScope.launch {
            try {
                val previewResult = if (onPreviewCsvImport != null) {
                    onPreviewCsvImport.invoke(file, type)
                } else {
                    CsvImportResult(
                        totalProcessed = 0,
                        addedCount = 0,
                        updatedCount = 0,
                        strippedBarcodesCount = 0,
                        errors = listOf("CSV import preview not supported."),
                        warnings = emptyList()
                    )
                }
                _csvImportPreview.value = file to previewResult
            } catch (e: Exception) {
                _errorMessage.value = "Failed to preview CSV: ${e.message}"
            }
        }
    }

    fun executeCsvImport() {
        val preview = _csvImportPreview.value ?: return
        val file = preview.first
        val type = _csvImportType.value
        _csvImportPreview.value = null
        viewModelScope.launch {
            try {
                if (onExecuteCsvImport != null) {
                    val result = onExecuteCsvImport.invoke(file, type)
                    if (result.errors.isNotEmpty()) {
                        _errorMessage.value = "Import finished with errors: ${result.errors.joinToString(", ")}"
                    } else {
                        _statusMessage.value = "Successfully imported ${result.totalProcessed} $type (${result.addedCount} added, ${result.updatedCount} updated)."
                    }
                } else {
                    _statusMessage.value = "Import executed for ${file.name}."
                }
            } catch (e: Exception) {
                _errorMessage.value = "Import failed: ${e.message}"
            }
        }
    }

    fun executeDbRestore(file: PlatformFile) {
        _activeRestoreDbFile.value = null
        viewModelScope.launch {
            try {
                if (onExecuteDbRestore != null) {
                    onExecuteDbRestore.invoke(file)
                    _statusMessage.value = "Database restored successfully from ${file.name}."
                } else {
                    _statusMessage.value = "Database restore requested for ${file.name}."
                }
            } catch (e: Exception) {
                _errorMessage.value = "Restore failed: ${e.message}"
            }
        }
    }

    fun executeWipeData() {
        _showWipeDataDialog.value = false
        viewModelScope.launch {
            try {
                if (onExecuteWipeData != null) {
                    onExecuteWipeData.invoke()
                    _statusMessage.value = "Factory Reset completed."
                } else {
                    _statusMessage.value = "Factory Reset completed."
                }
            } catch (e: Exception) {
                _errorMessage.value = "Factory Reset failed: ${e.message}"
            }
        }
    }

    fun clearCsvImportPreview() {
        _csvImportPreview.value = null
    }

    fun startOneDriveAuth() {
        val service = oneDriveBackupService ?: run {
            _errorMessage.value = "OneDrive backup service is unavailable."
            return
        }
        val clientId = _settings.value.oneDriveClientId.ifBlank { "202e1c94-b152-4751-b0e6-a2a4b8eb4901" }

        _showOneDriveAuthDialog.value = true
        _oneDriveAuthStatus.value = "Initializing browser login..."

        oneDriveAuthJob?.cancel()
        oneDriveAuthJob = viewModelScope.launch {
            val tokenRes = service.startPkceAuth(
                clientId = clientId,
                onStatusUpdate = { status -> _oneDriveAuthStatus.value = status }
            )

            val tokenData = tokenRes.getOrNull()
            if (tokenData?.refreshToken != null && tokenData.accessToken != null) {
                val profileRes = service.fetchUserProfile(tokenData.accessToken)
                val profile = profileRes.getOrNull()
                val email = profile?.mail ?: profile?.userPrincipalName
                val name = profile?.displayName

                val updatedSettings = _settings.value.copy(
                    oneDriveRefreshToken = tokenData.refreshToken,
                    oneDriveAccountEmail = email,
                    oneDriveAccountName = name
                )
                settingsRepository.updateSettings(updatedSettings)
                _settings.value = updatedSettings
                _showOneDriveAuthDialog.value = false
                _showOneDriveSuccessDialog.value = email ?: "User"
                _statusMessage.value = "✓ Connected to OneDrive as ${email ?: "User"}."
            } else {
                val err = tokenRes.exceptionOrNull()?.message ?: "Authorization failed."
                _oneDriveAuthStatus.value = err
                _errorMessage.value = err
            }
        }
    }

    fun cancelOneDriveAuth() {
        oneDriveAuthJob?.cancel()
        oneDriveAuthJob = null
        _showOneDriveAuthDialog.value = false
        _oneDriveDeviceCodeResponse.value = null
    }

    fun requestDisconnectOneDrive() {
        _showOneDriveDisconnectDialog.value = true
    }

    fun cancelDisconnectOneDrive() {
        _showOneDriveDisconnectDialog.value = false
    }

    fun confirmDisconnectOneDrive() {
        _showOneDriveDisconnectDialog.value = false
        disconnectOneDrive()
    }

    fun dismissOneDriveSuccessDialog() {
        _showOneDriveSuccessDialog.value = null
    }

    fun disconnectOneDrive() {
        viewModelScope.launch {
            val updatedSettings = _settings.value.copy(
                oneDriveRefreshToken = null,
                oneDriveAccountEmail = null,
                oneDriveAccountName = null
            )
            settingsRepository.updateSettings(updatedSettings)
            _settings.value = updatedSettings
            _statusMessage.value = "Disconnected OneDrive account."
        }
    }

    fun updateOneDriveClientId(clientId: String) {
        viewModelScope.launch {
            val updatedSettings = _settings.value.copy(oneDriveClientId = clientId)
            settingsRepository.updateSettings(updatedSettings)
            _settings.value = updatedSettings
        }
    }

    fun updateOneDriveTenant(tenant: String) {
        viewModelScope.launch {
            val updatedSettings = _settings.value.copy(oneDriveTenant = tenant)
            settingsRepository.updateSettings(updatedSettings)
            _settings.value = updatedSettings
        }
    }

    override fun onCleared() {
        super.onCleared()
        _statusMessage.value = null
        _showRoutineDialog.value = false
    }
}
