package de.joelneumann.lojinha.ui.screens.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import de.joelneumann.lojinha.domain.model.Barcode
import de.joelneumann.lojinha.domain.model.Product
import de.joelneumann.lojinha.domain.model.UnitType
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.theme.*
import de.joelneumann.lojinha.ui.utils.Formatting
import de.joelneumann.lojinha.ui.viewmodel.admin.AdminProductsViewModel

@Composable
fun AdminProductsTabScreen(
    viewModel: AdminProductsViewModel,
    expandedProductId: String?,
    onRequestToggleExpand: (String?) -> Unit,
    onRequestExpandProduct: (String) -> Unit,
    onUnsavedStateChanged: (Boolean) -> Unit
) {
    val products by viewModel.products.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    val showProductModal by viewModel.showProductModal.collectAsState()
    val editProduct by viewModel.editProduct.collectAsState()

    val strings = I18n.current

    val filteredProducts = remember(products, searchQuery) {
        if (searchQuery.isBlank()) products
        else products.filter { p ->
            p.name.contains(searchQuery, ignoreCase = true) ||
                    p.barcodes.any { b -> b.code.contains(searchQuery, ignoreCase = true) || (b.description != null && b.description.contains(searchQuery, ignoreCase = true)) }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        val openFirstResult = {
            if (filteredProducts.isNotEmpty()) {
                onRequestExpandProduct(filteredProducts.first().id)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = viewModel::updateSearchQuery,
                placeholder = {
                    Text(
                        text = "🔍 Search product by name or barcode...",
                        color = TextSecondaryMuted,
                        fontSize = 14.sp
                    )
                },
                textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown &&
                            (keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter)
                        ) {
                            openFirstResult()
                            true
                        } else {
                            false
                        }
                    },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { openFirstResult() }),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SurfaceWhite,
                    unfocusedContainerColor = SurfaceWhite,
                    focusedBorderColor = AccentNavy,
                    unfocusedBorderColor = DividerBorder
                ),
                singleLine = true
            )

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = SurfaceWhite,
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DividerBorder)),
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxHeight()
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isBlank()) "${products.size} Products" else "${filteredProducts.size} / ${products.size} Products",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PrimaryNavy
                    )
                }
            }

            Button(
                onClick = viewModel::openNewProductModal,
                colors = ButtonDefaults.buttonColors(containerColor = AccentNavy),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxHeight()
            ) {
                Text(strings.addProduct, color = SurfaceWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (filteredProducts.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No products found matching your search.", color = TextSecondaryMuted)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                items(filteredProducts, key = { it.id }) { product ->
                    val isExpanded = expandedProductId == product.id
                    AdminProductAccordionCard(
                        product = product,
                        allProducts = products,
                        isExpanded = isExpanded,
                        onExpandToggle = { onRequestToggleExpand(product.id) },
                        onSaveProduct = viewModel::saveProduct,
                        onAdjustStock = viewModel::adjustProductStock,
                        onToggleActive = viewModel::toggleProductActive,
                        onDeleteProduct = { viewModel.deleteProduct(it.id) },
                        onUnsavedStateChanged = onUnsavedStateChanged
                    )
                }
            }
        }
    }

    if (showProductModal && editProduct != null) {
        ProductEditDialog(
            product = editProduct!!,
            allProducts = products,
            onSave = { viewModel.saveProduct(it) },
            onCancel = { viewModel.closeProductModal() }
        )
    }
}

