package de.joelneumann.lojinha.ui.screens.admin

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.key.*
import de.joelneumann.lojinha.ui.components.general.AppVerticalScrollbar
import de.joelneumann.lojinha.ui.utils.pageDown
import de.joelneumann.lojinha.ui.utils.pageUp
import kotlinx.coroutines.launch
import de.joelneumann.lojinha.domain.model.TransactionType
import de.joelneumann.lojinha.ui.components.admin.AdminTopBar
import de.joelneumann.lojinha.ui.components.admin.transactions.AdminTransactionAccordionCard
import de.joelneumann.lojinha.ui.components.admin.transactions.PurchaseCorrectionDialog
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.theme.*
import de.joelneumann.lojinha.ui.viewmodel.admin.AdminTransactionsViewModel

@Composable
fun AdminTransactionsTabScreen(
    viewModel: AdminTransactionsViewModel,
    expandedTransactionId: String?,
    onRequestToggleExpand: (String?) -> Unit,
    onRequestExpandTransaction: (String) -> Unit,
    onRegisterPageScroller: (((Boolean) -> Boolean) -> Unit)? = null
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
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(onRegisterPageScroller, correctionTarget, errorMessage) {
        onRegisterPageScroller?.invoke { isDown ->
            if (correctionTarget == null && errorMessage == null) {
                coroutineScope.launch {
                    if (isDown) listState.pageDown() else listState.pageUp()
                }
                true
            } else false
        }
    }

    LaunchedEffect(Unit) {
        listState.scrollToItem(0)
        viewModel.loadData()
    }

    LaunchedEffect(currentPage, pageSize, searchQuery, selectedTypeFilter) {
        listState.scrollToItem(0)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.PageDown -> {
                            if (correctionTarget == null && errorMessage == null) {
                                coroutineScope.launch { listState.pageDown() }
                                true
                            } else false
                        }
                        Key.PageUp -> {
                            if (correctionTarget == null && errorMessage == null) {
                                coroutineScope.launch { listState.pageUp() }
                                true
                            } else false
                        }
                        else -> false
                    }
                } else false
            }
    ) {
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
                Surface(
                    onClick = { viewModel.updateTypeFilter(type) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) AccentNavy else SurfaceWhite,
                    contentColor = if (isSelected) SurfaceWhite else PrimaryNavy,
                    border = if (isSelected) null else BorderStroke(1.dp, DividerBorder),
                    shadowElevation = if (isSelected) 2.dp else 1.dp,
                    modifier = Modifier
                        .height(38.dp)
                        .pointerHoverIcon(PointerIcon.Hand)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(horizontal = 14.dp),
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

        Spacer(modifier = Modifier.height(8.dp))

        if (transactions.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(strings.noTransactionsFound, color = TextSecondaryMuted)
            }
        } else {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 16.dp, end = 14.dp),
                    modifier = Modifier.fillMaxSize()
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

                AppVerticalScrollbar(
                    scrollState = listState,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .padding(vertical = 4.dp)
                )
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

