package de.joelneumann.lojinha.ui.components.general

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.joelneumann.lojinha.domain.model.Language
import de.joelneumann.lojinha.ui.theme.AccentBlue
import de.joelneumann.lojinha.ui.theme.AccentNavy
import de.joelneumann.lojinha.ui.theme.DividerBorder
import de.joelneumann.lojinha.ui.theme.PrimaryNavy
import de.joelneumann.lojinha.ui.theme.SurfaceContainerHighLight
import de.joelneumann.lojinha.ui.theme.SurfaceWhite

@Composable
fun LanguageFlagSelector(
    selectedLanguage: Language,
    onLanguageSelected: (Language) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Language.entries.forEach { lang ->
            val isSelected = lang == selectedLanguage
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) AccentNavy else SurfaceContainerHighLight)
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) AccentBlue else DividerBorder,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable { onLanguageSelected(lang) }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LanguageFlagIcon(language = lang, width = 18.dp, height = 13.dp)
                    Text(
                        text = lang.code.uppercase(),
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) SurfaceWhite else PrimaryNavy
                    )
                }
            }
        }
    }
}
