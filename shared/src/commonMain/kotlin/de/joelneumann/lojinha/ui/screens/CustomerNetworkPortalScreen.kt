package de.joelneumann.lojinha.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.joelneumann.lojinha.domain.model.*
import de.joelneumann.lojinha.domain.repository.CustomerVeggieRoundHistoryItem
import de.joelneumann.lojinha.ui.components.admin.AdminStatusBadge
import de.joelneumann.lojinha.ui.components.admin.AdminBadgeType
import de.joelneumann.lojinha.ui.components.general.HeaderBar
import de.joelneumann.lojinha.ui.components.general.LogoutButton
import de.joelneumann.lojinha.ui.components.general.SearchInputField
import de.joelneumann.lojinha.ui.components.history.UserBalanceWarningBanner
import de.joelneumann.lojinha.ui.components.history.UserSettingsModalDialog
import de.joelneumann.lojinha.ui.components.shopping.UserBalanceHeader
import de.joelneumann.lojinha.ui.components.veggie.VeggieAmountInputDialog
import de.joelneumann.lojinha.ui.components.veggie.VeggieProductDetailModal
import de.joelneumann.lojinha.ui.components.veggie.VeggieProductImage
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.i18n.LanguageManager
import de.joelneumann.lojinha.ui.theme.*
import de.joelneumann.lojinha.ui.utils.Formatting
import de.joelneumann.lojinha.ui.utils.IsoWeekInfo
import de.joelneumann.lojinha.ui.utils.IsoWeekUtils
import de.joelneumann.lojinha.ui.utils.containsIgnoreAccents
import de.joelneumann.lojinha.ui.utils.setBrowserBeforeUnloadWarning
import de.joelneumann.lojinha.ui.viewmodel.CustomerVeggieViewModel
import de.joelneumann.lojinha.ui.viewmodel.TransactionHistoryViewModel
import kotlin.math.roundToLong

enum class CustomerPortalTab {
    VEGGIE_ORDER,
    VEGGIE_HISTORY,
    STORE_HISTORY
}

