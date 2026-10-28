package de.joelneumann.lojinha.ui.components.admin.products

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.joelneumann.lojinha.domain.model.PagedResult
import de.joelneumann.lojinha.domain.model.Product
import de.joelneumann.lojinha.domain.model.ProductHistoryEntry
import de.joelneumann.lojinha.domain.model.ProductHistoryType
import de.joelneumann.lojinha.ui.components.admin.AdminBadgeType
import de.joelneumann.lojinha.ui.components.admin.AdminStatusBadge
import de.joelneumann.lojinha.ui.components.general.AppVerticalScrollbar
import de.joelneumann.lojinha.ui.components.general.PaginationBar
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.theme.*
import de.joelneumann.lojinha.ui.utils.Formatting
import androidx.compose.foundation.lazy.rememberLazyListState

@Composable
fun ProductHistoryDialog(
    product: Product,
    historyResult: PagedResult<ProductHistoryEntry>?,
    isLoading: Boolean,
    onPageChange: (Int) -> Unit,
    onPageSizeChange: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val strings = I18n.current
    val listState = rememberLazyListState()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = SurfaceWhite,
            tonalElevation = 6.dp,
            modifier = Modifier
                .widthIn(min = 600.dp, max = 860.dp)
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.85f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = AccentNavy.copy(alpha = 0.12f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    tint = AccentNavy,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Column {
                            Text(
                                text = strings.productHistoryForTitle(product.name),
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryNavy,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "${strings.stock}: ${Formatting.formatQuantity(product.stockQuantity, product.unitType)}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (product.stockQuantity > 0) ColorSuccessEmerald else ColorDangerCrimson
                                )
                                Text(
                                    text = "•",
                                    fontSize = 12.sp,
                                    color = TextSecondaryMuted
                                )
                                Text(
                                    text = "${strings.basePrice}: ${Formatting.formatBrl(product.basePrice)}",
                                    fontSize = 12.sp,
                                    color = TextSecondaryMuted
                                )
                            }
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = strings.cancel,
                            tint = TextSecondaryMuted
                        )
                    }
                }

                HorizontalDivider(color = DividerBorder)

                // 2. Table / List Content
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (isLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = AccentNavy)
                        }
                    } else if (historyResult == null || historyResult.items.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    tint = TextSecondaryMuted.copy(alpha = 0.5f),
                                    modifier = Modifier.size(48.dp)
                                )
                                Text(
                                    text = strings.historyNoData,
                                    fontSize = 14.sp,
                                    color = TextSecondaryMuted
                                )
                            }
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Table Header Row
                            Surface(
                                shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
                                color = SurfaceContainerHighLight,
                                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DividerBorder)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = strings.historyColDate,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextSecondaryMuted,
                                        modifier = Modifier.weight(2.2f)
                                    )
                                    Text(
                                        text = strings.historyColType,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextSecondaryMuted,
                                        modifier = Modifier.weight(2.0f)
                                    )
                                    Text(
                                        text = strings.historyColUserOrDetails,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextSecondaryMuted,
                                        modifier = Modifier.weight(2.5f)
                                    )
                                    Text(
                                        text = strings.historyColQuantity,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextSecondaryMuted,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.weight(1.5f)
                                    )
                                }
                            }

                            // Table Body Rows
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .border(
                                        width = 1.dp,
                                        color = DividerBorder,
                                        shape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)
                                    )
                            ) {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(historyResult.items, key = { it.id }) { entry ->
                                        HistoryRowItem(entry = entry, product = product)
                                        HorizontalDivider(color = DividerBorder.copy(alpha = 0.5f))
                                    }
                                }

                                AppVerticalScrollbar(
                                    scrollState = listState,
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .fillMaxHeight()
                                        .padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                // 3. Exact PaginationBar Component matching Transaction Page
                val currentP = historyResult?.page ?: 0
                val totalP = historyResult?.totalPages ?: 1
                val pageS = historyResult?.pageSize ?: 25
                val totalC = historyResult?.totalCount ?: 0

                PaginationBar(
                    currentPage = currentP,
                    totalPages = totalP,
                    pageSize = pageS,
                    totalCount = totalC,
                    onPageChange = onPageChange,
                    onPageSizeChange = onPageSizeChange,
                    pageSizeOptions = listOf(10, 25, 50),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun HistoryRowItem(
    entry: ProductHistoryEntry,
    product: Product
) {
    val strings = I18n.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (entry.isCancelled) ColorDangerCrimson.copy(alpha = 0.04f) else SurfaceWhite)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Col 1: Date & Time
        Text(
            text = Formatting.formatTimestamp(entry.timestamp),
            fontSize = 12.sp,
            color = if (entry.isCancelled) TextSecondaryMuted else PrimaryNavy,
            style = if (entry.isCancelled) LocalTextStyle.current.copy(textDecoration = TextDecoration.LineThrough) else LocalTextStyle.current,
            modifier = Modifier.weight(2.2f)
        )

        // Col 2: Event Type Badge
        Box(modifier = Modifier.weight(2.0f)) {
            when (entry.type) {
                ProductHistoryType.STOCK_ADJUSTMENT -> {
                    val isPositive = entry.quantity > 0
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isPositive) ColorSuccessEmerald.copy(alpha = 0.12f) else ColorWarningAmber.copy(alpha = 0.12f),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = androidx.compose.ui.graphics.SolidColor(if (isPositive) ColorSuccessEmerald.copy(alpha = 0.35f) else ColorWarningAmber.copy(alpha = 0.35f))
                        )
                    ) {
                        Text(
                            text = strings.historyTypeStockAdjustment,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isPositive) ColorSuccessEmerald else ColorWarningAmber,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }
                ProductHistoryType.PURCHASE -> {
                    if (entry.isCancelled) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = ColorDangerCrimson.copy(alpha = 0.1f),
                            border = CardDefaults.outlinedCardBorder().copy(
                                brush = androidx.compose.ui.graphics.SolidColor(ColorDangerCrimson.copy(alpha = 0.3f))
                            )
                        ) {
                            Text(
                                text = strings.historyTypeCancellation,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorDangerCrimson,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = AccentNavy.copy(alpha = 0.1f),
                            border = CardDefaults.outlinedCardBorder().copy(
                                brush = androidx.compose.ui.graphics.SolidColor(AccentNavy.copy(alpha = 0.3f))
                            )
                        ) {
                            Text(
                                text = strings.historyTypePurchase,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentNavy,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
                ProductHistoryType.CANCELLATION -> {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = ColorDangerCrimson.copy(alpha = 0.1f),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = androidx.compose.ui.graphics.SolidColor(ColorDangerCrimson.copy(alpha = 0.3f))
                        )
                    ) {
                        Text(
                            text = strings.historyTypeCancellation,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorDangerCrimson,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }

        // Col 3: User / Details
        Box(modifier = Modifier.weight(2.5f)) {
            val detailText = when (entry.type) {
                ProductHistoryType.PURCHASE -> entry.userName ?: "-"
                ProductHistoryType.STOCK_ADJUSTMENT -> {
                    when (entry.note) {
                        "SYSNOTE|INITIAL_STOCK" -> strings.stockAdjustmentInitial
                        null, "" -> strings.stockAdjustmentManual
                        else -> entry.note
                    }
                }
                ProductHistoryType.CANCELLATION -> entry.userName ?: strings.historyTypeCancellation
            }

            Text(
                text = detailText,
                fontSize = 12.sp,
                fontWeight = if (entry.type == ProductHistoryType.PURCHASE) FontWeight.SemiBold else FontWeight.Normal,
                color = if (entry.isCancelled) TextSecondaryMuted else PrimaryNavy,
                style = if (entry.isCancelled) LocalTextStyle.current.copy(textDecoration = TextDecoration.LineThrough) else LocalTextStyle.current,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Col 4: Quantity
        Box(modifier = Modifier.weight(1.5f), contentAlignment = Alignment.CenterEnd) {
            val formattedQty = Formatting.formatQuantity(kotlin.math.abs(entry.quantity), entry.unitType)
            val qtyText = when (entry.type) {
                ProductHistoryType.STOCK_ADJUSTMENT -> if (entry.quantity > 0) "+$formattedQty" else "-$formattedQty"
                ProductHistoryType.PURCHASE -> "-$formattedQty"
                ProductHistoryType.CANCELLATION -> "+$formattedQty"
            }

            val qtyColor = when {
                entry.isCancelled -> TextSecondaryMuted
                entry.type == ProductHistoryType.STOCK_ADJUSTMENT && entry.quantity > 0 -> ColorSuccessEmerald
                entry.type == ProductHistoryType.STOCK_ADJUSTMENT -> ColorDangerCrimson
                else -> PrimaryNavy
            }

            Text(
                text = qtyText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = qtyColor,
                style = if (entry.isCancelled) LocalTextStyle.current.copy(textDecoration = TextDecoration.LineThrough) else LocalTextStyle.current,
                textAlign = TextAlign.End
            )
        }
    }
}
