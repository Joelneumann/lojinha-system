package de.joelneumann.lojinha.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.joelneumann.lojinha.domain.model.Language
import de.joelneumann.lojinha.domain.model.Transaction
import de.joelneumann.lojinha.domain.model.TransactionItem
import de.joelneumann.lojinha.domain.model.TransactionType
import de.joelneumann.lojinha.domain.model.UnitType
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.theme.*
import de.joelneumann.lojinha.ui.utils.Formatting
import de.joelneumann.lojinha.ui.viewmodel.admin.AdminTransactionsViewModel

@Composable
fun AdminTransactionsTabScreen(
    viewModel: AdminTransactionsViewModel,
    expandedTransactionId: String?,
    onRequestToggleExpand: (String?) -> Unit,
    onRequestExpandTransaction: (String) -> Unit
) {
    val transactions by viewModel.transactions.collectAsState()
    val searchQuery by viewModel.searchFilter.collectAsState()

    val filteredTransactions = remember(transactions, searchQuery) {
        if (searchQuery.isBlank()) transactions
        else transactions.filter { tx ->
            val dateStr = Formatting.formatTimestamp(tx.timestamp)
            val dateStrEn = Formatting.formatTimestamp(tx.timestamp, Language.EN)
            val dateStrDe = Formatting.formatTimestamp(tx.timestamp, Language.DE)
            val dateStrBr = Formatting.formatTimestamp(tx.timestamp, Language.BR)
            tx.userNameSnapshot.contains(searchQuery, ignoreCase = true) ||
                    tx.type.name.contains(searchQuery, ignoreCase = true) ||
                    dateStr.contains(searchQuery, ignoreCase = true) ||
                    dateStrEn.contains(searchQuery, ignoreCase = true) ||
                    dateStrDe.contains(searchQuery, ignoreCase = true) ||
                    dateStrBr.contains(searchQuery, ignoreCase = true) ||
                    tx.items.any { item -> item.productName.contains(searchQuery, ignoreCase = true) }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        val openFirstResult = {
            if (filteredTransactions.isNotEmpty()) {
                onRequestExpandTransaction(filteredTransactions.first().id)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = viewModel::updateSearchFilter,
                placeholder = {
                    Text(
                        text = "🔍 Search transaction by user, type, product, or date...",
                        color = TextSecondaryMuted,
                        fontSize = 14.sp
                    )
                },
                textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown &&
                            (keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter)
                        ) {
                            openFirstResult()
                            true
                        } else {
                            false
                        }
                    },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { openFirstResult() }),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SurfaceWhite,
                    unfocusedContainerColor = SurfaceWhite,
                    focusedBorderColor = AccentNavy,
                    unfocusedBorderColor = DividerBorder
                ),
                singleLine = true
            )

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = SurfaceWhite,
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DividerBorder)),
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxHeight()
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isBlank()) "${transactions.size} Transactions" else "${filteredTransactions.size} / ${transactions.size} Transactions",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PrimaryNavy
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (filteredTransactions.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No transactions found.", color = TextSecondaryMuted)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                items(filteredTransactions, key = { it.id }) { tx ->
                    val isExpanded = expandedTransactionId == tx.id

                    val cancellationChild = transactions.firstOrNull { it.referenceTransactionId == tx.id && it.type == TransactionType.CANCELLATION }
                    val correctionChild = transactions.firstOrNull { it.referenceTransactionId == tx.id && it.type == TransactionType.CORRECTION }

                    val isCanceled = tx.type == TransactionType.CANCELLATION || cancellationChild != null || (tx.items.isNotEmpty() && tx.items.all { it.quantity == 0L })
                    val isCorrected = !isCanceled && (tx.type == TransactionType.CORRECTION || correctionChild != null)

                    val activeItems = correctionChild?.items?.ifEmpty { tx.items } ?: tx.items
                    val refTx = transactions.firstOrNull { it.id == tx.referenceTransactionId }

                    AdminTransactionAccordionCard(
                        transaction = tx,
                        activeItems = activeItems,
                        referencedTransaction = refTx,
                        isExpanded = isExpanded,
                        isCanceled = isCanceled,
                        isCorrected = isCorrected,
                        onExpandToggle = { onRequestToggleExpand(tx.id) },
                        onStornoPurchaseWithUpdatedItems = viewModel::stornoPurchaseWithUpdatedItems,
                        onStornoNonPurchase = viewModel::stornoNonPurchaseTransaction
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminTransactionAccordionCard(
    transaction: Transaction,
    activeItems: List<TransactionItem>,
    referencedTransaction: Transaction?,
    isExpanded: Boolean,
    isCanceled: Boolean,
    isCorrected: Boolean,
    onExpandToggle: () -> Unit,
    onStornoPurchaseWithUpdatedItems: (Transaction, List<TransactionItem>) -> Unit,
    onStornoNonPurchase: (Transaction) -> Unit
) {
    val strings = I18n.current
    val dateStr = remember(transaction.timestamp) { Formatting.formatTimestamp(transaction.timestamp) }

    var isStornoMode by remember(transaction.id) { mutableStateOf(false) }
    var draftItems by remember(transaction.id, activeItems) { mutableStateOf(activeItems) }

    var weightInputStrings by remember(transaction.id, activeItems) {
        mutableStateOf(activeItems.mapIndexed { idx, item -> idx to item.quantity.toString() }.toMap())
    }

    LaunchedEffect(isExpanded) {
        if (!isExpanded) {
            isStornoMode = false
            draftItems = activeItems
            weightInputStrings = activeItems.mapIndexed { idx, item -> idx to item.quantity.toString() }.toMap()
        }
    }

    var showStornoEverythingConfirm by remember { mutableStateOf(false) }
    var showUpdateItemsConfirm by remember { mutableStateOf(false) }
    var showStornoNonPurchaseConfirm by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = SurfaceWhite,
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DividerBorder)),
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExpandToggle() }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = transaction.userNameSnapshot,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryNavy
                    )

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = when (transaction.type) {
                            TransactionType.PURCHASE -> AccentNavy.copy(alpha = 0.12f)
                            TransactionType.ADMIN_DEPOSIT -> ColorSuccessEmerald.copy(alpha = 0.12f)
                            TransactionType.ADMIN_WITHDRAWAL -> ColorWarningAmber.copy(alpha = 0.15f)
                            TransactionType.CANCELLATION -> ColorDangerCrimson.copy(alpha = 0.12f)
                            TransactionType.CORRECTION -> ColorWarningAmber.copy(alpha = 0.15f)
                        }
                    ) {
                        Text(
                            text = transaction.type.name,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = when (transaction.type) {
                                TransactionType.PURCHASE -> AccentNavy
                                TransactionType.ADMIN_DEPOSIT -> ColorSuccessEmerald
                                TransactionType.ADMIN_WITHDRAWAL -> ColorWarningAmber
                                TransactionType.CANCELLATION -> ColorDangerCrimson
                                TransactionType.CORRECTION -> ColorWarningAmber
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }

                    if (isCanceled) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = ColorDangerCrimson.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "REVERSED / CANCELED",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorDangerCrimson,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    } else if (isCorrected) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = ColorWarningAmber.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "CORRECTED",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorWarningAmber,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = dateStr,
                        fontSize = 13.sp,
                        color = TextSecondaryMuted
                    )
                    Text(
                        text = Formatting.formatBrl(transaction.totalAmount),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (transaction.totalAmount < 0) ColorDangerCrimson else PrimaryNavy
                    )
                    Text(
                        text = if (isExpanded) "▲" else "▼",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondaryMuted
                    )
                }
            }

            if (isExpanded) {
                HorizontalDivider(color = DividerBorder)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (transaction.note != null || referencedTransaction != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = SurfaceContainerHighLight,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (transaction.note != null) {
                                    Text("Description: ${transaction.note}", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = PrimaryNavy)
                                }
                                if (referencedTransaction != null) {
                                    val refDate = Formatting.formatTimestamp(referencedTransaction.timestamp)
                                    val refAmount = Formatting.formatBrl(kotlin.math.abs(referencedTransaction.totalAmount))
                                    Text(
                                        text = "🔗 Reference: ${referencedTransaction.type.name} on $refDate ($refAmount)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = AccentNavy
                                    )
                                }
                            }
                        }
                    }

                    if (transaction.type == TransactionType.PURCHASE && draftItems.isNotEmpty()) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isStornoMode) "✏️ Adjust Item Quantities below:" else "Purchased Items (${transaction.items.size})",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryNavy
                                )

                                if (!isCanceled && !isStornoMode) {
                                    Button(
                                        onClick = { isStornoMode = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = AccentNavy),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        Text("✏️ Edit / Storno Items", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = SurfaceWhite,
                                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DividerBorder)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(SurfaceContainerHighLight)
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Product", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondaryMuted, modifier = Modifier.weight(1.8f))
                                        Text("Unit Price", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondaryMuted, modifier = Modifier.weight(1f))
                                        if (isStornoMode && !isCanceled) {
                                            Text("Original", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondaryMuted, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                            Text("Adjusted Qty/Weight", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentNavy, modifier = Modifier.weight(2.2f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                        } else {
                                            Text("Qty / Weight", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondaryMuted, modifier = Modifier.weight(1.5f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                        }
                                        Text("Line Total", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondaryMuted, modifier = Modifier.weight(1.2f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                                    }

                                    HorizontalDivider(color = DividerBorder)

                                    draftItems.forEachIndexed { index, item ->
                                        val origItem = transaction.items.getOrNull(index) ?: item
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = item.productName,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = PrimaryNavy,
                                                modifier = Modifier.weight(1.8f)
                                            )

                                            Text(
                                                text = "${Formatting.formatBrl(item.unitPriceAtPurchase)}${if (item.unitType == UnitType.WEIGHT) "/kg" else ""}",
                                                fontSize = 13.sp,
                                                color = TextSecondaryMuted,
                                                modifier = Modifier.weight(1f)
                                            )

                                            if (isStornoMode && !isCanceled) {
                                                Text(
                                                    text = Formatting.formatQuantity(origItem.quantity, origItem.unitType),
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = TextSecondaryMuted,
                                                    modifier = Modifier.weight(1f),
                                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                                )

                                                Box(modifier = Modifier.weight(2.2f), contentAlignment = Alignment.Center) {
                                                    if (item.unitType == UnitType.WEIGHT) {
                                                        Surface(
                                                            shape = RoundedCornerShape(8.dp),
                                                            color = SurfaceWhite,
                                                            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AccentNavy)),
                                                            modifier = Modifier.width(110.dp).height(38.dp)
                                                        ) {
                                                            Row(
                                                                modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.Center
                                                            ) {
                                                                androidx.compose.foundation.text.BasicTextField(
                                                                    value = weightInputStrings[index] ?: item.quantity.toString(),
                                                                    onValueChange = { input ->
                                                                        weightInputStrings = weightInputStrings + (index to input)
                                                                        val parsedGrams = input.toLongOrNull() ?: 0L
                                                                        draftItems = draftItems.toMutableList().also { list ->
                                                                            list[index] = item.copy(quantity = parsedGrams)
                                                                        }
                                                                    },
                                                                    textStyle = androidx.compose.ui.text.TextStyle(
                                                                        fontSize = 14.sp,
                                                                        fontWeight = FontWeight.Bold,
                                                                        color = PrimaryNavy,
                                                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                                                    ),
                                                                    singleLine = true,
                                                                    modifier = Modifier.weight(1f)
                                                                )
                                                                Spacer(modifier = Modifier.width(4.dp))
                                                                Text("g", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextSecondaryMuted)
                                                            }
                                                        }
                                                    } else {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                        ) {
                                                            OutlinedButton(
                                                                onClick = {
                                                                    if (item.quantity > 0) {
                                                                        val newQty = item.quantity - 1
                                                                        draftItems = draftItems.toMutableList().also { list ->
                                                                            list[index] = item.copy(quantity = newQty)
                                                                        }
                                                                    }
                                                                },
                                                                shape = RoundedCornerShape(6.dp),
                                                                modifier = Modifier.size(32.dp),
                                                                contentPadding = PaddingValues(0.dp)
                                                            ) {
                                                                Text("-", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                                            }

                                                            Text(
                                                                text = item.quantity.toString(),
                                                                fontSize = 13.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = PrimaryNavy,
                                                                modifier = Modifier.padding(horizontal = 2.dp)
                                                            )

                                                            OutlinedButton(
                                                                onClick = {
                                                                    val newQty = item.quantity + 1
                                                                    draftItems = draftItems.toMutableList().also { list ->
                                                                        list[index] = item.copy(quantity = newQty)
                                                                    }
                                                                },
                                                                shape = RoundedCornerShape(6.dp),
                                                                modifier = Modifier.size(32.dp),
                                                                contentPadding = PaddingValues(0.dp)
                                                            ) {
                                                                Text("+", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                                            }
                                                        }
                                                    }
                                                }
                                            } else {
                                                Box(modifier = Modifier.weight(1.5f), contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = Formatting.formatQuantity(item.quantity, item.unitType),
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = PrimaryNavy
                                                    )
                                                }
                                            }

                                            Text(
                                                text = Formatting.formatBrl(item.totalLinePrice),
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = PrimaryNavy,
                                                modifier = Modifier.weight(1.2f),
                                                textAlign = androidx.compose.ui.text.style.TextAlign.End
                                            )
                                        }
                                        if (index < draftItems.size - 1) {
                                            HorizontalDivider(color = DividerBorder.copy(alpha = 0.5f))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (isStornoMode && transaction.type == TransactionType.PURCHASE) {
                        val originalCost = kotlin.math.abs(transaction.totalAmount)
                        val newCost = draftItems.sumOf { it.totalLinePrice }
                        val costDiff = newCost - originalCost

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = SurfaceContainerHighLight,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text("Original Order Total: ${Formatting.formatBrl(originalCost)}", fontSize = 13.sp, color = TextSecondaryMuted)
                                    Text("New Order Total: ${Formatting.formatBrl(newCost)}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = PrimaryNavy)
                                }

                                if (costDiff != 0L) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (costDiff < 0) ColorSuccessEmerald.copy(alpha = 0.15f) else ColorDangerCrimson.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = if (costDiff < 0) "Refund: ${Formatting.formatBrl(kotlin.math.abs(costDiff))}" else "Charge: ${Formatting.formatBrl(costDiff)}",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (costDiff < 0) ColorSuccessEmerald else ColorDangerCrimson,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = DividerBorder)

                    if (!isCanceled) {
                        if (transaction.type == TransactionType.PURCHASE) {
                            if (isStornoMode) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        OutlinedButton(
                                            onClick = {
                                                draftItems = activeItems
                                                weightInputStrings = activeItems.mapIndexed { idx, item -> idx to item.quantity.toString() }.toMap()
                                                isStornoMode = false
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.height(44.dp)
                                        ) {
                                            Text("Cancel", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        }

                                        Button(
                                            onClick = { showStornoEverythingConfirm = true },
                                            colors = ButtonDefaults.buttonColors(containerColor = ColorDangerCrimson),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.height(44.dp)
                                        ) {
                                            Text("🛑 Storno Everything", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SurfaceWhite)
                                        }

                                        val isDraftModified = draftItems != activeItems
                                        Button(
                                            onClick = { showUpdateItemsConfirm = true },
                                            enabled = isDraftModified,
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = AccentNavy,
                                                disabledContainerColor = SurfaceContainerHighLight
                                            ),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.height(44.dp)
                                        ) {
                                            Text("🔄 Apply Storno Changes", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { showStornoNonPurchaseConfirm = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = ColorDangerCrimson),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(44.dp)
                                ) {
                                    Text("🛑 Storno Transaction", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SurfaceWhite)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showStornoEverythingConfirm) {
        val originalCost = kotlin.math.abs(transaction.totalAmount)
        AlertDialog(
            onDismissRequest = { showStornoEverythingConfirm = false },
            title = { Text("Approval Required: Storno Everything", fontWeight = FontWeight.Bold, color = ColorDangerCrimson) },
            text = {
                Text(
                    "Are you sure you want to storno the ENTIRE purchase transaction for user ${transaction.userNameSnapshot}?\n\n" +
                            "• Refund Amount: ${Formatting.formatBrl(originalCost)} to account balance\n" +
                            "• Inventory: All purchased item quantities will be returned to stock"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val zeroedItems = transaction.items.map { it.copy(quantity = 0L) }
                        onStornoPurchaseWithUpdatedItems(transaction, zeroedItems)
                        showStornoEverythingConfirm = false
                        isStornoMode = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorDangerCrimson)
                ) {
                    Text("Approve Complete Storno", color = SurfaceWhite, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showStornoEverythingConfirm = false }) {
                    Text(strings.cancel)
                }
            }
        )
    }

    if (showUpdateItemsConfirm) {
        val originalCost = kotlin.math.abs(transaction.totalAmount)
        val updatedCost = draftItems.sumOf { it.totalLinePrice }
        val costDiff = updatedCost - originalCost

        AlertDialog(
            onDismissRequest = { showUpdateItemsConfirm = false },
            title = { Text("Approval Required: Storno Selected Items", fontWeight = FontWeight.Bold, color = AccentNavy) },
            text = {
                Text(
                    "Are you sure you want to apply the selected item quantity adjustments for user ${transaction.userNameSnapshot}?\n\n" +
                            "• Original Purchase Total: ${Formatting.formatBrl(originalCost)}\n" +
                            "• New Order Total: ${Formatting.formatBrl(updatedCost)}\n" +
                            "• Account Balance Adjustment: ${if (costDiff < 0) "Refund" else "Charge"} ${Formatting.formatBrl(kotlin.math.abs(costDiff))}\n" +
                            "• Inventory Stock: Will be updated automatically according to item changes."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onStornoPurchaseWithUpdatedItems(transaction, draftItems)
                        showUpdateItemsConfirm = false
                        isStornoMode = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentNavy)
                ) {
                    Text("Approve Selected Item Changes", color = SurfaceWhite, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showUpdateItemsConfirm = false }) {
                    Text(strings.cancel)
                }
            }
        )
    }

    if (showStornoNonPurchaseConfirm) {
        val refundCents = -transaction.totalAmount
        AlertDialog(
            onDismissRequest = { showStornoNonPurchaseConfirm = false },
            title = { Text("Approval Required: Storno ${transaction.type.name}", fontWeight = FontWeight.Bold, color = ColorDangerCrimson) },
            text = {
                Text(
                    "Are you sure you want to storno this ${transaction.type.name} transaction for user ${transaction.userNameSnapshot}?\n\n" +
                            "• Account Balance Adjustment: ${if (refundCents >= 0) "+" else ""}${Formatting.formatBrl(refundCents)}"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onStornoNonPurchase(transaction)
                        showStornoNonPurchaseConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorDangerCrimson)
                ) {
                    Text("Approve Storno", color = SurfaceWhite, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showStornoNonPurchaseConfirm = false }) {
                    Text(strings.cancel)
                }
            }
        )
    }
}
