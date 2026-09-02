package de.joelneumann.lojinha.ui.components.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.theme.AccentNavy
import de.joelneumann.lojinha.ui.theme.ColorSuccessEmerald
import de.joelneumann.lojinha.ui.theme.PrimaryNavy

@Composable
fun OneDriveSuccessDialog(
    accountEmail: String,
    onDismiss: () -> Unit
) {
    val strings = I18n.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Cloud,
                    contentDescription = null,
                    tint = AccentNavy,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = strings.oneDriveConnectedTitle,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryNavy
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = ColorSuccessEmerald,
                    modifier = Modifier.size(44.dp)
                )
                Text(
                    text = strings.oneDriveConnectedSuccessMsg(accountEmail),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ColorSuccessEmerald,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = strings.oneDriveConnectedDesc,
                    fontSize = 12.sp,
                    color = Color.DarkGray,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentNavy)
            ) {
                Text(strings.greatBtn)
            }
        }
    )
}
