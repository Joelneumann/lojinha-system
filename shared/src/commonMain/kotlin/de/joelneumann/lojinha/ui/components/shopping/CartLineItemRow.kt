package de.joelneumann.lojinha.ui.components.shopping

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import de.joelneumann.lojinha.domain.model.UnitType
import de.joelneumann.lojinha.domain.model.User
import de.joelneumann.lojinha.ui.theme.*
import de.joelneumann.lojinha.ui.utils.Formatting
import de.joelneumann.lojinha.ui.viewmodel.CartItem

@Composable
fun CartLineItemRow(
    cartItem: CartItem,
    user: User,
    rate: Double,
    onQtyChange: (Long) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLight),
        border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(DividerBorder))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = cartItem.product.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryNavy,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                val unitSuffix = if (cartItem.product.unitType == UnitType.WEIGHT) " / kg" else ""
                Text(
                    text = "${Formatting.formatQuantity(cartItem.quantity, cartItem.product.unitType)} × ${Formatting.formatBrl(cartItem.unitPriceWithMarkup)}$unitSuffix",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondaryMuted
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (cartItem.product.unitType == UnitType.PIECE) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = SurfaceWhite,
                        border = BorderStroke(1.dp, DividerBorder)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.height(30.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(30.dp)
                                    .pointerInput(cartItem.product.id, cartItem.quantity) {
                                        detectTapGestures { onQtyChange(cartItem.quantity - 1) }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("-", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = PrimaryNavy)
                            }

                            VerticalDivider(color = DividerBorder, modifier = Modifier.fillMaxHeight().width(1.dp))

                            Text(
                                text = "${cartItem.quantity}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryNavy,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.widthIn(min = 36.dp).padding(horizontal = 4.dp)
                            )

                            VerticalDivider(color = DividerBorder, modifier = Modifier.fillMaxHeight().width(1.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(30.dp)
                                    .pointerInput(cartItem.product.id, cartItem.quantity) {
                                        detectTapGestures { onQtyChange(cartItem.quantity + 1) }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("+", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = PrimaryNavy)
                            }
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = SurfaceWhite,
                        border = BorderStroke(1.dp, DividerBorder)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text(
                                text = Formatting.formatQuantity(cartItem.quantity, UnitType.WEIGHT),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryNavy,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 10.dp)
                            )

                            VerticalDivider(color = DividerBorder, modifier = Modifier.fillMaxHeight().width(1.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(30.dp)
                                    .pointerInput(cartItem.product.id) {
                                        detectTapGestures { onRemove() }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = null,
                                    tint = ColorDangerCrimson,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = Formatting.formatBrl(cartItem.lineTotal),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryNavy
                    )
                    val secText = Formatting.formatSecondaryCurrency(cartItem.lineTotal, user.secondaryCurrency, rate)
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
}
