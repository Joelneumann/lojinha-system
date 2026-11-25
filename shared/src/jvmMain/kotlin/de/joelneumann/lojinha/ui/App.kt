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
import de.joelneumann.lojinha.domain.model.Language
import de.joelneumann.lojinha.domain.model.User
import de.joelneumann.lojinha.data.repository.RoomProductRepositoryImpl
import de.joelneumann.lojinha.data.repository.RoomSettingsRepositoryImpl
import de.joelneumann.lojinha.data.repository.RoomTransactionRepositoryImpl
import de.joelneumann.lojinha.data.repository.RoomUserRepositoryImpl
import de.joelneumann.lojinha.data.repository.RoomAdminNoteRepositoryImpl
import de.joelneumann.lojinha.data.repository.RoomShoppingListRepositoryImpl
import de.joelneumann.lojinha.ui.components.general.ConfirmationDialog
import de.joelneumann.lojinha.ui.components.general.InactivityWarningDialog
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.screens.ShoppingScreen
import de.joelneumann.lojinha.ui.screens.TransactionHistoryScreen
import de.joelneumann.lojinha.ui.screens.UserSelectionScreen
import de.joelneumann.lojinha.ui.theme.*
import de.joelneumann.lojinha.ui.utils.PdfFilenames
import de.joelneumann.lojinha.ui.utils.currentTimeMillis
import de.joelneumann.lojinha.ui.utils.generateUuid
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import de.joelneumann.lojinha.domain.repository.*
import de.joelneumann.lojinha.ui.viewmodel.*
import de.joelneumann.lojinha.ui.viewmodel.admin.*
import de.joelneumann.lojinha.ui.components.userselection.PasswordInputDialog
import de.joelneumann.lojinha.security.PasswordHasher
import de.joelneumann.lojinha.util.AppLogger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Composable
fun App(
    database: de.joelneumann.lojinha.data.database.AppDatabase = remember { DatabaseFactory.createDatabase() },
    serverPort: Int = 8080,
    onExitApplication: (() -> Unit)? = null,
    productRepository: ProductRepository? = null,
    userRepository: UserRepository? = null,
    transactionRepository: TransactionRepository? = null,
    billingListRepository: BillingListRepository? = null,
    settingsRepository: SettingsRepository? = null,
    adminNoteRepository: AdminNoteRepository? = null,
    shoppingListRepository: ShoppingListRepository? = null,
    veggieOrderRepository: VeggieOrderRepository? = null,
    showExitAuthDialog: Boolean = false,
    onDismissExitAuthDialog: () -> Unit = {}
) {
    val candidateUrls = remember(serverPort) {
        val urls = mutableListOf("http://localhost:$serverPort")
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            if (interfaces != null) {
                for (iface in interfaces.asSequence()) {
                    if (iface.isLoopback || !iface.isUp) continue
                    for (addr in iface.inetAddresses.asSequence()) {
                        if (addr is java.net.Inet4Address && !addr.isLoopbackAddress) {
                            urls.add("http://${addr.hostAddress}:$serverPort")
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        urls.distinct()
    }
    val backupRepository = remember { de.joelneumann.lojinha.data.repository.RoomBackupRepositoryImpl(database.backupDao()) }
    val backupRestoreService = remember { de.joelneumann.lojinha.data.service.BackupRestoreService(database) }
    val oneDriveBackupService = remember { de.joelneumann.lojinha.data.service.OneDriveBackupService() }
    val currencyRateService = remember { de.joelneumann.lojinha.data.service.KtorCurrencyRateService() }
    val diagnosticsService = remember { de.joelneumann.lojinha.data.service.DiagnosticsService(database) }
    val scaleService = remember { de.joelneumann.lojinha.data.scale.JSerialCommScaleService() }
    val coroutineScope = rememberCoroutineScope()

    DisposableEffect(currencyRateService, scaleService) {
        onDispose {
            currencyRateService.close()
            scaleService.close()
        }
    }

    val onDataChanged = remember { { de.joelneumann.lojinha.data.service.DataChangeNotifier.notifyDataChanged() } }
    val effectiveSettingsRepo = settingsRepository ?: remember { RoomSettingsRepositoryImpl(database.settingsDao(), onDataChanged) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val current = effectiveSettingsRepo.getSettings()
                if (current.autoUpdateExchangeRates) {
                    AppLogger.info("Startup", "Auto-refreshing currency exchange rates...")
                    currencyRateService.fetchRates().onSuccess { rates ->
                        val latest = effectiveSettingsRepo.getSettings()
                        val updated = latest.copy(
                            usdExchangeRate = rates.usdRate,
                            eurExchangeRate = rates.eurRate,
                            lastExchangeRateUpdateTimestamp = rates.timestamp
                        )
                        effectiveSettingsRepo.updateSettings(updated)
                        AppLogger.info("Startup", "Exchange rates updated on startup: 1 BRL = ${rates.usdRate} USD, ${rates.eurRate} EUR (${rates.provider})")
                    }.onFailure { err ->
                        AppLogger.warn("Startup", "Could not update exchange rates on startup: ${err.message}")
                    }
                }
            } catch (e: Exception) {
                AppLogger.warn("Startup", "Exchange rate startup check error: ${e.message}")
            }
        }
    }

    val autoBackupScheduler = remember {
        de.joelneumann.lojinha.data.service.AutoBackupScheduler(
            backupRestoreService = backupRestoreService,
            backupRepository = backupRepository,
            externalScope = coroutineScope,
            settingsRepository = effectiveSettingsRepo,
            oneDriveBackupService = oneDriveBackupService
        )
    }

    DisposableEffect(autoBackupScheduler) {
        val listener = { autoBackupScheduler.triggerDataChangeBackup() }
        de.joelneumann.lojinha.data.service.DataChangeNotifier.addListener(listener)
        autoBackupScheduler.startScheduler()
        onDispose {
            de.joelneumann.lojinha.data.service.DataChangeNotifier.removeListener(listener)
            autoBackupScheduler.stopScheduler()
        }
    }

    val effectiveProductRepo = productRepository ?: remember { RoomProductRepositoryImpl(database.productDao(), database.transactionDao(), database.productStockAdjustmentDao(), onDataChanged) }
    val effectiveUserRepo = userRepository ?: remember { RoomUserRepositoryImpl(database.userDao(), database.transactionDao(), onDataChanged, database.veggieOrderDao()) }
    val effectiveTransactionRepo = transactionRepository ?: remember { RoomTransactionRepositoryImpl(database.transactionDao(), onDataChanged) }
    val effectiveBillingListRepo = billingListRepository ?: remember { de.joelneumann.lojinha.data.repository.RoomBillingListRepositoryImpl(database.billingListDao(), onDataChanged) }
    val effectiveAdminNoteRepo = adminNoteRepository ?: remember { RoomAdminNoteRepositoryImpl(database.adminNoteDao(), onDataChanged) }
    val effectiveShoppingListRepo = shoppingListRepository ?: remember {
        RoomShoppingListRepositoryImpl(database.shoppingListItemDao()) {
            de.joelneumann.lojinha.data.service.DataChangeNotifier.notifyScopedDataChanged(
                de.joelneumann.lojinha.data.service.DataChangeNotifier.ChangeScope(adminOnly = true)
            )
        }
    }
    val effectiveVeggieRepo = veggieOrderRepository ?: remember {
        de.joelneumann.lojinha.data.repository.RoomVeggieOrderRepositoryImpl(
            veggieOrderDao = database.veggieOrderDao(),
            userDao = database.userDao(),
            transactionDao = database.transactionDao(),
            onDataChanged = onDataChanged,
            onAdminOnlyDataChanged = {
                de.joelneumann.lojinha.data.service.DataChangeNotifier.notifyScopedDataChanged(
                    de.joelneumann.lojinha.data.service.DataChangeNotifier.ChangeScope(adminOnly = true)
                )
            },
            runInTransaction = { block ->
                DatabaseFactory.runImmediateTransaction(database, block)
            }
        )
    }
    val veggieImageService = remember { de.joelneumann.lojinha.data.service.VeggieImageService() }

    val appViewModel: AppViewModel = viewModel(
        factory = LojinhaViewModelFactory.createAppViewModelFactory(effectiveUserRepo, effectiveSettingsRepo)
    )

    DisposableEffect(appViewModel) {
        val listener = {
            appViewModel.loadSettings()
            appViewModel.refreshCurrentUser()
        }
        de.joelneumann.lojinha.data.service.DataChangeNotifier.addListener(listener)
        onDispose {
            de.joelneumann.lojinha.data.service.DataChangeNotifier.removeListener(listener)
        }
    }

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
                            factory = LojinhaViewModelFactory.createUserSelectionViewModelFactory(effectiveUserRepo, effectiveProductRepo)
                        )
                        LaunchedEffect(Unit) {
                            userSelectionViewModel.resetState()
                            userSelectionViewModel.loadUsers()
                        }
                        DisposableEffect(userSelectionViewModel) {
                            val listener = {
                                userSelectionViewModel.loadUsers()
                            }
                            de.joelneumann.lojinha.data.service.DataChangeNotifier.addListener(listener)
                            onDispose {
                                de.joelneumann.lojinha.data.service.DataChangeNotifier.removeListener(listener)
                            }
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
                                    settingsRepository = effectiveSettingsRepo,
                                    onLogoutRequest = {
                                        showAbandonCartGuardDialog = false
                                        appViewModel.logout()
                                    },
                                    initialSettings = settings
                                )
                            )

                            val shoppingViewModel: ShoppingViewModel = viewModel(
                                key = "shopping_${sessionUser.id}_$sessionNonce",
                                factory = LojinhaViewModelFactory.createShoppingViewModelFactory(
                                    effectiveProductRepo,
                                    effectiveUserRepo,
                                    effectiveTransactionRepo,
                                    effectiveAdminNoteRepo
                                )
                            )
                            val historyViewModel: TransactionHistoryViewModel = viewModel(
                                key = "history_${sessionUser.id}_$sessionNonce",
                                factory = LojinhaViewModelFactory.createTransactionHistoryViewModelFactory(effectiveTransactionRepo, effectiveUserRepo)
                            )

                            SideEffect {
                                onUserInteractedSession = { userSessionViewModel.onUserInteracted(true) }
                            }

                            DisposableEffect(sessionUser.id, sessionNonce) {
                                val listener = {
                                    shoppingViewModel.loadProducts()
                                    historyViewModel.loadUserTransactions(sessionUser.id, resetFilters = false)
                                }
                                de.joelneumann.lojinha.data.service.DataChangeNotifier.addListener(listener)
                                onDispose {
                                    de.joelneumann.lojinha.data.service.DataChangeNotifier.removeListener(listener)
                                    onUserInteractedSession = null
                                    userSessionViewModel.stopInactivityTimer()
                                    shoppingViewModel.teardown()
                                }
                            }

                            val cartItems by shoppingViewModel.cartItems.collectAsState()

                            val handleInactivityTimeout = remember(sessionUser, shoppingViewModel, historyViewModel, userSessionViewModel, appViewModel) {
                                {
                                    showAbandonCartGuardDialog = false
                                    val items = shoppingViewModel.cartItems.value
                                    if (items.isNotEmpty()) {
                                        userSessionViewModel.pauseInactivityTimer()
                                        appViewModel.navigateTo(AppScreen.SHOPPING)
                                        shoppingViewModel.completePurchase(
                                            user = sessionUser,
                                            onPurchaseFinalized = {
                                                appViewModel.refreshCurrentUser()
                                                historyViewModel.loadUserTransactions(sessionUser.id, resetFilters = true)
                                            },
                                            onError = { error ->
                                                AppLogger.warn("App", "Auto-checkout on inactivity failed: $error. Logging out to protect user session.")
                                                showAbandonCartGuardDialog = false
                                                userSessionViewModel.requestLogout()
                                            }
                                        )
                                    } else {
                                        userSessionViewModel.requestLogout()
                                    }
                                }
                            }

                            SideEffect {
                                userSessionViewModel.onInactivityTimeout = handleInactivityTimeout
                            }

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
                            val onNavigateToHistory = remember(userSessionViewModel, appViewModel, historyViewModel, sessionUser) {
                                {
                                    userSessionViewModel.resumeInactivityTimer()
                                    appViewModel.refreshCurrentUser()
                                    sessionUser?.let { historyViewModel.loadUserTransactions(it.id, resetFilters = true) }
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
                                                onUserUpdated = onUserUpdated,
                                                onUserInteracted = onUserInteracted,
                                                onPurchaseFinalized = onPurchaseFinalized,
                                                onPauseTimer = onPauseTimer,
                                                onResumeTimer = onResumeTimer,
                                                scaleService = scaleService
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
                                    showAbandonCartGuardDialog = false
                                    val inactivitySecondsRemaining by userSessionViewModel.inactivitySecondsRemaining.collectAsState()
                                    InactivityWarningDialog(
                                        secondsRemaining = inactivitySecondsRemaining,
                                        hasCartItems = cartItems.isNotEmpty(),
                                        onStayLoggedIn = {
                                            showAbandonCartGuardDialog = false
                                            userSessionViewModel.stayLoggedIn()
                                        }
                                    )
                                }
                            }
                        }
                    }

                    AppScreen.ADMIN_PANEL -> {
                        val adminNotesViewModel = remember {
                            AdminNotesViewModel(effectiveAdminNoteRepo)
                        }
                        val adminProductsViewModel = remember {
                            AdminProductsViewModel(effectiveProductRepo, effectiveSettingsRepo)
                        }
                        val adminShoppingListViewModel = remember {
                            AdminShoppingListViewModel(effectiveShoppingListRepo, effectiveProductRepo, effectiveAdminNoteRepo)
                        }
                        val adminUsersViewModel = remember {
                            AdminUsersViewModel(effectiveUserRepo, effectiveTransactionRepo, effectiveSettingsRepo)
                        }
                        val adminTransactionsViewModel = remember {
                            AdminTransactionsViewModel(effectiveTransactionRepo, effectiveUserRepo, effectiveProductRepo)
                        }
                        
                        val adminBulkBillingViewModel = remember {
                            AdminBulkBillingViewModel(effectiveBillingListRepo, effectiveUserRepo, effectiveTransactionRepo)
                        }

                        val adminVeggieViewModel = remember {
                            AdminVeggieViewModel(
                                veggieOrderRepository = effectiveVeggieRepo,
                                userRepository = effectiveUserRepo,
                                settingsRepository = effectiveSettingsRepo,
                                onSaveProductImage = { productId, productName, bytes, previousFileName ->
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        val otherKeys = effectiveVeggieRepo.getAllVeggieProducts()
                                            .filter { it.id != productId }
                                            .mapNotNull { it.imageFileName }
                                        veggieImageService.saveProductImage(
                                            productId = productId,
                                            productName = productName,
                                            rawImageBytes = bytes,
                                            previousImageFileName = previousFileName,
                                            existingImageKeys = otherKeys
                                        )
                                    }
                                },
                                onDeleteProductImage = { productId, previousFileName ->
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        val otherKeys = effectiveVeggieRepo.getAllVeggieProducts()
                                            .filter { it.id != productId }
                                            .mapNotNull { it.imageFileName }
                                        veggieImageService.deleteProductImages(previousFileName ?: productId, otherKeys)
                                    }
                                }
                            )
                        }
                        
                        val adminSettingsViewModel = remember {
                            AdminSettingsViewModel(
                                settingsRepository = effectiveSettingsRepo,
                                backupRepository = backupRepository,
                                onRunRoutineNow = { routine ->
                                    autoBackupScheduler.executeRoutine(routine)
                                },
                                onExecuteBackupNowLocal = { path, fileType, writeMode ->
                                    val dir = java.io.File(path)
                                    val generatedFiles = backupRestoreService.performLocalBackup(dir, fileType, writeMode)
                                    generatedFiles.map { it.name }
                                },
                                oneDriveBackupService = oneDriveBackupService,
                                currencyRateService = currencyRateService,
                                onPreviewCsvImport = { platformFile, type ->
                                    val file = java.io.File(platformFile.absolutePath)
                                    when {
                                        type.equals("Products", ignoreCase = true) ->
                                            backupRestoreService.importProductsFromCsv(file, dryRun = true)
                                        type.equals("VeggieProducts", ignoreCase = true) ->
                                            backupRestoreService.importVeggieProductsFromCsv(file, dryRun = true)
                                        else ->
                                            backupRestoreService.importUsersFromCsv(file, dryRun = true)
                                    }
                                },
                                onExecuteCsvImport = { platformFile, type ->
                                    val file = java.io.File(platformFile.absolutePath)
                                    val result = when {
                                        type.equals("Products", ignoreCase = true) ->
                                            backupRestoreService.importProductsFromCsv(file, dryRun = false)
                                        type.equals("VeggieProducts", ignoreCase = true) ->
                                            backupRestoreService.importVeggieProductsFromCsv(file, dryRun = false)
                                        else ->
                                            backupRestoreService.importUsersFromCsv(file, dryRun = false)
                                    }
                                    onDataChanged()
                                    result
                                },
                                onExecuteDbRestore = { platformFile, preserveExistingRoutines, preserveCurrentAdminPassword ->
                                    val file = java.io.File(platformFile.absolutePath)
                                    backupRestoreService.restoreDbFromBackup(file, preserveExistingRoutines, preserveCurrentAdminPassword)
                                    onDataChanged()
                                },
                                onExecuteWipeData = { preserveBackupRoutines, preserveAdminPassword ->
                                    backupRestoreService.wipeAllData(preserveBackupRoutines, preserveAdminPassword)
                                    onDataChanged()
                                },
                                onExportSupportBundle = { prepareEmail, recipientEmail ->
                                    val bundleZip = diagnosticsService.createSupportBundle()
                                    diagnosticsService.revealInFileExplorer(bundleZip)
                                    if (prepareEmail) {
                                        diagnosticsService.openEmailDraft(recipientEmail, bundleZip)
                                    }
                                },
                                onOpenLogFolder = {
                                    diagnosticsService.openLogFolder()
                                },
                                logDirectoryPath = de.joelneumann.lojinha.util.FileRollingLogger.getLogDirectory().absolutePath,
                                getLogFolderSizeFormatted = {
                                    de.joelneumann.lojinha.util.FileRollingLogger.formatFileSize(
                                        de.joelneumann.lojinha.util.FileRollingLogger.getTotalLogSizeBytes()
                                    )
                                },
                                onOpenVeggieImagesFolder = {
                                    diagnosticsService.openFolder(veggieImageService.getImageDirectory())
                                },
                                veggieImagesDirectoryPath = veggieImageService.getImageDirectory().absolutePath,
                                serverUrls = candidateUrls,
                                onExitApplication = onExitApplication,
                                userRepository = effectiveUserRepo
                            )
                        }

                        DisposableEffect(
                            adminNotesViewModel,
                            adminProductsViewModel,
                            adminShoppingListViewModel,
                            adminUsersViewModel,
                            adminTransactionsViewModel,
                            adminBulkBillingViewModel,
                            adminVeggieViewModel,
                            adminSettingsViewModel
                        ) {
                            val listener = {
                                adminNotesViewModel.loadNotes()
                                adminProductsViewModel.loadData()
                                adminUsersViewModel.loadData()
                                adminTransactionsViewModel.loadData()
                                adminBulkBillingViewModel.loadData()
                                adminVeggieViewModel.loadData()
                                adminSettingsViewModel.loadSettings()
                                adminSettingsViewModel.loadRoutines()
                                adminSettingsViewModel.refreshLogSize()
                            }
                            de.joelneumann.lojinha.data.service.DataChangeNotifier.addListener(listener)
                            onDispose {
                                de.joelneumann.lojinha.data.service.DataChangeNotifier.removeListener(listener)
                                adminNotesViewModel.viewModelScope.cancel()
                                adminProductsViewModel.viewModelScope.cancel()
                                adminShoppingListViewModel.viewModelScope.cancel()
                                adminUsersViewModel.viewModelScope.cancel()
                                adminTransactionsViewModel.viewModelScope.cancel()
                                adminBulkBillingViewModel.viewModelScope.cancel()
                                adminVeggieViewModel.viewModelScope.cancel()
                                adminSettingsViewModel.viewModelScope.cancel()
                            }
                        }

                        val defaultSaveDir = remember {
                            val userHome = System.getProperty("user.home")
                            val downloads = java.io.File(userHome, "Downloads")
                            if (downloads.exists()) downloads.absolutePath else userHome
                        }
                        var isExportingShoppingListPdf by remember { mutableStateOf(false) }
                        var isExportingVeggiePdf by remember { mutableStateOf(false) }
                        var isExportingCsv by remember { mutableStateOf(false) }
                        var csvExportResultPath by remember { mutableStateOf<String?>(null) }
                        var csvExportError by remember { mutableStateOf<String?>(null) }

                        fun exportCsvToDefaultDir(
                            filenameBuilder: (dateStr: String) -> String,
                            fetchAndFormat: suspend () -> String
                        ) {
                            if (isExportingCsv) return
                            isExportingCsv = true
                            csvExportResultPath = null
                            csvExportError = null
                            val filename = filenameBuilder(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()))
                            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                try {
                                    val csvContent = fetchAndFormat()
                                    val targetDir = java.io.File(defaultSaveDir)
                                    if (!targetDir.exists()) targetDir.mkdirs()
                                    val targetFile = java.io.File(targetDir, filename)
                                    targetFile.writeText(csvContent, Charsets.UTF_8)
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        csvExportResultPath = targetFile.absolutePath
                                    }
                                } catch (e: kotlinx.coroutines.CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    AppLogger.error("App", "Failed to export CSV ($filename): ${e.message}", e)
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        csvExportError = e.message ?: strings.csvExportFailed
                                    }
                                } finally {
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        isExportingCsv = false
                                    }
                                }
                            }
                        }

                        de.joelneumann.lojinha.ui.screens.admin.AdminScreen(
                            notesViewModel = adminNotesViewModel,
                            productsViewModel = adminProductsViewModel,
                            shoppingListViewModel = adminShoppingListViewModel,
                            usersViewModel = adminUsersViewModel,
                            transactionsViewModel = adminTransactionsViewModel,
                            bulkBillingViewModel = adminBulkBillingViewModel,
                            veggieViewModel = adminVeggieViewModel,
                            settingsViewModel = adminSettingsViewModel,
                            defaultSaveDirectory = defaultSaveDir,
                            onExportShoppingListPdf = { language ->
                                if (!isExportingShoppingListPdf) {
                                    isExportingShoppingListPdf = true
                                    coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                        try {
                                            val items = effectiveShoppingListRepo.getAllItems()
                                            val products = effectiveProductRepo.getAllProducts()
                                            val targetDir = java.io.File(defaultSaveDir)
                                            if (!targetDir.exists()) targetDir.mkdirs()
                                            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                                            val targetFile = java.io.File(targetDir, PdfFilenames.shoppingListFilename(dateStr, language))
                                            de.joelneumann.lojinha.data.service.ShoppingListPdfGenerator.generatePdf(
                                                outputFile = targetFile,
                                                items = items,
                                                language = language,
                                                products = products
                                            )
                                            effectiveShoppingListRepo.commitExportBaseline()
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                adminShoppingListViewModel.setExportResultPath(targetFile.absolutePath)
                                            }
                                        } catch (e: kotlinx.coroutines.CancellationException) {
                                            throw e
                                        } catch (e: Exception) {
                                            AppLogger.error("App", "Failed to export Shopping List PDF: ${e.message}", e)
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                adminShoppingListViewModel.reportError(e.message ?: "Failed to export PDF")
                                            }
                                        } finally {
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                isExportingShoppingListPdf = false
                                            }
                                        }
                                    }
                                }
                            },
                            onExecutePdfExport = { user, destinationFolder, fromTimestamp, toTimestamp, language ->
                                if (!adminUsersViewModel.isExportingPdf.value) {
                                    adminUsersViewModel.setIsExportingPdf(true)
                                    adminUsersViewModel.setExportPdfError(null)
                                    coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                        try {
                                            val transactions = effectiveTransactionRepo.getTransactionsByUserId(user.id)
                                            val dateSuffix = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
                                            val targetDir = java.io.File(destinationFolder)
                                            if (!targetDir.exists()) {
                                                targetDir.mkdirs()
                                            }
                                            val targetFileName = PdfFilenames.transactionStatementFilename(user.name, language, dateSuffix)
                                            val targetFile = java.io.File(targetDir, targetFileName)
                                            de.joelneumann.lojinha.data.service.TransactionPdfGenerator.generateStatementPdf(
                                                outputFile = targetFile,
                                                user = user,
                                                transactions = transactions,
                                                fromTimestamp = fromTimestamp,
                                                toTimestamp = toTimestamp,
                                                language = language
                                            )
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                adminUsersViewModel.setExportResultPath(targetFile.absolutePath)
                                            }
                                        } catch (e: Exception) {
                                            AppLogger.error("App", "Failed to export PDF statement: ${e.message}", e)
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                adminUsersViewModel.setExportResultPath(null)
                                                adminUsersViewModel.setExportPdfError(e.message ?: "Failed to export PDF statement")
                                            }
                                        } finally {
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                adminUsersViewModel.setIsExportingPdf(false)
                                            }
                                        }
                                    }
                                }
                            },
                            onExportVeggieBuyerPdf = { roundId, language ->
                                if (!isExportingVeggiePdf) {
                                    isExportingVeggiePdf = true
                                    adminVeggieViewModel.setExportError(null)
                                    coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                        try {
                                            val round = effectiveVeggieRepo.getRoundById(roundId)
                                                ?: throw IllegalStateException("Round $roundId not found")
                                            val entries = effectiveVeggieRepo.getEntriesForRound(roundId)
                                            val products = effectiveVeggieRepo.getAllVeggieProducts()
                                            val weekInfo = de.joelneumann.lojinha.ui.utils.IsoWeekUtils.fromEpochMillis(round.openedAt)
                                            val finalFile = writeVeggiePdfWithFallback(
                                                defaultSaveDir = defaultSaveDir,
                                                label = "Veggie Buyer Shopping List PDF",
                                                primaryFilename = PdfFilenames.buyerShoppingListFilename(weekInfo.weekOfYear, weekInfo.isoYear, language),
                                                fallbackFilename = { ts -> PdfFilenames.buyerShoppingListFilename(weekInfo.weekOfYear, weekInfo.isoYear, language, ts) }
                                            ) { outFile ->
                                                de.joelneumann.lojinha.data.service.VeggieOrderPdfGenerator.generateBuyerShoppingListPdf(
                                                    outputFile = outFile,
                                                    round = round,
                                                    entries = entries,
                                                    language = language,
                                                    products = products
                                                )
                                            }
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                adminVeggieViewModel.markPdfExported(roundId, isBuyer = true)
                                                adminVeggieViewModel.setExportResultPath(finalFile.absolutePath)
                                            }
                                        } catch (e: Exception) {
                                            AppLogger.error("App", "Failed to export Veggie Buyer Shopping List PDF: ${e.message}", e)
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                adminVeggieViewModel.setExportError(e.message ?: "Failed to export Veggie Buyer Shopping List PDF")
                                            }
                                        } finally {
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                isExportingVeggiePdf = false
                                            }
                                        }
                                    }
                                }
                            },
                            onExportVeggieWorkerPdf = { roundId, language ->
                                if (!isExportingVeggiePdf) {
                                    isExportingVeggiePdf = true
                                    adminVeggieViewModel.setExportError(null)
                                    coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                        try {
                                            val round = effectiveVeggieRepo.getRoundById(roundId)
                                                ?: throw IllegalStateException("Round $roundId not found")
                                            val entries = effectiveVeggieRepo.getEntriesForRound(roundId)
                                            val users = userRepository?.getAllUsers() ?: emptyList()
                                            val weekInfo = de.joelneumann.lojinha.ui.utils.IsoWeekUtils.fromEpochMillis(round.openedAt)
                                            val finalFile = writeVeggiePdfWithFallback(
                                                defaultSaveDir = defaultSaveDir,
                                                label = "Veggie Worker Sorting Matrix PDF",
                                                primaryFilename = PdfFilenames.workerSortingMatrixFilename(weekInfo.weekOfYear, weekInfo.isoYear, language),
                                                fallbackFilename = { ts -> PdfFilenames.workerSortingMatrixFilename(weekInfo.weekOfYear, weekInfo.isoYear, language, ts) }
                                            ) { outFile ->
                                                de.joelneumann.lojinha.data.service.VeggieOrderPdfGenerator.generateWorkerSortingMatrixPdf(
                                                    outputFile = outFile,
                                                    round = round,
                                                    entries = entries,
                                                    language = language,
                                                    users = users
                                                )
                                            }
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                adminVeggieViewModel.markPdfExported(roundId, isBuyer = false)
                                                adminVeggieViewModel.setExportResultPath(finalFile.absolutePath)
                                            }
                                        } catch (e: Exception) {
                                            AppLogger.error("App", "Failed to export Veggie Worker Sorting Matrix PDF: ${e.message}", e)
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                adminVeggieViewModel.setExportError(e.message ?: "Failed to export Veggie Worker Sorting Matrix PDF")
                                            }
                                        } finally {
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                isExportingVeggiePdf = false
                                            }
                                        }
                                    }
                                }
                            },
                            onExportVeggieOrderFormPdf = { language ->
                                if (!isExportingVeggiePdf) {
                                    isExportingVeggiePdf = true
                                    adminVeggieViewModel.setExportError(null)
                                    coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                        try {
                                            val products = effectiveVeggieRepo.getAllVeggieProducts()
                                            val unitTypes = effectiveVeggieRepo.getAllUnitTypes().associateBy { it.id }
                                            val dateStamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                                            val finalFile = writeVeggiePdfWithFallback(
                                                defaultSaveDir = defaultSaveDir,
                                                label = "Veggie Customer Order Form PDF",
                                                primaryFilename = PdfFilenames.customerOrderFormFilename(dateStamp, language),
                                                fallbackFilename = { ts -> PdfFilenames.customerOrderFormFilename(dateStamp, language, ts) }
                                            ) { outFile ->
                                                de.joelneumann.lojinha.data.service.VeggieOrderPdfGenerator.generateCustomerOrderFormPdf(
                                                    outputFile = outFile,
                                                    products = products,
                                                    unitMap = unitTypes,
                                                    language = language
                                                )
                                            }
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                adminVeggieViewModel.setExportResultPath(finalFile.absolutePath)
                                            }
                                        } catch (e: Exception) {
                                            AppLogger.error("App", "Failed to export Veggie Customer Order Form PDF: ${e.message}", e)
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                adminVeggieViewModel.setExportError(e.message ?: "Failed to export Veggie Customer Order Form PDF")
                                            }
                                        } finally {
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                isExportingVeggiePdf = false
                                            }
                                        }
                                    }
                                }
                            },
                            onOpenExportedPdf = { filePath ->
                                try {
                                    val file = java.io.File(filePath)
                                    if (file.exists()) {
                                        val os = System.getProperty("os.name").lowercase(java.util.Locale.US)
                                        when {
                                            os.contains("mac") -> {
                                                ProcessBuilder("open", file.absolutePath).start()
                                            }
                                            os.contains("win") -> {
                                                ProcessBuilder("cmd.exe", "/c", "start", "\"\"", file.absolutePath).start()
                                            }
                                            else -> {
                                                ProcessBuilder("xdg-open", file.absolutePath).start()
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    AppLogger.warn("App", "Failed to open PDF in system viewer: ${e.message}", e)
                                    try {
                                        val file = java.io.File(filePath)
                                        if (file.exists() && java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.OPEN)) {
                                            java.awt.Desktop.getDesktop().open(file)
                                        }
                                    } catch (_: Exception) {}
                                }
                            },
                            onRevealExportedFolder = { filePath ->
                                try {
                                    val file = java.io.File(filePath)
                                    diagnosticsService.revealInFileExplorer(file)
                                } catch (e: Exception) {
                                    AppLogger.warn("App", "Failed to reveal PDF in folder: ${e.message}", e)
                                }
                            },
                            onExportNotesCsv = {
                                exportCsvToDefaultDir(de.joelneumann.lojinha.data.service.CsvExportFilenames::notesFilename) {
                                    val notes = effectiveAdminNoteRepo.getAllNotes()
                                    de.joelneumann.lojinha.data.service.CsvExportFormatter.formatAdminNotesCsv(notes)
                                }
                            },
                            onExportProductsCsv = {
                                exportCsvToDefaultDir(de.joelneumann.lojinha.data.service.CsvExportFilenames::productsFilename) {
                                    val products = effectiveProductRepo.getAllProducts()
                                    de.joelneumann.lojinha.data.service.CsvExportFormatter.formatProductsCsv(products)
                                }
                            },
                            onExportProductHistoryCsv = { product ->
                                exportCsvToDefaultDir({ dateStr ->
                                    de.joelneumann.lojinha.data.service.CsvExportFilenames.productHistoryFilename(product.name, dateStr)
                                }) {
                                    val entries = effectiveProductRepo.getProductHistory(product.id, page = 0, pageSize = Int.MAX_VALUE).items
                                    de.joelneumann.lojinha.data.service.CsvExportFormatter.formatProductHistoryCsv(entries)
                                }
                            },
                            onExportUsersCsv = {
                                exportCsvToDefaultDir(de.joelneumann.lojinha.data.service.CsvExportFilenames::usersFilename) {
                                    val users = effectiveUserRepo.getAllUsers()
                                    de.joelneumann.lojinha.data.service.CsvExportFormatter.formatUsersCsv(users)
                                }
                            },
                            onExportUserTransactionsCsv = { user ->
                                exportCsvToDefaultDir({ dateStr ->
                                    de.joelneumann.lojinha.data.service.CsvExportFilenames.userTransactionsFilename(user.name, dateStr)
                                }) {
                                    val txs = effectiveTransactionRepo.getTransactionsByUserId(user.id)
                                    de.joelneumann.lojinha.data.service.CsvExportFormatter.formatTransactionsCsv(txs)
                                }
                            },
                            onExportTransactionsCsv = {
                                exportCsvToDefaultDir(de.joelneumann.lojinha.data.service.CsvExportFilenames::transactionsFilename) {
                                    val txs = effectiveTransactionRepo.getAllTransactions()
                                    de.joelneumann.lojinha.data.service.CsvExportFormatter.formatTransactionsCsv(txs)
                                }
                            },
                            onExportVeggieProductsCsv = {
                                exportCsvToDefaultDir(de.joelneumann.lojinha.data.service.CsvExportFilenames::veggieProductsFilename) {
                                    val products = effectiveVeggieRepo.getAllVeggieProducts()
                                    val unitTypes = effectiveVeggieRepo.getAllUnitTypes()
                                    de.joelneumann.lojinha.data.service.CsvExportFormatter.formatVeggieProductsCsv(products, unitTypes)
                                }
                            },
                            onExportVeggieOrdersCsv = {
                                exportCsvToDefaultDir(de.joelneumann.lojinha.data.service.CsvExportFilenames::veggieOrdersFilename) {
                                    val rounds = effectiveVeggieRepo.getAllRounds()
                                    val entriesByRoundId = rounds.associate { r -> r.id to effectiveVeggieRepo.getEntriesForRound(r.id) }
                                    val settlementsByRoundId = rounds.associate { r -> r.id to effectiveVeggieRepo.getProductSettlementsForRound(r.id) }
                                    de.joelneumann.lojinha.data.service.CsvExportFormatter.formatVeggieOrdersCsv(
                                        rounds = rounds,
                                        entriesByRoundId = entriesByRoundId,
                                        settlementsByRoundId = settlementsByRoundId
                                    )
                                }
                            },
                            scaleService = scaleService,
                            onExitAdmin = {
                                appViewModel.navigateTo(AppScreen.MAIN_USER_SELECT)
                            }
                        )

                        if (csvExportResultPath != null || csvExportError != null) {
                            val savedPath = csvExportResultPath
                            val err = csvExportError
                            androidx.compose.material3.AlertDialog(
                                onDismissRequest = {
                                    csvExportResultPath = null
                                    csvExportError = null
                                },
                                title = {
                                    androidx.compose.material3.Text(
                                        text = if (err != null) strings.csvExportFailed else strings.csvExportSuccessTitle,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                        color = if (err != null) ColorDangerCrimson else PrimaryNavy
                                    )
                                },
                                text = {
                                    androidx.compose.foundation.layout.Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                                        if (err != null) {
                                            androidx.compose.material3.Text(err, color = ColorDangerCrimson, fontSize = 13.sp)
                                        } else if (savedPath != null) {
                                            androidx.compose.material3.Text(strings.csvExportSavedTo, fontSize = 12.sp, color = TextSecondaryMuted)
                                            androidx.compose.material3.Text(savedPath, fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, color = PrimaryNavy)
                                        }
                                    }
                                },
                                confirmButton = {
                                    androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                                        if (savedPath != null) {
                                            androidx.compose.material3.OutlinedButton(
                                                onClick = {
                                                    try {
                                                        diagnosticsService.revealInFileExplorer(java.io.File(savedPath))
                                                    } catch (_: Exception) {}
                                                }
                                            ) {
                                                androidx.compose.material3.Text(strings.revealInFolder)
                                            }
                                        }
                                        androidx.compose.material3.Button(
                                            onClick = {
                                                csvExportResultPath = null
                                                csvExportError = null
                                            },
                                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = AccentNavy)
                                        ) {
                                            androidx.compose.material3.Text(strings.confirm, color = SurfaceWhite)
                                        }
                                    }
                                }
                            )
                        }
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

            // Guarded Exit Confirmation Dialog (e.g. from Alt+F4 or window close request)
            if (showExitAuthDialog) {
                var exitPasswordInput by remember { mutableStateOf("") }
                var exitPasswordError by remember { mutableStateOf<String?>(null) }
                PasswordInputDialog(
                    title = strings.exitKioskConfirmTitle,
                    promptText = strings.exitKioskConfirmMessage,
                    inputValue = exitPasswordInput,
                    onValueChange = {
                        exitPasswordInput = it
                        exitPasswordError = null
                    },
                    onDismiss = {
                        exitPasswordInput = ""
                        exitPasswordError = null
                        onDismissExitAuthDialog()
                    },
                    onSubmit = {
                        val isCorrect = PasswordHasher.verifyAdminBypass(
                            exitPasswordInput,
                            settings.adminPasswordHash
                        )
                        if (isCorrect) {
                            onDismissExitAuthDialog()
                            onExitApplication?.invoke()
                        } else {
                            exitPasswordError = strings.incorrectAdminPassword
                            exitPasswordInput = ""
                        }
                    },
                    errorText = exitPasswordError
                )
            }
        }
    }
}

private fun writeVeggiePdfWithFallback(
    defaultSaveDir: String,
    label: String,
    primaryFilename: String,
    fallbackFilename: (String) -> String,
    writePdf: (java.io.File) -> Unit
): java.io.File {
    val targetDir = java.io.File(defaultSaveDir)
    if (!targetDir.exists()) targetDir.mkdirs()
    val targetFile = java.io.File(targetDir, primaryFilename)
    return try {
        writePdf(targetFile)
        targetFile
    } catch (e: Exception) {
        AppLogger.warn("App", "Primary $label export to ${targetFile.absolutePath} failed (${e.message}), trying fallback", e)
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fallbackName = fallbackFilename(timeStamp)
        val fallbackFile = if (targetDir.canWrite()) {
            java.io.File(targetDir, fallbackName)
        } else {
            java.io.File(System.getProperty("user.home"), fallbackName)
        }
        writePdf(fallbackFile)
        fallbackFile
    }
}