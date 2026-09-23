package de.joelneumann.lojinha.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.joelneumann.lojinha.domain.model.Product
import de.joelneumann.lojinha.domain.model.SecondaryCurrency
import de.joelneumann.lojinha.domain.model.SystemSettings
import de.joelneumann.lojinha.domain.model.Transaction
import de.joelneumann.lojinha.domain.model.UnitType
import de.joelneumann.lojinha.domain.model.User
import de.joelneumann.lojinha.ui.components.general.*
import de.joelneumann.lojinha.ui.components.shopping.*
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.i18n.LanguageManager
import de.joelneumann.lojinha.ui.theme.*
import de.joelneumann.lojinha.ui.utils.Formatting
import de.joelneumann.lojinha.ui.utils.containsIgnoreAccents
import de.joelneumann.lojinha.ui.utils.currentTimeMillis
import de.joelneumann.lojinha.ui.utils.filterAndRankProducts
import de.joelneumann.lojinha.ui.utils.safeRequestFocus
import de.joelneumann.lojinha.ui.utils.sortedByAccentInsensitive
import de.joelneumann.lojinha.ui.utils.trackUserInteractions
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import de.joelneumann.lojinha.ui.viewmodel.CartItem
import de.joelneumann.lojinha.ui.viewmodel.ShoppingViewModel
import kotlinx.coroutines.launch

@Composable
fun ShoppingScreen(
    viewModel: ShoppingViewModel,
    user: User,
    settings: SystemSettings,
    onLogout: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onUserInteracted: (force: Boolean) -> Unit = {},
    onPurchaseFinalized: () -> Unit = {},
    onPauseTimer: () -> Unit = {},
    onResumeTimer: () -> Unit = {}
) {
    val products by viewModel.products.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val cartItems by viewModel.cartItems.collectAsState()
    val isProcessingPurchase by viewModel.isProcessingPurchase.collectAsState()
    val purchaseError by viewModel.purchaseError.collectAsState()
    val weightProductDialog by viewModel.weightProductDialog.collectAsState()
    val weightInput by viewModel.weightInput.collectAsState()
    val weightError by viewModel.weightError.collectAsState()
    val completedPurchase by viewModel.completedPurchase.collectAsState()

    ShoppingContent(
        user = user,
        settings = settings,
        products = products,
        searchQuery = searchQuery,
        cartItems = cartItems,
        isProcessingPurchase = isProcessingPurchase,
        purchaseError = purchaseError,
        onDismissPurchaseError = viewModel::clearPurchaseError,
        weightProductDialog = weightProductDialog,
        weightInput = weightInput,
        weightError = weightError,
        completedPurchase = completedPurchase,
        onLogout = onLogout,
        onSearchQueryChange = { query ->
            onUserInteracted(true)
            viewModel.updateSearchQuery(query)
        },
        onSearchSubmitted = {
            onUserInteracted(true)
            viewModel.onSearchSubmitted(settings.globalMarkupPercent)
        },
        onProductSelected = { product ->
            onUserInteracted(true)
            viewModel.onProductSelected(product, settings.globalMarkupPercent)
        },
        onUpdateCartQty = { id, qty ->
            onUserInteracted(true)
            viewModel.updateCartItemQuantity(id, qty)
        },
        onRemoveCartItem = { id ->
            onUserInteracted(true)
            viewModel.removeCartItem(id)
        },
        onFinalizePurchase = {
            onUserInteracted(true)
            viewModel.completePurchase(user, onPurchaseFinalized)
        },
        onDismissCompletedPurchase = viewModel::dismissCompletedPurchase,
        onWeightInputChange = { input ->
            onUserInteracted(true)
            viewModel.updateWeightInput(input)
        },
        onCloseWeightDialog = {
            onUserInteracted(true)
            viewModel.closeWeightDialog()
        },
        onSubmitWeightDialog = {
            onUserInteracted(true)
            viewModel.submitWeightDialog(settings.globalMarkupPercent)
        },
        onNavigateToHistory = onNavigateToHistory,
        onUserInteracted = onUserInteracted,
        onPauseTimer = onPauseTimer,
        onResumeTimer = onResumeTimer
    )
}

