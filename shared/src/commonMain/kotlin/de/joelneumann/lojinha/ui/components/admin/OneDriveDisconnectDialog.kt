package de.joelneumann.lojinha.ui.components.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.theme.ColorDangerCrimson
import de.joelneumann.lojinha.ui.theme.PrimaryNavy
import de.joelneumann.lojinha.ui.theme.SurfaceWhite
import de.joelneumann.lojinha.ui.theme.TextSecondaryMuted

import androidx.compose.ui.window.DialogProperties
import de.joelneumann.lojinha.ui.utils.confirmationDialogKeys

@Composable
fun OneDriveDisconnectDialog(
    accountEmail: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val strings = I18n.current
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true),
        containerColor = SurfaceWhite,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.confirmationDialogKeys(onCancel = onDismiss, onConfirm = onConfirm),
        title = {
            Text(
                text = strings.disconnectOneDriveTitle,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryNavy
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = strings.disconnectOneDriveConfirmMsg(accountEmail ?: "Microsoft Account"),
                    fontSize = 13.sp,
                    color = PrimaryNavy
                )
                Text(
                    text = strings.disconnectOneDriveWarningMsg,
                    fontSize = 12.sp,
                    color = TextSecondaryMuted
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ColorDangerCrimson)
            ) {
                Text(strings.disconnectBtn, color = SurfaceWhite, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(strings.cancel)
            }
        }
    )
}
