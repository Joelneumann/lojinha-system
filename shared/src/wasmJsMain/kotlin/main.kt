import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import de.joelneumann.lojinha.ui.screens.admin.AdminScreen
import de.joelneumann.lojinha.ui.theme.LojinhaTheme
import de.joelneumann.lojinha.ui.theme.WEB_UI_SCALE_FACTOR
import de.joelneumann.lojinha.ui.utils.PdfFilenames
import de.joelneumann.lojinha.ui.viewmodel.CustomerVeggieViewModel
import de.joelneumann.lojinha.ui.viewmodel.TransactionHistoryViewModel
import de.joelneumann.lojinha.ui.viewmodel.admin.*
import kotlinx.coroutines.cancel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private enum class WebPortalScreen {
    CUSTOMER_PORTAL,
    ADMIN_PANEL
}

@OptIn(ExperimentalComposeUiApi::class, FlowPreview::class)
fun main() {
    CanvasBasedWindow("Lojinha Portal") {
        var currentScreen by remember { mutableStateOf<WebPortalScreen?>(null) }
        var loggedInCustomer by remember { mutableStateOf<User?>(null) }
        var isCheckingSession by remember { mutableStateOf(true) }
        val isServerReachable by KioskConnectionMonitor.isServerReachable.collectAsState()
        val scope = rememberCoroutineScope()

        val adminNetworkClient = remember { AdminNetworkClient() }
        val productRepository = remember { HttpProductRepository(adminNetworkClient) }
        val userRepository = remember { HttpUserRepository(adminNetworkClient) }
        val transactionRepository = remember { HttpTransactionRepository(adminNetworkClient) }
        val billingListRepository = remember { HttpBillingListRepository(adminNetworkClient) }
        val adminNoteRepository = remember { HttpAdminNoteRepository(adminNetworkClient) }
        val shoppingListRepository = remember { HttpShoppingListRepository(adminNetworkClient) }
        val veggieOrderRepository = remember { HttpVeggieOrderRepository(adminNetworkClient) }
        val settingsRepository = remember { HttpSettingsRepository(adminNetworkClient) }

        val customerNetworkClient = remember { CustomerNetworkClient() }
        val customerUserRepository = remember { HttpCustomerUserRepository(customerNetworkClient) }
        val customerTransactionRepository = remember { HttpCustomerTransactionRepository(customerNetworkClient) }
        val defaultSettings = remember { SystemSettings() }

        // Session restoration on startup (Admin or Customer); redirect to native login page if unauthenticated
        LaunchedEffect(Unit) {
            when (adminNetworkClient.fetchSessionRole()) {
                "ADMIN" -> {
                    if (adminNetworkClient.tryRestoreSession()) {
                        currentScreen = WebPortalScreen.ADMIN_PANEL
                        isCheckingSession = false
                    } else {
                        jsRedirectToLogin(false)
                    }
                }
                "CUSTOMER" -> {
                    val restoredUser = customerNetworkClient.tryRestoreCustomerSession()
                    if (restoredUser != null) {
                        loggedInCustomer = restoredUser
                        LanguageManager.setLanguage(restoredUser.language)
                        currentScreen = WebPortalScreen.CUSTOMER_PORTAL
                        isCheckingSession = false
                    } else {
                        jsRedirectToLogin(false)
                    }
                }
                else -> jsRedirectToLogin(false)
            }
        }

        LojinhaTheme(scaleFactor = WEB_UI_SCALE_FACTOR) {
            Box(modifier = Modifier.fillMaxSize()) {
                val activeScreen = currentScreen
                if (isCheckingSession || activeScreen == null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    Crossfade(
                        targetState = activeScreen,
                        animationSpec = tween(durationMillis = 250),
                        modifier = Modifier.fillMaxSize()
                    ) { targetScreen ->
                        when (targetScreen) {
                        WebPortalScreen.CUSTOMER_PORTAL -> {
                            val activeCustomer = loggedInCustomer
                            if (activeCustomer == null) {
                                LaunchedEffect(Unit) {
                                    jsRedirectToLogin(false)
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
                                        customerNetworkClient.onDataChanged.debounce(300).collect {
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
                                            jsRedirectToLogin(false)
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

                                val customerDisplaySettings by customerVeggieViewModel.displaySettings.collectAsState()
                                val effectiveCustomerSettings = remember(customerDisplaySettings) {
                                    customerDisplaySettings?.toSystemSettings() ?: defaultSettings
                                }

                                CustomerNetworkPortalScreen(
                                    user = activeCustomer,
                                    settings = effectiveCustomerSettings,
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
                                            jsRedirectToLogin(false)
                                        }
                                    },
                                    onExecuteCustomerPdfExport = { customer, fromTimestamp, toTimestamp, language ->
                                        historyViewModel.setIsExportingPdf(true)
                                        historyViewModel.setExportPdfError(null)
                                        val fromQuery = if (fromTimestamp != null) "&from=$fromTimestamp" else ""
                                        val toQuery = if (toTimestamp != null) "&to=$toTimestamp" else ""
                                        val url = customerNetworkClient.resolveUrl("/api/customer/transactions-pdf?lang=${language.name}$fromQuery$toQuery")
                                        val fallbackName = PdfFilenames.transactionStatementFilename(customer.name, language)
                                        jsDownloadFile(
                                            url = url,
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
                            val shoppingListViewModel = remember { AdminShoppingListViewModel(shoppingListRepository, productRepository, adminNoteRepository) }
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
                                    adminNetworkClient.onDataChanged.debounce(300).collect {
                                        notesViewModel.loadNotes()
                                        productsViewModel.loadData()
                                        shoppingListViewModel.loadData()
                                        usersViewModel.loadData()
                                        transactionsViewModel.loadData()
                                        bulkBillingViewModel.loadData()
                                        veggieViewModel.loadData()
                                    }
                                }
                                launch {
                                    adminNetworkClient.onUnauthorized.collect {
                                        jsRedirectToLogin(true)
                                    }
                                }
                                launch {
                                    KioskConnectionMonitor.onReconnected.collect {
                                        adminNetworkClient.verifySessionStillValid()
                                        if (adminNetworkClient.isAuthenticated) {
                                            notesViewModel.loadNotes()
                                            productsViewModel.loadData()
                                            shoppingListViewModel.loadData()
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
                                    shoppingListViewModel.viewModelScope.cancel()
                                    usersViewModel.viewModelScope.cancel()
                                    transactionsViewModel.viewModelScope.cancel()
                                    bulkBillingViewModel.viewModelScope.cancel()
                                    veggieViewModel.viewModelScope.cancel()
                                }
                            }

                            var isExportingCsv by remember { mutableStateOf(false) }
                            var csvExportError by remember { mutableStateOf<String?>(null) }

                            fun downloadAdminCsv(
                                path: String,
                                fallbackFilename: String,
                                onError: (String) -> Unit = { err -> csvExportError = err }
                            ) {
                                if (isExportingCsv) return
                                isExportingCsv = true
                                jsDownloadFile(
                                    url = adminNetworkClient.resolveUrl(path),
                                    fallbackFilename = fallbackFilename,
                                    onSuccess = { isExportingCsv = false },
                                    onError = { err ->
                                        isExportingCsv = false
                                        onError(err)
                                    }
                                )
                            }

                            AdminScreen(
                                notesViewModel = notesViewModel,
                                productsViewModel = productsViewModel,
                                shoppingListViewModel = shoppingListViewModel,
                                usersViewModel = usersViewModel,
                                transactionsViewModel = transactionsViewModel,
                                bulkBillingViewModel = bulkBillingViewModel,
                                veggieViewModel = veggieViewModel,
                                settingsViewModel = null,
                                defaultSaveDirectory = "Downloads",
                                onExportShoppingListPdf = { language ->
                                    val url = adminNetworkClient.resolveUrl("/api/admin/shopping-list/pdf?lang=${language.name}")
                                    val fallbackName = PdfFilenames.shoppingListFilename(dateStamp = null, language = language)
                                    jsDownloadFile(
                                        url = url,
                                        fallbackFilename = fallbackName,
                                        onSuccess = { shoppingListViewModel.markExportedLocally() },
                                        onError = { err -> shoppingListViewModel.reportError(err) }
                                    )
                                },
                                onExecutePdfExport = { user, _, fromTimestamp, toTimestamp, language ->
                                    usersViewModel.setIsExportingPdf(true)
                                    usersViewModel.setExportPdfError(null)
                                    val fromQuery = if (fromTimestamp != null) "&from=$fromTimestamp" else ""
                                    val toQuery = if (toTimestamp != null) "&to=$toTimestamp" else ""
                                    val url = adminNetworkClient.resolveUrl("/api/admin/users/${user.id}/transactions-pdf?lang=${language.name}$fromQuery$toQuery")
                                    val fallbackName = PdfFilenames.transactionStatementFilename(user.name, language)
                                    jsDownloadFile(
                                        url = url,
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
                                    val url = adminNetworkClient.resolveUrl("/api/admin/veggie/rounds/$roundId/buyer-pdf?lang=${language.name}")
                                    jsDownloadFile(
                                        url = url,
                                        fallbackFilename = "buyer_shopping_list.pdf",
                                        onSuccess = { veggieViewModel.loadData(preserveDraftEdits = true) },
                                        onError = { err -> veggieViewModel.setExportError(err) }
                                    )
                                },
                                onExportVeggieWorkerPdf = { roundId, language ->
                                    val url = adminNetworkClient.resolveUrl("/api/admin/veggie/rounds/$roundId/worker-pdf?lang=${language.name}")
                                    jsDownloadFile(
                                        url = url,
                                        fallbackFilename = "worker_sorting_matrix.pdf",
                                        onSuccess = { veggieViewModel.loadData(preserveDraftEdits = true) },
                                        onError = { err -> veggieViewModel.setExportError(err) }
                                    )
                                },
                                onExportVeggieOrderFormPdf = { language ->
                                    val url = adminNetworkClient.resolveUrl("/api/admin/veggie/order-form-pdf?lang=${language.name}")
                                    jsDownloadFile(
                                        url = url,
                                        fallbackFilename = "customer_order_form.pdf",
                                        onSuccess = {},
                                        onError = { err -> veggieViewModel.setExportError(err) }
                                    )
                                },
                                onExportNotesCsv = {
                                    downloadAdminCsv("/api/admin/export/notes.csv", "admin_notes.csv")
                                },
                                onExportProductsCsv = {
                                    downloadAdminCsv("/api/admin/export/products.csv", "products.csv")
                                },
                                onExportProductHistoryCsv = { product ->
                                    downloadAdminCsv(
                                        "/api/admin/export/products/${product.id}/history.csv",
                                        de.joelneumann.lojinha.data.service.CsvExportFilenames.productHistoryFilename(product.name)
                                    )
                                },
                                onExportUsersCsv = {
                                    downloadAdminCsv("/api/admin/export/users.csv", "users.csv")
                                },
                                onExportUserTransactionsCsv = { user ->
                                    downloadAdminCsv(
                                        "/api/admin/export/users/${user.id}/transactions.csv",
                                        de.joelneumann.lojinha.data.service.CsvExportFilenames.userTransactionsFilename(user.name)
                                    )
                                },
                                onExportTransactionsCsv = {
                                    downloadAdminCsv("/api/admin/export/transactions.csv", "transactions.csv")
                                },
                                onExportVeggieProductsCsv = {
                                    downloadAdminCsv(
                                        "/api/admin/export/veggie-products.csv",
                                        "veggie_products.csv",
                                        onError = { err -> veggieViewModel.setExportError(err) }
                                    )
                                },
                                onExportVeggieOrdersCsv = {
                                    downloadAdminCsv(
                                        "/api/admin/export/veggie-orders.csv",
                                        "veggie_orders.csv",
                                        onError = { err -> veggieViewModel.setExportError(err) }
                                    )
                                },
                                onExitAdmin = {
                                    scope.launch {
                                        adminNetworkClient.logout()
                                        jsRedirectToLogin(false)
                                    }
                                }
                            )

                            val err = csvExportError
                            if (err != null) {
                                val strings = I18n.current
                                AlertDialog(
                                    onDismissRequest = { csvExportError = null },
                                    title = {
                                        Text(
                                            text = strings.csvExportFailed,
                                            fontWeight = FontWeight.Bold,
                                            color = de.joelneumann.lojinha.ui.theme.ColorDangerCrimson
                                        )
                                    },
                                    text = {
                                        Text(
                                            text = err,
                                            color = de.joelneumann.lojinha.ui.theme.ColorDangerCrimson,
                                            fontSize = 13.sp
                                        )
                                    },
                                    confirmButton = {
                                        Button(
                                            onClick = { csvExportError = null },
                                            colors = ButtonDefaults.buttonColors(containerColor = de.joelneumann.lojinha.ui.theme.AccentNavy)
                                        ) {
                                            Text(strings.confirm, color = de.joelneumann.lojinha.ui.theme.SurfaceWhite)
                                        }
                                    }
                                )
                            }
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
(showAdminRestartNotice) => {
    window.onbeforeunload = null;
    try {
        if (showAdminRestartNotice) {
            window.sessionStorage.setItem('lojinha_admin_restart_notice', '1');
        }
    } catch (_) {}
    window.location.replace('/');
}
""")
private external fun jsRedirectToLogin(showAdminRestartNotice: Boolean)

@JsFun("""
(url, fallbackFilename, onSuccess, onError) => {
    fetch(url, {
        method: 'GET',
        credentials: 'same-origin'
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
private external fun jsDownloadFile(
    url: String,
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
