package de.joelneumann.lojinha.ui.components.admin.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.joelneumann.lojinha.domain.model.Transaction
import de.joelneumann.lojinha.domain.model.TransactionItem
import de.joelneumann.lojinha.domain.model.UnitType
import de.joelneumann.lojinha.domain.model.calculateAdjustment
import de.joelneumann.lojinha.ui.i18n.I18n

import de.joelneumann.lojinha.ui.theme.*
import de.joelneumann.lojinha.ui.utils.Formatting

@Composable
fun CorrectionItemsTable(
    transaction: Transaction,
    referencedTransaction: Transaction? = null,
    modifier: Modifier = Modifier
) {
    val strings = I18n.current
    val items = transaction.items
    if (items.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = strings.updatedItemsHeader(items.size),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryNavy
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = SurfaceWhite,
            border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(DividerBorder)),
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
                    Text(strings.headerProduct, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondaryMuted, modifier = Modifier.weight(1.8f))
                    Text(strings.headerUnitPrice, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondaryMuted, modifier = Modifier.weight(1f))
                    Text(strings.headerPrevious, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondaryMuted, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    Text(strings.headerModifier, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentNavy, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    Text(strings.headerNew, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondaryMuted, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    Text(strings.headerAdjustment, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondaryMuted, modifier = Modifier.weight(1.2f), textAlign = TextAlign.End)
                }

                HorizontalDivider(color = DividerBorder)

                items.forEachIndexed { index, item ->
                    val (prevQty, qtyDiff, adjustmentAmount) = item.calculateAdjustment(
                        parentTransaction = referencedTransaction,
                        totalTransactionAmount = transaction.totalAmount,
                        totalItemCount = items.size
                    )

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

                        Text(
                            text = Formatting.formatQuantity(prevQty, item.unitType),
                            fontSize = 13.sp,
                            color = TextSecondaryMuted,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = if (qtyDiff != 0L) "(${if (qtyDiff > 0) "+" else ""}${Formatting.formatQuantity(qtyDiff, item.unitType)})" else "-",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (qtyDiff < 0) ColorSuccessEmerald else if (qtyDiff > 0) ColorDangerCrimson else TextSecondaryMuted,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = Formatting.formatQuantity(item.quantity, item.unitType),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryNavy,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = "${if (adjustmentAmount > 0) "+" else ""}${Formatting.formatBrl(adjustmentAmount)}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (adjustmentAmount > 0) ColorSuccessEmerald else if (adjustmentAmount < 0) ColorDangerCrimson else TextSecondaryMuted,
                            modifier = Modifier.weight(1.2f),
                            textAlign = TextAlign.End
                        )
                    }

                    if (index < items.size - 1) {
                        HorizontalDivider(color = DividerBorder.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}
