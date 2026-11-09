package de.joelneumann.lojinha.ui.components.admin.veggie

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import de.joelneumann.lojinha.ui.components.general.ConfirmationDialog
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.i18n.LanguageManager
import de.joelneumann.lojinha.ui.theme.*
import de.joelneumann.lojinha.ui.utils.Formatting
import de.joelneumann.lojinha.ui.utils.IsoWeekUtils
import de.joelneumann.lojinha.ui.utils.sortedByAccentInsensitive

private val COL_PRODUCT_WIDTH = 190.dp
private val COL_CUSTOMER_WIDTH = 148.dp
private val COL_TOTAL_QTY_WIDTH = 115.dp
private val COL_MARKET_PRICE_WIDTH = 135.dp
private val COL_TOTAL_SPENT_WIDTH = 140.dp
private val COL_EFFECTIVE_PRICE_WIDTH = 130.dp

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
    onAddAdminEntry: (user: User, product: VeggieProduct, quantityInput: String) -> Unit,
    onExportBuyerPdf: ((roundId: String, language: Language) -> Unit)? = null,
    onExportWorkerPdf: ((roundId: String, language: Language) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val strings = I18n.current
    val currentLanguage = LanguageManager.currentLanguage
    val isoWeek = remember(round.openedAt) { IsoWeekUtils.fromEpochMillis(round.openedAt) }

    var showConfirmSettle by remember { mutableStateOf(false) }
    var showAddEntryDialog by remember { mutableStateOf(false) }
    var transporterDropdownExpanded by remember { mutableStateOf(false) }

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

    val horizontalScrollState = rememberScrollState()

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
                            }
                            Text(
                                text = Formatting.formatTimestamp(round.openedAt),
                                fontSize = 12.sp,
                                color = TextSecondaryMuted
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
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

                        if (onExportBuyerPdf != null) {
                            OutlinedButton(
                                onClick = { onExportBuyerPdf(round.id, currentLanguage) },
                                enabled = entries.isNotEmpty(),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp), tint = PrimaryNavy)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(strings.exportBuyerPdfBtn, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PrimaryNavy)
                            }
                        }

                        if (onExportWorkerPdf != null) {
                            OutlinedButton(
                                onClick = { onExportWorkerPdf(round.id, currentLanguage) },
                                enabled = entries.isNotEmpty(),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.GridOn, contentDescription = null, modifier = Modifier.size(16.dp), tint = PrimaryNavy)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(strings.exportWorkerPdfBtn, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PrimaryNavy)
                            }
                        }

                        when (round.status) {
                            VeggieRoundStatus.OPEN -> {
                                Button(
                                    onClick = onCloseRound,
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
                                OutlinedButton(
                                    onClick = onReopenRound,
                                    enabled = !isSaving,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(16.dp), tint = PrimaryNavy)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(strings.reopenRoundBtn, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PrimaryNavy)
                                }

                                OutlinedButton(
                                    onClick = onSaveDraft,
                                    enabled = !isSaving,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp), tint = AccentNavy)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(strings.saveDraftBtn, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentNavy)
                                }

                                Button(
                                    onClick = { showConfirmSettle = true },
                                    enabled = !isSaving && entries.isNotEmpty(),
                                    colors = ButtonDefaults.buttonColors(containerColor = ColorSuccessEmerald),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp), tint = SurfaceWhite)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(strings.settleAndChargeBtn, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SurfaceWhite)
                                }
                            }

                            VeggieRoundStatus.SETTLED -> {
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
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalScroll(horizontalScrollState)
                ) {
                    // Matrix Table Header
                    Row(
                        modifier = Modifier
                            .background(PrimaryNavy)
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MatrixHeaderCell(strings.veggieProductNameLabel, COL_PRODUCT_WIDTH, TextAlign.Start)
                        roundCustomers.forEach { (_, customerName) ->
                            MatrixHeaderCell(customerName, COL_CUSTOMER_WIDTH, TextAlign.Center)
                        }
                        MatrixHeaderCell(strings.colActualQty, COL_TOTAL_QTY_WIDTH, TextAlign.Center)
                        MatrixHeaderCell(strings.colMarketUnitPrice, COL_MARKET_PRICE_WIDTH, TextAlign.Center)
                        MatrixHeaderCell(strings.colTotalSpent, COL_TOTAL_SPENT_WIDTH, TextAlign.Center)
                        MatrixHeaderCell(strings.colEffectivePrice, COL_EFFECTIVE_PRICE_WIDTH, TextAlign.End)
                    }

                    // Scrollable Product Rows
                    LazyColumn(
                        modifier = Modifier.weight(1f)
                    ) {
                        items(roundProductRows, key = { it.productId }) { rowInfo ->
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
                                    .border(0.5.dp, DividerBorder)
                                    .background(SurfaceWhite)
                                    .padding(vertical = 8.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Product Name & Unit
                                Column(
                                    modifier = Modifier.width(COL_PRODUCT_WIDTH).padding(end = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = rowInfo.productName,
                                        fontSize = 13.5.sp,
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

                                // Customer Cells
                                roundCustomers.forEach { (userId, _) ->
                                    val entry = rowInfo.entriesByUserId[userId]
                                    Box(
                                        modifier = Modifier.width(COL_CUSTOMER_WIDTH).padding(horizontal = 4.dp),
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

                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(3.dp)
                                            ) {
                                                Text(
                                                    text = "${strings.colOrderedQty}: $orderedFormatted ${rowInfo.unitName}",
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = TextSecondaryMuted
                                                )
                                                OutlinedTextField(
                                                    value = draftQtyStr,
                                                    onValueChange = { onUpdateActualQuantity(entry.id, it) },
                                                    singleLine = true,
                                                    textStyle = LocalTextStyle.current.copy(
                                                        fontSize = 12.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        textAlign = TextAlign.Center
                                                    ),
                                                    shape = RoundedCornerShape(6.dp),
                                                    modifier = Modifier.fillMaxWidth().height(46.dp)
                                                )
                                                Text(
                                                    text = Formatting.formatBrl(shareCents),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (shareCents > 0L) AccentNavy else TextSecondaryMuted
                                                )
                                            }
                                        }
                                    }
                                }

                                // Total Delivered Qty
                                Box(
                                    modifier = Modifier.width(COL_TOTAL_QTY_WIDTH).padding(horizontal = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "$formattedTotalDelivered ${rowInfo.unitName}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryNavy
                                    )
                                }

                                // Market Unit Price Input
                                Box(
                                    modifier = Modifier.width(COL_MARKET_PRICE_WIDTH).padding(horizontal = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    OutlinedTextField(
                                        value = draftMarketPrices[rowInfo.productId] ?: "",
                                        onValueChange = { onUpdateMarketUnitPrice(rowInfo.productId, it) },
                                        placeholder = { Text("0,00", fontSize = 11.5.sp, color = TextSecondaryMuted) },
                                        singleLine = true,
                                        textStyle = LocalTextStyle.current.copy(
                                            fontSize = 12.5.sp,
                                            textAlign = TextAlign.Center
                                        ),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.fillMaxWidth().height(48.dp)
                                    )
                                }

                                // Total Spent Input (Authoritative)
                                Box(
                                    modifier = Modifier.width(COL_TOTAL_SPENT_WIDTH).padding(horizontal = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    OutlinedTextField(
                                        value = draftTotalSpent[rowInfo.productId] ?: "",
                                        onValueChange = { onUpdateTotalSpent(rowInfo.productId, it) },
                                        placeholder = { Text("0,00", fontSize = 11.5.sp, color = TextSecondaryMuted) },
                                        singleLine = true,
                                        textStyle = LocalTextStyle.current.copy(
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center
                                        ),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.fillMaxWidth().height(48.dp)
                                    )
                                }

                                // Effective Unit Price (Read-Only)
                                Box(
                                    modifier = Modifier.width(COL_EFFECTIVE_PRICE_WIDTH).padding(horizontal = 6.dp),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    Text(
                                        text = if (effectiveUnitPrice > 0L) {
                                            "${Formatting.formatBrl(effectiveUnitPrice)}/${rowInfo.unitName}"
                                        } else "—",
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (effectiveUnitPrice > 0L) ColorSuccessEmerald else TextSecondaryMuted
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(thickness = 1.5.dp, color = PrimaryNavy.copy(alpha = 0.25f))

                    // Footer Row 1: Combined Fee Share per Customer
                    Row(
                        modifier = Modifier
                            .background(SurfaceContainerLight)
                            .padding(vertical = 6.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = strings.veggieCombinedFeeLabel,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PrimaryNavy,
                            modifier = Modifier.width(COL_PRODUCT_WIDTH)
                        )
                        roundCustomers.forEach { (userId, _) ->
                            val feeShare = previewCustomerSummaryMap[userId]?.combinedFeeShareCents ?: 0L
                            Text(
                                text = Formatting.formatBrl(feeShare),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextSecondaryMuted,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.width(COL_CUSTOMER_WIDTH)
                            )
                        }
                        Spacer(modifier = Modifier.width(COL_TOTAL_QTY_WIDTH + COL_MARKET_PRICE_WIDTH))
                        Text(
                            text = Formatting.formatBrl(livePreview?.combinedFeeCents ?: 0L),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryNavy,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(COL_TOTAL_SPENT_WIDTH)
                        )
                        Spacer(modifier = Modifier.width(COL_EFFECTIVE_PRICE_WIDTH))
                    }

                    // Footer Row 2: Grand Total per Customer
                    Row(
                        modifier = Modifier
                            .background(SurfaceContainerHighLight)
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = strings.veggieFinalTotalLabel,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryNavy,
                            modifier = Modifier.width(COL_PRODUCT_WIDTH)
                        )
                        roundCustomers.forEach { (userId, _) ->
                            val grandTotal = previewCustomerSummaryMap[userId]?.grandTotalCents ?: 0L
                            Text(
                                text = Formatting.formatBrl(grandTotal),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentNavy,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.width(COL_CUSTOMER_WIDTH)
                            )
                        }
                        Spacer(modifier = Modifier.width(COL_TOTAL_QTY_WIDTH + COL_MARKET_PRICE_WIDTH))
                        val totalAllCharged = (livePreview?.totalMarketProductsSpentCents ?: 0L) + (livePreview?.combinedFeeCents ?: 0L)
                        Text(
                            text = Formatting.formatBrl(totalAllCharged),
                            fontSize = 13.5.sp,
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

    if (showAddEntryDialog) {
        AddAdminVeggieEntryDialog(
            users = users,
            products = products.filter { !it.isDeleted && it.isActive },
            onConfirm = { user, product, qtyInput ->
                onAddAdminEntry(user, product, qtyInput)
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
    onConfirm: (User, VeggieProduct, String) -> Unit,
    onDismiss: () -> Unit
) {
    val strings = I18n.current
    var selectedUser by remember { mutableStateOf(users.firstOrNull()) }
    var selectedProduct by remember { mutableStateOf(products.firstOrNull()) }
    var quantityInput by remember { mutableStateOf("1") }
    var userMenuExpanded by remember { mutableStateOf(false) }
    var productMenuExpanded by remember { mutableStateOf(false) }

    val canAdd = selectedUser != null && selectedProduct != null && quantityInput.trim().isNotBlank()

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

                AdminLabeledField(
                    label = strings.quantity,
                    value = quantityInput,
                    onValueChange = { quantityInput = it },
                    placeholder = "1",
                    modifier = Modifier.fillMaxWidth()
                )

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
                                onConfirm(selectedUser!!, selectedProduct!!, quantityInput)
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
