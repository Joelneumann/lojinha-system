package de.joelneumann.lojinha.ui.components.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.joelneumann.lojinha.domain.model.TransactionType
import de.joelneumann.lojinha.ui.components.general.SearchInputField
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.theme.*

@Composable
fun TransactionFilterBar(
    searchFilter: String,
    selectedTypeFilter: TransactionType?,
    onSearchFilterChange: (String) -> Unit,
    onTypeFilterSelect: (TransactionType?) -> Unit,
    modifier: Modifier = Modifier,
    onEscape: () -> Unit = {},
    focusRequester: FocusRequester? = null
) {
    val strings = I18n.current

    Row(
        modifier = modifier.fillMaxWidth().height(56.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SearchInputField(
            query = searchFilter,
            onQueryChange = onSearchFilterChange,
            placeholder = strings.historyFilterPlaceholder,
            onSearchSubmitted = {},
            onEscape = onEscape,
            focusRequester = focusRequester,
            modifier = Modifier.weight(1f).fillMaxHeight()
        )

        // Filter Type Buttons
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val filterOptions: List<Pair<String, TransactionType?>> = listOf(
                strings.historyFilterAll to null,
                strings.historyTypePurchase to TransactionType.PURCHASE,
                strings.historyTypeExpenses to TransactionType.ADMIN_WITHDRAWAL,
                strings.historyTypeDeposit to TransactionType.ADMIN_DEPOSIT,
                strings.historyTypeCancellation to TransactionType.CANCELLATION,
                strings.historyTypeCorrection to TransactionType.CORRECTION
            )

            filterOptions.forEach { (label, type) ->
                val isSelected = selectedTypeFilter == type
                Surface(
                    onClick = { onTypeFilterSelect(type) },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) AccentNavy else SurfaceWhite,
                    contentColor = if (isSelected) SurfaceWhite else PrimaryNavy,
                    border = if (isSelected) null else BorderStroke(1.dp, DividerBorder),
                    shadowElevation = if (isSelected) 2.dp else 1.dp,
                    modifier = Modifier.fillMaxHeight().pointerHoverIcon(PointerIcon.Hand)
                ) {
                    Box(
                        modifier = Modifier.fillMaxHeight().padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                            color = if (isSelected) SurfaceWhite else PrimaryNavy,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
