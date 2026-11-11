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
import de.joelneumann.lojinha.ui.utils.safeRequestFocus
import de.joelneumann.lojinha.ui.utils.setBrowserBeforeUnloadWarning
import de.joelneumann.lojinha.ui.viewmodel.CustomerVeggieViewModel
import de.joelneumann.lojinha.ui.viewmodel.TransactionHistoryViewModel
import de.joelneumann.lojinha.ui.viewmodel.VeggieInputMode
import kotlinx.coroutines.launch
import kotlin.math.roundToLong

private enum class VeggieFocusZone {
    SEARCH,
    PRODUCTS,
    CART
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
    isMobile: Boolean,
    user: User
) {
    val strings = I18n.current
    val activeRound by veggieViewModel.activeRound.collectAsState()
    val products by veggieViewModel.products.collectAsState()
    val draftQuantitiesScaled by veggieViewModel.draftQuantitiesScaled.collectAsState()
    val previousRoundQuantitiesScaled by veggieViewModel.previousRoundQuantitiesScaled.collectAsState()
    val searchQuery by veggieViewModel.productSearchQuery.collectAsState()
    val filterFavoritesOnly by veggieViewModel.filterFavoritesOnly.collectAsState()
    val favoriteProductIds by veggieViewModel.favoriteProductIds.collectAsState()
    val isSavingOrder by veggieViewModel.isSavingOrder.collectAsState()
    val orderSavedSuccessBanner by veggieViewModel.orderSavedSuccessBanner.collectAsState()
    val errorMessage by veggieViewModel.errorMessage.collectAsState()
    val hasUnsavedChanges by veggieViewModel.hasUnsavedOrderChanges.collectAsState()
    val filterInOrderOnly by veggieViewModel.filterInOrderOnly.collectAsState()
    val inputMode by veggieViewModel.inputMode.collectAsState()

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
    val decimalSep = if (currentLanguage == Language.EN) '.' else ','

    val inOrderCount = remember(draftQuantitiesScaled, products) {
        products.count { (draftQuantitiesScaled[it.id] ?: 0L) > 0L }
    }
    val favoritesCount = remember(favoriteProductIds, products) {
        products.count { it.id in favoriteProductIds }
    }

    val filteredProducts = remember(
        products,
        searchQuery,
        filterFavoritesOnly,
        favoriteProductIds,
        filterInOrderOnly,
        if (filterInOrderOnly) draftQuantitiesScaled else Unit
    ) {
        products.filter { product ->
            val matchesFavorite = !filterFavoritesOnly || product.id in favoriteProductIds
            if (!matchesFavorite) return@filter false

            val matchesInOrder = !filterInOrderOnly || (draftQuantitiesScaled[product.id] ?: 0L) > 0L
            if (!matchesInOrder) return@filter false

            if (searchQuery.isBlank()) {
                true
            } else {
                product.name.containsIgnoreAccents(searchQuery) ||
                    (product.description?.containsIgnoreAccents(searchQuery) == true)
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

    val searchFocusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()
    val productListState = rememberLazyListState()
    val cartListState = rememberLazyListState()

    var activeZone by remember { mutableStateOf(VeggieFocusZone.SEARCH) }
    var highlightedProductIndex by remember { mutableStateOf(-1) }
    var highlightedCartIndex by remember { mutableStateOf(-1) }

    fun navigateToProduct(index: Int) {
        if (filteredProducts.isEmpty()) return
        val targetIndex = index.coerceIn(0, filteredProducts.lastIndex)
        activeZone = VeggieFocusZone.PRODUCTS
        highlightedProductIndex = targetIndex
        highlightedCartIndex = -1
        coroutineScope.launch {
            productListState.animateScrollToItem(targetIndex)
        }
    }

    fun navigateToCart(index: Int) {
        if (cartProducts.isEmpty()) return
        val targetIndex = index.coerceIn(0, cartProducts.lastIndex)
        activeZone = VeggieFocusZone.CART
        highlightedCartIndex = targetIndex
        highlightedProductIndex = -1
        coroutineScope.launch {
            cartListState.animateScrollToItem(targetIndex)
        }
    }

    fun navigateToSearch() {
        activeZone = VeggieFocusZone.SEARCH
        highlightedProductIndex = -1
        highlightedCartIndex = -1
        coroutineScope.launch {
            searchFocusRequester.safeRequestFocus()
        }
    }

    LaunchedEffect(filteredProducts) {
        if (highlightedProductIndex >= filteredProducts.size) {
            highlightedProductIndex = -1
        }
    }

    LaunchedEffect(cartProducts) {
        if (highlightedCartIndex >= cartProducts.size) {
            highlightedCartIndex = (cartProducts.size - 1).coerceAtLeast(-1)
        }
    }

    LaunchedEffect(Unit) {
        searchFocusRequester.safeRequestFocus()
    }

    val handleNavigationKey: (KeyEvent) -> Boolean = { event ->
        if (event.type != KeyEventType.KeyDown) {
            false
        } else if (activeZone == VeggieFocusZone.PRODUCTS && inputMode == VeggieInputMode.FAST_TAB) {
            false
        } else {
            when (event.key) {
                Key.DirectionDown -> {
                    when (activeZone) {
                        VeggieFocusZone.SEARCH -> {
                            if (filteredProducts.isNotEmpty()) {
                                navigateToProduct(0)
                                true
                            } else if (cartProducts.isNotEmpty()) {
                                navigateToCart(0)
                                true
                            } else false
                        }
                        VeggieFocusZone.PRODUCTS -> {
                            if (filteredProducts.isNotEmpty()) {
                                if (highlightedProductIndex < filteredProducts.lastIndex) {
                                    navigateToProduct(highlightedProductIndex + 1)
                                    true
                                } else if (cartProducts.isNotEmpty()) {
                                    navigateToCart(0)
                                    true
                                } else true
                            } else false
                        }
                        VeggieFocusZone.CART -> {
                            if (cartProducts.isNotEmpty()) {
                                navigateToCart(highlightedCartIndex + 1)
                                true
                            } else false
                        }
                    }
                }
                Key.DirectionUp -> {
                    when (activeZone) {
                        VeggieFocusZone.PRODUCTS -> {
                            if (highlightedProductIndex > 0) {
                                navigateToProduct(highlightedProductIndex - 1)
                                true
                            } else if (highlightedProductIndex == 0) {
                                navigateToSearch()
                                true
                            } else false
                        }
                        VeggieFocusZone.CART -> {
                            if (highlightedCartIndex > 0) {
                                navigateToCart(highlightedCartIndex - 1)
                                true
                            } else if (highlightedCartIndex == 0) {
                                if (filteredProducts.isNotEmpty()) {
                                    navigateToProduct(0)
                                    true
                                } else false
                            } else false
                        }
                        VeggieFocusZone.SEARCH -> false
                    }
                }
                Key.Tab -> {
                    if (event.isShiftPressed) {
                        when (activeZone) {
                            VeggieFocusZone.PRODUCTS -> {
                                if (highlightedProductIndex > 0) {
                                    navigateToProduct(highlightedProductIndex - 1)
                                    true
                                } else {
                                    navigateToSearch()
                                    true
                                }
                            }
                            VeggieFocusZone.CART -> {
                                if (highlightedCartIndex > 0) {
                                    navigateToCart(highlightedCartIndex - 1)
                                    true
                                } else {
                                    if (filteredProducts.isNotEmpty()) {
                                        navigateToProduct(filteredProducts.lastIndex)
                                    } else {
                                        navigateToSearch()
                                    }
                                    true
                                }
                            }
                            VeggieFocusZone.SEARCH -> {
                                if (cartProducts.isNotEmpty()) {
                                    navigateToCart(cartProducts.lastIndex)
                                    true
                                } else if (filteredProducts.isNotEmpty()) {
                                    navigateToProduct(filteredProducts.lastIndex)
                                    true
                                } else false
                            }
                        }
                    } else {
                        when (activeZone) {
                            VeggieFocusZone.SEARCH -> {
                                if (filteredProducts.isNotEmpty()) {
                                    navigateToProduct(0)
                                    true
                                } else if (cartProducts.isNotEmpty()) {
                                    navigateToCart(0)
                                    true
                                } else false
                            }
                            VeggieFocusZone.PRODUCTS -> {
                                if (highlightedProductIndex < filteredProducts.lastIndex) {
                                    navigateToProduct(highlightedProductIndex + 1)
                                    true
                                } else if (cartProducts.isNotEmpty()) {
                                    navigateToCart(0)
                                    true
                                } else {
                                    navigateToSearch()
                                    true
                                }
                            }
                            VeggieFocusZone.CART -> {
                                if (highlightedCartIndex < cartProducts.lastIndex) {
                                    navigateToCart(highlightedCartIndex + 1)
                                    true
                                } else {
                                    navigateToSearch()
                                    true
                                }
                            }
                        }
                    }
                }
                Key.DirectionRight -> {
                    if ((activeZone == VeggieFocusZone.PRODUCTS || (activeZone == VeggieFocusZone.SEARCH && searchQuery.isBlank())) && cartProducts.isNotEmpty()) {
                        navigateToCart(0)
                        true
                    } else false
                }
                Key.DirectionLeft -> {
                    if (activeZone == VeggieFocusZone.CART) {
                        if (filteredProducts.isNotEmpty()) {
                            navigateToProduct(0)
                            true
                        } else false
                    } else false
                }
                Key.Enter, Key.NumPadEnter -> {
                    when (activeZone) {
                        VeggieFocusZone.PRODUCTS -> {
                            if (inputMode == VeggieInputMode.FAST_TAB) {
                                if (highlightedProductIndex < filteredProducts.lastIndex) {
                                    navigateToProduct(highlightedProductIndex + 1)
                                    true
                                } else if (cartProducts.isNotEmpty()) {
                                    navigateToCart(0)
                                    true
                                } else false
                            } else {
                                if (highlightedProductIndex in filteredProducts.indices && isRoundOpen) {
                                    selectedProductForAmountDialog = filteredProducts[highlightedProductIndex]
                                    true
                                } else false
                            }
                        }
                        VeggieFocusZone.CART -> {
                            if (highlightedCartIndex in cartProducts.indices && isRoundOpen) {
                                selectedProductForAmountDialog = cartProducts[highlightedCartIndex]
                                true
                            } else false
                        }
                        VeggieFocusZone.SEARCH -> false
                    }
                }
                Key.Spacebar -> {
                    if (inputMode != VeggieInputMode.FAST_TAB && activeZone == VeggieFocusZone.PRODUCTS && highlightedProductIndex in filteredProducts.indices && isRoundOpen) {
                        selectedProductForAmountDialog = filteredProducts[highlightedProductIndex]
                        true
                    } else if (activeZone == VeggieFocusZone.CART && highlightedCartIndex in cartProducts.indices && isRoundOpen) {
                        selectedProductForAmountDialog = cartProducts[highlightedCartIndex]
                        true
                    } else false
                }
                Key.Backspace, Key.Delete -> {
                    if (activeZone == VeggieFocusZone.CART && highlightedCartIndex in cartProducts.indices && isRoundOpen) {
                        val prod = cartProducts[highlightedCartIndex]
                        veggieViewModel.updateProductQuantity(prod.id, 0L)
                        if (highlightedCartIndex >= cartProducts.size - 1) {
                            highlightedCartIndex = (cartProducts.size - 2).coerceAtLeast(0)
                        }
                        true
                    } else false
                }
                Key.Escape -> {
                    if (activeZone != VeggieFocusZone.SEARCH) {
                        navigateToSearch()
                        true
                    } else false
                }
                else -> false
            }
        }
    }

    val onSearchKeyDown: (KeyEvent) -> Boolean = handleNavigationKey
    val onGlobalKeyEvent: (KeyEvent) -> Boolean = handleNavigationKey

    if (isMobile) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(SurfaceContainerLight)
                .padding(10.dp)
                .onPreviewKeyEvent(handleNavigationKey),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
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

            // Content Area: Catalog or Cart
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
                        user = user,
                        favoriteProductIds = favoriteProductIds,
                        filterFavoritesOnly = filterFavoritesOnly,
                        productListState = productListState,
                        searchFocusRequester = searchFocusRequester,
                        highlightedProductIndex = if (activeZone == VeggieFocusZone.PRODUCTS) highlightedProductIndex else -1,
                        isDialogActive = (selectedProductForAmountDialog != null),
                        onKeyDown = handleNavigationKey,
                        onCardFocused = { idx ->
                            activeZone = VeggieFocusZone.PRODUCTS
                            highlightedProductIndex = idx
                            highlightedCartIndex = -1
                        },
                        onNavigateToProduct = { idx -> navigateToProduct(idx) },
                        onReturnToSearch = { navigateToSearch() },
                        onClearError = { veggieViewModel.clearError() },
                        onQueryChange = { query ->
                            activeZone = VeggieFocusZone.SEARCH
                            highlightedProductIndex = -1
                            highlightedCartIndex = -1
                            veggieViewModel.updateProductSearchQuery(query)
                        },
                        onOpenDetail = { veggieViewModel.openProductDetail(it) },
                        onSelectProduct = { selectedProductForAmountDialog = it },
                        onToggleFavorite = { veggieViewModel.toggleFavorite(it) },
                        onToggleFavoriteFilter = { veggieViewModel.toggleFilterFavoritesOnly() },
                        inputMode = inputMode,
                        filterInOrderOnly = filterInOrderOnly,
                        inOrderCount = inOrderCount,
                        favoritesCount = favoritesCount,
                        onSelectInputMode = { veggieViewModel.setInputMode(it) },
                        onToggleFilterInOrderOnly = { veggieViewModel.toggleFilterInOrderOnly() },
                        onUpdateProductQuantity = { id, qty -> veggieViewModel.updateProductQuantity(id, qty) },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                MobileVeggieSubTab.CART -> {
                    VeggieCartPanel(
                        cartProducts = cartProducts,
                        unitTypesById = unitTypesById,
                        draftQuantitiesScaled = draftQuantitiesScaled,
                        combinedFeeCents = round.combinedFeeCents,
                        highlightedCartIndex = if (activeZone == VeggieFocusZone.CART) highlightedCartIndex else -1,
                        cartListState = cartListState,
                        isRoundOpen = isRoundOpen,
                        decimalSep = decimalSep,
                        estimatedSubtotalCents = estimatedSubtotalCents,
                        hasUnsavedChanges = hasUnsavedChanges,
                        isSavingOrder = isSavingOrder,
                        hasPreviousRoundItems = hasPreviousRoundItems,
                        isDialogActive = (selectedProductForAmountDialog != null),
                        onCartKeyDown = handleNavigationKey,
                        onCartItemFocused = { idx ->
                            activeZone = VeggieFocusZone.CART
                            highlightedCartIndex = idx
                            highlightedProductIndex = -1
                        },
                        isDesktop = false,
                        onFillFromLastWeek = { veggieViewModel.copyPreviousRoundOrder() },
                        onEditQuantity = { selectedProductForAmountDialog = it },
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
                highlightedProductIndex = if (activeZone == VeggieFocusZone.PRODUCTS) highlightedProductIndex else -1,
                isDialogActive = (selectedProductForAmountDialog != null),
                onKeyDown = handleNavigationKey,
                onCardFocused = { idx ->
                    activeZone = VeggieFocusZone.PRODUCTS
                    highlightedProductIndex = idx
                    highlightedCartIndex = -1
                },
                onNavigateToProduct = { idx -> navigateToProduct(idx) },
                onReturnToSearch = { navigateToSearch() },
                onClearError = { veggieViewModel.clearError() },
                onQueryChange = { query ->
                    activeZone = VeggieFocusZone.SEARCH
                    highlightedProductIndex = -1
                    highlightedCartIndex = -1
                    veggieViewModel.updateProductSearchQuery(query)
                },
                onOpenDetail = { veggieViewModel.openProductDetail(it) },
                onSelectProduct = { selectedProductForAmountDialog = it },
                onToggleFavorite = { veggieViewModel.toggleFavorite(it) },
                onToggleFavoriteFilter = { veggieViewModel.toggleFilterFavoritesOnly() },
                inputMode = inputMode,
                filterInOrderOnly = filterInOrderOnly,
                inOrderCount = inOrderCount,
                favoritesCount = favoritesCount,
                onSelectInputMode = { veggieViewModel.setInputMode(it) },
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
                combinedFeeCents = round.combinedFeeCents,
                highlightedCartIndex = if (activeZone == VeggieFocusZone.CART) highlightedCartIndex else -1,
                cartListState = cartListState,
                isRoundOpen = isRoundOpen,
                decimalSep = decimalSep,
                estimatedSubtotalCents = estimatedSubtotalCents,
                hasUnsavedChanges = hasUnsavedChanges,
                isSavingOrder = isSavingOrder,
                hasPreviousRoundItems = hasPreviousRoundItems,
                isDialogActive = (selectedProductForAmountDialog != null),
                onCartKeyDown = handleNavigationKey,
                onCartItemFocused = { idx ->
                    activeZone = VeggieFocusZone.CART
                    highlightedCartIndex = idx
                    highlightedProductIndex = -1
                },
                isDesktop = true,
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
    user: User,
    favoriteProductIds: Set<String>,
    filterFavoritesOnly: Boolean,
    productListState: LazyListState,
    searchFocusRequester: FocusRequester,
    highlightedProductIndex: Int,
    isDialogActive: Boolean = false,
    inputMode: VeggieInputMode = VeggieInputMode.STANDARD,
    filterInOrderOnly: Boolean = false,
    inOrderCount: Int = 0,
    favoritesCount: Int = 0,
    onSelectInputMode: (VeggieInputMode) -> Unit = {},
    onToggleFilterInOrderOnly: () -> Unit = {},
    onUpdateProductQuantity: (String, Long) -> Unit = { _, _ -> },
    onKeyDown: ((KeyEvent) -> Boolean)? = null,
    onCardFocused: ((Int) -> Unit)? = null,
    onNavigateToProduct: ((Int) -> Unit)? = null,
    onReturnToSearch: (() -> Unit)? = null,
    onClearError: () -> Unit,
    onQueryChange: (String) -> Unit,
    onOpenDetail: (VeggieProduct) -> Unit,
    onSelectProduct: (VeggieProduct) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onToggleFavoriteFilter: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = I18n.current

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Name Card with Week/Year standing before the Open Badge
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = SurfaceWhite,
            shadowElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Avatar + Name
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    UserAvatar(
                        user = user,
                        modifier = Modifier.size(36.dp),
                        fontSize = 15.sp
                    )
                    Text(
                        text = user.name,
                        fontSize = 16.sp,
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

        // Search Bar Input + Mode Switcher + Filter by ★
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
                modifier = Modifier.weight(1f)
            )

            // Segmented Mode Switcher (Standard vs Fast Tab)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = SurfaceContainerHighLight,
                border = BorderStroke(1.dp, DividerBorder),
                modifier = Modifier
                    .height(56.dp)
                    .focusProperties { canFocus = false }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isStandard = inputMode == VeggieInputMode.STANDARD
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isStandard) AccentNavy else Color.Transparent)
                            .clickable { onSelectInputMode(VeggieInputMode.STANDARD) }
                            .padding(horizontal = if (isMobile) 8.dp else 12.dp)
                            .pointerHoverIcon(PointerIcon.Hand),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ViewAgenda,
                                contentDescription = strings.veggieInputModeStandard,
                                tint = if (isStandard) SurfaceWhite else TextSecondaryMuted,
                                modifier = Modifier.size(16.dp)
                            )
                            if (!isMobile) {
                                Text(
                                    text = strings.veggieInputModeStandard,
                                    fontSize = 12.sp,
                                    fontWeight = if (isStandard) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isStandard) SurfaceWhite else TextSecondaryMuted
                                )
                            }
                        }
                    }

                    val isFastTab = inputMode == VeggieInputMode.FAST_TAB
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isFastTab) AccentNavy else Color.Transparent)
                            .clickable { onSelectInputMode(VeggieInputMode.FAST_TAB) }
                            .padding(horizontal = if (isMobile) 8.dp else 12.dp)
                            .pointerHoverIcon(PointerIcon.Hand),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = strings.veggieInputModeFastTab,
                                tint = if (isFastTab) ColorStarYellow else TextSecondaryMuted,
                                modifier = Modifier.size(16.dp)
                            )
                            if (!isMobile) {
                                Text(
                                    text = strings.veggieInputModeFastTab,
                                    fontSize = 12.sp,
                                    fontWeight = if (isFastTab) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isFastTab) SurfaceWhite else TextSecondaryMuted
                                )
                            }
                        }
                    }
                }
            }

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
                    .size(56.dp)
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
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }

        // Quick Filter Chips (All | In Order | Favorites)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isAllSelected = !filterFavoritesOnly && !filterInOrderOnly
            Surface(
                onClick = {
                    if (!isAllSelected) {
                        if (filterFavoritesOnly) onToggleFavoriteFilter()
                        if (filterInOrderOnly) onToggleFilterInOrderOnly()
                    }
                },
                shape = RoundedCornerShape(8.dp),
                color = if (isAllSelected) AccentNavy else SurfaceWhite,
                border = BorderStroke(1.dp, if (isAllSelected) AccentNavy else DividerBorder),
                modifier = Modifier
                    .height(28.dp)
                    .focusProperties { canFocus = false }
                    .pointerHoverIcon(PointerIcon.Hand)
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${strings.veggieFilterAll} ($allProductsCount)",
                        fontSize = 11.5.sp,
                        fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isAllSelected) SurfaceWhite else PrimaryNavy
                    )
                }
            }

            Surface(
                onClick = {
                    if (filterFavoritesOnly) onToggleFavoriteFilter()
                    onToggleFilterInOrderOnly()
                },
                shape = RoundedCornerShape(8.dp),
                color = if (filterInOrderOnly) ColorSuccessEmerald else SurfaceWhite,
                border = BorderStroke(1.dp, if (filterInOrderOnly) ColorSuccessEmerald else DividerBorder),
                modifier = Modifier
                    .height(28.dp)
                    .focusProperties { canFocus = false }
                    .pointerHoverIcon(PointerIcon.Hand)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ShoppingCart,
                        contentDescription = null,
                        tint = if (filterInOrderOnly) SurfaceWhite else ColorSuccessEmerald,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "${strings.veggieFilterInOrder} ($inOrderCount)",
                        fontSize = 11.5.sp,
                        fontWeight = if (filterInOrderOnly) FontWeight.Bold else FontWeight.Medium,
                        color = if (filterInOrderOnly) SurfaceWhite else PrimaryNavy
                    )
                }
            }

            Surface(
                onClick = {
                    if (filterInOrderOnly) onToggleFilterInOrderOnly()
                    onToggleFavoriteFilter()
                },
                shape = RoundedCornerShape(8.dp),
                color = if (filterFavoritesOnly) ColorStarYellow.copy(alpha = 0.2f) else SurfaceWhite,
                border = BorderStroke(1.dp, if (filterFavoritesOnly) ColorStarYellow else DividerBorder),
                modifier = Modifier
                    .height(28.dp)
                    .focusProperties { canFocus = false }
                    .pointerHoverIcon(PointerIcon.Hand)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (filterFavoritesOnly) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = null,
                        tint = if (filterFavoritesOnly) ColorStarYellow else TextSecondaryMuted,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "${strings.filterFavoritesTooltip} ($favoritesCount)",
                        fontSize = 11.5.sp,
                        fontWeight = if (filterFavoritesOnly) FontWeight.Bold else FontWeight.Medium,
                        color = PrimaryNavy
                    )
                }
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
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                itemsIndexed(filteredProducts, key = { _, it -> it.id }) { index, product ->
                    val unitType = unitTypesById[product.unitTypeId]
                    val currentQtyScaled = draftQuantitiesScaled[product.id] ?: 0L
                    val prevQtyScaled = previousRoundQuantitiesScaled[product.id] ?: 0L

                    CustomerVeggieProductCard(
                        product = product,
                        unitType = unitType,
                        quantityScaled = currentQtyScaled,
                        previousQuantityScaled = prevQtyScaled,
                        isFavorite = product.id in favoriteProductIds,
                        isHighlighted = (index == highlightedProductIndex),
                        isDialogActive = isDialogActive,
                        isEditable = isRoundOpen,
                        inputMode = inputMode,
                        decimalSeparator = decimalSep,
                        isMobile = isMobile,
                        onOpenDetail = { onOpenDetail(product) },
                        onClickCard = { onSelectProduct(product) },
                        onQuantityChange = { newScaled -> onUpdateProductQuantity(product.id, newScaled) },
                        onCommitQuantity = {
                            if (index < filteredProducts.lastIndex) {
                                onNavigateToProduct?.invoke(index + 1)
                            } else {
                                onReturnToSearch?.invoke()
                            }
                        },
                        onCommitPrevious = {
                            if (index > 0) {
                                onNavigateToProduct?.invoke(index - 1)
                            } else {
                                onReturnToSearch?.invoke()
                            }
                        },
                        onReturnToSearch = onReturnToSearch,
                        onFocused = { onCardFocused?.invoke(index) },
                        onToggleFavorite = { onToggleFavorite(product.id) },
                        onKeyDown = onKeyDown,
                        modifier = Modifier.onFocusChanged { focusState ->
                            if (focusState.isFocused || focusState.hasFocus) {
                                onCardFocused?.invoke(index)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun VeggieCartPanel(
    cartProducts: List<VeggieProduct>,
    unitTypesById: Map<String, VeggieUnitType>,
    draftQuantitiesScaled: Map<String, Long>,
    combinedFeeCents: Long = 0L,
    highlightedCartIndex: Int = -1,
    cartListState: LazyListState = rememberLazyListState(),
    isRoundOpen: Boolean,
    decimalSep: Char,
    estimatedSubtotalCents: Long,
    hasUnsavedChanges: Boolean,
    isSavingOrder: Boolean,
    hasPreviousRoundItems: Boolean,
    isDialogActive: Boolean = false,
    onCartKeyDown: ((KeyEvent) -> Boolean)? = null,
    onCartItemFocused: ((Int) -> Unit)? = null,
    onFillFromLastWeek: () -> Unit,
    onEditQuantity: (VeggieProduct) -> Unit,
    onRemoveProduct: (VeggieProduct) -> Unit,
    onSaveOrder: () -> Unit,
    modifier: Modifier = Modifier,
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
                    itemsIndexed(cartProducts, key = { _, it -> it.id }) { index, product ->
                        val qtyScaled = draftQuantitiesScaled[product.id] ?: 0L
                        val unitType = unitTypesById[product.unitTypeId]
                        VeggieCartLineItemRow(
                            product = product,
                            unitType = unitType,
                            quantityScaled = qtyScaled,
                            isHighlighted = (index == highlightedCartIndex),
                            isDialogActive = isDialogActive,
                            isEditable = isRoundOpen,
                            decimalSeparator = decimalSep,
                            onEditQuantity = { onEditQuantity(product) },
                            onRemove = { onRemoveProduct(product) },
                            onKeyDown = onCartKeyDown,
                            modifier = Modifier.onFocusChanged { focusState ->
                                if (focusState.isFocused) {
                                    onCartItemFocused?.invoke(index)
                                }
                            }
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

                if (combinedFeeCents > 0L) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = SurfaceContainerHighLight.copy(alpha = 0.6f),
                        border = BorderStroke(1.dp, DividerBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
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
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = strings.veggieFixedRoundFeesLabel,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = PrimaryNavy
                                    )
                                }
                                Text(
                                    text = Formatting.formatBrl(combinedFeeCents),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryNavy
                                )
                            }
                            Text(
                                text = strings.veggieSharedFeeNotice,
                                fontSize = 11.sp,
                                color = TextSecondaryMuted,
                                lineHeight = 15.sp
                            )
                        }
                    }
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
    isHighlighted: Boolean = false,
    isDialogActive: Boolean = false,
    onEditQuantity: () -> Unit,
    onRemove: () -> Unit,
    onKeyDown: ((KeyEvent) -> Boolean)? = null,
    modifier: Modifier = Modifier
) {
    val allowDecimals = unitType?.allowDecimals ?: false
    val unitName = unitType?.name ?: "Unit"
    val qtyFormatted = VeggieQuantityScaling.formatScaledQuantity(quantityScaled, allowDecimals, decimalSeparator)

    val lineTotalCents = if (product.estimatedPriceCents != null) {
        ((quantityScaled.toDouble() / VEGGIE_QTY_SCALE.toDouble()) * product.estimatedPriceCents.toDouble()).roundToLong()
    } else 0L

    val rowFocusRequester = remember { FocusRequester() }

    LaunchedEffect(isHighlighted, isDialogActive) {
        if (isHighlighted && !isDialogActive) {
            rowFocusRequester.safeRequestFocus()
        }
    }

    Surface(
        onClick = onEditQuantity,
        enabled = isEditable,
        shape = RoundedCornerShape(10.dp),
        color = if (isHighlighted) Color(0xFFEFF6FF) else SurfaceContainerLight,
        border = if (isHighlighted) BorderStroke(2.dp, AccentNavy) else BorderStroke(1.dp, DividerBorder),
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(rowFocusRequester)
            .onPreviewKeyEvent { event ->
                if (onKeyDown != null && onKeyDown(event)) {
                    true
                } else false
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
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
                // Number Button (click re-opens amount dialog, excluded from Tab)
                Surface(
                    onClick = onEditQuantity,
                    enabled = isEditable,
                    shape = RoundedCornerShape(8.dp),
                    color = SurfaceWhite,
                    border = BorderStroke(1.dp, DividerBorder),
                    modifier = Modifier
                        .height(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .focusProperties { canFocus = false }
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

                // Disconnected Trashcan Button to remove (excluded from Tab)
                if (isEditable) {
                    Surface(
                        onClick = onRemove,
                        shape = RoundedCornerShape(8.dp),
                        color = SurfaceWhite,
                        border = BorderStroke(1.dp, DividerBorder),
                        modifier = Modifier
                            .size(28.dp)
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
    isFavorite: Boolean,
    isHighlighted: Boolean = false,
    isDialogActive: Boolean = false,
    isEditable: Boolean,
    inputMode: VeggieInputMode = VeggieInputMode.STANDARD,
    decimalSeparator: Char,
    isMobile: Boolean,
    onOpenDetail: () -> Unit,
    onClickCard: () -> Unit,
    onQuantityChange: (Long) -> Unit = {},
    onCommitQuantity: () -> Unit = {},
    onCommitPrevious: (() -> Unit)? = null,
    onReturnToSearch: (() -> Unit)? = null,
    onFocused: (() -> Unit)? = null,
    onToggleFavorite: () -> Unit,
    onKeyDown: ((KeyEvent) -> Boolean)? = null,
    modifier: Modifier = Modifier
) {
    val strings = I18n.current
    val allowDecimals = unitType?.allowDecimals ?: false
    val unitName = unitType?.name ?: "Unit"
    val isOrdered = quantityScaled > 0L

    val cardFocusRequester = remember { FocusRequester() }

    LaunchedEffect(isHighlighted, isDialogActive) {
        if (isHighlighted && !isDialogActive) {
            cardFocusRequester.safeRequestFocus()
        }
    }

    Card(
        onClick = {
            if (isEditable) {
                if (inputMode == VeggieInputMode.FAST_TAB) {
                    onFocused?.invoke()
                    try { cardFocusRequester.requestFocus() } catch (_: Exception) {}
                } else {
                    onClickCard()
                }
            }
        },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isHighlighted) Color(0xFFEFF6FF) else SurfaceWhite
        ),
        border = if (isHighlighted) {
            BorderStroke(2.dp, AccentNavy)
        } else {
            BorderStroke(1.dp, DividerBorder)
        },
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (inputMode == VeggieInputMode.STANDARD) {
                    Modifier
                        .focusRequester(cardFocusRequester)
                        .onPreviewKeyEvent { event ->
                            if (onKeyDown != null && onKeyDown(event)) {
                                true
                            } else false
                        }
                } else Modifier
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail (clickable ONLY to open detail modal, excluded from Tab)
            VeggieProductImage(
                productId = product.id,
                imageFileName = product.imageFileName,
                fullSize = false,
                contentDescription = product.name,
                shape = RoundedCornerShape(8.dp),
                placeholderIconSize = 22.dp,
                modifier = Modifier
                    .size(if (isMobile) 44.dp else 50.dp)
                    .focusProperties { canFocus = false },
                onClick = onOpenDetail
            )

            // Middle: Details
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
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
                        overflow = TextOverflow.Ellipsis
                    )
                    AdminStatusBadge(
                        text = unitName,
                        type = AdminBadgeType.NAVY
                    )
                }

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

            // Right side: Controls depend on inputMode
            if (inputMode == VeggieInputMode.FAST_TAB) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Previous order badge (clickable to fill quantity)
                    if (previousQuantityScaled > 0L) {
                        val formattedPrev = VeggieQuantityScaling.formatScaledQuantity(
                            previousQuantityScaled,
                            allowDecimals,
                            decimalSeparator
                        )
                        Surface(
                            onClick = { if (isEditable) onQuantityChange(previousQuantityScaled) },
                            enabled = isEditable,
                            shape = RoundedCornerShape(6.dp),
                            color = SurfaceContainerHighLight,
                            border = BorderStroke(1.dp, DividerBorder),
                            modifier = Modifier
                                .height(26.dp)
                                .focusProperties { canFocus = false }
                                .pointerHoverIcon(if (isEditable) PointerIcon.Hand else PointerIcon.Default)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .padding(horizontal = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = strings.lastOrderBadge(formattedPrev, unitName),
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = PrimaryNavy,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Line total preview (shown if ordered)
                    if (isOrdered) {
                        val lineTotalCents = if (product.estimatedPriceCents != null) {
                            ((quantityScaled.toDouble() / VEGGIE_QTY_SCALE.toDouble()) * product.estimatedPriceCents.toDouble()).roundToLong()
                        } else 0L
                        if (lineTotalCents > 0L) {
                            Text(
                                text = "~ ${Formatting.formatBrl(lineTotalCents)}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorSuccessEmerald,
                                maxLines = 1
                            )
                        }

                        // Disconnected Clear button (✕)
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

                    // Direct inline quantity input field (with cardFocusRequester)
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
                        focusRequester = cardFocusRequester
                    )

                    // VERY END: Star ☆ / ★ (excluded from Tab)
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier
                            .size(if (isMobile) 32.dp else 36.dp)
                            .focusProperties { canFocus = false }
                            .pointerHoverIcon(PointerIcon.Hand)
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = if (isFavorite) strings.unmarkFavoriteTooltip else strings.markFavoriteTooltip,
                            tint = if (isFavorite) ColorStarYellow else TextSecondaryMuted.copy(alpha = 0.5f),
                            modifier = Modifier.size(if (isMobile) 20.dp else 22.dp)
                        )
                    }
                }
            } else {
                // STANDARD MODE: Badges (Added X and/or Last Week Order)
                if (isOrdered || previousQuantityScaled > 0L) {
                    Column(
                        modifier = Modifier.width(if (isMobile) 100.dp else 125.dp),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        if (isOrdered) {
                            val currentFormatted = VeggieQuantityScaling.formatScaledQuantity(
                                quantityScaled,
                                allowDecimals,
                                decimalSeparator
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = ColorSuccessEmerald.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, ColorSuccessEmerald.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth().height(24.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = strings.addedOrderBadge(currentFormatted, unitName),
                                        fontSize = 10.5.sp,
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
                                shape = RoundedCornerShape(6.dp),
                                color = SurfaceContainerHighLight,
                                border = BorderStroke(1.dp, DividerBorder),
                                modifier = Modifier.fillMaxWidth().height(24.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = strings.lastOrderBadge(formattedPrev, unitName),
                                        fontSize = 10.5.sp,
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

                // VERY END: Star ☆ / ★ (excluded from Tab)
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier
                        .size(if (isMobile) 32.dp else 36.dp)
                        .focusProperties { canFocus = false }
                        .pointerHoverIcon(PointerIcon.Hand)
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = if (isFavorite) strings.unmarkFavoriteTooltip else strings.markFavoriteTooltip,
                        tint = if (isFavorite) ColorStarYellow else TextSecondaryMuted.copy(alpha = 0.5f),
                        modifier = Modifier.size(if (isMobile) 20.dp else 22.dp)
                    )
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
    val historyPage by veggieViewModel.historyPage.collectAsState()
    val historyPageSize by veggieViewModel.historyPageSize.collectAsState()
    val products by veggieViewModel.products.collectAsState()
    val productsById = remember(products) { products.associateBy { it.id } }
    val decimalSep = if (currentLanguage == Language.EN) '.' else ','

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
                    entry.allowDecimalsSnapshot,
                    decimalSeparator
                )
                val unitPriceCents = entry.effectiveUnitPriceCents
                    ?: productsById[entry.veggieProductId]?.estimatedPriceCents
                val lineTotalCents = entry.finalItemTotalCents ?: if (unitPriceCents != null && unitPriceCents > 0L) {
                    ((deliveredScaled.toDouble() / VEGGIE_QTY_SCALE.toDouble()) * unitPriceCents.toDouble()).roundToLong()
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
                        val priceSuffix = if (unitPriceCents != null && unitPriceCents > 0L) {
                            "  •  ${if (!isSettled) "~ " else ""}${Formatting.formatBrl(unitPriceCents)} / ${entry.unitNameSnapshot}"
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
