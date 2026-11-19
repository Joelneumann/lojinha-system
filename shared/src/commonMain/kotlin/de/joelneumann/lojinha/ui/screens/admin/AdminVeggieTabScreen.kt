package de.joelneumann.lojinha.ui.screens.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.joelneumann.lojinha.domain.model.Language
import de.joelneumann.lojinha.domain.model.VeggieOrderRound
import de.joelneumann.lojinha.domain.model.VeggieRoundStatus
import de.joelneumann.lojinha.ui.components.admin.AdminBadgeType
import de.joelneumann.lojinha.ui.components.admin.AdminStatusBadge
import de.joelneumann.lojinha.ui.components.admin.CompactCsvExportButton
import de.joelneumann.lojinha.ui.components.admin.veggie.AdminVeggieCatalogSection
import de.joelneumann.lojinha.ui.components.admin.veggie.AdminVeggieSettlementMatrix
import de.joelneumann.lojinha.ui.components.admin.veggie.ExportedBadgePill
import de.joelneumann.lojinha.ui.components.general.AppVerticalScrollbar
import de.joelneumann.lojinha.ui.components.general.ConfirmationDialog
import de.joelneumann.lojinha.ui.components.general.PaginationBar
import de.joelneumann.lojinha.ui.components.general.PdfExportConfirmDialog
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.i18n.LanguageManager
import de.joelneumann.lojinha.ui.theme.*
import de.joelneumann.lojinha.ui.utils.Formatting
import de.joelneumann.lojinha.ui.utils.IsoWeekUtils
import de.joelneumann.lojinha.ui.viewmodel.admin.AdminVeggieSubTab
import de.joelneumann.lojinha.ui.viewmodel.admin.AdminVeggieViewModel

private data class PendingPdfExport(val roundId: String, val isBuyer: Boolean)

private sealed interface VeggiePdfExportTarget {
    data class Buyer(val roundId: String) : VeggiePdfExportTarget
    data class Worker(val roundId: String) : VeggiePdfExportTarget
    data object OrderForm : VeggiePdfExportTarget
}

