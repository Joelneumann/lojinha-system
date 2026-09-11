package de.joelneumann.lojinha.ui.components.admin.bulk

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.joelneumann.lojinha.domain.model.BillingList
import de.joelneumann.lojinha.domain.model.BillingListType
import de.joelneumann.lojinha.domain.model.BillingListUser
import de.joelneumann.lojinha.domain.model.User
import de.joelneumann.lojinha.ui.components.admin.AdminLabeledField
import de.joelneumann.lojinha.ui.components.admin.AdminSegmentedOptionsRow
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.theme.*
import de.joelneumann.lojinha.ui.utils.formModalKeys
import de.joelneumann.lojinha.ui.utils.Formatting

@Composable
fun BillingListEditDialog(
    initialList: BillingList?,
    activeUsers: List<User>,
    onSave: (BillingList) -> Unit,
    onCancel: () -> Unit
) {
    val strings = I18n.current
    val isNew = initialList == null

    var name by remember { mutableStateOf(initialList?.name ?: "") }
    var comment by remember { mutableStateOf(initialList?.comment ?: "") }
    var type by remember { mutableStateOf(initialList?.type ?: BillingListType.FIXED) }
    
    var priceInput by remember { 
        mutableStateOf(if (initialList?.basePrice != null) (initialList.basePrice.toDouble() / 100.0).toString() else "") 
    }

    var selectedUsers by remember { 
        mutableStateOf<Map<String, Int>>(
            initialList?.users?.associate { it.userId to it.quantity } ?: emptyMap()
        )
    }

    val canSave = name.isNotBlank() && (type != BillingListType.FIXED || priceInput.isNotBlank())
    
    val handleSave = {
        if (canSave) {
            val price = if (type == BillingListType.FIXED) {
                priceInput.replace(',', '.').toDoubleOrNull()?.let { kotlin.math.round(it * 100).toLong() }
            } else null
            
            val updatedList = BillingList(
                id = initialList?.id ?: "",
                name = name,
                type = type,
                basePrice = price,
                comment = comment.trim().ifBlank { null },
                users = selectedUsers.map { (uid, qty) -> 
                    BillingListUser(id = "", listId = "", userId = uid, quantity = qty)
                }
            )
            onSave(updatedList)
        }
    }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(dismissOnClickOutside = true, dismissOnBackPress = true)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = SurfaceWhite,
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .widthIn(max = 480.dp)
                .fillMaxHeight(0.9f)
                .formModalKeys(onCancel = onCancel, onConfirm = handleSave, confirmEnabled = canSave)
        ) {
            Column(
                modifier = Modifier.padding(24.dp).fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = if (isNew) strings.createNewList else strings.editList,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryNavy
                )
                
                HorizontalDivider(color = DividerBorder)

                AdminLabeledField(
                    label = strings.listNameLabel,
                    value = name,
                    onValueChange = { name = it },
                    placeholder = strings.listNamePlaceholder,
                    modifier = Modifier.fillMaxWidth()
                )

                AdminLabeledField(
                    label = strings.billingListCommentLabel,
                    value = comment,
                    onValueChange = { comment = it },
                    placeholder = strings.billingListCommentPlaceholder,
                    modifier = Modifier.fillMaxWidth()
                )

                if (isNew) {
                    AdminSegmentedOptionsRow(
                        label = strings.listTypeLabel,
                        options = BillingListType.entries,
                        selected = type,
                        onSelect = { type = it },
                        optionLabel = { if (it == BillingListType.FIXED) strings.listTypeFixed else strings.listTypeVariable }
                    )
                }

                if (type == BillingListType.FIXED) {
                    AdminLabeledField(
                        label = strings.basePriceBrlLabel,
                        value = priceInput,
                        onValueChange = { priceInput = it },
                        placeholder = strings.amountPlaceholder,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                Text(strings.selectUsersTitle, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PrimaryNavy)
                
                // Users List with Checkboxes
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SurfaceContainerLight,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                ) {
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                        items(activeUsers.sortedBy { it.name.lowercase() }, key = { it.id }) { user ->
                            val isSelected = selectedUsers.containsKey(user.id)
                            val qty = selectedUsers[user.id] ?: 1
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        if (isSelected) {
                                            selectedUsers = selectedUsers - user.id
                                        } else {
                                            selectedUsers = selectedUsers + (user.id to 1)
                                        }
                                    }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = null // Handled by row click
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(user.name, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                }
                                
                                if (isSelected && type == BillingListType.FIXED) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = SurfaceWhite,
                                        border = BorderStroke(1.dp, DividerBorder)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.height(30.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxHeight()
                                                    .width(30.dp)
                                                    .clickable { 
                                                        if (qty > 1) {
                                                            selectedUsers = selectedUsers + (user.id to (qty - 1)) 
                                                        } else {
                                                            selectedUsers = selectedUsers - user.id
                                                        }
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text("-", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = PrimaryNavy)
                                            }

                                            VerticalDivider(color = DividerBorder, modifier = Modifier.fillMaxHeight().width(1.dp))

                                            Text(
                                                text = qty.toString(),
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = PrimaryNavy,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.widthIn(min = 36.dp).padding(horizontal = 4.dp)
                                            )

                                            VerticalDivider(color = DividerBorder, modifier = Modifier.fillMaxHeight().width(1.dp))

                                            Box(
                                                modifier = Modifier
                                                    .fillMaxHeight()
                                                    .width(30.dp)
                                                    .clickable { selectedUsers = selectedUsers + (user.id to (qty + 1)) },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text("+", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = PrimaryNavy)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(strings.cancel, fontSize = 14.sp)
                    }

                    Button(
                        onClick = handleSave,
                        enabled = canSave,
                        modifier = Modifier.weight(1f).height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentNavy),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(strings.save, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
