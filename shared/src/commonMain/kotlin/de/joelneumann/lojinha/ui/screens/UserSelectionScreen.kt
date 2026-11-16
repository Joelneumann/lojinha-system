package de.joelneumann.lojinha.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.joelneumann.lojinha.domain.model.SystemSettings
import de.joelneumann.lojinha.domain.model.User
import de.joelneumann.lojinha.ui.components.general.HeaderBar
import de.joelneumann.lojinha.ui.components.general.LocalHeaderCompact
import de.joelneumann.lojinha.ui.components.general.SearchInputField
import de.joelneumann.lojinha.ui.components.userselection.PasswordInputDialog
import de.joelneumann.lojinha.ui.components.userselection.ProductScanFeedbackBanner
import de.joelneumann.lojinha.ui.components.userselection.UserGrid
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.i18n.LanguageManager
import de.joelneumann.lojinha.ui.theme.*
import de.joelneumann.lojinha.ui.utils.containsIgnoreAccents
import de.joelneumann.lojinha.ui.utils.safeRequestFocus
import de.joelneumann.lojinha.ui.utils.sortedByAccentInsensitive
import de.joelneumann.lojinha.ui.viewmodel.ScannedProductInfo
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
    val isLoadingUsers by viewModel.isLoadingUsers.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedUserForPin by viewModel.selectedUserForPin.collectAsState()
    val pinInput by viewModel.pinInput.collectAsState()
    val pinError by viewModel.pinError.collectAsState()
    val keepLoggedIn by viewModel.keepLoggedIn.collectAsState()
    val showAdminAuthDialog by viewModel.showAdminAuthDialog.collectAsState()
    val adminPasswordInput by viewModel.adminPasswordInput.collectAsState()
    val adminPasswordError by viewModel.adminPasswordError.collectAsState()
    val scannedProduct by viewModel.scannedProduct.collectAsState()
    val notFoundNotice by viewModel.notFoundNotice.collectAsState()
    val pinRequiredNoticeUser by viewModel.pinRequiredNoticeUser.collectAsState()

    UserSelectionContent(
        users = users,
        isLoadingUsers = isLoadingUsers,
        searchQuery = searchQuery,
        scannedProduct = scannedProduct,
        notFoundNotice = notFoundNotice,
        selectedUserForPin = selectedUserForPin,
        pinInput = pinInput,
        pinError = pinError,
        showAdminAuthDialog = showAdminAuthDialog,
        adminPasswordInput = adminPasswordInput,
        adminPasswordError = adminPasswordError,
        pinRequiredNoticeUser = pinRequiredNoticeUser,
        onOpenAdminAuthDialog = viewModel::openAdminAuthDialog,
        onSearchQueryChange = viewModel::updateSearchQuery,
        onSearchSubmitted = { viewModel.onSearchSubmitted(settings.globalMarkupPercent, onUserLoggedIn) },
        onUserClick = { user -> viewModel.onUserCardClicked(user, onUserLoggedIn) },
        onPinChange = viewModel::updatePinInput,
        onPinSubmit = { viewModel.submitPin(settings.adminPasswordHash, onUserLoggedIn) },
        onPinDismiss = viewModel::cancelPinDialog,
        onAdminPasswordChange = viewModel::updateAdminPassword,
        onAdminPasswordSubmit = { viewModel.submitAdminPassword(settings.adminPasswordHash, onNavigateToAdmin) },
        onAdminPasswordDismiss = viewModel::closeAdminAuthDialog,
        onDismissFeedback = viewModel::dismissFeedback,
        onDismissPinRequiredNotice = viewModel::dismissPinRequiredNotice,
        keepLoggedIn = keepLoggedIn,
        onKeepLoggedInChange = if (viewModel.showKeepLoggedInOption) viewModel::updateKeepLoggedIn else null
    )
}

