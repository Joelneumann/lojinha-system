package de.joelneumann.lojinha.ui.components.admin.transactions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.joelneumann.lojinha.domain.model.Transaction
import de.joelneumann.lojinha.domain.model.TransactionItem
import de.joelneumann.lojinha.domain.model.TransactionType
import de.joelneumann.lojinha.ui.components.admin.AdminAccordionCard
import de.joelneumann.lojinha.ui.components.admin.AdminBadgeType
import de.joelneumann.lojinha.ui.components.admin.AdminStatusBadge
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.theme.*
import de.joelneumann.lojinha.ui.utils.Formatting

@Composable
fun AdminTransactionAccordionCard(
    transaction: Transaction,
    activeItems: List<TransactionItem>,
    referencedTransaction: Transaction?,
    isExpanded: Boolean,
    isCanceled: Boolean,
    isCorrected: Boolean,
    onExpandToggle: () -> Unit,
    onStornoPurchaseWithUpdatedItems: (Transaction, List<TransactionItem>) -> Unit,
    onStornoNonPurchase: (Transaction) -> Unit,
    modifier: Modifier = Modifier
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

    val typeBadgeType = when (transaction.type) {
        TransactionType.PURCHASE -> AdminBadgeType.NAVY
        TransactionType.ADMIN_DEPOSIT -> AdminBadgeType.SUCCESS
        TransactionType.ADMIN_WITHDRAWAL -> AdminBadgeType.WARNING
        TransactionType.CANCELLATION -> AdminBadgeType.DANGER
        TransactionType.CORRECTION -> AdminBadgeType.WARNING
    }

    AdminAccordionCard(
        title = transaction.userNameSnapshot,
        isExpanded = isExpanded,
        onExpandToggle = onExpandToggle,
        modifier = modifier,
        headerBadges = {
            AdminStatusBadge(
                text = transaction.type.name,
                type = typeBadgeType
            )
            if (isCanceled) {
                AdminStatusBadge(
                    text = "REVERSED / CANCELED",
                    type = AdminBadgeType.DANGER
                )
            } else if (isCorrected) {
                AdminStatusBadge(
                    text = "CORRECTED",
                    type = AdminBadgeType.WARNING
                )
            }
        },
        headerRightContent = {
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
        }
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
            TransactionItemsTable(
                transaction = transaction,
                draftItems = draftItems,
                isStornoMode = isStornoMode,
                isCanceled = isCanceled,
                weightInputStrings = weightInputStrings,
                onWeightInputChanged = { index, textInput, parsedGrams ->
                    weightInputStrings = weightInputStrings + (index to textInput)
                    draftItems = draftItems.toMutableList().also { list ->
                        list[index] = list[index].copy(quantity = parsedGrams)
                    }
                },
                onQuantityChanged = { index, newQty ->
                    draftItems = draftItems.toMutableList().also { list ->
                        list[index] = list[index].copy(quantity = newQty)
                    }
                },
                onToggleStornoMode = { isStornoMode = true }
            )
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
