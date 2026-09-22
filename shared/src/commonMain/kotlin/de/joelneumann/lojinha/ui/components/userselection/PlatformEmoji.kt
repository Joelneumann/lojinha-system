package de.joelneumann.lojinha.ui.components.userselection

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

@Composable
expect fun PlatformEmoji(
    emoji: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 22.sp
)
