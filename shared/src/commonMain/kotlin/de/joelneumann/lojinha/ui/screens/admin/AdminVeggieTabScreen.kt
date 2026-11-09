package de.joelneumann.lojinha.ui.screens.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import de.joelneumann.lojinha.ui.components.admin.veggie.AdminVeggieCatalogSection
import de.joelneumann.lojinha.ui.components.admin.veggie.AdminVeggieSettlementMatrix
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.i18n.LanguageManager
import de.joelneumann.lojinha.ui.theme.*
import de.joelneumann.lojinha.ui.utils.Formatting
import de.joelneumann.lojinha.ui.utils.IsoWeekUtils
import de.joelneumann.lojinha.ui.viewmodel.admin.AdminVeggieSubTab
import de.joelneumann.lojinha.ui.viewmodel.admin.AdminVeggieViewModel

@Composable
fun AdminVeggieTabScreen(
    viewModel: AdminVeggieViewModel,
    onUnsavedStateChanged: (Boolean) -> Unit = {},
    onExportBuyerPdf: ((roundId: String, language: Language) -> Unit)? = null,
    onExportWorkerPdf: ((roundId: String, language: Language) -> Unit)? = null
) {
    val strings = I18n.current
    val currentLanguage = LanguageManager.currentLanguage

    val currentSubTab by viewModel.currentSubTab.collectAsState()
    val products by viewModel.products.collectAsState()
    val unitTypes by viewModel.unitTypes.collectAsState()
    val catalogSearchQuery by viewModel.catalogSearchQuery.collectAsState()
    val users by viewModel.users.collectAsState()
    val rounds by viewModel.rounds.collectAsState()
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
                    onClick = { viewModel.selectSubTab(AdminVeggieSubTab.ACTIVE_ROUND) }
                )
                SubTabButton(
                    label = strings.veggieSubTabCatalog,
                    icon = Icons.Default.Eco,
                    selected = currentSubTab == AdminVeggieSubTab.CATALOG,
                    onClick = { viewModel.selectSubTab(AdminVeggieSubTab.CATALOG) }
                )
                SubTabButton(
                    label = "${strings.veggieSubTabHistory} (${rounds.size})",
                    icon = Icons.Default.History,
                    selected = currentSubTab == AdminVeggieSubTab.HISTORY,
                    onClick = { viewModel.selectSubTab(AdminVeggieSubTab.HISTORY) }
                )
            }

            if (!hasAnyOpenOrClosedRound) {
                Button(
                    onClick = { viewModel.openNewRound() },
                    enabled = !isSaving,
                    colors = ButtonDefaults.buttonColors(containerColor = ColorSuccessEmerald),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.AddCircleOutline, contentDescription = null, modifier = Modifier.size(16.dp), tint = SurfaceWhite)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(strings.openNewRoundBtn, fontWeight = FontWeight.Bold, color = SurfaceWhite, fontSize = 13.sp)
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
                if (selectedRound == null) {
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
                        }
                    }
                } else {
                    AdminVeggieSettlementMatrix(
                        round = selectedRound!!,
                        entries = selectedRoundEntries,
                        products = products,
                        users = users,
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
                        onAddAdminEntry = viewModel::addOrUpdateCustomerEntry,
                        onExportBuyerPdf = onExportBuyerPdf,
                        onExportWorkerPdf = onExportWorkerPdf,
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
                    isSavingProduct = isSaving,
                    modifier = Modifier.weight(1f)
                )
            }

            AdminVeggieSubTab.HISTORY -> {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    if (rounds.isEmpty()) {
                        item(key = "empty-rounds-history") {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(strings.noVeggieHistoryMsg, color = TextSecondaryMuted, fontSize = 14.sp)
                            }
                        }
                    } else {
                        items(rounds, key = { it.id }) { round ->
                            RoundHistoryCard(
                                round = round,
                                isSelected = selectedRound?.id == round.id,
                                transporterName = users.find { it.id == round.transporterUserId }?.name,
                                onOpenInMatrix = { viewModel.selectRound(round.id, switchToRoundTab = true) },
                                onExportBuyerPdf = onExportBuyerPdf?.let { cb -> { cb(round.id, currentLanguage) } },
                                onExportWorkerPdf = onExportWorkerPdf?.let { cb -> { cb(round.id, currentLanguage) } }
                            )
                        }
                    }
                }
            }
        }
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
                    }
                }

                Button(
                    onClick = onOpenInMatrix,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentNavy),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(15.dp), tint = SurfaceWhite)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (round.status == VeggieRoundStatus.SETTLED) strings.edit else strings.veggieSubTabRound,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = SurfaceWhite
                    )
                }
            }
        }
    }
}
