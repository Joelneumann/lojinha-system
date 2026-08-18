package de.joelneumann.lojinha.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import de.joelneumann.lojinha.domain.model.Language
import de.joelneumann.lojinha.domain.model.Product
import de.joelneumann.lojinha.domain.model.SystemSettings
import de.joelneumann.lojinha.domain.model.UnitType
import de.joelneumann.lojinha.domain.model.User
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
    onNavigateToHistory: () -> Unit
) {
    val strings = I18n.get(language)
    val products by viewModel.products.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val cartItems by viewModel.cartItems.collectAsState()
    val weightProductDialog by viewModel.weightProductDialog.collectAsState()
    val weightInput by viewModel.weightInput.collectAsState()
    val weightError by viewModel.weightError.collectAsState()
    val showCheckoutConfirmation by viewModel.showCheckoutConfirmation.collectAsState()

    val searchFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        searchFocusRequester.requestFocus()
    }

    val filteredProducts = remember(products, searchQuery) {
        if (searchQuery.isBlank()) products
        else products.filter { p ->
            p.name.contains(searchQuery, ignoreCase = true) ||
                    p.barcodes.any { b -> b.code.contains(searchQuery, ignoreCase = true) }
        }
    }

    val cartTotal = remember(cartItems) { cartItems.sumOf { it.lineTotal } }
    val balanceAfter = remember(user.balance, cartTotal) { user.balance - cartTotal }

    val rate = when (user.secondaryCurrency) {
        de.joelneumann.lojinha.domain.model.SecondaryCurrency.USD -> settings.usdExchangeRate
        de.joelneumann.lojinha.domain.model.SecondaryCurrency.EUR -> settings.eurExchangeRate
        else -> 0.0
    }

    Row(modifier = Modifier.fillMaxSize().background(SurfaceContainerLight)) {
        // Left Panel: Products Section
        Column(
            modifier = Modifier
                .weight(1.3f)
                .fillMaxHeight()
                .padding(20.dp)
        ) {
            // User Balance Prominent Header Bar
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = SurfaceWhite,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "${strings.shopping} — ${user.name}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryNavy
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${strings.balance}: ",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextSecondaryMuted
                        )
                        Text(
                            text = Formatting.formatBrl(user.balance),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (user.balance >= 0) ColorSuccessEmerald else ColorDangerCrimson
                        )
                        val secText = Formatting.formatSecondaryCurrency(user.balance, user.secondaryCurrency, rate)
                        if (secText.isNotEmpty()) {
                            Text(
                                text = secText,
                                fontSize = 14.sp,
                                color = TextSecondaryMuted
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Search Bar Input (Auto-Focused)
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { query ->
                    viewModel.updateSearchQuery(query)
                },
                placeholder = {
                    Text(
                        text = strings.searchProductPlaceholder,
                        color = TextSecondaryMuted
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(searchFocusRequester)
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyUp && keyEvent.key == Key.Enter) {
                            viewModel.onSearchSubmitted(settings.globalMarkupPercent)
                            true
                        } else false
                    },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SurfaceWhite,
                    unfocusedContainerColor = SurfaceWhite,
                    focusedBorderColor = AccentNavy,
                    unfocusedBorderColor = DividerBorder
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    viewModel.onSearchSubmitted(settings.globalMarkupPercent)
                })
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Product List (Vertical List of Cards)
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
                        onClick = { viewModel.onProductSelected(product, settings.globalMarkupPercent) }
                    )
                }
            }
        }

        // Right Panel: Sticky Cart Panel
        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            color = SurfaceWhite,
            shadowElevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                Text(
                    text = strings.cart,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryNavy
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = DividerBorder)

                // Cart Line Items List
                Box(modifier = Modifier.weight(1f)) {
                    if (cartItems.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "Cart is empty.\nScan product or click to add.",
                                color = TextSecondaryMuted,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(cartItems, key = { it.product.id }) { cartItem ->
                                CartLineItemRow(
                                    cartItem = cartItem,
                                    user = user,
                                    rate = rate,
                                    language = language,
                                    onQtyChange = { newQty ->
                                        viewModel.updateCartItemQuantity(cartItem.product.id, newQty)
                                    },
                                    onRemove = { viewModel.removeCartItem(cartItem.product.id) }
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = DividerBorder)

                // Cart Summary Bottom Block
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceContainerHighLight, RoundedCornerShape(12.dp))
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${strings.total}:",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryNavy
                        )

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = Formatting.formatBrl(cartTotal),
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryNavy
                            )
                            val secTotal = Formatting.formatSecondaryCurrency(cartTotal, user.secondaryCurrency, rate)
                            if (secTotal.isNotEmpty()) {
                                Text(
                                    text = secTotal,
                                    fontSize = 13.sp,
                                    color = TextSecondaryMuted
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${strings.balanceAfter}:",
                            fontSize = 14.sp,
                            color = TextSecondarySubtle
                        )
                        Text(
                            text = Formatting.formatBrl(balanceAfter),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (balanceAfter >= 0) ColorSuccessEmerald else ColorDangerCrimson
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Complete Purchase Button
                Button(
                    onClick = { viewModel.openCheckoutConfirmation() },
                    enabled = cartItems.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ColorSuccessEmerald)
                ) {
                    Text(
                        text = strings.completePurchase,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = SurfaceWhite
                    )
                }
            }
        }
    }

    // Weight Prompt Input Dialog Modal
    if (weightProductDialog != null) {
        val weightFocusRequester = remember { FocusRequester() }
        var showWeightTooltip by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { weightFocusRequester.requestFocus() }

        Dialog(onDismissRequest = { viewModel.closeWeightDialog() }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = SurfaceWhite,
                modifier = Modifier.width(420.dp).wrapContentHeight()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = strings.weightDialogTitle,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryNavy
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = strings.weightDialogMsg(weightProductDialog!!.name),
                        fontSize = 14.sp,
                        color = TextSecondarySubtle,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = weightInput,
                        onValueChange = { viewModel.updateWeightInput(it) },
                        singleLine = true,
                        isError = weightError != null,
                        shape = RoundedCornerShape(12.dp),
                        trailingIcon = {
                            IconButton(
                                onClick = { showWeightTooltip = !showWeightTooltip },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (showWeightTooltip) PrimaryNavy else androidx.compose.ui.graphics.Color.Transparent,
                                    border = BorderStroke(
                                        width = 1.5.dp,
                                        color = if (showWeightTooltip) PrimaryNavy else TextSecondaryMuted
                                    ),
                                    modifier = Modifier.size(22.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "i",
                                            color = if (showWeightTooltip) SurfaceWhite else TextSecondaryMuted,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            style = androidx.compose.ui.text.TextStyle(
                                                fontFamily = androidx.compose.ui.text.font.FontFamily.Serif
                                            )
                                        )
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(weightFocusRequester)
                            .onKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyUp && keyEvent.key == Key.Enter) {
                                    viewModel.submitWeightDialog(settings.globalMarkupPercent)
                                    true
                                } else false
                            },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            viewModel.submitWeightDialog(settings.globalMarkupPercent)
                        })
                    )

                    if (showWeightTooltip) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = SurfaceContainerHighLight,
                            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DividerBorder)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = strings.weightTooltip,
                                fontSize = 12.sp,
                                color = TextSecondarySubtle,
                                modifier = Modifier.padding(12.dp),
                                lineHeight = 16.sp
                            )
                        }
                    }

                    if (weightError != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(weightError!!, color = ColorDangerCrimson, fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.closeWeightDialog() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(strings.cancel)
                        }

                        Button(
                            onClick = { viewModel.submitWeightDialog(settings.globalMarkupPercent) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentNavy)
                        ) {
                            Text(strings.confirm, color = SurfaceWhite)
                        }
                    }
                }
            }
        }
    }

    // Checkout Confirmation Modal Dialog
    if (showCheckoutConfirmation) {
        Dialog(onDismissRequest = { viewModel.closeCheckoutConfirmation() }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = SurfaceWhite,
                modifier = Modifier.width(400.dp).wrapContentHeight()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = strings.confirmPurchaseTitle,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryNavy
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = strings.confirmPurchaseMsg(Formatting.formatBrl(cartTotal)),
                        fontSize = 15.sp,
                        color = TextSecondarySubtle,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.closeCheckoutConfirmation() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(strings.cancel)
                        }

                        Button(
                            onClick = {
                                viewModel.completePurchase(user, onNavigateToHistory)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ColorSuccessEmerald)
                        ) {
                            Text(strings.confirm, color = SurfaceWhite)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductCard(
    product: Product,
    globalMarkup: Double,
    user: User,
    rate: Double,
    language: Language,
    onClick: () -> Unit
) {
    val strings = I18n.get(language)
    val unitPrice = product.calculateEffectiveUnitPrice(globalMarkup)

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DividerBorder)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Column: Multiline Product Name & Unit Badge underneath
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
            ) {
                Text(
                    text = product.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryNavy,
                    lineHeight = 19.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = SurfaceContainerHighLight,
                    modifier = Modifier.wrapContentSize()
                ) {
                    Text(
                        text = if (product.unitType == UnitType.PIECE) strings.unitPiece else strings.unitWeight,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondaryMuted,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            // Right Column: Price & Secondary Currency underneath (Structured like Cart!)
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = Formatting.formatBrl(unitPrice),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentNavy
                    )
                    if (product.unitType == UnitType.WEIGHT) {
                        Text(
                            text = " / kg",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextSecondaryMuted,
                            modifier = Modifier.padding(bottom = 1.dp, start = 2.dp)
                        )
                    }
                }

                val secText = Formatting.formatSecondaryCurrency(unitPrice, user.secondaryCurrency, rate)
                if (secText.isNotEmpty()) {
                    Text(
                        text = secText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondaryMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun CartLineItemRow(
    cartItem: CartItem,
    user: User,
    rate: Double,
    language: Language,
    onQtyChange: (Long) -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLight),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DividerBorder))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = cartItem.product.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryNavy,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                val unitSuffix = if (cartItem.product.unitType == UnitType.WEIGHT) " / kg" else ""
                Text(
                    text = "${Formatting.formatQuantity(cartItem.quantity, cartItem.product.unitType)} × ${Formatting.formatBrl(cartItem.unitPriceWithMarkup)}$unitSuffix",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondaryMuted
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (cartItem.product.unitType == UnitType.PIECE) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = SurfaceWhite,
                        border = BorderStroke(1.dp, DividerBorder)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.height(30.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(30.dp)
                                    .clickable { onQtyChange(cartItem.quantity - 1) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("-", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = PrimaryNavy)
                            }

                            VerticalDivider(color = DividerBorder, modifier = Modifier.fillMaxHeight().width(1.dp))

                            Text(
                                text = "${cartItem.quantity}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryNavy,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.widthIn(min = 36.dp).padding(horizontal = 4.dp)
                            )

                            VerticalDivider(color = DividerBorder, modifier = Modifier.fillMaxHeight().width(1.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(30.dp)
                                    .clickable { onQtyChange(cartItem.quantity + 1) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("+", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = PrimaryNavy)
                            }
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = SurfaceWhite,
                        border = BorderStroke(1.dp, DividerBorder)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text(
                                text = Formatting.formatQuantity(cartItem.quantity, UnitType.WEIGHT),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryNavy,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 10.dp)
                            )

                            VerticalDivider(color = DividerBorder, modifier = Modifier.fillMaxHeight().width(1.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(30.dp)
                                    .clickable { onRemove() },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("✕", color = ColorDangerCrimson, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = Formatting.formatBrl(cartItem.lineTotal),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryNavy
                    )
                    val secText = Formatting.formatSecondaryCurrency(cartItem.lineTotal, user.secondaryCurrency, rate)
                    if (secText.isNotEmpty()) {
                        Text(
                            text = secText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextSecondaryMuted
                        )
                    }
                }
            }
        }
    }
}
