package de.joelneumann.lojinha.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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

    val filteredTransactions = remember(transactions, searchFilter, selectedTypeFilter) {
        transactions.filter { tx ->
            val matchesType = selectedTypeFilter == null || tx.type == selectedTypeFilter
            val matchesText = searchFilter.isBlank() ||
                    tx.userNameSnapshot.contains(searchFilter, ignoreCase = true) ||
                    (tx.note != null && tx.note.contains(searchFilter, ignoreCase = true)) ||
                    tx.items.any { it.productName.contains(searchFilter, ignoreCase = true) }
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
        // Header Bar Actions
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = SurfaceWhite,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = I18n.get("history", language),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryNavy
                    )
                    Text(
                        text = "${user.name} — ${I18n.get("balance", language)}: ${Formatting.formatBrl(user.balance)}${Formatting.formatSecondaryCurrency(user.balance, user.secondaryCurrency, rate)}",
                        fontSize = 14.sp,
                        color = TextSecondaryMuted
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onContinueShopping,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentNavy),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(I18n.get("continue_shopping", language), color = SurfaceWhite, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { viewModel.openSettingsModal(user) },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(I18n.get("user_settings", language), fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onLogout,
                        colors = ButtonDefaults.buttonColors(containerColor = ColorDangerCrimson),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(I18n.get("logout", language), color = SurfaceWhite, fontWeight = FontWeight.Bold)
                    }
                }
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
                placeholder = { Text(I18n.get("history_filter_placeholder", language), color = TextSecondaryMuted) },
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
                    I18n.get("history_filter_all", language) to null,
                    I18n.get("history_type_purchase", language) to TransactionType.PURCHASE,
                    I18n.get("history_type_deposit", language) to TransactionType.ADMIN_DEPOSIT,
                    I18n.get("history_type_cancellation", language) to TransactionType.CANCELLATION
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
                items(filteredTransactions, key = { it.id }) { tx ->
                    TransactionItemCard(tx = tx, language = language, rate = rate, secondaryCurrency = user.secondaryCurrency)
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
                        text = I18n.get("user_settings_title", language),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryNavy
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // PIN Setting
                    Text(text = I18n.get("set_pin", language), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
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
                    Text(text = I18n.get("preferred_language", language), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
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
                    Text(text = I18n.get("secondary_currency", language), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            SecondaryCurrency.NONE to I18n.get("secondary_currency_none", language),
                            SecondaryCurrency.USD to I18n.get("secondary_currency_usd", language),
                            SecondaryCurrency.EUR to I18n.get("secondary_currency_eur", language)
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
                    Text(text = I18n.get("assigned_barcode_id", language), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
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
                            Text(I18n.get("cancel", language))
                        }

                        Button(
                            onClick = {
                                viewModel.saveUserSettings(user, onUserUpdated)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentNavy)
                        ) {
                            Text(I18n.get("save", language), color = SurfaceWhite, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TransactionItemCard(
    tx: Transaction,
    language: Language,
    rate: Double,
    secondaryCurrency: SecondaryCurrency
) {
    val dateStr = remember(tx.timestamp) {
        val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm")
        sdf.format(Date(tx.timestamp))
    }

    val typeLabel = when (tx.type) {
        TransactionType.PURCHASE -> "🛒 ${I18n.get("history_type_purchase", language)}"
        TransactionType.ADMIN_DEPOSIT -> "💵 ${I18n.get("history_type_deposit", language)}"
        TransactionType.ADMIN_WITHDRAWAL -> "📤 ${I18n.get("history_type_withdrawal", language)}"
        TransactionType.CANCELLATION -> "🔄 ${I18n.get("history_type_cancellation", language)}"
        TransactionType.CORRECTION -> "✏️ ${I18n.get("history_type_correction", language)}"
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

            if (!tx.note.isNull_or_blank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Note: ${tx.note}",
                    fontSize = 13.sp,
                    color = TextSecondarySubtle
                )
            }

            // Direct Visibility of Purchased Items inside Transaction Card
            if (tx.items.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Divider(color = DividerBorder)
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
