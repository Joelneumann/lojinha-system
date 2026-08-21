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
    modifier: Modifier = Modifier
) {
    val strings = I18n.get(language)

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = SurfaceWhite,
        shadowElevation = 2.dp,
        modifier = modifier.fillMaxWidth()
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
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = secText,
                        fontSize = 14.sp,
                        color = TextSecondaryMuted
                    )
                }
            }
        }
    }
}
