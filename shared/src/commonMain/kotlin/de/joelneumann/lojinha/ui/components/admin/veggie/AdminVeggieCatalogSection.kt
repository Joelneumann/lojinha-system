package de.joelneumann.lojinha.ui.components.admin.veggie

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
import de.joelneumann.lojinha.domain.model.VeggieProduct
import de.joelneumann.lojinha.domain.model.VeggieUnitType
import de.joelneumann.lojinha.ui.components.admin.AdminBadgeType
import de.joelneumann.lojinha.ui.components.admin.AdminExpandableSection
import de.joelneumann.lojinha.ui.components.admin.AdminStatusBadge
import de.joelneumann.lojinha.ui.components.general.ConfirmationDialog
import de.joelneumann.lojinha.ui.components.general.SearchInputField
import de.joelneumann.lojinha.ui.components.veggie.VeggieProductImage
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.theme.*
import de.joelneumann.lojinha.ui.utils.Formatting
import de.joelneumann.lojinha.ui.utils.containsIgnoreAccents
import de.joelneumann.lojinha.ui.utils.currentTimeMillis
import de.joelneumann.lojinha.ui.utils.generateUuid
import de.joelneumann.lojinha.ui.utils.sortedByAccentInsensitive

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

    val filteredProducts = remember(products, searchQuery) {
        val nonDeleted = products.filter { !it.isDeleted }
        val matching = if (searchQuery.isBlank()) nonDeleted
        else nonDeleted.filter {
            it.name.containsIgnoreAccents(searchQuery.trim()) ||
                    (it.description?.containsIgnoreAccents(searchQuery.trim()) == true)
        }
        matching.sortedByAccentInsensitive { it.name }
    }

    val activeProducts = remember(filteredProducts) { filteredProducts.filter { it.isActive } }
    val disabledProducts = remember(filteredProducts) { filteredProducts.filter { !it.isActive } }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Top Action Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SearchInputField(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                placeholder = strings.searchVeggieProductsPlaceholder,
                modifier = Modifier.weight(1f)
            )

            OutlinedButton(
                onClick = { showUnitsDialog = true },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.height(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = PrimaryNavy,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(strings.manageUnitTypesBtn, fontWeight = FontWeight.Bold, color = PrimaryNavy)
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
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.height(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = SurfaceWhite,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(strings.addVeggieProduct, fontWeight = FontWeight.Bold, color = SurfaceWhite)
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            if (activeProducts.isEmpty() && disabledProducts.isEmpty()) {
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

            items(activeProducts, key = { it.id }) { product ->
                VeggieCatalogProductRow(
                    product = product,
                    unitType = unitMap[product.unitTypeId],
                    onEdit = { editingProduct = product },
                    onToggleActive = { onToggleProductActive(product) },
                    onDelete = { productToDelete = product }
                )
            }

            if (disabledProducts.isNotEmpty()) {
                item(key = "disabled-veggie-section") {
                    AdminExpandableSection(
                        title = strings.disabledVeggieProductsSection,
                        countText = disabledProducts.size.toString(),
                        accentColor = ColorDangerCrimson,
                        showLabel = strings.showDisabledProducts,
                        hideLabel = strings.hideDisabledProducts
                    ) {
                        disabledProducts.forEach { product ->
                            VeggieCatalogProductRow(
                                product = product,
                                unitType = unitMap[product.unitTypeId],
                                onEdit = { editingProduct = product },
                                onToggleActive = { onToggleProductActive(product) },
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
            onManageUnitsClick = { showUnitsDialog = true },
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
