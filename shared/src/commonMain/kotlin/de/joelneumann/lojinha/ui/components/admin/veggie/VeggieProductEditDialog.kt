package de.joelneumann.lojinha.ui.components.admin.veggie

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.joelneumann.lojinha.domain.model.VeggieProduct
import de.joelneumann.lojinha.domain.model.VeggieUnitType
import de.joelneumann.lojinha.ui.components.admin.AdminLabeledField
import de.joelneumann.lojinha.ui.components.general.ConfirmationDialog
import de.joelneumann.lojinha.ui.components.veggie.VeggieProductImage
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.theme.*
import de.joelneumann.lojinha.ui.utils.Formatting
import de.joelneumann.lojinha.ui.utils.currentTimeMillis
import de.joelneumann.lojinha.ui.utils.formModalKeys
import de.joelneumann.lojinha.ui.utils.pickImageBytes

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VeggieProductEditDialog(
    product: VeggieProduct,
    unitTypes: List<VeggieUnitType>,
    isSaving: Boolean = false,
    onSave: (product: VeggieProduct, newImageBytes: ByteArray?, removeExistingImage: Boolean) -> Unit,
    onCancel: () -> Unit
) {
    val strings = I18n.current
    val activeUnits = remember(unitTypes) { unitTypes.filter { it.isActive }.sortedBy { it.name.lowercase() } }
    val isNewProduct = remember(product.id, product.name) { product.name.isBlank() }

    val initialPriceInput = remember(product.estimatedPriceCents) {
        product.estimatedPriceCents?.let { Formatting.formatBrl(it).removePrefix("R$ ").trim() } ?: ""
    }

    var name by remember(product.id) { mutableStateOf(product.name) }
    var description by remember(product.id) { mutableStateOf(product.description ?: "") }
    var estimatedPriceInput by remember(product.id) { mutableStateOf(initialPriceInput) }
    var selectedUnitId by remember(product.id, activeUnits) {
        mutableStateOf(
            product.unitTypeId.takeIf { id -> activeUnits.any { it.id == id } }
                ?: activeUnits.firstOrNull()?.id
                ?: product.unitTypeId
        )
    }
    var selectedDeliveryUnitId by remember(product.id, activeUnits) {
        mutableStateOf(
            product.deliveryUnitTypeId?.takeIf { id -> activeUnits.any { it.id == id } }
        )
    }
    var selectedImageBytes by remember(product.id) { mutableStateOf<ByteArray?>(null) }
    var removeExistingImage by remember(product.id) { mutableStateOf(false) }
    var showDiscardConfirm by remember { mutableStateOf(false) }

    val parsedPriceCents = remember(estimatedPriceInput) {
        if (estimatedPriceInput.isBlank()) null
        else Formatting.parsePriceInputToCents(estimatedPriceInput)
    }
    val isPriceValid = remember(estimatedPriceInput, parsedPriceCents) {
        estimatedPriceInput.isBlank() || (parsedPriceCents != null && parsedPriceCents >= 0L)
    }

    val hasDialogChanges = name != product.name ||
            description.trim() != (product.description ?: "") ||
            estimatedPriceInput.trim() != initialPriceInput ||
            selectedUnitId != product.unitTypeId ||
            selectedDeliveryUnitId != product.deliveryUnitTypeId ||
            selectedImageBytes != null ||
            removeExistingImage

    val canSave = !isSaving &&
            name.trim().isNotBlank() &&
            selectedUnitId.isNotBlank() &&
            isPriceValid &&
            (isNewProduct || hasDialogChanges)

    val handleDismissRequest = {
        if (!isSaving) {
            if (hasDialogChanges && (!isNewProduct || name.isNotBlank() || description.isNotBlank() || estimatedPriceInput.isNotBlank() || selectedImageBytes != null)) {
                showDiscardConfirm = true
            } else {
                onCancel()
            }
        }
    }

    val handleSave = {
        if (canSave) {
            val updated = product.copy(
                name = name.trim(),
                description = description.trim().ifBlank { null },
                estimatedPriceCents = if (estimatedPriceInput.isBlank()) null else parsedPriceCents,
                unitTypeId = selectedUnitId,
                deliveryUnitTypeId = selectedDeliveryUnitId,
                imageFileName = if (removeExistingImage && selectedImageBytes == null) null else product.imageFileName,
                updatedAt = currentTimeMillis()
            )
            onSave(updated, selectedImageBytes, removeExistingImage)
        }
    }

    Dialog(
        onDismissRequest = handleDismissRequest,
        properties = DialogProperties(dismissOnClickOutside = true, dismissOnBackPress = true)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = SurfaceWhite,
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .widthIn(max = 600.dp)
                .wrapContentHeight()
                .formModalKeys(
                    onCancel = handleDismissRequest,
                    onConfirm = handleSave,
                    confirmEnabled = canSave
                )
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isNewProduct) strings.addVeggieProduct else strings.editVeggieProduct,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryNavy
                    )

                    if (!isNewProduct && hasDialogChanges) {
                        IconButton(
                            onClick = {
                                name = product.name
                                description = product.description ?: ""
                                estimatedPriceInput = initialPriceInput
                                selectedUnitId = product.unitTypeId
                                selectedDeliveryUnitId = product.deliveryUnitTypeId
                                selectedImageBytes = null
                                removeExistingImage = false
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Undo,
                                contentDescription = strings.revertChanges,
                                tint = AccentNavy,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(color = DividerBorder)

                // Image Upload & Preview Row
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SurfaceContainerHighLight,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        VeggieProductImage(
                            productId = product.id,
                            imageFileName = if (removeExistingImage) null else product.imageFileName,
                            previewBytes = selectedImageBytes,
                            shape = RoundedCornerShape(12.dp),
                            placeholderIconSize = 32.dp,
                            modifier = Modifier.size(84.dp)
                        )

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = strings.veggieProductPhotoLabel,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = PrimaryNavy
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        pickImageBytes(strings.uploadPhotoBtn) { _, bytes ->
                                            selectedImageBytes = bytes
                                            removeExistingImage = false
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PhotoCamera,
                                        contentDescription = null,
                                        tint = PrimaryNavy,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(strings.uploadPhotoBtn, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PrimaryNavy)
                                }

                                val hasCurrentOrNewImage = selectedImageBytes != null || (!removeExistingImage && !product.imageFileName.isNullOrBlank())
                                if (hasCurrentOrNewImage) {
                                    OutlinedButton(
                                        onClick = {
                                            selectedImageBytes = null
                                            removeExistingImage = !product.imageFileName.isNullOrBlank()
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorDangerCrimson)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = null,
                                            tint = ColorDangerCrimson,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(strings.removePhotoBtn, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    AdminLabeledField(
                        label = strings.veggieProductNameLabel,
                        value = name,
                        onValueChange = { name = it },
                        placeholder = strings.veggieProductNamePlaceholder,
                        modifier = Modifier.weight(1.5f)
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        AdminLabeledField(
                            label = strings.veggieEstimatedPriceLabel,
                            value = estimatedPriceInput,
                            onValueChange = { estimatedPriceInput = it },
                            placeholder = strings.veggieEstimatedPricePlaceholder,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (!isPriceValid) {
                            Text(
                                text = strings.invalidPriceError,
                                fontSize = 11.sp,
                                color = ColorDangerCrimson,
                                modifier = Modifier.padding(top = 2.dp, start = 2.dp)
                            )
                        }
                    }
                }

                // Unit Type Selector
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = strings.veggieUnitTypeLabel,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PrimaryNavy
                    )

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        activeUnits.forEach { unit ->
                            val isSelected = selectedUnitId == unit.id
                            OutlinedButton(
                                onClick = { selectedUnitId = unit.id },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isSelected) AccentNavy else SurfaceWhite,
                                    contentColor = if (isSelected) SurfaceWhite else PrimaryNavy
                                ),
                                border = BorderStroke(1.dp, if (isSelected) AccentNavy else DividerBorder)
                            ) {
                                Text(
                                    text = unit.name,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Settlement Unit Selector (Admin Table)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = strings.settlementUnitLabel,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PrimaryNavy
                    )

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val isSameSelected = selectedDeliveryUnitId == null
                        OutlinedButton(
                            onClick = { selectedDeliveryUnitId = null },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isSameSelected) AccentNavy else SurfaceWhite,
                                contentColor = if (isSameSelected) SurfaceWhite else PrimaryNavy
                            ),
                            border = BorderStroke(1.dp, if (isSameSelected) AccentNavy else DividerBorder)
                        ) {
                            Text(
                                text = strings.sameAsCustomerUnitOption,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        activeUnits.forEach { unit ->
                            val isSelected = selectedDeliveryUnitId == unit.id
                            OutlinedButton(
                                onClick = { selectedDeliveryUnitId = unit.id },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isSelected) AccentNavy else SurfaceWhite,
                                    contentColor = if (isSelected) SurfaceWhite else PrimaryNavy
                                ),
                                border = BorderStroke(1.dp, if (isSelected) AccentNavy else DividerBorder)
                            ) {
                                Text(
                                    text = unit.name,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Optional Description Field
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = strings.veggieProductDescriptionLabel,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PrimaryNavy
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        placeholder = {
                            Text(
                                text = strings.veggieProductDescriptionPlaceholder,
                                fontSize = 13.sp,
                                color = TextSecondaryMuted
                            )
                        },
                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                        minLines = 2,
                        maxLines = 4,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                HorizontalDivider(color = DividerBorder)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = handleDismissRequest,
                        enabled = !isSaving,
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(strings.cancel, fontSize = 14.sp)
                    }

                    Button(
                        onClick = handleSave,
                        enabled = canSave,
                        modifier = Modifier.weight(1f).height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentNavy),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = SurfaceWhite,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = if (isNewProduct) strings.addVeggieProduct else strings.save,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        if (showDiscardConfirm) {
            ConfirmationDialog(
                title = strings.discardChangesTitle,
                message = strings.discardChangesMsg,
                confirmText = strings.discard,
                cancelText = strings.cancel,
                confirmButtonColor = ColorDangerCrimson,
                onConfirm = {
                    showDiscardConfirm = false
                    onCancel()
                },
                onDismiss = {
                    showDiscardConfirm = false
                }
            )
        }
    }
}