@Composable
fun UserSelectionContent(
    users: List<User>,
    isLoadingUsers: Boolean = false,
    searchQuery: String,
    scannedProduct: ScannedProductInfo? = null,
    notFoundNotice: Boolean = false,
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
    onAdminPasswordDismiss: () -> Unit,
    onDismissFeedback: () -> Unit = {},
    pinRequiredNoticeUser: User? = null,
    onDismissPinRequiredNotice: () -> Unit = {},
    keepLoggedIn: Boolean = false,
    onKeepLoggedInChange: ((Boolean) -> Unit)? = null
) {
    val strings = I18n.current
    val focusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(selectedUserForPin, showAdminAuthDialog, LanguageManager.currentLanguage) {
        if (selectedUserForPin == null && !showAdminAuthDialog) {
            focusRequester.safeRequestFocus()
        }
    }

    val trimmedQuery = remember(searchQuery) { searchQuery.trim() }
    val filteredUsers = remember(users, trimmedQuery) {
        if (trimmedQuery.isBlank()) users
        else users.filter { user ->
            user.name.containsIgnoreAccents(trimmedQuery) ||
            user.userBarcode?.contains(trimmedQuery, ignoreCase = true) == true ||
            user.userBarcodeNumber?.contains(trimmedQuery, ignoreCase = true) == true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.F12) {
                    if (!showAdminAuthDialog && selectedUserForPin == null) {
                        onOpenAdminAuthDialog()
                    }
                    true
                } else false
            }
    ) {
        HeaderBar(
            title = strings.appTitle,
            onLanguageClick = {
                coroutineScope.launch {
                    focusRequester.safeRequestFocus()
                }
            },
            actions = {
                val isCompactHeader = LocalHeaderCompact.current
                Button(
                    onClick = onOpenAdminAuthDialog,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryNavy),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = if (isCompactHeader) 12.dp else 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = strings.adminLoginBtn,
                            fontSize = if (isCompactHeader) 12.sp else 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = SurfaceWhite
                        )
                        if (!isCompactHeader) {
                            Text(
                                text = "(F12)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Normal,
                                color = SurfaceWhite.copy(alpha = 0.45f)
                            )
                        }
                    }
                }
            }
        )

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(SurfaceContainerLight)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    coroutineScope.launch {
                        focusRequester.safeRequestFocus()
                    }
                }
        ) {
            val isMobile = maxWidth < MOBILE_BREAKPOINT

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (isMobile) 10.dp else ScreenPadding)
            ) {
                SearchInputField(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChange,
                    placeholder = strings.searchUserPlaceholder,
                    onSearchSubmitted = onSearchSubmitted,
                    focusRequester = focusRequester,
                    onKeyDown = { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.F12) {
                            if (!showAdminAuthDialog && selectedUserForPin == null) {
                                onOpenAdminAuthDialog()
                            }
                            true
                        } else false
                    }
                )

                Spacer(modifier = Modifier.height(if (isMobile) 10.dp else 16.dp))

                UserGrid(
                    users = filteredUsers,
                    onUserClick = onUserClick,
                    emptyText = strings.noUsersFound,
                    isSearchActive = searchQuery.isNotBlank(),
                    isLoading = isLoadingUsers,
                    isMobile = isMobile,
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
                    onSubmit = onPinSubmit,
                    isMobile = isMobile,
                    keepLoggedIn = keepLoggedIn,
                    onKeepLoggedInChange = onKeepLoggedInChange
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
                    onSubmit = onAdminPasswordSubmit,
                    isMobile = isMobile
                )
            }

            // Network Portal PIN Required Notice Dialog
            if (pinRequiredNoticeUser != null) {
                AlertDialog(
                    onDismissRequest = onDismissPinRequiredNotice,
                    title = {
                        Text(
                            text = strings.networkPortalPinRequiredTitle,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryNavy
                        )
                    },
                    text = {
                        Text(
                            text = strings.networkPortalPinRequiredMsg,
                            color = PrimaryNavy
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = onDismissPinRequiredNotice,
                            colors = ButtonDefaults.buttonColors(containerColor = AccentNavy),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(strings.confirm, fontWeight = FontWeight.Bold)
                        }
                    },
                    containerColor = SurfaceWhite,
                    shape = RoundedCornerShape(16.dp)
                )
            }

            ProductScanFeedbackBanner(
                scannedProduct = scannedProduct,
                notFoundNotice = notFoundNotice,
                onDismiss = onDismissFeedback,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
            )
        }
    }
}
