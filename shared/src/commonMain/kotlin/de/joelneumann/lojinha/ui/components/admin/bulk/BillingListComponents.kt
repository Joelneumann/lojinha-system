package de.joelneumann.lojinha.ui.components.admin.bulk

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import de.joelneumann.lojinha.domain.model.BillingListType
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.theme.AccentNavy
import de.joelneumann.lojinha.ui.theme.ColorDangerCrimson
import de.joelneumann.lojinha.ui.theme.SurfaceWhite
import de.joelneumann.lojinha.ui.theme.TextSecondaryMuted
import de.joelneumann.lojinha.ui.utils.confirmationDialogKeys

@Composable
fun CreateBillingListDialog(
    onDismiss: () -> Unit,
    onSubmit: (name: String, type: BillingListType, basePrice: Long?) -> Unit
) {
    val strings = I18n.current
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(BillingListType.FIXED) }
    var priceInput by remember { mutableStateOf("") }

    val confirm = {
        val price = if (type == BillingListType.FIXED) {
            priceInput.replace(',', '.').toDoubleOrNull()?.let { kotlin.math.round(it * 100).toLong() }
        } else null
        onSubmit(name, type, price)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true),
        modifier = Modifier.confirmationDialogKeys(onCancel = onDismiss, onConfirm = confirm),
        title = { Text(strings.createNewList, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(strings.listNameLabel) },
                    placeholder = { Text(strings.listNamePlaceholder) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(strings.listTypeLabel, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = type == BillingListType.FIXED,
                        onClick = { type = BillingListType.FIXED },
                        label = { Text(strings.listTypeFixed) }
                    )
                    FilterChip(
                        selected = type == BillingListType.VARIABLE,
                        onClick = { type = BillingListType.VARIABLE },
                        label = { Text(strings.listTypeVariable) }
                    )
                }

                if (type == BillingListType.FIXED) {
                    OutlinedTextField(
                        value = priceInput,
                        onValueChange = { priceInput = it },
                        label = { Text(strings.basePriceBrlLabel) },
                        placeholder = { Text(strings.amountPlaceholder) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = confirm,
                enabled = name.isNotBlank() && (type != BillingListType.FIXED || priceInput.isNotBlank()),
                colors = ButtonDefaults.buttonColors(containerColor = AccentNavy)
            ) {
                Text(strings.save, color = SurfaceWhite, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        }
    )
}

@Composable
fun DeleteBillingListDialog(
    listName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val strings = I18n.current

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true),
        modifier = Modifier.confirmationDialogKeys(onCancel = onDismiss, onConfirm = onConfirm),
        title = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = ColorDangerCrimson)
                Text(strings.deleteListTitle, fontWeight = FontWeight.Bold, color = ColorDangerCrimson)
            }
        },
        text = { Text(strings.deleteListMsg) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = ColorDangerCrimson)
            ) {
                Text(strings.delete, color = SurfaceWhite, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        }
    )
}

@Composable
fun ExecuteChargesDialog(
    count: Int,
    totalFormatted: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val strings = I18n.current

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true),
        modifier = Modifier.confirmationDialogKeys(onCancel = onDismiss, onConfirm = onConfirm),
        title = { Text(strings.executeChargesBtn, fontWeight = FontWeight.Bold) },
        text = { Text(strings.confirmExecuteChargesMsg(count, totalFormatted)) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = AccentNavy)
            ) {
                Text(strings.confirm, color = SurfaceWhite, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        }
    )
}