@Composable
fun ShoppingContent(
    user: User,
    settings: SystemSettings,
    products: List<Product>,
    searchQuery: String,
    cartItems: List<CartItem>,
    isProcessingPurchase: Boolean = false,
    purchaseError: String? = null,
    onDismissPurchaseError: () -> Unit = {},
    weightProductDialog: Product?,
    weightInput: String,
    weightError: String?,
    completedPurchase: Transaction? = null,
    onLogout: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchSubmitted: () -> Unit,
    onProductSelected: (Product) -> Unit,
    onUpdateCartQty: (productId: String, newQty: Long) -> Unit,
    onRemoveCartItem: (productId: String) -> Unit,
    onFinalizePurchase: () -> Unit,
    onDismissCompletedPurchase: () -> Unit = {},
    onWeightInputChange: (String) -> Unit,
    onCloseWeightDialog: () -> Unit,
    onSubmitWeightDialog: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onUserInteracted: (force: Boolean) -> Unit = {},
    onPauseTimer: () -> Unit = {},
    onResumeTimer: () -> Unit = {}
) {
    val strings = I18n.current
    val searchFocusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()

    val filteredProducts = remember(products, searchQuery) {
        products.filterAndRankProducts(searchQuery)
    }

    var highlightedProductIndex by remember(filteredProducts) {
        mutableStateOf(if (filteredProducts.isNotEmpty()) 0 else -1)
    }
    var selectedCartIndex by remember { mutableStateOf(-1) }
    var showCheckoutConfirmation by remember { mutableStateOf(false) }

    fun selectProductInCart(productId: String) {
        val idx = cartItems.indexOfFirst { it.product.id == productId }
        selectedCartIndex = if (idx >= 0) idx else cartItems.size
    }

    LaunchedEffect(cartItems) {
        if (cartItems.isEmpty()) {
            selectedCartIndex = -1
            showCheckoutConfirmation = false
        } else if (selectedCartIndex >= cartItems.size) {
            selectedCartIndex = cartItems.lastIndex
        }
    }

    LaunchedEffect(completedPurchase) {
        if (completedPurchase != null) {
            onPauseTimer()
        } else {
            onResumeTimer()
        }
    }

    LaunchedEffect(weightProductDialog, showCheckoutConfirmation, completedPurchase, LanguageManager.currentLanguage) {
        if (weightProductDialog == null && !showCheckoutConfirmation && completedPurchase == null) {
            searchFocusRequester.safeRequestFocus()
        }
    }

    val cartTotal = remember(cartItems) { cartItems.sumOf { it.lineTotal } }
    val balanceAfter = remember(user.balance, cartTotal) { user.balance - cartTotal }

    val rate = when (user.secondaryCurrency) {
        SecondaryCurrency.USD -> settings.usdExchangeRate
        SecondaryCurrency.EUR -> settings.eurExchangeRate
        else -> 0.0
    }

    fun isPlusKey(event: KeyEvent): Boolean {
        return event.key == Key.Plus ||
                event.key == Key.Equals ||
                event.key == Key.NumPadAdd ||
                event.utf16CodePoint == '+'.code ||
                event.utf16CodePoint == '='.code
    }

    fun isMinusKey(event: KeyEvent): Boolean {
        return event.key == Key.Minus ||
                event.key == Key.NumPadSubtract ||
                event.utf16CodePoint == '-'.code
    }

    fun handleSelectedAdjustment(event: KeyEvent): Boolean {
        if (selectedCartIndex in cartItems.indices) {
            val isNumpad = event.key == Key.NumPadAdd || event.key == Key.NumPadSubtract
            val isTextSearchEmpty = searchQuery.isBlank()

            // If the user is actively typing in the search bar, let normal '-' and '+' type into the search field!
            if (!isTextSearchEmpty && !isNumpad) {
                return false
            }

            val item = cartItems[selectedCartIndex]
            val step = if (item.product.unitType == UnitType.WEIGHT) 100L else 1L
            if (isPlusKey(event)) {
                if (event.type == KeyEventType.KeyDown) {
                    onUpdateCartQty(item.product.id, item.quantity + step)
                }
                return true
            } else if (isMinusKey(event)) {
                if (event.type == KeyEventType.KeyDown) {
                    if (item.quantity > step) {
                        onUpdateCartQty(item.product.id, item.quantity - step)
                    } else {
                        onRemoveCartItem(item.product.id)
                        if (selectedCartIndex >= cartItems.size - 1) {
                            selectedCartIndex = (cartItems.size - 2).coerceAtLeast(-1)
                        }
                    }
                }
                return true
            }
        }
        return false
    }

    fun handleCartNavigation(keyEvent: KeyEvent): Boolean {
        if (selectedCartIndex !in cartItems.indices) return false
        if (keyEvent.type != KeyEventType.KeyDown) {
            return keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter || keyEvent.key == Key.Tab
        }
        val handled = when (keyEvent.key) {
            Key.Backspace, Key.Delete -> {
                val item = cartItems[selectedCartIndex]
                onRemoveCartItem(item.product.id)
                if (selectedCartIndex >= cartItems.size - 1) {
                    selectedCartIndex = (cartItems.size - 2).coerceAtLeast(-1)
                }
                true
            }
            Key.DirectionDown -> {
                selectedCartIndex = (selectedCartIndex + 1).coerceAtMost(cartItems.size - 1)
                true
            }
            Key.DirectionUp -> {
                if (selectedCartIndex > 0) {
                    selectedCartIndex -= 1
                } else {
                    selectedCartIndex = -1
                    coroutineScope.launch { searchFocusRequester.safeRequestFocus() }
                }
                true
            }
            Key.Escape, Key.Tab -> {
                selectedCartIndex = -1
                coroutineScope.launch { searchFocusRequester.safeRequestFocus() }
                true
            }
            Key.Enter, Key.NumPadEnter -> {
                if (cartItems.isNotEmpty()) {
                    showCheckoutConfirmation = true
                }
                true
            }
            else -> {
                selectedCartIndex = -1
                coroutineScope.launch { searchFocusRequester.safeRequestFocus() }
                false
            }
        }
        if (handled) {
            onUserInteracted(true)
        }
        return handled
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .onKeyEvent { keyEvent ->
                if (handleSelectedAdjustment(keyEvent)) {
                    true
                } else if (handleCartNavigation(keyEvent)) {
                    true
                } else if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Escape) {
                    if (weightProductDialog == null && !showCheckoutConfirmation && completedPurchase == null) {
                        onLogout()
                        true
                    } else false
                } else false
            }
            .trackUserInteractions(onUserInteracted)
    ) {
        HeaderBar(
            title = strings.shopping,
            onLanguageClick = {
                coroutineScope.launch { searchFocusRequester.safeRequestFocus() }
            },
            actions = {
                Button(
                    onClick = onNavigateToHistory,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryNavy),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = SurfaceWhite,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = strings.account,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = SurfaceWhite
                        )
                    }
                }

                LogoutButton(
                    onClick = onLogout
                )
            }
        )

        Row(modifier = Modifier.fillMaxSize().background(SurfaceContainerLight)) {
            // Left Panel: Products Section
            Column(
                modifier = Modifier
                    .weight(1.3f)
                    .fillMaxHeight()
                    .padding(ScreenPadding)
            ) {
                // User Header Row
                UserBalanceHeader(
                    user = user,
                    rate = rate
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Search Bar Input (Auto-Focused)
                SearchInputField(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChange,
                    placeholder = strings.searchProductPlaceholder,
                    onSearchSubmitted = {
                        if (searchQuery.isBlank()) {
                            if (cartItems.isNotEmpty()) {
                                showCheckoutConfirmation = true
                            }
                        } else if (highlightedProductIndex in filteredProducts.indices) {
                            val p = filteredProducts[highlightedProductIndex]
                            selectProductInCart(p.id)
                            onProductSelected(p)
                        } else if (filteredProducts.isNotEmpty()) {
                            val p = filteredProducts.first()
                            selectProductInCart(p.id)
                            onProductSelected(p)
                        } else {
                            onSearchSubmitted()
                        }
                    },
                    focusRequester = searchFocusRequester,
                    onEscape = onLogout,
                    onKeyDown = { event ->
                        if (handleSelectedAdjustment(event)) {
                            true
                        } else if (selectedCartIndex in cartItems.indices) {
                            handleCartNavigation(event)
                        } else when {
                            event.key == Key.DirectionDown && event.type == KeyEventType.KeyDown -> {
                                if (filteredProducts.isNotEmpty()) {
                                    highlightedProductIndex = (highlightedProductIndex + 1).coerceAtMost(filteredProducts.size - 1)
                                    onUserInteracted(true)
                                    true
                                } else if (cartItems.isNotEmpty()) {
                                    selectedCartIndex = 0
                                    highlightedProductIndex = -1
                                    onUserInteracted(true)
                                    true
                                } else false
                            }
                            event.key == Key.DirectionUp && event.type == KeyEventType.KeyDown -> {
                                if (filteredProducts.isNotEmpty()) {
                                    if (highlightedProductIndex > 0) {
                                        highlightedProductIndex -= 1
                                        onUserInteracted(true)
                                        true
                                    } else if (highlightedProductIndex == 0) {
                                        highlightedProductIndex = -1
                                        onUserInteracted(true)
                                        true
                                    } else false
                                } else false
                            }
                            event.key == Key.Tab && event.type == KeyEventType.KeyDown -> {
                                if (cartItems.isNotEmpty()) {
                                    selectedCartIndex = 0
                                    highlightedProductIndex = -1
                                    onUserInteracted(true)
                                    true
                                } else false
                            }
                            (event.key == Key.Enter || event.key == Key.NumPadEnter) -> {
                                if (event.type == KeyEventType.KeyDown) {
                                    if (searchQuery.isBlank()) {
                                        if (cartItems.isNotEmpty()) {
                                            showCheckoutConfirmation = true
                                        }
                                        true
                                    } else if (highlightedProductIndex in filteredProducts.indices) {
                                        val p = filteredProducts[highlightedProductIndex]
                                        selectProductInCart(p.id)
                                        onProductSelected(p)
                                        true
                                    } else if (filteredProducts.isNotEmpty()) {
                                        val p = filteredProducts.first()
                                        selectProductInCart(p.id)
                                        onProductSelected(p)
                                        true
                                    } else false
                                } else if (event.type == KeyEventType.KeyUp) {
                                    searchQuery.isBlank() || filteredProducts.isNotEmpty()
                                } else false
                            }
                            else -> false
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Product Display Area
                if (searchQuery.isBlank()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = TextSecondaryMuted,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = strings.scanOrTypeSearch,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextSecondaryMuted,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else if (filteredProducts.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = strings.noProductsFoundMatching(searchQuery),
                            fontSize = 14.sp,
                            color = TextSecondaryMuted,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp, start = 2.dp, end = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = strings.productsCountText(filteredProducts.size, products.size),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondaryMuted
                        )
                    }
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 20.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(filteredProducts, key = { _, it -> it.id }) { index, product ->
                            ProductCard(
                                product = product,
                                globalMarkup = settings.globalMarkupPercent,
                                user = user,
                                rate = rate,
                                isHighlighted = (index == highlightedProductIndex),
                                onClick = {
                                    highlightedProductIndex = index
                                    selectProductInCart(product.id)
                                    onProductSelected(product)
                                    coroutineScope.launch {
                                        searchFocusRequester.safeRequestFocus()
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Right Panel: Sticky Cart Sidebar
            CartPanel(
                cartItems = cartItems,
                user = user,
                rate = rate,
                cartTotal = cartTotal,
                balanceAfter = balanceAfter,
                onQtyChange = { productId, newQty ->
                    onUpdateCartQty(productId, newQty)
                    coroutineScope.launch { searchFocusRequester.safeRequestFocus() }
                },
                onRemoveItem = { productId ->
                    onRemoveCartItem(productId)
                    coroutineScope.launch { searchFocusRequester.safeRequestFocus() }
                },
                onCompletePurchase = onFinalizePurchase,
                onSelectCartItem = { index ->
                    selectedCartIndex = index
                    highlightedProductIndex = -1
                    coroutineScope.launch { searchFocusRequester.safeRequestFocus() }
                },
                modifier = Modifier.weight(0.9f),
                selectedCartIndex = selectedCartIndex,
                isProcessing = isProcessingPurchase
            )
        }

        // Weight Input Modal Dialog
        weightProductDialog?.let { product ->
            WeightInputDialog(
                productName = product.name,
                weightInput = weightInput,
                weightError = if (weightError != null) strings.invalidWeightFormat else null,
                onWeightInputChange = onWeightInputChange,
                onDismiss = onCloseWeightDialog,
                onSubmit = {
                    selectProductInCart(product.id)
                    onSubmitWeightDialog()
                }
            )
        }

        // Complete Purchase Confirmation Modal Dialog
        if (showCheckoutConfirmation) {
            ConfirmationDialog(
                title = strings.confirmPurchaseTitle,
                message = strings.confirmPurchaseMsg(Formatting.formatBrl(cartTotal)),
                confirmText = strings.confirm,
                cancelText = strings.cancel,
                onDismiss = {
                    showCheckoutConfirmation = false
                    onUserInteracted(true)
                },
                onConfirm = {
                    showCheckoutConfirmation = false
                    onFinalizePurchase()
                },
                confirmButtonColor = ColorSuccessEmerald
            )
        }

        // Purchase Error Alert Modal Dialog
        purchaseError?.let { err ->
            ConfirmationDialog(
                title = strings.confirm,
                message = err,
                confirmText = strings.confirm,
                cancelText = null,
                onDismiss = onDismissPurchaseError,
                onConfirm = onDismissPurchaseError,
                confirmButtonColor = ColorDangerCrimson
            )
        }

        // Purchase Overview Modal Dialog (Shown upon purchase finalization)
        completedPurchase?.let { tx ->
            PurchaseOverviewDialog(
                transaction = tx,
                user = user,
                rate = rate,
                onGoToTransactions = {
                    onDismissCompletedPurchase()
                    onNavigateToHistory()
                },
                onLogout = {
                    onDismissCompletedPurchase()
                    onLogout()
                }
            )
        }
    }
}
