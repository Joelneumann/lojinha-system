package de.joelneumann.lojinha.ui.components.admin.veggie

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import de.joelneumann.lojinha.domain.model.VeggieProduct
import de.joelneumann.lojinha.domain.model.VeggieUnitType
import de.joelneumann.lojinha.ui.components.admin.AdminBadgeType
import de.joelneumann.lojinha.ui.components.admin.AdminCountPill
import de.joelneumann.lojinha.ui.components.admin.AdminExpandableSection
import de.joelneumann.lojinha.ui.components.admin.AdminStatusBadge
import de.joelneumann.lojinha.ui.components.general.ConfirmationDialog
import de.joelneumann.lojinha.ui.components.general.SearchInputField
import de.joelneumann.lojinha.ui.components.veggie.VeggieProductImage
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.theme.*
import de.joelneumann.lojinha.ui.utils.Formatting
import de.joelneumann.lojinha.ui.utils.confirmationDialogKeys
import de.joelneumann.lojinha.ui.utils.containsIgnoreAccents
import de.joelneumann.lojinha.ui.utils.currentTimeMillis
import de.joelneumann.lojinha.ui.utils.generateUuid

@Composable
fun AdminVeggieCatalogSection(
    products: List<VeggieProduct>,
    unitTypes: List<VeggieUnitType>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSaveProduct: (product: VeggieProduct, newImageBytes: ByteArray?, removeExistingImage: Boolean) -> Unit,
    onToggleProductActive: (VeggieProduct) -> Unit,
    onDeleteProduct: (VeggieProduct) -> Unit,
    onSaveUnitType: (VeggieUnitType) -> Unit,
    onDeleteUnitType: (String) -> Unit,
    isSavingProduct: Boolean = false,
    modifier: Modifier = Modifier
) {
    val strings = I18n.current
    val unitMap = remember(unitTypes) { unitTypes.associateBy { it.id } }

    var editingProduct by remember { mutableStateOf<VeggieProduct?>(null) }
    var showUnitsDialog by remember { mutableStateOf(false) }
    var productToDelete by remember { mutableStateOf<VeggieProduct?>(null) }
    var productToToggleActive by remember { mutableStateOf<VeggieProduct?>(null) }
    var selectedSortOption by remember { mutableStateOf(VeggieSortOption.NAME_ASC) }
    var selectedUnitFilterId by remember { mutableStateOf<String?>(null) }

    val nonDeleted = remember(products) { products.filter { !it.isDeleted } }

    val filteredActiveProducts = remember(nonDeleted, searchQuery, selectedUnitFilterId, selectedSortOption) {
        var list = nonDeleted.filter { it.isActive }
        if (selectedUnitFilterId != null) {
            list = list.filter { it.unitTypeId == selectedUnitFilterId }
        }
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim()
            list = list.filter {
                it.name.containsIgnoreAccents(q) || (it.description?.containsIgnoreAccents(q) == true)
            }
        }
        list.sortWithOption(selectedSortOption)
    }

    val filteredDisabledProducts = remember(nonDeleted, searchQuery, selectedUnitFilterId, selectedSortOption) {
        var list = nonDeleted.filter { !it.isActive }
        if (selectedUnitFilterId != null) {
            list = list.filter { it.unitTypeId == selectedUnitFilterId }
        }
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim()
            list = list.filter {
                it.name.containsIgnoreAccents(q) || (it.description?.containsIgnoreAccents(q) == true)
            }
        }
        list.sortWithOption(selectedSortOption)
    }

    val totalActiveNonDeleted = remember(nonDeleted) { nonDeleted.count { it.isActive } }
    val countText = remember(searchQuery, selectedUnitFilterId, filteredActiveProducts.size, totalActiveNonDeleted) {
        if (searchQuery.isBlank() && selectedUnitFilterId == null) {
            strings.productsCountText(totalActiveNonDeleted)
        } else {
            strings.productsCountText(filteredActiveProducts.size, totalActiveNonDeleted)
        }
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Top Action Bar matching Product tab style and 56dp height
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val isNarrow = maxWidth < 920.dp

            if (isNarrow) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SearchInputField(
                        query = searchQuery,
                        onQueryChange = onSearchQueryChange,
                        placeholder = strings.searchVeggieProductsPlaceholder,
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AdminCountPill(
                            text = countText,
                            modifier = Modifier.fillMaxHeight()
                        )
                        VeggieSortButton(
                            selectedOption = selectedSortOption,
                            onOptionSelected = { selectedSortOption = it },
                            modifier = Modifier.fillMaxHeight()
                        )
                        VeggieUnitFilterButton(
                            selectedUnitId = selectedUnitFilterId,
                            unitTypes = unitTypes,
                            onUnitSelected = { selectedUnitFilterId = it },
                            modifier = Modifier.fillMaxHeight()
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Button(
                            onClick = { showUnitsDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentNavy),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.fillMaxHeight()
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = null, tint = SurfaceWhite, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(strings.manageUnitTypesBtn, fontWeight = FontWeight.Bold, color = SurfaceWhite, fontSize = 13.sp, maxLines = 1)
                        }
                        Button(
                            onClick = {
                                val defaultUnitId = unitTypes.firstOrNull { it.isActive }?.id ?: "unit_kg"
                                editingProduct = VeggieProduct(
                                    id = generateUuid(),
                                    name = "",
                                    description = null,
                                    estimatedPriceCents = null,
                                    unitTypeId = defaultUnitId,
                                    imageFileName = null,
                                    isActive = true,
                                    isDeleted = false,
                                    updatedAt = currentTimeMillis()
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentNavy),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier.fillMaxHeight()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = SurfaceWhite, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(strings.addVeggieProduct, fontWeight = FontWeight.Bold, color = SurfaceWhite, fontSize = 13.sp, maxLines = 1)
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        SearchInputField(
                            query = searchQuery,
                            onQueryChange = onSearchQueryChange,
                            placeholder = strings.searchVeggieProductsPlaceholder,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    AdminCountPill(
                        text = countText,
                        modifier = Modifier.fillMaxHeight()
                    )

                    VeggieSortButton(
                        selectedOption = selectedSortOption,
                        onOptionSelected = { selectedSortOption = it },
                        modifier = Modifier.fillMaxHeight()
                    )

                    VeggieUnitFilterButton(
                        selectedUnitId = selectedUnitFilterId,
                        unitTypes = unitTypes,
                        onUnitSelected = { selectedUnitFilterId = it },
                        modifier = Modifier.fillMaxHeight()
                    )

                    Button(
                        onClick = { showUnitsDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentNavy),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null, tint = SurfaceWhite, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(strings.manageUnitTypesBtn, fontWeight = FontWeight.Bold, color = SurfaceWhite, fontSize = 13.sp, maxLines = 1)
                    }

                    Button(
                        onClick = {
                            val defaultUnitId = unitTypes.firstOrNull { it.isActive }?.id ?: "unit_kg"
                            editingProduct = VeggieProduct(
                                id = generateUuid(),
                                name = "",
                                description = null,
                                estimatedPriceCents = null,
                                unitTypeId = defaultUnitId,
                                imageFileName = null,
                                isActive = true,
                                isDeleted = false,
                                updatedAt = currentTimeMillis()
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentNavy),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = SurfaceWhite, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(strings.addVeggieProduct, fontWeight = FontWeight.Bold, color = SurfaceWhite, fontSize = 13.sp, maxLines = 1)
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            if (filteredActiveProducts.isEmpty() && filteredDisabledProducts.isEmpty()) {
                item(key = "empty-veggie-catalog") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(strings.noMatchingProducts, color = TextSecondaryMuted, fontSize = 14.sp)
                    }
                }
            }

            items(filteredActiveProducts, key = { it.id }) { product ->
                VeggieCatalogProductRow(
                    product = product,
                    unitType = unitMap[product.unitTypeId],
                    onEdit = { editingProduct = product },
                    onToggleActive = { productToToggleActive = product },
                    onDelete = { productToDelete = product }
                )
            }

            if (filteredDisabledProducts.isNotEmpty()) {
                item(key = "disabled-veggie-section") {
                    AdminExpandableSection(
                        title = strings.disabledVeggieProductsSection,
                        countText = filteredDisabledProducts.size.toString(),
                        accentColor = ColorDangerCrimson,
                        showLabel = strings.showDisabledProducts,
                        hideLabel = strings.hideDisabledProducts
                    ) {
                        filteredDisabledProducts.forEach { product ->
                            VeggieCatalogProductRow(
                                product = product,
                                unitType = unitMap[product.unitTypeId],
                                onEdit = { editingProduct = product },
                                onToggleActive = { productToToggleActive = product },
                                onDelete = { productToDelete = product }
                            )
                        }
                    }
                }
            }
        }
    }

    if (editingProduct != null) {
        VeggieProductEditDialog(
            product = editingProduct!!,
            unitTypes = unitTypes,
            isSaving = isSavingProduct,
            onSave = { updated, newBytes, removeImg ->
                onSaveProduct(updated, newBytes, removeImg)
                editingProduct = null
            },
            onCancel = { editingProduct = null }
        )
    }

    if (showUnitsDialog) {
        VeggieUnitTypesDialog(
            unitTypes = unitTypes,
            products = products,
            onSaveUnitType = onSaveUnitType,
            onDeleteUnitType = onDeleteUnitType,
            onDismiss = { showUnitsDialog = false }
        )
    }

    if (productToToggleActive != null) {
        val target = productToToggleActive!!
        val isCurrentlyActive = target.isActive
        val dismissDialog = { productToToggleActive = null }
        val confirmToggle = {
            onToggleProductActive(target)
            productToToggleActive = null
        }

        AlertDialog(
            onDismissRequest = dismissDialog,
            properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true),
            modifier = Modifier.confirmationDialogKeys(onCancel = dismissDialog, onConfirm = confirmToggle),
            title = {
                Text(
                    text = strings.confirmProductStatusChangeTitle,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryNavy
                )
            },
            text = {
                Text(
                    text = strings.confirmProductStatusChangeMsg(isCurrentlyActive, target.name),
                    color = PrimaryNavy
                )
            },
            confirmButton = {
                Button(
                    onClick = confirmToggle,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isCurrentlyActive) ColorWarningAmber else ColorSuccessEmerald
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (isCurrentlyActive) strings.disable else strings.enable,
                        color = SurfaceWhite,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = dismissDialog,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(strings.cancel)
                }
            }
        )
    }

    if (productToDelete != null) {
        val target = productToDelete!!
        ConfirmationDialog(
            title = strings.confirmDeleteProductTitle,
            message = strings.confirmDeleteProductMsg(target.name),
            confirmText = strings.delete,
            cancelText = strings.cancel,
            confirmButtonColor = ColorDangerCrimson,
            onConfirm = {
                onDeleteProduct(target)
                productToDelete = null
            },
            onDismiss = { productToDelete = null }
        )
    }
}

@Composable
private fun VeggieCatalogProductRow(
    product: VeggieProduct,
    unitType: VeggieUnitType?,
    onEdit: () -> Unit,
    onToggleActive: () -> Unit,
    onDelete: () -> Unit
) {
    val strings = I18n.current
    val unitName = unitType?.name ?: product.unitTypeId

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = SurfaceWhite,
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DividerBorder)),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            VeggieProductImage(
                productId = product.id,
                imageFileName = product.imageFileName,
                shape = RoundedCornerShape(10.dp),
                placeholderIconSize = 24.dp,
                modifier = Modifier.size(56.dp)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = product.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (product.isActive) PrimaryNavy else TextSecondaryMuted
                    )
                    AdminStatusBadge(
                        text = unitName,
                        type = if (unitType?.allowDecimals == true) AdminBadgeType.SUCCESS else AdminBadgeType.NAVY
                    )
                    if (!product.isActive) {
                        AdminStatusBadge(
                            text = strings.disabled,
                            type = AdminBadgeType.DANGER
                        )
                    }
                }

                if (!product.description.isNullOrBlank()) {
                    Text(
                        text = product.description,
                        fontSize = 12.sp,
                        color = TextSecondaryMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = product.estimatedPriceCents?.let { "${Formatting.formatBrl(it)} / $unitName" }
                        ?: strings.veggieEstimatedPriceUnknown,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (product.estimatedPriceCents != null) AccentNavy else TextSecondaryMuted
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = strings.edit, tint = AccentNavy, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onToggleActive, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = if (product.isActive) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (product.isActive) strings.disable else strings.enable,
                        tint = PrimaryNavy,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = strings.delete, tint = ColorDangerCrimson, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
