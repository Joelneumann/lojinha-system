package de.joelneumann.lojinha.ui.components.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.joelneumann.lojinha.ui.theme.ColorWarningAmber
import de.joelneumann.lojinha.ui.theme.DividerBorder
import de.joelneumann.lojinha.ui.theme.PrimaryNavy
import de.joelneumann.lojinha.ui.theme.SurfaceWhite
import de.joelneumann.lojinha.ui.theme.TextSecondaryMuted

@Composable
fun AdminAccordionCard(
    title: String,
    isExpanded: Boolean,
    onExpandToggle: () -> Unit,
    hasUnsaved: Boolean = false,
    headerBadges: @Composable RowScope.() -> Unit = {},
    headerRightContent: @Composable RowScope.() -> Unit = {},
    modifier: Modifier = Modifier,
    bodyContent: @Composable ColumnScope.() -> Unit
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val isMobile = maxWidth < 500.dp

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = SurfaceWhite,
            border = CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(
                    if (hasUnsaved && isExpanded) ColorWarningAmber else DividerBorder
                )
            ),
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onExpandToggle() }
                        .padding(if (isMobile) 12.dp else 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = title,
                                fontSize = if (isMobile) 15.sp else 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryNavy
                            )
                            if (isExpanded && hasUnsaved) {
                                AdminStatusBadge(
                                    text = "● Unsaved",
                                    type = AdminBadgeType.WARNING
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            headerBadges()
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        headerRightContent()
                        Text(
                            text = if (isExpanded) "▲" else "▼",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondaryMuted
                        )
                    }
                }

                if (isExpanded) {
                    HorizontalDivider(color = DividerBorder)

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(if (isMobile) 12.dp else 20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        bodyContent()
                    }
                }
            }
        }
    }
}
