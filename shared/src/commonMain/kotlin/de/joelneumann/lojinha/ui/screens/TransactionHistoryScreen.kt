package de.joelneumann.lojinha.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.joelneumann.lojinha.domain.model.Language
import de.joelneumann.lojinha.domain.model.SecondaryCurrency
import de.joelneumann.lojinha.domain.model.SystemSettings
import de.joelneumann.lojinha.domain.model.Transaction
import de.joelneumann.lojinha.domain.model.TransactionType
import de.joelneumann.lojinha.domain.model.User
import de.joelneumann.lojinha.ui.components.general.HeaderBar
import de.joelneumann.lojinha.ui.components.general.LogoutButton
import de.joelneumann.lojinha.ui.components.history.TransactionFilterBar
import de.joelneumann.lojinha.ui.components.history.TransactionItemCard
import de.joelneumann.lojinha.ui.components.history.UserSettingsModalDialog
import de.joelneumann.lojinha.ui.components.shopping.UserBalanceHeader
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.theme.*
import de.joelneumann.lojinha.ui.utils.Formatting
import de.joelneumann.lojinha.ui.viewmodel.TransactionHistoryViewModel

@Composable
fun TransactionHistoryScreen(
    viewModel: TransactionHistoryViewModel,
    user: User,
    language: Language,
    settings: SystemSettings,
    onLanguageSelected: (Language) -> Unit,
    onContinueShopping: () -> Unit,
    onLogout: () -> Unit,
    onUserUpdated: (User) -> Unit
) {
    val transactions by viewModel.transactions.collectAsState()
    val searchFilter by viewModel.searchFilter.collectAsState()
    val selectedTypeFilter by viewModel.selectedTypeFilter.collectAsState()
    val showSettingsModal by viewModel.showSettingsModal.collectAsState()

    val pinInput by viewModel.pinInput.collectAsState()
    val selectedLanguage by viewModel.selectedLanguage.collectAsState()
    val selectedSecondaryCurrency by viewModel.selectedSecondaryCurrency.collectAsState()

    LaunchedEffect(user.id) {
        viewModel.loadUserTransactions(user.id)
    }

    TransactionHistoryContent(
        user = user,
        language = language,
        settings = settings,
        transactions = transactions,
        searchFilter = searchFilter,
        selectedTypeFilter = selectedTypeFilter,
        showSettingsModal = showSettingsModal,
        pinInput = pinInput,
        selectedLanguage = selectedLanguage,
        selectedSecondaryCurrency = selectedSecondaryCurrency,
        onLanguageSelected = onLanguageSelected,
        onContinueShopping = onContinueShopping,
        onLogout = onLogout,
        onUserUpdated = onUserUpdated,
        onSearchFilterChange = viewModel::updateSearchFilter,
        onTypeFilterSelect = viewModel::selectTypeFilter,
        onOpenSettingsModal = { viewModel.openSettingsModal(user) },
        onCloseSettingsModal = viewModel::closeSettingsModal,
        onUpdatePinInput = viewModel::updatePinInput,
        onUpdateLanguage = viewModel::updateLanguage,
        onUpdateSecondaryCurrency = viewModel::updateSecondaryCurrency,
        onSaveUserSettings = { viewModel.saveUserSettings(user, onUserUpdated) }
    )
}

