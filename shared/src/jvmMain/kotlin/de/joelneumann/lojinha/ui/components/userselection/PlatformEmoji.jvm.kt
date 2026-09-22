package de.joelneumann.lojinha.ui.components.userselection

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

@Composable
actual fun PlatformEmoji(
    emoji: String,
    modifier: Modifier,
    fontSize: TextUnit
) {
    Text(
        text = emoji,
        fontSize = fontSize,
        modifier = modifier
    )
}
