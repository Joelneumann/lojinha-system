package de.joelneumann.lojinha.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.joelneumann.lojinha.domain.model.Product
import de.joelneumann.lojinha.domain.model.User
import de.joelneumann.lojinha.domain.repository.ProductRepository
import de.joelneumann.lojinha.domain.repository.UserRepository
import de.joelneumann.lojinha.ui.utils.containsIgnoreAccents
import de.joelneumann.lojinha.ui.utils.removeAccents
import de.joelneumann.lojinha.ui.utils.sortedByAccentInsensitive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ScannedProductInfo(
    val product: Product,
    val effectivePrice: Long
)

class UserSelectionViewModel(
    private val userRepository: UserRepository,
    private val productRepository: ProductRepository? = null,
    coroutineScope: CoroutineScope? = null,
    val isNetworkPortal: Boolean = false,
    private val onAuthenticateNetworkUser: (suspend (userId: String, pin: String, keepLoggedIn: Boolean) -> User?)? = null,
    private val onAuthenticateNetworkAdmin: (suspend (password: String) -> Boolean)? = null
) : ViewModel() {

    constructor(
        userRepository: UserRepository,
        coroutineScope: CoroutineScope? = null
    ) : this(userRepository, null, coroutineScope, false, null, null)

    private val activeScope = coroutineScope ?: viewModelScope

    private val _users = MutableStateFlow<List<User>>(emptyList())
    val users: StateFlow<List<User>> = _users.asStateFlow()

    private val _isLoadingUsers = MutableStateFlow(true)
    val isLoadingUsers: StateFlow<Boolean> = _isLoadingUsers.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _scannedProduct = MutableStateFlow<ScannedProductInfo?>(null)
    val scannedProduct: StateFlow<ScannedProductInfo?> = _scannedProduct.asStateFlow()

    private val _notFoundNotice = MutableStateFlow(false)
    val notFoundNotice: StateFlow<Boolean> = _notFoundNotice.asStateFlow()

    private val _selectedUserForPin = MutableStateFlow<User?>(null)
    val selectedUserForPin: StateFlow<User?> = _selectedUserForPin.asStateFlow()

    private val _pinRequiredNoticeUser = MutableStateFlow<User?>(null)
    val pinRequiredNoticeUser: StateFlow<User?> = _pinRequiredNoticeUser.asStateFlow()

    private val _pinInput = MutableStateFlow("")
    val pinInput: StateFlow<String> = _pinInput.asStateFlow()

    val showKeepLoggedInOption: Boolean get() = isNetworkPortal

    private val _keepLoggedIn = MutableStateFlow(true)
    val keepLoggedIn: StateFlow<Boolean> = _keepLoggedIn.asStateFlow()

    private val _pinError = MutableStateFlow<String?>(null)
    val pinError: StateFlow<String?> = _pinError.asStateFlow()

    private val _showAdminAuthDialog = MutableStateFlow(false)
    val showAdminAuthDialog: StateFlow<Boolean> = _showAdminAuthDialog.asStateFlow()

    private val _adminPasswordInput = MutableStateFlow("")
    val adminPasswordInput: StateFlow<String> = _adminPasswordInput.asStateFlow()

    private val _adminPasswordError = MutableStateFlow<String?>(null)
    val adminPasswordError: StateFlow<String?> = _adminPasswordError.asStateFlow()

    private var loadUsersJob: Job? = null
    private var searchSubmitJob: Job? = null
    private var feedbackJob: Job? = null
    private var isLoggingIn: Boolean = false

    init {
        loadUsers()
    }

    fun loadUsers() {
        loadUsersJob?.cancel()
        _isLoadingUsers.value = true
        loadUsersJob = activeScope.launch {
            try {
                userRepository.getUsersFlow().collect { list ->
                    _users.value = list.filter { it.isActive && !it.isDeleted }.sortedByAccentInsensitive { it.name }
                    _isLoadingUsers.value = false
                }
            } finally {
                _isLoadingUsers.value = false
            }
        }
    }

    fun resetState() {
        isLoggingIn = false
        searchSubmitJob?.cancel()
        searchSubmitJob = null
        dismissFeedback()
        _searchQuery.value = ""
        _selectedUserForPin.value = null
        _pinRequiredNoticeUser.value = null
        _pinInput.value = ""
        _pinError.value = null
        _showAdminAuthDialog.value = false
        _adminPasswordInput.value = ""
        _adminPasswordError.value = null
    }

    fun dismissPinRequiredNotice() {
        _pinRequiredNoticeUser.value = null
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        if (_scannedProduct.value != null || _notFoundNotice.value) {
            dismissFeedback()
        }
    }

    fun showScannedProduct(product: Product, effectivePrice: Long) {
        feedbackJob?.cancel()
        _notFoundNotice.value = false
        _scannedProduct.value = ScannedProductInfo(product, effectivePrice)
        feedbackJob = activeScope.launch {
            delay(4000)
            _scannedProduct.value = null
        }
    }

    fun showNotFoundNotice() {
        feedbackJob?.cancel()
        _scannedProduct.value = null
        _notFoundNotice.value = true
        feedbackJob = activeScope.launch {
            delay(3500)
            _notFoundNotice.value = false
        }
    }

    fun dismissFeedback() {
        feedbackJob?.cancel()
        feedbackJob = null
        _scannedProduct.value = null
        _notFoundNotice.value = false
    }

    fun onSearchSubmitted(onLoginSuccess: (User) -> Unit) {
        onSearchSubmitted(0.0, onLoginSuccess)
    }

    fun onSearchSubmitted(globalMarkup: Double, onLoginSuccess: (User) -> Unit) {
        if (isLoggingIn) return
        val query = _searchQuery.value.trim()
        if (query.isBlank()) return

        searchSubmitJob?.cancel()
        searchSubmitJob = activeScope.launch {
            // 1. Try matching user by barcode first
            val userByBarcode = userRepository.getUserByBarcode(query)
            if (userByBarcode != null && userByBarcode.isActive && !userByBarcode.isDeleted) {
                dismissFeedback()
                onUserCardClicked(userByBarcode, onLoginSuccess)
                return@launch
            }

            // 2. Otherwise check filtered user list
            val filtered = _users.value.filter { it.name.containsIgnoreAccents(query) }
            val exactMatch = filtered.firstOrNull { it.name.trim().removeAccents().equals(query.removeAccents(), ignoreCase = true) }
            if (exactMatch != null) {
                dismissFeedback()
                onUserCardClicked(exactMatch, onLoginSuccess)
                return@launch
            } else if (filtered.size == 1) {
                dismissFeedback()
                onUserCardClicked(filtered.first(), onLoginSuccess)
                return@launch
            }

            // 3. User not found -> Check product barcode
            val product = productRepository?.getProductByBarcode(query)
            if (product != null && product.isActive) {
                val effectivePrice = product.calculateEffectiveUnitPrice(globalMarkup)
                showScannedProduct(product, effectivePrice)
                _searchQuery.value = ""
                return@launch
            }

            // 4. Neither user nor product found -> Show toast notice
            _searchQuery.value = ""
            showNotFoundNotice()
        }
    }

    fun onUserCardClicked(user: User, onLoginSuccess: (User) -> Unit) {
        if (isLoggingIn) return
        dismissFeedback()
        _pinRequiredNoticeUser.value = null
        if (isNetworkPortal) {
            if (user.pin.isNullOrBlank()) {
                _pinRequiredNoticeUser.value = user
            } else {
                _selectedUserForPin.value = user
                _pinInput.value = ""
                _pinError.value = null
            }
        } else {
            if (user.pin.isNullOrBlank() || !user.requirePinOnKiosk) {
                isLoggingIn = true
                _searchQuery.value = ""
                onLoginSuccess(user)
            } else {
                _selectedUserForPin.value = user
                _pinInput.value = ""
                _pinError.value = null
            }
        }
    }

    fun updatePinInput(pin: String) {
        _pinInput.value = pin
        _pinError.value = null
    }

    fun updateKeepLoggedIn(keep: Boolean) {
        _keepLoggedIn.value = keep
    }

    fun submitPin(adminPassword: String = "", onLoginSuccess: (User) -> Unit) {
        if (isLoggingIn) return
        val user = _selectedUserForPin.value ?: return
        val trimmedInput = _pinInput.value.trim()
        if (onAuthenticateNetworkUser != null) {
            isLoggingIn = true
            activeScope.launch {
                val authedUser = try {
                    onAuthenticateNetworkUser.invoke(user.id, trimmedInput, _keepLoggedIn.value)
                } catch (_: Exception) {
                    null
                }
                if (authedUser != null) {
                    _selectedUserForPin.value = null
                    _pinInput.value = ""
                    _searchQuery.value = ""
                    onLoginSuccess(authedUser)
                } else {
                    isLoggingIn = false
                    _pinError.value = "pin_incorrect"
                    _pinInput.value = ""
                }
            }
            return
        }
        val trimmedAdmin = adminPassword.trim()
        if (verifyPinOrAdminBypass(user.pin, trimmedInput, trimmedAdmin)) {
            isLoggingIn = true
            _selectedUserForPin.value = null
            _pinInput.value = ""
            _searchQuery.value = ""
            onLoginSuccess(user)
        } else {
            _pinError.value = "pin_incorrect"
            _pinInput.value = ""
        }
    }

    companion object {
        fun verifyPinOrAdminBypass(userPin: String?, inputPin: String, adminPassword: String = ""): Boolean {
            val cleanInput = inputPin.trim()
            val cleanAdminPassword = adminPassword.trim()
            val isUserPinMatch = userPin != null && de.joelneumann.lojinha.security.PasswordHasher.verify(cleanInput, userPin)
            val isAdminBypass = de.joelneumann.lojinha.security.PasswordHasher.verifyAdminBypass(cleanInput, cleanAdminPassword)
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
        dismissFeedback()
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
        val trimmedInput = _adminPasswordInput.value.trim()
        if (onAuthenticateNetworkAdmin != null) {
            activeScope.launch {
                val ok = try {
                    onAuthenticateNetworkAdmin.invoke(trimmedInput)
                } catch (_: Exception) {
                    false
                }
                if (ok) {
                    closeAdminAuthDialog()
                    onAdminAuthSuccess()
                } else {
                    _adminPasswordError.value = "admin_password_incorrect"
                    _adminPasswordInput.value = ""
                }
            }
            return
        }
        val trimmedExpected = expectedPassword.trim()
        if (de.joelneumann.lojinha.security.PasswordHasher.verifyAdminBypass(trimmedInput, trimmedExpected)) {
            closeAdminAuthDialog()
            onAdminAuthSuccess()
        } else {
            _adminPasswordError.value = "admin_password_incorrect"
            _adminPasswordInput.value = ""
        }
    }

    override fun onCleared() {
        super.onCleared()
        loadUsersJob?.cancel()
        loadUsersJob = null
        searchSubmitJob?.cancel()
        searchSubmitJob = null
        feedbackJob?.cancel()
        feedbackJob = null
        resetState()
    }
}
