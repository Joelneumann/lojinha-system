package de.joelneumann.lojinha.ui.components.shopping

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.joelneumann.lojinha.domain.model.Transaction
import de.joelneumann.lojinha.domain.model.UnitType
import de.joelneumann.lojinha.ui.utils.currentTimeMillis
import de.joelneumann.lojinha.domain.model.User
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.i18n.LanguageManager
import de.joelneumann.lojinha.ui.theme.*
import de.joelneumann.lojinha.ui.utils.Formatting
import de.joelneumann.lojinha.ui.utils.safeRequestFocus
import kotlinx.coroutines.delay
import kotlin.math.abs

@Composable
fun PurchaseOverviewDialog(
    transaction: Transaction,
    user: User,
    rate: Double,
    onGoToTransactions: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = I18n.current
    var secondsRemaining by remember { mutableStateOf(15) }
    var lastInteractionTime by remember { mutableStateOf(currentTimeMillis()) }
    var isActionHandled by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    val safeLogout: () -> Unit = {
        if (!isActionHandled) {
            isActionHandled = true
            onLogout()
        }
    }

    val safeGoToTransactions: () -> Unit = {
        if (!isActionHandled) {
            isActionHandled = true
            onGoToTransactions()
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.safeRequestFocus()
    }

    // 15-second countdown timer (independent of inactivity timer, resets on interaction)
    LaunchedEffect(lastInteractionTime) {
        val totalDurationSecs = 15
        val startTime = lastInteractionTime
        secondsRemaining = totalDurationSecs
        while (secondsRemaining > 0 && !isActionHandled) {
            delay(250L)
            val now = currentTimeMillis()
            val elapsedSecs = maxOf(0, ((now - startTime) / 1000L).toInt())
            val remaining = minOf(totalDurationSecs, maxOf(0, totalDurationSecs - elapsedSecs))
            secondsRemaining = remaining
            if (remaining <= 0) {
                safeLogout()
                break
            }
        }
    }

    val totalCents = abs(transaction.totalAmount)
    val balanceBefore = transaction.userBalanceBefore ?: user.balance
    val balanceAfter = transaction.userBalanceAfter ?: (balanceBefore - totalCents)
    val formattedTimestamp = remember(transaction.timestamp, LanguageManager.currentLanguage) {
        Formatting.formatTimestamp(transaction.timestamp)
    }

    Dialog(
        onDismissRequest = safeLogout,
        properties = DialogProperties(
            dismissOnClickOutside = false,
            dismissOnBackPress = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = SurfaceWhite,
            shadowElevation = 12.dp,
            modifier = modifier
                .width(580.dp)
                .wrapContentHeight()
                .focusRequester(focusRequester)
                .focusable()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.type == PointerEventType.Press || event.type == PointerEventType.Scroll) {
                                lastInteractionTime = currentTimeMillis()
                            }
                        }
                    }
                }
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        lastInteractionTime = currentTimeMillis()
                        if (event.key == Key.Escape) {
                            safeLogout()
                            true
                        } else {
                            false
                        }
                    } else {
                        false
                    }
                }
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header with success checkmark
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = ColorSuccessEmerald.copy(alpha = 0.15f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = ColorSuccessEmerald,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Column {
                        Text(
                            text = strings.purchaseOverviewTitle,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryNavy
                        )
                        Text(
                            text = formattedTimestamp,
                            fontSize = 12.sp,
                            color = TextSecondaryMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Itemized Purchased Items
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SurfaceContainerLight,
                    border = BorderStroke(1.dp, DividerBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Table Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SurfaceContainerHighLight)
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = strings.products,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondaryMuted,
                                modifier = Modifier.weight(1.8f)
                            )
                            Text(
                                text = strings.headerQtyWeight,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondaryMuted,
                                modifier = Modifier.weight(1.2f),
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = strings.headerLineTotal,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondaryMuted,
                                modifier = Modifier.weight(1.2f),
                                textAlign = TextAlign.End
                            )
                        }

                        HorizontalDivider(color = DividerBorder)

                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            itemsIndexed(transaction.items, key = { index, item -> "${item.productId}_$index" }) { index, item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1.8f)) {
                                        Text(
                                            text = item.productName,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = PrimaryNavy,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val unitSuffix = if (item.unitType == UnitType.WEIGHT) "/kg" else ""
                                        Text(
                                            text = "${Formatting.formatBrl(item.unitPriceAtPurchase)}$unitSuffix",
                                            fontSize = 11.sp,
                                            color = TextSecondaryMuted
                                        )
                                    }

                                    Text(
                                        text = Formatting.formatQuantity(item.quantity, item.unitType),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = PrimaryNavy,
                                        modifier = Modifier.weight(1.2f),
                                        textAlign = TextAlign.Center
                                    )

                                    Text(
                                        text = Formatting.formatBrl(item.totalLinePrice),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryNavy,
                                        modifier = Modifier.weight(1.2f),
                                        textAlign = TextAlign.End
                                    )
                                }

                                if (index < transaction.items.size - 1) {
                                    HorizontalDivider(color = DividerBorder.copy(alpha = 0.5f))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Financial Summary Card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceContainerHighLight, RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${strings.total}:",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryNavy
                        )
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = Formatting.formatBrl(totalCents),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryNavy
                            )
                            val secTotal = Formatting.formatSecondaryCurrency(totalCents, user.secondaryCurrency, rate)
                            if (secTotal.isNotEmpty()) {
                                Text(
                                    text = secTotal,
                                    fontSize = 12.sp,
                                    color = TextSecondaryMuted
                                )
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = DividerBorder)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${strings.previousBalance}:",
                            fontSize = 13.sp,
                            color = TextSecondarySubtle
                        )
                        Text(
                            text = Formatting.formatBrl(balanceBefore),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PrimaryNavy
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${strings.newBalance}:",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextSecondarySubtle
                        )
                        Text(
                            text = Formatting.formatBrl(balanceAfter),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (balanceAfter >= 0) ColorSuccessEmerald else ColorDangerCrimson
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action Buttons Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Button 1: Go to Transactions
                    OutlinedButton(
                        onClick = safeGoToTransactions,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier
                            .weight(1.15f)
                            .height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.5.dp, PrimaryNavy)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                                contentDescription = null,
                                tint = PrimaryNavy,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = strings.goToTransactions,
                                color = PrimaryNavy,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    // Button 2: Logout with independent 15s countdown
                    Button(
                        onClick = safeLogout,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier
                            .weight(0.85f)
                            .height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ColorDangerCrimson)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = null,
                                tint = SurfaceWhite,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = strings.logoutWithTimer(secondsRemaining),
                                color = SurfaceWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }
    }
}
