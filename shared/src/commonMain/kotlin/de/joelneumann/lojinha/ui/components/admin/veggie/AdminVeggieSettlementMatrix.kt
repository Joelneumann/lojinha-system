package de.joelneumann.lojinha.ui.components.admin.veggie

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.joelneumann.lojinha.domain.model.*
import de.joelneumann.lojinha.domain.service.VeggieRoundSettlementResult
import de.joelneumann.lojinha.domain.service.VeggieSettlementCalculator
import de.joelneumann.lojinha.ui.components.admin.AdminBadgeType
import de.joelneumann.lojinha.ui.components.admin.AdminLabeledField
import de.joelneumann.lojinha.ui.components.admin.AdminStatusBadge
import de.joelneumann.lojinha.ui.components.general.AppHorizontalScrollbar
import de.joelneumann.lojinha.ui.components.general.AppVerticalScrollbar
import de.joelneumann.lojinha.ui.components.general.ConfirmationDialog
import de.joelneumann.lojinha.ui.components.veggie.filterVeggieAmountInput
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.i18n.LanguageManager
import de.joelneumann.lojinha.ui.theme.*
import de.joelneumann.lojinha.ui.utils.Formatting
import de.joelneumann.lojinha.ui.utils.IsoWeekUtils
import de.joelneumann.lojinha.ui.utils.currentTimeMillis
import de.joelneumann.lojinha.ui.utils.sortedByAccentInsensitive
import de.joelneumann.lojinha.ui.viewmodel.admin.AddOrderEntryMode
import kotlinx.coroutines.delay

private val COL_PRODUCT_WIDTH = 170.dp
private val COL_CUSTOMER_WIDTH = 80.dp
private val COL_TOTAL_QTY_WIDTH = 90.dp
private val COL_MARKET_PRICE_WIDTH = 115.dp
private val COL_TOTAL_SPENT_WIDTH = 115.dp
private val COL_EFFECTIVE_PRICE_WIDTH = 105.dp
private val COL_DIVIDER_WIDTH = 26.dp
private val OVERVIEW_TOTAL_WIDTH = COL_TOTAL_QTY_WIDTH + COL_MARKET_PRICE_WIDTH + COL_TOTAL_SPENT_WIDTH + COL_EFFECTIVE_PRICE_WIDTH

