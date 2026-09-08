package de.joelneumann.lojinha.ui.components.admin.products

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
import de.joelneumann.lojinha.domain.model.Product
import de.joelneumann.lojinha.domain.model.UnitType
import de.joelneumann.lojinha.ui.components.admin.*
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.theme.*
import de.joelneumann.lojinha.ui.utils.Formatting

@Composable
fun AdminProductAccordionCard(
    product: Product,
    isExpanded: Boolean,
    onExpandToggle: () -> Unit,
    onEditProduct: (Product) -> Unit,
    onAdjustStock: (String, Long) -> Unit,
    onToggleActive: (Product) -> Unit,
    onDeleteProduct: (Product) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = I18n.current

    var stockDeltaInput by remember(product.id) { mutableStateOf("") }
    var pendingStockAdjustment by remember { mutableStateOf<Long?>(null) }
    var showToggleActiveConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

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
            Text(
                text = "${strings.basePrice}: ${Formatting.formatBrl(product.basePrice)}",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = PrimaryNavy
            )
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
                    Text(strings.adjustStockBtn, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SurfaceWhite)
                }
            }
        }

        AdminCardActionsRow(
            onEdit = { onEditProduct(product) },
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

        AlertDialog(
            onDismissRequest = { pendingStockAdjustment = null },
            title = { Text(strings.confirmStockAdjustmentTitle, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    strings.confirmStockAdjustmentMsg(isAddition, formattedDelta, product.name)
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
                    Text(strings.confirm, color = SurfaceWhite, fontWeight = FontWeight.Bold)
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
        AlertDialog(
            onDismissRequest = { showToggleActiveConfirm = false },
            title = { Text(strings.confirmProductStatusChangeTitle, fontWeight = FontWeight.Bold) },
            text = { Text(strings.confirmProductStatusChangeMsg(product.isActive, product.name)) },
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
                    Text(strings.confirm, color = SurfaceWhite, fontWeight = FontWeight.Bold)
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
            title = { Text(strings.confirmDeleteProductTitle, fontWeight = FontWeight.Bold, color = ColorDangerCrimson) },
            text = { Text(strings.confirmDeleteProductMsg(product.name)) },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteProduct(product)
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorDangerCrimson)
                ) {
                    Text(strings.yesDeleteProduct, color = SurfaceWhite, fontWeight = FontWeight.Bold)
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
