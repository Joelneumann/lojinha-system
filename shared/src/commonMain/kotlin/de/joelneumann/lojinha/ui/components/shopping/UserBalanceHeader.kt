package de.joelneumann.lojinha.ui.components.shopping

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.joelneumann.lojinha.domain.model.Language
import de.joelneumann.lojinha.domain.model.User
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.theme.*
import de.joelneumann.lojinha.ui.utils.Formatting

@Composable
fun UserBalanceHeader(
    user: User,
    rate: Double,
    language: Language,
    onNavigateToHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = I18n.get(language)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: User Name & Balance Card
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = SurfaceWhite,
            shadowElevation = 2.dp,
            modifier = Modifier.weight(1f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = user.name,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryNavy
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${strings.balance}: ",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondaryMuted
                    )
                    Text(
                        text = Formatting.formatBrl(user.balance),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (user.balance >= 0) ColorSuccessEmerald else ColorDangerCrimson
                    )
                    val secText = Formatting.formatSecondaryCurrency(user.balance, user.secondaryCurrency, rate)
                    if (secText.isNotEmpty()) {
                        Text(
                            text = secText,
                            fontSize = 14.sp,
                            color = TextSecondaryMuted
                        )
                    }
                }
            }
        }

        // Right: Prominent Account Button
        Button(
            onClick = onNavigateToHistory,
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryNavy),
            shape = RoundedCornerShape(12.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
            modifier = Modifier.height(52.dp)
        ) {
            Text(
                text = "👤 ${strings.account}",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = SurfaceWhite
            )
        }
    }
}
