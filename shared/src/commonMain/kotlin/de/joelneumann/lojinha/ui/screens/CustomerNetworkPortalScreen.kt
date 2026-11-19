package de.joelneumann.lojinha.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.joelneumann.lojinha.domain.model.*
import de.joelneumann.lojinha.domain.repository.CustomerVeggieRoundHistoryItem
import de.joelneumann.lojinha.domain.service.VeggieSettlementCalculator
import de.joelneumann.lojinha.ui.components.admin.AdminStatusBadge
import de.joelneumann.lojinha.ui.components.admin.AdminBadgeType
import de.joelneumann.lojinha.ui.components.admin.users.UserTransactionsPdfExportDialog
import de.joelneumann.lojinha.ui.components.general.HeaderBar
import de.joelneumann.lojinha.ui.components.general.LogoutButton
import de.joelneumann.lojinha.ui.components.general.PaginationBar
import de.joelneumann.lojinha.ui.components.general.SearchInputField
import de.joelneumann.lojinha.ui.components.history.UserBalanceWarningBanner
import de.joelneumann.lojinha.ui.components.history.UserSettingsModalDialog
import de.joelneumann.lojinha.ui.components.shopping.UserBalanceHeader
import de.joelneumann.lojinha.ui.components.userselection.UserAvatar
import de.joelneumann.lojinha.ui.components.veggie.InlineVeggieQuantityField
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
import de.joelneumann.lojinha.ui.utils.filterAndRankVeggieProducts
import de.joelneumann.lojinha.ui.utils.loadVeggieImageBitmap
import de.joelneumann.lojinha.ui.utils.safeRequestFocus
import de.joelneumann.lojinha.ui.utils.setBrowserBeforeUnloadWarning
import de.joelneumann.lojinha.ui.utils.sortedByAccentInsensitive
import de.joelneumann.lojinha.ui.viewmodel.CustomerVeggieViewModel
import de.joelneumann.lojinha.ui.viewmodel.TransactionHistoryViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlin.math.roundToLong

