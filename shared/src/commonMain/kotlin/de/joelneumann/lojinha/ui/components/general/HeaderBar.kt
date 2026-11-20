package de.joelneumann.lojinha.ui.components.general

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.joelneumann.lojinha.domain.model.Language
import de.joelneumann.lojinha.ui.i18n.LanguageManager
import de.joelneumann.lojinha.ui.theme.*

val LocalHeaderCompact = compositionLocalOf { false }

@Composable
fun HeaderBar(
    title: String,
    modifier: Modifier = Modifier,
    onLanguageClick: (() -> Unit)? = null,
    onLanguageSelected: ((Language) -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val currentLanguage = LanguageManager.currentLanguage

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = SurfaceWhite,
        shadowElevation = 2.dp
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            val isMobile = maxWidth < 600.dp

            if (isMobile) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Languages (just the flags)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Language.entries.forEach { lang ->
                            val isSelected = lang == currentLanguage
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) AccentNavy else SurfaceContainerHighLight)
                                    .border(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = if (isSelected) AccentBlue else DividerBorder,
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .clickable {
                                        LanguageManager.setLanguage(lang)
                                        onLanguageSelected?.invoke(lang)
                                        onLanguageClick?.invoke()
                                    }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                LanguageFlagIcon(language = lang, width = 18.dp, height = 13.dp)
                            }
                        }
                    }

                    // Middle: Title
                    Text(
                        text = title,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryNavy,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    )

                    // Right: Actions (Logout button with just the icon, etc.)
                    CompositionLocalProvider(LocalHeaderCompact provides true) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            content = actions
                        )
                    }
                }
            } else {
                CompositionLocalProvider(LocalHeaderCompact provides false) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LanguageFlagSelector(
                            selectedLanguage = currentLanguage,
                            onLanguageSelected = { lang ->
                                LanguageManager.setLanguage(lang)
                                onLanguageSelected?.invoke(lang)
                                onLanguageClick?.invoke()
                            }
                        )

                        Text(
                            text = title,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryNavy
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            content = actions
                        )
                    }
                }
            }
        }
    }
}
