package de.joelneumann.lojinha.ui.components.shopping

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.joelneumann.lojinha.domain.model.Product
import de.joelneumann.lojinha.domain.model.UnitType
import de.joelneumann.lojinha.domain.model.User
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.theme.*
import de.joelneumann.lojinha.ui.utils.Formatting

@Composable
fun ProductCard(
    product: Product,
    globalMarkup: Double,
    user: User,
    rate: Double,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = I18n.current
    val unitPrice = product.calculateEffectiveUnitPrice(globalMarkup)

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(DividerBorder)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Column: Multiline Product Name & Unit Badge underneath
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
            ) {
                Text(
                    text = product.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryNavy,
                    lineHeight = 19.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = SurfaceContainerHighLight,
                    modifier = Modifier.wrapContentSize()
                ) {
                    Text(
                        text = if (product.unitType == UnitType.PIECE) strings.unitPiece else strings.unitWeight,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondaryMuted,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            // Right Column: Price & Secondary Currency underneath
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = Formatting.formatBrl(unitPrice),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentNavy
                    )
                    if (product.unitType == UnitType.WEIGHT) {
                        Text(
                            text = " / kg",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextSecondaryMuted,
                            modifier = Modifier.padding(bottom = 1.dp, start = 2.dp)
                        )
                    }
                }

                val secText = Formatting.formatSecondaryCurrency(unitPrice, user.secondaryCurrency, rate)
                if (secText.isNotEmpty()) {
                    Text(
                        text = secText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondaryMuted
                    )
                }
            }
        }
    }
}
