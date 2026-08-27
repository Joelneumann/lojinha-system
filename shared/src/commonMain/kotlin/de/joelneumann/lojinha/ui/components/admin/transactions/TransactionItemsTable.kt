package de.joelneumann.lojinha.ui.components.admin.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.joelneumann.lojinha.domain.model.Transaction
import de.joelneumann.lojinha.domain.model.TransactionItem
import de.joelneumann.lojinha.domain.model.UnitType
import de.joelneumann.lojinha.ui.theme.*
import de.joelneumann.lojinha.ui.utils.Formatting

@Composable
fun TransactionItemsTable(
    transaction: Transaction,
    draftItems: List<TransactionItem>,
    isStornoMode: Boolean,
    isCanceled: Boolean,
    weightInputStrings: Map<Int, String>,
    onWeightInputChanged: (Int, String, Long) -> Unit,
    onQuantityChanged: (Int, Long) -> Unit,
    onToggleStornoMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (isStornoMode) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = PrimaryNavy,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = if (isStornoMode) "Adjust Item Quantities below:" else "Purchased Items (${transaction.items.size})",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryNavy
                )
            }

            if (!isCanceled && !isStornoMode) {
                Button(
                    onClick = onToggleStornoMode,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentNavy),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = SurfaceWhite,
                            modifier = Modifier.size(14.dp)
                        )
                        Text("Edit / Storno Items", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
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
                                                    val parsedGrams = input.toLongOrNull() ?: 0L
                                                    onWeightInputChanged(index, input, parsedGrams)
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
                                                    onQuantityChanged(index, item.quantity - 1)
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
                                                onQuantityChanged(index, item.quantity + 1)
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