@Composable
private fun AdminProductAccordionCard(
    product: Product,
    allProducts: List<Product>,
    isExpanded: Boolean,
    onExpandToggle: () -> Unit,
    onSaveProduct: (Product) -> Unit,
    onAdjustStock: (String, Long) -> Unit,
    onToggleActive: (Product) -> Unit,
    onDeleteProduct: (Product) -> Unit,
    onUnsavedStateChanged: (Boolean) -> Unit
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

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = SurfaceWhite,
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(
                if (hasUnsaved && isExpanded) ColorWarningAmber else DividerBorder
            )
        ),
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExpandToggle() }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = product.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryNavy
                    )
                    if (!product.isActive) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = ColorDangerCrimson.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "Disabled",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorDangerCrimson,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (isExpanded && hasUnsaved) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = ColorWarningAmber.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "● Unsaved Edits",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorWarningAmber,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
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
                    Text(
                        text = if (isExpanded) "▲" else "▼",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondaryMuted
                    )
                }
            }

            if (isExpanded) {
                HorizontalDivider(color = DividerBorder)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
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
                            Text(
                                text = if (product.unitType == UnitType.PIECE) "📦 Stock Delta (+/- units):" else "📦 Stock Delta (+/- kg):",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryNavy
                            )

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
                                Text("📦 Adjust Stock", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SurfaceWhite)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column(modifier = Modifier.weight(1.5f)) {
                            Text("Product Name", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PrimaryNavy)
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = draftName,
                                onValueChange = { draftName = it },
                                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                shape = RoundedCornerShape(8.dp),
                                singleLine = true
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text("Base Price (R$)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PrimaryNavy)
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = draftPriceBrl,
                                onValueChange = { draftPriceBrl = it },
                                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                shape = RoundedCornerShape(8.dp),
                                singleLine = true
                            )
                        }
                    }

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("Unit Type", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PrimaryNavy)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            UnitType.entries.forEach { u ->
                                val isSel = draftUnitType == u
                                OutlinedButton(
                                    onClick = { draftUnitType = u },
                                    modifier = Modifier.weight(1f).height(38.dp),
                                    contentPadding = PaddingValues(0.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (isSel) AccentNavy else SurfaceWhite,
                                        contentColor = if (isSel) SurfaceWhite else PrimaryNavy
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(u.name, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (draftUnitType == UnitType.PIECE) "Absolute Stock (Units):" else "Absolute Stock (kg):",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = PrimaryNavy
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = draftStock,
                                onValueChange = { draftStock = it },
                                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                shape = RoundedCornerShape(8.dp),
                                singleLine = true
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text("Custom Markup % (Optional)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PrimaryNavy)
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = draftMarkup,
                                onValueChange = { draftMarkup = it },
                                placeholder = { Text("Standard", fontSize = 13.sp, color = TextSecondaryMuted) },
                                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                shape = RoundedCornerShape(8.dp),
                                singleLine = true
                            )
                        }
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
                                            Text("🏷️ ${b.code}${if (b.description != null) " (${b.description})" else ""}", fontSize = 12.sp, color = PrimaryNavy)
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
                            Text(
                                text = "❌ Barcode '${newBarcodeCode.trim()}' is already assigned to product '${newBarcodeConflictProduct.name}'!",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorDangerCrimson,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        if (assignedBarcodeConflictProduct != null) {
                            Text(
                                text = "❌ Contains barcode assigned to product '${assignedBarcodeConflictProduct.name}'!",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorDangerCrimson,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = DividerBorder)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
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
                                enabled = hasUnsaved && draftName.isNotBlank() && assignedBarcodeConflictProduct == null,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AccentNavy,
                                    disabledContainerColor = SurfaceContainerHighLight
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(42.dp)
                            ) {
                                Text(
                                    text = if (hasUnsaved) "💾 Save Changes" else "✓ Saved",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (hasUnsaved) {
                                OutlinedButton(
                                    onClick = {
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
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(42.dp)
                                ) {
                                    Text("↩️ Revert Changes", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PrimaryNavy)
                                }
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { showToggleActiveConfirm = true },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(42.dp)
                            ) {
                                Text(
                                    text = if (product.isActive) "⚠️ Disable" else "⚡ Enable",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (product.isActive) ColorWarningAmber else ColorSuccessEmerald
                                )
                            }

                            Button(
                                onClick = { showDeleteConfirm = true },
                                colors = ButtonDefaults.buttonColors(containerColor = ColorDangerCrimson),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(42.dp)
                            ) {
                                Text(
                                    text = "🗑️ Delete",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SurfaceWhite
                                )
                            }
                        }
                    }
                }
            }
        }
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

@Composable
private fun ProductEditDialog(
    product: Product,
    allProducts: List<Product>,
    onSave: (Product) -> Unit,
    onCancel: () -> Unit
) {
    val strings = I18n.current
    val isNewProduct = remember(product.id) { product.id.isBlank() || product.name.isBlank() }

    var name by remember { mutableStateOf(product.name) }
    var basePriceBrl by remember { mutableStateOf(if (isNewProduct) "0,00" else (product.basePrice.toDouble() / 100.0).toString().replace('.', ',')) }
    var unitType by remember { mutableStateOf(product.unitType) }
    var stockQuantity by remember { mutableStateOf(if (isNewProduct) "0" else Formatting.formatStockForAdmin(product.stockQuantity, product.unitType)) }
    var customMarkup by remember { mutableStateOf(product.customMarkupPercent?.toString() ?: "") }
    var barcodeCode by remember { mutableStateOf("") }
    var barcodeDesc by remember { mutableStateOf("") }
    var barcodeList by remember { mutableStateOf(product.barcodes) }

    val newBarcodeConflictProduct = remember(barcodeCode, allProducts, product.id) {
        val trimmed = barcodeCode.trim()
        if (trimmed.isBlank()) null
        else allProducts.firstOrNull { p -> p.id != product.id && p.barcodes.any { b -> b.code.equals(trimmed, ignoreCase = true) } }
    }

    val assignedBarcodeConflictProduct = remember(barcodeList, allProducts, product.id) {
        allProducts.firstOrNull { p -> p.id != product.id && p.barcodes.any { b -> barcodeList.any { bl -> bl.code.equals(b.code, ignoreCase = true) } } }
    }

    Dialog(onDismissRequest = onCancel) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = SurfaceWhite,
            shadowElevation = 8.dp,
            modifier = Modifier.width(660.dp).wrapContentHeight()
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = if (isNewProduct) strings.addProduct else strings.editProduct,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryNavy
                )

                HorizontalDivider(color = DividerBorder)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1.5f)) {
                        Text("Product Name", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PrimaryNavy)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            placeholder = { Text("Product Name", fontSize = 13.sp, color = TextSecondaryMuted) },
                            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text("Base Price (R$)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PrimaryNavy)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = basePriceBrl,
                            onValueChange = { basePriceBrl = it },
                            placeholder = { Text("0,00", fontSize = 13.sp, color = TextSecondaryMuted) },
                            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Unit Type", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PrimaryNavy)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        UnitType.entries.forEach { u ->
                            val isSel = unitType == u
                            OutlinedButton(
                                onClick = { unitType = u },
                                modifier = Modifier.weight(1f).height(38.dp),
                                contentPadding = PaddingValues(0.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isSel) AccentNavy else SurfaceWhite,
                                    contentColor = if (isSel) SurfaceWhite else PrimaryNavy
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(u.name, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (unitType == UnitType.PIECE) "Stock Quantity (Units):" else "Stock Quantity (kg):",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PrimaryNavy
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = stockQuantity,
                            onValueChange = { stockQuantity = it },
                            placeholder = {
                                Text(
                                    text = if (unitType == UnitType.PIECE) "e.g. 25 (Full numbers)" else "e.g. 2.500 (Decimal in kg)",
                                    fontSize = 13.sp,
                                    color = TextSecondaryMuted
                                )
                            },
                            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text("Custom Markup % (Optional)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PrimaryNavy)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = customMarkup,
                            onValueChange = { customMarkup = it },
                            placeholder = { Text("Standard", fontSize = 13.sp, color = TextSecondaryMuted) },
                            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Associated Barcodes (${barcodeList.size})", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PrimaryNavy)
                    Spacer(modifier = Modifier.height(6.dp))

                    if (barcodeList.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            barcodeList.forEach { b ->
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
                                        Text("🏷️ ${b.code}${if (b.description != null) " (${b.description})" else ""}", fontSize = 12.sp, color = PrimaryNavy)
                                        Text(
                                            text = "✕",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = ColorDangerCrimson,
                                            modifier = Modifier.clickable {
                                                barcodeList = barcodeList.filter { it.code != b.code }
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
                            value = barcodeCode,
                            onValueChange = { barcodeCode = it },
                            placeholder = { Text("Barcode Code", fontSize = 12.sp) },
                            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                            modifier = Modifier.weight(1f).height(56.dp),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = barcodeDesc,
                            onValueChange = { barcodeDesc = it },
                            placeholder = { Text("Description (Optional)", fontSize = 12.sp) },
                            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                            modifier = Modifier.weight(1f).height(56.dp),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true
                        )

                        Button(
                            onClick = {
                                if (barcodeCode.isNotBlank() && newBarcodeConflictProduct == null) {
                                    val code = barcodeCode.trim()
                                    if (barcodeList.none { it.code.equals(code, ignoreCase = true) }) {
                                        barcodeList = barcodeList + Barcode(code, barcodeDesc.trim().ifBlank { null })
                                        barcodeCode = ""
                                        barcodeDesc = ""
                                    }
                                }
                            },
                            enabled = barcodeCode.isNotBlank() && newBarcodeConflictProduct == null,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentNavy),
                            modifier = Modifier.height(52.dp)
                        ) {
                            Text("+ Add", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (newBarcodeConflictProduct != null) {
                        Text(
                            text = "❌ Barcode '${barcodeCode.trim()}' is already assigned to product '${newBarcodeConflictProduct.name}'!",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorDangerCrimson,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                HorizontalDivider(color = DividerBorder)

                if (assignedBarcodeConflictProduct != null) {
                    Text(
                        text = "❌ Contains barcode assigned to product '${assignedBarcodeConflictProduct.name}'!",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorDangerCrimson,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }

                val initialPriceBrl = Formatting.formatBrl(product.basePrice).removePrefix("R$ ").trim()
                val initialStockAdmin = Formatting.formatStockForAdmin(product.stockQuantity, product.unitType)
                val hasDialogChanges = name != product.name ||
                        basePriceBrl != initialPriceBrl ||
                        unitType != product.unitType ||
                        stockQuantity != initialStockAdmin ||
                        customMarkup != (product.customMarkupPercent?.toString() ?: "") ||
                        barcodeList != product.barcodes

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(strings.cancel, fontSize = 14.sp)
                    }

                    if (!isNewProduct && hasDialogChanges) {
                        OutlinedButton(
                            onClick = {
                                name = product.name
                                basePriceBrl = initialPriceBrl
                                unitType = product.unitType
                                stockQuantity = Formatting.formatStockForAdmin(product.stockQuantity, product.unitType)
                                customMarkup = product.customMarkupPercent?.toString() ?: ""
                                barcodeList = product.barcodes
                                barcodeCode = ""
                                barcodeDesc = ""
                            },
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("↩️ Revert", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = PrimaryNavy)
                        }
                    }

                    Button(
                        onClick = {
                            val priceCents = kotlin.math.round((basePriceBrl.replace(',', '.').toDoubleOrNull() ?: 0.0) * 100).toLong()
                            val stock = Formatting.parseAdminStockToDb(stockQuantity, unitType) ?: 0L
                            val markup = customMarkup.toDoubleOrNull()
                            val updated = product.copy(
                                name = name.trim(),
                                basePrice = priceCents,
                                unitType = unitType,
                                stockQuantity = stock,
                                customMarkupPercent = markup,
                                barcodes = barcodeList
                            )
                            onSave(updated)
                        },
                        enabled = name.isNotBlank() && assignedBarcodeConflictProduct == null,
                        modifier = Modifier.weight(1f).height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentNavy),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(if (isNewProduct) strings.addProduct else strings.save, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
