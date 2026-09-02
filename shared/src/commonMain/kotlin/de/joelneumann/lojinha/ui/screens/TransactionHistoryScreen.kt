package de.joelneumann.lojinha.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.joelneumann.lojinha.domain.model.AvatarType
import de.joelneumann.lojinha.domain.model.Language
import de.joelneumann.lojinha.domain.model.SecondaryCurrency
import de.joelneumann.lojinha.domain.model.SystemSettings
import de.joelneumann.lojinha.domain.model.Transaction
import de.joelneumann.lojinha.domain.model.TransactionType
import de.joelneumann.lojinha.domain.model.User
import de.joelneumann.lojinha.domain.model.UserAvatarConfig
import de.joelneumann.lojinha.ui.components.general.HeaderBar
import de.joelneumann.lojinha.ui.components.general.LogoutButton
import de.joelneumann.lojinha.ui.components.history.TransactionFilterBar
import de.joelneumann.lojinha.ui.components.history.TransactionItemCard
import de.joelneumann.lojinha.ui.components.history.UserSettingsModalDialog
import de.joelneumann.lojinha.ui.components.shopping.UserBalanceHeader
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.i18n.LanguageManager
import de.joelneumann.lojinha.ui.theme.*
import de.joelneumann.lojinha.ui.utils.Formatting
import de.joelneumann.lojinha.ui.utils.currentTimeMillis
import androidx.compose.ui.input.pointer.pointerInput
import de.joelneumann.lojinha.ui.viewmodel.TransactionHistoryViewModel

@Composable
fun TransactionHistoryScreen(
    viewModel: TransactionHistoryViewModel,
    user: User,
    settings: SystemSettings,
    onContinueShopping: () -> Unit,
    onLogout: () -> Unit,
    onUserUpdated: (User) -> Unit,
    onUserInteracted: () -> Unit = {}
) {
    val transactions by viewModel.transactions.collectAsState()
    val searchFilter by viewModel.searchFilter.collectAsState()
    val selectedTypeFilter by viewModel.selectedTypeFilter.collectAsState()
    val showSettingsModal by viewModel.showSettingsModal.collectAsState()

    val currentPage by viewModel.currentPage.collectAsState()
    val totalPages by viewModel.totalPages.collectAsState()
    val pageSize by viewModel.pageSize.collectAsState()
    val totalCount by viewModel.totalCount.collectAsState()
    val pinInput by viewModel.pinInput.collectAsState()
    val selectedLanguage by viewModel.selectedLanguage.collectAsState()
    val selectedSecondaryCurrency by viewModel.selectedSecondaryCurrency.collectAsState()
    val selectedAvatar by viewModel.selectedAvatar.collectAsState()

    LaunchedEffect(user.id) {
        viewModel.loadUserTransactions(user.id)
    }

    TransactionHistoryContent(
        user = user,
        settings = settings,
        transactions = transactions,
        searchFilter = searchFilter,
        selectedTypeFilter = selectedTypeFilter,
        showSettingsModal = showSettingsModal,
        currentPage = currentPage,
        totalPages = totalPages,
        pageSize = pageSize,
        totalCount = totalCount,
        pinInput = pinInput,
        selectedLanguage = selectedLanguage,
        selectedSecondaryCurrency = selectedSecondaryCurrency,
        selectedAvatar = selectedAvatar,
        onContinueShopping = onContinueShopping,
        onLogout = onLogout,
        onUserUpdated = onUserUpdated,
        onSearchFilterChange = { query ->
            onUserInteracted()
            viewModel.updateSearchFilter(query)
        },
        onTypeFilterSelect = { type ->
            onUserInteracted()
            viewModel.selectTypeFilter(type)
        },
        onPageChange = { page ->
            onUserInteracted()
            viewModel.setPage(page)
        },
        onPageSizeChange = { size ->
            onUserInteracted()
            viewModel.setPageSize(size)
        },
        onOpenSettingsModal = {
            onUserInteracted()
            viewModel.openSettingsModal(user)
        },
        onCloseSettingsModal = viewModel::closeSettingsModal,
        onUpdatePinInput = viewModel::updatePinInput,
        onUpdateLanguage = viewModel::updateLanguage,
        onUpdateSecondaryCurrency = viewModel::updateSecondaryCurrency,
        onUpdateAvatar = viewModel::updateAvatar,
        onSaveUserSettings = { viewModel.saveUserSettings(user, onUserUpdated) },
        onUserInteracted = onUserInteracted
    )
}

