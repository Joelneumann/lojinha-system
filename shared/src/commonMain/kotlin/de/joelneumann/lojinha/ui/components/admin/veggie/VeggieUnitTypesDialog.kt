package de.joelneumann.lojinha.ui.components.admin.veggie

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.joelneumann.lojinha.domain.model.VeggieProduct
import de.joelneumann.lojinha.domain.model.VeggieUnitType
import de.joelneumann.lojinha.ui.components.admin.AdminBadgeType
import de.joelneumann.lojinha.ui.components.admin.AdminLabeledField
import de.joelneumann.lojinha.ui.components.admin.AdminStatusBadge
import de.joelneumann.lojinha.ui.components.general.ConfirmationDialog
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.theme.*
import de.joelneumann.lojinha.ui.utils.currentTimeMillis
import de.joelneumann.lojinha.ui.utils.formModalKeys
import de.joelneumann.lojinha.ui.utils.generateUuid

@Composable
fun VeggieUnitTypesDialog(
    unitTypes: List<VeggieUnitType>,
    products: List<VeggieProduct>,
    onSaveUnitType: (VeggieUnitType) -> Unit,
    onDeleteUnitType: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val strings = I18n.current
    val activeUnits = remember(unitTypes) { unitTypes.filter { it.isActive }.sortedBy { it.name.lowercase() } }
    val usedUnitIds = remember(products) { products.filter { !it.isDeleted }.map { it.unitTypeId }.toSet() }

    var editingUnit by remember { mutableStateOf<VeggieUnitType?>(null) }
    var unitNameInput by remember { mutableStateOf("") }
    var allowDecimalsInput by remember { mutableStateOf(false) }
    var unitToDelete by remember { mutableStateOf<VeggieUnitType?>(null) }

    val canAddOrSave = unitNameInput.trim().isNotBlank() &&
            activeUnits.none { it.id != editingUnit?.id && it.name.equals(unitNameInput.trim(), ignoreCase = true) }

    val handleSaveUnit = {
        if (canAddOrSave) {
            val now = currentTimeMillis()
            val target = editingUnit?.copy(
                name = unitNameInput.trim(),
                allowDecimals = allowDecimalsInput,
                updatedAt = now
            ) ?: VeggieUnitType(
                id = generateUuid(),
                name = unitNameInput.trim(),
                allowDecimals = allowDecimalsInput,
                isActive = true,
                updatedAt = now
            )
            onSaveUnitType(target)
            editingUnit = null
            unitNameInput = ""
            allowDecimalsInput = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = true, dismissOnBackPress = true)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = SurfaceWhite,
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .widthIn(max = 540.dp)
                .wrapContentHeight()
                .formModalKeys(
                    onCancel = onDismiss,
                    onConfirm = handleSaveUnit,
                    confirmEnabled = canAddOrSave
                )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.veggieUnitTypesDialogTitle,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryNavy
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = strings.cancel, tint = PrimaryNavy)
                    }
                }

                HorizontalDivider(color = DividerBorder)

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SurfaceContainerHighLight,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AdminLabeledField(
                            label = strings.unitTypeNameLabel,
                            value = unitNameInput,
                            onValueChange = { unitNameInput = it },
                            placeholder = strings.unitTypeNamePlaceholder,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { allowDecimalsInput = !allowDecimalsInput }
                                .padding(vertical = 2.dp)
                        ) {
                            Checkbox(
                                checked = allowDecimalsInput,
                                onCheckedChange = { allowDecimalsInput = it }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = strings.allowDecimalsLabel,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = PrimaryNavy
                                )
                                Text(
                                    text = if (allowDecimalsInput) strings.unitAllowDecimals else strings.unitIntegerOnly,
                                    fontSize = 11.5.sp,
                                    color = TextSecondaryMuted
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (editingUnit != null) {
                                TextButton(
                                    onClick = {
                                        editingUnit = null
                                        unitNameInput = ""
                                        allowDecimalsInput = false
                                    }
                                ) {
                                    Text(strings.cancel, color = PrimaryNavy)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Button(
                                onClick = handleSaveUnit,
                                enabled = canAddOrSave,
                                colors = ButtonDefaults.buttonColors(containerColor = AccentNavy),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (editingUnit != null) Icons.Default.Save else Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (editingUnit != null) strings.save else strings.addUnitTypeBtn,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(activeUnits, key = { it.id }) { unit ->
                        val isUsed = unit.id in usedUnitIds
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = SurfaceContainerLight,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = unit.name,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryNavy
                                    )
                                    AdminStatusBadge(
                                        text = if (unit.allowDecimals) strings.unitAllowDecimals else strings.unitIntegerOnly,
                                        type = if (unit.allowDecimals) AdminBadgeType.SUCCESS else AdminBadgeType.NAVY
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IconButton(
                                        onClick = {
                                            editingUnit = unit
                                            unitNameInput = unit.name
                                            allowDecimalsInput = unit.allowDecimals
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = strings.edit,
                                            tint = AccentNavy,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            if (!isUsed) {
                                                unitToDelete = unit
                                            }
                                        },
                                        enabled = !isUsed,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = strings.delete,
                                            tint = if (isUsed) TextSecondaryMuted else ColorDangerCrimson,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = DividerBorder)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryNavy),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(strings.confirm, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (unitToDelete != null) {
            val target = unitToDelete!!
            ConfirmationDialog(
                title = strings.confirmDeleteUnitTypeTitle,
                message = strings.confirmDeleteUnitTypeMsg(target.name),
                confirmText = strings.delete,
                cancelText = strings.cancel,
                confirmButtonColor = ColorDangerCrimson,
                onConfirm = {
                    onDeleteUnitType(target.id)
                    unitToDelete = null
                },
                onDismiss = { unitToDelete = null }
            )
        }
    }
}
