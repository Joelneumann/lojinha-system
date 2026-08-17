package de.joelneumann.lojinha.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.joelneumann.lojinha.domain.model.User
import de.joelneumann.lojinha.domain.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class UserSelectionViewModel(
    private val userRepository: UserRepository
) : ViewModel() {

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

    init {
        loadUsers()
    }

    fun loadUsers() {
        viewModelScope.launch {
            userRepository.getUsersFlow().collect { list ->
                _users.value = list.filter { it.isActive }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun onUserBarcodeScanned(barcode: String, onLoginSuccess: (User) -> Unit) {
        viewModelScope.launch {
            val user = userRepository.getUserByBarcode(barcode.trim())
            if (user != null && user.isActive) {
                if (user.pin.isNull_or_blank()) {
                    onLoginSuccess(user)
                } else {
                    _selectedUserForPin.value = user
                    _pinInput.value = ""
                    _pinError.value = null
                }
            }
        }
    }

    fun onUserCardClicked(user: User, onLoginSuccess: (User) -> Unit) {
        if (user.pin.isNull_or_blank()) {
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

    fun submitPin(onLoginSuccess: (User) -> Unit) {
        val user = _selectedUserForPin.value ?: return
        if (user.pin == _pinInput.value) {
            _selectedUserForPin.value = null
            _pinInput.value = ""
            onLoginSuccess(user)
        } else {
            _pinError.value = "pin_incorrect"
        }
    }

    fun cancelPinDialog() {
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
        if (_adminPasswordInput.value == expectedPassword) {
            closeAdminAuthDialog()
            onAdminAuthSuccess()
        } else {
            _adminPasswordError.value = "admin_password_incorrect"
        }
    }

    private fun String?.isNull_or_blank(): Boolean = this == null || this.trim().isEmpty()
}
