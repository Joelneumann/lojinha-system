package de.joelneumann.lojinha.ui.components.shopping

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.joelneumann.lojinha.domain.model.Language
import de.joelneumann.lojinha.domain.model.User
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.theme.*
import de.joelneumann.lojinha.ui.utils.Formatting
import de.joelneumann.lojinha.ui.viewmodel.CartItem

@Composable
fun CartPanel(
    cartItems: List<CartItem>,
    user: User,
    rate: Double,
    language: Language,
    cartTotal: Long,
    balanceAfter: Long,
    onQtyChange: (productId: String, newQty: Long) -> Unit,
    onRemoveItem: (productId: String) -> Unit,
    onCompletePurchase: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = I18n.get(language)

    Surface(
        modifier = modifier.fillMaxHeight(),
        color = SurfaceWhite,
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            Text(
                text = strings.cart,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryNavy
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = DividerBorder)

            // Cart Line Items List
            Box(modifier = Modifier.weight(1f)) {
                if (cartItems.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Cart is empty.\nScan product or click to add.",
                            color = TextSecondaryMuted,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(cartItems, key = { it.product.id }) { cartItem ->
                            CartLineItemRow(
                                cartItem = cartItem,
                                user = user,
                                rate = rate,
                                language = language,
                                onQtyChange = { newQty -> onQtyChange(cartItem.product.id, newQty) },
                                onRemove = { onRemoveItem(cartItem.product.id) }
                            )
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = DividerBorder)

            // Cart Summary Bottom Block
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceContainerHighLight, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${strings.total}:",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryNavy
                    )

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = Formatting.formatBrl(cartTotal),
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryNavy
                        )
                        val secTotal = Formatting.formatSecondaryCurrency(cartTotal, user.secondaryCurrency, rate)
                        if (secTotal.isNotEmpty()) {
                            Text(
                                text = secTotal,
                                fontSize = 13.sp,
                                color = TextSecondaryMuted
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${strings.balanceAfter}:",
                        fontSize = 14.sp,
                        color = TextSecondarySubtle
                    )
                    Text(
                        text = Formatting.formatBrl(balanceAfter),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (balanceAfter >= 0) ColorSuccessEmerald else ColorDangerCrimson
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Complete Purchase Button
            Button(
                onClick = onCompletePurchase,
                enabled = cartItems.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ColorSuccessEmerald)
            ) {
                Text(
                    text = strings.completePurchase,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = SurfaceWhite
                )
            }
        }
    }
}
