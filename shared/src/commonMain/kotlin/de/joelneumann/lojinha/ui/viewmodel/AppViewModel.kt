package de.joelneumann.lojinha.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.joelneumann.lojinha.domain.model.Language
import de.joelneumann.lojinha.domain.model.SystemSettings
import de.joelneumann.lojinha.domain.model.User
import de.joelneumann.lojinha.domain.repository.SettingsRepository
import de.joelneumann.lojinha.domain.repository.UserRepository
import de.joelneumann.lojinha.ui.i18n.LanguageManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AppScreen {
    MAIN_USER_SELECT,
    SHOPPING,
    TRANSACTION_HISTORY,
    ADMIN_PANEL
}

class AppViewModel(
    private val userRepository: UserRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _currentScreen = MutableStateFlow(AppScreen.MAIN_USER_SELECT)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

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
            }
        }
    }

    fun setLanguage(language: Language) {
        LanguageManager.setLanguage(language)
        val user = _currentUser.value
        if (user != null && user.language != language) {
            val updated = user.copy(language = language)
            _currentUser.value = updated
            viewModelScope.launch {
                userRepository.saveUser(updated)
            }
        }
    }

    fun loginUser(user: User) {
        _currentUser.value = user
        LanguageManager.setLanguage(user.language)
        _currentScreen.value = AppScreen.SHOPPING
        resetInactivityTimer()
    }

    fun logout() {
        _currentUser.value = null
        LanguageManager.resetToDefault()
        _currentScreen.value = AppScreen.MAIN_USER_SELECT
        stopInactivityTimer()
        _showInactivityWarning.value = false
    }

    fun navigateTo(screen: AppScreen) {
        if (screen == AppScreen.MAIN_USER_SELECT) {
            logout()
            return
        }
        _currentScreen.value = screen
        if (screen == AppScreen.SHOPPING || screen == AppScreen.TRANSACTION_HISTORY) {
            resetInactivityTimer()
        } else {
            stopInactivityTimer()
        }
    }

    fun updateCurrentUser(user: User) {
        _currentUser.value = user
        LanguageManager.setLanguage(user.language)
        viewModelScope.launch {
            userRepository.saveUser(user)
        }
    }

    fun refreshCurrentUser() {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            val updated = userRepository.getUserById(user.id)
            if (updated != null) {
                _currentUser.value = updated
                LanguageManager.setLanguage(updated.language)
            }
        }
    }

    fun onUserInteracted() {
        if (_currentUser.value != null && (_currentScreen.value == AppScreen.SHOPPING || _currentScreen.value == AppScreen.TRANSACTION_HISTORY)) {
            resetInactivityTimer()
        }
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
            // Auto logout on timeout
            logout()
        }
    }

    private fun stopInactivityTimer() {
        inactivityJob?.cancel()
        inactivityJob = null
        _showInactivityWarning.value = false
    }
}
