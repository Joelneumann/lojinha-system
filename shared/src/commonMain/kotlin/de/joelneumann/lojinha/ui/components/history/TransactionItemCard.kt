package de.joelneumann.lojinha.ui.components.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.joelneumann.lojinha.domain.model.SecondaryCurrency
import de.joelneumann.lojinha.domain.model.Transaction
import de.joelneumann.lojinha.domain.model.TransactionType
import de.joelneumann.lojinha.domain.model.UnitType
import de.joelneumann.lojinha.ui.components.admin.AdminBadgeType
import de.joelneumann.lojinha.ui.components.admin.AdminStatusBadge
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.i18n.LanguageManager
import de.joelneumann.lojinha.ui.screens.TransactionWithBalance
import de.joelneumann.lojinha.ui.theme.*
import de.joelneumann.lojinha.ui.utils.Formatting

@Composable
fun TransactionItemCard(
    txWithBalance: TransactionWithBalance,
    allTransactionsMap: Map<String, Transaction>,
    rate: Double,
    secondaryCurrency: SecondaryCurrency,
    modifier: Modifier = Modifier
) {
    val tx = txWithBalance.transaction
    val balanceBefore = txWithBalance.balanceBefore
    val balanceAfter = txWithBalance.balanceAfter
    val strings = I18n.current
    val dateStr = remember(tx.timestamp, LanguageManager.currentLanguage) {
        Formatting.formatTimestamp(tx.timestamp)
    }

    val childCancellation = remember(tx.id, allTransactionsMap) {
        allTransactionsMap.values.firstOrNull { it.referenceTransactionId == tx.id && it.type == TransactionType.CANCELLATION }
    }
    val childCorrections = remember(tx.id, allTransactionsMap) {
        allTransactionsMap.values.filter { it.referenceTransactionId == tx.id && it.type == TransactionType.CORRECTION }.sortedBy { it.timestamp }
    }

    val isCanceled = childCancellation != null || (tx.type == TransactionType.PURCHASE && tx.items.isNotEmpty() && tx.items.all { it.quantity == 0L })
    val isCorrected = tx.type == TransactionType.PURCHASE && !isCanceled && childCorrections.isNotEmpty()
    val allChildren = remember(tx.id, allTransactionsMap) {
        allTransactionsMap.values.filter { it.referenceTransactionId == tx.id }
    }
    val cumulativeDelta = remember(allChildren, isCanceled, tx.totalAmount) {
        if (allChildren.isNotEmpty()) {
            allChildren.sumOf { it.totalAmount }
        } else if (isCanceled) {
            -tx.totalAmount
        } else {
            0L
        }
    }

    val displayNote = remember(tx, allTransactionsMap, LanguageManager.currentLanguage) {
        if (tx.type == TransactionType.CANCELLATION && tx.referenceTransactionId != null) {
            val refTx = allTransactionsMap[tx.referenceTransactionId]
            if (refTx != null) {
                val refDateStr = Formatting.formatTimestamp(refTx.timestamp)
                val refTypeStr = when (refTx.type) {
                    TransactionType.PURCHASE -> strings.historyTypePurchase
                    TransactionType.ADMIN_DEPOSIT -> if (refTx.items.isNotEmpty()) strings.historyTypeCustomIncome else strings.historyTypeDeposit
                    TransactionType.ADMIN_WITHDRAWAL -> if (refTx.items.isNotEmpty()) strings.historyTypeCustomExpense else strings.historyTypeDebit
                    TransactionType.CANCELLATION -> strings.historyTypeCancellation
                    TransactionType.CORRECTION -> strings.historyTypeCorrection
                }
                strings.cancellationNote(refTypeStr, refDateStr)
            } else {
                tx.note
            }
        } else {
            tx.note
        }
    }

    val isCustomExpense = tx.type == TransactionType.ADMIN_WITHDRAWAL && tx.items.isNotEmpty()
    val isCustomIncome = tx.type == TransactionType.ADMIN_DEPOSIT && tx.items.isNotEmpty()
    val typePair = when (tx.type) {
        TransactionType.PURCHASE -> Icons.Default.ShoppingCart to strings.historyTypePurchase
        TransactionType.ADMIN_DEPOSIT -> {
            if (isCustomIncome) {
                Icons.Default.AddCircle to strings.historyTypeCustomIncome
            } else {
                Icons.Default.AccountBalanceWallet to strings.historyTypeDeposit
            }
        }
        TransactionType.ADMIN_WITHDRAWAL -> {
            if (isCustomExpense) {
                Icons.AutoMirrored.Filled.ReceiptLong to strings.historyTypeCustomExpense
            } else {
                Icons.Default.Payments to strings.historyTypeDebit
            }
        }
        TransactionType.CANCELLATION -> Icons.AutoMirrored.Filled.Undo to strings.historyTypeCancellation
        TransactionType.CORRECTION -> Icons.Default.Edit to strings.historyTypeCorrection
    }
    val typeIcon = typePair.first
    val typeText = typePair.second

    val isPositive = tx.totalAmount > 0
    val amountColor = if (isPositive) ColorSuccessEmerald else PrimaryNavy

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(DividerBorder)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = typeIcon,
                        contentDescription = null,
                        tint = PrimaryNavy,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = typeText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryNavy
                    )
                    if (isCanceled) {
                        AdminStatusBadge(
                            text = strings.badgeReversedCanceled,
                            type = AdminBadgeType.DANGER
                        )
                    } else if (isCorrected) {
                        AdminStatusBadge(
                            text = strings.badgeCorrected,
                            type = AdminBadgeType.WARNING
                        )
                    }
                    Text(
                        text = "•  $dateStr",
                        fontSize = 13.sp,
                        color = TextSecondaryMuted
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    if (isCanceled) {
                        val delta = if (cumulativeDelta != 0L) cumulativeDelta else -tx.totalAmount
                        val finalAmount = tx.totalAmount + delta
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = Formatting.formatBrl(tx.totalAmount),
                                fontSize = 13.sp,
                                color = TextSecondaryMuted,
                                textDecoration = TextDecoration.LineThrough
                            )
                            Text(
                                text = "(${if (delta > 0) "+" else ""}${Formatting.formatBrl(delta)})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (delta > 0) ColorSuccessEmerald else ColorDangerCrimson
                            )
                            Text(
                                text = "➔ ${Formatting.formatBrl(finalAmount)}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondaryMuted
                            )
                        }
                    } else if (tx.type == TransactionType.PURCHASE && isCorrected && cumulativeDelta != 0L) {
                        val netAmount = tx.totalAmount + cumulativeDelta
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = Formatting.formatBrl(tx.totalAmount),
                                fontSize = 13.sp,
                                color = TextSecondaryMuted
                            )
                            Text(
                                text = "(${if (cumulativeDelta > 0) "+" else ""}${Formatting.formatBrl(cumulativeDelta)})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (cumulativeDelta > 0) ColorSuccessEmerald else ColorDangerCrimson
                            )
                            Text(
                                text = "➔ ${Formatting.formatBrl(netAmount)}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (netAmount < 0) ColorDangerCrimson else PrimaryNavy
                            )
                        }
                    } else {
                        val isNegative = tx.totalAmount < 0
                        Text(
                            text = "${if (!isNegative && tx.type != TransactionType.PURCHASE) "+" else ""}${Formatting.formatBrl(tx.totalAmount)}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isNegative) ColorDangerCrimson else amountColor
                        )
                    }
                    val secFormatted = Formatting.formatSecondaryCurrency(tx.totalAmount, secondaryCurrency, rate)
                    if (secFormatted.isNotEmpty()) {
                        Text(secFormatted, fontSize = 12.sp, color = TextSecondaryMuted)
                    }
                }
            }

            if (!displayNote.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Note: $displayNote",
                    fontSize = 13.sp,
                    color = TextSecondarySubtle
                )
            }

            // Balance Flow Banner: Before ➔ After
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = SurfaceContainerHighLight,
                border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(DividerBorder)),
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${strings.balance}:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondarySubtle
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = Formatting.formatBrl(balanceBefore),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondaryMuted
                        )

                        Text(
                            text = "➔",
                            fontSize = 12.sp,
                            color = TextSecondarySubtle
                        )

                        Text(
                            text = Formatting.formatBrl(balanceAfter),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (balanceAfter >= 0) PrimaryNavy else ColorDangerCrimson
                        )
                    }
                }
            }

            // Direct Visibility of Items inside Transaction Card
            if ((tx.type == TransactionType.CORRECTION || tx.type == TransactionType.CANCELLATION) && tx.items.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = DividerBorder)
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = strings.updatedItemsHeader(tx.items.size),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryNavy
                    )
                    tx.items.forEach { item ->
                        val (prevQty, diff, adjustmentAmount) = if (item.previousQuantity != null) {
                            val prev = item.previousQuantity!!
                            val d = item.quantity - prev
                            val diffCost = when (item.unitType) {
                                UnitType.PIECE -> item.unitPriceAtPurchase * d
                                UnitType.WEIGHT -> {
                                    val newC = kotlin.math.round((item.unitPriceAtPurchase * item.quantity) / 1000.0).toLong()
                                    val prevC = kotlin.math.round((item.unitPriceAtPurchase * prev) / 1000.0).toLong()
                                    newC - prevC
                                }
                            }
                            Triple(prev, d, -diffCost)
                        } else if (tx.items.size == 1 && item.unitPriceAtPurchase > 0) {
                            val d = when (item.unitType) {
                                UnitType.PIECE -> -tx.totalAmount / item.unitPriceAtPurchase
                                UnitType.WEIGHT -> kotlin.math.round((-tx.totalAmount * 1000.0) / item.unitPriceAtPurchase).toLong()
                            }
                            Triple(item.quantity - d, d, tx.totalAmount)
                        } else {
                            val prev = allTransactionsMap[tx.referenceTransactionId]?.items?.firstOrNull { it.productId == item.productId }?.quantity ?: item.quantity
                            val d = item.quantity - prev
                            val diffCost = when (item.unitType) {
                                UnitType.PIECE -> item.unitPriceAtPurchase * d
                                UnitType.WEIGHT -> {
                                    val newC = kotlin.math.round((item.unitPriceAtPurchase * item.quantity) / 1000.0).toLong()
                                    val prevC = kotlin.math.round((item.unitPriceAtPurchase * prev) / 1000.0).toLong()
                                    newC - prevC
                                }
                            }
                            Triple(prev, d, -diffCost)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                Text(
                                    text = "• ${item.productName}:",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = PrimaryNavy
                                )
                                Text(
                                    text = "${Formatting.formatQuantity(prevQty, item.unitType)} ➔ ${Formatting.formatQuantity(item.quantity, item.unitType)}",
                                    fontSize = 13.sp,
                                    color = TextSecondarySubtle
                                )
                                if (diff != 0L) {
                                    Text(
                                        text = "(${if (diff > 0) "+" else ""}${Formatting.formatQuantity(diff, item.unitType)})",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (diff < 0) ColorSuccessEmerald else ColorDangerCrimson
                                    )
                                }
                            }
                            Text(
                                text = "${if (adjustmentAmount > 0) "+" else ""}${Formatting.formatBrl(adjustmentAmount)}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (adjustmentAmount > 0) ColorSuccessEmerald else if (adjustmentAmount < 0) ColorDangerCrimson else TextSecondaryMuted
                            )
                        }
                    }
                }
            } else {
                val displayedItems = if (isCorrected) {
                    de.joelneumann.lojinha.ui.viewmodel.admin.AdminTransactionsViewModel.computeEffectiveItems(tx.items, childCorrections)
                } else {
                    tx.items
                }
                val nonZeroItems = displayedItems.filter { it.quantity > 0 }
                if (nonZeroItems.isNotEmpty() && tx.type != TransactionType.CANCELLATION && tx.type != TransactionType.ADMIN_WITHDRAWAL && tx.type != TransactionType.ADMIN_DEPOSIT) {
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = DividerBorder)
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        nonZeroItems.forEach { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "• ${Formatting.formatQuantity(item.quantity, item.unitType)} ${item.productName} x ${Formatting.formatBrl(item.unitPriceAtPurchase)}",
                                    fontSize = 13.sp,
                                    color = if (isCanceled) TextSecondaryMuted else TextSecondarySubtle,
                                    textDecoration = if (isCanceled) TextDecoration.LineThrough else TextDecoration.None
                                )
                                Text(
                                    text = Formatting.formatBrl(item.totalLinePrice),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isCanceled) TextSecondaryMuted else PrimaryNavy,
                                    textDecoration = if (isCanceled) TextDecoration.LineThrough else TextDecoration.None
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
