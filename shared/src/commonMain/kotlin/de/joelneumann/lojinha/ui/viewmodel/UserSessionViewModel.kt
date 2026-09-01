package de.joelneumann.lojinha.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.joelneumann.lojinha.domain.model.SystemSettings
import de.joelneumann.lojinha.domain.model.User
import de.joelneumann.lojinha.domain.repository.SettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class UserSessionViewModel(
    val user: User,
    private val settingsRepository: SettingsRepository,
    private val onLogoutRequest: () -> Unit
) : ViewModel() {

    private val _settings = MutableStateFlow(SystemSettings())
    val settings: StateFlow<SystemSettings> = _settings.asStateFlow()

    private val _inactivitySecondsRemaining = MutableStateFlow(180)
    val inactivitySecondsRemaining: StateFlow<Int> = _inactivitySecondsRemaining.asStateFlow()

    private val _showInactivityWarning = MutableStateFlow(false)
    val showInactivityWarning: StateFlow<Boolean> = _showInactivityWarning.asStateFlow()

    private var inactivityJob: Job? = null

    init {
        viewModelScope.launch {
            settingsRepository.getSettingsFlow().collect { updatedSettings ->
                _settings.value = updatedSettings
                resetInactivityTimer()
            }
        }
    }

    fun onUserInteracted() {
        resetInactivityTimer()
    }

    fun resetInactivityTimer() {
        _showInactivityWarning.value = false
        val timeoutSecs = _settings.value.inactivityTimeoutMinutes * 60
        _inactivitySecondsRemaining.value = timeoutSecs

        inactivityJob?.cancel()
        inactivityJob = viewModelScope.launch {
            while (_inactivitySecondsRemaining.value > 0) {
                delay(1000)
                _inactivitySecondsRemaining.value -= 1
                if (_inactivitySecondsRemaining.value <= 60) {
                    _showInactivityWarning.value = true
                }
            }
            requestLogout()
        }
    }

    fun requestLogout() {
        stopInactivityTimer()
        onLogoutRequest()
    }

    private fun stopInactivityTimer() {
        inactivityJob?.cancel()
        inactivityJob = null
        _showInactivityWarning.value = false
    }

    override fun onCleared() {
        super.onCleared()
        stopInactivityTimer()
    }
}
