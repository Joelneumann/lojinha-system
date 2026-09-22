package de.joelneumann.lojinha.ui.components.userselection

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.decodeToImageBitmap
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@JsFun("""
(emoji, size) => {
    try {
        const canvas = document.createElement('canvas');
        canvas.width = size;
        canvas.height = size;
        const ctx = canvas.getContext('2d');
        if (!ctx) return '';
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        ctx.font = Math.round(size * 0.72) + 'px -apple-system, BlinkMacSystemFont, "Segoe UI Emoji", "Apple Color Emoji", "Noto Color Emoji", sans-serif';
        ctx.fillText(emoji, size / 2, size / 2 + Math.round(size * 0.05));
        const dataUrl = canvas.toDataURL('image/png');
        const commaIndex = dataUrl.indexOf(',');
        return commaIndex >= 0 ? dataUrl.substring(commaIndex + 1) : '';
    } catch (e) {
        return '';
    }
}
""")
private external fun renderEmojiToPngBase64(emoji: String, size: Int): String

private val emojiCache = mutableMapOf<Pair<String, Int>, ImageBitmap?>()

@OptIn(ExperimentalEncodingApi::class)
@Composable
actual fun PlatformEmoji(
    emoji: String,
    modifier: Modifier,
    fontSize: TextUnit
) {
    val density = LocalDensity.current
    val targetPx = remember(fontSize, density) {
        with(density) {
            (fontSize.toDp().value * 2.5f).toInt().coerceIn(36, 128)
        }
    }

    val bitmap = remember(emoji, targetPx) {
        emojiCache.getOrPut(emoji to targetPx) {
            val base64 = renderEmojiToPngBase64(emoji, targetPx)
            if (base64.isNotBlank()) {
                try {
                    val bytes = Base64.Default.decode(base64)
                    bytes.decodeToImageBitmap()
                } catch (e: Exception) {
                    null
                }
            } else {
                null
            }
        }
    }

    if (bitmap != null) {
        val dpSize = with(density) { fontSize.toDp() * 1.25f }
        Image(
            bitmap = bitmap,
            contentDescription = emoji,
            modifier = modifier.size(dpSize)
        )
    } else {
        Text(
            text = emoji,
            fontSize = fontSize,
            modifier = modifier
        )
    }
}
