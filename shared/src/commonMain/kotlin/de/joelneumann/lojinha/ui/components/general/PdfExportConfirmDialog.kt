package de.joelneumann.lojinha.ui.components.general

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.joelneumann.lojinha.domain.model.Language
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.i18n.LanguageManager
import de.joelneumann.lojinha.ui.theme.AccentNavy
import de.joelneumann.lojinha.ui.theme.PrimaryNavy
import de.joelneumann.lojinha.ui.theme.SurfaceWhite

@Composable
fun PdfExportConfirmDialog(
    question: String,
    initialLanguage: Language = LanguageManager.currentLanguage,
    isExporting: Boolean = false,
    onConfirm: (Language) -> Unit,
    onDismiss: () -> Unit
) {
    val strings = I18n.current
    var selectedLanguage by remember { mutableStateOf(initialLanguage) }

    AlertDialog(
        onDismissRequest = { if (!isExporting) onDismiss() },
        shape = RoundedCornerShape(16.dp),
        containerColor = SurfaceWhite,
        icon = {
            Icon(
                imageVector = Icons.Default.PictureAsPdf,
                contentDescription = null,
                tint = PrimaryNavy,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                text = question,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = PrimaryNavy
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = strings.statementLanguage,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryNavy
                )
                LanguageFlagSelector(
                    selectedLanguage = selectedLanguage,
                    onLanguageSelected = { selectedLanguage = it }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedLanguage) },
                enabled = !isExporting,
                colors = ButtonDefaults.buttonColors(containerColor = AccentNavy),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row {
                    Icon(
                        imageVector = Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        tint = SurfaceWhite,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(strings.exportPdf, fontWeight = FontWeight.Bold, color = SurfaceWhite)
                }
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                enabled = !isExporting,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(strings.cancel, color = PrimaryNavy)
            }
        }
    )
}
