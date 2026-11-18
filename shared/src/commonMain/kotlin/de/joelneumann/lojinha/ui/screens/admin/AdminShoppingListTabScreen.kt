package de.joelneumann.lojinha.ui.screens.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import de.joelneumann.lojinha.domain.model.Language
import de.joelneumann.lojinha.domain.model.Product
import de.joelneumann.lojinha.domain.model.ShoppingListItem
import de.joelneumann.lojinha.domain.model.UnitType
import de.joelneumann.lojinha.ui.components.admin.AdminCountPill
import de.joelneumann.lojinha.ui.components.admin.shoppinglist.ShoppingListExportApprovalDialog
import de.joelneumann.lojinha.ui.components.general.AppVerticalScrollbar
import de.joelneumann.lojinha.ui.components.general.ConfirmationDialog
import de.joelneumann.lojinha.ui.components.general.SearchInputField
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.i18n.LanguageManager
import de.joelneumann.lojinha.ui.theme.*
import de.joelneumann.lojinha.ui.utils.Formatting
import de.joelneumann.lojinha.ui.utils.pageDown
import de.joelneumann.lojinha.ui.utils.pageUp
import de.joelneumann.lojinha.ui.utils.safeRequestFocus
import de.joelneumann.lojinha.ui.viewmodel.admin.AdminShoppingListViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

