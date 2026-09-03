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

    private var resetLanguageJob: Job? = null

    fun loginUser(user: User) {
        resetLanguageJob?.cancel()
        viewModelScope.launch {
            _currentUser.value = user
            LanguageManager.setLanguage(user.language)
            _currentScreen.value = AppScreen.SHOPPING
        }
    }

    fun logout() {
        resetLanguageJob?.cancel()
        viewModelScope.launch {
            _currentUser.value = null
            _currentScreen.value = AppScreen.MAIN_USER_SELECT
            resetLanguageJob = viewModelScope.launch {
                delay(300)
                LanguageManager.resetToDefault()
            }
        }
    }

    fun navigateTo(screen: AppScreen) {
        viewModelScope.launch {
            if (screen == AppScreen.MAIN_USER_SELECT) {
                logout()
                return@launch
            }
            _currentScreen.value = screen
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
}