@Composable
fun TransactionHistoryContent(
    user: User,
    settings: SystemSettings,
    transactions: List<Transaction>,
    searchFilter: String,
    selectedTypeFilter: TransactionType?,
    showSettingsModal: Boolean,
    currentPage: Int,
    totalPages: Int,
    pageSize: Int,
    totalCount: Int,
    pinInput: String,
    selectedLanguage: Language,
    selectedSecondaryCurrency: SecondaryCurrency,
    selectedAvatar: UserAvatarConfig,
    onContinueShopping: () -> Unit,
    onLogout: () -> Unit,
    onUserUpdated: (User) -> Unit,
    onSearchFilterChange: (String) -> Unit,
    onTypeFilterSelect: (TransactionType?) -> Unit,
    onPageChange: (Int) -> Unit,
    onPageSizeChange: (Int) -> Unit,
    onOpenSettingsModal: () -> Unit,
    onCloseSettingsModal: () -> Unit,
    onUpdatePinInput: (String) -> Unit,
    onUpdateLanguage: (Language) -> Unit,
    onUpdateSecondaryCurrency: (SecondaryCurrency) -> Unit,
    onUpdateAvatar: (UserAvatarConfig) -> Unit,
    onSaveUserSettings: () -> Unit,
    onUserInteracted: () -> Unit = {}
) {
    val strings = I18n.current

    val transactionsWithBalance = remember(transactions, user.balance) {
        val sortedDesc = transactions.sortedByDescending { it.timestamp }
        val list = ArrayList<TransactionWithBalance>(sortedDesc.size)
        var current = user.balance
        for (tx in sortedDesc) {
            val after = current
            val before = after - tx.totalAmount
            list.add(TransactionWithBalance(tx, before, after))
            current = before
        }
        list
    }

    val rate = when (user.secondaryCurrency) {
        SecondaryCurrency.USD -> settings.usdExchangeRate
        SecondaryCurrency.EUR -> settings.eurExchangeRate
        else -> 0.0
    }

    val allTransactionsMap = remember(transactions) { transactions.associateBy { it.id } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                var lastInteractionTime = 0L
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent()
                        val now = currentTimeMillis()
                        if (now - lastInteractionTime >= 1000L) {
                            lastInteractionTime = now
                            onUserInteracted()
                        }
                    }
                }
            }
    ) {
        // Header Bar with "Continue Shopping" button on the left of Logout
        HeaderBar(
            title = strings.history,
            actions = {
                Button(
                    onClick = onContinueShopping,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentNavy),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ShoppingCart,
                            contentDescription = null,
                            tint = SurfaceWhite,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = strings.continueShopping,
                            color = SurfaceWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }

                LogoutButton(
                    onClick = onLogout
                )
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(SurfaceContainerLight)
                .padding(24.dp)
        ) {
            // Top Row: Reused UserBalanceHeader (Left) & User Settings Button (Right)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UserBalanceHeader(
                    user = user,
                    rate = rate,
                    modifier = Modifier.weight(1f)
                )

                OutlinedButton(
                    onClick = onOpenSettingsModal,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, DividerBorder),
                    modifier = Modifier.height(52.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = PrimaryNavy,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = strings.userSettings,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryNavy,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Search & Filter Bar reusing SearchInputField
            TransactionFilterBar(
                searchFilter = searchFilter,
                selectedTypeFilter = selectedTypeFilter,
                onSearchFilterChange = onSearchFilterChange,
                onTypeFilterSelect = onTypeFilterSelect
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Transaction Ledger List & Pagination Bar
            if (transactionsWithBalance.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(strings.noTransactionsFound, color = TextSecondaryMuted)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f).fillMaxWidth()
                ) {
                    items(transactionsWithBalance, key = { it.transaction.id }) { txWithBalance ->
                        TransactionItemCard(
                            txWithBalance = txWithBalance,
                            allTransactionsMap = allTransactionsMap,
                            rate = rate,
                            secondaryCurrency = user.secondaryCurrency
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            de.joelneumann.lojinha.ui.components.general.PaginationBar(
                currentPage = currentPage,
                totalPages = totalPages,
                pageSize = pageSize,
                totalCount = totalCount,
                onPageChange = onPageChange,
                onPageSizeChange = onPageSizeChange
            )
        }

        // User Settings Modal Dialog
        if (showSettingsModal) {
            UserSettingsModalDialog(
                user = user,
                pinInput = pinInput,
                selectedLanguage = selectedLanguage,
                selectedSecondaryCurrency = selectedSecondaryCurrency,
                selectedAvatar = selectedAvatar,
                onPinInputChange = onUpdatePinInput,
                onLanguageSelect = onUpdateLanguage,
                onSecondaryCurrencySelect = onUpdateSecondaryCurrency,
                onAvatarSelect = onUpdateAvatar,
                onDismiss = onCloseSettingsModal,
                onSave = onSaveUserSettings
            )
        }
    }
}

data class TransactionWithBalance(
    val transaction: Transaction,
    val balanceBefore: Long,
    val balanceAfter: Long
)