@Composable
fun AdminShoppingListTabScreen(
    viewModel: AdminShoppingListViewModel,
    onExportPdf: ((Language) -> Unit)? = null,
    onOpenExportedPdf: ((String) -> Unit)? = null,
    onRevealExportedFolder: ((String) -> Unit)? = null,
    onRegisterPageScroller: (((Boolean) -> Boolean) -> Unit)? = null,
    focusTrigger: Int = 0
) {
    val items by viewModel.items.collectAsState()
    val products by viewModel.products.collectAsState()
    val productsById by viewModel.productsById.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val pendingModal by viewModel.pendingModalRequest.collectAsState()
    val showExportApprovalDialog by viewModel.showExportApprovalDialog.collectAsState()
    val exportResultPath by viewModel.exportResultPath.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val strings = I18n.current

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val searchFocusRequester = remember { FocusRequester() }
    var showClearAllConfirm by remember { mutableStateOf(false) }

    // Drag-and-drop state
    var draggedItemId by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    var expectedLazyIndexAfterSwap by remember { mutableStateOf<Int?>(null) }
    val currentItemsState by rememberUpdatedState(items)

    val trySwapDraggedItem: (String) -> Unit = { activeId ->
        val visibleItems = listState.layoutInfo.visibleItemsInfo.filter { it.key != "top-anchor" }
        val draggedInfo = visibleItems.firstOrNull { it.key == activeId }
        val expectedIdx = expectedLazyIndexAfterSwap
        if (draggedInfo != null && (expectedIdx == null || draggedInfo.index == expectedIdx)) {
            expectedLazyIndexAfterSwap = null
            val draggedTop = draggedInfo.offset + dragOffsetY
            val draggedBottom = draggedTop + draggedInfo.size

            // Directional midpoint crossing prevents ping-pong oscillation
            val targetInfo = visibleItems.firstOrNull { candidate ->
                if (candidate.key == activeId) return@firstOrNull false
                val candidateMid = candidate.offset + candidate.size / 2f
                when {
                    dragOffsetY > 0f && candidate.index > draggedInfo.index -> draggedBottom > candidateMid
                    dragOffsetY < 0f && candidate.index < draggedInfo.index -> draggedTop < candidateMid
                    else -> false
                }
            }

            if (targetInfo != null) {
                val fromIdx = currentItemsState.indexOfFirst { it.id == activeId }
                val toIdx = currentItemsState.indexOfFirst { it.id == targetInfo.key }
                if (fromIdx in currentItemsState.indices && toIdx in currentItemsState.indices && fromIdx != toIdx) {
                    dragOffsetY -= (targetInfo.offset - draggedInfo.offset).toFloat()
                    expectedLazyIndexAfterSwap = targetInfo.index
                    viewModel.moveItem(fromIdx, toIdx)
                }
            }
        }
    }

    LaunchedEffect(draggedItemId) {
        while (draggedItemId != null) {
            val activeId = draggedItemId ?: break
            val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == activeId }
            if (info != null) {
                val viewportStart = listState.layoutInfo.viewportStartOffset.toFloat()
                val viewportEnd = listState.layoutInfo.viewportEndOffset.toFloat()
                val itemTop = info.offset + dragOffsetY
                val itemBottom = itemTop + info.size
                val scrollStep = when {
                    itemTop < viewportStart + 44f && listState.canScrollBackward -> -12f
                    itemBottom > viewportEnd - 44f && listState.canScrollForward -> 12f
                    else -> 0f
                }
                if (scrollStep != 0f) {
                    val consumed = listState.scrollBy(scrollStep)
                    dragOffsetY += consumed
                    trySwapDraggedItem(activeId)
                }
            }
            delay(16L)
        }
    }

    val matchingProducts = remember(searchQuery, products) {
        viewModel.getMatchingProducts(searchQuery)
    }
    var highlightedSuggestionIndex by remember(searchQuery, matchingProducts) {
        mutableStateOf(-1)
    }

    LaunchedEffect(focusTrigger, pendingModal, showClearAllConfirm, showExportApprovalDialog, errorMessage) {
        if (pendingModal == null && !showClearAllConfirm && !showExportApprovalDialog && errorMessage == null) {
            searchFocusRequester.safeRequestFocus()
        }
    }

    LaunchedEffect(onRegisterPageScroller, showClearAllConfirm, showExportApprovalDialog, errorMessage) {
        onRegisterPageScroller?.invoke { isDown ->
            if (!showClearAllConfirm && !showExportApprovalDialog && errorMessage == null) {
                coroutineScope.launch {
                    if (isDown) listState.pageDown() else listState.pageUp()
                }
                true
            } else false
        }
    }

    val handleQuickAddFromSearch = {
        val trimmed = searchQuery.trim()
        if (trimmed.isNotEmpty()) {
            if (highlightedSuggestionIndex in matchingProducts.indices) {
                viewModel.requestAddProduct(matchingProducts[highlightedSuggestionIndex])
            } else {
                viewModel.requestAddCustomItem(trimmed, forceCustom = true)
            }
            viewModel.updateSearchQuery("")
            highlightedSuggestionIndex = -1
        }
    }

    val searchKeyHandler: (KeyEvent) -> Boolean = { keyEvent ->
        if (keyEvent.type == KeyEventType.KeyDown && searchQuery.isNotBlank()) {
            when (keyEvent.key) {
                Key.Tab -> {
                    if (matchingProducts.isNotEmpty()) {
                        highlightedSuggestionIndex = if (keyEvent.isShiftPressed) {
                            if (highlightedSuggestionIndex <= -1) matchingProducts.lastIndex else highlightedSuggestionIndex - 1
                        } else {
                            if (highlightedSuggestionIndex >= matchingProducts.lastIndex) -1 else highlightedSuggestionIndex + 1
                        }
                        true
                    } else {
                        false
                    }
                }
                Key.DirectionDown -> {
                    if (matchingProducts.isNotEmpty()) {
                        highlightedSuggestionIndex = if (highlightedSuggestionIndex >= matchingProducts.lastIndex) -1 else highlightedSuggestionIndex + 1
                        true
                    } else false
                }
                Key.DirectionUp -> {
                    if (matchingProducts.isNotEmpty()) {
                        highlightedSuggestionIndex = if (highlightedSuggestionIndex <= -1) matchingProducts.lastIndex else highlightedSuggestionIndex - 1
                        true
                    } else false
                }
                Key.Escape -> {
                    if (highlightedSuggestionIndex >= 0) {
                        highlightedSuggestionIndex = -1
                        true
                    } else false
                }
                Key.Enter, Key.NumPadEnter -> {
                    handleQuickAddFromSearch()
                    true
                }
                else -> false
            }
        } else false
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isCompact = maxWidth < 760.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { focusManager.clearFocus() })
                }
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && !showClearAllConfirm && !showExportApprovalDialog && errorMessage == null) {
                        when (event.key) {
                            Key.PageDown -> {
                                coroutineScope.launch { listState.pageDown() }
                                true
                            }
                            Key.PageUp -> {
                                coroutineScope.launch { listState.pageUp() }
                                true
                            }
                            else -> false
                        }
                    } else false
                }
        ) {
            // Top Bar: Search/Add input + Count Pill + Export PDF + Clear All
            if (isCompact) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ShoppingListSearchAndAddBar(
                        searchQuery = searchQuery,
                        onQueryChange = viewModel::updateSearchQuery,
                        placeholder = strings.shoppingListSearchOrAddPlaceholder,
                        addLabel = strings.shoppingListAddBtn,
                        onQuickAdd = handleQuickAddFromSearch,
                        onKeyDown = searchKeyHandler,
                        focusRequester = searchFocusRequester,
                        isCompact = true,
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    )
                    ShoppingListToolbarActions(
                        countText = strings.shoppingListCountText(items.size),
                        exportLabel = strings.shoppingListExportPdfBtn,
                        clearAllLabel = strings.shoppingListClearAllBtn,
                        hasItems = items.isNotEmpty(),
                        isCompact = true,
                        onExportClick = {
                            focusManager.clearFocus()
                            viewModel.requestExportPdf()
                        },
                        onClearAllClick = {
                            focusManager.clearFocus()
                            showClearAllConfirm = true
                        },
                        modifier = Modifier.fillMaxWidth().height(40.dp)
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ShoppingListSearchAndAddBar(
                        searchQuery = searchQuery,
                        onQueryChange = viewModel::updateSearchQuery,
                        placeholder = strings.shoppingListSearchOrAddPlaceholder,
                        addLabel = strings.shoppingListAddBtn,
                        onQuickAdd = handleQuickAddFromSearch,
                        onKeyDown = searchKeyHandler,
                        focusRequester = searchFocusRequester,
                        isCompact = false,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                    ShoppingListToolbarActions(
                        countText = strings.shoppingListCountText(items.size),
                        exportLabel = strings.shoppingListExportPdfBtn,
                        clearAllLabel = strings.shoppingListClearAllBtn,
                        hasItems = items.isNotEmpty(),
                        isCompact = false,
                        onExportClick = {
                            focusManager.clearFocus()
                            viewModel.requestExportPdf()
                        },
                        onClearAllClick = {
                            focusManager.clearFocus()
                            showClearAllConfirm = true
                        },
                        modifier = Modifier.fillMaxHeight()
                    )
                }
            }

        // Inline Search Suggestions Dropdown when typing
        if (searchQuery.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                color = SurfaceWhite,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, DividerBorder),
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    matchingProducts.forEachIndexed { idx, product ->
                        val alreadyInList = items.any { it.productId == product.id }
                        val isHighlighted = idx == highlightedSuggestionIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isHighlighted) AccentNavy.copy(alpha = 0.10f) else Color.Transparent)
                                .clickable {
                                    viewModel.requestAddProduct(product)
                                    viewModel.updateSearchQuery("")
                                    highlightedSuggestionIndex = -1
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Inventory2,
                                    contentDescription = null,
                                    tint = if (isHighlighted) AccentNavy else AccentNavy.copy(alpha = 0.75f),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = product.name,
                                    fontSize = 13.sp,
                                    fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.SemiBold,
                                    color = PrimaryNavy
                                )
                                Text(
                                    text = "• ${strings.stock}: ${Formatting.formatQuantity(product.stockQuantity, product.unitType, strings)}",
                                    fontSize = 12.sp,
                                    color = if (product.stockQuantity <= 0L) ColorDangerCrimson else TextSecondaryMuted
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (alreadyInList) {
                                    Surface(
                                        color = ColorWarningAmber.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "✓",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = ColorWarningAmber,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "+ ${strings.shoppingListAddBtn}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentNavy
                                )
                            }
                        }
                        HorizontalDivider(color = DividerBorder.copy(alpha = 0.6f))
                    }

                    // Option to add typed text as custom item
                    val isCustomDefault = highlightedSuggestionIndex == -1
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isCustomDefault) ColorSuccessEmerald.copy(alpha = 0.08f) else Color.Transparent)
                            .clickable {
                                viewModel.requestAddCustomItem(searchQuery.trim(), forceCustom = true)
                                viewModel.updateSearchQuery("")
                                highlightedSuggestionIndex = -1
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddCircleOutline,
                            contentDescription = null,
                            tint = ColorSuccessEmerald,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = strings.shoppingListAddCustomItem(searchQuery.trim()),
                            fontSize = 13.sp,
                            fontWeight = if (isCustomDefault) FontWeight.Bold else FontWeight.SemiBold,
                            color = ColorSuccessEmerald
                        )
                    }
                }
            }
        }

        // Export Result Banner (Desktop PDF viewer / reveal folder shortcut)
        exportResultPath?.let { path ->
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                color = ColorSuccessEmerald.copy(alpha = 0.1f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, ColorSuccessEmerald.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = ColorSuccessEmerald,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "${strings.pdfExportSuccess}: $path",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = PrimaryNavy,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (onOpenExportedPdf != null && (path.contains("/") || path.contains("\\"))) {
                            OutlinedButton(
                                onClick = { onOpenExportedPdf.invoke(path) },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text(strings.openPdf, fontSize = 12.sp)
                            }
                        }
                        if (onRevealExportedFolder != null && (path.contains("/") || path.contains("\\"))) {
                            OutlinedButton(
                                onClick = { onRevealExportedFolder(path) },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text(strings.revealInFolder, fontSize = 12.sp)
                            }
                        }
                        IconButton(
                            onClick = { viewModel.setExportResultPath(null) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = strings.clear, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Column Headers
        if (items.isNotEmpty() && !isCompact) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.width(36.dp)) // Drag handle space
                Text(
                    text = strings.shoppingListColName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondaryMuted,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = strings.shoppingListColStock,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondaryMuted,
                    modifier = Modifier.width(105.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = strings.shoppingListColQuantity,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondaryMuted,
                    modifier = Modifier.width(170.dp)
                )
                Spacer(modifier = Modifier.width(184.dp)) // Status + Confirm + Delete + Scrollbar space
            }
        }

        // Main List Content
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (items.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ShoppingCartCheckout,
                            contentDescription = null,
                            tint = TextSecondaryMuted.copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = strings.shoppingListEmptyTitle,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PrimaryNavy
                        )
                        Text(
                            text = strings.shoppingListEmptyMsg,
                            fontSize = 13.sp,
                            color = TextSecondaryMuted
                        )
                    }
                }
            } else {
                val itemIdsKey = remember(items) { items.map { it.id } }
                val qtyFocusRequesters = remember(itemIdsKey) {
                    items.associate { it.id to FocusRequester() }
                }
                val focusQuantityAt: (Int) -> Unit = { targetIndex ->
                    if (targetIndex in currentItemsState.indices) {
                        val targetId = currentItemsState[targetIndex].id
                        val isVisible = listState.layoutInfo.visibleItemsInfo.any { it.key == targetId }
                        if (isVisible) {
                            try { qtyFocusRequesters[targetId]?.requestFocus() } catch (_: Exception) {}
                        } else {
                            coroutineScope.launch {
                                listState.scrollToItem(targetIndex + 1)
                                yield()
                                qtyFocusRequesters[targetId]?.safeRequestFocus()
                            }
                        }
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    // Fixed top anchor prevents LazyColumn from jumping scroll when swapping the first item
                    item(key = "top-anchor") {
                        Spacer(modifier = Modifier.height(1.dp))
                    }

                    itemsIndexed(
                        items = items,
                        key = { _, item -> item.id }
                    ) { index, item ->
                        val isDragging = draggedItemId == item.id
                        val linkedProduct = item.productId?.let { productsById[it] }

                        ShoppingListRowCard(
                            item = item,
                            linkedProduct = linkedProduct,
                            isCompact = isCompact,
                            isDragging = isDragging,
                            dragOffsetY = if (isDragging) dragOffsetY else 0f,
                            qtyFocusRequester = qtyFocusRequesters[item.id],
                            onFocusOwnQty = { focusQuantityAt(index) },
                            onTabQuantity = { reverse ->
                                val targetIndex = if (reverse) index - 1 else index + 1
                                if (targetIndex in currentItemsState.indices) {
                                    focusQuantityAt(targetIndex)
                                } else {
                                    focusManager.clearFocus()
                                }
                            },
                            onDragModifier = Modifier
                                .pointerHoverIcon(PointerIcon.Hand)
                                .pointerInput(item.id) {
                                    detectDragGestures(
                                        onDragStart = {
                                            focusManager.clearFocus()
                                            draggedItemId = item.id
                                            dragOffsetY = 0f
                                            expectedLazyIndexAfterSwap = null
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            dragOffsetY += dragAmount.y
                                            trySwapDraggedItem(item.id)
                                        },
                                        onDragEnd = {
                                            draggedItemId = null
                                            dragOffsetY = 0f
                                            expectedLazyIndexAfterSwap = null
                                            viewModel.persistCurrentOrder()
                                        },
                                        onDragCancel = {
                                            draggedItemId = null
                                            dragOffsetY = 0f
                                            expectedLazyIndexAfterSwap = null
                                            viewModel.persistCurrentOrder()
                                        }
                                    )
                                },
                            onUpdateInPlace = { newName, newQty, confirm ->
                                viewModel.updateItemInPlace(item.id, newName, newQty, confirm = confirm)
                            },
                            onConfirmItem = {
                                focusManager.clearFocus()
                                viewModel.confirmItem(item.id)
                            },
                            onUnconfirmItem = {
                                focusManager.clearFocus()
                                viewModel.unconfirmItem(item.id)
                            },
                            onDeleteItem = {
                                focusManager.clearFocus()
                                viewModel.deleteItem(item.id)
                            }
                        )
                    }
                }

                AppVerticalScrollbar(
                    scrollState = listState,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .padding(vertical = 4.dp)
                )
            }
        }
        }
    }

    if (showClearAllConfirm) {
        ConfirmationDialog(
            title = strings.shoppingListClearAllTitle,
            message = strings.shoppingListClearAllMsg,
            confirmText = strings.shoppingListClearAllBtn,
            cancelText = strings.cancel,
            onDismiss = { showClearAllConfirm = false },
            onConfirm = {
                showClearAllConfirm = false
                viewModel.clearAllItems()
            },
            confirmButtonColor = ColorDangerCrimson
        )
    }

    if (showExportApprovalDialog) {
        ShoppingListExportApprovalDialog(
            items = items,
            productsById = productsById,
            onDismiss = { viewModel.dismissExportApprovalDialog() },
            onConfirmExport = {
                viewModel.dismissExportApprovalDialog()
                onExportPdf?.invoke(LanguageManager.currentLanguage)
            }
        )
    }

    if (errorMessage != null) {
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            shape = RoundedCornerShape(16.dp),
            containerColor = SurfaceWhite,
            title = {
                Text(
                    text = strings.errorTitle,
                    fontWeight = FontWeight.Bold,
                    color = ColorDangerCrimson
                )
            },
            text = { Text(errorMessage ?: "") },
            confirmButton = {
                Button(
                    onClick = viewModel::clearError,
                    colors = ButtonDefaults.buttonColors(containerColor = ColorDangerCrimson)
                ) {
                    Text(strings.ok)
                }
            }
        )
    }
}

