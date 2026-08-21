package de.joelneumann.lojinha.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.joelneumann.lojinha.domain.model.Language
import de.joelneumann.lojinha.domain.model.Product
import de.joelneumann.lojinha.domain.model.SecondaryCurrency
import de.joelneumann.lojinha.domain.model.SystemSettings
import de.joelneumann.lojinha.domain.model.User
import de.joelneumann.lojinha.ui.components.general.*
import de.joelneumann.lojinha.ui.components.shopping.*
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.theme.*
import de.joelneumann.lojinha.ui.utils.Formatting
import de.joelneumann.lojinha.ui.viewmodel.CartItem
import de.joelneumann.lojinha.ui.viewmodel.ShoppingViewModel

@Composable
fun ShoppingScreen(
    viewModel: ShoppingViewModel,
    user: User,
    language: Language,
    settings: SystemSettings,
    onLanguageSelected: (Language) -> Unit,
    onLogout: () -> Unit,
    onNavigateToHistory: () -> Unit
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
        language = language,
        settings = settings,
        products = products,
        searchQuery = searchQuery,
        cartItems = cartItems,
        weightProductDialog = weightProductDialog,
        weightInput = weightInput,
        weightError = weightError,
        showCheckoutConfirmation = showCheckoutConfirmation,
        onLanguageSelected = onLanguageSelected,
        onLogout = onLogout,
        onSearchQueryChange = viewModel::updateSearchQuery,
        onSearchSubmitted = { viewModel.onSearchSubmitted(settings.globalMarkupPercent) },
        onProductSelected = { product -> viewModel.onProductSelected(product, settings.globalMarkupPercent) },
        onUpdateCartQty = viewModel::updateCartItemQuantity,
        onRemoveCartItem = viewModel::removeCartItem,
        onOpenCheckout = viewModel::openCheckoutConfirmation,
        onCloseCheckout = viewModel::closeCheckoutConfirmation,
        onCompletePurchase = { viewModel.completePurchase(user, onNavigateToHistory) },
        onWeightInputChange = viewModel::updateWeightInput,
        onCloseWeightDialog = viewModel::closeWeightDialog,
        onSubmitWeightDialog = { viewModel.submitWeightDialog(settings.globalMarkupPercent) },
        onNavigateToHistory = onNavigateToHistory
    )
}

@Composable
fun ShoppingContent(
    user: User,
    language: Language,
    settings: SystemSettings,
    products: List<Product>,
    searchQuery: String,
    cartItems: List<CartItem>,
    weightProductDialog: Product?,
    weightInput: String,
    weightError: String?,
    showCheckoutConfirmation: Boolean,
    onLanguageSelected: (Language) -> Unit,
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
    onNavigateToHistory: () -> Unit
) {
    val strings = I18n.get(language)
    val searchFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        searchFocusRequester.requestFocus()
    }

    LaunchedEffect(weightProductDialog) {
        if (weightProductDialog == null) {
            searchFocusRequester.requestFocus()
        }
    }

    val filteredProducts = remember(products, searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else products.filter { p ->
            p.name.contains(searchQuery, ignoreCase = true) ||
                    p.barcodes.any { b -> b.code.contains(searchQuery, ignoreCase = true) }
        }
    }

    val cartTotal = remember(cartItems) { cartItems.sumOf { it.lineTotal } }
    val balanceAfter = remember(user.balance, cartTotal) { user.balance - cartTotal }

    val rate = when (user.secondaryCurrency) {
        SecondaryCurrency.USD -> settings.usdExchangeRate
        SecondaryCurrency.EUR -> settings.eurExchangeRate
        else -> 0.0
    }

    Column(modifier = Modifier.fillMaxSize()) {
        HeaderBar(
            title = strings.shopping,
            currentLanguage = language,
            onLanguageSelected = onLanguageSelected,
            actions = {
                Button(
                    onClick = onNavigateToHistory,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryNavy),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "👤 ${strings.account}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = SurfaceWhite
                    )
                }

                LogoutButton(
                    language = language,
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
                    .padding(20.dp)
            ) {
                // User Header Row
                UserBalanceHeader(
                    user = user,
                    rate = rate,
                    language = language
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
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "🔍 Scan product barcode or type to search",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondaryMuted,
                            textAlign = TextAlign.Center
                        )
                    }
                } else if (filteredProducts.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No products found matching \"$searchQuery\"",
                            fontSize = 14.sp,
                            color = TextSecondaryMuted,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredProducts, key = { it.id }) { product ->
                            ProductCard(
                                product = product,
                                globalMarkup = settings.globalMarkupPercent,
                                user = user,
                                rate = rate,
                                language = language,
                                onClick = {
                                    onProductSelected(product)
                                    searchFocusRequester.requestFocus()
                                }
                            )
                        }
                    }
                }
            }

            // Right Panel: Sticky Cart Panel
            CartPanel(
                cartItems = cartItems,
                user = user,
                rate = rate,
                language = language,
                cartTotal = cartTotal,
                balanceAfter = balanceAfter,
                onQtyChange = { productId, newQty ->
                    onUpdateCartQty(productId, newQty)
                    searchFocusRequester.requestFocus()
                },
                onRemoveItem = { productId ->
                    onRemoveCartItem(productId)
                    searchFocusRequester.requestFocus()
                },
                onCompletePurchase = onOpenCheckout,
                modifier = Modifier.weight(1f)
            )
        }
    }

    // Weight Prompt Input Dialog Modal
    if (weightProductDialog != null) {
        WeightInputDialog(
            productName = weightProductDialog.name,
            weightInput = weightInput,
            weightError = weightError,
            language = language,
            onWeightInputChange = onWeightInputChange,
            onDismiss = onCloseWeightDialog,
            onSubmit = onSubmitWeightDialog
        )
    }

    // Checkout Confirmation Modal Dialog using generic ConfirmationDialog
    if (showCheckoutConfirmation) {
        ConfirmationDialog(
            title = strings.confirmPurchaseTitle,
            message = strings.confirmPurchaseMsg(Formatting.formatBrl(cartTotal)),
            language = language,
            onDismiss = onCloseCheckout,
            onConfirm = onCompletePurchase,
            confirmButtonColor = ColorSuccessEmerald
        )
    }
}
