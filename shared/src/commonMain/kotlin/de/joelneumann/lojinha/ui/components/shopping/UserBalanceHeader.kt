package de.joelneumann.lojinha.ui.components.shopping

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.joelneumann.lojinha.domain.model.User
import de.joelneumann.lojinha.ui.components.userselection.UserAvatar
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.theme.*
import de.joelneumann.lojinha.ui.utils.Formatting

@Composable
fun UserBalanceHeader(
    user: User,
    rate: Double,
    modifier: Modifier = Modifier
) {
    val strings = I18n.current

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = SurfaceWhite,
        shadowElevation = 2.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        val userRow = @Composable {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                UserAvatar(
                    user = user,
                    modifier = Modifier.size(36.dp),
                    fontSize = 15.sp
                )
                Text(
                    text = user.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryNavy,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        val balanceRow = @Composable {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${strings.balance}: ",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondaryMuted,
                    softWrap = false
                )
                Text(
                    text = Formatting.formatBrl(user.balance),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (user.balance >= 0) ColorSuccessEmerald else ColorDangerCrimson,
                    softWrap = false
                )
                val secText = Formatting.formatSecondaryCurrency(user.balance, user.secondaryCurrency, rate)
                if (secText.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = secText,
                        fontSize = 13.sp,
                        color = TextSecondaryMuted,
                        softWrap = false,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Layout(
            content = {
                userRow()
                balanceRow()
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
        ) { measurables, constraints ->
            val paddingGap = 12.dp.roundToPx()
            val rowGap = 6.dp.roundToPx()

            // Measure balance row first with unbounded width to see how much space it needs
            val balancePlaceable = measurables[1].measure(
                constraints.copy(minWidth = 0, minHeight = 0)
            )

            // See if side-by-side fits: avatar (36dp) + name (at least 80dp) + gap + balance
            val minUserWidth = 116.dp.roundToPx()
            val canFitSideBySide = constraints.maxWidth >= balancePlaceable.width + minUserWidth + paddingGap

            if (canFitSideBySide) {
                val remainingForUser = (constraints.maxWidth - balancePlaceable.width - paddingGap).coerceAtLeast(0)
                val userPlaceable = measurables[0].measure(
                    constraints.copy(minWidth = 0, maxWidth = remainingForUser, minHeight = 0)
                )

                val height = maxOf(userPlaceable.height, balancePlaceable.height)
                val width = constraints.maxWidth

                layout(width, height) {
                    val userY = (height - userPlaceable.height) / 2
                    val balanceY = (height - balancePlaceable.height) / 2

                    userPlaceable.placeRelative(0, userY)
                    balancePlaceable.placeRelative(width - balancePlaceable.width, balanceY)
                }
            } else {
                // Stack vertically
                val userPlaceable = measurables[0].measure(
                    constraints.copy(minWidth = 0, maxWidth = constraints.maxWidth, minHeight = 0)
                )
                val finalBalancePlaceable = if (balancePlaceable.width > constraints.maxWidth) {
                    measurables[1].measure(
                        constraints.copy(minWidth = 0, maxWidth = constraints.maxWidth, minHeight = 0)
                    )
                } else {
                    balancePlaceable
                }

                val height = userPlaceable.height + rowGap + finalBalancePlaceable.height
                val width = constraints.maxWidth

                layout(width, height) {
                    userPlaceable.placeRelative(0, 0)
                    finalBalancePlaceable.placeRelative(0, userPlaceable.height + rowGap)
                }
            }
        }
    }
}
