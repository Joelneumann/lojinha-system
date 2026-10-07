package de.joelneumann.lojinha.ui.components.userselection

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.joelneumann.lojinha.ui.theme.AccentNavy
import de.joelneumann.lojinha.ui.theme.DividerBorder
import de.joelneumann.lojinha.ui.theme.PrimaryNavy
import de.joelneumann.lojinha.ui.theme.SurfaceWhite
import kotlinx.coroutines.launch

@Composable
fun EmojiPickerRow(
    selectedEmoji: String,
    onEmojiSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    itemSize: Dp = 34.dp,
    fontSize: TextUnit = 16.sp
) {
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    var hasScrolledInitially by remember { mutableStateOf(false) }
    LaunchedEffect(selectedEmoji) {
        if (!hasScrolledInitially) {
            val idx = PRESET_AVATAR_EMOJIS.indexOf(selectedEmoji)
            if (idx > 0) {
                val approxPx = idx * 42
                scrollState.scrollTo(approxPx)
            }
            hasScrolledInitially = true
        }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val canScrollBack = scrollState.value > 0
        IconButton(
            onClick = {
                coroutineScope.launch {
                    scrollState.animateScrollTo((scrollState.value - 160).coerceAtLeast(0))
                }
            },
            enabled = canScrollBack,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ChevronLeft,
                contentDescription = "Scroll Left",
                tint = if (canScrollBack) PrimaryNavy else DividerBorder,
                modifier = Modifier.size(20.dp)
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .pointerInput(scrollState) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Scroll) {
                                val change = event.changes.firstOrNull()
                                val delta = change?.scrollDelta ?: androidx.compose.ui.geometry.Offset.Zero
                                if (delta.y != 0f && delta.x == 0f) {
                                    scrollState.dispatchRawDelta(delta.y * 50f)
                                }
                            }
                        }
                    }
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PRESET_AVATAR_EMOJIS.forEach { emoji ->
                    val isSelected = selectedEmoji == emoji
                    Box(
                        modifier = Modifier
                            .size(itemSize)
                            .clip(CircleShape)
                            .background(if (isSelected) AccentNavy.copy(alpha = 0.2f) else SurfaceWhite)
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) AccentNavy else DividerBorder,
                                shape = CircleShape
                            )
                            .clickable { onEmojiSelect(emoji) },
                        contentAlignment = Alignment.Center
                    ) {
                        PlatformEmoji(emoji = emoji, fontSize = fontSize)
                    }
                }
            }
        }

        val canScrollForward = scrollState.value < scrollState.maxValue
        IconButton(
            onClick = {
                coroutineScope.launch {
                    scrollState.animateScrollTo((scrollState.value + 160).coerceAtMost(scrollState.maxValue))
                }
            },
            enabled = canScrollForward,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Scroll Right",
                tint = if (canScrollForward) PrimaryNavy else DividerBorder,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
