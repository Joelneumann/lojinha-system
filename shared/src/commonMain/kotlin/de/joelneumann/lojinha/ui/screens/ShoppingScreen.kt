package de.joelneumann.lojinha.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.joelneumann.lojinha.domain.model.Product
import de.joelneumann.lojinha.domain.model.SecondaryCurrency
import de.joelneumann.lojinha.domain.model.SystemSettings
import de.joelneumann.lojinha.domain.model.User
import de.joelneumann.lojinha.ui.components.general.*
import de.joelneumann.lojinha.ui.components.shopping.*
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.i18n.LanguageManager
import de.joelneumann.lojinha.ui.theme.*
import de.joelneumann.lojinha.ui.utils.Formatting
import de.joelneumann.lojinha.ui.utils.containsIgnoreAccents
import de.joelneumann.lojinha.ui.utils.currentTimeMillis
import de.joelneumann.lojinha.ui.utils.safeRequestFocus
import de.joelneumann.lojinha.ui.utils.sortedByAccentInsensitive
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
    onUserInteracted: (force: Boolean) -> Unit = {}
) {
    val products by viewModel.products.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val cartItems by viewModel.cartItems.collectAsState()
    val weightProductDialog by viewModel.weightProductDialog.collectAsState()
    val weightInput by viewModel.weightInput.collectAsState()
    val weightError by viewModel.weightError.collectAsState()
    val showCheckoutConfirmation by viewModel.showCheckoutConfirmation.collectAsState()

    ShoppingContent(
        user = user,
        settings = settings,
        products = products,
        searchQuery = searchQuery,
        cartItems = cartItems,
        weightProductDialog = weightProductDialog,
        weightInput = weightInput,
        weightError = weightError,
        showCheckoutConfirmation = showCheckoutConfirmation,
        onLogout = onLogout,
        onSearchQueryChange = { query ->
            onUserInteracted(false)
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
        onOpenCheckout = {
            onUserInteracted(true)
            viewModel.openCheckoutConfirmation()
        },
        onCloseCheckout = viewModel::closeCheckoutConfirmation,
        onCompletePurchase = { viewModel.completePurchase(user, onNavigateToHistory) },
        onWeightInputChange = viewModel::updateWeightInput,
        onCloseWeightDialog = viewModel::closeWeightDialog,
        onSubmitWeightDialog = {
            onUserInteracted(true)
            viewModel.submitWeightDialog(settings.globalMarkupPercent)
        },
        onNavigateToHistory = onNavigateToHistory,
        onUserInteracted = onUserInteracted
    )
}

@Composable
fun ShoppingContent(
    user: User,
    settings: SystemSettings,
    products: List<Product>,
    searchQuery: String,
    cartItems: List<CartItem>,
    weightProductDialog: Product?,
    weightInput: String,
    weightError: String?,
    showCheckoutConfirmation: Boolean,
    onLogout: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchSubmitted: () -> Unit,
    onProductSelected: (Product) -> Unit,
    onUpdateCartQty: (productId: String, newQty: Long) -> Unit,
    onRemoveCartItem: (productId: String) -> Unit,
    onOpenCheckout: () -> Unit,
    onCloseCheckout: () -> Unit,
    onCompletePurchase: () -> Unit,
    onWeightInputChange: (String) -> Unit,
    onCloseWeightDialog: () -> Unit,
    onSubmitWeightDialog: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onUserInteracted: (force: Boolean) -> Unit = {}
) {
    val strings = I18n.current
    val searchFocusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(weightProductDialog, showCheckoutConfirmation, LanguageManager.currentLanguage) {
        if (weightProductDialog == null && !showCheckoutConfirmation) {
            searchFocusRequester.safeRequestFocus()
        }
    }

    val filteredProducts = remember(products, searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else products.filter { p ->
            p.name.containsIgnoreAccents(searchQuery) ||
                    p.barcodes.any { b -> b.code.containsIgnoreAccents(searchQuery) }
        }.sortedByAccentInsensitive { it.name }
    }

    val cartTotal = remember(cartItems) { cartItems.sumOf { it.lineTotal } }
    val balanceAfter = remember(user.balance, cartTotal) { user.balance - cartTotal }

    val rate = when (user.secondaryCurrency) {
        SecondaryCurrency.USD -> settings.usdExchangeRate
        SecondaryCurrency.EUR -> settings.eurExchangeRate
        else -> 0.0
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                var lastInteractionTime = 0L
                var lastPosition: Offset? = null
                var accumulatedDistance = 0f
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        // Ignore exit and enter events (e.g. dialog popups appearing/disappearing or window focus shifts)
                        if (event.type == PointerEventType.Exit || event.type == PointerEventType.Enter) {
                            lastPosition = null
                            accumulatedDistance = 0f
                            continue
                        }

                        val currentPosition = event.changes.firstOrNull()?.position
                        val prevPosition = lastPosition
                        val isClickOrScroll = event.type == PointerEventType.Press || event.type == PointerEventType.Scroll

                        var isRealMovement = false
                        if (currentPosition != null && prevPosition != null && event.type == PointerEventType.Move) {
                            val delta = (currentPosition - prevPosition).getDistance()
                            accumulatedDistance += delta
                            if (accumulatedDistance >= 15f) {
                                isRealMovement = true
                                accumulatedDistance = 0f
                            }
                        }

                        if (currentPosition != null) {
                            lastPosition = currentPosition
                        }

                        if (isClickOrScroll || isRealMovement) {
                            val now = currentTimeMillis()
                            if (now - lastInteractionTime >= 500L) {
                                lastInteractionTime = now
                                onUserInteracted(false)
                            }
                        }
                    }
                }
            }
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
                    onSearchSubmitted = onSearchSubmitted,
                    focusRequester = searchFocusRequester
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
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 20.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredProducts, key = { it.id }) { product ->
                            ProductCard(
                                product = product,
                                globalMarkup = settings.globalMarkupPercent,
                                user = user,
                                rate = rate,
                                onClick = {
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
                onCompletePurchase = onOpenCheckout,
                modifier = Modifier.weight(0.9f)
            )
        }

        // Weight Input Modal Dialog
        weightProductDialog?.let { product ->
            WeightInputDialog(
                productName = product.name,
                weightInput = weightInput,
                weightError = weightError,
                onWeightInputChange = onWeightInputChange,
                onDismiss = onCloseWeightDialog,
                onSubmit = onSubmitWeightDialog
            )
        }

        // Complete Purchase Confirmation Modal Dialog
        if (showCheckoutConfirmation) {
            ConfirmationDialog(
                title = strings.confirmPurchaseTitle,
                message = strings.confirmPurchaseMsg(Formatting.formatBrl(cartTotal)),
                onDismiss = {
                    onCloseCheckout()
                    coroutineScope.launch { searchFocusRequester.safeRequestFocus() }
                },
                onConfirm = onCompletePurchase,
                confirmButtonColor = ColorSuccessEmerald
            )
        }
    }
}
