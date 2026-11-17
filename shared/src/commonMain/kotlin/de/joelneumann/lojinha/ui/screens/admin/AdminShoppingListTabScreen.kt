package de.joelneumann.lojinha.ui.screens.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
    val searchQuery by viewModel.searchQuery.collectAsState()
    val showExportApprovalDialog by viewModel.showExportApprovalDialog.collectAsState()
    val exportResultPath by viewModel.exportResultPath.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val strings = I18n.current

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val searchFocusRequester = remember { FocusRequester() }
    var showClearAllConfirm by remember { mutableStateOf(false) }

    // Drag-and-drop state
    var draggedItemId by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableStateOf(0f) }

    val matchingProducts = remember(searchQuery, viewModel.products.collectAsState().value) {
        viewModel.getMatchingProducts(searchQuery)
    }

    LaunchedEffect(focusTrigger) {
        if (!showClearAllConfirm && !showExportApprovalDialog && errorMessage == null) {
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
            val exactProduct = matchingProducts.firstOrNull { it.name.equals(trimmed, ignoreCase = true) }
            if (exactProduct != null) {
                viewModel.requestAddProduct(exactProduct)
            } else {
                viewModel.requestAddCustomItem(trimmed)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
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
        // Top Bar: Search/Add input + Count Pill + Export PDF + Clear All (matching AdminTopBar styling)
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val isCompact = maxWidth < 760.dp

            if (isCompact) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            SearchInputField(
                                query = searchQuery,
                                onQueryChange = viewModel::updateSearchQuery,
                                placeholder = strings.shoppingListSearchOrAddPlaceholder,
                                onSearchSubmitted = handleQuickAddFromSearch,
                                focusRequester = searchFocusRequester,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Button(
                            onClick = { handleQuickAddFromSearch() },
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
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = strings.shoppingListAddBtn,
                                color = SurfaceWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AdminCountPill(
                            text = strings.shoppingListCountText(items.size),
                            modifier = Modifier.fillMaxHeight()
                        )

                        Row(
                            modifier = Modifier.fillMaxHeight(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { viewModel.requestExportPdf() },
                                enabled = items.isNotEmpty(),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryNavy),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.fillMaxHeight()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Print,
                                    contentDescription = null,
                                    tint = SurfaceWhite,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = strings.shoppingListExportPdfBtn,
                                    color = SurfaceWhite,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    maxLines = 1
                                )
                            }

                            OutlinedButton(
                                onClick = { showClearAllConfirm = true },
                                enabled = items.isNotEmpty(),
                                border = BorderStroke(1.dp, if (items.isNotEmpty()) ColorDangerCrimson else DividerBorder),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorDangerCrimson),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.fillMaxHeight()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteSweep,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = strings.shoppingListClearAllBtn,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        SearchInputField(
                            query = searchQuery,
                            onQueryChange = viewModel::updateSearchQuery,
                            placeholder = strings.shoppingListSearchOrAddPlaceholder,
                            onSearchSubmitted = handleQuickAddFromSearch,
                            focusRequester = searchFocusRequester,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Button(
                        onClick = { handleQuickAddFromSearch() },
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
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = strings.shoppingListAddBtn,
                            color = SurfaceWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1
                        )
                    }

                    AdminCountPill(
                        text = strings.shoppingListCountText(items.size),
                        modifier = Modifier.fillMaxHeight()
                    )

                    Button(
                        onClick = { viewModel.requestExportPdf() },
                        enabled = items.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryNavy),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Print,
                            contentDescription = null,
                            tint = SurfaceWhite,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = strings.shoppingListExportPdfBtn,
                            color = SurfaceWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1
                        )
                    }

                    OutlinedButton(
                        onClick = { showClearAllConfirm = true },
                        enabled = items.isNotEmpty(),
                        border = BorderStroke(1.dp, if (items.isNotEmpty()) ColorDangerCrimson else DividerBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorDangerCrimson),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = strings.shoppingListClearAllBtn,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1
                        )
                    }
                }
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
                    matchingProducts.forEach { product ->
                        val alreadyInList = items.any { it.productId == product.id }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.requestAddProduct(product)
                                    viewModel.updateSearchQuery("")
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
                                    tint = AccentNavy,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = product.name,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.requestAddCustomItem(searchQuery.trim())
                                viewModel.updateSearchQuery("")
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
                            fontWeight = FontWeight.SemiBold,
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
        if (items.isNotEmpty()) {
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
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = strings.shoppingListColQuantity,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondaryMuted,
                    modifier = Modifier.width(170.dp)
                )
                Spacer(modifier = Modifier.width(164.dp)) // Status + Confirm + Delete space
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
                val currentItemsState by rememberUpdatedState(items)

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    itemsIndexed(
                        items = items,
                        key = { _, item -> item.id }
                    ) { index, item ->
                        val isDragging = draggedItemId == item.id
                        val currentIndex by rememberUpdatedState(index)

                        ShoppingListRowCard(
                            item = item,
                            isDragging = isDragging,
                            dragOffsetY = if (isDragging) dragOffsetY else 0f,
                            onDragModifier = Modifier
                                .pointerHoverIcon(PointerIcon.Hand)
                                .pointerInput(item.id) {
                                    detectDragGestures(
                                        onDragStart = {
                                            draggedItemId = item.id
                                            dragOffsetY = 0f
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            dragOffsetY += dragAmount.y

                                            val visibleItems = listState.layoutInfo.visibleItemsInfo
                                            val draggedInfo = visibleItems.firstOrNull { it.key == item.id } ?: return@detectDragGestures
                                            val draggedCenter = draggedInfo.offset + draggedInfo.size / 2f + dragOffsetY

                                            // Check if we crossed over an adjacent visible item
                                            val targetInfo = visibleItems.firstOrNull { candidate ->
                                                candidate.key != item.id &&
                                                    draggedCenter in candidate.offset.toFloat()..(candidate.offset + candidate.size).toFloat()
                                            }

                                            if (targetInfo != null) {
                                                val fromIdx = currentIndex
                                                val toIdx = targetInfo.index
                                                if (fromIdx in currentItemsState.indices && toIdx in currentItemsState.indices && fromIdx != toIdx) {
                                                    val offsetDelta = targetInfo.offset - draggedInfo.offset
                                                    dragOffsetY -= offsetDelta.toFloat()
                                                    viewModel.moveItem(fromIdx, toIdx)
                                                }
                                            }
                                        },
                                        onDragEnd = {
                                            draggedItemId = null
                                            dragOffsetY = 0f
                                            viewModel.persistCurrentOrder()
                                        },
                                        onDragCancel = {
                                            draggedItemId = null
                                            dragOffsetY = 0f
                                            viewModel.persistCurrentOrder()
                                        }
                                    )
                                },
                            onUpdateInPlace = { newName, newQty, confirm ->
                                viewModel.updateItemInPlace(item.id, newName, newQty, confirm = confirm)
                            },
                            onConfirmItem = { viewModel.confirmItem(item.id) },
                            onDeleteItem = { viewModel.deleteItem(item.id) }
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
private fun ShoppingListRowCard(
    item: ShoppingListItem,
    isDragging: Boolean,
    dragOffsetY: Float,
    onDragModifier: Modifier,
    onUpdateInPlace: (newName: String, newQuantity: String, confirm: Boolean) -> Unit,
    onConfirmItem: () -> Unit,
    onDeleteItem: () -> Unit
) {
    val strings = I18n.current
    val focusManager = LocalFocusManager.current

    var nameField by remember(item.id, item.name) {
        mutableStateOf(TextFieldValue(item.name))
    }
    var qtyField by remember(item.id, item.quantity) {
        mutableStateOf(TextFieldValue(item.quantity))
    }

    val (cardBg, borderColor) = when {
        isDragging -> SurfaceWhite to AccentNavy
        item.isPendingReview -> ColorWarningAmber.copy(alpha = 0.06f) to ColorWarningAmber.copy(alpha = 0.45f)
        item.isNewlyAdded -> ColorSuccessEmerald.copy(alpha = 0.05f) to ColorSuccessEmerald.copy(alpha = 0.35f)
        item.isContentChanged -> AccentNavy.copy(alpha = 0.05f) to AccentNavy.copy(alpha = 0.35f)
        else -> SurfaceWhite to DividerBorder
    }

    val (statusText, statusColor) = when {
        item.isPendingReview -> strings.shoppingListStatusPending to ColorWarningAmber
        item.isNewlyAdded -> strings.shoppingListStatusNew to ColorSuccessEmerald
        item.isContentChanged -> strings.shoppingListStatusEdited to AccentNavy
        else -> strings.shoppingListStatusConfirmed to ColorSuccessEmerald
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Drag Handle (non-focusable so Tab skips directly between textboxes)
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

            // Directly Editable Name Textbox
            InlineSelectAllTextField(
                value = nameField,
                onValueChange = { nameField = it },
                onCommit = { confirm ->
                    onUpdateInPlace(nameField.text, qtyField.text, confirm)
                },
                onTabNavigate = { reverse ->
                    focusManager.moveFocus(if (reverse) FocusDirection.Previous else FocusDirection.Next)
                },
                textStyle = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PrimaryNavy
                ),
                modifier = Modifier.weight(1f)
            )

            // Directly Editable Quantity Textbox
            InlineSelectAllTextField(
                value = qtyField,
                onValueChange = { qtyField = it },
                onCommit = { confirm ->
                    onUpdateInPlace(nameField.text, qtyField.text, confirm)
                },
                onTabNavigate = { reverse ->
                    focusManager.moveFocus(if (reverse) FocusDirection.Previous else FocusDirection.Next)
                },
                textStyle = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryNavy
                ),
                modifier = Modifier.width(170.dp)
            )

            // Status Helper Pill
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

            // Quick Confirm Button (for unadjusted items from last week)
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
                } else {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = ColorSuccessEmerald.copy(alpha = 0.55f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Delete Button (non-focusable so Tab skips directly between textboxes)
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
    }
}

@Composable
private fun InlineSelectAllTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onCommit: (confirm: Boolean) -> Unit,
    onTabNavigate: (reverse: Boolean) -> Unit,
    textStyle: TextStyle,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    // Select all text whenever the field gains focus (whether via mouse click or Tab)
    LaunchedEffect(isFocused) {
        if (isFocused) {
            yield()
            onValueChange(value.copy(selection = TextRange(0, value.text.length)))
        }
    }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = textStyle,
        cursorBrush = SolidColor(AccentNavy),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(
            onDone = {
                onCommit(true)
            }
        ),
        modifier = modifier
            .background(
                color = if (isFocused) SurfaceWhite else Color.Transparent,
                shape = RoundedCornerShape(6.dp)
            )
            .border(
                width = 1.dp,
                color = if (isFocused) AccentNavy else DividerBorder.copy(alpha = 0.6f),
                shape = RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 10.dp, vertical = 7.dp)
            .onFocusChanged { focusState ->
                val wasFocused = isFocused
                isFocused = focusState.isFocused
                if (wasFocused && !focusState.isFocused) {
                    onCommit(false)
                }
            }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.Tab -> {
                            onCommit(false)
                            onTabNavigate(event.isShiftPressed)
                            true
                        }
                        Key.Enter, Key.NumPadEnter -> {
                            onCommit(true)
                            onTabNavigate(false)
                            true
                        }
                        else -> false
                    }
                } else false
            }
    )
}
