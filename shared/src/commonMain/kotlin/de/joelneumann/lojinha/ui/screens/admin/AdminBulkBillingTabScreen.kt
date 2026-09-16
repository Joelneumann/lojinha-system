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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.joelneumann.lojinha.domain.model.BillingList
import de.joelneumann.lojinha.domain.model.BillingListType
import de.joelneumann.lojinha.ui.components.admin.bulk.*
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.theme.*
import de.joelneumann.lojinha.ui.utils.Formatting
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
                Text(strings.bulkBillingTitle, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = PrimaryNavy)
                IconButton(onClick = { isCreatingNew = true }) {
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
                            Column(modifier = Modifier.weight(1f)) {
                                Text(list.name, fontWeight = FontWeight.Bold, color = if (isSelected) AccentNavy else PrimaryNavy)
                                Text(
                                    if (list.type == BillingListType.FIXED) strings.listTypeFixed else strings.listTypeVariable,
                                    fontSize = 12.sp,
                                    color = TextSecondaryMuted
                                )
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.selectList(null) }
                            .padding(bottom = 8.dp)
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
                }

                Text(selectedList.name, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = PrimaryNavy)
                val typeText = if (selectedList.type == BillingListType.FIXED) {
                    "${strings.listTypeFixed} (${Formatting.formatBrl(selectedList.basePrice ?: 0L)})"
                } else strings.listTypeVariable
                Text(typeText, fontSize = 14.sp, color = TextSecondaryMuted)

                if (!selectedList.comment.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("${strings.billingListCommentLabel}: ${selectedList.comment}", fontSize = 14.sp, color = TextSecondaryMuted)
                }

                Spacer(modifier = Modifier.height(16.dp))

                val sortedUsers = selectedList.users.mapNotNull { listUser ->
                    activeUsers.find { it.id == listUser.userId }?.let { it to listUser }
                }.sortedBy { it.first.name.lowercase() }

                val totalExpectedAmount = if (selectedList.type == BillingListType.FIXED) {
                    sortedUsers.sumOf { it.second.quantity * (selectedList.basePrice ?: 0L) }
                } else {
                    sortedUsers.sumOf { variableAmounts[it.first.id] ?: 0L }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (sortedUsers.isEmpty()) {
                        item {
                            Text(strings.noUsersInList, color = TextSecondaryMuted, modifier = Modifier.padding(16.dp))
                        }
                    } else {
                        items(sortedUsers, key = { it.first.id }) { (user, listUser) ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(user.name, modifier = Modifier.weight(1f))
                                
                                if (selectedList.type == BillingListType.FIXED) {
                                    val lineTotal = listUser.quantity * (selectedList.basePrice ?: 0L)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("${listUser.quantity}x", modifier = Modifier.width(40.dp))
                                        Text(Formatting.formatBrl(lineTotal), modifier = Modifier.width(100.dp).padding(start = 8.dp))
                                    }
                                } else {
                                    val currentAmount = variableAmounts[user.id] ?: 0L
                                    var amountStr by remember(selectedList.id, user.id) { 
                                        mutableStateOf(if (currentAmount == 0L) "" else (currentAmount.toDouble() / 100.0).toString()) 
                                    }
                                    OutlinedTextField(
                                        value = amountStr,
                                        onValueChange = { amountStr = it; 
                                            val cents = it.replace(',', '.').toDoubleOrNull()?.let { v -> kotlin.math.round(v * 100).toLong() } ?: 0L
                                            viewModel.setVariableAmount(user.id, cents)
                                        },
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
                    Text("${strings.total}: ${Formatting.formatBrl(totalExpectedAmount)}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Button(
                        onClick = { showExecuteDialogFor = selectedList },
                        colors = ButtonDefaults.buttonColors(containerColor = ColorSuccessEmerald),
                        enabled = selectedList.users.isNotEmpty() && totalExpectedAmount > 0
                    ) {
                        Text(strings.executeChargesBtn, fontWeight = FontWeight.Bold, color = SurfaceWhite)
                    }
                }
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        val isMobile = maxWidth < 700.dp
        if (isMobile) {
            if (selectedList == null) {
                contentLeftPane(Modifier.fillMaxSize())
            } else {
                contentRightPane(Modifier.fillMaxSize(), true)
            }
        } else {
            Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                contentLeftPane(Modifier.weight(1f).fillMaxHeight())
                contentRightPane(Modifier.weight(2f).fillMaxHeight(), false)
            }
        }
    }

    if (isCreatingNew || editingList != null) {
        BillingListEditDialog(
            initialList = editingList,
            activeUsers = activeUsers.filter { it.isActive && !it.isDeleted },
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
        var total = 0L
        if (list.type == BillingListType.FIXED) {
            total = list.users.sumOf { it.quantity * (list.basePrice ?: 0L) }
        } else {
            total = list.users.sumOf { variableAmounts[it.userId] ?: 0L }
        }
        
        ExecuteChargesDialog(
            count = list.users.filter { 
                if (list.type == BillingListType.FIXED) it.quantity > 0 
                else (variableAmounts[it.userId] ?: 0L) > 0 
            }.size,
            totalFormatted = Formatting.formatBrl(total),
            onDismiss = { showExecuteDialogFor = null },
            onConfirm = {
                viewModel.executeCharges(list)
                showExecuteDialogFor = null
                onNavigateToTransactions()
            }
        )
    }
}
