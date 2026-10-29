package de.joelneumann.lojinha.ui.components.admin.products

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.joelneumann.lojinha.domain.model.Product
import de.joelneumann.lojinha.domain.model.UnitType
import de.joelneumann.lojinha.ui.components.admin.*
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.theme.*
import de.joelneumann.lojinha.ui.utils.Formatting
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.window.DialogProperties
import de.joelneumann.lojinha.ui.utils.confirmationDialogKeys

private data class PendingStockAndPriceAdjustment(
    val delta: Long,
    val incomingPriceCents: Long,
    val calculatedNewBasePrice: Long
)

@Composable
fun AdminProductAccordionCard(
    product: Product,
    globalMarkup: Double = 0.0,
    isExpanded: Boolean,
    onExpandToggle: () -> Unit,
    onEditProduct: (Product) -> Unit,
    onAdjustStock: (String, Long, Long?) -> Unit,
    onToggleActive: (Product) -> Unit,
    onDeleteProduct: (Product) -> Unit,
    onViewHistory: ((Product) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val strings = I18n.current

    var stockDeltaInput by remember(product.id) { mutableStateOf("") }
    var newPriceInput by remember(product.id) { mutableStateOf("") }
    var pendingStockAdjustment by remember { mutableStateOf<Long?>(null) }
    var pendingStockAndPriceAdjustment by remember { mutableStateOf<PendingStockAndPriceAdjustment?>(null) }
    var showToggleActiveConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val deltaDb = remember(stockDeltaInput, product.unitType) {
        Formatting.parseAdminStockToDb(stockDeltaInput, product.unitType)
    }
    val isDeltaAddition = deltaDb != null && deltaDb > 0L
    val isDeltaNegative = deltaDb != null && deltaDb < 0L

    val isPriceFieldEnabled = isDeltaAddition || stockDeltaInput.isBlank()
    val parsedPriceCents = remember(newPriceInput) {
        Formatting.parsePriceInputToCents(newPriceInput)
    }
    val isPriceInvalid = newPriceInput.isNotBlank() && parsedPriceCents == null
    val canAdjust = deltaDb != null && deltaDb != 0L && (!isDeltaAddition || newPriceInput.isBlank() || parsedPriceCents != null)

    val handleTriggerAdjustment = {
        val delta = Formatting.parseAdminStockToDb(stockDeltaInput, product.unitType)
        if (delta != null && delta != 0L) {
            val priceTrimmed = newPriceInput.trim()
            if (delta > 0L && priceTrimmed.isNotBlank()) {
                val priceCents = Formatting.parsePriceInputToCents(priceTrimmed)
                if (priceCents != null) {
                    val calculated = Formatting.calculateMovingAveragePrice(
                        oldStock = product.stockQuantity,
                        oldPriceCents = product.basePrice,
                        addedStock = delta,
                        incomingPriceCents = priceCents
                    )
                    pendingStockAndPriceAdjustment = PendingStockAndPriceAdjustment(
                        delta = delta,
                        incomingPriceCents = priceCents,
                        calculatedNewBasePrice = calculated
                    )
                }
            } else {
                pendingStockAdjustment = delta
            }
        }
    }

    AdminAccordionCard(
        title = product.name,
        isExpanded = isExpanded,
        onExpandToggle = onExpandToggle,
        hasUnsaved = false,
        modifier = modifier,
        headerBadges = {
            if (!product.isActive) {
                AdminStatusBadge(
                    text = strings.disabled,
                    type = AdminBadgeType.DANGER
                )
            }
        },
        headerRightContent = {
            val effectivePrice = product.calculateEffectiveUnitPrice(globalMarkup)
            val isCustom = product.customMarkupPercent != null
            val activeMarkup = product.customMarkupPercent ?: globalMarkup
            val markupPercentStr = Formatting.formatMarkupPercent(activeMarkup)
            val markupTag = if (isCustom) "$markupPercentStr • ${strings.markupCustomSuffix}" else markupPercentStr

            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "${strings.basePrice}: ${Formatting.formatBrl(product.basePrice)}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PrimaryNavy
                )
                Text(
                    text = "${strings.sellingPriceLabel}: ${Formatting.formatBrl(effectivePrice)} ($markupTag)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentNavy
                )
            }
            Text(
                text = "${strings.stock}: ${Formatting.formatQuantity(product.stockQuantity, product.unitType)}",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (product.stockQuantity > 0) ColorSuccessEmerald else ColorDangerCrimson
            )
        }
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = SurfaceContainerHighLight,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Row 1: Stock delta input
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.width(220.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Inventory,
                                contentDescription = null,
                                tint = PrimaryNavy,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (product.unitType == UnitType.PIECE) strings.stockDeltaPieceLabel else strings.stockDeltaWeightLabel,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryNavy
                            )
                        }

                        OutlinedTextField(
                            value = stockDeltaInput,
                            onValueChange = { stockDeltaInput = it },
                            placeholder = {
                                Text(
                                    if (product.unitType == UnitType.PIECE) strings.stockDeltaPiecePlaceholder else strings.stockDeltaWeightPlaceholder,
                                    fontSize = 13.sp,
                                    color = TextSecondaryMuted
                                )
                            },
                            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .onPreviewKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown && (event.key == Key.Enter || event.key == Key.NumPadEnter)) {
                                        handleTriggerAdjustment()
                                        true
                                    } else false
                                },
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = SurfaceWhite,
                                unfocusedContainerColor = SurfaceWhite,
                                focusedBorderColor = AccentNavy,
                                unfocusedBorderColor = DividerBorder
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            singleLine = true
                        )
                    }

                    // Row 2: Optional new price input under unit input
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.width(220.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AttachMoney,
                                contentDescription = null,
                                tint = if (isPriceFieldEnabled) PrimaryNavy else TextSecondaryMuted,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = strings.stockNewPurchasePriceLabel,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isPriceFieldEnabled) PrimaryNavy else TextSecondaryMuted
                            )
                        }

                        OutlinedTextField(
                            value = newPriceInput,
                            onValueChange = { newPriceInput = it },
                            enabled = isPriceFieldEnabled,
                            isError = isPriceInvalid,
                            placeholder = {
                                Text(
                                    if (isDeltaNegative) strings.stockPriceDisabledForNegativeHint else strings.stockNewPurchasePricePlaceholder,
                                    fontSize = 13.sp,
                                    color = TextSecondaryMuted
                                )
                            },
                            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .onPreviewKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown && (event.key == Key.Enter || event.key == Key.NumPadEnter)) {
                                        handleTriggerAdjustment()
                                        true
                                    } else false
                                },
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = SurfaceWhite,
                                unfocusedContainerColor = if (isPriceFieldEnabled) SurfaceWhite else SurfaceContainerHighLight,
                                disabledContainerColor = SurfaceContainerHighLight,
                                focusedBorderColor = AccentNavy,
                                unfocusedBorderColor = DividerBorder
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { handleTriggerAdjustment() }),
                            singleLine = true
                        )
                    }
                }

                Button(
                    onClick = handleTriggerAdjustment,
                    enabled = canAdjust,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentNavy),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Text(strings.adjustStockBtn, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SurfaceWhite)
                }
            }
        }

        AdminCardActionsRow(
            onEdit = { onEditProduct(product) },
            onHistory = onViewHistory?.let { { it(product) } },
            toggleStatusText = if (product.isActive) strings.disable else strings.enable,
            onToggleStatus = { showToggleActiveConfirm = true },
            isStatusActive = product.isActive,
            onDelete = { showDeleteConfirm = true }
        )
    }

    if (pendingStockAdjustment != null) {
        val delta = pendingStockAdjustment!!
        val isAddition = delta > 0
        val absDelta = kotlin.math.abs(delta)
        val formattedDelta = Formatting.formatQuantity(absDelta, product.unitType)
        val dismissDialog = { pendingStockAdjustment = null }
        val confirmAdjustment = {
            onAdjustStock(product.id, delta, null)
            pendingStockAdjustment = null
            stockDeltaInput = ""
            newPriceInput = ""
        }

        AlertDialog(
            onDismissRequest = dismissDialog,
            properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true),
            modifier = Modifier.confirmationDialogKeys(onCancel = dismissDialog, onConfirm = confirmAdjustment),
            title = { Text(strings.confirmStockAdjustmentTitle, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    strings.confirmStockAdjustmentMsg(isAddition, formattedDelta, product.name)
                )
            },
            confirmButton = {
                Button(
                    onClick = confirmAdjustment,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isAddition) ColorSuccessEmerald else ColorDangerCrimson
                    )
                ) {
                    Text(strings.confirm, color = SurfaceWhite, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = dismissDialog) {
                    Text(strings.cancel)
                }
            }
        )
    }

    if (pendingStockAndPriceAdjustment != null) {
        val pending = pendingStockAndPriceAdjustment!!
        val oldStock = product.stockQuantity
        val addedStock = pending.delta
        val newStock = (oldStock + addedStock).coerceAtLeast(0L)
        val oldBasePrice = product.basePrice
        val incomingPrice = pending.incomingPriceCents
        val newBasePrice = pending.calculatedNewBasePrice

        val oldEffectivePrice = product.calculateEffectiveUnitPrice(globalMarkup)
        val newProductCandidate = product.copy(basePrice = newBasePrice, stockQuantity = newStock)
        val newEffectivePrice = newProductCandidate.calculateEffectiveUnitPrice(globalMarkup)

        val unitSuffix = if (product.unitType == UnitType.WEIGHT) " / kg" else ""
        val oldStockStr = Formatting.formatQuantity(oldStock, product.unitType)
        val addedStockStr = Formatting.formatQuantity(addedStock, product.unitType)
        val newStockStr = Formatting.formatQuantity(newStock, product.unitType)

        val dismissDialog = { pendingStockAndPriceAdjustment = null }
        val confirmAdjustment = {
            onAdjustStock(product.id, pending.delta, pending.calculatedNewBasePrice)
            pendingStockAndPriceAdjustment = null
            stockDeltaInput = ""
            newPriceInput = ""
        }

        AlertDialog(
            onDismissRequest = dismissDialog,
            properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true),
            modifier = Modifier
                .widthIn(min = 450.dp, max = 560.dp)
                .confirmationDialogKeys(onCancel = dismissDialog, onConfirm = confirmAdjustment),
            title = {
                Text(
                    strings.confirmStockAndPriceAdjustmentTitle,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = PrimaryNavy
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        strings.confirmStockAndPriceMsg(product.name),
                        fontSize = 14.sp,
                        color = PrimaryNavy
                    )


                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = SurfaceContainerHighLight,
                        border = BorderStroke(1.dp, DividerBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Row: Current Stock & Price
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(strings.stockPriceCurrentStockLabel, fontSize = 13.sp, color = TextSecondaryMuted)
                                Text(
                                    "$oldStockStr @ ${Formatting.formatBrl(oldBasePrice)}$unitSuffix",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = PrimaryNavy
                                )
                            }

                            // Row: Incoming Stock & Price
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(strings.stockPriceIncomingStockLabel, fontSize = 13.sp, color = TextSecondaryMuted)
                                Text(
                                    "+$addedStockStr @ ${Formatting.formatBrl(incomingPrice)}$unitSuffix",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = ColorSuccessEmerald
                                )
                            }

                            HorizontalDivider(color = DividerBorder)

                            // Row: Resulting Stock
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(strings.stockPriceTotalStockLabel, fontSize = 13.sp, color = TextSecondaryMuted)
                                Text(
                                    newStockStr,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryNavy
                                )
                            }

                            // Calculation Breakdown
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    strings.stockPriceCalculationLabel,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextSecondaryMuted
                                )
                                val formulaText = if (oldStock <= 0L) {
                                    "${strings.stockPriceCurrentStockLabel} <= 0 → ${Formatting.formatBrl(incomingPrice)}$unitSuffix"
                                } else {
                                    val oldValBrl = if (product.unitType == UnitType.WEIGHT) {
                                        Formatting.formatBrl(kotlin.math.round((oldStock.toDouble() * oldBasePrice.toDouble()) / 1000.0).toLong())
                                    } else {
                                        Formatting.formatBrl(oldStock * oldBasePrice)
                                    }
                                    val addValBrl = if (product.unitType == UnitType.WEIGHT) {
                                        Formatting.formatBrl(kotlin.math.round((addedStock.toDouble() * incomingPrice.toDouble()) / 1000.0).toLong())
                                    } else {
                                        Formatting.formatBrl(addedStock * incomingPrice)
                                    }
                                    "($oldValBrl + $addValBrl) / $newStockStr = ${Formatting.formatBrl(newBasePrice)}$unitSuffix"
                                }
                                Text(
                                    formulaText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = AccentNavy
                                )
                            }
                        }
                    }

                    // Result card with new prices
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = ColorSuccessEmerald.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, ColorSuccessEmerald.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    strings.stockPriceNewBasePriceLabel,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = PrimaryNavy
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "${Formatting.formatBrl(oldBasePrice)}$unitSuffix",
                                        fontSize = 12.sp,
                                        color = TextSecondaryMuted,
                                        style = TextStyle(textDecoration = TextDecoration.LineThrough)
                                    )
                                    Text(
                                        "→",
                                        fontSize = 12.sp,
                                        color = TextSecondaryMuted
                                    )
                                    Text(
                                        "${Formatting.formatBrl(newBasePrice)}$unitSuffix",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ColorSuccessEmerald
                                    )
                                }
                            }

                            val hasMarkup = oldEffectivePrice != oldBasePrice || newEffectivePrice != newBasePrice
                            if (hasMarkup) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        strings.stockPriceNewSellingPriceLabel,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = PrimaryNavy
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "${Formatting.formatBrl(oldEffectivePrice)}$unitSuffix",
                                            fontSize = 12.sp,
                                            color = TextSecondaryMuted,
                                            style = TextStyle(textDecoration = TextDecoration.LineThrough)
                                        )
                                        Text(
                                            "→",
                                            fontSize = 12.sp,
                                            color = TextSecondaryMuted
                                        )
                                        Text(
                                            "${Formatting.formatBrl(newEffectivePrice)}$unitSuffix",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AccentNavy
                                        )
                                    }
                                }
                            }

                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = confirmAdjustment,
                    colors = ButtonDefaults.buttonColors(containerColor = ColorSuccessEmerald)
                ) {
                    Text(strings.confirm, color = SurfaceWhite, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = dismissDialog) {
                    Text(strings.cancel)
                }
            }
        )
    }


    if (showToggleActiveConfirm) {
        val dismissDialog = { showToggleActiveConfirm = false }
        val confirmToggle = {
            onToggleActive(product)
            showToggleActiveConfirm = false
        }

        AlertDialog(
            onDismissRequest = dismissDialog,
            properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true),
            modifier = Modifier.confirmationDialogKeys(onCancel = dismissDialog, onConfirm = confirmToggle),
            title = { Text(strings.confirmProductStatusChangeTitle, fontWeight = FontWeight.Bold) },
            text = { Text(strings.confirmProductStatusChangeMsg(product.isActive, product.name)) },
            confirmButton = {
                Button(
                    onClick = confirmToggle,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (product.isActive) ColorWarningAmber else ColorSuccessEmerald
                    )
                ) {
                    Text(strings.confirm, color = SurfaceWhite, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = dismissDialog) {
                    Text(strings.cancel)
                }
            }
        )
    }

    if (showDeleteConfirm) {
        val dismissDialog = { showDeleteConfirm = false }
        val confirmDelete = {
            onDeleteProduct(product)
            showDeleteConfirm = false
        }

        AlertDialog(
            onDismissRequest = dismissDialog,
            containerColor = SurfaceWhite,
            shape = RoundedCornerShape(16.dp),
            properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true),
            modifier = Modifier.confirmationDialogKeys(onCancel = dismissDialog, onConfirm = confirmDelete),
            title = { Text(strings.confirmDeleteProductTitle, fontWeight = FontWeight.Bold, color = ColorDangerCrimson) },
            text = { Text(strings.confirmDeleteProductMsg(product.name)) },
            confirmButton = {
                Button(
                    onClick = confirmDelete,
                    colors = ButtonDefaults.buttonColors(containerColor = ColorDangerCrimson)
                ) {
                    Text(strings.yesDeleteProduct, color = SurfaceWhite, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = dismissDialog) {
                    Text(strings.cancel)
                }
            }
        )
    }
}
