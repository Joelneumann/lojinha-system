package de.joelneumann.lojinha.ui.components.admin.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.joelneumann.lojinha.domain.model.Transaction
import de.joelneumann.lojinha.domain.model.TransactionItem
import de.joelneumann.lojinha.domain.model.UnitType
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.theme.*
import de.joelneumann.lojinha.ui.utils.Formatting

@Composable
fun PurchaseCorrectionDialog(
    originalTransaction: Transaction,
    currentItems: List<TransactionItem>,
    onApplyCorrection: (originalTx: Transaction, currentItems: List<TransactionItem>, newItems: List<TransactionItem>) -> Unit,
    onDismiss: () -> Unit
) {
    val strings = I18n.current

    var draftItems by remember { mutableStateOf(currentItems) }
    var weightInputStrings by remember {
        mutableStateOf(currentItems.mapIndexed { idx, item -> idx to item.quantity.toString() }.toMap())
    }

    var showStornoAllConfirm by remember { mutableStateOf(false) }

    val originalCost = remember(originalTransaction) { kotlin.math.abs(originalTransaction.totalAmount) }
    val currentCost = remember(currentItems) { currentItems.sumOf { it.totalLinePrice } }
    val newCost = remember(draftItems) { draftItems.sumOf { it.totalLinePrice } }
    val costDiff = newCost - currentCost
    val isModified = draftItems != currentItems

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = SurfaceWhite,
            shadowElevation = 8.dp,
            modifier = Modifier
                .widthIn(min = 600.dp, max = 740.dp)
                .fillMaxWidth(0.9f)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Modal Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = strings.correctPurchaseDialogTitle(originalTransaction.userNameSnapshot),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryNavy
                        )
                        val dateStr = Formatting.formatTimestamp(originalTransaction.timestamp)
                        val origAmountStr = Formatting.formatBrl(originalCost)
                        Text(
                            text = "$dateStr • ${strings.originalOrderTotalLabel(origAmountStr)}",
                            fontSize = 13.sp,
                            color = TextSecondaryMuted
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = strings.cancel,
                            tint = TextSecondaryMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Items Table
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SurfaceWhite,
                    border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(DividerBorder)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        // Table Header
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
                            Text(strings.headerOriginal, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondaryMuted, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                            Text(strings.headerCurrent, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondaryMuted, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                            Text(strings.headerCorrection, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentNavy, modifier = Modifier.weight(1.8f), textAlign = TextAlign.Center)
                            Text(strings.headerLineTotal, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondaryMuted, modifier = Modifier.weight(1.1f), textAlign = TextAlign.End)
                        }

                        HorizontalDivider(color = DividerBorder)

                        // Table Rows
                        draftItems.forEachIndexed { index, item ->
                            val origItem = originalTransaction.items.firstOrNull { it.productId == item.productId } ?: item
                            val currItem = currentItems.firstOrNull { it.productId == item.productId } ?: item

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = item.productName,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = PrimaryNavy,
                                    modifier = Modifier.weight(1.8f)
                                )

                                Text(
                                    text = "${Formatting.formatBrl(item.unitPriceAtPurchase)}${if (item.unitType == UnitType.WEIGHT) "/kg" else ""}",
                                    fontSize = 12.sp,
                                    color = TextSecondaryMuted,
                                    modifier = Modifier.weight(1f)
                                )

                                // Original quantity
                                Text(
                                    text = Formatting.formatQuantity(origItem.quantity, origItem.unitType),
                                    fontSize = 12.sp,
                                    color = TextSecondaryMuted,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center
                                )

                                // Current effective quantity
                                Text(
                                    text = Formatting.formatQuantity(currItem.quantity, currItem.unitType),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = PrimaryNavy,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center
                                )

                                // Correction Input Field
                                Box(modifier = Modifier.weight(1.8f), contentAlignment = Alignment.Center) {
                                    if (item.unitType == UnitType.WEIGHT) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = SurfaceWhite,
                                            border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(AccentNavy)),
                                            modifier = Modifier.width(100.dp).height(34.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                BasicTextField(
                                                    value = weightInputStrings[index] ?: item.quantity.toString(),
                                                    onValueChange = { input ->
                                                        val digits = input.filter { it.isDigit() }
                                                        val parsedGrams = digits.toLongOrNull() ?: 0L
                                                        weightInputStrings = weightInputStrings + (index to digits)
                                                        draftItems = draftItems.toMutableList().also { list ->
                                                            list[index] = list[index].copy(quantity = parsedGrams)
                                                        }
                                                    },
                                                    textStyle = TextStyle(
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = PrimaryNavy,
                                                        textAlign = TextAlign.Center
                                                    ),
                                                    singleLine = true,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Spacer(modifier = Modifier.width(2.dp))
                                                Text("g", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondaryMuted)
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
                                                        draftItems = draftItems.toMutableList().also { list ->
                                                            list[index] = list[index].copy(quantity = item.quantity - 1)
                                                        }
                                                    }
                                                },
                                                enabled = item.quantity > 0,
                                                shape = RoundedCornerShape(6.dp),
                                                modifier = Modifier.size(30.dp),
                                                contentPadding = PaddingValues(0.dp)
                                            ) {
                                                Text("-", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                            }

                                            Text(
                                                text = item.quantity.toString(),
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = PrimaryNavy,
                                                modifier = Modifier.padding(horizontal = 4.dp)
                                            )

                                            OutlinedButton(
                                                onClick = {
                                                    draftItems = draftItems.toMutableList().also { list ->
                                                        list[index] = list[index].copy(quantity = item.quantity + 1)
                                                    }
                                                },
                                                shape = RoundedCornerShape(6.dp),
                                                modifier = Modifier.size(30.dp),
                                                contentPadding = PaddingValues(0.dp)
                                            ) {
                                                Text("+", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }

                                // Line Total
                                Text(
                                    text = Formatting.formatBrl(item.totalLinePrice),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryNavy,
                                    modifier = Modifier.weight(1.1f),
                                    textAlign = TextAlign.End
                                )
                            }

                            if (index < draftItems.size - 1) {
                                HorizontalDivider(color = DividerBorder.copy(alpha = 0.5f))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Financial Summary Banner
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SurfaceContainerHighLight,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                text = strings.currentOrderTotalLabel(Formatting.formatBrl(currentCost)),
                                fontSize = 13.sp,
                                color = TextSecondaryMuted
                            )
                            Text(
                                text = strings.newOrderTotalLabel(Formatting.formatBrl(newCost)),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryNavy
                            )
                        }

                        if (costDiff != 0L) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (costDiff < 0) ColorSuccessEmerald.copy(alpha = 0.15f) else ColorDangerCrimson.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = if (costDiff < 0) {
                                        strings.refundAdjustmentLabel(Formatting.formatBrl(kotlin.math.abs(costDiff)))
                                    } else {
                                        strings.chargeAdjustmentLabel(Formatting.formatBrl(costDiff))
                                    },
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (costDiff < 0) ColorSuccessEmerald else ColorDangerCrimson,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = TextSecondaryMuted.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = strings.noChangesLabel,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextSecondaryMuted,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Dialog Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { showStornoAllConfirm = true },
                        colors = ButtonDefaults.buttonColors(containerColor = ColorDangerCrimson),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(44.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Block,
                                contentDescription = null,
                                tint = SurfaceWhite,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = strings.stornoEntirePurchaseBtn,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = SurfaceWhite
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(44.dp)
                        ) {
                            Text(strings.cancel, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }

                        Button(
                            onClick = {
                                onApplyCorrection(originalTransaction, currentItems, draftItems)
                            },
                            enabled = isModified,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentNavy,
                                disabledContainerColor = SurfaceContainerHighLight
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(44.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = if (isModified) SurfaceWhite else TextSecondaryMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = strings.applyCorrectionBtn,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isModified) SurfaceWhite else TextSecondaryMuted
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showStornoAllConfirm) {
        AlertDialog(
            onDismissRequest = { showStornoAllConfirm = false },
            title = {
                Text(
                    strings.approvalStornoEverythingTitle,
                    fontWeight = FontWeight.Bold,
                    color = ColorDangerCrimson
                )
            },
            text = {
                Text(
                    "Are you sure you want to completely storno this purchase for user ${originalTransaction.userNameSnapshot}?\n\n" +
                            "• Account Balance Refund: ${Formatting.formatBrl(currentCost)}\n" +
                            "• Inventory: All remaining items (${currentItems.filter { it.quantity > 0 }.size} products) will be restored to stock."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val zeroedItems = currentItems.map { it.copy(quantity = 0L) }
                        onApplyCorrection(originalTransaction, currentItems, zeroedItems)
                        showStornoAllConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorDangerCrimson)
                ) {
                    Text(strings.approveCompleteStorno, color = SurfaceWhite, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showStornoAllConfirm = false }) {
                    Text(strings.cancel)
                }
            }
        )
    }
}
