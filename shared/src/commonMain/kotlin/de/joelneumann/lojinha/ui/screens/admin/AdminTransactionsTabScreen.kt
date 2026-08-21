package de.joelneumann.lojinha.ui.screens.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.joelneumann.lojinha.domain.model.Language
import de.joelneumann.lojinha.domain.model.TransactionType
import de.joelneumann.lojinha.ui.components.admin.AdminTopBar
import de.joelneumann.lojinha.ui.components.admin.transactions.AdminTransactionAccordionCard
import de.joelneumann.lojinha.ui.theme.TextSecondaryMuted
import de.joelneumann.lojinha.ui.utils.Formatting
import de.joelneumann.lojinha.ui.viewmodel.admin.AdminTransactionsViewModel

@Composable
fun AdminTransactionsTabScreen(
    viewModel: AdminTransactionsViewModel,
    expandedTransactionId: String?,
    onRequestToggleExpand: (String?) -> Unit,
    onRequestExpandTransaction: (String) -> Unit
) {
    val transactions by viewModel.transactions.collectAsState()
    val searchQuery by viewModel.searchFilter.collectAsState()

    val filteredTransactions = remember(transactions, searchQuery) {
        if (searchQuery.isBlank()) transactions
        else transactions.filter { tx ->
            val dateStr = Formatting.formatTimestamp(tx.timestamp)
            val dateStrEn = Formatting.formatTimestamp(tx.timestamp, Language.EN)
            val dateStrDe = Formatting.formatTimestamp(tx.timestamp, Language.DE)
            val dateStrBr = Formatting.formatTimestamp(tx.timestamp, Language.BR)
            tx.userNameSnapshot.contains(searchQuery, ignoreCase = true) ||
                    tx.type.name.contains(searchQuery, ignoreCase = true) ||
                    dateStr.contains(searchQuery, ignoreCase = true) ||
                    dateStrEn.contains(searchQuery, ignoreCase = true) ||
                    dateStrDe.contains(searchQuery, ignoreCase = true) ||
                    dateStrBr.contains(searchQuery, ignoreCase = true) ||
                    tx.items.any { item -> item.productName.contains(searchQuery, ignoreCase = true) }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        val openFirstResult = {
            if (filteredTransactions.isNotEmpty()) {
                onRequestExpandTransaction(filteredTransactions.first().id)
            }
        }

        AdminTopBar(
            searchQuery = searchQuery,
            onQueryChange = viewModel::updateSearchFilter,
            placeholder = "🔍 Search transaction by user, type, product, or date...",
            countText = if (searchQuery.isBlank()) "${transactions.size} Transactions" else "${filteredTransactions.size} / ${transactions.size} Transactions",
            onSearchSubmitted = openFirstResult
        )

        Spacer(modifier = Modifier.height(14.dp))

        if (filteredTransactions.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No transactions found.", color = TextSecondaryMuted)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                items(filteredTransactions, key = { it.id }) { tx ->
                    val isExpanded = expandedTransactionId == tx.id

                    val cancellationChild = transactions.firstOrNull { it.referenceTransactionId == tx.id && it.type == TransactionType.CANCELLATION }
                    val correctionChild = transactions.firstOrNull { it.referenceTransactionId == tx.id && it.type == TransactionType.CORRECTION }

                    val isCanceled = tx.type == TransactionType.CANCELLATION || cancellationChild != null || (tx.items.isNotEmpty() && tx.items.all { it.quantity == 0L })
                    val isCorrected = !isCanceled && (tx.type == TransactionType.CORRECTION || correctionChild != null)

                    val activeItems = correctionChild?.items?.ifEmpty { tx.items } ?: tx.items
                    val refTx = transactions.firstOrNull { it.id == tx.referenceTransactionId }

                    AdminTransactionAccordionCard(
                        transaction = tx,
                        activeItems = activeItems,
                        referencedTransaction = refTx,
                        isExpanded = isExpanded,
                        isCanceled = isCanceled,
                        isCorrected = isCorrected,
                        onExpandToggle = { onRequestToggleExpand(tx.id) },
                        onStornoPurchaseWithUpdatedItems = viewModel::stornoPurchaseWithUpdatedItems,
                        onStornoNonPurchase = viewModel::stornoNonPurchaseTransaction
                    )
                }
            }
        }
    }
}
