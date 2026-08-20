package de.joelneumann.lojinha.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import de.joelneumann.lojinha.domain.model.*
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.theme.*
import de.joelneumann.lojinha.ui.utils.Formatting
import de.joelneumann.lojinha.ui.viewmodel.AdminTab
import de.joelneumann.lojinha.ui.viewmodel.AdminViewModel
import java.text.SimpleDateFormat
import java.util.Date

@Composable
fun AdminScreen(
    viewModel: AdminViewModel,
    language: Language,
    onCloseAdmin: () -> Unit
) {
    val strings = I18n.get(language)
    val currentTab by viewModel.currentTab.collectAsState()
    val products by viewModel.products.collectAsState()
    val users by viewModel.users.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val settings by viewModel.settings.collectAsState()

    val depositUser by viewModel.depositUser.collectAsState()
    val depositAmountInput by viewModel.depositAmountInput.collectAsState()
    val depositNoteInput by viewModel.depositNoteInput.collectAsState()

    val editProduct by viewModel.editProduct.collectAsState()
    val showProductModal by viewModel.showProductModal.collectAsState()

    val editUser by viewModel.editUser.collectAsState()
    val showUserModal by viewModel.showUserModal.collectAsState()

    val userDeleteError by viewModel.userDeleteErrorMessage.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceContainerLight)
            .padding(24.dp)
    ) {
        // Full-Width Navigation Tab Bar
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = SurfaceWhite,
            shadowElevation = 2.dp,
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DividerBorder)),
            modifier = Modifier.fillMaxWidth()
        ) {
            val tabs = listOf(
                AdminTab.PRODUCTS to "📦 ${strings.tabProducts}",
                AdminTab.USERS to "👥 ${strings.tabUsers}",
                AdminTab.TRANSACTIONS to "📜 ${strings.tabTransactions}",
                AdminTab.SETTINGS to "⚙️ ${strings.tabSettings}"
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tabs.forEach { (tab, label) ->
                    val isSel = currentTab == tab
                    Button(
                        onClick = { viewModel.selectTab(tab) },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSel) AccentNavy else SurfaceContainerHighLight,
                            contentColor = if (isSel) SurfaceWhite else PrimaryNavy
                        ),
                        shape = RoundedCornerShape(8.dp),
                        elevation = if (isSel) ButtonDefaults.buttonElevation(defaultElevation = 2.dp) else ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                    ) {
                        Text(
                            text = label,
                            fontSize = 14.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Tab Body Content
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (currentTab) {
                AdminTab.PRODUCTS -> AdminProductsTab(
                    products = products,
                    language = language,
                    onAdd = { viewModel.openNewProductModal() },
                    onEdit = { viewModel.openEditProductModal(it) },
                    onToggleActive = { viewModel.toggleProductActive(it) },
                    onDelete = { viewModel.deleteProduct(it.id) }
                )

                AdminTab.USERS -> AdminUsersTab(
                    users = users,
                    language = language,
                    onAdd = { viewModel.openNewUserModal() },
                    onEdit = { viewModel.openEditUserModal(it) },
                    onDeposit = { viewModel.openDepositModal(it) },
                    onToggleActive = { viewModel.toggleUserActive(it) },
                    onDelete = { viewModel.attemptDeleteUser(it) }
                )

                AdminTab.TRANSACTIONS -> AdminTransactionsTab(
                    transactions = transactions,
                    language = language,
                    onReverse = { viewModel.reverseTransaction(it) }
                )

                AdminTab.SETTINGS -> AdminSettingsTab(
                    settings = settings,
                    language = language,
                    onSaveSettings = { viewModel.updateSystemSettings(it) }
                )
            }
        }
    }

    // Quick Deposit / Withdrawal Modal Dialog
    if (depositUser != null) {
        Dialog(onDismissRequest = { viewModel.closeDepositModal() }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = SurfaceWhite,
                modifier = Modifier.width(400.dp).wrapContentHeight()
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = "${strings.depositWithdraw} (${depositUser!!.name})",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryNavy
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(text = "Amount (BRL):", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = depositAmountInput,
                        onValueChange = { viewModel.updateDepositAmount(it) },
                        placeholder = { Text("e.g. 50,00 or 10.50") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(text = "Note / Reason:", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = depositNoteInput,
                        onValueChange = { viewModel.updateDepositNote(it) },
                        placeholder = { Text("e.g. Cash deposit via Admin") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { viewModel.submitDeposit(isDeposit = true) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ColorSuccessEmerald)
                        ) {
                            Text("+ Deposit", color = SurfaceWhite, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { viewModel.submitDeposit(isDeposit = false) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ColorDangerCrimson)
                        ) {
                            Text("- Withdraw", color = SurfaceWhite, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // Product Edit Modal
    if (showProductModal && editProduct != null) {
        ProductEditDialog(
            product = editProduct!!,
            language = language,
            onSave = { viewModel.saveProduct(it) },
            onCancel = { viewModel.closeProductModal() }
        )
    }

    // User Edit Modal
    if (showUserModal && editUser != null) {
        UserEditDialog(
            user = editUser!!,
            language = language,
            onSave = { viewModel.saveUser(it) },
            onCancel = { viewModel.closeUserModal() }
        )
    }

    // User Delete Error Dialog (Soft Delete Enforcement Notice)
    if (userDeleteError != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearUserDeleteError() },
            title = { Text("Audit Ledger Enforcement", fontWeight = FontWeight.Bold) },
            text = { Text(userDeleteError!!) },
            confirmButton = {
                Button(onClick = { viewModel.clearUserDeleteError() }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
private fun AdminProductsTab(
    products: List<Product>,
    language: Language,
    onAdd: () -> Unit,
    onEdit: (Product) -> Unit,
    onToggleActive: (Product) -> Unit,
    onDelete: (Product) -> Unit
) {
    val strings = I18n.get(language)
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Product Catalog (${products.size})", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Button(
                onClick = onAdd,
                colors = ButtonDefaults.buttonColors(containerColor = AccentNavy),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(strings.addProduct, color = SurfaceWhite)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(products, key = { it.id }) { product ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SurfaceWhite,
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DividerBorder)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(product.name, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                if (!product.isActive) {
                                    Text("(Deactivated)", fontSize = 12.sp, color = ColorDangerCrimson)
                                }
                            }
                            Text(
                                "Price: ${Formatting.formatBrl(product.basePrice)} • Stock: ${Formatting.formatQuantity(product.stockQuantity, product.unitType)} • Barcodes: ${product.barcodes.size}",
                                fontSize = 13.sp,
                                color = TextSecondaryMuted
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { onEdit(product) }, shape = RoundedCornerShape(6.dp)) {
                                Text("Edit", fontSize = 12.sp)
                            }
                            OutlinedButton(onClick = { onToggleActive(product) }, shape = RoundedCornerShape(6.dp)) {
                                Text(if (product.isActive) strings.softDelete else "Activate", fontSize = 12.sp)
                            }
                            TextButton(onClick = { onDelete(product) }) {
                                Text(strings.hardDelete, color = ColorDangerCrimson, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminUsersTab(
    users: List<User>,
    language: Language,
    onAdd: () -> Unit,
    onEdit: (User) -> Unit,
    onDeposit: (User) -> Unit,
    onToggleActive: (User) -> Unit,
    onDelete: (User) -> Unit
) {
    val strings = I18n.get(language)
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("User Management (${users.size})", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Button(
                onClick = onAdd,
                colors = ButtonDefaults.buttonColors(containerColor = AccentNavy),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(strings.addUser, color = SurfaceWhite)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(users, key = { it.id }) { user ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SurfaceWhite,
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DividerBorder)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(user.name, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                if (!user.isActive) {
                                    Text("(Deactivated)", fontSize = 12.sp, color = ColorDangerCrimson)
                                }
                            }
                            Text(
                                "Balance: ${Formatting.formatBrl(user.balance)} • Barcode: ${user.userBarcodeNumber ?: "None"} • PIN: ${if (user.pin != null) "Set" else "None"}",
                                fontSize = 13.sp,
                                color = TextSecondaryMuted
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(
                                onClick = { onDeposit(user) },
                                colors = ButtonDefaults.buttonColors(containerColor = ColorSuccessEmerald),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("💵 $+/-", fontSize = 12.sp, color = SurfaceWhite)
                            }

                            OutlinedButton(onClick = { onEdit(user) }, shape = RoundedCornerShape(6.dp)) {
                                Text("Edit", fontSize = 12.sp)
                            }
                            OutlinedButton(onClick = { onToggleActive(user) }, shape = RoundedCornerShape(6.dp)) {
                                Text(if (user.isActive) strings.softDelete else "Activate", fontSize = 12.sp)
                            }
                            TextButton(onClick = { onDelete(user) }) {
                                Text(strings.hardDelete, color = ColorDangerCrimson, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminTransactionsTab(
    transactions: List<Transaction>,
    language: Language,
    onReverse: (Transaction) -> Unit
) {
    val strings = I18n.get(language)
    Column(modifier = Modifier.fillMaxSize()) {
        Text("System Transactions & Strict Reversals (${transactions.size})", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(transactions, key = { it.id }) { tx ->
                val dateStr = remember(tx.timestamp, language) { Formatting.formatTimestamp(tx.timestamp, language) }
                val isCancelled = tx.type == TransactionType.CANCELLATION || transactions.any { it.referenceTransactionId == tx.id }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SurfaceWhite,
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DividerBorder)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${tx.type.name} • ${tx.userNameSnapshot} • $dateStr", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("Total: ${Formatting.formatBrl(tx.totalAmount)} • Note: ${tx.note ?: "-"}", fontSize = 13.sp, color = TextSecondaryMuted)
                        }

                        if (!isCancelled && tx.type != TransactionType.CANCELLATION) {
                            Button(
                                onClick = { onReverse(tx) },
                                colors = ButtonDefaults.buttonColors(containerColor = ColorDangerCrimson),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(strings.reversalBtn, fontSize = 12.sp, color = SurfaceWhite)
                            }
                        } else {
                            Text("CANCELED / REVERSED", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondaryMuted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminSettingsTab(
    settings: SystemSettings,
    language: Language,
    onSaveSettings: (SystemSettings) -> Unit
) {
    var adminPassword by remember { mutableStateOf(settings.adminPasswordHash) }
    var globalMarkup by remember { mutableStateOf(settings.globalMarkupPercent.toString()) }
    var usdRate by remember { mutableStateOf(settings.usdExchangeRate.toString()) }
    var eurRate by remember { mutableStateOf(settings.eurExchangeRate.toString()) }
    var inactivityTimeout by remember { mutableStateOf(settings.inactivityTimeoutMinutes.toString()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("System Settings", fontSize = 16.sp, fontWeight = FontWeight.Bold)

        OutlinedTextField(
            value = adminPassword,
            onValueChange = { adminPassword = it },
            label = { Text("Admin Password") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = globalMarkup,
            onValueChange = { globalMarkup = it },
            label = { Text("Global Product Markup % (e.g. 10.0)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = usdRate,
            onValueChange = { usdRate = it },
            label = { Text("USD Exchange Rate (1 BRL = X USD, e.g. 0.18)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = eurRate,
            onValueChange = { eurRate = it },
            label = { Text("EUR Exchange Rate (1 BRL = X EUR, e.g. 0.16)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = inactivityTimeout,
            onValueChange = { inactivityTimeout = it },
            label = { Text("Inactivity Timeout (Minutes)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                val newSet = settings.copy(
                    adminPasswordHash = adminPassword,
                    globalMarkupPercent = globalMarkup.toDoubleOrNull() ?: settings.globalMarkupPercent,
                    usdExchangeRate = usdRate.toDoubleOrNull() ?: settings.usdExchangeRate,
                    eurExchangeRate = eurRate.toDoubleOrNull() ?: settings.eurExchangeRate,
                    inactivityTimeoutMinutes = inactivityTimeout.toIntOrNull() ?: settings.inactivityTimeoutMinutes
                )
                onSaveSettings(newSet)
            },
            colors = ButtonDefaults.buttonColors(containerColor = AccentNavy),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("Save System Settings", color = SurfaceWhite, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ProductEditDialog(
    product: Product,
    language: Language,
    onSave: (Product) -> Unit,
    onCancel: () -> Unit
) {
    val strings = I18n.get(language)
    var name by remember { mutableStateOf(product.name) }
    var basePriceBrl by remember { mutableStateOf((product.basePrice.toDouble() / 100.0).toString()) }
    var unitType by remember { mutableStateOf(product.unitType) }
    var stockQuantity by remember { mutableStateOf(product.stockQuantity.toString()) }
    var customMarkup by remember { mutableStateOf(product.customMarkupPercent?.toString() ?: "") }
    var barcodeCode by remember { mutableStateOf("") }
    var barcodeDesc by remember { mutableStateOf("") }
    var barcodeList by remember { mutableStateOf(product.barcodes) }

    Dialog(onDismissRequest = onCancel) {
        Surface(shape = RoundedCornerShape(16.dp), color = SurfaceWhite, modifier = Modifier.width(480.dp).wrapContentHeight()) {
            Column(modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState())) {
                Text(strings.editProduct, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Product Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(value = basePriceBrl, onValueChange = { basePriceBrl = it }, label = { Text("Base Price BRL (e.g. 8.00)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    UnitType.entries.forEach { u ->
                        OutlinedButton(
                            onClick = { unitType = u },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = if (unitType == u) AccentNavy else SurfaceWhite, contentColor = if (unitType == u) SurfaceWhite else PrimaryNavy)
                        ) {
                            Text(u.name)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = stockQuantity, onValueChange = { stockQuantity = it }, label = { Text(if (unitType == UnitType.PIECE) "Stock (Units)" else "Stock (Grams, e.g. 15000)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = customMarkup, onValueChange = { customMarkup = it }, label = { Text("Custom Markup % (Optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())

                Spacer(modifier = Modifier.height(12.dp))
                Text("Barcodes (${barcodeList.size})", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                barcodeList.forEach { b ->
                    Text("• ${b.code} (${b.description ?: "Default"})", fontSize = 12.sp)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = barcodeCode, onValueChange = { barcodeCode = it }, placeholder = { Text("Code") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(value = barcodeDesc, onValueChange = { barcodeDesc = it }, placeholder = { Text("Desc") }, modifier = Modifier.weight(1f), singleLine = true)
                    Button(onClick = {
                        if (barcodeCode.isNotBlank()) {
                            barcodeList = barcodeList + Barcode(barcodeCode.trim(), barcodeDesc.ifBlank { null })
                            barcodeCode = ""
                            barcodeDesc = ""
                        }
                    }) { Text("+ Add") }
                }

                Spacer(modifier = Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text(strings.cancel) }
                    Button(
                        onClick = {
                            val priceCents = kotlin.math.round((basePriceBrl.replace(',', '.').toDoubleOrNull() ?: 0.0) * 100).toLong()
                            val stock = stockQuantity.toLongOrNull() ?: 0L
                            val markup = customMarkup.toDoubleOrNull()
                            val updated = product.copy(
                                name = name,
                                basePrice = priceCents,
                                unitType = unitType,
                                stockQuantity = stock,
                                customMarkupPercent = markup,
                                barcodes = barcodeList
                            )
                            onSave(updated)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentNavy)
                    ) {
                        Text(strings.save, color = SurfaceWhite)
                    }
                }
            }
        }
    }
}

@Composable
private fun UserEditDialog(
    user: User,
    language: Language,
    onSave: (User) -> Unit,
    onCancel: () -> Unit
) {
    val strings = I18n.get(language)
    var name by remember { mutableStateOf(user.name) }
    var pin by remember { mutableStateOf(user.pin ?: "") }
    var barcode by remember { mutableStateOf(user.userBarcode ?: "") }
    var barcodeNumber by remember { mutableStateOf(user.userBarcodeNumber ?: "") }
    var selectedLang by remember { mutableStateOf(user.language) }

    Dialog(onDismissRequest = onCancel) {
        Surface(shape = RoundedCornerShape(16.dp), color = SurfaceWhite, modifier = Modifier.width(420.dp).wrapContentHeight()) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(strings.editUser, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Full Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(value = pin, onValueChange = { pin = it }, label = { Text("PIN (Optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = barcode,
                    onValueChange = {
                        barcode = it
                        if (barcodeNumber.isBlank()) barcodeNumber = it
                    },
                    label = { Text("User Barcode String") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = barcodeNumber,
                    onValueChange = { barcodeNumber = it },
                    label = { Text("User Barcode Number Display") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text(strings.cancel) }
                    Button(
                        onClick = {
                            val bCode = barcode.ifBlank { null }
                            val bNum = barcodeNumber.ifBlank { null }
                            val updated = user.copy(
                                name = name,
                                pin = pin.ifBlank { null },
                                userBarcode = if (bCode != null && bNum != null) bCode else null,
                                userBarcodeNumber = if (bCode != null && bNum != null) bNum else null,
                                language = selectedLang
                            )
                            onSave(updated)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentNavy)
                    ) {
                        Text(strings.save, color = SurfaceWhite)
                    }
                }
            }
        }
    }
}
