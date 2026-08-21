package de.joelneumann.lojinha.ui.viewmodel.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.joelneumann.lojinha.domain.model.SystemSettings
import de.joelneumann.lojinha.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AdminSettingsViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _settings = MutableStateFlow(SystemSettings())
    val settings: StateFlow<SystemSettings> = _settings.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            settingsRepository.getSettingsFlow().collect { _settings.value = it }
        }
    }

    fun updateSystemSettings(newSettings: SystemSettings) {
        viewModelScope.launch {
            settingsRepository.updateSettings(newSettings)
        }
    }
}