@Composable
fun AdminVeggieTabScreen(
    viewModel: AdminVeggieViewModel,
    onUnsavedStateChanged: (Boolean) -> Unit = {},
    onExportBuyerPdf: ((roundId: String, language: Language) -> Unit)? = null,
    onExportWorkerPdf: ((roundId: String, language: Language) -> Unit)? = null,
    onExportOrderFormPdf: ((language: Language) -> Unit)? = null,
    onExportVeggieProductsCsv: (() -> Unit)? = null,
    onExportVeggieOrdersCsv: (() -> Unit)? = null,
    onOpenExportedPdf: ((String) -> Unit)? = null,
    onRevealExportedFolder: ((String) -> Unit)? = null
) {
    val strings = I18n.current
    val currentLanguage = LanguageManager.currentLanguage

    val currentSubTab by viewModel.currentSubTab.collectAsState()
    val products by viewModel.products.collectAsState()
    val unitTypes by viewModel.unitTypes.collectAsState()
    val catalogSearchQuery by viewModel.catalogSearchQuery.collectAsState()
    val users by viewModel.users.collectAsState()
    val allUsersById by viewModel.allUsersById.collectAsState()
    val rounds by viewModel.rounds.collectAsState()
    val historyCurrentPage by viewModel.historyCurrentPage.collectAsState()
    val historyTotalPages by viewModel.historyTotalPages.collectAsState()
    val historyPageSize by viewModel.historyPageSize.collectAsState()
    val historyTotalCount by viewModel.historyTotalCount.collectAsState()
    val pagedSettledRounds by viewModel.pagedSettledRounds.collectAsState()
    val activeRound by viewModel.activeRound.collectAsState()
    val editingPastRound by viewModel.editingPastRound.collectAsState()
    val selectedRound by viewModel.selectedRound.collectAsState()
    val selectedRoundEntries by viewModel.selectedRoundEntries.collectAsState()
    val draftActualQuantities by viewModel.draftActualQuantities.collectAsState()
    val draftMarketPrices by viewModel.draftMarketPrices.collectAsState()
    val draftTotalSpent by viewModel.draftTotalSpent.collectAsState()
    val draftBuyerFeeInput by viewModel.draftBuyerFeeInput.collectAsState()
    val draftTransportFeeInput by viewModel.draftTransportFeeInput.collectAsState()
    val draftTransporterUserId by viewModel.draftTransporterUserId.collectAsState()
    val hasUnsavedSettlementChanges by viewModel.hasUnsavedSettlementChanges.collectAsState()
    val liveSettlementPreview by viewModel.liveSettlementPreview.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val pdfExportResultPath by viewModel.pdfExportResultPath.collectAsState()
    val nextAutoRoundSchedule by viewModel.nextAutoRoundSchedule.collectAsState()

    var pendingSubTab by remember { mutableStateOf<AdminVeggieSubTab?>(null) }
    var pendingBackFromHistoryEdit by remember { mutableStateOf(false) }
    var showDiscardConfirmDialog by remember { mutableStateOf(false) }
    var pendingPdfExport by remember { mutableStateOf<PendingPdfExport?>(null) }
    var pendingPdfConfirmation by remember { mutableStateOf<VeggiePdfExportTarget?>(null) }

    LaunchedEffect(hasUnsavedSettlementChanges) {
        onUnsavedStateChanged(hasUnsavedSettlementChanges)
    }

    val hasAnyOpenOrClosedRound = remember(rounds) {
        rounds.any { it.status == VeggieRoundStatus.OPEN || it.status == VeggieRoundStatus.CLOSED }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Sub-Tab Selector Bar + "Open New Round" Quick Action
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SubTabButton(
                    label = strings.veggieSubTabRound,
                    icon = Icons.Default.TableChart,
                    selected = currentSubTab == AdminVeggieSubTab.ACTIVE_ROUND,
                    onClick = {
                        if (currentSubTab != AdminVeggieSubTab.ACTIVE_ROUND) {
                            if (hasUnsavedSettlementChanges) {
                                pendingSubTab = AdminVeggieSubTab.ACTIVE_ROUND
                                showDiscardConfirmDialog = true
                            } else {
                                if (editingPastRound != null) {
                                    viewModel.closePastRoundEditor()
                                }
                                viewModel.selectSubTab(AdminVeggieSubTab.ACTIVE_ROUND)
                            }
                        }
                    }
                )
                SubTabButton(
                    label = strings.veggieSubTabCatalog,
                    icon = Icons.Default.Eco,
                    selected = currentSubTab == AdminVeggieSubTab.CATALOG,
                    onClick = {
                        if (currentSubTab != AdminVeggieSubTab.CATALOG) {
                            if (hasUnsavedSettlementChanges) {
                                pendingSubTab = AdminVeggieSubTab.CATALOG
                                showDiscardConfirmDialog = true
                            } else {
                                if (editingPastRound != null) {
                                    viewModel.closePastRoundEditor()
                                }
                                viewModel.selectSubTab(AdminVeggieSubTab.CATALOG)
                            }
                        }
                    }
                )
                SubTabButton(
                    label = strings.veggieSubTabHistory,
                    icon = Icons.Default.History,
                    selected = currentSubTab == AdminVeggieSubTab.HISTORY,
                    onClick = {
                        if (currentSubTab != AdminVeggieSubTab.HISTORY) {
                            if (hasUnsavedSettlementChanges) {
                                pendingSubTab = AdminVeggieSubTab.HISTORY
                                showDiscardConfirmDialog = true
                            } else {
                                viewModel.selectSubTab(AdminVeggieSubTab.HISTORY)
                            }
                        }
                    }
                )
            }

            // Top action buttons based on selected sub-tab
            when (currentSubTab) {
                AdminVeggieSubTab.ACTIVE_ROUND -> {
                    val currentRound = activeRound
                    if (currentRound != null) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.height(IntrinsicSize.Min)
                        ) {
                            if (onExportBuyerPdf != null) {
                                OutlinedButton(
                                    onClick = {
                                        if (hasUnsavedSettlementChanges) {
                                            pendingPdfExport = PendingPdfExport(currentRound.id, isBuyer = true)
                                        } else {
                                            pendingPdfConfirmation = VeggiePdfExportTarget.Buyer(currentRound.id)
                                        }
                                    },
                                    enabled = !isSaving,
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = PrimaryNavy, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(strings.exportBuyerPdfBtn, fontWeight = FontWeight.Bold, color = PrimaryNavy, fontSize = 12.sp)
                                    if (currentRound.buyerPdfExportedAt != null) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        if (currentRound.isAdminBuyerPdfOutdated) {
                                            AdminStatusBadge(
                                                text = strings.buyerPdfOutdatedBadge,
                                                type = AdminBadgeType.WARNING
                                            )
                                        } else {
                                            ExportedBadgePill()
                                        }
                                    }
                                }
                                val buyerBadgeText = when {
                                    currentRound.buyerPortalPdfExportedAt == null -> strings.adminBuyerPortalPdfNotExported
                                    currentRound.isBuyerPortalPdfOutdated -> strings.adminBuyerPortalPdfOutdated
                                    else -> strings.adminBuyerPortalPdfUpToDate
                                }
                                val (buyerBadgeBg, buyerBadgeBorder, buyerBadgeTextColor) = when {
                                    currentRound.buyerPortalPdfExportedAt == null -> Triple(
                                        PrimaryNavy.copy(alpha = 0.08f),
                                        PrimaryNavy.copy(alpha = 0.25f),
                                        PrimaryNavy
                                    )
                                    currentRound.isBuyerPortalPdfOutdated -> Triple(
                                        ColorWarningAmber.copy(alpha = 0.14f),
                                        ColorWarningAmber,
                                        ColorWarningAmber
                                    )
                                    else -> Triple(
                                        ColorSuccessEmerald.copy(alpha = 0.14f),
                                        ColorSuccessEmerald,
                                        ColorSuccessEmerald
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = buyerBadgeBg,
                                    border = BorderStroke(1.dp, buyerBadgeBorder),
                                    modifier = Modifier.fillMaxHeight()
                                ) {
                                    Box(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = buyerBadgeText,
                                            fontWeight = FontWeight.Bold,
                                            color = buyerBadgeTextColor,
                                            fontSize = 12.sp,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                            if (onExportWorkerPdf != null) {
                                OutlinedButton(
                                    onClick = {
                                        if (hasUnsavedSettlementChanges) {
                                            pendingPdfExport = PendingPdfExport(currentRound.id, isBuyer = false)
                                        } else {
                                            pendingPdfConfirmation = VeggiePdfExportTarget.Worker(currentRound.id)
                                        }
                                    },
                                    enabled = !isSaving,
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.GridOn, contentDescription = null, tint = PrimaryNavy, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(strings.exportWorkerPdfBtn, fontWeight = FontWeight.Bold, color = PrimaryNavy, fontSize = 12.sp)
                                    if (currentRound.workerPdfExportedAt != null) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        ExportedBadgePill()
                                    }
                                }
                            }
                        }
                    }
                }
                AdminVeggieSubTab.CATALOG -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (onExportVeggieProductsCsv != null) {
                            CompactCsvExportButton(onClick = onExportVeggieProductsCsv)
                        }
                        if (onExportOrderFormPdf != null) {
                            OutlinedButton(
                                onClick = { pendingPdfConfirmation = VeggiePdfExportTarget.OrderForm },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = PrimaryNavy, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(strings.exportOrderFormPdfBtn, fontWeight = FontWeight.Bold, color = PrimaryNavy, fontSize = 12.sp)
                            }
                        }
                    }
                }
                AdminVeggieSubTab.HISTORY -> {
                    val pastRound = editingPastRound
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (onExportVeggieOrdersCsv != null) {
                            CompactCsvExportButton(onClick = onExportVeggieOrdersCsv)
                        }
                        if (pastRound != null) {
                            if (onExportBuyerPdf != null) {
                                OutlinedButton(
                                    onClick = {
                                        pendingPdfConfirmation = VeggiePdfExportTarget.Buyer(pastRound.id)
                                    },
                                    enabled = !isSaving,
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = PrimaryNavy, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(strings.exportBuyerPdfBtn, fontWeight = FontWeight.Bold, color = PrimaryNavy, fontSize = 12.sp)
                                    if (pastRound.buyerPdfExportedAt != null) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        ExportedBadgePill()
                                    }
                                }
                            }
                            if (onExportWorkerPdf != null) {
                                OutlinedButton(
                                    onClick = {
                                        pendingPdfConfirmation = VeggiePdfExportTarget.Worker(pastRound.id)
                                    },
                                    enabled = !isSaving,
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.GridOn, contentDescription = null, tint = PrimaryNavy, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(strings.exportWorkerPdfBtn, fontWeight = FontWeight.Bold, color = PrimaryNavy, fontSize = 12.sp)
                                    if (pastRound.workerPdfExportedAt != null) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        ExportedBadgePill()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (statusMessage != null) {
            Surface(
                color = ColorSuccessEmerald.copy(alpha = 0.12f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(statusMessage!!, color = ColorSuccessEmerald, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    IconButton(onClick = { viewModel.clearMessages() }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = ColorSuccessEmerald, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

        if (errorMessage != null) {
            Surface(
                color = ColorDangerCrimson.copy(alpha = 0.12f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(errorMessage!!, color = ColorDangerCrimson, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    IconButton(onClick = { viewModel.clearMessages() }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = ColorDangerCrimson, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

        when (currentSubTab) {
            AdminVeggieSubTab.ACTIVE_ROUND -> {
                if (activeRound == null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SurfaceWhite,
                        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DividerBorder)),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Eco,
                                contentDescription = null,
                                tint = ColorSuccessEmerald,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = strings.noActiveRoundTitle,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryNavy
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = strings.noActiveRoundDesc,
                                fontSize = 13.sp,
                                color = TextSecondaryMuted
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { viewModel.openNewRound() },
                                enabled = !isSaving,
                                colors = ButtonDefaults.buttonColors(containerColor = ColorSuccessEmerald),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.AddCircleOutline, contentDescription = null, modifier = Modifier.size(18.dp), tint = SurfaceWhite)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(strings.openNewRoundBtn, fontWeight = FontWeight.Bold, color = SurfaceWhite)
                            }

                            if (nextAutoRoundSchedule != null) {
                                val scheduleInfo = nextAutoRoundSchedule!!
                                Spacer(modifier = Modifier.height(16.dp))
                                Surface(
                                    color = ColorSuccessEmerald.copy(alpha = 0.08f),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, ColorSuccessEmerald.copy(alpha = 0.25f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Schedule,
                                            contentDescription = null,
                                            tint = ColorSuccessEmerald,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = scheduleInfo.formatScheduleText(strings, currentLanguage),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = PrimaryNavy
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    AdminVeggieSettlementMatrix(
                        round = activeRound!!,
                        entries = selectedRoundEntries,
                        products = products,
                        users = users,
                        allUsersById = allUsersById,
                        draftActualQuantities = draftActualQuantities,
                        draftMarketPrices = draftMarketPrices,
                        draftTotalSpent = draftTotalSpent,
                        draftBuyerFeeInput = draftBuyerFeeInput,
                        draftTransportFeeInput = draftTransportFeeInput,
                        draftTransporterUserId = draftTransporterUserId,
                        livePreview = liveSettlementPreview,
                        isSaving = isSaving,
                        onUpdateActualQuantity = viewModel::updateEntryActualQuantity,
                        onUpdateMarketUnitPrice = viewModel::updateProductMarketUnitPrice,
                        onUpdateTotalSpent = viewModel::updateProductTotalSpent,
                        onUpdateBuyerFee = viewModel::updateBuyerFeeInput,
                        onUpdateTransportFee = viewModel::updateTransportFeeInput,
                        onUpdateTransporterUser = viewModel::updateTransporterUserId,
                        onCloseRound = viewModel::closeSelectedRound,
                        onReopenRound = viewModel::reopenSelectedRound,
                        onSaveDraft = viewModel::saveDraftSettlement,
                        onExecuteSettlement = viewModel::executeSettlement,
                        onDeleteRound = viewModel::deleteCurrentRound,
                        onAddAdminEntry = viewModel::addOrUpdateCustomerEntry,
                        onRemoveAdminEntry = viewModel::removeCustomerEntry,
                        onExportBuyerPdf = onExportBuyerPdf,
                        onExportWorkerPdf = onExportWorkerPdf,
                        hasUnsavedChanges = hasUnsavedSettlementChanges,
                        onResetChanges = viewModel::discardDraft,
                        unitTypes = unitTypes,
                        allowCollapseSettingsCard = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            AdminVeggieSubTab.CATALOG -> {
                AdminVeggieCatalogSection(
                    products = products,
                    unitTypes = unitTypes,
                    searchQuery = catalogSearchQuery,
                    onSearchQueryChange = viewModel::updateCatalogSearchQuery,
                    onSaveProduct = viewModel::saveVeggieProduct,
                    onToggleProductActive = viewModel::toggleVeggieProductActive,
                    onDeleteProduct = viewModel::deleteVeggieProduct,
                    onSaveUnitType = viewModel::saveUnitType,
                    onDeleteUnitType = viewModel::deleteUnitType,
                    openRoundOrderCountForProduct = { productId ->
                        val openRound = activeRound?.takeIf { it.status == VeggieRoundStatus.OPEN }
                        if (openRound != null && selectedRound?.id == openRound.id) {
                            selectedRoundEntries.count {
                                it.veggieProductId == productId && it.orderedQuantityScaled > 0L
                            }
                        } else {
                            0
                        }
                    },
                    isSavingProduct = isSaving,
                    modifier = Modifier.weight(1f)
                )
            }

            AdminVeggieSubTab.HISTORY -> {
                if (editingPastRound != null) {
                    Column(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val isoWeek = remember(editingPastRound!!.openedAt) {
                            IsoWeekUtils.fromEpochMillis(editingPastRound!!.openedAt)
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = SurfaceWhite,
                            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DividerBorder)),
                            shadowElevation = 1.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            if (hasUnsavedSettlementChanges) {
                                                pendingBackFromHistoryEdit = true
                                                showDiscardConfirmDialog = true
                                            } else {
                                                viewModel.closePastRoundEditor()
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(strings.backToPastRounds, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Text(
                                        text = strings.editingPastRoundTitle(isoWeek.weekOfYear, isoWeek.isoYear),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryNavy
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (hasUnsavedSettlementChanges) {
                                        AdminStatusBadge(
                                            text = strings.unsavedChangesBadge,
                                            type = AdminBadgeType.WARNING
                                        )
                                    }
                                }
                            }
                        }

                        AdminVeggieSettlementMatrix(
                            round = editingPastRound!!,
                            entries = selectedRoundEntries,
                            products = products,
                            users = users,
                            allUsersById = allUsersById,
                            draftActualQuantities = draftActualQuantities,
                            draftMarketPrices = draftMarketPrices,
                            draftTotalSpent = draftTotalSpent,
                            draftBuyerFeeInput = draftBuyerFeeInput,
                            draftTransportFeeInput = draftTransportFeeInput,
                            draftTransporterUserId = draftTransporterUserId,
                            livePreview = liveSettlementPreview,
                            isSaving = isSaving,
                            onUpdateActualQuantity = viewModel::updateEntryActualQuantity,
                            onUpdateMarketUnitPrice = viewModel::updateProductMarketUnitPrice,
                            onUpdateTotalSpent = viewModel::updateProductTotalSpent,
                            onUpdateBuyerFee = viewModel::updateBuyerFeeInput,
                            onUpdateTransportFee = viewModel::updateTransportFeeInput,
                            onUpdateTransporterUser = viewModel::updateTransporterUserId,
                            onCloseRound = viewModel::closeSelectedRound,
                            onReopenRound = viewModel::reopenSelectedRound,
                            onSaveDraft = viewModel::saveDraftSettlement,
                            onExecuteSettlement = viewModel::executeSettlement,
                            onDeleteRound = viewModel::deleteCurrentRound,
                            onAddAdminEntry = viewModel::addOrUpdateCustomerEntry,
                            onRemoveAdminEntry = viewModel::removeCustomerEntry,
                            onExportBuyerPdf = onExportBuyerPdf,
                            onExportWorkerPdf = onExportWorkerPdf,
                            hasUnsavedChanges = hasUnsavedSettlementChanges,
                            onResetChanges = viewModel::discardDraft,
                            unitTypes = unitTypes,
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    ) {
                        val historyListState = rememberLazyListState()

                        LaunchedEffect(historyCurrentPage, historyPageSize) {
                            historyListState.scrollToItem(0)
                        }

                        LazyColumn(
                            state = historyListState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 12.dp)
                        ) {
                            if (historyTotalCount == 0) {
                                item(key = "empty-rounds-history") {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(strings.noVeggieHistoryMsg, color = TextSecondaryMuted, fontSize = 14.sp)
                                    }
                                }
                            } else {
                                items(pagedSettledRounds, key = { it.id }) { round ->
                                    RoundHistoryCard(
                                        round = round,
                                        isSelected = false,
                                        transporterName = round.transporterUserId?.let { tid ->
                                            allUsersById[tid]?.name ?: users.find { it.id == tid }?.name
                                        },
                                        onOpenInMatrix = { viewModel.openPastRoundForEditing(round.id) },
                                        onExportBuyerPdf = onExportBuyerPdf?.let { { pendingPdfConfirmation = VeggiePdfExportTarget.Buyer(round.id) } },
                                        onExportWorkerPdf = onExportWorkerPdf?.let { { pendingPdfConfirmation = VeggiePdfExportTarget.Worker(round.id) } }
                                    )
                                }
                            }
                        }

                        AppVerticalScrollbar(
                            scrollState = historyListState,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .padding(vertical = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    PaginationBar(
                        currentPage = historyCurrentPage,
                        totalPages = historyTotalPages,
                        pageSize = historyPageSize,
                        totalCount = historyTotalCount,
                        onPageChange = viewModel::setHistoryPage,
                        onPageSizeChange = viewModel::setHistoryPageSize,
                        pageSizeOptions = listOf(10, 25, 50, 100),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    if (showDiscardConfirmDialog) {
        ConfirmationDialog(
            title = strings.discardChangesTitle,
            message = strings.discardChangesMsg,
            confirmText = strings.discard,
            cancelText = strings.cancel,
            confirmButtonColor = ColorDangerCrimson,
            onConfirm = {
                showDiscardConfirmDialog = false
                if (pendingBackFromHistoryEdit) {
                    pendingBackFromHistoryEdit = false
                    viewModel.closePastRoundEditor()
                } else if (pendingSubTab != null) {
                    val target = pendingSubTab!!
                    pendingSubTab = null
                    if (editingPastRound != null) {
                        viewModel.closePastRoundEditor()
                    } else {
                        viewModel.discardDraft()
                    }
                    viewModel.selectSubTab(target)
                }
            },
            onDismiss = {
                showDiscardConfirmDialog = false
                pendingBackFromHistoryEdit = false
                pendingSubTab = null
            }
        )
    }

    if (pendingPdfExport != null) {
        val target = pendingPdfExport!!
        ConfirmationDialog(
            title = strings.unsavedExportTitle,
            message = strings.unsavedExportMsg,
            confirmText = strings.saveAndExportBtn,
            cancelText = strings.cancel,
            confirmButtonColor = AccentNavy,
            onConfirm = {
                pendingPdfExport = null
                viewModel.saveDraftSettlement {
                    if (target.isBuyer) {
                        pendingPdfConfirmation = VeggiePdfExportTarget.Buyer(target.roundId)
                    } else {
                        pendingPdfConfirmation = VeggiePdfExportTarget.Worker(target.roundId)
                    }
                }
            },
            onDismiss = {
                pendingPdfExport = null
            }
        )
    }

    pendingPdfConfirmation?.let { target ->
        val question = when (target) {
            is VeggiePdfExportTarget.Buyer -> strings.exportBuyerPdfQuestion
            is VeggiePdfExportTarget.Worker -> strings.exportWorkerPdfQuestion
            is VeggiePdfExportTarget.OrderForm -> strings.exportOrderFormPdfQuestion
        }
        PdfExportConfirmDialog(
            question = question,
            initialLanguage = currentLanguage,
            onConfirm = { lang ->
                pendingPdfConfirmation = null
                when (target) {
                    is VeggiePdfExportTarget.Buyer -> onExportBuyerPdf?.invoke(target.roundId, lang)
                    is VeggiePdfExportTarget.Worker -> onExportWorkerPdf?.invoke(target.roundId, lang)
                    is VeggiePdfExportTarget.OrderForm -> onExportOrderFormPdf?.invoke(lang)
                }
            },
            onDismiss = { pendingPdfConfirmation = null }
        )
    }

    if (pdfExportResultPath != null) {
        val resultPath = pdfExportResultPath!!
        AlertDialog(
            onDismissRequest = { viewModel.dismissExportResultDialog() },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ColorSuccessEmerald, modifier = Modifier.size(24.dp))
                    Text(strings.pdfExportSuccessTitle, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = PrimaryNavy)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(strings.pdfSavedToMsg, fontSize = 13.sp, color = TextSecondaryMuted)
                    Surface(
                        color = SurfaceContainerHighLight,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = resultPath,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = PrimaryNavy,
                            modifier = Modifier.padding(10.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (onOpenExportedPdf != null) {
                            Button(
                                onClick = {
                                    onOpenExportedPdf(resultPath)
                                    viewModel.dismissExportResultDialog()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentNavy),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f).height(40.dp)
                            ) {
                                Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(strings.openPdfBtn, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (onRevealExportedFolder != null) {
                            OutlinedButton(
                                onClick = {
                                    onRevealExportedFolder(resultPath)
                                    viewModel.dismissExportResultDialog()
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f).height(40.dp)
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp), tint = PrimaryNavy)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(strings.showInFolderBtn, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PrimaryNavy)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.dismissExportResultDialog() },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentNavy)
                ) {
                    Text(strings.confirm, fontWeight = FontWeight.Bold, color = SurfaceWhite)
                }
            }
        )
    }
}

@Composable
private fun SubTabButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) AccentNavy else SurfaceWhite,
            contentColor = if (selected) SurfaceWhite else PrimaryNavy
        ),
        border = BorderStroke(1.dp, if (selected) AccentNavy else DividerBorder),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RoundHistoryCard(
    round: VeggieOrderRound,
    isSelected: Boolean,
    transporterName: String?,
    onOpenInMatrix: () -> Unit,
    onExportBuyerPdf: (() -> Unit)?,
    onExportWorkerPdf: (() -> Unit)?
) {
    val strings = I18n.current
    val isoWeek = remember(round.openedAt) { IsoWeekUtils.fromEpochMillis(round.openedAt) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = SurfaceWhite,
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) AccentNavy else DividerBorder),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = strings.weekOfYearLabel(isoWeek.weekOfYear, isoWeek.isoYear),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryNavy
                    )
                }

                Text(
                    text = Formatting.formatTimestamp(round.openedAt),
                    fontSize = 12.sp,
                    color = TextSecondaryMuted
                )

                Text(
                    text = "${strings.veggieCombinedFeeLabel}: ${Formatting.formatBrl(round.combinedFeeCents)} • ${strings.transporterLabel}: ${transporterName ?: strings.transporterExternal}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = AccentNavy
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onExportBuyerPdf != null) {
                    OutlinedButton(
                        onClick = onExportBuyerPdf,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(15.dp), tint = PrimaryNavy)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(strings.exportBuyerPdfBtn, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PrimaryNavy)
                        if (round.buyerPdfExportedAt != null) {
                            Spacer(modifier = Modifier.width(6.dp))
                            ExportedBadgePill()
                        }
                    }
                }

                if (onExportWorkerPdf != null) {
                    OutlinedButton(
                        onClick = onExportWorkerPdf,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.GridOn, contentDescription = null, modifier = Modifier.size(15.dp), tint = PrimaryNavy)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(strings.exportWorkerPdfBtn, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PrimaryNavy)
                        if (round.workerPdfExportedAt != null) {
                            Spacer(modifier = Modifier.width(6.dp))
                            ExportedBadgePill()
                        }
                    }
                }

                Button(
                    onClick = onOpenInMatrix,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentNavy),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp), tint = SurfaceWhite)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = strings.edit,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = SurfaceWhite
                    )
                }
            }
        }
    }
}