@Composable
fun CustomerNetworkPortalScreen(
    user: User,
    settings: SystemSettings,
    veggieViewModel: CustomerVeggieViewModel,
    historyViewModel: TransactionHistoryViewModel,
    onUserUpdated: (User) -> Unit,
    onLogout: () -> Unit
) {
    val strings = I18n.current
    val currentLanguage = LanguageManager.currentLanguage
    val rate = settings.exchangeRateFor(user.secondaryCurrency)

    var currentTab by remember { mutableStateOf(CustomerPortalTab.VEGGIE_ORDER) }
    var pendingNavigation by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showUnsavedDialog by remember { mutableStateOf(false) }

    val hasUnsavedChanges by veggieViewModel.hasUnsavedOrderChanges.collectAsState()

    DisposableEffect(hasUnsavedChanges) {
        setBrowserBeforeUnloadWarning(hasUnsavedChanges)
        onDispose {
            setBrowserBeforeUnloadWarning(false)
        }
    }

    fun requestNavigation(action: () -> Unit) {
        if (currentTab == CustomerPortalTab.VEGGIE_ORDER && hasUnsavedChanges) {
            pendingNavigation = action
            showUnsavedDialog = true
        } else {
            action()
        }
    }

    val showSettingsModal by historyViewModel.showSettingsModal.collectAsState()
    val pinInput by historyViewModel.pinInput.collectAsState()
    val selectedLanguage by historyViewModel.selectedLanguage.collectAsState()
    val selectedSecondaryCurrency by historyViewModel.selectedSecondaryCurrency.collectAsState()
    val selectedAvatar by historyViewModel.selectedAvatar.collectAsState()
    val selectedRequirePinOnKiosk by historyViewModel.selectedRequirePinOnKiosk.collectAsState()

    val selectedProductForDetail by veggieViewModel.selectedProductForDetail.collectAsState()
    val unitTypes by veggieViewModel.unitTypes.collectAsState()
    val unitTypesById = remember(unitTypes) { unitTypes.associateBy { it.id } }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceContainerLight)
    ) {
        val isMobile = maxWidth < 600.dp
        val isNarrow = maxWidth < 750.dp

        Column(modifier = Modifier.fillMaxSize()) {
            HeaderBar(
                title = "Lojinha",
                onLanguageSelected = { lang ->
                    LanguageManager.setLanguage(lang)
                    onUserUpdated(user.copy(language = lang))
                },
                actions = {
                    LogoutButton(onClick = { requestNavigation(onLogout) })
                }
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (isMobile) 10.dp else ScreenPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Top Row: UserBalanceHeader + User Settings Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(if (isMobile) 8.dp else 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UserBalanceHeader(
                        user = user,
                        rate = rate,
                        modifier = Modifier.weight(1f)
                    )

                    Surface(
                        onClick = { historyViewModel.openSettingsModal(user) },
                        shape = RoundedCornerShape(12.dp),
                        color = SurfaceWhite,
                        shadowElevation = 2.dp
                    ) {
                        if (isMobile) {
                            Box(
                                modifier = Modifier.size(52.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = strings.userSettings,
                                    tint = PrimaryNavy,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = null,
                                    tint = PrimaryNavy,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = strings.userSettings,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryNavy,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                }

                val warningLevel = user.balanceWarningLevel(
                    yellowThresholdCents = settings.yellowBalanceWarningThresholdCents,
                    redThresholdCents = settings.redBalanceWarningThresholdCents
                )
                if (warningLevel != BalanceWarningLevel.NORMAL) {
                    UserBalanceWarningBanner(user = user, settings = settings)
                }

                // 3-Tab Navigation Bar
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SurfaceWhite,
                    shadowElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val tabs = listOf(
                            Triple(CustomerPortalTab.VEGGIE_ORDER, Icons.Default.Eco, strings.customerPortalTabOrder),
                            Triple(CustomerPortalTab.VEGGIE_HISTORY, Icons.Default.History, strings.customerPortalTabVeggieHistory),
                            Triple(CustomerPortalTab.STORE_HISTORY, Icons.AutoMirrored.Filled.ReceiptLong, strings.customerPortalTabStoreHistory)
                        )
                        tabs.forEach { (tab, icon, label) ->
                            val isSelected = currentTab == tab
                            Box(
                                modifier = Modifier
                                    .then(if (isNarrow) Modifier.wrapContentWidth() else Modifier.weight(1f))
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) AccentNavy else SurfaceContainerHighLight)
                                    .clickable {
                                        if (currentTab != tab) {
                                            requestNavigation { currentTab = tab }
                                        }
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = if (isSelected) SurfaceWhite else PrimaryNavy,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = label,
                                        fontSize = if (isMobile) 13.sp else 14.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) SurfaceWhite else PrimaryNavy,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                    if (tab == CustomerPortalTab.VEGGIE_ORDER && hasUnsavedChanges) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = ColorWarningAmber,
                                            modifier = Modifier.size(8.dp)
                                        ) {}
                                    }
                                }
                            }
                        }
                    }
                }

                // Active Tab Content
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (currentTab) {
                        CustomerPortalTab.VEGGIE_ORDER -> {
                            CustomerVeggieOrderTabContent(
                                veggieViewModel = veggieViewModel,
                                unitTypesById = unitTypesById,
                                currentLanguage = currentLanguage,
                                isMobile = isMobile
                            )
                        }
                        CustomerPortalTab.VEGGIE_HISTORY -> {
                            CustomerVeggieHistoryTabContent(
                                veggieViewModel = veggieViewModel,
                                currentLanguage = currentLanguage,
                                isMobile = isMobile
                            )
                        }
                        CustomerPortalTab.STORE_HISTORY -> {
                            TransactionHistoryScreen(
                                viewModel = historyViewModel,
                                user = user,
                                settings = settings,
                                onContinueShopping = { requestNavigation { currentTab = CustomerPortalTab.VEGGIE_ORDER } },
                                onLogout = { requestNavigation(onLogout) },
                                onUserUpdated = onUserUpdated,
                                isNetworkPortal = true,
                                showHeaderAndBalance = false
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSettingsModal) {
        UserSettingsModalDialog(
            user = user,
            pinInput = pinInput,
            selectedLanguage = selectedLanguage,
            selectedSecondaryCurrency = selectedSecondaryCurrency,
            selectedAvatar = selectedAvatar,
            selectedRequirePinOnKiosk = selectedRequirePinOnKiosk,
            isNetworkPortal = true,
            onPinInputChange = { historyViewModel.updatePinInput(it) },
            onLanguageSelect = { historyViewModel.updateLanguage(it) },
            onSecondaryCurrencySelect = { historyViewModel.updateSecondaryCurrency(it) },
            onAvatarSelect = { historyViewModel.updateAvatar(it) },
            onRequirePinOnKioskChange = { historyViewModel.updateRequirePinOnKiosk(it) },
            onDismiss = { historyViewModel.closeSettingsModal() },
            onSave = { removePin ->
                historyViewModel.saveUserSettings(user, removePin = removePin) { updated ->
                    LanguageManager.setLanguage(updated.language)
                    onUserUpdated(updated)
                }
            }
        )
    }

    val detailProduct = selectedProductForDetail
    if (detailProduct != null) {
        VeggieProductDetailModal(
            product = detailProduct,
            unitType = unitTypesById[detailProduct.unitTypeId],
            onDismiss = { veggieViewModel.closeProductDetail() }
        )
    }

    if (showUnsavedDialog) {
        AlertDialog(
            onDismissRequest = {
                showUnsavedDialog = false
                pendingNavigation = null
            },
            title = {
                Text(
                    text = strings.unsavedChangesTitle,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryNavy,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = strings.unsavedChangesMessage,
                    fontSize = 14.sp,
                    color = PrimaryNavy
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        veggieViewModel.saveOrder {
                            showUnsavedDialog = false
                            val nav = pendingNavigation
                            pendingNavigation = null
                            nav?.invoke()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorSuccessEmerald),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(strings.btnSaveAndProceed, color = SurfaceWhite, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            showUnsavedDialog = false
                            pendingNavigation = null
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(strings.btnKeepEditing)
                    }

                    Button(
                        onClick = {
                            showUnsavedDialog = false
                            veggieViewModel.loadData(preserveDraftIfSameRound = false)
                            val nav = pendingNavigation
                            pendingNavigation = null
                            nav?.invoke()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ColorDangerCrimson),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(strings.btnDiscardChanges, color = SurfaceWhite, fontWeight = FontWeight.Bold)
                    }
                }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = SurfaceWhite
        )
    }
}

private enum class MobileVeggieSubTab {
    CATALOG,
    CART
}

@Composable
private fun CustomerVeggieOrderTabContent(
    veggieViewModel: CustomerVeggieViewModel,
    unitTypesById: Map<String, VeggieUnitType>,
    currentLanguage: Language,
    isMobile: Boolean
) {
    val strings = I18n.current
    val activeRound by veggieViewModel.activeRound.collectAsState()
    val products by veggieViewModel.products.collectAsState()
    val draftQuantitiesScaled by veggieViewModel.draftQuantitiesScaled.collectAsState()
    val previousRoundQuantitiesScaled by veggieViewModel.previousRoundQuantitiesScaled.collectAsState()
    val searchQuery by veggieViewModel.productSearchQuery.collectAsState()
    val isSavingOrder by veggieViewModel.isSavingOrder.collectAsState()
    val orderSavedSuccessBanner by veggieViewModel.orderSavedSuccessBanner.collectAsState()
    val errorMessage by veggieViewModel.errorMessage.collectAsState()
    val hasUnsavedChanges by veggieViewModel.hasUnsavedOrderChanges.collectAsState()

    var selectedProductForAmountDialog by remember { mutableStateOf<VeggieProduct?>(null) }
    var mobileSubTab by remember { mutableStateOf(MobileVeggieSubTab.CATALOG) }

    val round = activeRound
    if (round == null) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = SurfaceWhite,
            shadowElevation = 1.dp,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Eco,
                    contentDescription = null,
                    tint = TextSecondaryMuted,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = strings.noActiveRoundTitle,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryNavy,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = strings.noActiveRoundDesc,
                    fontSize = 14.sp,
                    color = TextSecondaryMuted,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    val weekInfo = remember(round.openedAt) { IsoWeekUtils.fromEpochMillis(round.openedAt) }
    val isRoundOpen = round.status == VeggieRoundStatus.OPEN
    val decimalSep = if (currentLanguage == Language.EN) '.' else ','

    val filteredProducts = remember(products, searchQuery) {
        if (searchQuery.isBlank()) {
            products
        } else {
            products.filter {
                it.name.containsIgnoreAccents(searchQuery) ||
                    (it.description?.containsIgnoreAccents(searchQuery) == true)
            }
        }
    }

    val cartProducts = remember(draftQuantitiesScaled, products) {
        products.filter { (draftQuantitiesScaled[it.id] ?: 0L) > 0L }
    }

    val hasPreviousRoundItems = remember(previousRoundQuantitiesScaled) {
        previousRoundQuantitiesScaled.any { it.value > 0L }
    }

    val estimatedSubtotalCents = remember(draftQuantitiesScaled, products) {
        veggieViewModel.calculateEstimatedItemsSubtotalCents()
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (isMobile) {
            // Mobile Sub-Tab Switcher
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = SurfaceWhite,
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val tabs = listOf(
                        MobileVeggieSubTab.CATALOG to strings.mobileSubTabCatalog,
                        MobileVeggieSubTab.CART to strings.mobileSubTabCart(cartProducts.size)
                    )
                    tabs.forEach { (subTab, label) ->
                        val isSelected = mobileSubTab == subTab
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) AccentNavy else SurfaceContainerHighLight)
                                .clickable { mobileSubTab = subTab }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) SurfaceWhite else PrimaryNavy
                            )
                        }
                    }
                }
            }
        }

        // Content Area: Split 2 columns on desktop/tablet, single screen on mobile
        if (isMobile) {
            when (mobileSubTab) {
                MobileVeggieSubTab.CATALOG -> {
                    VeggieCatalogPanel(
                        filteredProducts = filteredProducts,
                        allProductsCount = products.size,
                        searchQuery = searchQuery,
                        unitTypesById = unitTypesById,
                        draftQuantitiesScaled = draftQuantitiesScaled,
                        previousRoundQuantitiesScaled = previousRoundQuantitiesScaled,
                        isRoundOpen = isRoundOpen,
                        decimalSep = decimalSep,
                        isMobile = true,
                        weekInfo = weekInfo,
                        roundStatus = round.status,
                        errorMessage = errorMessage,
                        onClearError = { veggieViewModel.clearError() },
                        onQueryChange = { veggieViewModel.updateProductSearchQuery(it) },
                        onOpenDetail = { veggieViewModel.openProductDetail(it) },
                        onSelectProduct = { selectedProductForAmountDialog = it },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                MobileVeggieSubTab.CART -> {
                    VeggieCartPanel(
                        cartProducts = cartProducts,
                        unitTypesById = unitTypesById,
                        draftQuantitiesScaled = draftQuantitiesScaled,
                        isRoundOpen = isRoundOpen,
                        decimalSep = decimalSep,
                        estimatedSubtotalCents = estimatedSubtotalCents,
                        hasUnsavedChanges = hasUnsavedChanges,
                        isSavingOrder = isSavingOrder,
                        hasPreviousRoundItems = hasPreviousRoundItems,
                        onFillFromLastWeek = { veggieViewModel.copyPreviousRoundOrder() },
                        onEditQuantity = { selectedProductForAmountDialog = it },
                        onRemoveProduct = { veggieViewModel.updateProductQuantity(it.id, 0L) },
                        onSaveOrder = { veggieViewModel.saveOrder() },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        } else {
            // Desktop 2-column Row: Left = Products, Right = Cart
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                VeggieCatalogPanel(
                    filteredProducts = filteredProducts,
                    allProductsCount = products.size,
                    searchQuery = searchQuery,
                    unitTypesById = unitTypesById,
                    draftQuantitiesScaled = draftQuantitiesScaled,
                    previousRoundQuantitiesScaled = previousRoundQuantitiesScaled,
                    isRoundOpen = isRoundOpen,
                    decimalSep = decimalSep,
                    isMobile = false,
                    weekInfo = weekInfo,
                    roundStatus = round.status,
                    errorMessage = errorMessage,
                    onClearError = { veggieViewModel.clearError() },
                    onQueryChange = { veggieViewModel.updateProductSearchQuery(it) },
                    onOpenDetail = { veggieViewModel.openProductDetail(it) },
                    onSelectProduct = { selectedProductForAmountDialog = it },
                    modifier = Modifier
                        .weight(1.3f)
                        .fillMaxHeight()
                )

                VeggieCartPanel(
                    cartProducts = cartProducts,
                    unitTypesById = unitTypesById,
                    draftQuantitiesScaled = draftQuantitiesScaled,
                    isRoundOpen = isRoundOpen,
                    decimalSep = decimalSep,
                    estimatedSubtotalCents = estimatedSubtotalCents,
                    hasUnsavedChanges = hasUnsavedChanges,
                    isSavingOrder = isSavingOrder,
                    hasPreviousRoundItems = hasPreviousRoundItems,
                    onFillFromLastWeek = { veggieViewModel.copyPreviousRoundOrder() },
                    onEditQuantity = { selectedProductForAmountDialog = it },
                    onRemoveProduct = { veggieViewModel.updateProductQuantity(it.id, 0L) },
                    onSaveOrder = { veggieViewModel.saveOrder() },
                    modifier = Modifier
                        .weight(0.9f)
                        .fillMaxHeight()
                )
            }
        }
    }

    val editingProduct = selectedProductForAmountDialog
    if (editingProduct != null) {
        VeggieAmountInputDialog(
            product = editingProduct,
            unitType = unitTypesById[editingProduct.unitTypeId],
            initialScaledQuantity = draftQuantitiesScaled[editingProduct.id] ?: 0L,
            decimalSeparator = decimalSep,
            previousScaledQuantity = previousRoundQuantitiesScaled[editingProduct.id] ?: 0L,
            onDismiss = { selectedProductForAmountDialog = null },
            onSubmit = { newScaledQty ->
                veggieViewModel.updateProductQuantity(editingProduct.id, newScaledQty)
                selectedProductForAmountDialog = null
            }
        )
    }
}

@Composable
private fun VeggieCatalogPanel(
    filteredProducts: List<VeggieProduct>,
    allProductsCount: Int,
    searchQuery: String,
    unitTypesById: Map<String, VeggieUnitType>,
    draftQuantitiesScaled: Map<String, Long>,
    previousRoundQuantitiesScaled: Map<String, Long>,
    isRoundOpen: Boolean,
    decimalSep: Char,
    isMobile: Boolean,
    weekInfo: IsoWeekInfo,
    roundStatus: VeggieRoundStatus,
    errorMessage: String?,
    onClearError: () -> Unit,
    onQueryChange: (String) -> Unit,
    onOpenDetail: (VeggieProduct) -> Unit,
    onSelectProduct: (VeggieProduct) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = I18n.current

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Round header + Search Input
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = SurfaceWhite,
            shadowElevation = 1.dp,
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
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Eco,
                            contentDescription = null,
                            tint = ColorSuccessEmerald,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = strings.weekOfYearLabel(weekInfo.weekOfYear, weekInfo.isoYear),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryNavy
                        )
                    }

                    AdminStatusBadge(
                        text = when (roundStatus) {
                            VeggieRoundStatus.OPEN -> strings.roundStatusOpen
                            VeggieRoundStatus.CLOSED -> strings.roundStatusClosed
                            VeggieRoundStatus.SETTLED -> strings.roundStatusSettled
                        },
                        type = when (roundStatus) {
                            VeggieRoundStatus.OPEN -> AdminBadgeType.SUCCESS
                            VeggieRoundStatus.CLOSED -> AdminBadgeType.WARNING
                            VeggieRoundStatus.SETTLED -> AdminBadgeType.NAVY
                        }
                    )
                }

                if (!isRoundOpen) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = ColorWarningAmber.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, ColorWarningAmber.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = ColorWarningAmber,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = strings.veggieOrderClosedBanner,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = PrimaryNavy
                            )
                        }
                    }
                }

                SearchInputField(
                    query = searchQuery,
                    onQueryChange = onQueryChange,
                    placeholder = strings.searchVeggieProductsPlaceholder,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (errorMessage != null) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = ColorDangerCrimson.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, ColorDangerCrimson.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = errorMessage,
                        fontSize = 13.sp,
                        color = ColorDangerCrimson,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onClearError, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = ColorDangerCrimson, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = strings.productsCountText(filteredProducts.size, allProductsCount),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondaryMuted
            )
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 8.dp)
        ) {
            items(filteredProducts, key = { it.id }) { product ->
                val unitType = unitTypesById[product.unitTypeId]
                val currentQtyScaled = draftQuantitiesScaled[product.id] ?: 0L
                val prevQtyScaled = previousRoundQuantitiesScaled[product.id] ?: 0L

                CustomerVeggieProductCard(
                    product = product,
                    unitType = unitType,
                    quantityScaled = currentQtyScaled,
                    previousQuantityScaled = prevQtyScaled,
                    isEditable = isRoundOpen,
                    decimalSeparator = decimalSep,
                    isMobile = isMobile,
                    onOpenDetail = { onOpenDetail(product) },
                    onClickCard = { onSelectProduct(product) }
                )
            }
        }
    }
}

