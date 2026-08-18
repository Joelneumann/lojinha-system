package de.joelneumann.lojinha.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/**
 * Development UI Scale Factor.
 * Adjust this constant to scale the entire application UI proportionally (layout dp & font sp).
 * 1.0f = 100% (Standard), 1.15f = 115% (Easier to see / Kiosk mode), 1.25f = 125% (Large).
 */
const val DEV_UI_SCALE_FACTOR: Float = 1.15f

val SurfaceWhite = Color(0xFFFFFFFF)
val SurfaceContainerLight = Color(0xFFF8FAFC)
val SurfaceContainerHighLight = Color(0xFFF1F5F9)
val PrimaryNavy = Color(0xFF0F172A)
val AccentNavy = Color(0xFF1E3A8A)
val AccentBlue = Color(0xFF2563EB)
val TextSecondaryMuted = Color(0xFF64748B)
val TextSecondarySubtle = Color(0xFF475569)
val DividerBorder = Color(0xFFE2E8F0)
val ColorSuccessEmerald = Color(0xFF059669)
val ColorDangerCrimson = Color(0xFFDC2626)
val ColorWarningAmber = Color(0xFFD97706)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryNavy,
    onPrimary = SurfaceWhite,
    secondary = AccentNavy,
    onSecondary = SurfaceWhite,
    tertiary = AccentBlue,
    background = SurfaceContainerLight,
    surface = SurfaceWhite,
    surfaceVariant = SurfaceContainerHighLight,
    onSurface = PrimaryNavy,
    onSurfaceVariant = TextSecondaryMuted,
    outline = DividerBorder,
    error = ColorDangerCrimson
)

@Composable
fun LojinhaTheme(content: @Composable () -> Unit) {
    val currentDensity = LocalDensity.current
    val scaledDensity = remember(currentDensity) {
        Density(
            density = currentDensity.density * DEV_UI_SCALE_FACTOR,
            fontScale = currentDensity.fontScale * DEV_UI_SCALE_FACTOR
        )
    }

    CompositionLocalProvider(
        LocalDensity provides scaledDensity
    ) {
        MaterialTheme(
            colorScheme = LightColorScheme,
            content = content
        )
    }
}
