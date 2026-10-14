package de.joelneumann.lojinha.ui.components.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.joelneumann.lojinha.domain.model.BalanceWarningLevel
import de.joelneumann.lojinha.domain.model.SystemSettings
import de.joelneumann.lojinha.domain.model.User
import de.joelneumann.lojinha.domain.model.balanceWarningLevel
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.theme.ColorDangerCrimson
import de.joelneumann.lojinha.ui.theme.ColorWarningAmber
import de.joelneumann.lojinha.ui.theme.PrimaryNavy

@Composable
fun UserBalanceWarningBanner(
    user: User,
    settings: SystemSettings = SystemSettings(),
    modifier: Modifier = Modifier
) {
    val level = user.balanceWarningLevel(
        yellowThresholdCents = settings.yellowBalanceWarningThresholdCents,
        redThresholdCents = settings.redBalanceWarningThresholdCents
    )
    if (level == BalanceWarningLevel.NORMAL) return

    val strings = I18n.current
    val isNegative = level == BalanceWarningLevel.NEGATIVE

    val backgroundColor = if (isNegative) Color(0xFFFEF2F2) else Color(0xFFFFFBEB)
    val borderColor = if (isNegative) ColorDangerCrimson.copy(alpha = 0.4f) else ColorWarningAmber.copy(alpha = 0.4f)
    val accentColor = if (isNegative) ColorDangerCrimson else ColorWarningAmber
    val icon = if (isNegative) Icons.Default.Warning else Icons.Default.Info
    val title = if (isNegative) strings.negativeBalanceWarningTitle else strings.lowBalanceWarningTitle
    val message = if (isNegative) strings.negativeBalanceWarningMsg else strings.lowBalanceWarningMsg

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 1.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(accentColor.copy(alpha = 0.12f), shape = RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = message,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    color = PrimaryNavy
                )
            }
        }
    }
}
