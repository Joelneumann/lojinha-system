package de.joelneumann.lojinha.ui.components.admin.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
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
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.theme.*
import de.joelneumann.lojinha.ui.utils.Formatting

@Composable
fun TransactionItemsTable(
    transaction: Transaction,
    effectiveItems: List<TransactionItem>,
    isCorrected: Boolean,
    modifier: Modifier = Modifier
) {
    val strings = I18n.current

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = strings.purchasedItemsHeader(effectiveItems.size),
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
                    if (isCorrected) {
                        Text(strings.headerOriginal, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondaryMuted, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                        Text(strings.headerModifier, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentNavy, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                        Text(strings.headerCurrent, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondaryMuted, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    } else {
                        Text(strings.headerQtyWeight, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondaryMuted, modifier = Modifier.weight(1.5f), textAlign = TextAlign.Center)
                    }
                    Text(strings.headerLineTotal, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondaryMuted, modifier = Modifier.weight(1.2f), textAlign = TextAlign.End)
                }

                HorizontalDivider(color = DividerBorder)

                effectiveItems.forEachIndexed { index, item ->
                    val origItem = transaction.items.firstOrNull { it.productId == item.productId } ?: item
                    val qtyDiff = item.quantity - origItem.quantity

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

                        if (isCorrected) {
                            Text(
                                text = Formatting.formatQuantity(origItem.quantity, origItem.unitType),
                                fontSize = 13.sp,
                                color = TextSecondaryMuted,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center
                            )

                            Text(
                                text = if (qtyDiff != 0L) "(${if (qtyDiff > 0) "+" else ""}${Formatting.formatQuantity(qtyDiff, origItem.unitType)})" else "-",
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
                        } else {
                            Text(
                                text = Formatting.formatQuantity(item.quantity, item.unitType),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryNavy,
                                modifier = Modifier.weight(1.5f),
                                textAlign = TextAlign.Center
                            )
                        }

                        Text(
                            text = Formatting.formatBrl(item.totalLinePrice),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryNavy,
                            modifier = Modifier.weight(1.2f),
                            textAlign = TextAlign.End
                        )
                    }

                    if (index < effectiveItems.size - 1) {
                        HorizontalDivider(color = DividerBorder.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}
