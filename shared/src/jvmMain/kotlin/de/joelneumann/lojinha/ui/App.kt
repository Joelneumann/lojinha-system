package de.joelneumann.lojinha.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import de.joelneumann.lojinha.data.database.DatabaseFactory
import de.joelneumann.lojinha.domain.model.User
import de.joelneumann.lojinha.data.repository.RoomProductRepositoryImpl
import de.joelneumann.lojinha.data.repository.RoomSettingsRepositoryImpl
import de.joelneumann.lojinha.data.repository.RoomTransactionRepositoryImpl
import de.joelneumann.lojinha.data.repository.RoomUserRepositoryImpl
import de.joelneumann.lojinha.ui.components.general.ConfirmationDialog
import de.joelneumann.lojinha.ui.components.general.InactivityWarningDialog
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.screens.ShoppingScreen
import de.joelneumann.lojinha.ui.screens.TransactionHistoryScreen
import de.joelneumann.lojinha.ui.screens.UserSelectionScreen
import de.joelneumann.lojinha.ui.theme.*
import de.joelneumann.lojinha.ui.utils.currentTimeMillis
import de.joelneumann.lojinha.ui.utils.generateUuid
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import de.joelneumann.lojinha.ui.viewmodel.*
import de.joelneumann.lojinha.ui.viewmodel.admin.*

