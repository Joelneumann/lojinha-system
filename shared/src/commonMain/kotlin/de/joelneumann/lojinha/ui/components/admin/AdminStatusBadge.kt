package de.joelneumann.lojinha.ui.components.admin

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.joelneumann.lojinha.ui.theme.AccentNavy
import de.joelneumann.lojinha.ui.theme.ColorDangerCrimson
import de.joelneumann.lojinha.ui.theme.ColorSuccessEmerald
import de.joelneumann.lojinha.ui.theme.ColorWarningAmber

enum class AdminBadgeType {
    WARNING,
    DANGER,
    SUCCESS,
    NAVY
}

@Composable
fun AdminStatusBadge(
    text: String,
    type: AdminBadgeType = AdminBadgeType.WARNING,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor) = when (type) {
        AdminBadgeType.WARNING -> ColorWarningAmber.copy(alpha = 0.15f) to ColorWarningAmber
        AdminBadgeType.DANGER -> ColorDangerCrimson.copy(alpha = 0.12f) to ColorDangerCrimson
        AdminBadgeType.SUCCESS -> ColorSuccessEmerald.copy(alpha = 0.12f) to ColorSuccessEmerald
        AdminBadgeType.NAVY -> AccentNavy.copy(alpha = 0.12f) to AccentNavy
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        modifier = modifier
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}
