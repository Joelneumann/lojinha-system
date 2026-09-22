package de.joelneumann.lojinha.ui.components.admin.products

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOn
import de.joelneumann.lojinha.domain.model.Product
import de.joelneumann.lojinha.ui.components.admin.AdminBadgeType
import de.joelneumann.lojinha.ui.components.admin.AdminStatusBadge
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.theme.ColorSuccessEmerald
import de.joelneumann.lojinha.ui.theme.DividerBorder
import de.joelneumann.lojinha.ui.theme.PrimaryNavy
import androidx.compose.ui.window.DialogProperties
import de.joelneumann.lojinha.ui.utils.confirmationDialogKeys
import de.joelneumann.lojinha.ui.theme.SurfaceWhite
import de.joelneumann.lojinha.ui.theme.TextSecondaryMuted
import de.joelneumann.lojinha.ui.utils.Formatting

@Composable
fun DisabledProductCard(
    product: Product,
    onEnableProduct: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = I18n.current
    var showEnableConfirm by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = SurfaceWhite,
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(DividerBorder)
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = product.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryNavy
                    )
                    AdminStatusBadge(
                        text = strings.disabled,
                        type = AdminBadgeType.WARNING
                    )
                }
                Text(
                    text = "${strings.basePrice}: ${Formatting.formatBrl(product.basePrice)} • ${strings.stock}: ${Formatting.formatQuantity(product.stockQuantity, product.unitType)}",
                    fontSize = 13.sp,
                    color = TextSecondaryMuted
                )
            }

            Button(
                onClick = { showEnableConfirm = true },
                colors = ButtonDefaults.buttonColors(containerColor = ColorSuccessEmerald),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(38.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FlashOn,
                        contentDescription = null,
                        tint = SurfaceWhite,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(strings.enableProduct, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SurfaceWhite)
                }
            }
        }
    }

    if (showEnableConfirm) {
        val dismissDialog = { showEnableConfirm = false }
        val confirmEnable = {
            onEnableProduct()
            showEnableConfirm = false
        }

        AlertDialog(
            onDismissRequest = dismissDialog,
            containerColor = SurfaceWhite,
            shape = RoundedCornerShape(16.dp),
            properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true),
            modifier = Modifier.confirmationDialogKeys(onCancel = dismissDialog, onConfirm = confirmEnable),
            title = { Text(strings.confirmProductActivationTitle, fontWeight = FontWeight.Bold, color = ColorSuccessEmerald) },
            text = { Text(strings.confirmProductActivationMsg(product.name)) },
            confirmButton = {
                Button(
                    onClick = confirmEnable,
                    colors = ButtonDefaults.buttonColors(containerColor = ColorSuccessEmerald)
                ) {
                    Text(strings.yesEnableProduct, color = SurfaceWhite, fontWeight = FontWeight.Bold)
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
