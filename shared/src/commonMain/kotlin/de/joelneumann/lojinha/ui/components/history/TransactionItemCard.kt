package de.joelneumann.lojinha.ui.components.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.joelneumann.lojinha.domain.model.SecondaryCurrency
import de.joelneumann.lojinha.domain.model.Transaction
import de.joelneumann.lojinha.domain.model.TransactionType
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

    val displayNote = remember(tx, allTransactionsMap, LanguageManager.currentLanguage) {
        if (tx.type == TransactionType.CANCELLATION && tx.referenceTransactionId != null) {
            val refTx = allTransactionsMap[tx.referenceTransactionId]
            if (refTx != null) {
                val refDateStr = Formatting.formatTimestamp(refTx.timestamp)
                val refTypeStr = when (refTx.type) {
                    TransactionType.PURCHASE -> strings.historyTypePurchase
                    TransactionType.ADMIN_DEPOSIT -> strings.historyTypeDeposit
                    TransactionType.ADMIN_WITHDRAWAL -> strings.historyTypeWithdrawal
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

    val typePair = when (tx.type) {
        TransactionType.PURCHASE -> Icons.Default.ShoppingCart to strings.historyTypePurchase
        TransactionType.ADMIN_DEPOSIT -> Icons.Default.AccountBalanceWallet to strings.historyTypeDeposit
        TransactionType.ADMIN_WITHDRAWAL -> Icons.Default.Payments to strings.historyTypeWithdrawal
        TransactionType.CANCELLATION -> Icons.Default.Undo to strings.historyTypeCancellation
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
                    Text(
                        text = "•  $dateStr",
                        fontSize = 13.sp,
                        color = TextSecondaryMuted
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${if (isPositive) "+" else ""}${Formatting.formatBrl(tx.totalAmount)}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = amountColor
                    )
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

            // Direct Visibility of Purchased Items inside Transaction Card
            if (tx.items.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = DividerBorder)
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    tx.items.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "• ${Formatting.formatQuantity(item.quantity, item.unitType)} ${item.productName} x ${Formatting.formatBrl(item.unitPriceAtPurchase)}",
                                fontSize = 13.sp,
                                color = TextSecondarySubtle
                            )
                            Text(
                                text = Formatting.formatBrl(item.totalLinePrice),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = PrimaryNavy
                            )
                        }
                    }
                }
            }
        }
    }
}
