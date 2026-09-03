package de.joelneumann.lojinha.ui.components.userselection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import de.joelneumann.lojinha.domain.model.AvatarType
import de.joelneumann.lojinha.domain.model.User
import de.joelneumann.lojinha.domain.model.UserAvatarConfig
import de.joelneumann.lojinha.ui.theme.AccentNavy
import de.joelneumann.lojinha.ui.theme.SurfaceWhite

val PRESET_AVATAR_COLORS = listOf(
    "#1E293B", // Navy
    "#2563EB", // Blue
    "#0D9488", // Teal
    "#059669", // Green
    "#D97706", // Amber
    "#DC2626", // Red
    "#7C3AED", // Purple
    "#DB2777", // Pink
    "#4B5563"  // Gray
)

val PRESET_AVATAR_EMOJIS = listOf(
    "🐒", "🦜", "🐸", "🦋", "🐆", "🐢", "🐊", "🐍", "🐜", "🦥",
    "🐬", "🐟", "🕷️", "🦇", "🌴", "🌿", "🌺", "🍄", "🪵", "🌞",
    "🌧️", "⛈️", "💧", "🛶", "⛺", "🚤", "✈️", "🧭", "🔦", "🪓",
    "🥾", "🎒", "🗺️", "📻", "🍌", "🍉", "🥭", "🍍", "🥥", "☕",
    "🍔", "🍽️", "🥩", "😀", "😊", "😎", "🤠", "👤", "👥", "👩‍🌾",
    "👨‍🔧", "⚽", "🎸", "📱", "🏐", "🎣", "📖", "💤", "🏠"
)

fun parseHexColor(colorHex: String, fallback: Color = AccentNavy): Color {
    val cleaned = colorHex.trim().removePrefix("#")
    if (cleaned.length != 6) return fallback
    return try {
        val r = cleaned.substring(0, 2).toInt(16)
        val g = cleaned.substring(2, 4).toInt(16)
        val b = cleaned.substring(4, 6).toInt(16)
        Color(r, g, b)
    } catch (e: Exception) {
        fallback
    }
}

@Composable
fun UserAvatar(
    user: User,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 22.sp,
    customAvatar: UserAvatarConfig? = null
) {
    val avatar = customAvatar ?: user.avatar
    val bgColor = parseHexColor(avatar.colorHex, AccentNavy)

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        if (avatar.type == AvatarType.EMOJI) {
            Text(
                text = avatar.emoji.ifBlank { "😀" },
                fontSize = fontSize
            )
        } else {
            Text(
                text = user.initials,
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                color = SurfaceWhite
            )
        }
    }
}
