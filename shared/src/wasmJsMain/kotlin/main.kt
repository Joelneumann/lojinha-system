import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.CanvasBasedWindow
import androidx.lifecycle.viewModelScope
import de.joelneumann.lojinha.data.repository.*
import de.joelneumann.lojinha.domain.model.SystemSettings
import de.joelneumann.lojinha.domain.model.User
import de.joelneumann.lojinha.ui.i18n.I18n
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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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
        var adminSessionRestartNotice by remember { mutableStateOf<String?>(null) }
        val isServerReachable by KioskConnectionMonitor.isServerReachable.collectAsState()
        val scope = rememberCoroutineScope()

        val adminNetworkClient = remember { AdminNetworkClient() }
        val productRepository = remember { HttpProductRepository(adminNetworkClient) }
        val userRepository = remember { HttpUserRepository(adminNetworkClient) }
        val transactionRepository = remember { HttpTransactionRepository(adminNetworkClient) }
        val billingListRepository = remember { HttpBillingListRepository(adminNetworkClient) }
        val adminNoteRepository = remember { HttpAdminNoteRepository(adminNetworkClient) }
        val veggieOrderRepository = remember { HttpVeggieOrderRepository(adminNetworkClient) }
        val settingsRepository = remember { HttpSettingsRepository(adminNetworkClient) }

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
            Box(modifier = Modifier.fillMaxSize()) {
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
                                        onAuthenticateNetworkUser = { userId, pin, keepLoggedIn ->
                                            when (val res = customerNetworkClient.loginCustomer(userId, pin, keepLoggedIn)) {
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
                                    launch {
                                        KioskConnectionMonitor.onReconnected.collect {
                                            userSelectionViewModel.loadUsers()
                                        }
                                    }
                                }

                                Box(modifier = Modifier.fillMaxSize()) {
                                    UserSelectionScreen(
                                        viewModel = userSelectionViewModel,
                                        settings = defaultSettings,
                                        onUserLoggedIn = { user ->
                                            adminSessionRestartNotice = null
                                            loggedInCustomer = user
                                            LanguageManager.setLanguage(user.language)
                                            currentScreen = WebPortalScreen.CUSTOMER_PORTAL
                                        },
                                        onNavigateToAdmin = {
                                            adminSessionRestartNotice = null
                                            currentScreen = WebPortalScreen.ADMIN_PANEL
                                        }
                                    )

                                    adminSessionRestartNotice?.let { notice ->
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = Color(0xFFFEF3C7),
                                            border = BorderStroke(1.dp, Color(0xFFF59E0B)),
                                            shadowElevation = 6.dp,
                                            modifier = Modifier
                                                .align(Alignment.TopCenter)
                                                .padding(16.dp)
                                                .widthIn(max = 560.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Info,
                                                    contentDescription = null,
                                                    tint = Color(0xFFB45309)
                                                )
                                                Text(
                                                    text = notice,
                                                    color = Color(0xFF78350F),
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                IconButton(
                                                    onClick = { adminSessionRestartNotice = null },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = null,
                                                        tint = Color(0xFF78350F)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
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
                                        },
                                        onLoadDraft = { roundId ->
                                            loadCustomerVeggieDraft(activeCustomer.id, roundId)
                                        },
                                        onSaveDraft = { roundId, quantities ->
                                            saveCustomerVeggieDraft(activeCustomer.id, roundId, quantities)
                                        },
                                        onClearDraft = { roundId ->
                                            clearCustomerVeggieDraft(activeCustomer.id, roundId)
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
                                    launch {
                                        KioskConnectionMonitor.onReconnected.collect {
                                            customerNetworkClient.fetchCurrentUser()?.let { refreshed ->
                                                loggedInCustomer = refreshed
                                            }
                                            customerVeggieViewModel.loadData(preserveDraftIfSameRound = true)
                                            historyViewModel.loadUserTransactions(activeCustomer.id, resetFilters = false)
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
                                        historyViewModel.setExportPdfError(null)
                                        val token = customerNetworkClient.authToken ?: ""
                                        val fromQuery = if (fromTimestamp != null) "&from=$fromTimestamp" else ""
                                        val toQuery = if (toTimestamp != null) "&to=$toTimestamp" else ""
                                        val url = customerNetworkClient.resolveUrl("/api/customer/transactions-pdf?lang=${language.name}$fromQuery$toQuery")
                                        val fallbackName = PdfFilenames.transactionStatementFilename(customer.name, language)
                                        jsDownloadPdf(
                                            url = url,
                                            token = token,
                                            fallbackFilename = fallbackName,
                                            onSuccess = {
                                                historyViewModel.setIsExportingPdf(false)
                                                historyViewModel.closeExportPdfModal()
                                            },
                                            onError = { err ->
                                                historyViewModel.setIsExportingPdf(false)
                                                historyViewModel.setExportPdfError(err)
                                            }
                                        )
                                    }
                                )
                            }
                        }

                        WebPortalScreen.ADMIN_PANEL -> {
                            val notesViewModel = remember { AdminNotesViewModel(adminNoteRepository) }
                            val productsViewModel = remember { AdminProductsViewModel(productRepository, settingsRepository) }
                            val usersViewModel = remember { AdminUsersViewModel(userRepository, transactionRepository) }
                            val transactionsViewModel = remember { AdminTransactionsViewModel(transactionRepository, userRepository, productRepository) }
                            val bulkBillingViewModel = remember { AdminBulkBillingViewModel(billingListRepository, userRepository, transactionRepository) }
                            val veggieViewModel = remember {
                                AdminVeggieViewModel(
                                    veggieOrderRepository = veggieOrderRepository,
                                    userRepository = userRepository,
                                    settingsRepository = settingsRepository,
                                    onSaveProductImage = { productId, productName, bytes, _ ->
                                        veggieOrderRepository.uploadProductImage(productId, productName, bytes)
                                    },
                                    onDeleteProductImage = { productId, _ ->
                                        veggieOrderRepository.deleteProductImage(productId)
                                    },
                                    onLoadMatrixDraft = { roundId ->
                                        loadAdminVeggieMatrixDraft(roundId)
                                    },
                                    onSaveMatrixDraft = { roundId, draft ->
                                        saveAdminVeggieMatrixDraft(roundId, draft)
                                    },
                                    onClearMatrixDraft = { roundId ->
                                        clearAdminVeggieMatrixDraft(roundId)
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
                                        adminSessionRestartNotice = I18n.get().adminSessionRestartedNotice
                                        currentScreen = WebPortalScreen.USER_SELECTION
                                    }
                                }
                                launch {
                                    KioskConnectionMonitor.onReconnected.collect {
                                        adminNetworkClient.verifySessionStillValid()
                                        if (!adminNetworkClient.authToken.isNullOrBlank()) {
                                            notesViewModel.loadNotes()
                                            productsViewModel.loadData()
                                            usersViewModel.loadData()
                                            transactionsViewModel.loadData()
                                            bulkBillingViewModel.loadData()
                                            veggieViewModel.loadData()
                                        }
                                    }
                                }
                            }

                            DisposableEffect(Unit) {
                                onDispose {
                                    adminNetworkClient.stopRealtimeSync()
                                    notesViewModel.viewModelScope.cancel()
                                    productsViewModel.viewModelScope.cancel()
                                    usersViewModel.viewModelScope.cancel()
                                    transactionsViewModel.viewModelScope.cancel()
                                    bulkBillingViewModel.viewModelScope.cancel()
                                    veggieViewModel.viewModelScope.cancel()
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
                                    usersViewModel.setExportPdfError(null)
                                    val token = adminNetworkClient.authToken ?: ""
                                    val fromQuery = if (fromTimestamp != null) "&from=$fromTimestamp" else ""
                                    val toQuery = if (toTimestamp != null) "&to=$toTimestamp" else ""
                                    val url = adminNetworkClient.resolveUrl("/api/admin/users/${user.id}/transactions-pdf?lang=${language.name}$fromQuery$toQuery")
                                    val fallbackName = PdfFilenames.transactionStatementFilename(user.name, language)
                                    jsDownloadPdf(
                                        url = url,
                                        token = token,
                                        fallbackFilename = fallbackName,
                                        onSuccess = {
                                            usersViewModel.setIsExportingPdf(false)
                                            usersViewModel.closeExportPdfModal()
                                        },
                                        onError = { err ->
                                            usersViewModel.setIsExportingPdf(false)
                                            usersViewModel.setExportPdfError(err)
                                        }
                                    )
                                },
                                onExportVeggieBuyerPdf = { roundId, language ->
                                    val token = adminNetworkClient.authToken ?: ""
                                    val url = adminNetworkClient.resolveUrl("/api/admin/veggie/rounds/$roundId/buyer-pdf?lang=${language.name}")
                                    jsDownloadPdf(
                                        url = url,
                                        token = token,
                                        fallbackFilename = "buyer_shopping_list.pdf",
                                        onSuccess = { veggieViewModel.loadData(preserveDraftEdits = true) },
                                        onError = { err -> veggieViewModel.setExportError(err) }
                                    )
                                },
                                onExportVeggieWorkerPdf = { roundId, language ->
                                    val token = adminNetworkClient.authToken ?: ""
                                    val url = adminNetworkClient.resolveUrl("/api/admin/veggie/rounds/$roundId/worker-pdf?lang=${language.name}")
                                    jsDownloadPdf(
                                        url = url,
                                        token = token,
                                        fallbackFilename = "worker_sorting_matrix.pdf",
                                        onSuccess = { veggieViewModel.loadData(preserveDraftEdits = true) },
                                        onError = { err -> veggieViewModel.setExportError(err) }
                                    )
                                },
                                onExportVeggieOrderFormPdf = { language ->
                                    val token = adminNetworkClient.authToken ?: ""
                                    val url = adminNetworkClient.resolveUrl("/api/admin/veggie/order-form-pdf?lang=${language.name}")
                                    jsDownloadPdf(
                                        url = url,
                                        token = token,
                                        fallbackFilename = "customer_order_form.pdf",
                                        onSuccess = {},
                                        onError = { err -> veggieViewModel.setExportError(err) }
                                    )
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

                if (!isServerReachable) {
                    KioskOfflineBlockingOverlay(
                        onRetryClick = {
                            scope.launch {
                                KioskConnectionMonitor.checkHealthNow()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun KioskOfflineBlockingOverlay(onRetryClick: () -> Unit) {
    val strings = I18n.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            ),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 12.dp,
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 440.dp)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(44.dp),
                    strokeWidth = 4.dp
                )
                Text(
                    text = strings.kioskOfflineTitle,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = strings.kioskOfflineDescription,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
                Text(
                    text = strings.kioskOfflineWaiting,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = onRetryClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(strings.kioskOfflineRetryButton)
                }
            }
        }
    }
}

@JsFun("""
(url, token, fallbackFilename, onSuccess, onError) => {
    fetch(url, {
        method: 'GET',
        headers: token ? { 'Authorization': 'Bearer ' + token } : {}
    }).then(async (res) => {
        if (!res.ok) {
            throw new Error('HTTP ' + res.status);
        }
        const disposition = res.headers.get('Content-Disposition') || '';
        const utf8Match = disposition.match(/filename\*=UTF-8''([^;]+)/i);
        const asciiMatch = disposition.match(/filename=["']?([^"';]+)["']?/i);
        const filename = utf8Match
            ? decodeURIComponent(utf8Match[1])
            : (asciiMatch ? asciiMatch[1] : fallbackFilename);
        const blob = await res.blob();
        const objectUrl = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = objectUrl;
        a.download = filename;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        setTimeout(() => URL.revokeObjectURL(objectUrl), 1000);
        onSuccess();
    }).catch((err) => {
        onError(err && err.message ? err.message : 'Download failed');
    });
}
""")
private external fun jsDownloadPdf(
    url: String,
    token: String,
    fallbackFilename: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
)

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

@JsFun("""
(key) => {
    try {
        window.localStorage.removeItem(key);
    } catch (e) {
        console.warn('Failed to remove localStorage item:', e);
    }
}
""")
private external fun jsRemoveLocalItem(key: String)

private val draftJson = Json { ignoreUnknownKeys = true }

private fun customerVeggieDraftKey(userId: String, roundId: String): String =
    "lojinha_veggie_draft_${userId}_$roundId"

private fun loadCustomerVeggieDraft(userId: String, roundId: String): Map<String, Long>? {
    return try {
        val raw = jsGetLocalItem(customerVeggieDraftKey(userId, roundId)) ?: return null
        draftJson.decodeFromString<Map<String, Long>>(raw)
    } catch (_: Exception) {
        null
    }
}

private fun saveCustomerVeggieDraft(userId: String, roundId: String, quantities: Map<String, Long>) {
    try {
        jsSetLocalItem(customerVeggieDraftKey(userId, roundId), draftJson.encodeToString(quantities))
    } catch (_: Exception) {}
}

private fun clearCustomerVeggieDraft(userId: String, roundId: String) {
    try {
        jsRemoveLocalItem(customerVeggieDraftKey(userId, roundId))
    } catch (_: Exception) {}
}

private fun adminMatrixDraftKey(roundId: String): String =
    "lojinha_admin_veggie_matrix_draft_$roundId"

private fun loadAdminVeggieMatrixDraft(roundId: String): AdminVeggieMatrixDraft? {
    return try {
        val raw = jsGetLocalItem(adminMatrixDraftKey(roundId)) ?: return null
        draftJson.decodeFromString<AdminVeggieMatrixDraft>(raw)
    } catch (_: Exception) {
        null
    }
}

private fun saveAdminVeggieMatrixDraft(roundId: String, draft: AdminVeggieMatrixDraft) {
    try {
        jsSetLocalItem(adminMatrixDraftKey(roundId), draftJson.encodeToString(draft))
    } catch (_: Exception) {}
}

private fun clearAdminVeggieMatrixDraft(roundId: String) {
    try {
        jsRemoveLocalItem(adminMatrixDraftKey(roundId))
    } catch (_: Exception) {}
}