@Composable
fun App() {
    val database = remember { DatabaseFactory.createDatabase() }
    val backupRepository = remember { de.joelneumann.lojinha.data.repository.RoomBackupRepositoryImpl(database.backupDao()) }
    val backupRestoreService = remember { de.joelneumann.lojinha.data.service.BackupRestoreService(database) }
    val oneDriveBackupService = remember { de.joelneumann.lojinha.data.service.OneDriveBackupService() }
    val coroutineScope = rememberCoroutineScope()

    val onDataChanged = remember { { de.joelneumann.lojinha.data.service.DataChangeNotifier.notifyDataChanged() } }
    val settingsRepository = remember { RoomSettingsRepositoryImpl(database.settingsDao(), onDataChanged) }
    val autoBackupScheduler = remember {
        de.joelneumann.lojinha.data.service.AutoBackupScheduler(
            backupRestoreService = backupRestoreService,
            backupRepository = backupRepository,
            externalScope = coroutineScope,
            settingsRepository = settingsRepository,
            oneDriveBackupService = oneDriveBackupService
        )
    }

    DisposableEffect(autoBackupScheduler) {
        val listener = { autoBackupScheduler.triggerDataChangeBackup() }
        de.joelneumann.lojinha.data.service.DataChangeNotifier.addListener(listener)
        onDispose {
            de.joelneumann.lojinha.data.service.DataChangeNotifier.removeListener(listener)
        }
    }

    val productRepository = remember { RoomProductRepositoryImpl(database.productDao(), database.transactionDao(), onDataChanged) }
    val userRepository = remember { RoomUserRepositoryImpl(database.userDao(), database.transactionDao(), onDataChanged) }
    val transactionRepository = remember { RoomTransactionRepositoryImpl(database.transactionDao(), onDataChanged) }

    LaunchedEffect(Unit) {
        autoBackupScheduler.startScheduler()
    }

    val appViewModel: AppViewModel = viewModel(
        factory = LojinhaViewModelFactory.createAppViewModelFactory(userRepository, settingsRepository)
    )

    val currentScreen by appViewModel.currentScreen.collectAsState()
    val settings by appViewModel.settings.collectAsState()
    val currentUser by appViewModel.currentUser.collectAsState()

    var cachedSessionUser by remember { mutableStateOf<User?>(null) }
    if (currentUser != null) {
        cachedSessionUser = currentUser
    }
    val sessionUser = currentUser ?: cachedSessionUser

    var showAbandonCartGuardDialog by remember { mutableStateOf(false) }
    var onUserInteractedSession by remember { mutableStateOf<(() -> Unit)?>(null) }

    LaunchedEffect(currentUser) {
        if (currentUser == null) {
            showAbandonCartGuardDialog = false
        }
    }

    val interactionSource = remember { MutableInteractionSource() }

    LojinhaTheme {
        val strings = I18n.current
        Box(modifier = Modifier.fillMaxSize()) {
            Crossfade(
                targetState = when (currentScreen) {
                    AppScreen.MAIN_USER_SELECT -> AppScreen.MAIN_USER_SELECT
                    AppScreen.ADMIN_PANEL -> AppScreen.ADMIN_PANEL
                    AppScreen.SHOPPING, AppScreen.TRANSACTION_HISTORY -> AppScreen.SHOPPING
                },
                animationSpec = tween(durationMillis = 250),
                modifier = Modifier.fillMaxSize()
            ) { targetContext ->
                when (targetContext) {
                    AppScreen.MAIN_USER_SELECT -> {
                        val userSelectionViewModel: UserSelectionViewModel = viewModel(
                            factory = LojinhaViewModelFactory.createUserSelectionViewModelFactory(userRepository)
                        )
                        LaunchedEffect(Unit) {
                            userSelectionViewModel.resetState()
                        }
                        UserSelectionScreen(
                            viewModel = userSelectionViewModel,
                            settings = settings,
                            onUserLoggedIn = { user ->
                                appViewModel.loginUser(user)
                            },
                            onNavigateToAdmin = {
                                appViewModel.navigateTo(AppScreen.ADMIN_PANEL)
                            }
                        )
                    }

                    AppScreen.SHOPPING, AppScreen.TRANSACTION_HISTORY -> {
                        if (sessionUser != null) {
                            val sessionNonce = remember(sessionUser.id) { generateUuid() }
                            val userSessionViewModel: UserSessionViewModel = viewModel(
                                key = "user_session_${sessionUser.id}_$sessionNonce",
                                factory = LojinhaViewModelFactory.createUserSessionViewModelFactory(
                                    user = sessionUser,
                                    settingsRepository = settingsRepository,
                                    onLogoutRequest = {
                                        showAbandonCartGuardDialog = false
                                        appViewModel.logout()
                                    },
                                    initialSettings = settings
                                )
                            )

                            SideEffect {
                                onUserInteractedSession = { userSessionViewModel.onUserInteracted(true) }
                            }

                            DisposableEffect(sessionUser.id, sessionNonce) {
                                onDispose {
                                    onUserInteractedSession = null
                                    userSessionViewModel.stopInactivityTimer()
                                }
                            }

                            val shoppingViewModel: ShoppingViewModel = viewModel(
                                key = "shopping_${sessionUser.id}_$sessionNonce",
                                factory = LojinhaViewModelFactory.createShoppingViewModelFactory(productRepository, userRepository, transactionRepository)
                            )
                            val historyViewModel: TransactionHistoryViewModel = viewModel(
                                key = "history_${sessionUser.id}_$sessionNonce",
                                factory = LojinhaViewModelFactory.createTransactionHistoryViewModelFactory(transactionRepository, userRepository)
                            )
                            val cartItems by shoppingViewModel.cartItems.collectAsState()

                            val showInactivityWarning by userSessionViewModel.showInactivityWarning.collectAsState()

                            val handleLogoutRequest = remember(cartItems.isNotEmpty(), userSessionViewModel) {
                                {
                                    if (cartItems.isNotEmpty()) {
                                        showAbandonCartGuardDialog = true
                                    } else {
                                        userSessionViewModel.requestLogout()
                                    }
                                }
                            }
                            val onContinueShopping = remember(appViewModel) {
                                { appViewModel.navigateTo(AppScreen.SHOPPING) }
                            }
                            val onNavigateToHistory = remember(userSessionViewModel, appViewModel) {
                                {
                                    userSessionViewModel.resumeInactivityTimer()
                                    appViewModel.refreshCurrentUser()
                                    appViewModel.navigateTo(AppScreen.TRANSACTION_HISTORY)
                                }
                            }
                            val onUserUpdated = remember(appViewModel) {
                                { updated: de.joelneumann.lojinha.domain.model.User -> appViewModel.updateCurrentUser(updated) }
                            }
                            val onUserInteracted = remember(userSessionViewModel) {
                                { force: Boolean -> userSessionViewModel.onUserInteracted(force) }
                            }
                            val onPurchaseFinalized = remember(appViewModel) {
                                { appViewModel.refreshCurrentUser() }
                            }
                            val onPauseTimer = remember(userSessionViewModel) {
                                { userSessionViewModel.pauseInactivityTimer() }
                            }
                            val onResumeTimer = remember(userSessionViewModel) {
                                { userSessionViewModel.resumeInactivityTimer() }
                            }

                            Box(modifier = Modifier.fillMaxSize()) {
                                val sessionSubScreen = if (currentScreen == AppScreen.TRANSACTION_HISTORY) AppScreen.TRANSACTION_HISTORY else AppScreen.SHOPPING
                                Crossfade(
                                    targetState = sessionSubScreen,
                                    animationSpec = tween(durationMillis = 250),
                                    modifier = Modifier.fillMaxSize()
                                ) { activeScreen ->
                                    when (activeScreen) {
                                        AppScreen.SHOPPING -> {
                                            ShoppingScreen(
                                                viewModel = shoppingViewModel,
                                                user = sessionUser,
                                                settings = settings,
                                                onLogout = handleLogoutRequest,
                                                onNavigateToHistory = onNavigateToHistory,
                                                onUserInteracted = onUserInteracted,
                                                onPurchaseFinalized = onPurchaseFinalized,
                                                onPauseTimer = onPauseTimer,
                                                onResumeTimer = onResumeTimer
                                            )
                                        }

                                        AppScreen.TRANSACTION_HISTORY -> {
                                            TransactionHistoryScreen(
                                                viewModel = historyViewModel,
                                                user = sessionUser,
                                                settings = settings,
                                                onContinueShopping = onContinueShopping,
                                                onLogout = handleLogoutRequest,
                                                onUserUpdated = onUserUpdated,
                                                onUserInteracted = onUserInteracted
                                            )
                                        }

                                        else -> {}
                                    }
                                }

                                // Inactivity Warning Modal Dialog (scoped to active user session)
                                if (showInactivityWarning) {
                                    val inactivitySecondsRemaining by userSessionViewModel.inactivitySecondsRemaining.collectAsState()
                                    InactivityWarningDialog(
                                        secondsRemaining = inactivitySecondsRemaining,
                                        onStayLoggedIn = { userSessionViewModel.stayLoggedIn() }
                                    )
                                }
                            }
                        }
                    }

                    AppScreen.ADMIN_PANEL -> {
                        val adminProductsViewModel = remember {
                            AdminProductsViewModel(productRepository, settingsRepository)
                        }
                        val adminUsersViewModel = remember {
                            AdminUsersViewModel(userRepository, transactionRepository)
                        }
                        val adminTransactionsViewModel = remember {
                            AdminTransactionsViewModel(transactionRepository, userRepository, productRepository)
                        }
                        
                        val billingListRepository = remember {
                            de.joelneumann.lojinha.data.repository.RoomBillingListRepositoryImpl(
                                database.billingListDao(), onDataChanged
                            )
                        }
                        
                        val adminBulkBillingViewModel = remember {
                            AdminBulkBillingViewModel(billingListRepository, userRepository, transactionRepository)
                        }
                        
                        val adminSettingsViewModel = remember {
                            AdminSettingsViewModel(
                                settingsRepository = settingsRepository,
                                backupRepository = backupRepository,
                                onRunRoutineNow = { routine ->
                                    coroutineScope.launch {
                                        autoBackupScheduler.executeRoutine(routine)
                                    }
                                },
                                oneDriveBackupService = oneDriveBackupService,
                                onPreviewCsvImport = { platformFile, type ->
                                    val file = java.io.File(platformFile.absolutePath)
                                    if (type.equals("Products", ignoreCase = true)) {
                                        backupRestoreService.importProductsFromCsv(file, dryRun = true)
                                    } else {
                                        backupRestoreService.importUsersFromCsv(file, dryRun = true)
                                    }
                                },
                                onExecuteCsvImport = { platformFile, type ->
                                    val file = java.io.File(platformFile.absolutePath)
                                    val result = if (type.equals("Products", ignoreCase = true)) {
                                        backupRestoreService.importProductsFromCsv(file, dryRun = false)
                                    } else {
                                        backupRestoreService.importUsersFromCsv(file, dryRun = false)
                                    }
                                    onDataChanged()
                                    result
                                },
                                onExecuteDbRestore = { platformFile ->
                                    val file = java.io.File(platformFile.absolutePath)
                                    backupRestoreService.restoreDbFromBackup(file)
                                    onDataChanged()
                                },
                                onExecuteWipeData = {
                                    backupRestoreService.wipeAllData()
                                    onDataChanged()
                                }
                            )
                        }

                        DisposableEffect(Unit) {
                            onDispose {
                                adminProductsViewModel.viewModelScope.cancel()
                                adminUsersViewModel.viewModelScope.cancel()
                                adminTransactionsViewModel.viewModelScope.cancel()
                                adminBulkBillingViewModel.viewModelScope.cancel()
                                adminSettingsViewModel.viewModelScope.cancel()
                            }
                        }

                        de.joelneumann.lojinha.ui.screens.admin.AdminScreen(
                            productsViewModel = adminProductsViewModel,
                            usersViewModel = adminUsersViewModel,
                            transactionsViewModel = adminTransactionsViewModel,
                            bulkBillingViewModel = adminBulkBillingViewModel,
                            settingsViewModel = adminSettingsViewModel,
                            onExitAdmin = {
                                appViewModel.navigateTo(AppScreen.MAIN_USER_SELECT)
                            }
                        )
                    }
                }
            }

            // Abandon Cart Guard Dialog
            if (showAbandonCartGuardDialog) {
                ConfirmationDialog(
                    title = strings.abandonCartTitle,
                    message = strings.abandonCartMsg,
                    confirmText = strings.confirm,
                    cancelText = strings.cancel,
                    confirmButtonColor = ColorDangerCrimson,
                    onConfirm = {
                        showAbandonCartGuardDialog = false
                        appViewModel.logout()
                    },
                    onDismiss = {
                        showAbandonCartGuardDialog = false
                        onUserInteractedSession?.invoke()
                    }
                )
            }
        }
    }
}