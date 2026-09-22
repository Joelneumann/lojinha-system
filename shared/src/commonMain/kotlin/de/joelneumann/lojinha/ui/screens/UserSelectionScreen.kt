package de.joelneumann.lojinha.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.joelneumann.lojinha.domain.model.SystemSettings
import de.joelneumann.lojinha.domain.model.User
import de.joelneumann.lojinha.ui.components.general.HeaderBar
import de.joelneumann.lojinha.ui.components.general.SearchInputField
import de.joelneumann.lojinha.ui.components.userselection.PasswordInputDialog
import de.joelneumann.lojinha.ui.components.userselection.UserGrid
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.i18n.LanguageManager
import de.joelneumann.lojinha.ui.theme.*
import de.joelneumann.lojinha.ui.utils.containsIgnoreAccents
import de.joelneumann.lojinha.ui.utils.safeRequestFocus
import de.joelneumann.lojinha.ui.utils.sortedByAccentInsensitive
import de.joelneumann.lojinha.ui.viewmodel.UserSelectionViewModel
import kotlinx.coroutines.launch

@Composable
fun UserSelectionScreen(
    viewModel: UserSelectionViewModel,
    settings: SystemSettings,
    onUserLoggedIn: (User) -> Unit,
    onNavigateToAdmin: () -> Unit
) {
    val users by viewModel.users.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedUserForPin by viewModel.selectedUserForPin.collectAsState()
    val pinInput by viewModel.pinInput.collectAsState()
    val pinError by viewModel.pinError.collectAsState()
    val showAdminAuthDialog by viewModel.showAdminAuthDialog.collectAsState()
    val adminPasswordInput by viewModel.adminPasswordInput.collectAsState()
    val adminPasswordError by viewModel.adminPasswordError.collectAsState()

    UserSelectionContent(
        users = users,
        searchQuery = searchQuery,
        selectedUserForPin = selectedUserForPin,
        pinInput = pinInput,
        pinError = pinError,
        showAdminAuthDialog = showAdminAuthDialog,
        adminPasswordInput = adminPasswordInput,
        adminPasswordError = adminPasswordError,
        onOpenAdminAuthDialog = viewModel::openAdminAuthDialog,
        onSearchQueryChange = viewModel::updateSearchQuery,
        onSearchSubmitted = { viewModel.onSearchSubmitted(onUserLoggedIn) },
        onUserClick = { user -> viewModel.onUserCardClicked(user, onUserLoggedIn) },
        onPinChange = viewModel::updatePinInput,
        onPinSubmit = { viewModel.submitPin(settings.adminPasswordHash, onUserLoggedIn) },
        onPinDismiss = viewModel::cancelPinDialog,
        onAdminPasswordChange = viewModel::updateAdminPassword,
        onAdminPasswordSubmit = { viewModel.submitAdminPassword(settings.adminPasswordHash, onNavigateToAdmin) },
        onAdminPasswordDismiss = viewModel::closeAdminAuthDialog
    )
}

@Composable
fun UserSelectionContent(
    users: List<User>,
    searchQuery: String,
    selectedUserForPin: User?,
    pinInput: String,
    pinError: String?,
    showAdminAuthDialog: Boolean,
    adminPasswordInput: String,
    adminPasswordError: String?,
    onOpenAdminAuthDialog: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchSubmitted: () -> Unit,
    onUserClick: (User) -> Unit,
    onPinChange: (String) -> Unit,
    onPinSubmit: () -> Unit,
    onPinDismiss: () -> Unit,
    onAdminPasswordChange: (String) -> Unit,
    onAdminPasswordSubmit: () -> Unit,
    onAdminPasswordDismiss: () -> Unit
) {
    val strings = I18n.current
    val focusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(selectedUserForPin, showAdminAuthDialog, LanguageManager.currentLanguage) {
        if (selectedUserForPin == null && !showAdminAuthDialog) {
            focusRequester.safeRequestFocus()
        }
    }

    val filteredUsers = remember(users, searchQuery) {
        if (searchQuery.isBlank()) users
        else users.filter { it.name.containsIgnoreAccents(searchQuery) }.sortedByAccentInsensitive { it.name }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        HeaderBar(
            title = strings.appTitle,
            onLanguageClick = {
                coroutineScope.launch {
                    focusRequester.safeRequestFocus()
                }
            },
            actions = {
                Button(
                    onClick = onOpenAdminAuthDialog,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryNavy),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = strings.adminLoginBtn,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = SurfaceWhite
                    )
                }
            }
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SurfaceContainerLight)
                .padding(ScreenPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                SearchInputField(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChange,
                    placeholder = strings.searchUserPlaceholder,
                    onSearchSubmitted = onSearchSubmitted,
                    focusRequester = focusRequester
                )

                Spacer(modifier = Modifier.height(16.dp))

                UserGrid(
                    users = filteredUsers,
                    onUserClick = onUserClick,
                    emptyText = strings.noUsersFound,
                    isSearchActive = searchQuery.isNotBlank(),
                    onOpenAdminSetup = onOpenAdminAuthDialog
                )
            }

            // User PIN Authentication Dialog
            selectedUserForPin?.let { user ->
                PasswordInputDialog(
                    title = user.name,
                    promptText = "${strings.enterPinPrompt} ${user.name}:",
                    inputValue = pinInput,
                    onValueChange = onPinChange,
                    errorText = if (pinError != null) strings.pinIncorrect else null,
                    onDismiss = onPinDismiss,
                    onSubmit = onPinSubmit
                )
            }

            // Admin Password Authentication Dialog
            if (showAdminAuthDialog) {
                PasswordInputDialog(
                    title = strings.adminLoginBtn,
                    promptText = strings.adminPasswordPrompt,
                    inputValue = adminPasswordInput,
                    onValueChange = onAdminPasswordChange,
                    errorText = if (adminPasswordError != null) strings.adminPasswordIncorrect else null,
                    onDismiss = onAdminPasswordDismiss,
                    onSubmit = onAdminPasswordSubmit
                )
            }
        }
    }
}
