package de.joelneumann.lojinha.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import de.joelneumann.lojinha.domain.model.BillingList
import de.joelneumann.lojinha.domain.model.BillingListType
import de.joelneumann.lojinha.ui.components.admin.bulk.*
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.theme.*
import de.joelneumann.lojinha.ui.utils.Formatting
import de.joelneumann.lojinha.ui.utils.sortedByAccentInsensitive
import de.joelneumann.lojinha.ui.viewmodel.admin.AdminBulkBillingViewModel

@Composable
fun AdminBulkBillingTabScreen(
    viewModel: AdminBulkBillingViewModel,
    onNavigateToTransactions: () -> Unit
) {
    val strings = I18n.current
    val billingLists by viewModel.billingLists.collectAsState()
    val selectedListId by viewModel.selectedListId.collectAsState()
    val activeUsers by viewModel.activeUsers.collectAsState()
    val variableAmounts by viewModel.variableAmounts.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val isExecutingCharges by viewModel.isExecutingCharges.collectAsState()

    var editingList by remember { mutableStateOf<BillingList?>(null) }
    var isCreatingNew by remember { mutableStateOf(false) }
    var showDeleteDialogFor by remember { mutableStateOf<String?>(null) }
    var showExecuteDialogFor by remember { mutableStateOf<BillingList?>(null) }

    val selectedList = billingLists.find { it.id == selectedListId }

    val contentLeftPane: @Composable (Modifier) -> Unit = { modifier ->
        Column(
            modifier = modifier
                .background(SurfaceWhite, RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = strings.bulkBillingTitle,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = PrimaryNavy,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = { isCreatingNew = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = strings.createNewList, tint = AccentNavy)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = DividerBorder)
            Spacer(modifier = Modifier.height(16.dp))

            if (billingLists.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(strings.noListsCreated, color = TextSecondaryMuted)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(billingLists, key = { it.id }) { list ->
                        val isSelected = list.id == selectedListId
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) AccentNavy.copy(alpha = 0.1f) else SurfaceContainerLight)
                                .clickable { viewModel.selectList(list.id) }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = list.name,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) AccentNavy else PrimaryNavy,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val listTypeWithPrice = if (list.type == BillingListType.FIXED) {
                                    "${strings.listTypeFixed} (${Formatting.formatBrl(list.basePrice ?: 0L)})"
                                } else strings.listTypeVariable
                                Text(
                                    text = listTypeWithPrice,
                                    fontSize = 12.sp,
                                    color = TextSecondaryMuted,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                if (list.lastExecutionTime != null) {
                                    Text(
                                        text = "${strings.lastExecutionTimeLabel} ${Formatting.formatTimestamp(list.lastExecutionTime)}",
                                        fontSize = 11.sp,
                                        color = TextSecondaryMuted,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                            Row {
                                IconButton(onClick = { editingList = list }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Edit, contentDescription = strings.editList, tint = PrimaryNavy, modifier = Modifier.size(16.dp))
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                IconButton(onClick = { showDeleteDialogFor = list.id }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Delete, contentDescription = strings.delete, tint = ColorDangerCrimson, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val contentRightPane: @Composable (Modifier, Boolean) -> Unit = { modifier, showBackButton ->
        Column(
            modifier = modifier
                .background(SurfaceWhite, RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            if (selectedList == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(strings.noListsCreated, color = TextSecondaryMuted)
                }
            } else {
                if (showBackButton) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { viewModel.selectList(null) }
                                .padding(vertical = 4.dp, horizontal = 2.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = strings.backBtn,
                                tint = AccentNavy,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                strings.backBtn,
                                color = AccentNavy,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                        }
                        IconButton(
                            onClick = { isCreatingNew = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = strings.createNewList, tint = AccentNavy)
                        }
                    }
                }

                Text(selectedList.name, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = PrimaryNavy)
                val typeText = if (selectedList.type == BillingListType.FIXED) {
                    "${strings.listTypeFixed} (${Formatting.formatBrl(selectedList.basePrice ?: 0L)})"
                } else strings.listTypeVariable
                Text(typeText, fontSize = 14.sp, color = TextSecondaryMuted, maxLines = 1, softWrap = false)

                if (!selectedList.comment.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("${strings.billingListCommentLabel}: ${selectedList.comment}", fontSize = 14.sp, color = TextSecondaryMuted)
                }

                if (selectedList.lastExecutionTime != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${strings.lastExecutionTimeLabel} ${Formatting.formatTimestamp(selectedList.lastExecutionTime)}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondaryMuted,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                val activeListUsers = selectedList.users.mapNotNull { listUser ->
                    activeUsers.find { it.id == listUser.userId && it.isActive && !it.isDeleted }?.let { it to listUser }
                }.sortedByAccentInsensitive { it.first.name }

                val totalExpectedAmount = if (selectedList.type == BillingListType.FIXED) {
                    activeListUsers.sumOf { it.second.quantity.coerceAtLeast(0) * (selectedList.basePrice ?: 0L).coerceAtLeast(0L) }
                } else {
                    activeListUsers.sumOf {
                        (variableAmounts["${selectedList.id}:${it.first.id}"] ?: variableAmounts[it.first.id] ?: 0L).coerceAtLeast(0L)
                    }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (activeListUsers.isEmpty()) {
                        item {
                            Text(strings.noUsersInList, color = TextSecondaryMuted, modifier = Modifier.padding(16.dp))
                        }
                    } else {
                        items(activeListUsers, key = { it.first.id }) { (user, listUser) ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = user.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = PrimaryNavy,
                                    modifier = Modifier.weight(1f)
                                )
                                
                                if (selectedList.type == BillingListType.FIXED) {
                                    val lineTotal = listUser.quantity * (selectedList.basePrice ?: 0L)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "${listUser.quantity}x",
                                            modifier = Modifier.widthIn(min = 32.dp),
                                            textAlign = TextAlign.End,
                                            fontSize = 13.sp,
                                            color = TextSecondaryMuted
                                        )
                                        Text(
                                            text = Formatting.formatBrl(lineTotal),
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp,
                                            color = PrimaryNavy,
                                            maxLines = 1,
                                            softWrap = false,
                                            textAlign = TextAlign.End,
                                            modifier = Modifier.widthIn(min = 100.dp)
                                        )
                                    }
                                } else {
                                    val currentAmount = variableAmounts["${selectedList.id}:${user.id}"] ?: variableAmounts[user.id] ?: 0L
                                    var amountStr by remember(selectedList.id, user.id) { 
                                        mutableStateOf(if (currentAmount == 0L) "" else Formatting.formatBrl(currentAmount).removePrefix("R$ ").trim()) 
                                    }
                                    LaunchedEffect(isExecutingCharges) {
                                        if (!isExecutingCharges && currentAmount == 0L && amountStr.isNotBlank()) {
                                            amountStr = ""
                                        }
                                    }
                                    OutlinedTextField(
                                        value = amountStr,
                                        onValueChange = { 
                                            val sanitized = it.filter { c -> c.isDigit() || c == '.' || c == ',' }
                                            amountStr = sanitized
                                            val cents = sanitized.replace(',', '.').toDoubleOrNull()
                                                ?.let { v -> kotlin.math.round(v * 100).toLong().coerceAtLeast(0L) } ?: 0L
                                            viewModel.setVariableAmount(selectedList.id, user.id, cents)
                                        },
                                        enabled = !isExecutingCharges,
                                        modifier = Modifier.width(120.dp),
                                        label = { Text(strings.amount, fontSize = 10.sp) },
                                        singleLine = true
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${strings.total}: ${Formatting.formatBrl(totalExpectedAmount)}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = PrimaryNavy,
                        maxLines = 1,
                        softWrap = false
                    )
                    Button(
                        onClick = { showExecuteDialogFor = selectedList },
                        colors = ButtonDefaults.buttonColors(containerColor = ColorSuccessEmerald),
                        enabled = !isExecutingCharges && activeListUsers.isNotEmpty() && totalExpectedAmount > 0
                    ) {
                        Text(strings.executeChargesBtn, fontWeight = FontWeight.Bold, color = SurfaceWhite, maxLines = 1)
                    }
                }
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        val isMobile = maxWidth < 750.dp
        if (isMobile) {
            if (selectedList == null) {
                contentLeftPane(Modifier.fillMaxSize())
            } else {
                contentRightPane(Modifier.fillMaxSize(), true)
            }
        } else {
            Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                contentLeftPane(Modifier.widthIn(min = 280.dp).weight(1f).fillMaxHeight())
                contentRightPane(Modifier.weight(2f).fillMaxHeight(), false)
            }
        }
    }

    if (isCreatingNew || editingList != null) {
        BillingListEditDialog(
            initialList = editingList,
            activeUsers = activeUsers.filter { !it.isDeleted },
            onSave = { updatedList ->
                viewModel.saveBillingList(updatedList)
                isCreatingNew = false
                editingList = null
            },
            onCancel = {
                isCreatingNew = false
                editingList = null
            }
        )
    }

    if (showDeleteDialogFor != null) {
        DeleteBillingListDialog(
            listName = billingLists.find { it.id == showDeleteDialogFor }?.name ?: "",
            onDismiss = { showDeleteDialogFor = null },
            onConfirm = {
                viewModel.deleteList(showDeleteDialogFor!!)
                showDeleteDialogFor = null
            }
        )
    }

    if (showExecuteDialogFor != null) {
        val list = showExecuteDialogFor!!
        val chargeableListUsers = list.users.filter { u ->
            val usr = activeUsers.find { it.id == u.userId }
            usr != null && usr.isActive && !usr.isDeleted
        }
        val total = if (list.type == BillingListType.FIXED) {
            chargeableListUsers.sumOf { it.quantity.coerceAtLeast(0) * (list.basePrice ?: 0L).coerceAtLeast(0L) }
        } else {
            chargeableListUsers.sumOf { viewModel.getVariableAmount(list.id, it.userId).coerceAtLeast(0L) }
        }
        val eligibleCount = chargeableListUsers.filter { 
            if (list.type == BillingListType.FIXED) it.quantity > 0 
            else viewModel.getVariableAmount(list.id, it.userId) > 0 
        }.size
        
        ExecuteChargesDialog(
            count = eligibleCount,
            totalFormatted = Formatting.formatBrl(total),
            isExecuting = isExecutingCharges,
            onDismiss = { showExecuteDialogFor = null },
            onConfirm = {
                val targetList = list
                viewModel.executeCharges(targetList) { success ->
                    showExecuteDialogFor = null
                    if (success) {
                        onNavigateToTransactions()
                    }
                }
            }
        )
    }

    if (errorMessage != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearErrorMessage() },
            containerColor = SurfaceWhite,
            shape = RoundedCornerShape(16.dp),
            title = { Text(strings.errorTitle, fontWeight = FontWeight.Bold, color = ColorDangerCrimson) },
            text = { Text(errorMessage ?: "") },
            confirmButton = {
                TextButton(onClick = { viewModel.clearErrorMessage() }) {
                    Text(strings.ok, color = AccentNavy, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}