@Composable
fun AdminVeggieSettlementMatrix(
    round: VeggieOrderRound,
    entries: List<VeggieOrderEntry>,
    products: List<VeggieProduct>,
    users: List<User>,
    draftActualQuantities: Map<String, String>,
    draftMarketPrices: Map<String, String>,
    draftTotalSpent: Map<String, String>,
    draftBuyerFeeInput: String,
    draftTransportFeeInput: String,
    draftTransporterUserId: String?,
    livePreview: VeggieRoundSettlementResult?,
    isSaving: Boolean,
    onUpdateActualQuantity: (entryId: String, input: String) -> Unit,
    onUpdateMarketUnitPrice: (productId: String, input: String) -> Unit,
    onUpdateTotalSpent: (productId: String, input: String) -> Unit,
    onUpdateBuyerFee: (String) -> Unit,
    onUpdateTransportFee: (String) -> Unit,
    onUpdateTransporterUser: (String?) -> Unit,
    onCloseRound: () -> Unit,
    onReopenRound: () -> Unit,
    onSaveDraft: () -> Unit,
    onExecuteSettlement: () -> Unit,
    onDeleteRound: (() -> Unit)? = null,
    onCompleteEmptyRound: (() -> Unit)? = null,
    onAddAdminEntry: (user: User, product: VeggieProduct, quantityInput: String, mode: AddOrderEntryMode) -> Unit,
    onExportBuyerPdf: ((roundId: String, language: Language) -> Unit)? = null,
    onExportWorkerPdf: ((roundId: String, language: Language) -> Unit)? = null,
    hasUnsavedChanges: Boolean = false,
    onResetChanges: (() -> Unit)? = null,
    unitTypes: List<VeggieUnitType> = emptyList(),
    modifier: Modifier = Modifier
) {
    val strings = I18n.current
    val currentLanguage = LanguageManager.currentLanguage
    val isoWeek = remember(round.openedAt) { IsoWeekUtils.fromEpochMillis(round.openedAt) }

    var showConfirmCloseRound by remember { mutableStateOf(false) }
    var showConfirmResetChanges by remember { mutableStateOf(false) }
    var showConfirmSettle by remember { mutableStateOf(false) }
    var showConfirmCompleteEmpty by remember { mutableStateOf(false) }
    var showConfirmDeleteRound by remember { mutableStateOf(false) }
    var showAddEntryDialog by remember { mutableStateOf(false) }
    var transporterDropdownExpanded by remember { mutableStateOf(false) }
    var showBuyerOverview by remember { mutableStateOf(true) }

    val animatedOverviewWidth by animateDpAsState(
        targetValue = if (showBuyerOverview) OVERVIEW_TOTAL_WIDTH else 0.dp,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "overviewWidthAnim"
    )

    // Distinct customers and products in this round
    val roundCustomers = remember(entries) {
        entries
            .map { it.userId to it.userNameSnapshot }
            .distinctBy { it.first }
            .sortedByAccentInsensitive { it.second }
    }

    val roundProductRows = remember(entries) {
        entries
            .groupBy { it.veggieProductId }
            .map { (productId, prodEntries) ->
                val sample = prodEntries.first()
                VeggieMatrixProductRowInfo(
                    productId = productId,
                    productName = sample.veggieNameSnapshot,
                    unitName = sample.unitNameSnapshot,
                    allowDecimals = sample.allowDecimalsSnapshot,
                    entriesByUserId = prodEntries.associateBy { it.userId }
                )
            }
            .sortedByAccentInsensitive { it.productName }
    }

    val previewEntryMap = remember(livePreview) {
        livePreview?.updatedEntries?.associateBy { it.id } ?: emptyMap()
    }
    val previewProductSettlementMap = remember(livePreview) {
        livePreview?.updatedProductSettlements?.associateBy { it.veggieProductId } ?: emptyMap()
    }
    val previewCustomerSummaryMap = remember(livePreview) {
        livePreview?.customerSummaries?.associateBy { it.userId } ?: emptyMap()
    }

    // Focus Requester Maps for custom Tab order
    val matrixFocusRequesters = remember(entries) {
        entries.associate { it.id to FocusRequester() }
    }
    val priceFocusRequesters = remember(roundProductRows) {
        roundProductRows.associate { it.productId to FocusRequester() }
    }
    val spentFocusRequesters = remember(roundProductRows) {
        roundProductRows.associate { it.productId to FocusRequester() }
    }
    val orderedMatrixEntryIds = remember(roundProductRows, roundCustomers) {
        buildList {
            for (row in roundProductRows) {
                for ((userId, _) in roundCustomers) {
                    val entry = row.entriesByUserId[userId]
                    if (entry != null) {
                        add(entry.id)
                    }
                }
            }
        }
    }
    val priceProductIds = remember(roundProductRows) {
        roundProductRows.map { it.productId }
    }

    val horizontalScrollState = rememberScrollState()
    val verticalScrollState = rememberScrollState()

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Round Lifecycle & PDF Export Header Card
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = SurfaceWhite,
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DividerBorder)),
            shadowElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Eco,
                            contentDescription = null,
                            tint = ColorSuccessEmerald,
                            modifier = Modifier.size(22.dp)
                        )
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = strings.weekOfYearLabel(isoWeek.weekOfYear, isoWeek.isoYear),
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryNavy
                                )
                                AdminStatusBadge(
                                    text = when (round.status) {
                                        VeggieRoundStatus.OPEN -> strings.roundStatusOpen
                                        VeggieRoundStatus.CLOSED -> strings.roundStatusClosed
                                        VeggieRoundStatus.SETTLED -> strings.roundStatusSettled
                                    },
                                    type = when (round.status) {
                                        VeggieRoundStatus.OPEN -> AdminBadgeType.SUCCESS
                                        VeggieRoundStatus.CLOSED -> AdminBadgeType.WARNING
                                        VeggieRoundStatus.SETTLED -> AdminBadgeType.NAVY
                                    }
                                )
                                if (hasUnsavedChanges) {
                                    AdminStatusBadge(
                                        text = strings.unsavedChangesBadge,
                                        type = AdminBadgeType.WARNING
                                    )
                                }
                            }
                            Text(
                                text = Formatting.formatTimestamp(round.openedAt),
                                fontSize = 12.sp,
                                color = TextSecondaryMuted
                            )
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showAddEntryDialog = true },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = PrimaryNavy)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(strings.addUserToList, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PrimaryNavy)
                        }

                        when (round.status) {
                            VeggieRoundStatus.OPEN -> {
                                if (onResetChanges != null) {
                                    OutlinedButton(
                                        onClick = { showConfirmResetChanges = true },
                                        enabled = hasUnsavedChanges && !isSaving,
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = if (hasUnsavedChanges) ColorDangerCrimson else TextSecondaryMuted,
                                            disabledContentColor = TextSecondaryMuted.copy(alpha = 0.4f)
                                        ),
                                        border = BorderStroke(
                                            1.dp,
                                            if (hasUnsavedChanges) ColorDangerCrimson.copy(alpha = 0.5f) else DividerBorder.copy(alpha = 0.6f)
                                        )
                                    ) {
                                        Icon(
                                            Icons.Default.Restore,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = if (hasUnsavedChanges) ColorDangerCrimson else TextSecondaryMuted.copy(alpha = 0.4f)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            strings.resetChangesBtn,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (hasUnsavedChanges) ColorDangerCrimson else TextSecondaryMuted.copy(alpha = 0.4f)
                                        )
                                    }
                                }

                                OutlinedButton(
                                    onClick = onSaveDraft,
                                    enabled = hasUnsavedChanges && !isSaving,
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = if (hasUnsavedChanges) AccentNavy else TextSecondaryMuted,
                                        disabledContentColor = TextSecondaryMuted.copy(alpha = 0.4f)
                                    ),
                                    border = BorderStroke(
                                        1.dp,
                                        if (hasUnsavedChanges) AccentNavy.copy(alpha = 0.5f) else DividerBorder.copy(alpha = 0.6f)
                                    )
                                ) {
                                    Icon(
                                        Icons.Default.Save,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = if (hasUnsavedChanges) AccentNavy else TextSecondaryMuted.copy(alpha = 0.4f)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        strings.saveChangesBtn,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (hasUnsavedChanges) AccentNavy else TextSecondaryMuted.copy(alpha = 0.4f)
                                    )
                                }

                                if (entries.isEmpty()) {
                                    if (onCompleteEmptyRound != null) {
                                        Button(
                                            onClick = { showConfirmCompleteEmpty = true },
                                            enabled = !isSaving,
                                            colors = ButtonDefaults.buttonColors(containerColor = ColorSuccessEmerald),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp), tint = SurfaceWhite)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(strings.completeRoundBtn, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SurfaceWhite)
                                        }
                                    }

                                    if (onDeleteRound != null) {
                                        OutlinedButton(
                                            onClick = { showConfirmDeleteRound = true },
                                            enabled = !isSaving,
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorDangerCrimson),
                                            border = BorderStroke(1.dp, ColorDangerCrimson.copy(alpha = 0.5f)),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp), tint = ColorDangerCrimson)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(strings.deleteRoundBtn, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ColorDangerCrimson)
                                        }
                                    }
                                }

                                Button(
                                    onClick = { showConfirmCloseRound = true },
                                    enabled = !isSaving,
                                    colors = ButtonDefaults.buttonColors(containerColor = ColorWarningAmber),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp), tint = SurfaceWhite)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(strings.closeRoundBtn, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SurfaceWhite)
                                }
                            }

                            VeggieRoundStatus.CLOSED -> {
                                if (onResetChanges != null) {
                                    OutlinedButton(
                                        onClick = { showConfirmResetChanges = true },
                                        enabled = hasUnsavedChanges && !isSaving,
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = if (hasUnsavedChanges) ColorDangerCrimson else TextSecondaryMuted,
                                            disabledContentColor = TextSecondaryMuted.copy(alpha = 0.4f)
                                        ),
                                        border = BorderStroke(
                                            1.dp,
                                            if (hasUnsavedChanges) ColorDangerCrimson.copy(alpha = 0.5f) else DividerBorder.copy(alpha = 0.6f)
                                        )
                                    ) {
                                        Icon(
                                            Icons.Default.Restore,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = if (hasUnsavedChanges) ColorDangerCrimson else TextSecondaryMuted.copy(alpha = 0.4f)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            strings.resetChangesBtn,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (hasUnsavedChanges) ColorDangerCrimson else TextSecondaryMuted.copy(alpha = 0.4f)
                                        )
                                    }
                                }

                                OutlinedButton(
                                    onClick = onSaveDraft,
                                    enabled = hasUnsavedChanges && !isSaving,
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = if (hasUnsavedChanges) AccentNavy else TextSecondaryMuted,
                                        disabledContentColor = TextSecondaryMuted.copy(alpha = 0.4f)
                                    ),
                                    border = BorderStroke(
                                        1.dp,
                                        if (hasUnsavedChanges) AccentNavy.copy(alpha = 0.5f) else DividerBorder.copy(alpha = 0.6f)
                                    )
                                ) {
                                    Icon(
                                        Icons.Default.Save,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = if (hasUnsavedChanges) AccentNavy else TextSecondaryMuted.copy(alpha = 0.4f)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        strings.saveDraftBtn,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (hasUnsavedChanges) AccentNavy else TextSecondaryMuted.copy(alpha = 0.4f)
                                    )
                                }

                                OutlinedButton(
                                    onClick = onReopenRound,
                                    enabled = !isSaving,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(16.dp), tint = PrimaryNavy)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(strings.reopenRoundBtn, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PrimaryNavy)
                                }

                                if (entries.isEmpty()) {
                                    if (onCompleteEmptyRound != null) {
                                        Button(
                                            onClick = { showConfirmCompleteEmpty = true },
                                            enabled = !isSaving,
                                            colors = ButtonDefaults.buttonColors(containerColor = ColorSuccessEmerald),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp), tint = SurfaceWhite)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(strings.completeRoundBtn, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SurfaceWhite)
                                        }
                                    }

                                    if (onDeleteRound != null) {
                                        OutlinedButton(
                                            onClick = { showConfirmDeleteRound = true },
                                            enabled = !isSaving,
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorDangerCrimson),
                                            border = BorderStroke(1.dp, ColorDangerCrimson.copy(alpha = 0.5f)),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp), tint = ColorDangerCrimson)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(strings.deleteRoundBtn, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ColorDangerCrimson)
                                        }
                                    }
                                } else {
                                    Button(
                                        onClick = { showConfirmSettle = true },
                                        enabled = !isSaving,
                                        colors = ButtonDefaults.buttonColors(containerColor = ColorSuccessEmerald),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp), tint = SurfaceWhite)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(strings.settleAndChargeBtn, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SurfaceWhite)
                                    }
                                }
                            }

                            VeggieRoundStatus.SETTLED -> {
                                if (onResetChanges != null) {
                                    OutlinedButton(
                                        onClick = { showConfirmResetChanges = true },
                                        enabled = hasUnsavedChanges && !isSaving,
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = if (hasUnsavedChanges) ColorDangerCrimson else TextSecondaryMuted,
                                            disabledContentColor = TextSecondaryMuted.copy(alpha = 0.4f)
                                        ),
                                        border = BorderStroke(
                                            1.dp,
                                            if (hasUnsavedChanges) ColorDangerCrimson.copy(alpha = 0.5f) else DividerBorder.copy(alpha = 0.6f)
                                        )
                                    ) {
                                        Icon(
                                            Icons.Default.Restore,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = if (hasUnsavedChanges) ColorDangerCrimson else TextSecondaryMuted.copy(alpha = 0.4f)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            strings.resetChangesBtn,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (hasUnsavedChanges) ColorDangerCrimson else TextSecondaryMuted.copy(alpha = 0.4f)
                                        )
                                    }
                                }

                                Button(
                                    onClick = { showConfirmSettle = true },
                                    enabled = !isSaving && entries.isNotEmpty(),
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentNavy),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp), tint = SurfaceWhite)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(strings.updateSettledRoundBtn, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SurfaceWhite)
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = DividerBorder)

                // Fees & Transporter Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    AdminLabeledField(
                        label = strings.buyerOperationFeeLabel,
                        value = draftBuyerFeeInput,
                        onValueChange = onUpdateBuyerFee,
                        placeholder = "0",
                        modifier = Modifier.weight(1f)
                    )

                    AdminLabeledField(
                        label = strings.transportFeeLabel,
                        value = draftTransportFeeInput,
                        onValueChange = onUpdateTransportFee,
                        placeholder = "0",
                        modifier = Modifier.weight(1f)
                    )

                    // Transporter Selector
                    Column(modifier = Modifier.weight(1.4f)) {
                        Text(
                            text = strings.transporterLabel,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PrimaryNavy
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box {
                            val selectedTransporterName = users.find { it.id == draftTransporterUserId }?.name
                                ?: strings.transporterExternal
                            OutlinedButton(
                                onClick = { transporterDropdownExpanded = true },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().height(56.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (draftTransporterUserId == null) Icons.Default.LocalShipping else Icons.Default.Person,
                                            contentDescription = null,
                                            tint = PrimaryNavy,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = selectedTransporterName,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = PrimaryNavy,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = PrimaryNavy)
                                }
                            }

                            DropdownMenu(
                                expanded = transporterDropdownExpanded,
                                onDismissRequest = { transporterDropdownExpanded = false },
                                modifier = Modifier.heightIn(max = 320.dp)
                            ) {
                                DropdownMenuItem(
                                    text = { Text(strings.transporterExternal, fontWeight = FontWeight.Bold) },
                                    onClick = {
                                        onUpdateTransporterUser(null)
                                        transporterDropdownExpanded = false
                                    }
                                )
                                HorizontalDivider(color = DividerBorder)
                                users.forEach { user ->
                                    DropdownMenuItem(
                                        text = { Text(user.name) },
                                        onClick = {
                                            onUpdateTransporterUser(user.id)
                                            transporterDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. 2D Settlement Matrix Table
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = SurfaceWhite,
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DividerBorder)),
            shadowElevation = 1.dp,
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
            if (roundProductRows.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(strings.noMatchingProducts, color = TextSecondaryMuted, fontSize = 14.sp)
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Matrix Table Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                            .background(PrimaryNavy)
                            .padding(vertical = 4.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Fixed Product Name Header
                        Box(
                            modifier = Modifier.width(COL_PRODUCT_WIDTH).height(88.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = strings.veggieProductNameLabel,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = SurfaceWhite
                            )
                        }

                        // 2. Middle Scrollable Customer Headers (90° Rotated Names)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(horizontalScrollState)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                roundCustomers.forEach { (_, customerName) ->
                                    Box(
                                        modifier = Modifier
                                            .width(COL_CUSTOMER_WIDTH)
                                            .height(88.dp)
                                            .padding(horizontal = 2.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = Formatting.formatShortName(customerName, 14),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = SurfaceWhite,
                                            maxLines = 1,
                                            softWrap = false,
                                            modifier = Modifier
                                                .wrapContentSize(unbounded = true)
                                                .graphicsLayer { rotationZ = -90f }
                                        )
                                    }
                                }
                            }
                        }

                        // 3. Separator Button between the two lists
                        Box(
                            modifier = Modifier
                                .width(COL_DIVIDER_WIDTH)
                                .height(88.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                onClick = { showBuyerOverview = !showBuyerOverview },
                                shape = RoundedCornerShape(6.dp),
                                color = SurfaceWhite.copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, SurfaceWhite.copy(alpha = 0.45f)),
                                modifier = Modifier.size(width = 22.dp, height = 36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (showBuyerOverview) Icons.Default.ChevronRight else Icons.Default.ChevronLeft,
                                        contentDescription = if (showBuyerOverview) strings.hideBuyerOverviewBtn else strings.showBuyerOverviewBtn,
                                        tint = SurfaceWhite,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        // 4. Animated Buyer Overview Headers
                        if (animatedOverviewWidth > 0.dp) {
                            Box(
                                modifier = Modifier
                                    .width(animatedOverviewWidth)
                                    .clipToBounds()
                            ) {
                                Row(
                                    modifier = Modifier.width(OVERVIEW_TOTAL_WIDTH),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    MatrixHeaderCell(strings.colActualQty, COL_TOTAL_QTY_WIDTH, TextAlign.Center)
                                    MatrixHeaderCell(strings.colMarketUnitPrice, COL_MARKET_PRICE_WIDTH, TextAlign.Center)
                                    MatrixHeaderCell(strings.colTotalSpent, COL_TOTAL_SPENT_WIDTH, TextAlign.Center)
                                    MatrixHeaderCell(strings.colEffectivePrice, COL_EFFECTIVE_PRICE_WIDTH, TextAlign.End)
                                }
                            }
                        }
                    }

                    // Scrollable Product Rows
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(verticalScrollState)
                        ) {
                            roundProductRows.forEach { rowInfo ->
                                key(rowInfo.productId) {
                            val productSettlement = previewProductSettlementMap[rowInfo.productId]
                            val totalDeliveredScaled = rowInfo.entriesByUserId.values.sumOf { entry ->
                                previewEntryMap[entry.id]?.deliveredQuantityScaled ?: entry.deliveredQuantityScaled
                            }
                            val formattedTotalDelivered = VeggieQuantityScaling.formatScaledQuantity(
                                totalDeliveredScaled,
                                rowInfo.allowDecimals
                            )
                            val effectiveUnitPrice = productSettlement?.marketUnitPriceCents?.let {
                                val spent = productSettlement.totalSpentCents ?: 0L
                                VeggieSettlementCalculator.calculateEffectiveUnitPrice(spent, totalDeliveredScaled)
                            } ?: VeggieSettlementCalculator.calculateEffectiveUnitPrice(
                                productSettlement?.totalSpentCents ?: 0L,
                                totalDeliveredScaled
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(0.5.dp, DividerBorder)
                                    .background(SurfaceWhite)
                                    .padding(vertical = 4.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 1. Product Name & Unit (Fixed Left)
                                Column(
                                    modifier = Modifier.width(COL_PRODUCT_WIDTH).padding(end = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = rowInfo.productName,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryNavy,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    AdminStatusBadge(
                                        text = rowInfo.unitName,
                                        type = if (rowInfo.allowDecimals) AdminBadgeType.SUCCESS else AdminBadgeType.NAVY
                                    )
                                }

                                // 2. Customer Cells (Middle Horizontally Scrolled)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .horizontalScroll(horizontalScrollState)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        roundCustomers.forEach { (userId, _) ->
                                            val entry = rowInfo.entriesByUserId[userId]
                                            Box(
                                                modifier = Modifier
                                                    .width(COL_CUSTOMER_WIDTH)
                                                    .padding(horizontal = 3.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (entry == null) {
                                                    Text("—", color = TextSecondaryMuted, fontSize = 13.sp)
                                                } else {
                                                    val orderedFormatted = VeggieQuantityScaling.formatScaledQuantity(
                                                        entry.orderedQuantityScaled,
                                                        entry.allowDecimalsSnapshot
                                                    )
                                                    val draftQtyStr = draftActualQuantities[entry.id] ?: orderedFormatted
                                                    val previewEntry = previewEntryMap[entry.id]
                                                    val shareCents = previewEntry?.finalItemTotalCents ?: 0L

                                                    val entryIdx = orderedMatrixEntryIds.indexOf(entry.id)
                                                    val prevReq = if (entryIdx > 0) matrixFocusRequesters[orderedMatrixEntryIds[entryIdx - 1]] else null
                                                    val nextReq = if (entryIdx in 0 until orderedMatrixEntryIds.size - 1) matrixFocusRequesters[orderedMatrixEntryIds[entryIdx + 1]] else null

                                                    Column(
                                                        horizontalAlignment = Alignment.CenterHorizontally,
                                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                                    ) {
                                                        Text(
                                                            text = "$orderedFormatted ${rowInfo.unitName}",
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = TextSecondaryMuted,
                                                            maxLines = 1
                                                        )
                                                        MatrixNumberInputField(
                                                            value = draftQtyStr,
                                                            onValueChange = { onUpdateActualQuantity(entry.id, it) },
                                                            isBold = true,
                                                            fontSize = 12.5.sp,
                                                            focusRequester = matrixFocusRequesters[entry.id],
                                                            prevFocusRequester = prevReq,
                                                            nextFocusRequester = nextReq,
                                                            modifier = Modifier.fillMaxWidth().height(36.dp)
                                                        )
                                                        Text(
                                                            text = Formatting.formatBrl(shareCents),
                                                            fontSize = 10.5.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (shareCents > 0L) AccentNavy else TextSecondaryMuted,
                                                            maxLines = 1
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // 3. Vertical Divider Line between lists
                                Box(
                                    modifier = Modifier
                                        .width(COL_DIVIDER_WIDTH)
                                        .height(52.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (showBuyerOverview || animatedOverviewWidth > 0.dp) {
                                        Box(
                                            modifier = Modifier
                                                .width(1.5.dp)
                                                .fillMaxHeight()
                                                .background(PrimaryNavy.copy(alpha = 0.15f))
                                        )
                                    }
                                }

                                // 4. Animated Overview Cells
                                if (animatedOverviewWidth > 0.dp) {
                                    Box(
                                        modifier = Modifier
                                            .width(animatedOverviewWidth)
                                            .clipToBounds()
                                    ) {
                                        Row(
                                            modifier = Modifier.width(OVERVIEW_TOTAL_WIDTH),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Total Delivered Qty
                                            Box(
                                                modifier = Modifier.width(COL_TOTAL_QTY_WIDTH).padding(horizontal = 4.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "$formattedTotalDelivered ${rowInfo.unitName}",
                                                    fontSize = 12.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = PrimaryNavy,
                                                    maxLines = 1
                                                )
                                            }

                                            // Market Unit Price Input
                                            Box(
                                                modifier = Modifier.width(COL_MARKET_PRICE_WIDTH).padding(horizontal = 4.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                val pIdx = priceProductIds.indexOf(rowInfo.productId)
                                                val prevPriceReq = if (pIdx > 0) priceFocusRequesters[priceProductIds[pIdx - 1]] else null
                                                val nextPriceReq = if (pIdx in 0 until priceProductIds.size - 1) {
                                                    priceFocusRequesters[priceProductIds[pIdx + 1]]
                                                } else {
                                                    spentFocusRequesters[priceProductIds.firstOrNull()]
                                                }

                                                MatrixNumberInputField(
                                                    value = draftMarketPrices[rowInfo.productId] ?: "",
                                                    onValueChange = { onUpdateMarketUnitPrice(rowInfo.productId, it) },
                                                    placeholder = "0,00",
                                                    fontSize = 12.5.sp,
                                                    focusRequester = priceFocusRequesters[rowInfo.productId],
                                                    prevFocusRequester = prevPriceReq,
                                                    nextFocusRequester = nextPriceReq,
                                                    modifier = Modifier.fillMaxWidth().height(36.dp)
                                                )
                                            }

                                            // Total Spent Input (Authoritative)
                                            Box(
                                                modifier = Modifier.width(COL_TOTAL_SPENT_WIDTH).padding(horizontal = 4.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                val pIdx = priceProductIds.indexOf(rowInfo.productId)
                                                val prevSpentReq = if (pIdx > 0) {
                                                    spentFocusRequesters[priceProductIds[pIdx - 1]]
                                                } else {
                                                    priceFocusRequesters[priceProductIds.lastOrNull()]
                                                }
                                                val nextSpentReq = if (pIdx in 0 until priceProductIds.size - 1) {
                                                    spentFocusRequesters[priceProductIds[pIdx + 1]]
                                                } else {
                                                    null
                                                }

                                                MatrixNumberInputField(
                                                    value = draftTotalSpent[rowInfo.productId] ?: "",
                                                    onValueChange = { onUpdateTotalSpent(rowInfo.productId, it) },
                                                    placeholder = "0,00",
                                                    isBold = true,
                                                    fontSize = 12.5.sp,
                                                    focusRequester = spentFocusRequesters[rowInfo.productId],
                                                    prevFocusRequester = prevSpentReq,
                                                    nextFocusRequester = nextSpentReq,
                                                    modifier = Modifier.fillMaxWidth().height(36.dp)
                                                )
                                            }

                                            // Effective Unit Price (Read-Only)
                                            Box(
                                                modifier = Modifier.width(COL_EFFECTIVE_PRICE_WIDTH).padding(horizontal = 4.dp),
                                                contentAlignment = Alignment.CenterEnd
                                            ) {
                                                Text(
                                                    text = if (effectiveUnitPrice > 0L) {
                                                        "${Formatting.formatBrl(effectiveUnitPrice)}/${rowInfo.unitName}"
                                                    } else "—",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (effectiveUnitPrice > 0L) ColorSuccessEmerald else TextSecondaryMuted,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                AppVerticalScrollbar(
                    scrollState = verticalScrollState,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .padding(vertical = 4.dp)
                )
            }

                    HorizontalDivider(thickness = 1.5.dp, color = PrimaryNavy.copy(alpha = 0.25f))

                    // Footer Row 1: Combined Fee Share per Customer
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SurfaceContainerLight)
                            .padding(vertical = 6.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = strings.veggieCombinedFeeLabel,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PrimaryNavy,
                            modifier = Modifier.width(COL_PRODUCT_WIDTH)
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(horizontalScrollState)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                roundCustomers.forEach { (userId, _) ->
                                    val feeShare = previewCustomerSummaryMap[userId]?.combinedFeeShareCents ?: 0L
                                    Text(
                                        text = Formatting.formatBrl(feeShare),
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextSecondaryMuted,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.width(COL_CUSTOMER_WIDTH)
                                    )
                                }
                            }
                        }
                        // 3. Vertical Divider Line
                        Box(
                            modifier = Modifier
                                .width(COL_DIVIDER_WIDTH)
                                .height(28.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (showBuyerOverview || animatedOverviewWidth > 0.dp) {
                                Box(
                                    modifier = Modifier
                                        .width(1.5.dp)
                                        .fillMaxHeight()
                                        .background(PrimaryNavy.copy(alpha = 0.15f))
                                )
                            }
                        }

                        // 4. Animated Overview Fee Share
                        if (animatedOverviewWidth > 0.dp) {
                            Box(
                                modifier = Modifier
                                    .width(animatedOverviewWidth)
                                    .clipToBounds()
                            ) {
                                Row(
                                    modifier = Modifier.width(OVERVIEW_TOTAL_WIDTH),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Spacer(modifier = Modifier.width(COL_TOTAL_QTY_WIDTH + COL_MARKET_PRICE_WIDTH))
                                    Text(
                                        text = Formatting.formatBrl(livePreview?.combinedFeeCents ?: 0L),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryNavy,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.width(COL_TOTAL_SPENT_WIDTH)
                                    )
                                    Spacer(modifier = Modifier.width(COL_EFFECTIVE_PRICE_WIDTH))
                                }
                            }
                        }
                    }

                    // Footer Row 2: Grand Total per Customer
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SurfaceContainerHighLight)
                            .padding(vertical = 8.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = strings.veggieFinalTotalLabel,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryNavy,
                            modifier = Modifier.width(COL_PRODUCT_WIDTH)
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(horizontalScrollState)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                roundCustomers.forEach { (userId, _) ->
                                    val grandTotal = previewCustomerSummaryMap[userId]?.grandTotalCents ?: 0L
                                    Text(
                                        text = Formatting.formatBrl(grandTotal),
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AccentNavy,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.width(COL_CUSTOMER_WIDTH)
                                    )
                                }
                            }
                        }
                        // 3. Vertical Divider Line
                        Box(
                            modifier = Modifier
                                .width(COL_DIVIDER_WIDTH)
                                .height(28.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (showBuyerOverview || animatedOverviewWidth > 0.dp) {
                                Box(
                                    modifier = Modifier
                                        .width(1.5.dp)
                                        .fillMaxHeight()
                                        .background(PrimaryNavy.copy(alpha = 0.15f))
                                )
                            }
                        }

                        // 4. Animated Overview Grand Total
                        if (animatedOverviewWidth > 0.dp) {
                            Box(
                                modifier = Modifier
                                    .width(animatedOverviewWidth)
                                    .clipToBounds()
                            ) {
                                Row(
                                    modifier = Modifier.width(OVERVIEW_TOTAL_WIDTH),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Spacer(modifier = Modifier.width(COL_TOTAL_QTY_WIDTH + COL_MARKET_PRICE_WIDTH))
                                    val totalAllCharged = (livePreview?.totalMarketProductsSpentCents ?: 0L) + (livePreview?.combinedFeeCents ?: 0L)
                                    Text(
                                        text = Formatting.formatBrl(totalAllCharged),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ColorSuccessEmerald,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.width(COL_TOTAL_SPENT_WIDTH)
                                    )
                                    Spacer(modifier = Modifier.width(COL_EFFECTIVE_PRICE_WIDTH))
                                }
                            }
                        }
                    }

                    // Horizontal Scrollbar at bottom of matrix table (visible only when overflowing)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Spacer(modifier = Modifier.width(COL_PRODUCT_WIDTH))
                        Box(modifier = Modifier.weight(1f)) {
                            AppHorizontalScrollbar(
                                scrollState = horizontalScrollState,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Spacer(modifier = Modifier.width(COL_DIVIDER_WIDTH + animatedOverviewWidth))
                    }
                }
            }
        }
    }

    if (showConfirmCloseRound) {
        ConfirmationDialog(
            title = strings.confirmCloseRoundTitle,
            message = strings.confirmCloseRoundMsg,
            confirmText = strings.closeRoundBtn,
            cancelText = strings.cancel,
            confirmButtonColor = ColorWarningAmber,
            onConfirm = {
                showConfirmCloseRound = false
                onCloseRound()
            },
            onDismiss = { showConfirmCloseRound = false }
        )
    }

    if (showConfirmResetChanges) {
        ConfirmationDialog(
            title = strings.confirmResetChangesTitle,
            message = strings.confirmResetChangesMsg,
            confirmText = strings.resetChangesBtn,
            cancelText = strings.cancel,
            confirmButtonColor = ColorDangerCrimson,
            onConfirm = {
                showConfirmResetChanges = false
                onResetChanges?.invoke()
            },
            onDismiss = { showConfirmResetChanges = false }
        )
    }

    if (showConfirmSettle) {
        val totalAllCharged = (livePreview?.totalMarketProductsSpentCents ?: 0L) + (livePreview?.combinedFeeCents ?: 0L)
        val participantCount = livePreview?.customerSummaries?.size ?: 0
        ConfirmationDialog(
            title = if (round.status == VeggieRoundStatus.SETTLED) strings.updateSettledRoundBtn else strings.settleAndChargeBtn,
            message = strings.confirmExecuteChargesMsg(participantCount, Formatting.formatBrl(totalAllCharged)),
            confirmText = strings.confirm,
            cancelText = strings.cancel,
            confirmButtonColor = ColorSuccessEmerald,
            onConfirm = {
                showConfirmSettle = false
                onExecuteSettlement()
            },
            onDismiss = { showConfirmSettle = false }
        )
    }

    if (showConfirmCompleteEmpty) {
        ConfirmationDialog(
            title = strings.confirmCompleteEmptyRoundTitle,
            message = strings.confirmCompleteEmptyRoundMsg,
            confirmText = strings.confirm,
            cancelText = strings.cancel,
            confirmButtonColor = ColorSuccessEmerald,
            onConfirm = {
                showConfirmCompleteEmpty = false
                onCompleteEmptyRound?.invoke()
            },
            onDismiss = { showConfirmCompleteEmpty = false }
        )
    }

    if (showConfirmDeleteRound) {
        ConfirmationDialog(
            title = strings.confirmDeleteRoundTitle,
            message = strings.confirmDeleteRoundMsg,
            confirmText = strings.delete,
            cancelText = strings.cancel,
            confirmButtonColor = ColorDangerCrimson,
            onConfirm = {
                showConfirmDeleteRound = false
                onDeleteRound?.invoke()
            },
            onDismiss = { showConfirmDeleteRound = false }
        )
    }

    if (showAddEntryDialog) {
        AddAdminVeggieEntryDialog(
            users = users,
            products = products.filter { !it.isDeleted && it.isActive },
            unitTypes = unitTypes,
            existingEntries = entries,
            onConfirm = { user, product, qtyInput, mode ->
                onAddAdminEntry(user, product, qtyInput, mode)
                showAddEntryDialog = false
            },
            onDismiss = { showAddEntryDialog = false }
        )
    }
}

private data class VeggieMatrixProductRowInfo(
    val productId: String,
    val productName: String,
    val unitName: String,
    val allowDecimals: Boolean,
    val entriesByUserId: Map<String, VeggieOrderEntry>
)

@Composable
private fun MatrixHeaderCell(
    text: String,
    width: androidx.compose.ui.unit.Dp,
    textAlign: TextAlign
) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = SurfaceWhite,
        textAlign = textAlign,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.width(width).padding(horizontal = 4.dp)
    )
}

@Composable
private fun AddAdminVeggieEntryDialog(
    users: List<User>,
    products: List<VeggieProduct>,
    unitTypes: List<VeggieUnitType>,
    existingEntries: List<VeggieOrderEntry>,
    onConfirm: (User, VeggieProduct, String, AddOrderEntryMode) -> Unit,
    onDismiss: () -> Unit
) {
    val strings = I18n.current
    var selectedUser by remember { mutableStateOf(users.firstOrNull()) }
    var selectedProduct by remember { mutableStateOf(products.firstOrNull()) }
    var userMenuExpanded by remember { mutableStateOf(false) }
    var productMenuExpanded by remember { mutableStateOf(false) }

    val unitTypesById = remember(unitTypes) { unitTypes.associateBy { it.id } }
    val unitType = selectedProduct?.let { unitTypesById[it.unitTypeId] }
    val allowDecimals = unitType?.allowDecimals ?: false
    val unitName = unitType?.name ?: "Unit"

    val existingEntry = remember(selectedUser?.id, selectedProduct?.id, existingEntries) {
        val uid = selectedUser?.id ?: return@remember null
        val pid = selectedProduct?.id ?: return@remember null
        existingEntries.find { it.userId == uid && it.veggieProductId == pid }
    }

    var mode by remember(selectedUser?.id, selectedProduct?.id) {
        mutableStateOf(AddOrderEntryMode.OVERWRITE)
    }

    val existingScaled = existingEntry?.actualQuantityScaled ?: existingEntry?.orderedQuantityScaled ?: 0L

    val initialText = remember(selectedUser?.id, selectedProduct?.id) {
        if (existingEntry != null) {
            VeggieQuantityScaling.formatScaledQuantity(existingScaled, allowDecimals)
        } else {
            "1"
        }
    }

    var textFieldValue by remember(selectedUser?.id, selectedProduct?.id) {
        mutableStateOf(TextFieldValue(initialText, selection = TextRange(0, initialText.length)))
    }
    var hadFocus by remember { mutableStateOf(false) }
    var justFocused by remember { mutableStateOf(false) }

    val parsedQtyScaled = remember(textFieldValue.text, allowDecimals) {
        VeggieQuantityScaling.parseQuantityScaled(textFieldValue.text, allowDecimals)
    }

    val canAdd = selectedUser != null && selectedProduct != null && parsedQtyScaled != null && parsedQtyScaled > 0L

    val newTotalScaled = when (mode) {
        AddOrderEntryMode.OVERWRITE -> parsedQtyScaled ?: 0L
        AddOrderEntryMode.ADD -> existingScaled + (parsedQtyScaled ?: 0L)
    }
    val newTotalFormatted = remember(newTotalScaled, allowDecimals) {
        VeggieQuantityScaling.formatScaledQuantity(newTotalScaled, allowDecimals)
    }
    val existingFormatted = remember(existingScaled, allowDecimals) {
        VeggieQuantityScaling.formatScaledQuantity(existingScaled, allowDecimals)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = true, dismissOnBackPress = true)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = SurfaceWhite,
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .widthIn(max = 460.dp)
                .wrapContentHeight()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = strings.addUserToList,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryNavy
                )

                HorizontalDivider(color = DividerBorder)

                // User Selector
                Column {
                    Text(strings.tabUsers, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PrimaryNavy)
                    Spacer(modifier = Modifier.height(4.dp))
                    Box {
                        OutlinedButton(
                            onClick = { userMenuExpanded = true },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(selectedUser?.name ?: "—", color = PrimaryNavy, fontWeight = FontWeight.SemiBold)
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = PrimaryNavy)
                            }
                        }
                        DropdownMenu(
                            expanded = userMenuExpanded,
                            onDismissRequest = { userMenuExpanded = false },
                            modifier = Modifier.heightIn(max = 280.dp)
                        ) {
                            users.forEach { u ->
                                DropdownMenuItem(
                                    text = { Text(u.name) },
                                    onClick = {
                                        selectedUser = u
                                        userMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Product Selector
                Column {
                    Text(strings.veggieProductNameLabel, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PrimaryNavy)
                    Spacer(modifier = Modifier.height(4.dp))
                    Box {
                        OutlinedButton(
                            onClick = { productMenuExpanded = true },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(selectedProduct?.name ?: "—", color = PrimaryNavy, fontWeight = FontWeight.SemiBold)
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = PrimaryNavy)
                            }
                        }
                        DropdownMenu(
                            expanded = productMenuExpanded,
                            onDismissRequest = { productMenuExpanded = false },
                            modifier = Modifier.heightIn(max = 280.dp)
                        ) {
                            products.forEach { p ->
                                DropdownMenuItem(
                                    text = { Text(p.name) },
                                    onClick = {
                                        selectedProduct = p
                                        productMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Quantity Field with Unit Badge
                Column {
                    Text(strings.quantity, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PrimaryNavy)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = textFieldValue,
                            onValueChange = { newVal ->
                                if (justFocused && newVal.text == textFieldValue.text) {
                                    textFieldValue = newVal.copy(selection = TextRange(0, newVal.text.length))
                                    justFocused = false
                                } else {
                                    justFocused = false
                                    val filteredText = filterVeggieAmountInput(newVal.text, allowDecimals)
                                    if (filteredText == newVal.text) {
                                        textFieldValue = newVal
                                    } else {
                                        textFieldValue = TextFieldValue(
                                            text = filteredText,
                                            selection = TextRange(filteredText.length)
                                        )
                                    }
                                }
                            },
                            placeholder = { Text("1", fontSize = 15.sp, color = TextSecondaryMuted) },
                            textStyle = LocalTextStyle.current.copy(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = PrimaryNavy
                            ),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = if (allowDecimals) KeyboardType.Decimal else KeyboardType.Number
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .onFocusChanged { focusState ->
                                    if (focusState.isFocused) {
                                        if (!hadFocus) {
                                            hadFocus = true
                                            justFocused = true
                                            textFieldValue = textFieldValue.copy(
                                                selection = TextRange(0, textFieldValue.text.length)
                                            )
                                        }
                                    } else {
                                        hadFocus = false
                                        justFocused = false
                                    }
                                }
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = AccentNavy.copy(alpha = 0.08f),
                            border = BorderStroke(1.dp, AccentNavy.copy(alpha = 0.25f)),
                            modifier = Modifier
                                .height(56.dp)
                                .widthIn(min = 64.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            ) {
                                Text(
                                    text = unitName,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryNavy,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                // Existing Order Detection & Mode Choice
                if (existingEntry != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = SurfaceContainerLight,
                        border = BorderStroke(1.dp, DividerBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = strings.addOrderCurrentQuantityLabel(existingFormatted, unitName),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextSecondaryMuted
                                )
                                Text(
                                    text = strings.addOrderNewTotalLabel(newTotalFormatted, unitName),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (canAdd) ColorSuccessEmerald else TextSecondaryMuted
                                )
                            }

                            HorizontalDivider(color = DividerBorder.copy(alpha = 0.5f))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { mode = AddOrderEntryMode.OVERWRITE }
                                    .padding(vertical = 2.dp)
                            ) {
                                RadioButton(
                                    selected = (mode == AddOrderEntryMode.OVERWRITE),
                                    onClick = { mode = AddOrderEntryMode.OVERWRITE },
                                    colors = RadioButtonDefaults.colors(selectedColor = AccentNavy)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = strings.addOrderModeOverwrite,
                                    fontSize = 13.sp,
                                    fontWeight = if (mode == AddOrderEntryMode.OVERWRITE) FontWeight.Bold else FontWeight.Normal,
                                    color = PrimaryNavy
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { mode = AddOrderEntryMode.ADD }
                                    .padding(vertical = 2.dp)
                            ) {
                                RadioButton(
                                    selected = (mode == AddOrderEntryMode.ADD),
                                    onClick = { mode = AddOrderEntryMode.ADD },
                                    colors = RadioButtonDefaults.colors(selectedColor = AccentNavy)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = strings.addOrderModeAddToExisting,
                                    fontSize = 13.sp,
                                    fontWeight = if (mode == AddOrderEntryMode.ADD) FontWeight.Bold else FontWeight.Normal,
                                    color = PrimaryNavy
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = DividerBorder)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(strings.cancel)
                    }
                    Button(
                        onClick = {
                            if (canAdd) {
                                onConfirm(selectedUser!!, selectedProduct!!, textFieldValue.text, mode)
                            }
                        },
                        enabled = canAdd,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentNavy),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(strings.confirm, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun MatrixNumberInputField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    isBold: Boolean = false,
    fontSize: TextUnit = 13.sp,
    textAlign: TextAlign = TextAlign.Center,
    focusRequester: FocusRequester? = null,
    nextFocusRequester: FocusRequester? = null,
    prevFocusRequester: FocusRequester? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    var hadFocus by remember { mutableStateOf(false) }
    var focusGainedTime by remember { mutableStateOf(0L) }
    var textOnFocusGain by remember { mutableStateOf("") }

    var textFieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = value,
                selection = TextRange(value.length)
            )
        )
    }

    LaunchedEffect(value) {
        if (value != textFieldValue.text) {
            textFieldValue = textFieldValue.copy(
                text = value,
                selection = TextRange(value.length)
            )
        }
    }

    LaunchedEffect(hadFocus) {
        if (hadFocus) {
            delay(60)
            if (hadFocus && textFieldValue.text == textOnFocusGain && textFieldValue.selection != TextRange(0, textFieldValue.text.length)) {
                textFieldValue = textFieldValue.copy(
                    selection = TextRange(0, textFieldValue.text.length)
                )
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = SurfaceWhite,
        border = CardDefaults.outlinedCardBorder().copy(
            brush = SolidColor(if (isFocused) AccentNavy else DividerBorder)
        ),
        shadowElevation = if (isFocused) 1.dp else 0.dp,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            if (value.isEmpty() && placeholder != null) {
                Text(
                    text = placeholder,
                    fontSize = fontSize,
                    fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                    color = TextSecondaryMuted,
                    textAlign = textAlign,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            BasicTextField(
                value = textFieldValue,
                onValueChange = { newValue ->
                    val isInitialFocusEvent = hadFocus &&
                            (currentTimeMillis() - focusGainedTime < 300L) &&
                            (newValue.text == textFieldValue.text)
                    if (isInitialFocusEvent) {
                        textFieldValue = newValue.copy(
                            selection = TextRange(0, newValue.text.length)
                        )
                    } else {
                        textFieldValue = newValue
                        if (newValue.text != value) {
                            onValueChange(newValue.text)
                        }
                    }
                },
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(
                    fontSize = fontSize,
                    fontWeight = if (isBold) FontWeight.Bold else FontWeight.Medium,
                    color = PrimaryNavy,
                    textAlign = textAlign
                ),
                cursorBrush = SolidColor(AccentNavy),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                    .onFocusChanged { focusState ->
                        isFocused = focusState.isFocused
                        if (focusState.isFocused) {
                            if (!hadFocus) {
                                hadFocus = true
                                focusGainedTime = currentTimeMillis()
                                textOnFocusGain = textFieldValue.text
                                textFieldValue = textFieldValue.copy(
                                    selection = TextRange(0, textFieldValue.text.length)
                                )
                            }
                        } else {
                            hadFocus = false
                            focusGainedTime = 0L
                            textOnFocusGain = ""
                            textFieldValue = textFieldValue.copy(
                                selection = TextRange(textFieldValue.text.length)
                            )
                        }
                    }
                    .onPreviewKeyEvent { keyEvent ->
                        if (keyEvent.key == Key.Tab && keyEvent.type == KeyEventType.KeyDown) {
                            if (keyEvent.isShiftPressed) {
                                if (prevFocusRequester != null) {
                                    prevFocusRequester.requestFocus()
                                    true
                                } else {
                                    false
                                }
                            } else {
                                if (nextFocusRequester != null) {
                                    nextFocusRequester.requestFocus()
                                    true
                                } else {
                                    false
                                }
                            }
                        } else {
                            false
                        }
                    }
            )
        }
    }
}

@Composable
internal fun ExportedBadgePill() {
    Surface(
        color = ColorSuccessEmerald.copy(alpha = 0.15f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, ColorSuccessEmerald.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = ColorSuccessEmerald,
                modifier = Modifier.size(11.dp)
            )
        }
    }
}