private enum class VeggieFocusZone {
    SEARCH,
    PRODUCTS
}

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
    onLogout: () -> Unit,
    onExecuteCustomerPdfExport: ((user: User, fromTimestamp: Long?, toTimestamp: Long?, language: Language) -> Unit)? = null
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
    val showExportPdfModal by historyViewModel.showExportPdfModal.collectAsState()
    val exportPdfTransactions by historyViewModel.exportPdfTransactions.collectAsState()
    val isExportingPdf by historyViewModel.isExportingPdf.collectAsState()
    val exportPdfError by historyViewModel.exportPdfError.collectAsState()
    val pinInput by historyViewModel.pinInput.collectAsState()
    val selectedLanguage by historyViewModel.selectedLanguage.collectAsState()
    val selectedSecondaryCurrency by historyViewModel.selectedSecondaryCurrency.collectAsState()
    val selectedAvatar by historyViewModel.selectedAvatar.collectAsState()
    val selectedRequirePinOnKiosk by historyViewModel.selectedRequirePinOnKiosk.collectAsState()

    val selectedProductForDetail by veggieViewModel.selectedProductForDetail.collectAsState()
    val favoriteProductIds by veggieViewModel.favoriteProductIds.collectAsState()
    val unitTypes by veggieViewModel.unitTypes.collectAsState()
    val unitTypesById = remember(unitTypes) { unitTypes.associateBy { it.id } }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceContainerLight)
    ) {
        val isMobile = maxWidth < MOBILE_BREAKPOINT
        val isKeyboardOpenMobile = isMobile && maxHeight < 480.dp

        Column(modifier = Modifier.fillMaxSize()) {
            if (!isKeyboardOpenMobile) {
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

                // Tabs for Veggie Order, History and Account directly under header, attached with same SurfaceWhite background
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = SurfaceWhite,
                    shadowElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = if (isMobile) 8.dp else 16.dp, vertical = 6.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val tabs = listOf(
                            Triple(CustomerPortalTab.VEGGIE_ORDER, Icons.Default.Eco, strings.customerPortalTabOrder),
                            Triple(CustomerPortalTab.VEGGIE_HISTORY, Icons.Default.History, strings.customerPortalTabVeggieHistory),
                            Triple(CustomerPortalTab.STORE_HISTORY, Icons.Default.Person, strings.account)
                        )
                        tabs.forEach { (tab, icon, label) ->
                            val isSelected = currentTab == tab
                            Surface(
                                onClick = {
                                    if (currentTab != tab) {
                                        requestNavigation { currentTab = tab }
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) AccentNavy else SurfaceContainerHighLight,
                                modifier = Modifier.clip(RoundedCornerShape(8.dp))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = if (isMobile) 10.dp else 14.dp, vertical = 8.dp),
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
                                        fontSize = if (isMobile) 12.sp else 13.sp,
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
            }

            // Active Tab Content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (currentTab) {
                    CustomerPortalTab.VEGGIE_ORDER -> {
                        CustomerVeggieOrderTabContent(
                            veggieViewModel = veggieViewModel,
                            unitTypesById = unitTypesById,
                            currentLanguage = currentLanguage,
                            isMobile = isMobile,
                            isKeyboardOpenMobile = isKeyboardOpenMobile,
                            user = user
                        )
                    }
                    CustomerPortalTab.VEGGIE_HISTORY -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(SurfaceContainerLight)
                                .padding(if (isMobile) 10.dp else ScreenPadding)
                        ) {
                            CustomerVeggieHistoryTabContent(
                                veggieViewModel = veggieViewModel,
                                currentLanguage = currentLanguage,
                                isMobile = isMobile
                            )
                        }
                    }
                    CustomerPortalTab.STORE_HISTORY -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(SurfaceContainerLight)
                                .padding(if (isMobile) 10.dp else ScreenPadding),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Top Row: UserBalanceHeader (Name Card) + User Settings Button next to it
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
            isFavorite = detailProduct.id in favoriteProductIds,
            onToggleFavorite = { veggieViewModel.toggleFavorite(detailProduct.id) },
            onDismiss = { veggieViewModel.closeProductDetail() }
        )
    }

    if (showExportPdfModal) {
        UserTransactionsPdfExportDialog(
            user = user,
            userTransactions = exportPdfTransactions,
            defaultSaveDirectory = "Downloads",
            isExporting = isExportingPdf,
            errorMessage = exportPdfError,
            hideFolderPicker = true,
            onExportPdf = { _, fromTimestamp, toTimestamp, language ->
                onExecuteCustomerPdfExport?.invoke(user, fromTimestamp, toTimestamp, language)
            },
            onDismiss = { historyViewModel.closeExportPdfModal() }
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
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
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
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(strings.btnSaveAndProceed, color = SurfaceWhite, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = {
                            showUnsavedDialog = false
                            pendingNavigation = null
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(strings.btnKeepEditing)
                    }

                    Button(
                        onClick = {
                            showUnsavedDialog = false
                            veggieViewModel.discardUnsavedChanges()
                            val nav = pendingNavigation
                            pendingNavigation = null
                            nav?.invoke()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ColorDangerCrimson),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(strings.btnDiscardChanges, color = SurfaceWhite, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = null,
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
    isMobile: Boolean,
    isKeyboardOpenMobile: Boolean = false,
    user: User
) {
    val strings = I18n.current
    val activeRound by veggieViewModel.activeRound.collectAsState()
    val products by veggieViewModel.products.collectAsState()
    val inactiveOrderedProducts by veggieViewModel.inactiveOrderedProducts.collectAsState()
    val draftQuantitiesScaled by veggieViewModel.draftQuantitiesScaled.collectAsState()
    val savedQuantitiesScaled by veggieViewModel.savedQuantitiesScaled.collectAsState()
    val previousRoundQuantitiesScaled by veggieViewModel.previousRoundQuantitiesScaled.collectAsState()
    val searchQuery by veggieViewModel.productSearchQuery.collectAsState()
    val filterFavoritesOnly by veggieViewModel.filterFavoritesOnly.collectAsState()
    val favoriteProductIds by veggieViewModel.favoriteProductIds.collectAsState()
    val isSavingOrder by veggieViewModel.isSavingOrder.collectAsState()
    val errorMessage by veggieViewModel.errorMessage.collectAsState()
    val hasUnsavedChanges by veggieViewModel.hasUnsavedOrderChanges.collectAsState()
    val filterInOrderOnly by veggieViewModel.filterInOrderOnly.collectAsState()

    var selectedProductForAmountDialog by remember { mutableStateOf<VeggieProduct?>(null) }
    var mobileSubTab by remember { mutableStateOf(MobileVeggieSubTab.CATALOG) }

    val round = activeRound
    if (round == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SurfaceContainerLight)
                .padding(if (isMobile) 10.dp else ScreenPadding)
        ) {
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
        }
        return
    }

    val weekInfo = remember(round.openedAt) { IsoWeekUtils.fromEpochMillis(round.openedAt) }
    val isRoundOpen = round.status == VeggieRoundStatus.OPEN
    val decimalSep = Formatting.decimalSeparator(currentLanguage)

    val filteredProducts = remember(
        products,
        searchQuery,
        filterFavoritesOnly,
        favoriteProductIds,
        filterInOrderOnly,
        if (filterInOrderOnly) draftQuantitiesScaled else Unit,
        unitTypesById
    ) {
        val base = if (!filterFavoritesOnly && !filterInOrderOnly) {
            products
        } else {
            products.filter { product ->
                val matchesFavorite = filterFavoritesOnly && product.id in favoriteProductIds
                val matchesInOrder = filterInOrderOnly && (draftQuantitiesScaled[product.id] ?: 0L) > 0L
                matchesFavorite || matchesInOrder
            }
        }
        if (searchQuery.isBlank()) {
            base
        } else {
            base.filterAndRankVeggieProducts(
                query = searchQuery,
                unitTypesById = unitTypesById
            )
        }
    }

    val cartProducts = remember(draftQuantitiesScaled, products, inactiveOrderedProducts) {
        (products + inactiveOrderedProducts)
            .filter { (draftQuantitiesScaled[it.id] ?: 0L) > 0L }
            .sortedByAccentInsensitive { it.name }
    }

    val hasPreviousRoundItems = remember(previousRoundQuantitiesScaled) {
        previousRoundQuantitiesScaled.any { it.value > 0L }
    }

    val estimatedSubtotalCents = remember(draftQuantitiesScaled, products, inactiveOrderedProducts) {
        veggieViewModel.calculateEstimatedItemsSubtotalCents()
    }

    val searchFocusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()
    val productListState = rememberLazyListState()
    val cartListState = rememberLazyListState()

    var activeZone by remember { mutableStateOf(VeggieFocusZone.SEARCH) }
    var highlightedProductIndex by remember(filteredProducts, searchQuery) {
        mutableStateOf(if (searchQuery.isNotBlank() && filteredProducts.isNotEmpty()) 0 else -1)
    }
    var pendingFocusProductId by remember { mutableStateOf<String?>(null) }

    fun navigateToProduct(index: Int) {
        if (filteredProducts.isEmpty()) return
        val targetIndex = index.coerceIn(0, filteredProducts.lastIndex)
        val hasHeaderItem = isMobile && searchQuery.isBlank()
        val listIndex = if (hasHeaderItem) targetIndex + 1 else targetIndex
        activeZone = VeggieFocusZone.PRODUCTS
        highlightedProductIndex = targetIndex
        coroutineScope.launch {
            if (isMobile) {
                productListState.scrollToItem(listIndex)
            } else {
                productListState.animateScrollToItem(listIndex)
            }
        }
    }

    fun navigateToSearch() {
        activeZone = VeggieFocusZone.SEARCH
        highlightedProductIndex = if (searchQuery.isNotBlank() && filteredProducts.isNotEmpty()) 0 else -1
        coroutineScope.launch {
            searchFocusRequester.safeRequestFocus()
        }
    }

    fun selectProductFromCart(product: VeggieProduct) {
        if (!product.isActive || product.isDeleted) {
            selectedProductForAmountDialog = product
            return
        }
        val wasOnCatalog = !isMobile || mobileSubTab == MobileVeggieSubTab.CATALOG
        if (isMobile && mobileSubTab != MobileVeggieSubTab.CATALOG) {
            mobileSubTab = MobileVeggieSubTab.CATALOG
        }
        val currentIdx = filteredProducts.indexOfFirst { it.id == product.id }
        if (currentIdx >= 0 && wasOnCatalog) {
            navigateToProduct(currentIdx)
        } else {
            if (currentIdx < 0) {
                if (searchQuery.isNotBlank()) {
                    veggieViewModel.updateProductSearchQuery("")
                }
                if (filterFavoritesOnly) {
                    veggieViewModel.toggleFilterFavoritesOnly()
                }
            }
            pendingFocusProductId = product.id
        }
    }

    LaunchedEffect(filteredProducts) {
        if (highlightedProductIndex >= filteredProducts.size) {
            highlightedProductIndex = -1
        }
    }

    LaunchedEffect(searchQuery) {
        if (filteredProducts.isNotEmpty()) {
            productListState.scrollToItem(0)
        }
    }

    LaunchedEffect(filteredProducts, pendingFocusProductId, mobileSubTab) {
        val targetId = pendingFocusProductId ?: return@LaunchedEffect
        if (isMobile && mobileSubTab != MobileVeggieSubTab.CATALOG) return@LaunchedEffect
        val targetIndex = filteredProducts.indexOfFirst { it.id == targetId }
        if (targetIndex >= 0) {
            pendingFocusProductId = null
            navigateToProduct(targetIndex)
        }
    }

    LaunchedEffect(Unit) {
        if (!isMobile) {
            searchFocusRequester.safeRequestFocus()
        }
    }

    LaunchedEffect(products, isMobile) {
        if (!isMobile || products.isEmpty()) return@LaunchedEffect
        // Let initial visible items compose and draw first before warming up remaining thumbnails.
        delay(350L)
        for (product in products) {
            val fileName = product.imageFileName
            if (!fileName.isNullOrBlank()) {
                loadVeggieImageBitmap(product.id, fileName, fullSize = false)
                yield()
            }
        }
    }

    LaunchedEffect(isKeyboardOpenMobile, highlightedProductIndex, activeZone) {
        if (isMobile && activeZone == VeggieFocusZone.PRODUCTS && highlightedProductIndex in filteredProducts.indices) {
            if (!isKeyboardOpenMobile) {
                delay(120L)
            }
            val hasHeaderItem = searchQuery.isBlank()
            val listIndex = if (hasHeaderItem) highlightedProductIndex + 1 else highlightedProductIndex
            val visibleItem = productListState.layoutInfo.visibleItemsInfo.find { it.index == listIndex }
            val viewportEnd = productListState.layoutInfo.viewportEndOffset
            val isFullyVisible = visibleItem != null &&
                visibleItem.offset >= 0 &&
                (visibleItem.offset + visibleItem.size) <= viewportEnd
            if (!isFullyVisible) {
                productListState.scrollToItem(listIndex)
            }
        }
    }

    val handleNavigationKey: (KeyEvent) -> Boolean = { event ->
        if (event.type != KeyEventType.KeyDown || activeZone != VeggieFocusZone.SEARCH) {
            false
        } else {
            when (event.key) {
                Key.DirectionDown -> {
                    if (filteredProducts.isNotEmpty()) {
                        val targetIdx = if (highlightedProductIndex in filteredProducts.indices) highlightedProductIndex else 0
                        navigateToProduct(targetIdx)
                        true
                    } else false
                }
                Key.Tab -> {
                    if (filteredProducts.isNotEmpty()) {
                        val targetIdx = if (event.isShiftPressed) {
                            filteredProducts.lastIndex
                        } else if (highlightedProductIndex in filteredProducts.indices) {
                            highlightedProductIndex
                        } else {
                            0
                        }
                        navigateToProduct(targetIdx)
                        true
                    } else false
                }
                Key.Enter, Key.NumPadEnter -> {
                    if (searchQuery.isNotBlank() && isRoundOpen && filteredProducts.isNotEmpty()) {
                        val targetIndex = if (highlightedProductIndex in filteredProducts.indices) highlightedProductIndex else 0
                        navigateToProduct(targetIndex)
                        true
                    } else false
                }
                else -> false
            }
        }
    }

    if (isMobile) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(SurfaceContainerLight)
                .padding(if (isKeyboardOpenMobile) 6.dp else 10.dp)
                .onPreviewKeyEvent(handleNavigationKey),
            verticalArrangement = Arrangement.spacedBy(if (isKeyboardOpenMobile) 6.dp else 8.dp)
        ) {
            if (!isKeyboardOpenMobile) {
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
                                    .padding(vertical = 8.dp),
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

            // Content Area: Catalog or Cart
            when (mobileSubTab) {
                MobileVeggieSubTab.CATALOG -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
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
                            isMobile = true,
                            isKeyboardOpenMobile = isKeyboardOpenMobile,
                            weekInfo = weekInfo,
                            roundStatus = round.status,
                            errorMessage = errorMessage,
                            user = user,
                            favoriteProductIds = favoriteProductIds,
                            filterFavoritesOnly = filterFavoritesOnly,
                            productListState = productListState,
                            searchFocusRequester = searchFocusRequester,
                            highlightedProductIndex = if (activeZone == VeggieFocusZone.PRODUCTS || searchQuery.isNotBlank()) highlightedProductIndex else -1,
                            isDialogActive = (selectedProductForAmountDialog != null),
                            isProductZoneActive = (activeZone == VeggieFocusZone.PRODUCTS),
                            onKeyDown = handleNavigationKey,
                            onNavigateToProduct = { idx -> navigateToProduct(idx) },
                            onReturnToSearch = { navigateToSearch() },
                            onCardFocused = { idx ->
                                activeZone = VeggieFocusZone.PRODUCTS
                                highlightedProductIndex = idx
                            },
                            onSearchFocused = { navigateToSearch() },
                            onClearError = { veggieViewModel.clearError() },
                            onQueryChange = { query ->
                                activeZone = VeggieFocusZone.SEARCH
                                veggieViewModel.updateProductSearchQuery(query)
                            },
                            onSearchSubmitted = {
                                if (searchQuery.isNotBlank() && isRoundOpen && filteredProducts.isNotEmpty()) {
                                    val targetIndex = if (highlightedProductIndex in filteredProducts.indices) highlightedProductIndex else 0
                                    navigateToProduct(targetIndex)
                                }
                            },
                            onOpenDetail = { veggieViewModel.openProductDetail(it) },
                            onToggleFavorite = { veggieViewModel.toggleFavorite(it) },
                            onToggleFavoriteFilter = { veggieViewModel.toggleFilterFavoritesOnly() },
                            filterInOrderOnly = filterInOrderOnly,
                            onToggleFilterInOrderOnly = { veggieViewModel.toggleFilterInOrderOnly() },
                            onUpdateProductQuantity = { id, qty -> veggieViewModel.updateProductQuantity(id, qty) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        )

                        if (isRoundOpen && !isKeyboardOpenMobile && (hasUnsavedChanges || cartProducts.isNotEmpty())) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = SurfaceWhite,
                                shadowElevation = 3.dp,
                                border = BorderStroke(1.dp, if (hasUnsavedChanges) ColorWarningAmber.copy(alpha = 0.5f) else DividerBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { mobileSubTab = MobileVeggieSubTab.CART }
                                            .padding(end = 8.dp)
                                    ) {
                                        Text(
                                            text = strings.mobileSubTabCart(cartProducts.size),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = TextSecondaryMuted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "~ ${Formatting.formatBrl(estimatedSubtotalCents)}",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = PrimaryNavy,
                                            maxLines = 1
                                        )
                                    }

                                    val canSave = hasUnsavedChanges && !isSavingOrder
                                    Button(
                                        onClick = { veggieViewModel.saveOrder() },
                                        enabled = canSave,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = ColorSuccessEmerald,
                                            disabledContainerColor = SurfaceContainerHighLight,
                                            disabledContentColor = TextSecondaryMuted
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                                    ) {
                                        if (isSavingOrder) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(14.dp),
                                                strokeWidth = 2.dp,
                                                color = SurfaceWhite
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                        }
                                        Text(
                                            text = if (hasUnsavedChanges || isSavingOrder) strings.saveVeggieOrderBtn else strings.veggieOrderAllSavedBtn,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (canSave) SurfaceWhite else TextSecondaryMuted,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                MobileVeggieSubTab.CART -> {
                    VeggieCartPanel(
                        cartProducts = cartProducts,
                        unitTypesById = unitTypesById,
                        draftQuantitiesScaled = draftQuantitiesScaled,
                        cartListState = cartListState,
                        isRoundOpen = isRoundOpen,
                        decimalSep = decimalSep,
                        estimatedSubtotalCents = estimatedSubtotalCents,
                        hasUnsavedChanges = hasUnsavedChanges,
                        isSavingOrder = isSavingOrder,
                        hasPreviousRoundItems = hasPreviousRoundItems,
                        errorMessage = errorMessage,
                        onClearError = { veggieViewModel.clearError() },
                        isDesktop = false,
                        onFillFromLastWeek = { veggieViewModel.copyPreviousRoundOrder() },
                        onEditQuantity = { selectProductFromCart(it) },
                        onRemoveProduct = { veggieViewModel.updateProductQuantity(it.id, 0L) },
                        onSaveOrder = { veggieViewModel.saveOrder() },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    } else {
        // Desktop 2-column Row: Left = Products (with ScreenPadding), Right = Cart (going all the way to header!)
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(SurfaceContainerLight)
                .onPreviewKeyEvent(handleNavigationKey)
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
                user = user,
                favoriteProductIds = favoriteProductIds,
                filterFavoritesOnly = filterFavoritesOnly,
                productListState = productListState,
                searchFocusRequester = searchFocusRequester,
                highlightedProductIndex = if (activeZone == VeggieFocusZone.PRODUCTS || searchQuery.isNotBlank()) highlightedProductIndex else -1,
                isDialogActive = (selectedProductForAmountDialog != null),
                isProductZoneActive = (activeZone == VeggieFocusZone.PRODUCTS),
                onKeyDown = handleNavigationKey,
                onNavigateToProduct = { idx -> navigateToProduct(idx) },
                onReturnToSearch = { navigateToSearch() },
                onCardFocused = { idx ->
                    activeZone = VeggieFocusZone.PRODUCTS
                    highlightedProductIndex = idx
                },
                onSearchFocused = { navigateToSearch() },
                onClearError = { veggieViewModel.clearError() },
                onQueryChange = { query ->
                    activeZone = VeggieFocusZone.SEARCH
                    veggieViewModel.updateProductSearchQuery(query)
                },
                onSearchSubmitted = {
                    if (searchQuery.isNotBlank() && isRoundOpen && filteredProducts.isNotEmpty()) {
                        val targetIndex = if (highlightedProductIndex in filteredProducts.indices) highlightedProductIndex else 0
                        navigateToProduct(targetIndex)
                    }
                },
                onOpenDetail = { veggieViewModel.openProductDetail(it) },
                onToggleFavorite = { veggieViewModel.toggleFavorite(it) },
                onToggleFavoriteFilter = { veggieViewModel.toggleFilterFavoritesOnly() },
                filterInOrderOnly = filterInOrderOnly,
                onToggleFilterInOrderOnly = { veggieViewModel.toggleFilterInOrderOnly() },
                onUpdateProductQuantity = { id, qty -> veggieViewModel.updateProductQuantity(id, qty) },
                modifier = Modifier
                    .weight(1.3f)
                    .fillMaxHeight()
                    .padding(ScreenPadding)
            )

            VeggieCartPanel(
                cartProducts = cartProducts,
                unitTypesById = unitTypesById,
                draftQuantitiesScaled = draftQuantitiesScaled,
                cartListState = cartListState,
                isRoundOpen = isRoundOpen,
                decimalSep = decimalSep,
                estimatedSubtotalCents = estimatedSubtotalCents,
                hasUnsavedChanges = hasUnsavedChanges,
                isSavingOrder = isSavingOrder,
                hasPreviousRoundItems = hasPreviousRoundItems,
                isDesktop = true,
                onFillFromLastWeek = { veggieViewModel.copyPreviousRoundOrder() },
                onEditQuantity = { selectProductFromCart(it) },
                onRemoveProduct = { veggieViewModel.updateProductQuantity(it.id, 0L) },
                onSaveOrder = { veggieViewModel.saveOrder() },
                modifier = Modifier
                    .weight(0.9f)
                    .fillMaxHeight()
            )
        }
    }

    val editingProduct = selectedProductForAmountDialog
    if (editingProduct != null) {
        val maxAllowedForInactive = if (!editingProduct.isActive || editingProduct.isDeleted) {
            savedQuantitiesScaled[editingProduct.id] ?: draftQuantitiesScaled[editingProduct.id]
        } else {
            null
        }
        VeggieAmountInputDialog(
            product = editingProduct,
            unitType = unitTypesById[editingProduct.unitTypeId],
            initialScaledQuantity = draftQuantitiesScaled[editingProduct.id] ?: 0L,
            decimalSeparator = decimalSep,
            previousScaledQuantity = previousRoundQuantitiesScaled[editingProduct.id] ?: 0L,
            maxAllowedScaledQuantity = maxAllowedForInactive,
            onDismiss = {
                val lastEditedId = editingProduct.id
                selectedProductForAmountDialog = null
                val idx = filteredProducts.indexOfFirst { it.id == lastEditedId }
                if (idx >= 0) navigateToProduct(idx)
            },
            onSubmit = { newScaledQty ->
                veggieViewModel.updateProductQuantity(editingProduct.id, newScaledQty)
                val lastEditedId = editingProduct.id
                selectedProductForAmountDialog = null
                val idx = filteredProducts.indexOfFirst { it.id == lastEditedId }
                if (idx >= 0) navigateToProduct(idx)
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
    isKeyboardOpenMobile: Boolean = false,
    weekInfo: IsoWeekInfo,
    roundStatus: VeggieRoundStatus,
    errorMessage: String?,
    user: User,
    favoriteProductIds: Set<String>,
    filterFavoritesOnly: Boolean,
    productListState: LazyListState,
    searchFocusRequester: FocusRequester,
    highlightedProductIndex: Int,
    isDialogActive: Boolean = false,
    filterInOrderOnly: Boolean = false,
    onToggleFilterInOrderOnly: () -> Unit = {},
    onUpdateProductQuantity: (String, Long) -> Unit = { _, _ -> },
    isProductZoneActive: Boolean = false,
    onKeyDown: ((KeyEvent) -> Boolean)? = null,
    onNavigateToProduct: ((Int) -> Unit)? = null,
    onReturnToSearch: (() -> Unit)? = null,
    onCardFocused: ((Int) -> Unit)? = null,
    onSearchFocused: (() -> Unit)? = null,
    onClearError: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSearchSubmitted: () -> Unit = {},
    onOpenDetail: (VeggieProduct) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onToggleFavoriteFilter: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = I18n.current

    val userRoundHeaderCard: @Composable () -> Unit = {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = SurfaceWhite,
            shadowElevation = if (isMobile) 0.dp else 1.dp,
            border = if (isMobile) BorderStroke(1.dp, DividerBorder) else null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (isMobile) 10.dp else 14.dp, vertical = if (isMobile) 8.dp else 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Avatar + Name
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (isMobile) 8.dp else 10.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    UserAvatar(
                        user = user,
                        modifier = Modifier.size(if (isMobile) 32.dp else 36.dp),
                        fontSize = if (isMobile) 13.sp else 15.sp
                    )
                    Text(
                        text = user.name,
                        fontSize = if (isMobile) 14.sp else 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryNavy,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Right: Week and Year standing before the Open Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (isMobile) 6.dp else 10.dp)
                ) {
                    Text(
                        text = strings.weekOfYearLabel(weekInfo.weekOfYear, weekInfo.isoYear),
                        fontSize = if (isMobile) 11.sp else 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondaryMuted
                    )

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
            }
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(if (isKeyboardOpenMobile) 6.dp else 10.dp)
    ) {
        if (!isMobile) {
            userRoundHeaderCard()
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

        // Search Bar Input + Filter by ★ + Filter by Cart
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SearchInputField(
                query = searchQuery,
                onQueryChange = onQueryChange,
                placeholder = strings.searchVeggieProductsPlaceholder,
                focusRequester = searchFocusRequester,
                onKeyDown = onKeyDown,
                onSearchSubmitted = onSearchSubmitted,
                onFocusChanged = { focusState ->
                    if (focusState.isFocused) {
                        onReturnToSearch?.invoke() ?: onSearchFocused?.invoke()
                    }
                },
                modifier = Modifier.weight(1f)
            )

            val filterBtnSize = if (isMobile) 48.dp else 56.dp
            Surface(
                onClick = onToggleFavoriteFilter,
                shape = RoundedCornerShape(12.dp),
                color = Color.Transparent,
                border = BorderStroke(
                    width = if (filterFavoritesOnly) 1.5.dp else 1.dp,
                    color = if (filterFavoritesOnly) ColorStarYellow else DividerBorder
                ),
                shadowElevation = 0.dp,
                modifier = Modifier
                    .size(filterBtnSize)
                    .focusProperties { canFocus = false }
                    .pointerHoverIcon(PointerIcon.Hand)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (filterFavoritesOnly) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = if (filterFavoritesOnly) strings.filterFavoritesActiveTooltip else strings.filterFavoritesTooltip,
                        tint = if (filterFavoritesOnly) ColorStarYellow else TextSecondaryMuted,
                        modifier = Modifier.size(if (isMobile) 22.dp else 26.dp)
                    )
                }
            }

            Surface(
                onClick = onToggleFilterInOrderOnly,
                shape = RoundedCornerShape(12.dp),
                color = Color.Transparent,
                border = BorderStroke(
                    width = if (filterInOrderOnly) 1.5.dp else 1.dp,
                    color = if (filterInOrderOnly) ColorSuccessEmerald else DividerBorder
                ),
                shadowElevation = 0.dp,
                modifier = Modifier
                    .size(filterBtnSize)
                    .focusProperties { canFocus = false }
                    .pointerHoverIcon(PointerIcon.Hand)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ShoppingCart,
                        contentDescription = strings.veggieFilterInOrder,
                        tint = if (filterInOrderOnly) ColorSuccessEmerald else TextSecondaryMuted,
                        modifier = Modifier.size(if (isMobile) 21.dp else 24.dp)
                    )
                }
            }
        }

        if (errorMessage != null) {
            VeggieErrorBanner(errorMessage = errorMessage, onClearError = onClearError)
        }

        if (!isKeyboardOpenMobile) {
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
        }

        if (filteredProducts.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = when {
                            filterInOrderOnly -> Icons.Default.ShoppingCart
                            filterFavoritesOnly -> Icons.Default.StarBorder
                            else -> Icons.Default.Search
                        },
                        contentDescription = null,
                        tint = TextSecondaryMuted,
                        modifier = Modifier.size(40.dp)
                    )
                    Text(
                        text = when {
                            filterInOrderOnly && searchQuery.isBlank() -> strings.veggieCartEmptyMsg
                            filterFavoritesOnly && searchQuery.isBlank() -> strings.noFavoriteProductsFound
                            else -> strings.noProductsFoundMatching(searchQuery)
                        },
                        fontSize = 14.sp,
                        color = TextSecondaryMuted,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                state = productListState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(5.dp),
                contentPadding = PaddingValues(bottom = if (isMobile && isKeyboardOpenMobile) 120.dp else 8.dp)
            ) {
                if (isMobile && searchQuery.isBlank()) {
                    item(key = "user_round_header", contentType = "header") {
                        userRoundHeaderCard()
                    }
                }
                val lastProductIndex = filteredProducts.lastIndex
                itemsIndexed(
                    items = filteredProducts,
                    key = { _, it -> it.id },
                    contentType = { _, _ -> "product" }
                ) { index, product ->
                    val unitType = unitTypesById[product.unitTypeId]
                    val currentQtyScaled = draftQuantitiesScaled[product.id] ?: 0L
                    val prevQtyScaled = previousRoundQuantitiesScaled[product.id] ?: 0L
                    val isFavorite = product.id in favoriteProductIds
                    val isHighlighted = index == highlightedProductIndex
                    val isFocused = isProductZoneActive && isHighlighted

                    val openDetailCb = remember(product.id, onOpenDetail) { { onOpenDetail(product) } }
                    val qtyChangeCb = remember(product.id, onUpdateProductQuantity) {
                        { newScaled: Long -> onUpdateProductQuantity(product.id, newScaled) }
                    }
                    val toggleFavoriteCb = remember(product.id, onToggleFavorite) {
                        { onToggleFavorite(product.id) }
                    }
                    val commitQtyCb = remember(index, lastProductIndex, onNavigateToProduct, onReturnToSearch) {
                        {
                            if (index < lastProductIndex) {
                                onNavigateToProduct?.invoke(index + 1)
                            } else {
                                onReturnToSearch?.invoke()
                            }
                            Unit
                        }
                    }
                    val commitPrevCb = remember(index, onNavigateToProduct, onReturnToSearch) {
                        {
                            if (index > 0) {
                                onNavigateToProduct?.invoke(index - 1)
                            } else {
                                onReturnToSearch?.invoke()
                            }
                            Unit
                        }
                    }
                    val focusedCb = remember(index, onCardFocused) {
                        { onCardFocused?.invoke(index); Unit }
                    }
                    val cardFocusModifier = remember(index, onCardFocused) {
                        if (onCardFocused == null) {
                            Modifier
                        } else {
                            Modifier.onFocusChanged { focusState ->
                                if (focusState.isFocused || focusState.hasFocus) {
                                    onCardFocused(index)
                                }
                            }
                        }
                    }

                    CustomerVeggieProductCard(
                        product = product,
                        unitType = unitType,
                        quantityScaled = currentQtyScaled,
                        previousQuantityScaled = prevQtyScaled,
                        isFavorite = isFavorite,
                        isHighlighted = isHighlighted,
                        isFocused = isFocused,
                        isDialogActive = isDialogActive,
                        isEditable = isRoundOpen,
                        decimalSeparator = decimalSep,
                        isMobile = isMobile,
                        onOpenDetail = openDetailCb,
                        onQuantityChange = qtyChangeCb,
                        onCommitQuantity = commitQtyCb,
                        onCommitPrevious = commitPrevCb,
                        onReturnToSearch = onReturnToSearch,
                        onFocused = focusedCb,
                        onToggleFavorite = toggleFavoriteCb,
                        modifier = cardFocusModifier
                    )
                }
            }
        }
    }
}

@Composable
private fun VeggieErrorBanner(
    errorMessage: String,
    onClearError: () -> Unit
) {
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

@Composable
private fun VeggieCartPanel(
    cartProducts: List<VeggieProduct>,
    unitTypesById: Map<String, VeggieUnitType>,
    draftQuantitiesScaled: Map<String, Long>,
    cartListState: LazyListState = rememberLazyListState(),
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
    modifier: Modifier = Modifier,
    errorMessage: String? = null,
    onClearError: () -> Unit = {},
    isDesktop: Boolean = false
) {
    val strings = I18n.current
    val hasPricelessItemsInCart = remember(cartProducts) {
        cartProducts.any { it.estimatedPriceCents == null }
    }

    Surface(
        shape = if (isDesktop) RoundedCornerShape(0.dp) else RoundedCornerShape(12.dp),
        color = SurfaceWhite,
        shadowElevation = if (isDesktop) 4.dp else 2.dp,
        border = if (isDesktop) null else BorderStroke(1.dp, DividerBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isDesktop) ScreenPadding else 14.dp),
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

            if (errorMessage != null) {
                VeggieErrorBanner(errorMessage = errorMessage, onClearError = onClearError)
            }

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
                    state = cartListState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(cartProducts, key = { _, it -> it.id }) { _, product ->
                        val qtyScaled = draftQuantitiesScaled[product.id] ?: 0L
                        val unitType = unitTypesById[product.unitTypeId]
                        VeggieCartLineItemRow(
                            product = product,
                            unitType = unitType,
                            quantityScaled = qtyScaled,
                            isEditable = isRoundOpen,
                            decimalSeparator = decimalSep,
                            isMobile = !isDesktop,
                            onEditQuantity = { onEditQuantity(product) },
                            onRemove = { onRemoveProduct(product) }
                        )
                    }
                }
            }

            HorizontalDivider(color = DividerBorder)

            // Footer: Totals + Fees + Save Button
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
                            text = if (hasUnsavedChanges || isSavingOrder) strings.saveVeggieOrderBtn else strings.veggieOrderAllSavedBtn,
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
    isMobile: Boolean = false,
    onEditQuantity: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = I18n.current
    val allowDecimals = unitType?.allowDecimals ?: false
    val isDiscontinued = !product.isActive || product.isDeleted
    val unitName = unitType?.name ?: "Unit"
    val qtyFormatted = VeggieQuantityScaling.formatScaledQuantity(quantityScaled, allowDecimals, decimalSeparator)

    val lineTotalCents = product.estimatedPriceCents?.let {
        VeggieSettlementCalculator.calculateTotalSpentFromUnitPrice(it, quantityScaled)
    } ?: 0L

    Surface(
        onClick = onEditQuantity,
        enabled = isEditable,
        shape = RoundedCornerShape(10.dp),
        color = SurfaceContainerLight,
        border = BorderStroke(1.dp, DividerBorder),
        modifier = modifier
            .fillMaxWidth()
            .focusProperties { canFocus = false }
            .pointerHoverIcon(if (isEditable) PointerIcon.Hand else PointerIcon.Default)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = product.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryNavy,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isDiscontinued) {
                        AdminStatusBadge(
                            text = strings.veggieProductDiscontinuedBadge,
                            type = AdminBadgeType.WARNING
                        )
                    }
                }
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
                // Quantity Label (non-button styling)
                Text(
                    text = "$qtyFormatted $unitName",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondarySubtle
                )

                Text(
                    text = Formatting.formatBrl(lineTotalCents),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryNavy,
                    modifier = Modifier.widthIn(min = 52.dp),
                    textAlign = TextAlign.End
                )

                // Trashcan Button on the complete right to remove item
                if (isEditable) {
                    Surface(
                        onClick = onRemove,
                        shape = RoundedCornerShape(8.dp),
                        color = SurfaceWhite,
                        border = BorderStroke(1.dp, DividerBorder),
                        modifier = Modifier
                            .size(if (isMobile) 34.dp else 28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .focusProperties { canFocus = false }
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
                                modifier = Modifier.size(if (isMobile) 17.dp else 15.dp)
                            )
                        }
                    }
                }
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
    isFavorite: Boolean,
    isHighlighted: Boolean = false,
    isFocused: Boolean = false,
    isDialogActive: Boolean = false,
    isEditable: Boolean,
    decimalSeparator: Char,
    isMobile: Boolean,
    onOpenDetail: () -> Unit,
    onQuantityChange: (Long) -> Unit = {},
    onCommitQuantity: () -> Unit = {},
    onCommitPrevious: (() -> Unit)? = null,
    onReturnToSearch: (() -> Unit)? = null,
    onFocused: (() -> Unit)? = null,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = I18n.current
    val allowDecimals = unitType?.allowDecimals ?: false
    val unitName = unitType?.name ?: "Unit"
    val isOrdered = quantityScaled > 0L

    val cardFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var isFieldFocused by remember { mutableStateOf(false) }

    if (isFocused && !isDialogActive && !isFieldFocused) {
        LaunchedEffect(Unit) {
            cardFocusRequester.safeRequestFocus()
            keyboardController?.show()
        }
    }

    val cardShape = RoundedCornerShape(10.dp)
    val cardColors = CardDefaults.cardColors(
        containerColor = if (isHighlighted) Color(0xFFEFF6FF) else SurfaceWhite
    )
    val cardBorder = if (isHighlighted) {
        BorderStroke(2.dp, AccentNavy)
    } else {
        BorderStroke(1.dp, DividerBorder)
    }
    val cardElevation = CardDefaults.cardElevation(defaultElevation = if (isMobile) 0.dp else 1.dp)

    val cardContent: @Composable ColumnScope.() -> Unit = {
        val lineTotalCents = if (isOrdered && product.estimatedPriceCents != null) {
            VeggieSettlementCalculator.calculateTotalSpentFromUnitPrice(product.estimatedPriceCents, quantityScaled)
        } else 0L

        val previousBadge: @Composable () -> Unit = {
            if (previousQuantityScaled > 0L) {
                val formattedPrev = VeggieQuantityScaling.formatScaledQuantity(
                    previousQuantityScaled,
                    allowDecimals,
                    decimalSeparator
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = SurfaceContainerHighLight,
                    border = BorderStroke(1.dp, DividerBorder),
                    modifier = Modifier.height(22.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(horizontal = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = strings.lastOrderBadge(formattedPrev, unitName),
                            fontSize = 9.5.sp,
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

        val favoriteButton: @Composable () -> Unit = {
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier
                    .size(if (isMobile) 26.dp else 36.dp)
                    .focusProperties { canFocus = false }
                    .pointerHoverIcon(PointerIcon.Hand)
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = if (isFavorite) strings.unmarkFavoriteTooltip else strings.markFavoriteTooltip,
                    tint = if (isFavorite) ColorStarYellow else TextSecondaryMuted.copy(alpha = 0.5f),
                    modifier = Modifier.size(if (isMobile) 18.dp else 22.dp)
                )
            }
        }

        if (isMobile) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Thumbnail centered across all lines
                VeggieProductImage(
                    productId = product.id,
                    imageFileName = product.imageFileName,
                    fullSize = false,
                    contentDescription = product.name,
                    shape = RoundedCornerShape(8.dp),
                    placeholderIconSize = 20.dp,
                    modifier = Modifier
                        .size(48.dp)
                        .focusProperties { canFocus = false },
                    onClick = onOpenDetail
                )

                // Right: 3 lines (Line 3 hidden if no description and not ordered)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    // Line 1: Name (+ subtle ★ if favorited) -> Last Order
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = product.name,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryNavy,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (isFavorite) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = ColorStarYellow,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        previousBadge()
                    }

                    // Line 2: Price -> Input field (always anchored right)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (product.estimatedPriceCents != null) {
                                "~ ${Formatting.formatBrl(product.estimatedPriceCents)} / $unitName"
                            } else {
                                strings.veggieEstimatedPriceUnknown
                            },
                            fontSize = 12.sp,
                            color = if (product.estimatedPriceCents != null) PrimaryNavy else TextSecondaryMuted,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        InlineVeggieQuantityField(
                            quantityScaled = quantityScaled,
                            allowDecimals = allowDecimals,
                            unitName = unitName,
                            decimalSeparator = decimalSeparator,
                            enabled = isEditable,
                            onQuantityChange = onQuantityChange,
                            onCommit = onCommitQuantity,
                            onCommitPrevious = onCommitPrevious,
                            onReturnToSearch = onReturnToSearch,
                            onFocused = onFocused,
                            focusRequester = cardFocusRequester,
                            isMobile = true,
                            modifier = Modifier.onFocusChanged { isFieldFocused = it.hasFocus }
                        )
                    }

                    // Line 3: Description -> Price Sum -> X (shown when description or ordered)
                    if (!product.description.isNullOrBlank() || isOrdered) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = product.description?.takeIf { it.isNotBlank() } ?: "",
                                fontSize = 11.sp,
                                color = TextSecondaryMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )

                            if (isOrdered && lineTotalCents > 0L) {
                                Text(
                                    text = "~ ${Formatting.formatBrl(lineTotalCents)}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ColorSuccessEmerald,
                                    maxLines = 1
                                )
                            }

                            if (isOrdered) {
                                IconButton(
                                    onClick = { onQuantityChange(0L) },
                                    enabled = isEditable,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .focusProperties { canFocus = false }
                                        .pointerHoverIcon(PointerIcon.Hand)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = strings.veggieClearItemTooltip,
                                        tint = TextSecondaryMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                VeggieProductImage(
                    productId = product.id,
                    imageFileName = product.imageFileName,
                    fullSize = false,
                    contentDescription = product.name,
                    shape = RoundedCornerShape(8.dp),
                    placeholderIconSize = 22.dp,
                    modifier = Modifier
                        .size(50.dp)
                        .focusProperties { canFocus = false },
                    onClick = onOpenDetail
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = product.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryNavy,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (product.estimatedPriceCents != null) {
                                "~ ${Formatting.formatBrl(product.estimatedPriceCents)} / $unitName"
                            } else {
                                strings.veggieEstimatedPriceUnknown
                            },
                            fontSize = 12.sp,
                            color = if (product.estimatedPriceCents != null) PrimaryNavy else TextSecondaryMuted,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )

                        if (!product.description.isNullOrBlank()) {
                            Text(
                                text = "• ${product.description}",
                                fontSize = 11.sp,
                                color = TextSecondaryMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    previousBadge()
                    if (isOrdered && lineTotalCents > 0L) {
                        Text(
                            text = "~ ${Formatting.formatBrl(lineTotalCents)}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorSuccessEmerald,
                            maxLines = 1
                        )
                    }
                    if (isOrdered) {
                        IconButton(
                            onClick = { onQuantityChange(0L) },
                            enabled = isEditable,
                            modifier = Modifier
                                .size(24.dp)
                                .focusProperties { canFocus = false }
                                .pointerHoverIcon(PointerIcon.Hand)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = strings.veggieClearItemTooltip,
                                tint = TextSecondaryMuted,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                    InlineVeggieQuantityField(
                        quantityScaled = quantityScaled,
                        allowDecimals = allowDecimals,
                        unitName = unitName,
                        decimalSeparator = decimalSeparator,
                        enabled = isEditable,
                        onQuantityChange = onQuantityChange,
                        onCommit = onCommitQuantity,
                        onCommitPrevious = onCommitPrevious,
                        onReturnToSearch = onReturnToSearch,
                        onFocused = onFocused,
                        focusRequester = cardFocusRequester,
                        isMobile = false,
                        modifier = Modifier.onFocusChanged { isFieldFocused = it.hasFocus }
                    )
                    favoriteButton()
                }
            }
        }
    }

    if (isMobile) {
        Card(
            shape = cardShape,
            colors = cardColors,
            border = cardBorder,
            elevation = cardElevation,
            modifier = modifier.fillMaxWidth(),
            content = cardContent
        )
    } else {
        Card(
            onClick = {
                if (isEditable) {
                    onFocused?.invoke()
                    try { cardFocusRequester.requestFocus() } catch (_: Exception) {}
                }
            },
            shape = cardShape,
            colors = cardColors,
            border = cardBorder,
            elevation = cardElevation,
            modifier = modifier.fillMaxWidth(),
            content = cardContent
        )
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
    val historyPage by veggieViewModel.historyPage.collectAsState()
    val historyPageSize by veggieViewModel.historyPageSize.collectAsState()
    val products by veggieViewModel.products.collectAsState()
    val productsById = remember(products) { products.associateBy { it.id } }
    val decimalSep = Formatting.decimalSeparator(currentLanguage)

    val filteredHistory = remember(historyItems, historySearchQuery) {
        veggieViewModel.filteredHistoryItems()
    }
    val totalCount = filteredHistory.size
    val totalPages = remember(totalCount, historyPageSize) {
        if (totalCount == 0) 1 else (totalCount + historyPageSize - 1) / historyPageSize
    }
    val safePage = remember(historyPage, totalPages) {
        historyPage.coerceIn(0, (totalPages - 1).coerceAtLeast(0))
    }
    val pagedHistory = remember(filteredHistory, safePage, historyPageSize) {
        val fromIndex = (safePage * historyPageSize).coerceAtMost(filteredHistory.size)
        val toIndex = (fromIndex + historyPageSize).coerceAtMost(filteredHistory.size)
        filteredHistory.subList(fromIndex, toIndex)
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
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
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
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                items(pagedHistory, key = { it.round.id }) { item ->
                    CustomerVeggieHistoryRoundCard(
                        item = item,
                        productsById = productsById,
                        searchQuery = historySearchQuery,
                        currentLanguage = currentLanguage,
                        decimalSeparator = decimalSep,
                        isMobile = isMobile
                    )
                }
            }
        }

        PaginationBar(
            currentPage = safePage,
            totalPages = totalPages,
            pageSize = historyPageSize,
            totalCount = totalCount,
            onPageChange = { veggieViewModel.setHistoryPage(it) },
            onPageSizeChange = { veggieViewModel.setHistoryPageSize(it) },
            pageSizeOptions = listOf(5, 10, 25),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun CustomerVeggieHistoryRoundCard(
    item: CustomerVeggieRoundHistoryItem,
    productsById: Map<String, VeggieProduct>,
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
                Column(
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
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
                        text = (if (!isSettled) "~ " else "") + Formatting.formatBrl(item.grandTotalCents),
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
                    entry.effectiveAllowDecimals,
                    decimalSeparator
                )
                val unitPriceCents = entry.effectiveUnitPriceCents
                    ?: productsById[entry.veggieProductId]?.estimatedPriceCents
                val lineTotalCents = entry.finalItemTotalCents ?: if (unitPriceCents != null && unitPriceCents > 0L) {
                    VeggieSettlementCalculator.calculateTotalSpentFromUnitPrice(unitPriceCents, deliveredScaled)
                } else null

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (matchesSearch) ColorStarYellow.copy(alpha = 0.2f) else SurfaceWhite)
                        .padding(vertical = 4.dp, horizontal = if (matchesSearch) 6.dp else 0.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = entry.veggieNameSnapshot,
                            fontSize = 14.sp,
                            fontWeight = if (matchesSearch) FontWeight.Bold else FontWeight.Medium,
                            color = PrimaryNavy
                        )
                        val qtySummary = if (isSettled || entry.actualQuantityScaled != null) {
                            "${strings.colOrderedQty}: $orderedText ${entry.unitNameSnapshot}  •  ${strings.colActualQty}: $deliveredText ${entry.effectiveDeliveryUnitName}"
                        } else {
                            "${strings.colOrderedQty}: $orderedText ${entry.unitNameSnapshot}"
                        }
                        val priceSuffix = if (unitPriceCents != null && unitPriceCents > 0L) {
                            "  •  ${if (!isSettled) "~ " else ""}${Formatting.formatBrl(unitPriceCents)} / ${if (isSettled) entry.effectiveDeliveryUnitName else entry.unitNameSnapshot}"
                        } else ""
                        Text(
                            text = qtySummary + priceSuffix,
                            fontSize = 12.sp,
                            color = TextSecondaryMuted
                        )
                    }

                    Text(
                        text = if (lineTotalCents != null) {
                            "${if (!isSettled) "~ " else ""}${Formatting.formatBrl(lineTotalCents)}"
                        } else {
                            strings.veggieEstimatedPriceUnknown
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PrimaryNavy
                    )
                }
            }

            // Single Combined Fee Line (Buyer Operation Fee + Transport Fee share combined)
            if (item.combinedFeeShareCents > 0L) {
                HorizontalDivider(color = DividerBorder.copy(alpha = 0.6f))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = AccentNavy,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = strings.veggieCombinedFeeLabel,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = PrimaryNavy
                            )
                        }
                        Text(
                            text = (if (!isSettled) "~ " else "") + Formatting.formatBrl(item.combinedFeeShareCents),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PrimaryNavy
                        )
                    }
                    if (!isSettled) {
                        Text(
                            text = strings.veggieSharedFeeNotice,
                            fontSize = 11.sp,
                            color = TextSecondaryMuted,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }
    }
}
