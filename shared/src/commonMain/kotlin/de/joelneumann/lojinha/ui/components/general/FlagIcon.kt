package de.joelneumann.lojinha.ui.components.general

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.joelneumann.lojinha.domain.model.Language

/**
 * Renders a crisp vector flag icon for the given language.
 */
@Composable
fun LanguageFlagIcon(
    language: Language,
    modifier: Modifier = Modifier,
    width: Dp = 20.dp,
    height: Dp = 14.dp
) {
    Box(
        modifier = modifier
            .size(width = width, height = height)
            .clip(RoundedCornerShape(2.5.dp))
            .border(width = 0.5.dp, color = Color(0x33000000), shape = RoundedCornerShape(2.5.dp))
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height

            when (language) {
                Language.BR -> {
                    // Brazil Flag: Green background, Yellow rhombus, Blue circle
                    val green = Color(0xFF009B3A)
                    val yellow = Color(0xFFFEDF00)
                    val blue = Color(0xFF002776)

                    drawRect(color = green, size = Size(w, h))

                    val rhombusPath = Path().apply {
                        moveTo(w * 0.5f, h * 0.12f)
                        lineTo(w * 0.88f, h * 0.5f)
                        lineTo(w * 0.5f, h * 0.88f)
                        lineTo(w * 0.12f, h * 0.5f)
                        close()
                    }
                    drawPath(path = rhombusPath, color = yellow)

                    val circleRadius = h * 0.26f
                    drawCircle(color = blue, radius = circleRadius, center = Offset(w * 0.5f, h * 0.5f))
                }

                Language.EN -> {
                    // UK / Great Britain Flag: Blue background, St George cross & saltire
                    val navy = Color(0xFF012169)
                    val white = Color(0xFFFFFFFF)
                    val red = Color(0xFFC8102E)

                    drawRect(color = navy, size = Size(w, h))

                    // White saltire (diagonals)
                    val strokeW = h * 0.28f
                    drawLine(color = white, start = Offset(0f, 0f), end = Offset(w, h), strokeWidth = strokeW)
                    drawLine(color = white, start = Offset(0f, h), end = Offset(w, 0f), strokeWidth = strokeW)

                    // Red saltire
                    val redStrokeW = h * 0.14f
                    drawLine(color = red, start = Offset(0f, 0f), end = Offset(w, h), strokeWidth = redStrokeW)
                    drawLine(color = red, start = Offset(0f, h), end = Offset(w, 0f), strokeWidth = redStrokeW)

                    // White cross
                    val crossWhiteW = h * 0.36f
                    drawRect(color = white, topLeft = Offset(w * 0.5f - crossWhiteW * 0.5f, 0f), size = Size(crossWhiteW, h))
                    drawRect(color = white, topLeft = Offset(0f, h * 0.5f - crossWhiteW * 0.5f), size = Size(w, crossWhiteW))

                    // Red cross
                    val crossRedW = h * 0.22f
                    drawRect(color = red, topLeft = Offset(w * 0.5f - crossRedW * 0.5f, 0f), size = Size(crossRedW, h))
                    drawRect(color = red, topLeft = Offset(0f, h * 0.5f - crossRedW * 0.5f), size = Size(w, crossRedW))
                }

                Language.DE -> {
                    // Germany Flag: Black, Red, Gold horizontal stripes
                    val black = Color(0xFF000000)
                    val red = Color(0xFFDD0000)
                    val gold = Color(0xFFFFCC00)
                    val stripeH = h / 3f

                    drawRect(color = black, topLeft = Offset(0f, 0f), size = Size(w, stripeH))
                    drawRect(color = red, topLeft = Offset(0f, stripeH), size = Size(w, stripeH))
                    drawRect(color = gold, topLeft = Offset(0f, stripeH * 2f), size = Size(w, stripeH))
                }
            }
        }
    }
}
