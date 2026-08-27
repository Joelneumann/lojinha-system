package de.joelneumann.lojinha.ui.components.admin.products

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.joelneumann.lojinha.domain.model.Barcode
import de.joelneumann.lojinha.domain.model.Product
import de.joelneumann.lojinha.domain.model.UnitType
import de.joelneumann.lojinha.ui.components.admin.*
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.theme.*
import de.joelneumann.lojinha.ui.utils.Formatting

@Composable
fun AdminProductAccordionCard(
    product: Product,
    allProducts: List<Product>,
    isExpanded: Boolean,
    onExpandToggle: () -> Unit,
    onSaveProduct: (Product) -> Unit,
    onAdjustStock: (String, Long) -> Unit,
    onToggleActive: (Product) -> Unit,
    onDeleteProduct: (Product) -> Unit,
    onUnsavedStateChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = I18n.current

    var draftName by remember(product.id, product.name) { mutableStateOf(product.name) }
    var draftPriceBrl by remember(product.id, product.basePrice) {
        mutableStateOf(Formatting.formatBrl(product.basePrice).replace("R$", "").trim())
    }
    var draftUnitType by remember(product.id, product.unitType) { mutableStateOf(product.unitType) }
    var draftStock by remember(product.id, product.stockQuantity, product.unitType) {
        mutableStateOf(Formatting.formatStockForAdmin(product.stockQuantity, product.unitType))
    }
    var draftMarkup by remember(product.id, product.customMarkupPercent) {
        mutableStateOf(product.customMarkupPercent?.toString() ?: "")
    }
    var draftBarcodes by remember(product.id, product.barcodes) { mutableStateOf(product.barcodes) }

    var newBarcodeCode by remember(product.id) { mutableStateOf("") }
    var newBarcodeDesc by remember(product.id) { mutableStateOf("") }

    val newBarcodeConflictProduct = remember(newBarcodeCode, allProducts, product.id) {
        val trimmed = newBarcodeCode.trim()
        if (trimmed.isBlank()) null
        else allProducts.firstOrNull { p -> p.id != product.id && p.barcodes.any { b -> b.code.equals(trimmed, ignoreCase = true) } }
    }

    val assignedBarcodeConflictProduct = remember(draftBarcodes, allProducts, product.id) {
        allProducts.firstOrNull { p -> p.id != product.id && p.barcodes.any { b -> draftBarcodes.any { db -> db.code.equals(b.code, ignoreCase = true) } } }
    }

    var stockDeltaInput by remember(product.id) { mutableStateOf("") }
    var pendingStockAdjustment by remember { mutableStateOf<Long?>(null) }
    var showToggleActiveConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val initialPriceBrl = remember(product.basePrice) { Formatting.formatBrl(product.basePrice).replace("R$", "").trim() }
    val initialStockAdmin = remember(product.stockQuantity, product.unitType) { Formatting.formatStockForAdmin(product.stockQuantity, product.unitType) }

    val hasUnsaved = remember(
        draftName, draftPriceBrl, draftUnitType, draftStock, draftMarkup, draftBarcodes, product
    ) {
        draftName != product.name ||
                draftPriceBrl != initialPriceBrl ||
                draftUnitType != product.unitType ||
                draftStock != initialStockAdmin ||
                draftMarkup != (product.customMarkupPercent?.toString() ?: "") ||
                draftBarcodes != product.barcodes
    }

    LaunchedEffect(hasUnsaved, isExpanded) {
        if (isExpanded) {
            onUnsavedStateChanged(hasUnsaved)
        }
    }

    AdminAccordionCard(
        title = product.name,
        isExpanded = isExpanded,
        onExpandToggle = onExpandToggle,
        hasUnsaved = hasUnsaved,
        modifier = modifier,
        headerBadges = {
            if (!product.isActive) {
                AdminStatusBadge(
                    text = "Disabled",
                    type = AdminBadgeType.DANGER
                )
            }
        },
        headerRightContent = {
            Text(
                text = "Base Price: ${Formatting.formatBrl(product.basePrice)}",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = PrimaryNavy
            )
            Text(
                text = "Stock: ${Formatting.formatQuantity(product.stockQuantity, product.unitType)}",
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
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Inventory,
                        contentDescription = null,
                        tint = PrimaryNavy,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (product.unitType == UnitType.PIECE) "Stock Delta (+/- units):" else "Stock Delta (+/- kg):",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryNavy
                    )
                }

                OutlinedTextField(
                    value = stockDeltaInput,
                    onValueChange = { stockDeltaInput = it },
                    placeholder = { Text(if (product.unitType == UnitType.PIECE) "e.g. +10 or -5" else "e.g. +2.500 or -0.500", fontSize = 13.sp, color = TextSecondaryMuted) },
                    textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SurfaceWhite,
                        unfocusedContainerColor = SurfaceWhite,
                        focusedBorderColor = AccentNavy,
                        unfocusedBorderColor = DividerBorder
                    ),
                    singleLine = true
                )

                Button(
                    onClick = {
                        val deltaDb = Formatting.parseAdminStockToDb(stockDeltaInput, product.unitType)
                        if (deltaDb != null && deltaDb != 0L) {
                            pendingStockAdjustment = deltaDb
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentNavy),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(44.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Inventory,
                            contentDescription = null,
                            tint = SurfaceWhite,
                            modifier = Modifier.size(16.dp)
                        )
                        Text("Adjust Stock", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SurfaceWhite)
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AdminLabeledField(
                label = "Product Name",
                value = draftName,
                onValueChange = { draftName = it },
                modifier = Modifier.weight(1.5f)
            )

            AdminLabeledField(
                label = "Base Price (R$)",
                value = draftPriceBrl,
                onValueChange = { draftPriceBrl = it },
                modifier = Modifier.weight(1f)
            )
        }

        AdminSegmentedOptionsRow(
            label = "Unit Type",
            options = UnitType.entries,
            selected = draftUnitType,
            onSelect = { draftUnitType = it },
            optionLabel = { it.name }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AdminLabeledField(
                label = if (draftUnitType == UnitType.PIECE) "Absolute Stock (Units):" else "Absolute Stock (kg):",
                value = draftStock,
                onValueChange = { draftStock = it },
                modifier = Modifier.weight(1f)
            )

            AdminLabeledField(
                label = "Custom Markup % (Optional)",
                value = draftMarkup,
                onValueChange = { draftMarkup = it },
                placeholder = "Standard",
                modifier = Modifier.weight(1f)
            )
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            Text("Associated Barcodes (${draftBarcodes.size})", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PrimaryNavy)
            Spacer(modifier = Modifier.height(6.dp))

            if (draftBarcodes.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    draftBarcodes.forEach { b ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = SurfaceContainerHighLight,
                            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DividerBorder))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Sell,
                                    contentDescription = null,
                                    tint = PrimaryNavy,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text("${b.code}${if (b.description != null) " (${b.description})" else ""}", fontSize = 12.sp, color = PrimaryNavy)
                                Text(
                                    text = "✕",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ColorDangerCrimson,
                                    modifier = Modifier.clickable {
                                        draftBarcodes = draftBarcodes.filter { it.code != b.code }
                                    }
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newBarcodeCode,
                    onValueChange = { newBarcodeCode = it },
                    placeholder = { Text("Barcode Code", fontSize = 12.sp) },
                    textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = newBarcodeDesc,
                    onValueChange = { newBarcodeDesc = it },
                    placeholder = { Text("Description (Optional)", fontSize = 12.sp) },
                    textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )

                Button(
                    onClick = {
                        if (newBarcodeCode.isNotBlank() && newBarcodeConflictProduct == null) {
                            val code = newBarcodeCode.trim()
                            if (draftBarcodes.none { it.code.equals(code, ignoreCase = true) }) {
                                draftBarcodes = draftBarcodes + Barcode(code, newBarcodeDesc.trim().ifBlank { null })
                                newBarcodeCode = ""
                                newBarcodeDesc = ""
                            }
                        }
                    },
                    enabled = newBarcodeCode.isNotBlank() && newBarcodeConflictProduct == null,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentNavy),
                    modifier = Modifier.height(52.dp)
                ) {
                    Text("+ Add", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (newBarcodeConflictProduct != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        tint = ColorDangerCrimson,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Barcode '${newBarcodeCode.trim()}' is already assigned to product '${newBarcodeConflictProduct.name}'!",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorDangerCrimson
                    )
                }
            }

            if (assignedBarcodeConflictProduct != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        tint = ColorDangerCrimson,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Contains barcode assigned to product '${assignedBarcodeConflictProduct.name}'!",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorDangerCrimson
                    )
                }
            }
        }

        HorizontalDivider(color = DividerBorder)

        AdminCardActionsRow(
            hasUnsaved = hasUnsaved,
            onSave = {
                val priceCents = kotlin.math.round((draftPriceBrl.replace(',', '.').toDoubleOrNull() ?: 0.0) * 100).toLong()
                val stock = Formatting.parseAdminStockToDb(draftStock, draftUnitType) ?: 0L
                val markup = draftMarkup.toDoubleOrNull()
                val updated = product.copy(
                    name = draftName.trim(),
                    basePrice = priceCents,
                    unitType = draftUnitType,
                    stockQuantity = stock,
                    customMarkupPercent = markup,
                    barcodes = draftBarcodes
                )
                onSaveProduct(updated)
                onUnsavedStateChanged(false)
            },
            onRevert = {
                draftName = product.name
                draftPriceBrl = initialPriceBrl
                draftUnitType = product.unitType
                draftStock = initialStockAdmin
                draftMarkup = product.customMarkupPercent?.toString() ?: ""
                draftBarcodes = product.barcodes
                newBarcodeCode = ""
                newBarcodeDesc = ""
                onUnsavedStateChanged(false)
            },
            saveEnabled = hasUnsaved && draftName.isNotBlank() && assignedBarcodeConflictProduct == null,
            toggleStatusText = if (product.isActive) "Disable" else "Enable",
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

        AlertDialog(
            onDismissRequest = { pendingStockAdjustment = null },
            title = { Text("Confirm Stock Adjustment", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Are you sure you want to ${if (isAddition) "ADD" else "REMOVE"} $formattedDelta ${if (isAddition) "to" else "from"} ${product.name}'s inventory?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAdjustStock(product.id, delta)
                        pendingStockAdjustment = null
                        stockDeltaInput = ""
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isAddition) ColorSuccessEmerald else ColorDangerCrimson
                    )
                ) {
                    Text("Confirm", color = SurfaceWhite, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingStockAdjustment = null }) {
                    Text(strings.cancel)
                }
            }
        )
    }

    if (showToggleActiveConfirm) {
        val actionText = if (product.isActive) "disable" else "enable"
        AlertDialog(
            onDismissRequest = { showToggleActiveConfirm = false },
            title = { Text("Confirm Product Status Change", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to $actionText product '${product.name}'?") },
            confirmButton = {
                Button(
                    onClick = {
                        onToggleActive(product)
                        showToggleActiveConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (product.isActive) ColorWarningAmber else ColorSuccessEmerald
                    )
                ) {
                    Text("Confirm", color = SurfaceWhite, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showToggleActiveConfirm = false }) {
                    Text(strings.cancel)
                }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Confirm Delete Product", fontWeight = FontWeight.Bold, color = ColorDangerCrimson) },
            text = { Text("Are you sure you want to permanently delete product '${product.name}'?") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteProduct(product)
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorDangerCrimson)
                ) {
                    Text("Yes, Delete Product", color = SurfaceWhite, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirm = false }) {
                    Text(strings.cancel)
                }
            }
        )
    }
}
