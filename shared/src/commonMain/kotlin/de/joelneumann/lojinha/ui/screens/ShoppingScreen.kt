package de.joelneumann.lojinha.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
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
                            text = "${I18n.get("shopping", language)} — ${user.name}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryNavy
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${I18n.get("balance", language)}: ",
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
                    viewModel.updateSearchQuery(query, settings.globalMarkupPercent)
                },
                placeholder = {
                    Text(
                        text = I18n.get("search_product_placeholder", language),
                        color = TextSecondaryMuted
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(searchFocusRequester)
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyUp && keyEvent.key == Key.Enter) {
                            viewModel.onBarcodeScanned(searchQuery, settings.globalMarkupPercent)
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
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Product Grid
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredProducts, key = { it.id }) { product ->
                    ProductCard(
                        product = product,
                        globalMarkup = settings.globalMarkupPercent,
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🛒 ${I18n.get("cart", language)} (${cartItems.size})",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryNavy
                    )

                    if (cartItems.isNotEmpty()) {
                        TextButton(onClick = { viewModel.clearCart() }) {
                            Text("Clear", color = ColorDangerCrimson, fontSize = 13.sp)
                        }
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 12.dp), color = DividerBorder)

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

                Divider(modifier = Modifier.padding(vertical = 12.dp), color = DividerBorder)

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
                            text = "${I18n.get("total", language)}:",
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
                            text = "${I18n.get("balance_after", language)}:",
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
                        text = I18n.get("complete_purchase", language),
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
                        text = I18n.get("weight_dialog_title", language),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryNavy
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = I18n.get("weight_dialog_msg", language).replace("{product}", weightProductDialog!!.name),
                        fontSize = 14.sp,
                        color = TextSecondarySubtle,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Preset weight click buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("250g" to 250L, "500g" to 500L, "1 kg" to 1000L, "1,5 kg" to 1500L).forEach { (label, grams) ->
                            OutlinedButton(
                                onClick = { viewModel.setWeightPreset(grams) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(4.dp)
                            ) {
                                Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = weightInput,
                        onValueChange = { viewModel.updateWeightInput(it) },
                        placeholder = { Text("e.g. 1,5 kg or 500 g") },
                        singleLine = true,
                        isError = weightError != null,
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
                            Text(I18n.get("cancel", language))
                        }

                        Button(
                            onClick = { viewModel.submitWeightDialog(settings.globalMarkupPercent) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentNavy)
                        ) {
                            Text(I18n.get("confirm", language), color = SurfaceWhite)
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
                        text = I18n.get("confirm_purchase_title", language),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryNavy
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = I18n.get("confirm_purchase_msg", language).replace("{amount}", Formatting.formatBrl(cartTotal)),
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
                            Text(I18n.get("cancel", language))
                        }

                        Button(
                            onClick = {
                                viewModel.completePurchase(user, onNavigateToHistory)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ColorSuccessEmerald)
                        ) {
                            Text(I18n.get("confirm", language), color = SurfaceWhite)
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
    language: Language,
    onClick: () -> Unit
) {
    val unitPrice = product.calculateEffectiveUnitPrice(globalMarkup)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(135.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DividerBorder)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = product.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryNavy,
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (product.unitType == UnitType.WEIGHT) "${Formatting.formatBrl(unitPrice)} / kg" else Formatting.formatBrl(unitPrice),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AccentNavy
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .background(SurfaceContainerHighLight, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (product.unitType == UnitType.PIECE) I18n.get("unit_piece", language) else I18n.get("unit_weight", language),
                        fontSize = 11.sp,
                        color = TextSecondaryMuted
                    )
                }

                Text(
                    text = "${I18n.get("stock", language)}: ${Formatting.formatQuantity(product.stockQuantity, product.unitType)}",
                    fontSize = 11.sp,
                    color = TextSecondarySubtle
                )
            }
        }
    }
}

@Composable
private fun CartLineItemRow(
    cartItem: CartItem,
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = cartItem.product.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryNavy
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${Formatting.formatQuantity(cartItem.quantity, cartItem.product.unitType)} x ${Formatting.formatBrl(cartItem.unitPriceWithMarkup)}",
                    fontSize = 12.sp,
                    color = TextSecondaryMuted
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (cartItem.product.unitType == UnitType.PIECE) {
                    IconButton(
                        onClick = { onQtyChange(cartItem.quantity - 1) },
                        modifier = Modifier.size(28.dp).background(SurfaceWhite, CircleShape)
                    ) {
                        Text("-", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }

                    Text(
                        text = "${cartItem.quantity}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )

                    IconButton(
                        onClick = { onQtyChange(cartItem.quantity + 1) },
                        modifier = Modifier.size(28.dp).background(SurfaceWhite, CircleShape)
                    ) {
                        Text("+", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                } else {
                    IconButton(
                        onClick = { onRemove() },
                        modifier = Modifier.size(28.dp).background(SurfaceWhite, CircleShape)
                    ) {
                        Text("✕", color = ColorDangerCrimson, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                Text(
                    text = Formatting.formatBrl(cartItem.lineTotal),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryNavy
                )
            }
        }
    }
}
