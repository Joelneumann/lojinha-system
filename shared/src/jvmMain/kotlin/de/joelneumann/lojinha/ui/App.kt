package de.joelneumann.lojinha.ui

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
import de.joelneumann.lojinha.data.repository.RoomProductRepositoryImpl
import de.joelneumann.lojinha.data.repository.RoomSettingsRepositoryImpl
import de.joelneumann.lojinha.data.repository.RoomTransactionRepositoryImpl
import de.joelneumann.lojinha.data.repository.RoomUserRepositoryImpl
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

    val settingsRepository = remember { RoomSettingsRepositoryImpl(database.settingsDao()) }
    val autoBackupScheduler = remember {
        de.joelneumann.lojinha.data.service.AutoBackupScheduler(
            backupRestoreService = backupRestoreService,
            backupRepository = backupRepository,
            externalScope = coroutineScope,
            settingsRepository = settingsRepository,
            oneDriveBackupService = oneDriveBackupService
        )
    }

    val onDataChanged = remember { { autoBackupScheduler.triggerDataChangeBackup() } }

    val productRepository = remember { RoomProductRepositoryImpl(database.productDao(), onDataChanged) }
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

    var showAbandonCartGuardDialog by remember { mutableStateOf(false) }

    val interactionSource = remember { MutableInteractionSource() }

    LojinhaTheme {
        val strings = I18n.current
        Box(modifier = Modifier.fillMaxSize()) {
            when (currentScreen) {
                AppScreen.MAIN_USER_SELECT -> {
                    val userSelectionViewModel: UserSelectionViewModel = viewModel(
                        factory = LojinhaViewModelFactory.createUserSelectionViewModelFactory(userRepository)
                    )
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
                    if (currentUser != null) {
                        val sessionNonce = remember(currentUser!!.id) { generateUuid() }
                        val userSessionViewModel: UserSessionViewModel = viewModel(
                            key = "user_session_${currentUser!!.id}_$sessionNonce",
                            factory = LojinhaViewModelFactory.createUserSessionViewModelFactory(
                                user = currentUser!!,
                                settingsRepository = settingsRepository,
                                onLogoutRequest = { appViewModel.logout() }
                            )
                        )

                        val showInactivityWarning by userSessionViewModel.showInactivityWarning.collectAsState()
                        val inactivitySecondsRemaining by userSessionViewModel.inactivitySecondsRemaining.collectAsState()

                        Box(modifier = Modifier.fillMaxSize()) {
                            when (currentScreen) {
                                AppScreen.SHOPPING -> {
                                    val shoppingViewModel: ShoppingViewModel = viewModel(
                                        key = "shopping_${currentUser!!.id}_$sessionNonce",
                                        factory = LojinhaViewModelFactory.createShoppingViewModelFactory(productRepository, userRepository, transactionRepository)
                                    )
                                    val cartItems by shoppingViewModel.cartItems.collectAsState()

                                    ShoppingScreen(
                                        viewModel = shoppingViewModel,
                                        user = currentUser!!,
                                        settings = settings,
                                        onLogout = {
                                            if (cartItems.isNotEmpty()) {
                                                showAbandonCartGuardDialog = true
                                            } else {
                                                userSessionViewModel.requestLogout()
                                            }
                                        },
                                        onNavigateToHistory = {
                                            appViewModel.refreshCurrentUser()
                                            appViewModel.navigateTo(AppScreen.TRANSACTION_HISTORY)
                                        },
                                        onUserInteracted = { userSessionViewModel.onUserInteracted() }
                                    )
                                }

                                AppScreen.TRANSACTION_HISTORY -> {
                                    val historyViewModel: TransactionHistoryViewModel = viewModel(
                                        key = "history_${currentUser!!.id}_$sessionNonce",
                                        factory = LojinhaViewModelFactory.createTransactionHistoryViewModelFactory(transactionRepository, userRepository)
                                    )

                                    TransactionHistoryScreen(
                                        viewModel = historyViewModel,
                                        user = currentUser!!,
                                        settings = settings,
                                        onContinueShopping = {
                                            appViewModel.navigateTo(AppScreen.SHOPPING)
                                        },
                                        onLogout = { userSessionViewModel.requestLogout() },
                                        onUserUpdated = { updated ->
                                            appViewModel.updateCurrentUser(updated)
                                        },
                                        onUserInteracted = { userSessionViewModel.onUserInteracted() }
                                    )
                                }

                                else -> {}
                            }

                            // DEBUG: Realtime Inactivity Timer Overlay Badge (easily removable)
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(12.dp),
                                color = Color.Black.copy(alpha = 0.8f),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                val mins = inactivitySecondsRemaining / 60
                                val secs = inactivitySecondsRemaining % 60
                                val formattedTime = "${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}"
                                Text(
                                    text = "⏱️ Debug Timer: $formattedTime (${inactivitySecondsRemaining}s)",
                                    color = Color(0xFFFFD700),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }

                            // Inactivity Warning Modal Dialog (scoped to active user session)
                            if (showInactivityWarning) {
                                InactivityWarningDialog(
                                    secondsRemaining = inactivitySecondsRemaining,
                                    onStayLoggedIn = { userSessionViewModel.onUserInteracted() },
                                    onLogoutNow = { userSessionViewModel.requestLogout() }
                                )
                            }
                        }
                    }
                }

                AppScreen.ADMIN_PANEL -> {
                    val adminProductsViewModel: AdminProductsViewModel = viewModel(
                        factory = LojinhaViewModelFactory.createAdminProductsViewModelFactory(productRepository, settingsRepository)
                    )
                    val adminUsersViewModel: AdminUsersViewModel = viewModel(
                        factory = LojinhaViewModelFactory.createAdminUsersViewModelFactory(userRepository, transactionRepository)
                    )
                    val adminTransactionsViewModel: AdminTransactionsViewModel = viewModel(
                        factory = LojinhaViewModelFactory.createAdminTransactionsViewModelFactory(transactionRepository, userRepository, productRepository)
                    )
                    val adminSettingsViewModel: AdminSettingsViewModel = viewModel(
                        factory = LojinhaViewModelFactory.createAdminSettingsViewModelFactory(
                            settingsRepository = settingsRepository,
                            backupRepository = backupRepository,
                            onRunRoutineNow = { routine ->
                                coroutineScope.launch {
                                    autoBackupScheduler.executeRoutine(routine)
                                }
                            },
                            oneDriveBackupService = oneDriveBackupService
                        )
                    )

                    de.joelneumann.lojinha.ui.screens.admin.AdminScreen(
                        productsViewModel = adminProductsViewModel,
                        usersViewModel = adminUsersViewModel,
                        transactionsViewModel = adminTransactionsViewModel,
                        settingsViewModel = adminSettingsViewModel,
                        onExitAdmin = {
                            appViewModel.navigateTo(AppScreen.MAIN_USER_SELECT)
                        }
                    )
                }
            }

            // Abandon Cart Guard Dialog
            if (showAbandonCartGuardDialog) {
                AlertDialog(
                    onDismissRequest = { showAbandonCartGuardDialog = false },
                    title = { Text(strings.abandonCartTitle, fontWeight = FontWeight.Bold, color = PrimaryNavy) },
                    text = { Text(strings.abandonCartMsg, color = TextSecondarySubtle) },
                    confirmButton = {
                        Button(
                            onClick = {
                                showAbandonCartGuardDialog = false
                                appViewModel.logout()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ColorDangerCrimson)
                        ) {
                            Text(strings.confirm, color = SurfaceWhite, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = { showAbandonCartGuardDialog = false }) {
                            Text(strings.cancel)
                        }
                    }
                )
            }
        }
    }
}