@Composable
private fun ShoppingListSearchAndAddBar(
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    addLabel: String,
    onQuickAdd: () -> Unit,
    onKeyDown: (KeyEvent) -> Boolean,
    focusRequester: FocusRequester,
    isCompact: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (isCompact) 8.dp else 12.dp)
    ) {
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            SearchInputField(
                query = searchQuery,
                onQueryChange = onQueryChange,
                placeholder = placeholder,
                onSearchSubmitted = onQuickAdd,
                onKeyDown = onKeyDown,
                focusRequester = focusRequester,
                modifier = Modifier.fillMaxSize()
            )
        }
        Button(
            onClick = onQuickAdd,
            enabled = searchQuery.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = AccentNavy),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxHeight()
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = SurfaceWhite,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(if (isCompact) 4.dp else 6.dp))
            Text(
                text = addLabel,
                color = SurfaceWhite,
                fontWeight = FontWeight.Bold,
                fontSize = if (isCompact) 13.sp else 14.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ShoppingListToolbarActions(
    countText: String,
    exportLabel: String,
    clearAllLabel: String,
    hasItems: Boolean,
    isCompact: Boolean,
    onExportClick: () -> Unit,
    onClearAllClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val iconSize = if (isCompact) 16.dp else 18.dp
    val fontSize = if (isCompact) 12.sp else 14.sp
    val shape = RoundedCornerShape(if (isCompact) 10.dp else 12.dp)
    val padding = if (isCompact) PaddingValues(horizontal = 12.dp, vertical = 6.dp) else ButtonDefaults.ContentPadding

    Row(
        modifier = modifier,
        horizontalArrangement = if (isCompact) Arrangement.SpaceBetween else Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AdminCountPill(
            text = countText,
            modifier = Modifier.fillMaxHeight()
        )
        Row(
            modifier = Modifier.fillMaxHeight(),
            horizontalArrangement = Arrangement.spacedBy(if (isCompact) 8.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onExportClick,
                enabled = hasItems,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryNavy),
                shape = shape,
                contentPadding = padding,
                modifier = Modifier.fillMaxHeight()
            ) {
                Icon(
                    imageVector = Icons.Default.Print,
                    contentDescription = null,
                    tint = SurfaceWhite,
                    modifier = Modifier.size(iconSize)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = exportLabel,
                    color = SurfaceWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = fontSize,
                    maxLines = 1
                )
            }
            OutlinedButton(
                onClick = onClearAllClick,
                enabled = hasItems,
                border = BorderStroke(1.dp, if (hasItems) ColorDangerCrimson else DividerBorder),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorDangerCrimson),
                shape = shape,
                contentPadding = padding,
                modifier = Modifier.fillMaxHeight()
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = null,
                    modifier = Modifier.size(iconSize)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = clearAllLabel,
                    fontWeight = FontWeight.Bold,
                    fontSize = fontSize,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun ShoppingListRowCard(
    item: ShoppingListItem,
    linkedProduct: Product?,
    isCompact: Boolean,
    isDragging: Boolean,
    dragOffsetY: Float,
    qtyFocusRequester: FocusRequester?,
    onFocusOwnQty: () -> Unit,
    onTabQuantity: (reverse: Boolean) -> Unit,
    onDragModifier: Modifier,
    onUpdateInPlace: (newName: String, newQuantity: String, confirm: Boolean) -> Unit,
    onConfirmItem: () -> Unit,
    onUnconfirmItem: () -> Unit,
    onDeleteItem: () -> Unit
) {
    val strings = I18n.current
    val focusManager = LocalFocusManager.current
    val unitType = linkedProduct?.unitType
    val unitLabel = when (unitType) {
        UnitType.PIECE -> strings.unitPieceBadge
        UnitType.WEIGHT -> "kg"
        null -> null
    }

    val editableQty = remember(item.id, item.quantity, unitType) {
        AdminShoppingListViewModel.extractEditableNumericPart(item.quantity, unitType)
    }

    var nameField by remember(item.id, item.name) {
        mutableStateOf(TextFieldValue(item.name))
    }
    var qtyField by remember(item.id, editableQty) {
        mutableStateOf(TextFieldValue(editableQty))
    }

    val (cardBg, borderColor) = when {
        isDragging -> SurfaceWhite to AccentNavy
        item.isPendingReview -> ColorWarningAmber.copy(alpha = 0.06f) to ColorWarningAmber.copy(alpha = 0.45f)
        else -> SurfaceWhite to DividerBorder
    }

    val (statusText, statusColor) = when {
        item.isPendingReview -> strings.shoppingListStatusPending to ColorWarningAmber
        item.isNewlyAdded -> strings.shoppingListStatusNew to ColorSuccessEmerald
        item.isContentChanged -> strings.shoppingListStatusEdited to AccentNavy
        else -> strings.shoppingListStatusConfirmed to ColorSuccessEmerald
    }

    val commitIfChanged = { confirm: Boolean ->
        val validName = nameField.text.trim().ifBlank { item.name }
        val normalizedQty = AdminShoppingListViewModel.normalizeInlineQuantity(qtyField.text, unitType)
        if (nameField.text.isBlank()) {
            nameField = TextFieldValue(item.name)
        }
        if (normalizedQty == null) {
            qtyField = TextFieldValue(editableQty)
        }
        val effectiveQty = normalizedQty ?: item.quantity
        val hasTextChanges = validName != item.name ||
            (normalizedQty != null && normalizedQty != (AdminShoppingListViewModel.normalizeInlineQuantity(item.quantity, unitType) ?: item.quantity))
        if (hasTextChanges || (confirm && normalizedQty != null)) {
            onUpdateInPlace(validName, effectiveQty, confirm)
        }
    }

    val resetFieldsAndClearFocus = {
        nameField = TextFieldValue(item.name)
        qtyField = TextFieldValue(editableQty)
        focusManager.clearFocus()
    }

    val dragHandle: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .size(28.dp)
                .focusProperties { canFocus = false }
                .then(onDragModifier),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = strings.shoppingListDragHandleTooltip,
                tint = if (isDragging) AccentNavy else TextSecondaryMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }

    val nameBox: @Composable (Modifier) -> Unit = { mod ->
        InlineSelectAllTextField(
            value = nameField,
            onValueChange = { nameField = it },
            onCommit = { confirm -> commitIfChanged(confirm) },
            onCancel = resetFieldsAndClearFocus,
            onEnterAction = {
                commitIfChanged(true)
                focusManager.clearFocus()
            },
            onTabNavigate = { reverse ->
                if (reverse) {
                    focusManager.moveFocus(FocusDirection.Previous)
                } else {
                    onFocusOwnQty()
                }
            },
            textStyle = TextStyle(
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = PrimaryNavy
            ),
            selectAllOnFocus = false,
            modifier = mod
        )
    }

    val stockBox: @Composable (Modifier) -> Unit = { mod ->
        Box(modifier = mod, contentAlignment = Alignment.CenterStart) {
            if (linkedProduct != null) {
                val isZeroStock = linkedProduct.stockQuantity <= 0L
                Surface(
                    color = if (isZeroStock) ColorDangerCrimson.copy(alpha = 0.1f) else SurfaceContainerLight,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(
                        1.dp,
                        if (isZeroStock) ColorDangerCrimson.copy(alpha = 0.35f) else DividerBorder
                    )
                ) {
                    Text(
                        text = Formatting.formatQuantity(linkedProduct.stockQuantity, linkedProduct.unitType, strings),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isZeroStock) ColorDangerCrimson else TextSecondarySubtle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            } else {
                Text(
                    text = "-",
                    fontSize = 13.sp,
                    color = TextSecondaryMuted,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }
    }

    val qtyBox: @Composable (Modifier) -> Unit = { mod ->
        Row(
            modifier = mod,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            InlineSelectAllTextField(
                value = qtyField,
                onValueChange = { newVal ->
                    if (AdminShoppingListViewModel.isAllowedQuantityText(newVal.text, unitType)) {
                        qtyField = newVal
                    }
                },
                onCommit = { confirm -> commitIfChanged(confirm) },
                onCancel = resetFieldsAndClearFocus,
                onEnterAction = {
                    commitIfChanged(true)
                    onTabQuantity(false)
                },
                onTabNavigate = onTabQuantity,
                textStyle = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryNavy
                ),
                selectAllOnFocus = true,
                focusRequester = qtyFocusRequester,
                modifier = Modifier.weight(1f)
            )

            if (unitLabel != null) {
                Text(
                    text = unitLabel,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondaryMuted,
                    maxLines = 1,
                    modifier = Modifier.width(34.dp)
                )
            }
        }
    }

    val statusPill: @Composable () -> Unit = {
        Surface(
            color = statusColor.copy(alpha = 0.14f),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.width(92.dp)
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = statusText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = statusColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    val confirmSlot: @Composable () -> Unit = {
        Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            if (item.isPendingReview) {
                IconButton(
                    onClick = onConfirmItem,
                    modifier = Modifier
                        .size(32.dp)
                        .focusProperties { canFocus = false }
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircleOutline,
                        contentDescription = strings.shoppingListConfirmUnchangedTooltip,
                        tint = ColorSuccessEmerald,
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else if (!item.isNewlyAdded) {
                IconButton(
                    onClick = onUnconfirmItem,
                    modifier = Modifier
                        .size(32.dp)
                        .focusProperties { canFocus = false }
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = strings.shoppingListMarkUnadjustedTooltip,
                        tint = ColorSuccessEmerald.copy(alpha = 0.75f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }

    val deleteBtn: @Composable () -> Unit = {
        IconButton(
            onClick = onDeleteItem,
            modifier = Modifier
                .size(32.dp)
                .focusProperties { canFocus = false }
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = strings.shoppingListDeleteItemTooltip,
                tint = ColorDangerCrimson,
                modifier = Modifier.size(18.dp)
            )
        }
    }

    Surface(
        color = cardBg,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(if (isDragging) 1.5.dp else 1.dp, borderColor),
        shadowElevation = if (isDragging) 8.dp else 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer {
                translationY = dragOffsetY
            }
    ) {
        if (isCompact) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    dragHandle()
                    nameBox(Modifier.weight(1f))
                    deleteBtn()
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 36.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    stockBox(Modifier.wrapContentWidth())
                    qtyBox(Modifier.weight(1f))
                    statusPill()
                    confirmSlot()
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                dragHandle()
                nameBox(Modifier.weight(1f))
                stockBox(Modifier.width(105.dp))
                qtyBox(Modifier.width(170.dp))
                statusPill()
                confirmSlot()
                deleteBtn()
            }
        }
    }
}

@Composable
private fun InlineSelectAllTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onCommit: (confirm: Boolean) -> Unit,
    onCancel: () -> Unit,
    onEnterAction: () -> Unit,
    onTabNavigate: (reverse: Boolean) -> Unit,
    textStyle: TextStyle,
    selectAllOnFocus: Boolean = false,
    focusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    var selectAllPending by remember { mutableStateOf(false) }
    var suppressNextBlurCommit by remember { mutableStateOf(false) }
    val latestValue by rememberUpdatedState(value)
    val coroutineScope = rememberCoroutineScope()
    val internalFocusRequester = remember(focusRequester) { focusRequester ?: FocusRequester() }

    LaunchedEffect(selectAllPending) {
        if (selectAllPending) {
            delay(250L)
            selectAllPending = false
        }
    }

    // Ensure full selection on initial focus gain (including keyboard Tab/Enter)
    LaunchedEffect(isFocused, selectAllOnFocus) {
        if (isFocused && selectAllOnFocus) {
            yield()
            if (isFocused && selectAllPending) {
                onValueChange(latestValue.copy(selection = TextRange(0, latestValue.text.length)))
            }
        }
    }

    BasicTextField(
        value = value,
        onValueChange = { newValue ->
            if (selectAllOnFocus && selectAllPending && isFocused && newValue.text == latestValue.text) {
                // Preserve full selection while the initial focus click settles
                onValueChange(newValue.copy(selection = TextRange(0, newValue.text.length)))
            } else {
                selectAllPending = false
                onValueChange(newValue)
            }
        },
        singleLine = true,
        textStyle = textStyle,
        cursorBrush = SolidColor(AccentNavy),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(
            onDone = { onEnterAction() }
        ),
        modifier = modifier
            .focusRequester(internalFocusRequester)
            .onFocusChanged { focusState ->
                val wasFocused = isFocused
                isFocused = focusState.isFocused
                if (!wasFocused && focusState.isFocused) {
                    suppressNextBlurCommit = false
                    if (selectAllOnFocus) {
                        selectAllPending = true
                        onValueChange(latestValue.copy(selection = TextRange(0, latestValue.text.length)))
                    }
                } else if (wasFocused && !focusState.isFocused) {
                    selectAllPending = false
                    if (suppressNextBlurCommit) {
                        suppressNextBlurCommit = false
                    } else {
                        // Collapse selection on blur so unfocused text never stays highlighted
                        if (!latestValue.selection.collapsed) {
                            onValueChange(latestValue.copy(selection = TextRange(latestValue.text.length)))
                        }
                        onCommit(false)
                    }
                }
            }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    if (
                        event.key == Key.DirectionLeft ||
                        event.key == Key.DirectionRight ||
                        event.key == Key.MoveHome ||
                        event.key == Key.MoveEnd
                    ) {
                        selectAllPending = false
                    }
                    when (event.key) {
                        Key.Escape -> {
                            selectAllPending = false
                            suppressNextBlurCommit = true
                            onCancel()
                            true
                        }
                        Key.Tab -> {
                            selectAllPending = false
                            onCommit(false)
                            onTabNavigate(event.isShiftPressed)
                            true
                        }
                        Key.Enter, Key.NumPadEnter -> {
                            selectAllPending = false
                            onEnterAction()
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .pointerInput(selectAllOnFocus) {
                if (selectAllOnFocus) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Final)
                            if (event.type == PointerEventType.Release && selectAllPending) {
                                coroutineScope.launch {
                                    yield()
                                    selectAllPending = false
                                }
                            }
                        }
                    }
                }
            },
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = if (isFocused) SurfaceWhite else Color.Transparent,
                        shape = RoundedCornerShape(6.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = if (isFocused) AccentNavy else DividerBorder.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                innerTextField()
            }
        }
    )
}