@Composable
fun TransactionHistoryContent(
    user: User,
    language: Language,
    settings: SystemSettings,
    transactions: List<Transaction>,
    searchFilter: String,
    selectedTypeFilter: TransactionType?,
    showSettingsModal: Boolean,
    pinInput: String,
    selectedLanguage: Language,
    selectedSecondaryCurrency: SecondaryCurrency,
    onLanguageSelected: (Language) -> Unit,
    onContinueShopping: () -> Unit,
    onLogout: () -> Unit,
    onUserUpdated: (User) -> Unit,
    onSearchFilterChange: (String) -> Unit,
    onTypeFilterSelect: (TransactionType?) -> Unit,
    onOpenSettingsModal: () -> Unit,
    onCloseSettingsModal: () -> Unit,
    onUpdatePinInput: (String) -> Unit,
    onUpdateLanguage: (Language) -> Unit,
    onUpdateSecondaryCurrency: (SecondaryCurrency) -> Unit,
    onSaveUserSettings: () -> Unit
) {
    val strings = I18n.get(language)

    val transactionsWithBalance = remember(transactions) {
        val sortedAsc = transactions.sortedBy { it.timestamp }
        var current = 0L
        val list = ArrayList<TransactionWithBalance>(sortedAsc.size)
        for (tx in sortedAsc) {
            val before = current
            val after = before + tx.totalAmount
            current = after
            list.add(TransactionWithBalance(tx, before, after))
        }
        list.sortedByDescending { it.transaction.timestamp }
    }

    val filteredTransactions = remember(transactionsWithBalance, searchFilter, selectedTypeFilter, language) {
        transactionsWithBalance.filter { item ->
            val tx = item.transaction
            val matchesType = selectedTypeFilter == null || tx.type == selectedTypeFilter
            val dateStr = Formatting.formatTimestamp(tx.timestamp, language)
            val dateStrEn = Formatting.formatTimestamp(tx.timestamp, Language.EN)
            val dateStrDe = Formatting.formatTimestamp(tx.timestamp, Language.DE)
            val dateStrBr = Formatting.formatTimestamp(tx.timestamp, Language.BR)
            val matchesText = searchFilter.isBlank() ||
                    tx.userNameSnapshot.contains(searchFilter, ignoreCase = true) ||
                    (tx.note != null && tx.note.contains(searchFilter, ignoreCase = true)) ||
                    tx.items.any { it.productName.contains(searchFilter, ignoreCase = true) } ||
                    dateStr.contains(searchFilter, ignoreCase = true) ||
                    dateStrEn.contains(searchFilter, ignoreCase = true) ||
                    dateStrDe.contains(searchFilter, ignoreCase = true) ||
                    dateStrBr.contains(searchFilter, ignoreCase = true)
            matchesType && matchesText
        }
    }

    val rate = when (user.secondaryCurrency) {
        SecondaryCurrency.USD -> settings.usdExchangeRate
        SecondaryCurrency.EUR -> settings.eurExchangeRate
        else -> 0.0
    }

    val allTransactionsMap = remember(transactions) { transactions.associateBy { it.id } }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header Bar with "Continue Shopping" button on the left of Logout
        HeaderBar(
            title = strings.history,
            currentLanguage = language,
            onLanguageSelected = onLanguageSelected,
            actions = {
                Button(
                    onClick = onContinueShopping,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentNavy),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "🛒 ${strings.continueShopping}",
                        color = SurfaceWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                LogoutButton(
                    language = language,
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
                    language = language,
                    modifier = Modifier.weight(1f)
                )

                OutlinedButton(
                    onClick = onOpenSettingsModal,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, DividerBorder),
                    modifier = Modifier.height(52.dp)
                ) {
                    Text(
                        text = "⚙️ ${strings.userSettings}",
                        fontWeight = FontWeight.Bold,
                        color = PrimaryNavy,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Search & Filter Bar reusing SearchInputField
            TransactionFilterBar(
                searchFilter = searchFilter,
                selectedTypeFilter = selectedTypeFilter,
                language = language,
                onSearchFilterChange = onSearchFilterChange,
                onTypeFilterSelect = onTypeFilterSelect
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Transaction Ledger List
            if (filteredTransactions.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No transactions found.", color = TextSecondaryMuted)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredTransactions, key = { it.transaction.id }) { txWithBalance ->
                        TransactionItemCard(
                            txWithBalance = txWithBalance,
                            allTransactionsMap = allTransactionsMap,
                            language = language,
                            rate = rate,
                            secondaryCurrency = user.secondaryCurrency
                        )
                    }
                }
            }
        }

        // User Settings Modal Dialog
        if (showSettingsModal) {
            UserSettingsModalDialog(
                user = user,
                pinInput = pinInput,
                selectedLanguage = selectedLanguage,
                selectedSecondaryCurrency = selectedSecondaryCurrency,
                language = language,
                onPinInputChange = onUpdatePinInput,
                onLanguageSelect = onUpdateLanguage,
                onSecondaryCurrencySelect = onUpdateSecondaryCurrency,
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
