package de.joelneumann.lojinha.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.joelneumann.lojinha.domain.model.*
import de.joelneumann.lojinha.domain.service.VeggieSettlementCalculator
import de.joelneumann.lojinha.ui.components.admin.AdminBadgeType
import de.joelneumann.lojinha.ui.components.admin.AdminStatusBadge
import de.joelneumann.lojinha.ui.components.general.ConfirmationDialog
import de.joelneumann.lojinha.ui.components.veggie.VeggieProductImage
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.i18n.LanguageManager
import de.joelneumann.lojinha.ui.theme.*
import de.joelneumann.lojinha.ui.utils.Formatting
import de.joelneumann.lojinha.ui.utils.IsoWeekUtils
import de.joelneumann.lojinha.ui.utils.SelectAllOnFocusEffect
import de.joelneumann.lojinha.ui.utils.containsIgnoreAccents
import de.joelneumann.lojinha.ui.utils.rememberSelectAllOnFocusState
import de.joelneumann.lojinha.ui.viewmodel.BuyerVeggieViewModel
import de.joelneumann.lojinha.ui.viewmodel.admin.AdminVeggieViewModel

@Composable
fun BuyerVeggiePortalScreen(
    viewModel: BuyerVeggieViewModel,
    onExportPdf: (Language) -> Unit,
    onUnsavedChangesChanged: (Boolean) -> Unit,
    onLoggedOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = I18n.current
    val currentLanguage = LanguageManager.currentLanguage
    val roundState by viewModel.roundState.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val isExportingPdf by viewModel.isExportingPdf.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val marketPrices by viewModel.marketPrices.collectAsState()
    val totalSpent by viewModel.totalSpent.collectAsState()
    val buyerFeeInput by viewModel.buyerFeeInput.collectAsState()
    val hasUnsavedChanges by viewModel.hasUnsavedChanges.collectAsState()
    val feedbackMessage by viewModel.feedbackMessage.collectAsState()
    val isErrorFeedback by viewModel.isErrorFeedback.collectAsState()

    var showLogoutConfirmDialog by remember { mutableStateOf(false) }
    var showLanguageMenu by remember { mutableStateOf(false) }

    DisposableEffect(hasUnsavedChanges) {
        onUnsavedChangesChanged(hasUnsavedChanges)
        onDispose {
            onUnsavedChangesChanged(false)
        }
    }

    if (showLogoutConfirmDialog) {
        ConfirmationDialog(
            title = strings.buyerLogoutBtn,
            message = strings.buyerUnsavedChangesLogoutWarning,
            confirmText = strings.buyerLogoutBtn,
            onConfirm = {
                showLogoutConfirmDialog = false
                onUnsavedChangesChanged(false)
                viewModel.logout(onLoggedOut)
            },
            onDismiss = { showLogoutConfirmDialog = false }
        )
    }

    val activeRound = roundState?.round
    val isClosedRound = activeRound?.status == VeggieRoundStatus.CLOSED

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val isCompact = maxWidth < 680.dp

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                Surface(
                    color = PrimaryNavy,
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = if (isCompact) 12.dp else 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ShoppingCart,
                                contentDescription = null,
                                tint = SurfaceWhite,
                                modifier = Modifier.size(22.dp)
                            )
                            Column {
                                Text(
                                    text = strings.buyerPortalTitle,
                                    color = SurfaceWhite,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = if (isCompact) 16.sp else 18.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (activeRound != null) {
                                    val isoWeek = remember(activeRound.openedAt) {
                                        IsoWeekUtils.fromEpochMillis(activeRound.openedAt)
                                    }
                                    val localizedStatus = when (activeRound.status) {
                                        VeggieRoundStatus.OPEN -> strings.roundStatusOpen
                                        VeggieRoundStatus.CLOSED -> strings.roundStatusClosed
                                        VeggieRoundStatus.SETTLED -> strings.roundStatusSettled
                                    }
                                    Text(
                                        text = "${strings.weekOfYearLabel(isoWeek.weekOfYear, isoWeek.isoYear)} • $localizedStatus",
                                        color = SurfaceWhite.copy(alpha = 0.8f),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box {
                                TextButton(
                                    onClick = { showLanguageMenu = true },
                                    colors = ButtonDefaults.textButtonColors(contentColor = SurfaceWhite)
                                ) {
                                    Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(currentLanguage.code.uppercase(), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                DropdownMenu(
                                    expanded = showLanguageMenu,
                                    onDismissRequest = { showLanguageMenu = false }
                                ) {
                                    Language.entries.forEach { lang ->
                                        DropdownMenuItem(
                                            text = { Text(lang.label) },
                                            onClick = {
                                                LanguageManager.setLanguage(lang)
                                                showLanguageMenu = false
                                            }
                                        )
                                    }
                                }
                            }

                            IconButton(
                                onClick = {
                                    if (hasUnsavedChanges) {
                                        showLogoutConfirmDialog = true
                                    } else {
                                        viewModel.logout(onLoggedOut)
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Logout,
                                    contentDescription = strings.buyerLogoutBtn,
                                    tint = SurfaceWhite
                                )
                            }
                        }
                    }
                }
            },
            bottomBar = {
                if (isClosedRound && isCompact) {
                    Surface(
                        color = SurfaceWhite,
                        shadowElevation = 8.dp,
                        border = BorderStroke(1.dp, DividerBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { onExportPdf(currentLanguage) },
                                enabled = !isExportingPdf,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(strings.buyerExportPdfBtn, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
                            }
                            Button(
                                onClick = {
                                    viewModel.saveDraft(
                                        successMessage = strings.buyerDraftSavedSuccess,
                                        errorMessage = strings.buyerDraftSaveError
                                    )
                                },
                                enabled = hasUnsavedChanges && !isSaving,
                                colors = ButtonDefaults.buttonColors(containerColor = AccentNavy),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(strings.buyerSaveDraftBtn, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.TopCenter
            ) {
                if (isLoading && roundState == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = PrimaryNavy)
                    }
                } else if (!isClosedRound) {
                    val isRoundOpen = activeRound?.status == VeggieRoundStatus.OPEN
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = SurfaceWhite,
                            border = BorderStroke(1.dp, DividerBorder),
                            shadowElevation = 2.dp,
                            modifier = Modifier.widthIn(max = 520.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = if (isRoundOpen) Icons.Default.HourglassTop else Icons.Default.LockClock,
                                    contentDescription = null,
                                    tint = if (isRoundOpen) ColorWarningAmber else PrimaryNavy,
                                    modifier = Modifier.size(48.dp)
                                )
                                Text(
                                    text = if (isRoundOpen) strings.buyerRoundOpenLockedTitle else strings.buyerNoActiveRoundTitle,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryNavy,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = if (isRoundOpen) strings.buyerRoundOpenLockedMessage else strings.buyerNoActiveRoundMessage,
                                    fontSize = 14.sp,
                                    color = TextSecondaryMuted,
                                    textAlign = TextAlign.Center
                                )
                                feedbackMessage?.let { msg ->
                                    Text(
                                        text = msg,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isErrorFeedback) ColorDangerCrimson else ColorSuccessEmerald,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                } else {
                    val state = roundState!!
                    val filteredItems = remember(state.items, searchQuery) {
                        val q = searchQuery.trim()
                        if (q.isBlank()) state.items
                        else state.items.filter { it.name.containsIgnoreAccents(q) }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 960.dp)
                            .padding(horizontal = if (isCompact) 12.dp else 24.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Feedback banner
                        feedbackMessage?.let { msg ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isErrorFeedback) ColorDangerCrimson.copy(alpha = 0.1f) else ColorSuccessEmerald.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, if (isErrorFeedback) ColorDangerCrimson else ColorSuccessEmerald),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = msg,
                                        color = if (isErrorFeedback) ColorDangerCrimson else ColorSuccessEmerald,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = { viewModel.clearFeedback() }, modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }

                        // Outdated PDF warning banner
                        if (state.ordersChangedSinceLastPdf) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = ColorWarningAmber.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, ColorWarningAmber),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = ColorWarningAmber)
                                    Text(
                                        text = strings.buyerOrdersChangedSincePdfBanner,
                                        fontSize = 13.sp,
                                        color = PrimaryNavy,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        // Controls card: Search Field + Desktop Action Buttons
                        val buyerExportedAt = state.round?.buyerPortalPdfExportedAt
                        val pdfStatusText = when {
                            buyerExportedAt == null -> strings.buyerPdfNotExportedBadge
                            state.ordersChangedSinceLastPdf -> strings.buyerPdfOutdatedBadge
                            else -> strings.buyerPdfUpToDateBadge
                        }
                        val pdfStatusType = when {
                            buyerExportedAt == null -> AdminBadgeType.NAVY
                            state.ordersChangedSinceLastPdf -> AdminBadgeType.WARNING
                            else -> AdminBadgeType.SUCCESS
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = SurfaceWhite,
                            border = BorderStroke(1.dp, DividerBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = searchQuery,
                                        onValueChange = viewModel::updateSearchQuery,
                                        placeholder = { Text(strings.buyerSearchPlaceholder, fontSize = 13.sp) },
                                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                        trailingIcon = {
                                            if (searchQuery.isNotEmpty()) {
                                                IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                                    Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        },
                                        singleLine = true,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f)
                                    )

                                    if (!isCompact) {
                                        OutlinedButton(
                                            onClick = { onExportPdf(currentLanguage) },
                                            enabled = !isExportingPdf,
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.height(54.dp)
                                        ) {
                                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(strings.buyerExportPdfBtn, fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            AdminStatusBadge(
                                                text = pdfStatusText,
                                                type = pdfStatusType
                                            )
                                        }

                                        Button(
                                            onClick = {
                                                viewModel.saveDraft(
                                                    successMessage = strings.buyerDraftSavedSuccess,
                                                    errorMessage = strings.buyerDraftSaveError
                                                )
                                            },
                                            enabled = hasUnsavedChanges && !isSaving,
                                            colors = ButtonDefaults.buttonColors(containerColor = AccentNavy),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.height(54.dp)
                                        ) {
                                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(strings.buyerSaveDraftBtn, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                if (isCompact) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AdminStatusBadge(
                                            text = pdfStatusText,
                                            type = pdfStatusType
                                        )
                                    }
                                }
                            }
                        }

                        val submitDraft = {
                            if (hasUnsavedChanges && !isSaving) {
                                viewModel.saveDraft(
                                    successMessage = strings.buyerDraftSavedSuccess,
                                    errorMessage = strings.buyerDraftSaveError
                                )
                            }
                        }

                        // Product Price List
                        LazyColumn(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 8.dp)
                        ) {
                            items(filteredItems, key = { it.productId }) { item ->
                                BuyerProductPriceCard(
                                    item = item,
                                    unitPriceInput = marketPrices[item.productId].orEmpty(),
                                    totalSpentInput = totalSpent[item.productId].orEmpty(),
                                    onUnitPriceChange = { viewModel.updateMarketUnitPrice(item.productId, it) },
                                    onTotalSpentChange = { viewModel.updateTotalSpent(item.productId, it) },
                                    onSubmit = submitDraft,
                                    isCompact = isCompact
                                )
                            }

                            item(key = "buyer-fee-input-card") {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = SurfaceWhite,
                                    border = BorderStroke(1.dp, DividerBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = strings.buyerOperationFeeLabel,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = PrimaryNavy,
                                            modifier = Modifier.weight(1f)
                                        )
                                        BuyerAmountTextField(
                                            label = strings.buyerOperationFeeLabel,
                                            value = buyerFeeInput,
                                            onValueChange = { viewModel.updateBuyerFee(it) },
                                            onSubmit = submitDraft,
                                            prefix = "R$",
                                            modifier = Modifier.width(if (isCompact) 160.dp else 220.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Bottom Summary Card: Non-editable Totals like PDF on the bottom right
                        val subtotalCents = remember(state.items, marketPrices, totalSpent) {
                            state.items.sumOf { item ->
                                val unitCents = marketPrices[item.productId]
                                    ?.takeIf { it.isNotBlank() }
                                    ?.let(AdminVeggieViewModel::parseBrlInputToCents)
                                val spentCents = totalSpent[item.productId]
                                    ?.takeIf { it.isNotBlank() }
                                    ?.let(AdminVeggieViewModel::parseBrlInputToCents)
                                VeggieSettlementCalculator.resolveAuthoritativeTotalSpent(
                                    marketUnitPriceCents = unitCents,
                                    totalSpentCents = spentCents,
                                    totalDeliveredQtyScaled = item.totalOrderedScaled
                                )
                            }
                        }
                        val buyerFeeCents = remember(buyerFeeInput) {
                            if (buyerFeeInput.isBlank()) 0L
                            else (AdminVeggieViewModel.parseBrlInputToCents(buyerFeeInput) ?: 0L).coerceAtLeast(0L)
                        }
                        val grandTotalCents = subtotalCents + buyerFeeCents

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = SurfaceWhite,
                            border = BorderStroke(1.dp, DividerBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = if (isCompact) Arrangement.Start else Arrangement.End
                            ) {
                                BuyerPdfTotalsSummary(
                                    subtotalCents = subtotalCents,
                                    buyerFeeCents = buyerFeeCents,
                                    grandTotalCents = grandTotalCents,
                                    modifier = if (isCompact) Modifier.fillMaxWidth() else Modifier.widthIn(min = 280.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BuyerProductPriceCard(
    item: BuyerVeggieProductRowDto,
    unitPriceInput: String,
    totalSpentInput: String,
    onUnitPriceChange: (String) -> Unit,
    onTotalSpentChange: (String) -> Unit,
    onSubmit: () -> Unit,
    isCompact: Boolean
) {
    val strings = I18n.current
    val currentLanguage = LanguageManager.currentLanguage
    val decimalSep = Formatting.decimalSeparator(currentLanguage)
    val orderedFormatted = remember(item.totalOrderedScaled, item.allowDecimals, decimalSep) {
        VeggieQuantityScaling.formatScaledQuantity(item.totalOrderedScaled, item.allowDecimals, decimalSep)
    }
    val unitDisplay = if (item.isDualUnit) {
        "${item.unitAbbreviation} (${item.deliveryUnitAbbreviation})"
    } else {
        item.unitAbbreviation
    }
    val unitPriceCents = remember(unitPriceInput) {
        if (unitPriceInput.isBlank()) null else AdminVeggieViewModel.parseBrlInputToCents(unitPriceInput)
    }
    val totalSpentCents = remember(totalSpentInput) {
        if (totalSpentInput.isBlank()) null else AdminVeggieViewModel.parseBrlInputToCents(totalSpentInput)
    }
    val mismatch = remember(unitPriceCents, totalSpentCents, item.totalOrderedScaled, item.isDualUnit) {
        if (item.isDualUnit) null
        else VeggieSettlementCalculator.detectPriceTotalMismatch(unitPriceCents, totalSpentCents, item.totalOrderedScaled)
    }
    val effectiveUnitPriceCents = remember(totalSpentCents, item.totalOrderedScaled, item.isDualUnit) {
        if (item.isDualUnit) 0L
        else totalSpentCents?.let {
            VeggieSettlementCalculator.calculateEffectiveUnitPriceCents(it, item.totalOrderedScaled)
        } ?: 0L
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = SurfaceWhite,
        border = BorderStroke(1.dp, if (mismatch != null) ColorWarningAmber else DividerBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (isCompact) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        VeggieProductImage(
                            productId = item.productId,
                            imageFileName = item.imageFileName,
                            modifier = Modifier.size(44.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = PrimaryNavy,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${strings.buyerTotalOrderedLabel}: $orderedFormatted $unitDisplay",
                                fontSize = 13.sp,
                                color = AccentNavy,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BuyerAmountTextField(
                            label = strings.buyerMarketUnitPriceLabel,
                            value = unitPriceInput,
                            onValueChange = onUnitPriceChange,
                            onSubmit = onSubmit,
                            prefix = "R$",
                            modifier = Modifier.weight(1f)
                        )
                        BuyerAmountTextField(
                            label = strings.buyerTotalSpentLabel,
                            value = totalSpentInput,
                            onValueChange = onTotalSpentChange,
                            onSubmit = onSubmit,
                            prefix = "R$",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    VeggieProductImage(
                        productId = item.productId,
                        imageFileName = item.imageFileName,
                        modifier = Modifier.size(44.dp)
                    )
                    Column(modifier = Modifier.weight(1.4f)) {
                        Text(
                            text = item.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = PrimaryNavy,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${strings.buyerTotalOrderedLabel}: $orderedFormatted $unitDisplay",
                            fontSize = 13.sp,
                            color = AccentNavy,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    BuyerAmountTextField(
                        label = strings.buyerMarketUnitPriceLabel,
                        value = unitPriceInput,
                        onValueChange = onUnitPriceChange,
                        onSubmit = onSubmit,
                        prefix = "R$",
                        modifier = Modifier.weight(1f)
                    )
                    BuyerAmountTextField(
                        label = strings.buyerTotalSpentLabel,
                        value = totalSpentInput,
                        onValueChange = onTotalSpentChange,
                        onSubmit = onSubmit,
                        prefix = "R$",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (mismatch != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AdminStatusBadge(
                        text = strings.priceTotalMismatchBadge,
                        type = AdminBadgeType.WARNING
                    )
                    Text(
                        text = strings.buyerPriceMismatchExpected(
                            qty = orderedFormatted,
                            unit = item.unitAbbreviation,
                            unitPrice = Formatting.formatBrl(unitPriceCents ?: 0L),
                            expectedTotal = Formatting.formatBrl(mismatch.expectedTotalCents)
                        ),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ColorWarningAmber
                    )
                }
            } else if (unitPriceCents == null && effectiveUnitPriceCents > 0L) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = strings.effectiveUnitPriceShort(Formatting.formatBrl(effectiveUnitPriceCents), item.unitAbbreviation),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondaryMuted
                    )
                }
            }
        }
    }
}

private fun filterBrlAmountInput(input: String): String {
    val result = StringBuilder()
    var hasSeparator = false
    var decimalDigits = 0
    for (ch in input) {
        when {
            ch.isDigit() -> if (!hasSeparator || decimalDigits < 2) {
                result.append(ch)
                if (hasSeparator) decimalDigits++
            }
            (ch == '.' || ch == ',') && !hasSeparator -> {
                hasSeparator = true
                result.append(ch)
            }
        }
    }
    return result.toString()
}

@Composable
private fun BuyerAmountTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    prefix: String,
    modifier: Modifier = Modifier
) {
    val focusState = rememberSelectAllOnFocusState()
    var tfv by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    LaunchedEffect(value) {
        if (tfv.text != value) {
            tfv = tfv.copy(text = value, selection = TextRange(value.length))
        }
    }
    SelectAllOnFocusEffect(
        state = focusState,
        currentValue = { tfv },
        onUpdateValue = { tfv = it }
    )

    OutlinedTextField(
        value = tfv,
        onValueChange = { newTfv ->
            if (focusState.shouldForceSelectAllOnChange(newTfv.text, tfv.text)) {
                tfv = newTfv.copy(selection = TextRange(0, newTfv.text.length))
            } else {
                val filteredText = filterBrlAmountInput(newTfv.text)
                val clampedSelection = TextRange(
                    newTfv.selection.start.coerceAtMost(filteredText.length),
                    newTfv.selection.end.coerceAtMost(filteredText.length)
                )
                if (filteredText != tfv.text) {
                    focusState.onCursorNavigationKey()
                    tfv = newTfv.copy(text = filteredText, selection = clampedSelection)
                    onValueChange(filteredText)
                } else {
                    tfv = newTfv.copy(text = filteredText, selection = clampedSelection)
                }
            }
        },
        label = { Text(label, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        prefix = { Text("$prefix ", fontSize = 13.sp, color = TextSecondaryMuted) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(
            onDone = { onSubmit() }
        ),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown &&
                    (keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter)
                ) {
                    onSubmit()
                    true
                } else {
                    false
                }
            }
            .onFocusChanged { fs ->
                if (fs.isFocused) {
                    focusState.onFocusGained(tfv.text)
                    tfv = tfv.copy(selection = TextRange(0, tfv.text.length))
                } else {
                    focusState.onFocusLost()
                }
            }
    )
}

@Composable
private fun BuyerPdfTotalsSummary(
    subtotalCents: Long,
    buyerFeeCents: Long,
    grandTotalCents: Long,
    modifier: Modifier = Modifier
) {
    val strings = I18n.current
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(strings.buyerSubtotalLabel, fontSize = 13.sp, color = TextSecondaryMuted)
            Text(Formatting.formatBrl(subtotalCents), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PrimaryNavy)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(strings.buyerOperationFeeLabel, fontSize = 13.sp, color = TextSecondaryMuted)
            Text(Formatting.formatBrl(buyerFeeCents), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PrimaryNavy)
        }
        HorizontalDivider(color = DividerBorder, modifier = Modifier.padding(vertical = 2.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(strings.buyerGrandTotalLabel, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = PrimaryNavy)
            Text(Formatting.formatBrl(grandTotalCents), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AccentNavy)
        }
    }
}
