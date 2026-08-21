package de.joelneumann.lojinha.ui.components.general

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import de.joelneumann.lojinha.domain.model.Language
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.theme.*

@Composable
fun InactivityWarningDialog(
    secondsRemaining: Int,
    language: Language,
    onStayLoggedIn: () -> Unit,
    onLogoutNow: () -> Unit
) {
    val strings = I18n.get(language)
    val mins = secondsRemaining / 60
    val secs = secondsRemaining % 60
    val formattedTime = "${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}"

    Dialog(onDismissRequest = onStayLoggedIn) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = SurfaceWhite,
            shadowElevation = 10.dp,
            modifier = Modifier.width(420.dp).wrapContentHeight()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = strings.inactivityWarningTitle,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorWarningAmber
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = strings.inactivityWarningMsg,
                    fontSize = 14.sp,
                    color = TextSecondarySubtle
                )

                Spacer(modifier = Modifier.height(20.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceContainerHighLight, RoundedCornerShape(12.dp))
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "⏰ $formattedTime",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorWarningAmber
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onLogoutNow,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorDangerCrimson)
                    ) {
                        Text(text = "🚪 ${strings.logout}", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onStayLoggedIn,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ColorSuccessEmerald)
                    ) {
                        Text(text = strings.stayLoggedIn, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
