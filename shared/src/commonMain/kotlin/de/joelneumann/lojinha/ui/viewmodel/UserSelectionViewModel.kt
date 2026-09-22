package de.joelneumann.lojinha.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.joelneumann.lojinha.domain.model.User
import de.joelneumann.lojinha.domain.repository.UserRepository
import de.joelneumann.lojinha.ui.utils.containsIgnoreAccents
import de.joelneumann.lojinha.ui.utils.removeAccents
import de.joelneumann.lojinha.ui.utils.sortedByAccentInsensitive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class UserSelectionViewModel(
    private val userRepository: UserRepository,
    coroutineScope: CoroutineScope? = null
) : ViewModel() {

    private val activeScope = coroutineScope ?: viewModelScope

    private val _users = MutableStateFlow<List<User>>(emptyList())
    val users: StateFlow<List<User>> = _users.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedUserForPin = MutableStateFlow<User?>(null)
    val selectedUserForPin: StateFlow<User?> = _selectedUserForPin.asStateFlow()

    private val _pinInput = MutableStateFlow("")
    val pinInput: StateFlow<String> = _pinInput.asStateFlow()

    private val _pinError = MutableStateFlow<String?>(null)
    val pinError: StateFlow<String?> = _pinError.asStateFlow()

    private val _showAdminAuthDialog = MutableStateFlow(false)
    val showAdminAuthDialog: StateFlow<Boolean> = _showAdminAuthDialog.asStateFlow()

    private val _adminPasswordInput = MutableStateFlow("")
    val adminPasswordInput: StateFlow<String> = _adminPasswordInput.asStateFlow()

    private val _adminPasswordError = MutableStateFlow<String?>(null)
    val adminPasswordError: StateFlow<String?> = _adminPasswordError.asStateFlow()

    private var loadUsersJob: Job? = null
    private var isLoggingIn: Boolean = false

    init {
        loadUsers()
    }

    fun loadUsers() {
        loadUsersJob?.cancel()
        loadUsersJob = activeScope.launch {
            userRepository.getUsersFlow().collect { list ->
                _users.value = list.filter { it.isActive && !it.isDeleted }.sortedByAccentInsensitive { it.name }
            }
        }
    }

    fun resetState() {
        isLoggingIn = false
        _searchQuery.value = ""
        _selectedUserForPin.value = null
        _pinInput.value = ""
        _pinError.value = null
        _showAdminAuthDialog.value = false
        _adminPasswordInput.value = ""
        _adminPasswordError.value = null
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun onSearchSubmitted(onLoginSuccess: (User) -> Unit) {
        val query = _searchQuery.value.trim()
        if (query.isBlank()) return

        activeScope.launch {
            // 1. Try matching user by barcode first
            val userByBarcode = userRepository.getUserByBarcode(query)
            if (userByBarcode != null && userByBarcode.isActive && !userByBarcode.isDeleted) {
                onUserCardClicked(userByBarcode, onLoginSuccess)
                _searchQuery.value = ""
                return@launch
            }

            // 2. Otherwise check filtered user list
            val filtered = _users.value.filter { it.name.containsIgnoreAccents(query) }
            val exactMatch = filtered.firstOrNull { it.name.trim().removeAccents().equals(query.removeAccents(), ignoreCase = true) }
            if (exactMatch != null) {
                onUserCardClicked(exactMatch, onLoginSuccess)
                _searchQuery.value = ""
            } else if (filtered.size == 1) {
                onUserCardClicked(filtered.first(), onLoginSuccess)
                _searchQuery.value = ""
            }
        }
    }

    fun onUserCardClicked(user: User, onLoginSuccess: (User) -> Unit) {
        if (isLoggingIn) return
        if (user.pin.isNullOrBlank()) {
            isLoggingIn = true
            _searchQuery.value = ""
            onLoginSuccess(user)
        } else {
            _selectedUserForPin.value = user
            _pinInput.value = ""
            _pinError.value = null
        }
    }

    fun updatePinInput(pin: String) {
        _pinInput.value = pin
        _pinError.value = null
    }

    fun submitPin(adminPassword: String = "", onLoginSuccess: (User) -> Unit) {
        if (isLoggingIn) return
        val user = _selectedUserForPin.value ?: return
        if (verifyPinOrAdminBypass(user.pin, _pinInput.value, adminPassword)) {
            isLoggingIn = true
            _selectedUserForPin.value = null
            _pinInput.value = ""
            _searchQuery.value = ""
            onLoginSuccess(user)
        } else {
            _pinError.value = "pin_incorrect"
        }
    }

    companion object {
        fun verifyPinOrAdminBypass(userPin: String?, inputPin: String, adminPassword: String = ""): Boolean {
            val isUserPinMatch = userPin != null && de.joelneumann.lojinha.security.PasswordHasher.verify(inputPin, userPin)
            val isAdminBypass = de.joelneumann.lojinha.security.PasswordHasher.verifyAdminBypass(inputPin, adminPassword)
            return isUserPinMatch || isAdminBypass
        }
    }

    fun cancelPinDialog() {
        isLoggingIn = false
        _selectedUserForPin.value = null
        _pinInput.value = ""
        _pinError.value = null
    }

    fun openAdminAuthDialog() {
        _showAdminAuthDialog.value = true
        _adminPasswordInput.value = ""
        _adminPasswordError.value = null
    }

    fun closeAdminAuthDialog() {
        _showAdminAuthDialog.value = false
        _adminPasswordInput.value = ""
        _adminPasswordError.value = null
    }

    fun updateAdminPassword(pass: String) {
        _adminPasswordInput.value = pass
        _adminPasswordError.value = null
    }

    fun submitAdminPassword(expectedPassword: String, onAdminAuthSuccess: () -> Unit) {
        if (de.joelneumann.lojinha.security.PasswordHasher.verifyAdminBypass(_adminPasswordInput.value, expectedPassword)) {
            closeAdminAuthDialog()
            onAdminAuthSuccess()
        } else {
            _adminPasswordError.value = "admin_password_incorrect"
        }
    }

    override fun onCleared() {
        super.onCleared()
        loadUsersJob?.cancel()
        loadUsersJob = null
        resetState()
    }
}
