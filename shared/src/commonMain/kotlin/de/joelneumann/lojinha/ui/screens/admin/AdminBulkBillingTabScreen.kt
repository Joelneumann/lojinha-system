package de.joelneumann.lojinha.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
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
    viewModel: AdminBulkBillingViewModel
) {
    val strings = I18n.current
    val billingLists by viewModel.billingLists.collectAsState()
    val selectedListId by viewModel.selectedListId.collectAsState()
    val activeUsers by viewModel.activeUsers.collectAsState()
    val variableAmounts by viewModel.variableAmounts.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var showDeleteDialogFor by remember { mutableStateOf<String?>(null) }
    var showExecuteDialogFor by remember { mutableStateOf<BillingList?>(null) }

    val selectedList = billingLists.find { it.id == selectedListId }

    Row(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        // LEFT PANE: List of Billing Lists
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight()
                .background(SurfaceWhite, RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(strings.bulkBillingTitle, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = PrimaryNavy)
                IconButton(onClick = { showCreateDialog = true }) {
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
                            Column {
                                Text(list.name, fontWeight = FontWeight.Bold, color = if (isSelected) AccentNavy else PrimaryNavy)
                                Text(
                                    if (list.type == BillingListType.FIXED) strings.listTypeFixed else strings.listTypeVariable,
                                    fontSize = 12.sp,
                                    color = TextSecondaryMuted
                                )
                            }
                            IconButton(onClick = { showDeleteDialogFor = list.id }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = strings.delete, tint = ColorDangerCrimson, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }

        // RIGHT PANE: Selected List Details
        Column(
            modifier = Modifier.weight(2f).fillMaxHeight()
                .background(SurfaceWhite, RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            if (selectedList == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(strings.noListsCreated, color = TextSecondaryMuted) // Or a "Select a list" placeholder
                }
            } else {
                Text(selectedList.name, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = PrimaryNavy)
                val typeText = if (selectedList.type == BillingListType.FIXED) {
                    "${strings.listTypeFixed} (${Formatting.formatBrl(selectedList.basePrice ?: 0L)})"
                } else strings.listTypeVariable
                Text(typeText, fontSize = 14.sp, color = TextSecondaryMuted)

                Spacer(modifier = Modifier.height(16.dp))

                // User search dropdown would go here in a real app, keeping it simple for this MVP
                var userSearchQuery by remember { mutableStateOf("") }
                OutlinedTextField(
                    value = userSearchQuery,
                    onValueChange = { userSearchQuery = it },
                    placeholder = { Text(strings.searchUserToAdd) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                if (userSearchQuery.isNotBlank()) {
                    val searchResults = activeUsers.filter {
                        it.name.contains(userSearchQuery, ignoreCase = true) &&
                        selectedList.users.none { u -> u.userId == it.id }
                    }.take(3)
                    
                    if (searchResults.isNotEmpty()) {
                        Column(modifier = Modifier.fillMaxWidth().background(SurfaceContainerLight).padding(8.dp)) {
                            searchResults.forEach { user ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        viewModel.addUserToList(selectedList.id, user.id, 1)
                                        userSearchQuery = ""
                                    }.padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(user.name)
                                    Icon(Icons.Default.Add, contentDescription = null, tint = AccentNavy)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                var totalExpectedAmount = 0L

                LazyColumn(modifier = Modifier.weight(1f)) {
                    if (selectedList.users.isEmpty()) {
                        item {
                            Text(strings.noUsersInList, color = TextSecondaryMuted, modifier = Modifier.padding(16.dp))
                        }
                    } else {
                        items(selectedList.users, key = { it.id }) { listUser ->
                            val user = activeUsers.find { it.id == listUser.userId }
                            if (user != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(user.name, modifier = Modifier.weight(1f))
                                    
                                    if (selectedList.type == BillingListType.FIXED) {
                                        var qtyString by remember(listUser.quantity) { mutableStateOf(listUser.quantity.toString()) }
                                        OutlinedTextField(
                                            value = qtyString,
                                            onValueChange = { qtyString = it },
                                            modifier = Modifier.width(80.dp),
                                            label = { Text(strings.quantity, fontSize = 10.sp) },
                                            singleLine = true
                                        )
                                        // Update logic on focus lost or enter key would be better here, simplified for now
                                        val qty = qtyString.toIntOrNull() ?: 0
                                        val lineTotal = qty * (selectedList.basePrice ?: 0L)
                                        totalExpectedAmount += lineTotal
                                        Text(Formatting.formatBrl(lineTotal), modifier = Modifier.width(100.dp).padding(start = 8.dp))
                                    } else {
                                        val currentAmount = variableAmounts[user.id] ?: 0L
                                        var amountStr by remember(currentAmount) { 
                                            mutableStateOf(if (currentAmount == 0L) "" else Formatting.formatBrl(currentAmount).replace("R$ ", "")) 
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
                                        totalExpectedAmount += currentAmount
                                    }

                                    IconButton(onClick = { viewModel.removeUserFromList(selectedList.id, user.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = null, tint = ColorDangerCrimson)
                                    }
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

    if (showCreateDialog) {
        CreateBillingListDialog(
            onDismiss = { showCreateDialog = false },
            onSubmit = { name, type, price ->
                viewModel.createList(name, type, price)
                showCreateDialog = false
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
            }
        )
    }
}
