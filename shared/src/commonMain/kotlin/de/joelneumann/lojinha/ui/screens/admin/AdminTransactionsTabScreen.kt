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
    val currentPage by viewModel.currentPage.collectAsState()
    val totalPages by viewModel.totalPages.collectAsState()
    val pageSize by viewModel.pageSize.collectAsState()
    val totalCount by viewModel.totalCount.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        val openFirstResult = {
            if (transactions.isNotEmpty()) {
                onRequestExpandTransaction(transactions.first().id)
            }
        }

        AdminTopBar(
            searchQuery = searchQuery,
            onQueryChange = viewModel::updateSearchFilter,
            placeholder = "🔍 Search transaction by user, type, product, or date...",
            countText = if (searchQuery.isBlank()) "$totalCount Transactions" else "${transactions.size} of $totalCount Transactions",
            onSearchSubmitted = openFirstResult
        )

        Spacer(modifier = Modifier.height(14.dp))

        if (transactions.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No transactions found.", color = TextSecondaryMuted)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                items(transactions, key = { it.id }) { tx ->
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

        Spacer(modifier = Modifier.height(10.dp))

        de.joelneumann.lojinha.ui.components.general.PaginationBar(
            currentPage = currentPage,
            totalPages = totalPages,
            pageSize = pageSize,
            totalCount = totalCount,
            onPageChange = viewModel::setPage,
            onPageSizeChange = viewModel::setPageSize
        )
    }
}
