package de.joelneumann.lojinha.ui.components.admin.transactions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Edit
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
    effectiveItems: List<TransactionItem>,
    referencedTransaction: Transaction?,
    cumulativeDelta: Long,
    isExpanded: Boolean,
    isCanceled: Boolean,
    isCorrected: Boolean,
    onExpandToggle: () -> Unit,
    onOpenCorrectionModal: () -> Unit,
    onStornoNonPurchase: (Transaction) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = I18n.current
    val dateStr = remember(transaction.timestamp) { Formatting.formatTimestamp(transaction.timestamp) }

    var showStornoNonPurchaseConfirm by remember { mutableStateOf(false) }

    val typeBadgeType = when (transaction.type) {
        TransactionType.PURCHASE -> AdminBadgeType.NAVY
        TransactionType.ADMIN_DEPOSIT -> AdminBadgeType.SUCCESS
        TransactionType.ADMIN_WITHDRAWAL -> AdminBadgeType.WARNING
        TransactionType.CANCELLATION -> AdminBadgeType.DANGER
        TransactionType.CORRECTION -> AdminBadgeType.WARNING
    }

    val typeText = when (transaction.type) {
        TransactionType.PURCHASE -> strings.historyTypePurchase.uppercase()
        TransactionType.ADMIN_DEPOSIT -> strings.historyTypeDeposit.uppercase()
        TransactionType.ADMIN_WITHDRAWAL -> {
            if (transaction.items.isNotEmpty()) {
                strings.historyTypeCustomExpense.uppercase()
            } else {
                strings.historyTypeDebit.uppercase()
            }
        }
        TransactionType.CANCELLATION -> strings.historyTypeCancellation.uppercase()
        TransactionType.CORRECTION -> strings.historyTypeCorrection.uppercase()
    }

    AdminAccordionCard(
        title = transaction.userNameSnapshot,
        isExpanded = isExpanded,
        onExpandToggle = onExpandToggle,
        modifier = modifier,
        headerBadges = {
            AdminStatusBadge(
                text = typeText,
                type = typeBadgeType
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
        },
        headerRightContent = {
            Text(
                text = dateStr,
                fontSize = 13.sp,
                color = TextSecondaryMuted
            )

            if (isCanceled) {
                val delta = if (cumulativeDelta != 0L) cumulativeDelta else -transaction.totalAmount
                val finalAmount = transaction.totalAmount + delta
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = Formatting.formatBrl(transaction.totalAmount),
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
            } else if (isCorrected && cumulativeDelta != 0L) {
                val netAmount = transaction.totalAmount + cumulativeDelta
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = Formatting.formatBrl(transaction.totalAmount),
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
                val isNegative = transaction.totalAmount < 0
                Text(
                    text = "${if (!isNegative && transaction.type != TransactionType.PURCHASE) "+" else ""}${Formatting.formatBrl(transaction.totalAmount)}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isNegative) ColorDangerCrimson else if (transaction.type == TransactionType.PURCHASE) PrimaryNavy else ColorSuccessEmerald
                )
            }
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
                        val refTypeStr = when (referencedTransaction.type) {
                            TransactionType.PURCHASE -> strings.historyTypePurchase
                            TransactionType.ADMIN_DEPOSIT -> strings.historyTypeDeposit
                            TransactionType.ADMIN_WITHDRAWAL -> if (referencedTransaction.items.isNotEmpty()) strings.historyTypeCustomExpense else strings.historyTypeDebit
                            TransactionType.CANCELLATION -> strings.historyTypeCancellation
                            TransactionType.CORRECTION -> strings.historyTypeCorrection
                        }
                        Text(
                            text = "🔗 Reference: $refTypeStr on $refDate ($refAmount)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AccentNavy
                        )
                    }
                }
            }
        }

        // Items table for purchases, and updated items table for corrections (custom expenses show description in note above)
        if (transaction.type == TransactionType.PURCHASE && effectiveItems.isNotEmpty()) {
            TransactionItemsTable(
                transaction = transaction,
                effectiveItems = effectiveItems,
                isCorrected = isCorrected
            )
        } else if (transaction.type == TransactionType.CORRECTION && transaction.items.isNotEmpty()) {
            CorrectionItemsTable(
                transaction = transaction,
                referencedTransaction = referencedTransaction
            )
        }

        // Action Buttons Row
        if (!isCanceled) {
            if (transaction.type == TransactionType.PURCHASE) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onOpenCorrectionModal,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentNavy),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(40.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                tint = SurfaceWhite,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = strings.correctPurchaseBtn,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = SurfaceWhite
                            )
                        }
                    }
                }
            } else if (transaction.type == TransactionType.ADMIN_DEPOSIT || transaction.type == TransactionType.ADMIN_WITHDRAWAL) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { showStornoNonPurchaseConfirm = true },
                        colors = ButtonDefaults.buttonColors(containerColor = ColorDangerCrimson),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(40.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Block,
                                contentDescription = null,
                                tint = SurfaceWhite,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = strings.stornoTransactionBtn,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = SurfaceWhite
                            )
                        }
                    }
                }
            }
            // Note: CORRECTION and CANCELLATION have NO buttons rendered!
        }
    }

    if (showStornoNonPurchaseConfirm) {
        val refundCents = -transaction.totalAmount
        val readableType = when (transaction.type) {
            TransactionType.ADMIN_DEPOSIT -> strings.historyTypeDeposit
            TransactionType.ADMIN_WITHDRAWAL -> if (transaction.items.isNotEmpty()) strings.historyTypeCustomExpense else strings.historyTypeDebit
            else -> transaction.type.name
        }

        AlertDialog(
            onDismissRequest = { showStornoNonPurchaseConfirm = false },
            title = {
                Text(
                    text = strings.approvalStornoTxTitle(readableType),
                    fontWeight = FontWeight.Bold,
                    color = ColorDangerCrimson
                )
            },
            text = {
                Text(
                    "Are you sure you want to storno this $readableType transaction for user ${transaction.userNameSnapshot}?\n\n" +
                            "• Account Balance Adjustment: ${if (refundCents >= 0) "+" else ""}${Formatting.formatBrl(refundCents)}\n" +
                            "• Current note: ${transaction.note ?: "-"}"
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
                    Text(strings.approveStorno, color = SurfaceWhite, fontWeight = FontWeight.Bold)
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
