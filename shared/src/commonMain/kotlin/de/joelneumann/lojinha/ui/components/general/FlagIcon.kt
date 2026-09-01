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
                    // USA / American Flag: 13 red and white stripes, blue canton on top-left, white stars
                    val red = Color(0xFFB22234)
                    val white = Color(0xFFFFFFFF)
                    val navy = Color(0xFF3C3B6E)

                    val stripeH = h / 13f
                    for (i in 0..12) {
                        drawRect(
                            color = if (i % 2 == 0) red else white,
                            topLeft = Offset(0f, i * stripeH),
                            size = Size(w, stripeH)
                        )
                    }

                    // Canton (Top-left blue rectangle covering 7 stripes height)
                    val cantonW = w * 0.42f
                    val cantonH = stripeH * 7f
                    drawRect(
                        color = navy,
                        topLeft = Offset(0f, 0f),
                        size = Size(cantonW, cantonH)
                    )

                    // White stars inside canton (Simplified grid of dots for crisp icon rendering)
                    val starRadius = h * 0.035f
                    val rows = 3
                    val cols = 3
                    for (r in 0 until rows) {
                        for (c in 0 until cols) {
                            val cx = cantonW * (0.25f + c * 0.25f)
                            val cy = cantonH * (0.25f + r * 0.25f)
                            drawCircle(
                                color = white,
                                radius = starRadius,
                                center = Offset(cx, cy)
                            )
                        }
                    }
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
