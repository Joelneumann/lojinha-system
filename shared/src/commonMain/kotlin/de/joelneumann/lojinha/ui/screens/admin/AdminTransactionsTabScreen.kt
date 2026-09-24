package de.joelneumann.lojinha.ui.screens.admin

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.joelneumann.lojinha.domain.model.TransactionType
import de.joelneumann.lojinha.ui.components.admin.AdminTopBar
import de.joelneumann.lojinha.ui.components.admin.transactions.AdminTransactionAccordionCard
import de.joelneumann.lojinha.ui.components.admin.transactions.PurchaseCorrectionDialog
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.theme.ColorDangerCrimson
import de.joelneumann.lojinha.ui.theme.SurfaceWhite
import de.joelneumann.lojinha.ui.theme.TextSecondaryMuted
import de.joelneumann.lojinha.ui.viewmodel.admin.AdminTransactionsViewModel

@Composable
fun AdminTransactionsTabScreen(
    viewModel: AdminTransactionsViewModel,
    expandedTransactionId: String?,
    onRequestToggleExpand: (String?) -> Unit,
    onRequestExpandTransaction: (String) -> Unit
) {
    val strings = I18n.current
    val transactions by viewModel.transactions.collectAsState()
    val relatedChildrenMap by viewModel.relatedChildrenMap.collectAsState()
    val referencedParentsMap by viewModel.referencedParentsMap.collectAsState()
    val correctionTarget by viewModel.correctionTarget.collectAsState()
    val isSubmitting by viewModel.isSubmitting.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    val searchQuery by viewModel.searchFilter.collectAsState()
    val currentPage by viewModel.currentPage.collectAsState()
    val totalPages by viewModel.totalPages.collectAsState()
    val pageSize by viewModel.pageSize.collectAsState()
    val totalCount by viewModel.totalCount.collectAsState()

    val selectedTypeFilter by viewModel.selectedTypeFilter.collectAsState()

    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        listState.scrollToItem(0)
        viewModel.loadData()
    }

    LaunchedEffect(currentPage, pageSize, searchQuery, selectedTypeFilter) {
        listState.scrollToItem(0)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        val openFirstResult = {
            if (transactions.isNotEmpty()) {
                onRequestExpandTransaction(transactions.first().id)
            }
        }

        AdminTopBar(
            searchQuery = searchQuery,
            onQueryChange = viewModel::updateSearchFilter,
            placeholder = strings.searchTransactionAdminPlaceholder,
            countText = if (searchQuery.isBlank()) strings.transactionsCountText(totalCount) else strings.transactionsCountText(transactions.size, totalCount),
            onSearchSubmitted = openFirstResult
        )

        Spacer(modifier = Modifier.height(8.dp))

        val filterOptions = listOf(
            null to strings.historyFilterAll,
            TransactionType.PURCHASE to strings.historyTypePurchase,
            TransactionType.ADMIN_DEPOSIT to strings.historyTypeDeposit,
            TransactionType.ADMIN_WITHDRAWAL to strings.historyTypeDebit,
            TransactionType.CORRECTION to strings.historyTypeCorrection,
            TransactionType.CANCELLATION to strings.historyTypeCancellation
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            filterOptions.forEach { (type, label) ->
                val isSelected = selectedTypeFilter == type
                FilterChip(
                    selected = isSelected,
                    onClick = { viewModel.updateTypeFilter(type) },
                    label = {
                        Text(
                            text = label,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (transactions.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(strings.noTransactionsFound, color = TextSecondaryMuted)
            }
        } else {
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                items(transactions, key = { it.id }) { tx ->
                    val isExpanded = expandedTransactionId == tx.id

                    val children = relatedChildrenMap[tx.id] ?: emptyList()
                    val cancellationChild = children.firstOrNull { it.type == TransactionType.CANCELLATION }
                    val correctionChildren = children.filter { it.type == TransactionType.CORRECTION }.sortedBy { it.timestamp }
                    val latestCorrection = correctionChildren.lastOrNull()

                    val isCanceled = cancellationChild != null || (tx.type == TransactionType.PURCHASE && tx.items.isNotEmpty() && tx.items.all { it.quantity == 0L })
                    val isCorrected = tx.type == TransactionType.PURCHASE && !isCanceled && correctionChildren.isNotEmpty()

                    val effectiveItems = AdminTransactionsViewModel.computeEffectiveItems(
                        originalItems = tx.items,
                        corrections = correctionChildren,
                        cancellation = cancellationChild
                    )
                    val refTx = referencedParentsMap[tx.referenceTransactionId] ?: transactions.firstOrNull { it.id == tx.referenceTransactionId }

                    val cumulativeDelta = if (children.isNotEmpty()) {
                        children.sumOf { it.totalAmount }
                    } else if (isCanceled) {
                        -tx.totalAmount
                    } else {
                        0L
                    }

                    AdminTransactionAccordionCard(
                        transaction = tx,
                        effectiveItems = effectiveItems,
                        referencedTransaction = refTx,
                        cumulativeDelta = cumulativeDelta,
                        isExpanded = isExpanded,
                        isCanceled = isCanceled,
                        isCorrected = isCorrected,
                        isSubmitting = isSubmitting,
                        onExpandToggle = { onRequestToggleExpand(tx.id) },
                        onOpenCorrectionModal = { viewModel.openCorrectionModal(tx, effectiveItems) },
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

    if (correctionTarget != null) {
        val (origTx, currentEffective) = correctionTarget!!
        PurchaseCorrectionDialog(
            originalTransaction = origTx,
            currentItems = currentEffective,
            isSubmitting = isSubmitting,
            onApplyCorrection = viewModel::applyPurchaseCorrection,
            onDismiss = viewModel::closeCorrectionModal
        )
    }

    if (errorMessage != null) {
        AlertDialog(
            onDismissRequest = viewModel::clearErrorMessage,
            shape = RoundedCornerShape(16.dp),
            containerColor = SurfaceWhite,
            title = {
                Text(
                    text = strings.errorTitle,
                    fontWeight = FontWeight.Bold,
                    color = ColorDangerCrimson
                )
            },
            text = {
                Text(errorMessage ?: "")
            },
            confirmButton = {
                Button(
                    onClick = viewModel::clearErrorMessage,
                    colors = ButtonDefaults.buttonColors(containerColor = ColorDangerCrimson)
                ) {
                    Text(strings.ok, color = SurfaceWhite)
                }
            }
        )
    }
}

