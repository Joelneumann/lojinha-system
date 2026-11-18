package de.joelneumann.lojinha.ui.components.admin.shoppinglist

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import de.joelneumann.lojinha.domain.model.Product
import de.joelneumann.lojinha.domain.model.ShoppingListItem
import de.joelneumann.lojinha.domain.model.UnitType
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.theme.*
import de.joelneumann.lojinha.ui.utils.confirmationDialogKeys
import de.joelneumann.lojinha.ui.viewmodel.admin.AdminShoppingListViewModel

@Composable
fun ShoppingListExportApprovalDialog(
    items: List<ShoppingListItem>,
    productsById: Map<String, Product> = emptyMap(),
    onDismiss: () -> Unit,
    onConfirmExport: () -> Unit
) {
    val strings = I18n.current
    val unreviewedItems = items.filter { it.isPendingReview }
    val hasWarnings = unreviewedItems.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite,
        shape = RoundedCornerShape(16.dp),
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true),
        modifier = Modifier.confirmationDialogKeys(
            onCancel = onDismiss,
            onConfirm = onConfirmExport
        ),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Print,
                    contentDescription = null,
                    tint = AccentNavy,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = strings.shoppingListExportApprovalTitle,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryNavy
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = strings.shoppingListExportSummary(items.size),
                    fontSize = 14.sp,
                    color = TextSecondarySubtle
                )

                if (hasWarnings) {
                    Surface(
                        color = ColorWarningAmber.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, ColorWarningAmber.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = ColorWarningAmber,
                                    modifier = Modifier.size(18.dp).padding(top = 1.dp)
                                )
                                Text(
                                    text = strings.shoppingListExportUnreviewedWarning(unreviewedItems.size),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = PrimaryNavy,
                                    lineHeight = 18.sp
                                )
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 160.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                unreviewedItems.forEach { item ->
                                    val unitType = item.productId?.let { productsById[it]?.unitType }
                                    val numPart = AdminShoppingListViewModel.extractEditableNumericPart(item.quantity, unitType)
                                    val displayQty = when (unitType) {
                                        UnitType.PIECE -> "$numPart ${strings.unitPieceBadge}"
                                        UnitType.WEIGHT -> "$numPart kg"
                                        null -> item.quantity
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(start = 26.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "• ${item.name}",
                                            fontSize = 12.sp,
                                            color = PrimaryNavy,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = displayQty,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextSecondaryMuted
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmExport,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (hasWarnings) ColorWarningAmber else AccentNavy
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = if (hasWarnings) strings.shoppingListExportProceedAnywayBtn else strings.shoppingListExportPdfBtn,
                    color = SurfaceWhite,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = if (hasWarnings) strings.shoppingListExportReviewItemsBtn else strings.cancel
                )
            }
        }
    )
}
