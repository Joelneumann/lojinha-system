package de.joelneumann.lojinha.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.window.Dialog
import de.joelneumann.lojinha.domain.model.Language
import de.joelneumann.lojinha.domain.model.SecondaryCurrency
import de.joelneumann.lojinha.domain.model.SystemSettings
import de.joelneumann.lojinha.domain.model.Transaction
import de.joelneumann.lojinha.domain.model.TransactionType
import de.joelneumann.lojinha.domain.model.User
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.theme.*
import de.joelneumann.lojinha.ui.utils.Formatting
import de.joelneumann.lojinha.ui.viewmodel.TransactionHistoryViewModel
import java.text.SimpleDateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionHistoryScreen(
    viewModel: TransactionHistoryViewModel,
    user: User,
    language: Language,
    settings: SystemSettings,
    onContinueShopping: () -> Unit,
    onLogout: () -> Unit,
    onUserUpdated: (User) -> Unit
) {
    val strings = I18n.get(language)
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceContainerLight)
            .padding(24.dp)
    ) {
        // Top Action Row: Continue Shopping (Left), User & Balance Box (Center), User Settings (Right)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onContinueShopping,
                colors = ButtonDefaults.buttonColors(containerColor = AccentNavy),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(56.dp)
            ) {
                Text(
                    text = strings.continueShopping,
                    color = SurfaceWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = SurfaceWhite,
                shadowElevation = 2.dp,
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DividerBorder)),
                modifier = Modifier.weight(1f).height(56.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = user.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryNavy
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${strings.balance}: ",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextSecondarySubtle
                        )
                        Text(
                            text = Formatting.formatBrl(user.balance),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (user.balance >= 0) ColorSuccessEmerald else ColorDangerCrimson
                        )
                        val secText = Formatting.formatSecondaryCurrency(user.balance, user.secondaryCurrency, rate)
                        if (secText.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = secText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextSecondaryMuted
                            )
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = { viewModel.openSettingsModal(user) },
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, DividerBorder),
                modifier = Modifier.height(56.dp)
            ) {
                Text(
                    text = strings.userSettings,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryNavy,
                    fontSize = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Search & Filter Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchFilter,
                onValueChange = { viewModel.updateSearchFilter(it) },
                placeholder = { Text(strings.historyFilterPlaceholder, color = TextSecondaryMuted) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SurfaceWhite,
                    unfocusedContainerColor = SurfaceWhite,
                    focusedBorderColor = AccentNavy,
                    unfocusedBorderColor = DividerBorder
                ),
                singleLine = true
            )

            // Filter Type Buttons
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
                        onClick = { viewModel.selectTypeFilter(type) },
                        label = { Text(label, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentNavy,
                            selectedLabelColor = SurfaceWhite,
                            containerColor = SurfaceWhite
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val allTransactionsMap = remember(transactions) { transactions.associateBy { it.id } }

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
        Dialog(onDismissRequest = { viewModel.closeSettingsModal() }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = SurfaceWhite,
                modifier = Modifier.width(460.dp).wrapContentHeight()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = strings.userSettingsTitle,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryNavy
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // PIN Setting
                    Text(text = strings.setPin, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { viewModel.updatePinInput(it) },
                        placeholder = { Text("e.g. 1234 (leave blank for none)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Language Selection
                    Text(text = strings.preferredLanguage, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Language.entries.forEach { lang ->
                            val isSel = selectedLanguage == lang
                            OutlinedButton(
                                onClick = { viewModel.updateLanguage(lang) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isSel) AccentNavy else SurfaceWhite,
                                    contentColor = if (isSel) SurfaceWhite else PrimaryNavy
                                )
                            ) {
                                Text("${lang.flagEmoji} ${lang.code.uppercase()}", fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Secondary Display Currency Selection
                    Text(text = strings.secondaryCurrency, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            SecondaryCurrency.NONE to strings.secondaryCurrencyNone,
                            SecondaryCurrency.USD to strings.secondaryCurrencyUsd,
                            SecondaryCurrency.EUR to strings.secondaryCurrencyEur
                        ).forEach { (curr, label) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.updateSecondaryCurrency(curr) }
                                    .padding(vertical = 4.dp)
                            ) {
                                RadioButton(
                                    selected = selectedSecondaryCurrency == curr,
                                    onClick = { viewModel.updateSecondaryCurrency(curr) }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(label, fontSize = 14.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Assigned Barcode ID (Read-Only)
                    Text(text = strings.assignedBarcodeId, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SurfaceContainerHighLight, RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = user.userBarcodeNumber ?: "No barcode assigned",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondarySubtle
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.closeSettingsModal() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(strings.cancel)
                        }

                        Button(
                            onClick = {
                                viewModel.saveUserSettings(user, onUserUpdated)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentNavy)
                        ) {
                            Text(strings.save, color = SurfaceWhite, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

data class TransactionWithBalance(
    val transaction: Transaction,
    val balanceBefore: Long,
    val balanceAfter: Long
)

@Composable
private fun TransactionItemCard(
    txWithBalance: TransactionWithBalance,
    allTransactionsMap: Map<String, Transaction>,
    language: Language,
    rate: Double,
    secondaryCurrency: SecondaryCurrency
) {
    val tx = txWithBalance.transaction
    val balanceBefore = txWithBalance.balanceBefore
    val balanceAfter = txWithBalance.balanceAfter
    val strings = I18n.get(language)
    val dateStr = remember(tx.timestamp, language) {
        Formatting.formatTimestamp(tx.timestamp, language)
    }

    val displayNote = remember(tx, allTransactionsMap, language) {
        if (tx.type == TransactionType.CANCELLATION && tx.referenceTransactionId != null) {
            val refTx = allTransactionsMap[tx.referenceTransactionId]
            if (refTx != null) {
                val refDateStr = Formatting.formatTimestamp(refTx.timestamp, language)
                val refTypeStr = when (refTx.type) {
                    TransactionType.PURCHASE -> strings.historyTypePurchase
                    TransactionType.ADMIN_DEPOSIT -> strings.historyTypeDeposit
                    TransactionType.ADMIN_WITHDRAWAL -> strings.historyTypeWithdrawal
                    TransactionType.CANCELLATION -> strings.historyTypeCancellation
                    TransactionType.CORRECTION -> strings.historyTypeCorrection
                }
                strings.cancellationNote(refTypeStr, refDateStr)
            } else {
                tx.note
            }
        } else {
            tx.note
        }
    }

    val typeLabel = when (tx.type) {
        TransactionType.PURCHASE -> "🛒 ${strings.historyTypePurchase}"
        TransactionType.ADMIN_DEPOSIT -> "💵 ${strings.historyTypeDeposit}"
        TransactionType.ADMIN_WITHDRAWAL -> "📤 ${strings.historyTypeWithdrawal}"
        TransactionType.CANCELLATION -> "🔄 ${strings.historyTypeCancellation}"
        TransactionType.CORRECTION -> "✏️ ${strings.historyTypeCorrection}"
    }

    val isPositive = tx.totalAmount > 0
    val amountColor = if (isPositive) ColorSuccessEmerald else PrimaryNavy

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DividerBorder)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = typeLabel,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryNavy
                    )
                    Text(
                        text = "•  $dateStr",
                        fontSize = 13.sp,
                        color = TextSecondaryMuted
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${if (isPositive) "+" else ""}${Formatting.formatBrl(tx.totalAmount)}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = amountColor
                    )
                    val secFormatted = Formatting.formatSecondaryCurrency(tx.totalAmount, secondaryCurrency, rate)
                    if (secFormatted.isNotEmpty()) {
                        Text(secFormatted, fontSize = 12.sp, color = TextSecondaryMuted)
                    }
                }
            }

            if (!displayNote.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Note: $displayNote",
                    fontSize = 13.sp,
                    color = TextSecondarySubtle
                )
            }

            // Balance Flow Banner: Before ➔ After
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = SurfaceContainerHighLight,
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DividerBorder)),
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${strings.balance}:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondarySubtle
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = Formatting.formatBrl(balanceBefore),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondaryMuted
                        )

                        Text(
                            text = "➔",
                            fontSize = 12.sp,
                            color = TextSecondarySubtle
                        )

                        Text(
                            text = Formatting.formatBrl(balanceAfter),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (balanceAfter >= 0) PrimaryNavy else ColorDangerCrimson
                        )
                    }
                }
            }

            // Direct Visibility of Purchased Items inside Transaction Card
            if (tx.items.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = DividerBorder)
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    tx.items.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "• ${Formatting.formatQuantity(item.quantity, item.unitType)} ${item.productName} @ ${Formatting.formatBrl(item.unitPriceAtPurchase)}",
                                fontSize = 13.sp,
                                color = TextSecondarySubtle
                            )
                            Text(
                                text = Formatting.formatBrl(item.totalLinePrice),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = PrimaryNavy
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun String?.isNull_or_blank(): Boolean = this == null || this.trim().isEmpty()