@Composable
private fun VeggieCartPanel(
    cartProducts: List<VeggieProduct>,
    unitTypesById: Map<String, VeggieUnitType>,
    draftQuantitiesScaled: Map<String, Long>,
    isRoundOpen: Boolean,
    decimalSep: Char,
    estimatedSubtotalCents: Long,
    hasUnsavedChanges: Boolean,
    isSavingOrder: Boolean,
    hasPreviousRoundItems: Boolean,
    onFillFromLastWeek: () -> Unit,
    onEditQuantity: (VeggieProduct) -> Unit,
    onRemoveProduct: (VeggieProduct) -> Unit,
    onSaveOrder: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = I18n.current
    val hasPricelessItemsInCart = remember(cartProducts) {
        cartProducts.any { it.estimatedPriceCents == null }
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = SurfaceWhite,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, DividerBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Cart Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ShoppingCart,
                        contentDescription = null,
                        tint = AccentNavy,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = strings.veggieCartTitle,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryNavy
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SurfaceContainerHighLight
                    ) {
                        Text(
                            text = "${cartProducts.size}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryNavy,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                if (hasUnsavedChanges) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = ColorWarningAmber.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, ColorWarningAmber.copy(alpha = 0.45f))
                    ) {
                        Text(
                            text = strings.veggieUnsavedBadge,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorWarningAmber,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = DividerBorder)

            // Cart Items
            if (cartProducts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Eco,
                            contentDescription = null,
                            tint = TextSecondaryMuted.copy(alpha = 0.4f),
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = strings.veggieCartEmptyMsg,
                            fontSize = 13.sp,
                            color = TextSecondaryMuted,
                            textAlign = TextAlign.Center
                        )

                        if (hasPreviousRoundItems && isRoundOpen) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Button(
                                onClick = onFillFromLastWeek,
                                colors = ButtonDefaults.buttonColors(containerColor = AccentNavy),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription = null,
                                        tint = SurfaceWhite,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = strings.btnFillFromLastWeek,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SurfaceWhite
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(cartProducts, key = { it.id }) { product ->
                        val qtyScaled = draftQuantitiesScaled[product.id] ?: 0L
                        val unitType = unitTypesById[product.unitTypeId]
                        VeggieCartLineItemRow(
                            product = product,
                            unitType = unitType,
                            quantityScaled = qtyScaled,
                            isEditable = isRoundOpen,
                            decimalSeparator = decimalSep,
                            onEditQuantity = { onEditQuantity(product) },
                            onRemove = { onRemoveProduct(product) }
                        )
                    }
                }
            }

            HorizontalDivider(color = DividerBorder)

            // Footer: Totals + Save Button
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${strings.veggieEstimatedTotalLabel}:",
                        fontSize = 13.sp,
                        color = TextSecondaryMuted,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = if (hasPricelessItemsInCart) "> ${Formatting.formatBrl(estimatedSubtotalCents)}" else Formatting.formatBrl(estimatedSubtotalCents),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryNavy
                    )
                }

                if (hasPricelessItemsInCart) {
                    Text(
                        text = strings.veggiePricelessItemsNotice,
                        fontSize = 11.sp,
                        color = TextSecondaryMuted,
                        lineHeight = 14.sp
                    )
                }

                if (isRoundOpen) {
                    val canSave = hasUnsavedChanges && !isSavingOrder
                    Button(
                        onClick = onSaveOrder,
                        enabled = canSave,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ColorSuccessEmerald,
                            disabledContainerColor = SurfaceContainerHighLight,
                            disabledContentColor = TextSecondaryMuted
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        if (isSavingOrder) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = SurfaceWhite
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        } else {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = if (canSave) SurfaceWhite else TextSecondaryMuted,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = strings.saveVeggieOrderBtn,
                            color = if (canSave) SurfaceWhite else TextSecondaryMuted,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VeggieCartLineItemRow(
    product: VeggieProduct,
    unitType: VeggieUnitType?,
    quantityScaled: Long,
    isEditable: Boolean,
    decimalSeparator: Char,
    onEditQuantity: () -> Unit,
    onRemove: () -> Unit
) {
    val allowDecimals = unitType?.allowDecimals ?: false
    val unitName = unitType?.name ?: "Unit"
    val qtyFormatted = VeggieQuantityScaling.formatScaledQuantity(quantityScaled, allowDecimals, decimalSeparator)

    val lineTotalCents = if (product.estimatedPriceCents != null) {
        ((quantityScaled.toDouble() / VEGGIE_QTY_SCALE.toDouble()) * product.estimatedPriceCents.toDouble()).roundToLong()
    } else 0L

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = SurfaceContainerLight,
        border = BorderStroke(1.dp, DividerBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = product.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryNavy,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (product.estimatedPriceCents != null) {
                        "~ ${Formatting.formatBrl(product.estimatedPriceCents)} / $unitName"
                    } else {
                        unitName
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondaryMuted
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Number Button (click re-opens amount dialog with rounded press)
                Surface(
                    onClick = onEditQuantity,
                    enabled = isEditable,
                    shape = RoundedCornerShape(8.dp),
                    color = SurfaceWhite,
                    border = BorderStroke(1.dp, DividerBorder),
                    modifier = Modifier
                        .height(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .pointerHoverIcon(if (isEditable) PointerIcon.Hand else PointerIcon.Default)
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$qtyFormatted $unitName",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryNavy
                        )
                    }
                }

                // Disconnected Trashcan Button to remove (with rounded press)
                if (isEditable) {
                    Surface(
                        onClick = onRemove,
                        shape = RoundedCornerShape(8.dp),
                        color = SurfaceWhite,
                        border = BorderStroke(1.dp, DividerBorder),
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .pointerHoverIcon(PointerIcon.Hand)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                tint = ColorDangerCrimson,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }

                Text(
                    text = Formatting.formatBrl(lineTotalCents),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryNavy,
                    modifier = Modifier.widthIn(min = 52.dp),
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

@Composable
private fun CustomerVeggieProductCard(
    product: VeggieProduct,
    unitType: VeggieUnitType?,
    quantityScaled: Long,
    previousQuantityScaled: Long,
    isEditable: Boolean,
    decimalSeparator: Char,
    isMobile: Boolean,
    onOpenDetail: () -> Unit,
    onClickCard: () -> Unit
) {
    val strings = I18n.current
    val allowDecimals = unitType?.allowDecimals ?: false
    val unitName = unitType?.name ?: "Unit"
    val isOrdered = quantityScaled > 0L

    Card(
        onClick = {
            if (isEditable) onClickCard()
        },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = SurfaceWhite
        ),
        border = BorderStroke(1.dp, DividerBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail (clickable ONLY to open detail modal)
            VeggieProductImage(
                productId = product.id,
                imageFileName = product.imageFileName,
                fullSize = false,
                contentDescription = product.name,
                shape = RoundedCornerShape(10.dp),
                placeholderIconSize = 28.dp,
                modifier = Modifier.size(if (isMobile) 64.dp else 76.dp),
                onClick = onOpenDetail
            )

            // Middle: Details
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = product.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryNavy,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    AdminStatusBadge(
                        text = unitName,
                        type = AdminBadgeType.NAVY
                    )
                }

                Text(
                    text = if (product.estimatedPriceCents != null) {
                        "~ ${Formatting.formatBrl(product.estimatedPriceCents)} / $unitName"
                    } else {
                        strings.veggieEstimatedPriceUnknown
                    },
                    fontSize = 13.sp,
                    color = if (product.estimatedPriceCents != null) PrimaryNavy else TextSecondaryMuted,
                    fontWeight = FontWeight.Medium
                )

                if (!product.description.isNullOrBlank()) {
                    Text(
                        text = product.description,
                        fontSize = 12.sp,
                        color = TextSecondaryMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Right side: Badges (Added X and/or Last Week Order)
            if (isOrdered || previousQuantityScaled > 0L) {
                Column(
                    modifier = Modifier.width(if (isMobile) 120.dp else 155.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (isOrdered) {
                        val currentFormatted = VeggieQuantityScaling.formatScaledQuantity(
                            quantityScaled,
                            allowDecimals,
                            decimalSeparator
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = ColorSuccessEmerald.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, ColorSuccessEmerald.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth().height(28.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = strings.addedOrderBadge(currentFormatted, unitName),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ColorSuccessEmerald,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    if (previousQuantityScaled > 0L) {
                        val formattedPrev = VeggieQuantityScaling.formatScaledQuantity(
                            previousQuantityScaled,
                            allowDecimals,
                            decimalSeparator
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = SurfaceContainerHighLight,
                            border = BorderStroke(1.dp, DividerBorder),
                            modifier = Modifier.fillMaxWidth().height(28.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = strings.lastOrderBadge(formattedPrev, unitName),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = PrimaryNavy,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
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
private fun CustomerVeggieHistoryTabContent(
    veggieViewModel: CustomerVeggieViewModel,
    currentLanguage: Language,
    isMobile: Boolean
) {
    val strings = I18n.current
    val historyItems by veggieViewModel.historyItems.collectAsState()
    val historySearchQuery by veggieViewModel.historySearchQuery.collectAsState()
    val decimalSep = if (currentLanguage == Language.EN) '.' else ','

    val filteredHistory = remember(historyItems, historySearchQuery) {
        veggieViewModel.filteredHistoryItems()
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = SurfaceWhite,
            shadowElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(modifier = Modifier.padding(12.dp)) {
                SearchInputField(
                    query = historySearchQuery,
                    onQueryChange = { veggieViewModel.updateHistorySearchQuery(it) },
                    placeholder = strings.searchVeggieHistoryPlaceholder,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (filteredHistory.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = SurfaceWhite,
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = strings.noVeggieHistoryMsg,
                        fontSize = 14.sp,
                        color = TextSecondaryMuted
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(filteredHistory, key = { it.round.id }) { item ->
                    CustomerVeggieHistoryRoundCard(
                        item = item,
                        searchQuery = historySearchQuery,
                        currentLanguage = currentLanguage,
                        decimalSeparator = decimalSep,
                        isMobile = isMobile
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomerVeggieHistoryRoundCard(
    item: CustomerVeggieRoundHistoryItem,
    searchQuery: String,
    currentLanguage: Language,
    decimalSeparator: Char,
    isMobile: Boolean
) {
    val strings = I18n.current
    val round = item.round
    val weekInfo = remember(round.openedAt) { IsoWeekUtils.fromEpochMillis(round.openedAt) }
    val isSettled = round.status == VeggieRoundStatus.SETTLED

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = SurfaceWhite,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Card Header: Week of Year + Date + Status + Total
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = strings.weekOfYearLabel(weekInfo.weekOfYear, weekInfo.isoYear),
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
                        text = Formatting.formatTimestamp(round.settledAt ?: round.openedAt, currentLanguage),
                        fontSize = 12.sp,
                        color = TextSecondaryMuted
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (isSettled) strings.veggieFinalTotalLabel else strings.veggieEstimatedTotalLabel,
                        fontSize = 11.sp,
                        color = TextSecondaryMuted
                    )
                    Text(
                        text = Formatting.formatBrl(item.grandTotalCents),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryNavy
                    )
                }
            }

            HorizontalDivider(color = DividerBorder)

            // Itemized Entries
            item.entries.forEach { entry ->
                val matchesSearch = searchQuery.isNotBlank() &&
                    entry.veggieNameSnapshot.containsIgnoreAccents(searchQuery.trim())
                val orderedText = VeggieQuantityScaling.formatScaledQuantity(
                    entry.orderedQuantityScaled,
                    entry.allowDecimalsSnapshot,
                    decimalSeparator
                )
                val deliveredScaled = entry.actualQuantityScaled ?: entry.orderedQuantityScaled
                val deliveredText = VeggieQuantityScaling.formatScaledQuantity(
                    deliveredScaled,
                    entry.allowDecimalsSnapshot,
                    decimalSeparator
                )
                val unitPriceCents = entry.effectiveUnitPriceCents
                val lineTotalCents = entry.finalItemTotalCents ?: if (unitPriceCents != null) {
                    ((deliveredScaled.toDouble() / VEGGIE_QTY_SCALE.toDouble()) * unitPriceCents.toDouble()).roundToLong()
                } else 0L

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (matchesSearch) ColorWarningAmber.copy(alpha = 0.14f) else SurfaceWhite)
                        .padding(vertical = 4.dp, horizontal = if (matchesSearch) 6.dp else 0.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = entry.veggieNameSnapshot,
                            fontSize = 14.sp,
                            fontWeight = if (matchesSearch) FontWeight.Bold else FontWeight.Medium,
                            color = PrimaryNavy
                        )
                        val qtySummary = if (isSettled || entry.actualQuantityScaled != null) {
                            "${strings.colOrderedQty}: $orderedText ${entry.unitNameSnapshot}  •  ${strings.colActualQty}: $deliveredText ${entry.unitNameSnapshot}"
                        } else {
                            "${strings.colOrderedQty}: $orderedText ${entry.unitNameSnapshot}"
                        }
                        Text(
                            text = qtySummary + (if (unitPriceCents != null) "  •  ${Formatting.formatBrl(unitPriceCents)} / ${entry.unitNameSnapshot}" else ""),
                            fontSize = 12.sp,
                            color = TextSecondaryMuted
                        )
                    }

                    Text(
                        text = Formatting.formatBrl(lineTotalCents),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PrimaryNavy
                    )
                }
            }

            // Single Combined Fee Line (Buyer Operation Fee + Transport Fee share combined)
            if (item.combinedFeeShareCents > 0L) {
                HorizontalDivider(color = DividerBorder.copy(alpha = 0.6f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.veggieCombinedFeeLabel,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondaryMuted
                    )
                    Text(
                        text = Formatting.formatBrl(item.combinedFeeShareCents),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PrimaryNavy
                    )
                }
            }
        }
    }
}
