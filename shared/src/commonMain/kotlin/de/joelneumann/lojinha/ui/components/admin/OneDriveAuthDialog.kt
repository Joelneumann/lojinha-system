package de.joelneumann.lojinha.ui.components.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.joelneumann.lojinha.domain.model.DeviceCodeResponse
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.theme.AccentNavy
import de.joelneumann.lojinha.ui.theme.PrimaryNavy

@Composable
fun OneDriveAuthDialog(
    deviceCodeResponse: DeviceCodeResponse? = null,
    statusMessage: String,
    onDismiss: () -> Unit,
    onOpenBrowser: ((String) -> Unit)? = null
) {
    val strings = I18n.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = strings.connectOneDriveTitle,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryNavy
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(36.dp),
                    color = AccentNavy,
                    strokeWidth = 3.dp
                )

                Text(
                    text = statusMessage.ifBlank { strings.oneDriveAuthOpeningBrowser },
                    fontSize = 13.sp,
                    color = PrimaryNavy,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = strings.oneDriveAuthCompleteInstruction,
                    fontSize = 12.sp,
                    color = Color.DarkGray,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(8.dp)) {
                Text(strings.cancel)
            }
        }
    )
}
