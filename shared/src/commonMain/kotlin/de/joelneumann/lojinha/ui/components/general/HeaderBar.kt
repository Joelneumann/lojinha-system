package de.joelneumann.lojinha.ui.components.general

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.joelneumann.lojinha.domain.model.Language
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.theme.*
import de.joelneumann.lojinha.ui.viewmodel.AppScreen

@Composable
fun HeaderBar(
    currentScreen: AppScreen,
    currentLanguage: Language,
    onLanguageSelected: (Language) -> Unit,
    onLogoutClicked: () -> Unit,
    onAdminLoginClicked: () -> Unit
) {
    val strings = I18n.get(currentLanguage)

    val screenTitle = when (currentScreen) {
        AppScreen.MAIN_USER_SELECT -> "Lojinha"
        AppScreen.SHOPPING -> strings.shopping
        AppScreen.TRANSACTION_HISTORY -> strings.history
        AppScreen.ADMIN_PANEL -> strings.adminPanel
    }

    Surface(
        modifier = Modifier.fillMaxWidth().height(64.dp),
        color = SurfaceWhite,
        shadowElevation = 2.dp
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)
        ) {
            // Left: Language Switcher Flags
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Language.entries.forEach { lang ->
                    val isSelected = lang == currentLanguage
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
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "${lang.flagEmoji} ${lang.code.uppercase()}",
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) SurfaceWhite else PrimaryNavy
                        )
                    }
                }
            }

            // Center: Screen Heading Title
            Text(
                text = screenTitle,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryNavy,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center)
            )

            // Right: Navigation / Action Buttons (Admin Login on Main, Logout/Exit on others)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                if (currentScreen == AppScreen.MAIN_USER_SELECT) {
                    Button(
                        onClick = onAdminLoginClicked,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryNavy),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = strings.adminLoginBtn,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = SurfaceWhite
                        )
                    }
                } else if (currentScreen == AppScreen.ADMIN_PANEL) {
                    Button(
                        onClick = onLogoutClicked,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryNavy),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "🚪 Main Menu",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = SurfaceWhite
                        )
                    }
                } else {
                    Button(
                        onClick = onLogoutClicked,
                        colors = ButtonDefaults.buttonColors(containerColor = ColorDangerCrimson),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "🚪 ${strings.logout}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = SurfaceWhite
                        )
                    }
                }
            }
        }
    }
}
