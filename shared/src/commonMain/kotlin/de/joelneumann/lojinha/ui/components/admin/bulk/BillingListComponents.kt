package de.joelneumann.lojinha.ui.components.admin.bulk

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.theme.AccentNavy
import de.joelneumann.lojinha.ui.theme.ColorDangerCrimson
import de.joelneumann.lojinha.ui.theme.SurfaceWhite
import de.joelneumann.lojinha.ui.utils.confirmationDialogKeys

@Composable
fun DeleteBillingListDialog(
    listName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val strings = I18n.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite,
        shape = RoundedCornerShape(16.dp),
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
    isExecuting: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val strings = I18n.current

    AlertDialog(
        onDismissRequest = { if (!isExecuting) onDismiss() },
        containerColor = SurfaceWhite,
        shape = RoundedCornerShape(16.dp),
        properties = DialogProperties(dismissOnBackPress = !isExecuting, dismissOnClickOutside = !isExecuting),
        modifier = Modifier.confirmationDialogKeys(onCancel = { if (!isExecuting) onDismiss() }, onConfirm = { if (!isExecuting) onConfirm() }),
        title = { Text(strings.executeChargesBtn, fontWeight = FontWeight.Bold) },
        text = { Text(strings.confirmExecuteChargesMsg(count, totalFormatted)) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isExecuting,
                colors = ButtonDefaults.buttonColors(containerColor = AccentNavy)
            ) {
                if (isExecuting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = SurfaceWhite,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(strings.confirm, color = SurfaceWhite, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                enabled = !isExecuting
            ) {
                Text(strings.cancel)
            }
        }
    )
}
