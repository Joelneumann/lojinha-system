package de.joelneumann.lojinha.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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
    MaterialTheme(
        colorScheme = LightColorScheme,
        content = content
    )
}
