package de.joelneumann.lojinha.ui.components.history

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    modifier: Modifier = Modifier
) {
    val strings = I18n.current

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SearchInputField(
            query = searchFilter,
            onQueryChange = onSearchFilterChange,
            placeholder = strings.historyFilterPlaceholder,
            onSearchSubmitted = {},
            modifier = Modifier.weight(1f)
        )

        // Filter Type Chip Buttons
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val filterOptions: List<Pair<String, TransactionType?>> = listOf(
                strings.historyFilterAll to null,
                strings.historyTypePurchase to TransactionType.PURCHASE,
                strings.historyTypeDeposit to TransactionType.ADMIN_DEPOSIT,
                strings.historyTypeCancellation to TransactionType.CANCELLATION
            )

            filterOptions.forEach { (label, type) ->
                val isSelected = selectedTypeFilter == type
                FilterChip(
                    selected = isSelected,
                    onClick = { onTypeFilterSelect(type) },
                    label = {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentNavy,
                        selectedLabelColor = SurfaceWhite,
                        containerColor = SurfaceWhite
                    )
                )
            }
        }
    }
}
