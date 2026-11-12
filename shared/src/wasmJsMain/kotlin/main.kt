import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.CanvasBasedWindow
import de.joelneumann.lojinha.data.repository.*
import de.joelneumann.lojinha.domain.model.SystemSettings
import de.joelneumann.lojinha.domain.model.User
import de.joelneumann.lojinha.ui.i18n.LanguageManager
import de.joelneumann.lojinha.ui.screens.CustomerNetworkPortalScreen
import de.joelneumann.lojinha.ui.screens.UserSelectionScreen
import de.joelneumann.lojinha.ui.screens.admin.AdminScreen
import de.joelneumann.lojinha.ui.theme.LojinhaTheme
import de.joelneumann.lojinha.ui.theme.WEB_UI_SCALE_FACTOR
import de.joelneumann.lojinha.ui.utils.PdfFilenames
import de.joelneumann.lojinha.ui.viewmodel.CustomerVeggieViewModel
import de.joelneumann.lojinha.ui.viewmodel.TransactionHistoryViewModel
import de.joelneumann.lojinha.ui.viewmodel.UserSelectionViewModel
import de.joelneumann.lojinha.ui.viewmodel.admin.*
import kotlinx.coroutines.launch

private enum class WebPortalScreen {
    USER_SELECTION,
    CUSTOMER_PORTAL,
    ADMIN_PANEL
}

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    CanvasBasedWindow("Lojinha Portal") {
        var currentScreen by remember { mutableStateOf(WebPortalScreen.USER_SELECTION) }
        var loggedInCustomer by remember { mutableStateOf<User?>(null) }
        var isCheckingSession by remember { mutableStateOf(true) }
        val scope = rememberCoroutineScope()

        val adminNetworkClient = remember { AdminNetworkClient() }
        val productRepository = remember { HttpProductRepository(adminNetworkClient) }
        val userRepository = remember { HttpUserRepository(adminNetworkClient) }
        val transactionRepository = remember { HttpTransactionRepository(adminNetworkClient) }
        val billingListRepository = remember { HttpBillingListRepository(adminNetworkClient) }
        val adminNoteRepository = remember { HttpAdminNoteRepository(adminNetworkClient) }
        val veggieOrderRepository = remember { HttpVeggieOrderRepository(adminNetworkClient) }

        val customerNetworkClient = remember { CustomerNetworkClient() }
        val customerUserRepository = remember { HttpCustomerUserRepository(customerNetworkClient) }
        val customerTransactionRepository = remember { HttpCustomerTransactionRepository(customerNetworkClient) }
        val defaultSettings = remember { SystemSettings() }

        // Session restoration on startup (Admin or Customer)
        LaunchedEffect(Unit) {
            val adminRestored = adminNetworkClient.tryRestoreSession()
            if (adminRestored) {
                currentScreen = WebPortalScreen.ADMIN_PANEL
            } else {
                val restoredUser = customerNetworkClient.tryRestoreCustomerSession()
                if (restoredUser != null) {
                    loggedInCustomer = restoredUser
                    LanguageManager.setLanguage(restoredUser.language)
                    currentScreen = WebPortalScreen.CUSTOMER_PORTAL
                }
            }
            isCheckingSession = false
        }

        LojinhaTheme(scaleFactor = WEB_UI_SCALE_FACTOR) {
            if (isCheckingSession) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                Crossfade(
                    targetState = currentScreen,
                    animationSpec = tween(durationMillis = 250),
                    modifier = Modifier.fillMaxSize()
                ) { targetScreen ->
                    when (targetScreen) {
                        WebPortalScreen.USER_SELECTION -> {
                            val userSelectionViewModel = remember {
                                UserSelectionViewModel(
                                    userRepository = customerUserRepository,
                                    productRepository = null,
                                    isNetworkPortal = true,
                                    onAuthenticateNetworkUser = { userId, pin ->
                                        when (val res = customerNetworkClient.loginCustomer(userId, pin)) {
                                            is CustomerLoginResult.Success -> res.user
                                            else -> null
                                        }
                                    },
                                    onAuthenticateNetworkAdmin = { password ->
                                        when (adminNetworkClient.login(password)) {
                                            is LoginResult.Success -> true
                                            else -> false
                                        }
                                    }
                                )
                            }

                            LaunchedEffect(Unit) {
                                userSelectionViewModel.resetState()
                                userSelectionViewModel.loadUsers()
                            }

                            UserSelectionScreen(
                                viewModel = userSelectionViewModel,
                                settings = defaultSettings,
                                onUserLoggedIn = { user ->
                                    loggedInCustomer = user
                                    LanguageManager.setLanguage(user.language)
                                    currentScreen = WebPortalScreen.CUSTOMER_PORTAL
                                },
                                onNavigateToAdmin = {
                                    currentScreen = WebPortalScreen.ADMIN_PANEL
                                }
                            )
                        }

                        WebPortalScreen.CUSTOMER_PORTAL -> {
                            val activeCustomer = loggedInCustomer
                            if (activeCustomer == null) {
                                LaunchedEffect(Unit) {
                                    currentScreen = WebPortalScreen.USER_SELECTION
                                }
                            } else {
                                val customerVeggieViewModel = remember(activeCustomer.id) {
                                    CustomerVeggieViewModel(
                                        onFetchVeggieState = { customerNetworkClient.getVeggieState() },
                                        onSaveOrder = { roundId, quantities ->
                                            customerNetworkClient.saveCustomerOrder(roundId, quantities)
                                        },
                                        onFetchHistory = { customerNetworkClient.getVeggieHistory() },
                                        initialFavoriteProductIds = loadUserFavorites(activeCustomer.id),
                                        onFavoritesChanged = { favorites ->
                                            saveUserFavorites(activeCustomer.id, favorites)
                                        },
                                        onSaveFavorites = { favorites ->
                                            customerNetworkClient.saveVeggieFavorites(favorites)
                                        }
                                    )
                                }
                                val historyViewModel = remember(activeCustomer.id) {
                                    TransactionHistoryViewModel(
                                        transactionRepository = customerTransactionRepository,
                                        userRepository = customerUserRepository
                                    )
                                }

                                LaunchedEffect(activeCustomer.id) {
                                    customerNetworkClient.startRealtimeSync(this)
                                    launch {
                                        customerNetworkClient.onDataChanged.collect {
                                            customerNetworkClient.fetchCurrentUser()?.let { refreshed ->
                                                loggedInCustomer = refreshed
                                            }
                                            customerVeggieViewModel.loadData(preserveDraftIfSameRound = true)
                                            historyViewModel.loadUserTransactions(activeCustomer.id, resetFilters = false)
                                        }
                                    }
                                    launch {
                                        customerNetworkClient.onUnauthorized.collect {
                                            loggedInCustomer = null
                                            LanguageManager.resetToDefault()
                                            currentScreen = WebPortalScreen.USER_SELECTION
                                        }
                                    }
                                }

                                DisposableEffect(activeCustomer.id) {
                                    onDispose {
                                        customerNetworkClient.stopRealtimeSync()
                                    }
                                }

                                CustomerNetworkPortalScreen(
                                    user = activeCustomer,
                                    settings = defaultSettings,
                                    veggieViewModel = customerVeggieViewModel,
                                    historyViewModel = historyViewModel,
                                    onUserUpdated = { updated ->
                                        loggedInCustomer = updated
                                        LanguageManager.setLanguage(updated.language)
                                        scope.launch {
                                            customerUserRepository.saveUser(updated)
                                            customerNetworkClient.fetchCurrentUser()?.let { refreshed ->
                                                loggedInCustomer = refreshed
                                            }
                                        }
                                    },
                                    onLogout = {
                                        scope.launch {
                                            customerNetworkClient.logout()
                                            loggedInCustomer = null
                                            LanguageManager.resetToDefault()
                                            currentScreen = WebPortalScreen.USER_SELECTION
                                        }
                                    },
                                    onExecuteCustomerPdfExport = { customer, fromTimestamp, toTimestamp, language ->
                                        historyViewModel.setIsExportingPdf(true)
                                        val token = customerNetworkClient.authToken ?: ""
                                        val fromQuery = if (fromTimestamp != null) "&from=$fromTimestamp" else ""
                                        val toQuery = if (toTimestamp != null) "&to=$toTimestamp" else ""
                                        val url = customerNetworkClient.resolveUrl("/api/customer/transactions-pdf?lang=${language.name}&token=$token$fromQuery$toQuery")
                                        jsTriggerDownload(url)
                                        historyViewModel.setIsExportingPdf(false)
                                        historyViewModel.closeExportPdfModal()
                                    }
                                )
                            }
                        }

                        WebPortalScreen.ADMIN_PANEL -> {
                            val notesViewModel = remember { AdminNotesViewModel(adminNoteRepository) }
                            val productsViewModel = remember { AdminProductsViewModel(productRepository) }
                            val usersViewModel = remember { AdminUsersViewModel(userRepository, transactionRepository) }
                            val transactionsViewModel = remember { AdminTransactionsViewModel(transactionRepository, userRepository, productRepository) }
                            val bulkBillingViewModel = remember { AdminBulkBillingViewModel(billingListRepository, userRepository, transactionRepository) }
                            val veggieViewModel = remember {
                                AdminVeggieViewModel(
                                    veggieOrderRepository = veggieOrderRepository,
                                    userRepository = userRepository,
                                    onSaveProductImage = { productId, productName, bytes, _ ->
                                        veggieOrderRepository.uploadProductImage(productId, productName, bytes)
                                    },
                                    onDeleteProductImage = { productId, _ ->
                                        veggieOrderRepository.deleteProductImage(productId)
                                    }
                                )
                            }

                            LaunchedEffect(Unit) {
                                adminNetworkClient.startRealtimeSync(this)
                                launch {
                                    adminNetworkClient.onDataChanged.collect {
                                        notesViewModel.loadNotes()
                                        productsViewModel.loadData()
                                        usersViewModel.loadData()
                                        transactionsViewModel.loadData()
                                        bulkBillingViewModel.loadData()
                                        veggieViewModel.loadData()
                                    }
                                }
                                launch {
                                    adminNetworkClient.onUnauthorized.collect {
                                        currentScreen = WebPortalScreen.USER_SELECTION
                                    }
                                }
                            }

                            DisposableEffect(Unit) {
                                onDispose {
                                    adminNetworkClient.stopRealtimeSync()
                                }
                            }

                            AdminScreen(
                                notesViewModel = notesViewModel,
                                productsViewModel = productsViewModel,
                                usersViewModel = usersViewModel,
                                transactionsViewModel = transactionsViewModel,
                                bulkBillingViewModel = bulkBillingViewModel,
                                veggieViewModel = veggieViewModel,
                                settingsViewModel = null,
                                defaultSaveDirectory = "Downloads",
                                onExecutePdfExport = { user, _, fromTimestamp, toTimestamp, language ->
                                    usersViewModel.setIsExportingPdf(true)
                                    val token = adminNetworkClient.authToken ?: ""
                                    val fromQuery = if (fromTimestamp != null) "&from=$fromTimestamp" else ""
                                    val toQuery = if (toTimestamp != null) "&to=$toTimestamp" else ""
                                    val url = adminNetworkClient.resolveUrl("/api/admin/users/${user.id}/transactions-pdf?lang=${language.name}&token=$token$fromQuery$toQuery")
                                    jsTriggerDownload(url)
                                    usersViewModel.setIsExportingPdf(false)
                                    usersViewModel.setExportResultPath(PdfFilenames.transactionStatementFilename(user.name, language))
                                },
                                onExportVeggieBuyerPdf = { roundId, language ->
                                    val token = adminNetworkClient.authToken ?: ""
                                    val url = adminNetworkClient.resolveUrl("/api/admin/veggie/rounds/$roundId/buyer-pdf?lang=${language.name}&token=$token")
                                    jsTriggerDownload(url)
                                    veggieViewModel.markPdfExported(roundId, isBuyer = true)
                                },
                                onExportVeggieWorkerPdf = { roundId, language ->
                                    val token = adminNetworkClient.authToken ?: ""
                                    val url = adminNetworkClient.resolveUrl("/api/admin/veggie/rounds/$roundId/worker-pdf?lang=${language.name}&token=$token")
                                    jsTriggerDownload(url)
                                    veggieViewModel.markPdfExported(roundId, isBuyer = false)
                                },
                                onExportVeggieOrderFormPdf = { language ->
                                    val token = adminNetworkClient.authToken ?: ""
                                    val url = adminNetworkClient.resolveUrl("/api/admin/veggie/order-form-pdf?lang=${language.name}&token=$token")
                                    jsTriggerDownload(url)
                                },
                                onExitAdmin = {
                                    scope.launch {
                                        adminNetworkClient.logout()
                                        currentScreen = WebPortalScreen.USER_SELECTION
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@JsFun("""
(url) => {
    const a = document.createElement('a');
    a.href = url;
    a.target = '_blank';
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
}
""")
private external fun jsTriggerDownload(url: String)

@JsFun("""
(key, value) => {
    try {
        window.localStorage.setItem(key, value);
    } catch (e) {
        console.warn('Failed to set localStorage item:', e);
    }
}
""")
private external fun jsSetLocalItem(key: String, value: String)

@JsFun("""
(key) => {
    try {
        return window.localStorage.getItem(key);
    } catch (e) {
        console.warn('Failed to get localStorage item:', e);
        return null;
    }
}
""")
private external fun jsGetLocalItem(key: String): String?

private fun loadUserFavorites(userId: String): Set<String> {
    return try {
        val raw = jsGetLocalItem("lojinha_veggie_favorites_$userId") ?: return emptySet()
        raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    } catch (_: Exception) {
        emptySet()
    }
}

private fun saveUserFavorites(userId: String, favorites: Set<String>) {
    try {
        jsSetLocalItem("lojinha_veggie_favorites_$userId", favorites.joinToString(","))
    } catch (_: Exception) {}
}
