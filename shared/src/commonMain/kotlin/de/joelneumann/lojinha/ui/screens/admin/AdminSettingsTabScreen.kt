package de.joelneumann.lojinha.ui.screens.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.joelneumann.lojinha.ui.components.admin.*
import de.joelneumann.lojinha.ui.theme.*
import de.joelneumann.lojinha.ui.utils.Formatting
import de.joelneumann.lojinha.ui.utils.PlatformFile
import de.joelneumann.lojinha.ui.utils.pickFile
import de.joelneumann.lojinha.ui.utils.pickFolder
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.viewmodel.admin.AdminSettingsViewModel

@Composable
fun AdminSettingsTabScreen(
    viewModel: AdminSettingsViewModel,
    onUnsavedStateChanged: (Boolean) -> Unit
) {
    val strings = I18n.current
    val settings by viewModel.settings.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val activeRestoreDbFile by viewModel.activeRestoreDbFile.collectAsState()
    val showWipeDataDialog by viewModel.showWipeDataDialog.collectAsState()
    val csvImportPreview by viewModel.csvImportPreview.collectAsState()
    val csvImportType by viewModel.csvImportType.collectAsState()

    var newPassword by remember(settings) { mutableStateOf("") }
    var confirmPassword by remember(settings) { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    var globalMarkup by remember(settings) { mutableStateOf(settings.globalMarkupPercent.toString()) }
    var usdRate by remember(settings) { mutableStateOf(settings.usdExchangeRate.toString()) }
    var eurRate by remember(settings) { mutableStateOf(settings.eurExchangeRate.toString()) }
    var inactivityTimeout by remember(settings) { mutableStateOf(settings.inactivityTimeoutMinutes.toString()) }

    var backupLocation by remember(settings) { mutableStateOf(settings.backupLocationPath) }
    var supportEmail by remember(settings) { mutableStateOf(settings.supportEmail ?: "") }

    LaunchedEffect(settings) {
        newPassword = ""
        confirmPassword = ""
        globalMarkup = settings.globalMarkupPercent.toString()
        usdRate = settings.usdExchangeRate.toString()
        eurRate = settings.eurExchangeRate.toString()
        inactivityTimeout = settings.inactivityTimeoutMinutes.toString()
        backupLocation = settings.backupLocationPath
        supportEmail = settings.supportEmail ?: ""
    }

    val isPasswordEntered = newPassword.isNotEmpty() || confirmPassword.isNotEmpty()
    val doPasswordsMatch = newPassword == confirmPassword
    val isPasswordValid = !isPasswordEntered || (newPassword.isNotBlank() && doPasswordsMatch)
    val inactivityTimeoutValue = inactivityTimeout.toIntOrNull()
    val isInactivityTimeoutValid = inactivityTimeoutValue != null && inactivityTimeoutValue >= 2

    val globalMarkupValue = remember(globalMarkup) { Formatting.parsePercentageInput(globalMarkup) }
    val isGlobalMarkupValid = globalMarkupValue != null && globalMarkupValue in 0.0..1000.0
    val parsedUsdRate = remember(usdRate) { usdRate.trim().replace(',', '.').toDoubleOrNull() }
    val isUsdRateValid = parsedUsdRate != null && parsedUsdRate.isFinite() && parsedUsdRate > 0.0
    val parsedEurRate = remember(eurRate) { eurRate.trim().replace(',', '.').toDoubleOrNull() }
    val isEurRateValid = parsedEurRate != null && parsedEurRate.isFinite() && parsedEurRate > 0.0

    val hasFieldChanges = remember(
        settings, newPassword, confirmPassword, globalMarkup, usdRate, eurRate, inactivityTimeout,
        backupLocation, supportEmail
    ) {
        newPassword.isNotEmpty() ||
                globalMarkup != settings.globalMarkupPercent.toString() ||
                usdRate != settings.usdExchangeRate.toString() ||
                eurRate != settings.eurExchangeRate.toString() ||
                inactivityTimeout != settings.inactivityTimeoutMinutes.toString() ||
                backupLocation != settings.backupLocationPath ||
                supportEmail != (settings.supportEmail ?: "")
    }

    LaunchedEffect(hasFieldChanges) {
        onUnsavedStateChanged(hasFieldChanges)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = PrimaryNavy,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = strings.systemAdminSettingsTitle,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryNavy
                )

                if (hasFieldChanges) {
                    AdminStatusBadge(
                        text = strings.unsavedEditsBadge,
                        type = AdminBadgeType.WARNING
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxHeight()
            ) {
                if (hasFieldChanges) {
                    OutlinedButton(
                        onClick = {
                            newPassword = ""
                            confirmPassword = ""
                            globalMarkup = settings.globalMarkupPercent.toString()
                            usdRate = settings.usdExchangeRate.toString()
                            eurRate = settings.eurExchangeRate.toString()
                            inactivityTimeout = settings.inactivityTimeoutMinutes.toString()
                            backupLocation = settings.backupLocationPath
                            supportEmail = settings.supportEmail ?: ""
                            onUnsavedStateChanged(false)
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Undo,
                                contentDescription = null,
                                tint = PrimaryNavy,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(strings.revertChanges, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PrimaryNavy)
                        }
                    }
                }

                Button(
                    onClick = {
                        if (hasFieldChanges && isPasswordValid && isInactivityTimeoutValid && isGlobalMarkupValid && isUsdRateValid && isEurRateValid) {
                            val updatedSettings = settings.copy(
                                adminPasswordHash = if (newPassword.isNotBlank()) de.joelneumann.lojinha.security.PasswordHasher.hash(newPassword) else settings.adminPasswordHash,
                                globalMarkupPercent = globalMarkupValue ?: settings.globalMarkupPercent,
                                usdExchangeRate = parsedUsdRate ?: settings.usdExchangeRate,
                                eurExchangeRate = parsedEurRate ?: settings.eurExchangeRate,
                                inactivityTimeoutMinutes = maxOf(2, inactivityTimeout.toIntOrNull() ?: settings.inactivityTimeoutMinutes),
                                backupLocationPath = backupLocation,
                                supportEmail = supportEmail.takeIf { it.isNotBlank() }
                            )
                            viewModel.updateSystemSettings(updatedSettings)
                            newPassword = ""
                            confirmPassword = ""
                            onUnsavedStateChanged(false)
                        }
                    },
                    enabled = hasFieldChanges && isPasswordValid && isInactivityTimeoutValid && isGlobalMarkupValid && isUsdRateValid && isEurRateValid,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentNavy,
                        disabledContainerColor = SurfaceContainerHighLight
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxHeight()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (hasFieldChanges) Icons.Default.Save else Icons.Default.Check,
                            contentDescription = null,
                            tint = SurfaceWhite,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (hasFieldChanges) strings.saveSettings else strings.saved,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = SurfaceWhite
                        )
                    }
                }
            }
        }

        if (statusMessage != null) {
            Surface(
                color = ColorSuccessEmerald.copy(alpha = 0.12f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(statusMessage!!, color = ColorSuccessEmerald, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    IconButton(onClick = { viewModel.clearStatusMessages() }) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = ColorSuccessEmerald, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

        if (errorMessage != null) {
            Surface(
                color = ColorDangerCrimson.copy(alpha = 0.12f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(errorMessage!!, color = ColorDangerCrimson, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    IconButton(onClick = { viewModel.clearStatusMessages() }) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = ColorDangerCrimson, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
            // CARD 1: ADMIN MASTER PASSWORD
            item(key = "admin-security-card") {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SurfaceWhite,
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DividerBorder)),
                    shadowElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = PrimaryNavy,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(strings.adminMasterPasswordTitle, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = PrimaryNavy)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            AdminLabeledField(
                                label = strings.newPasswordLabel,
                                value = newPassword,
                                onValueChange = { newPassword = it },
                                placeholder = strings.newPasswordPlaceholder,
                                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { showPassword = !showPassword }) {
                                        Icon(
                                            imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = "Toggle Visibility",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )

                            AdminLabeledField(
                                label = strings.confirmNewPasswordLabel,
                                value = confirmPassword,
                                onValueChange = { confirmPassword = it },
                                placeholder = strings.confirmNewPasswordPlaceholder,
                                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { showPassword = !showPassword }) {
                                        Icon(
                                            imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = "Toggle Visibility",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (isPasswordEntered) {
                            if (!doPasswordsMatch) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Error,
                                        contentDescription = null,
                                        tint = ColorDangerCrimson,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(strings.passwordsDoNotMatch, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ColorDangerCrimson)
                                }
                            } else if (newPassword.isNotBlank()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = ColorSuccessEmerald,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(strings.passwordsMatch, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ColorSuccessEmerald)
                                }
                            }
                        }
                    }
                }
            }

            // CARD 2: PRODUCT PRICING RULES
            item(key = "product-pricing-card") {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SurfaceWhite,
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DividerBorder)),
                    shadowElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sell,
                                contentDescription = null,
                                tint = PrimaryNavy,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(strings.productPricingRulesTitle, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = PrimaryNavy)
                        }
                        AdminLabeledField(
                            label = strings.globalProductMarkupLabel,
                            value = globalMarkup,
                            onValueChange = { globalMarkup = it },
                            placeholder = strings.globalProductMarkupPlaceholder,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (!isGlobalMarkupValid) {
                            Text(
                                text = strings.invalidMarkupError,
                                fontSize = 11.sp,
                                color = ColorDangerCrimson,
                                modifier = Modifier.padding(top = 4.dp, start = 2.dp)
                            )
                        }
                    }
                }
            }

            // CARD 3: CURRENCY EXCHANGE RATES
            item(key = "currency-exchange-card") {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SurfaceWhite,
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DividerBorder)),
                    shadowElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CurrencyExchange,
                                contentDescription = null,
                                tint = PrimaryNavy,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(strings.currencyExchangeRatesTitle, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = PrimaryNavy)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            AdminLabeledField(
                                label = strings.usdRateLabel,
                                value = usdRate,
                                onValueChange = { usdRate = it },
                                placeholder = strings.usdRatePlaceholder,
                                modifier = Modifier.weight(1f)
                            )

                            AdminLabeledField(
                                label = strings.eurRateLabel,
                                value = eurRate,
                                onValueChange = { eurRate = it },
                                placeholder = strings.eurRatePlaceholder,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // CARD 4: KIOSK SYSTEM TIMERS
            item(key = "kiosk-timers-card") {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SurfaceWhite,
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DividerBorder)),
                    shadowElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = null,
                                tint = PrimaryNavy,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(strings.kioskSystemTimersTitle, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = PrimaryNavy)
                        }
                        AdminLabeledField(
                            label = strings.inactivityTimeoutLabel,
                            value = inactivityTimeout,
                            onValueChange = { inactivityTimeout = it },
                            placeholder = strings.inactivityTimeoutPlaceholder,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (!isInactivityTimeoutValid) {
                            Text(
                                text = strings.inactivityTimeoutMinError,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = ColorDangerCrimson,
                                modifier = Modifier.padding(start = 2.dp)
                            )
                        }
                    }
                }
            }

            // CARD 4.5: MICROSOFT ONEDRIVE INTEGRATION
            item(key = "onedrive-integration-card") {
                val settingsState by viewModel.settings.collectAsState()

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SurfaceWhite,
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DividerBorder)),
                    shadowElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Cloud,
                                    contentDescription = null,
                                    tint = PrimaryNavy,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(strings.oneDriveIntegrationTitle, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = PrimaryNavy)
                            }

                            val isConnected = !settingsState.oneDriveRefreshToken.isNullOrBlank()
                            Surface(
                                color = if (isConnected) ColorSuccessEmerald.copy(alpha = 0.12f) else ColorDangerCrimson.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = if (isConnected) strings.connected else strings.disconnected,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isConnected) ColorSuccessEmerald else ColorDangerCrimson
                                )
                            }
                        }

                        var clientIdInput by remember(settingsState.oneDriveClientId) { mutableStateOf(settingsState.oneDriveClientId) }
                        OutlinedTextField(
                            value = clientIdInput,
                            onValueChange = {
                                clientIdInput = it
                                viewModel.updateOneDriveClientId(it)
                            },
                            label = { Text(strings.azureClientIdLabel) },
                            placeholder = { Text(strings.azureClientIdPlaceholder) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (!settingsState.oneDriveRefreshToken.isNullOrBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = strings.oneDriveAccountLabel(settingsState.oneDriveAccountEmail ?: strings.microsoftAccount),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = PrimaryNavy
                                    )
                                    if (!settingsState.oneDriveAccountName.isNullOrBlank()) {
                                        Text(
                                            text = settingsState.oneDriveAccountName ?: "",
                                            fontSize = 11.sp,
                                            color = Color.Gray
                                        )
                                    }
                                }

                                OutlinedButton(
                                    onClick = { viewModel.requestDisconnectOneDrive() },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorDangerCrimson)
                                ) {
                                    Text(strings.disconnectOneDrive)
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = strings.connectOneDriveDesc,
                                    fontSize = 12.sp,
                                    color = Color.DarkGray,
                                    modifier = Modifier.weight(1f).padding(end = 12.dp)
                                )

                                Button(
                                    onClick = { viewModel.startOneDriveAuth() },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentNavy)
                                ) {
                                    Text(strings.connectOneDrive)
                                }
                            }
                        }
                    }
                }
            }

            // CARD 5: BACKUP ROUTINES MANAGEMENT
            item(key = "backup-routines-card") {
                val routines by viewModel.routines.collectAsState()

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SurfaceWhite,
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DividerBorder)),
                    shadowElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Inventory,
                                    contentDescription = null,
                                    tint = PrimaryNavy,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(strings.configuredBackupRoutinesTitle, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = PrimaryNavy)
                                Surface(
                                    color = PrimaryNavy.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(strings.routinesCountBadge(routines.size), modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PrimaryNavy)
                                }
                            }

                            Button(
                                onClick = { viewModel.openCreateRoutineDialog() },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AccentNavy)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        tint = SurfaceWhite,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(strings.createRoutine, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        if (routines.isEmpty()) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = SurfaceContainerHighLight),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(strings.noRoutinesYet, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PrimaryNavy)
                                    Text(strings.noRoutinesYetSub, fontSize = 12.sp, color = AccentNavy)
                                }
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                routines.forEach { routine ->
                                    val lastBackupStr = routine.lastBackupTimestamp?.let {
                                        Formatting.formatTimestamp(it)
                                    } ?: strings.never

                                    val nextDueStr = if (routine.scheduleConfig is de.joelneumann.lojinha.domain.model.BackupScheduleConfig.OnDataChange) {
                                        strings.onRealtimeEvent
                                    } else {
                                        Formatting.formatTimestamp(routine.calculateNextDueTimestamp())
                                    }

                                    val scheduleBadgeText = when (val cfg = routine.scheduleConfig) {
                                        is de.joelneumann.lojinha.domain.model.BackupScheduleConfig.Timed -> strings.badgeTimed(cfg.timeOfDay)
                                        is de.joelneumann.lojinha.domain.model.BackupScheduleConfig.Interval -> strings.badgeInterval(cfg.intervalHours, cfg.intervalMinutes)
                                        is de.joelneumann.lojinha.domain.model.BackupScheduleConfig.OnDataChange -> strings.badgeRealtime
                                    }

                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = SurfaceContainerHighLight),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    Text(routine.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = PrimaryNavy)

                                                    Surface(
                                                        color = PrimaryNavy.copy(alpha = 0.12f),
                                                        shape = RoundedCornerShape(4.dp)
                                                    ) {
                                                        Text(routine.type.name, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PrimaryNavy)
                                                    }

                                                    Surface(
                                                         color = ColorSuccessEmerald.copy(alpha = 0.12f),
                                                         shape = RoundedCornerShape(4.dp)
                                                     ) {
                                                         Text(".${routine.fileType.name.lowercase()}", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = ColorSuccessEmerald)
                                                     }

                                                     Surface(
                                                         color = PrimaryNavy.copy(alpha = 0.08f),
                                                         shape = RoundedCornerShape(4.dp)
                                                     ) {
                                                         val writeModeText = if (routine.writeMode == de.joelneumann.lojinha.domain.model.BackupWriteMode.OVERWRITE_LATEST) strings.badgeOverwrite else strings.badgeNewFile
                                                         Text(writeModeText, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PrimaryNavy)
                                                     }

                                                     Surface(
                                                         color = AccentNavy.copy(alpha = 0.12f),
                                                         shape = RoundedCornerShape(4.dp)
                                                     ) {
                                                         Text(scheduleBadgeText, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AccentNavy)
                                                     }
                                                }

                                                Switch(
                                                    checked = routine.isEnabled,
                                                    onCheckedChange = { viewModel.requestToggleRoutine(routine, it) }
                                                )
                                            }

                                            Text(strings.targetPathLabel(routine.backupLocationPath), fontSize = 11.sp, color = AccentNavy)
                                            Text(strings.lastBackupNextDueLabel(lastBackupStr, nextDueStr), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = PrimaryNavy)

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Button(
                                                    onClick = { viewModel.runRoutineNow(routine) },
                                                    shape = RoundedCornerShape(6.dp),
                                                    colors = ButtonDefaults.buttonColors(containerColor = AccentNavy),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.PlayArrow,
                                                            contentDescription = null,
                                                            tint = SurfaceWhite,
                                                            modifier = Modifier.size(13.dp)
                                                        )
                                                        Text(strings.runNow, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }

                                                OutlinedButton(
                                                    onClick = { viewModel.openEditRoutineDialog(routine) },
                                                    shape = RoundedCornerShape(6.dp),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Edit,
                                                            contentDescription = null,
                                                            tint = PrimaryNavy,
                                                            modifier = Modifier.size(13.dp)
                                                        )
                                                        Text(strings.edit, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PrimaryNavy)
                                                    }
                                                }

                                                OutlinedButton(
                                                    onClick = { viewModel.requestDeleteRoutine(routine) },
                                                    shape = RoundedCornerShape(6.dp),
                                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorDangerCrimson),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Delete,
                                                            contentDescription = null,
                                                            tint = ColorDangerCrimson,
                                                            modifier = Modifier.size(13.dp)
                                                        )
                                                        Text(strings.delete, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // CARD 6: DATABASE RESTORE (.DB) & DATA WIPE
            item(key = "restore-wipe-card") {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SurfaceWhite,
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DividerBorder)),
                    shadowElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Restore,
                                contentDescription = null,
                                tint = PrimaryNavy,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(strings.databaseRestoreResetTitle, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = PrimaryNavy)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    pickFile(strings.selectDbBackupFile, ".db") { selectedFile ->
                                        viewModel.setRestoreDbFile(selectedFile)
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = ColorDangerCrimson),
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.RestoreFromTrash,
                                        contentDescription = null,
                                        tint = SurfaceWhite,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(strings.restoreDatabaseBtn, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SurfaceWhite)
                                }
                            }

                            Button(
                                onClick = {
                                    viewModel.setShowWipeDataDialog(true)
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = ColorDangerCrimson),
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteForever,
                                        contentDescription = null,
                                        tint = SurfaceWhite,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(strings.factoryResetBtn, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SurfaceWhite)
                                }
                            }
                        }
                    }
                }
            }

            // CARD 7: CSV DATA IMPORT
            item(key = "csv-import-card") {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SurfaceWhite,
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DividerBorder)),
                    shadowElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileUpload,
                                contentDescription = null,
                                tint = PrimaryNavy,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(strings.importCsvDataTitle, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = PrimaryNavy)
                        }
                        Text(strings.importCsvDataDesc, fontSize = 12.sp, color = AccentNavy)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    pickFile(strings.selectProductsCsvFile, ".csv") { selectedFile ->
                                        viewModel.prepareCsvImport(selectedFile, "Products")
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Sell,
                                        contentDescription = null,
                                        tint = PrimaryNavy,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(strings.importProductsCsvBtn, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PrimaryNavy)
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    pickFile(strings.selectUsersCsvFile, ".csv") { selectedFile ->
                                        viewModel.prepareCsvImport(selectedFile, "Users")
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = PrimaryNavy,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(strings.importUsersCsvBtn, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PrimaryNavy)
                                }
                            }
                        }
                    }
                }
            }

            // CARD 8: SUPPORT CONTACT
            item(key = "support-contact-card") {
                val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SurfaceWhite,
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DividerBorder)),
                    shadowElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ContactSupport,
                                contentDescription = null,
                                tint = PrimaryNavy,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(strings.supportContactTitle, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = PrimaryNavy)
                        }

                        AdminLabeledField(
                            label = strings.supportEmailLabel,
                            value = supportEmail,
                            onValueChange = { supportEmail = it },
                            placeholder = strings.supportEmailPlaceholder,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val email = supportEmail.takeIf { it.isNotBlank() } ?: "support@example.com"
                                    uriHandler.openUri("mailto:$email")
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Email,
                                        contentDescription = null,
                                        tint = PrimaryNavy,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(strings.contactSupportBtn, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PrimaryNavy)
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    uriHandler.openUri("https://github.com/TODO_YOUR_PROJECT")
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Code,
                                        contentDescription = null,
                                        tint = PrimaryNavy,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(strings.githubRepoBtn, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PrimaryNavy)
                                }
                            }
                        }
                    }
                }
            }
        }

    // MULTI-APPROVAL DIALOG TRIGGERS
    if (activeRestoreDbFile != null) {
        DbRestoreMultiApprovalDialog(
            file = activeRestoreDbFile!!,
            adminPasswordHash = settings.adminPasswordHash,
            onConfirmRestore = {
                viewModel.executeDbRestore(activeRestoreDbFile!!)
            },
            onDismiss = {
                viewModel.setRestoreDbFile(null)
            }
        )
    }

    if (showWipeDataDialog) {
        WipeDataMultiApprovalDialog(
            adminPasswordHash = settings.adminPasswordHash,
            onConfirmWipe = {
                viewModel.executeWipeData()
            },
            onDismiss = {
                viewModel.setShowWipeDataDialog(false)
            }
        )
    }

    if (csvImportPreview != null) {
        CsvImportMultiApprovalDialog(
            file = csvImportPreview!!.first,
            importResultPreview = csvImportPreview!!.second,
            importType = csvImportType,
            adminPasswordHash = settings.adminPasswordHash,
            onConfirmImport = {
                viewModel.executeCsvImport()
            },
            onDismiss = {
                viewModel.clearCsvImportPreview()
            }
        )
    }

    val showRoutineDialog by viewModel.showRoutineDialog.collectAsState()
    val editingRoutine by viewModel.editingRoutine.collectAsState()

    val routineToDelete by viewModel.routineToDelete.collectAsState()
    val routineToToggle by viewModel.routineToToggle.collectAsState()

    if (showRoutineDialog) {
        de.joelneumann.lojinha.ui.components.admin.BackupRoutineDialog(
            initialRoutine = editingRoutine,
            onSaveRoutine = { routine ->
                viewModel.saveBackupRoutine(routine)
            },
            onDismiss = {
                viewModel.closeRoutineDialog()
            }
        )
    }

    if (routineToDelete != null) {
        de.joelneumann.lojinha.ui.components.admin.DeleteRoutineConfirmationDialog(
            routine = routineToDelete!!,
            onConfirmDelete = {
                viewModel.confirmDeleteRoutine()
            },
            onDismiss = {
                viewModel.cancelDeleteRoutine()
            }
        )
    }

    if (routineToToggle != null) {
        de.joelneumann.lojinha.ui.components.admin.ToggleRoutineConfirmationDialog(
            routine = routineToToggle!!.first,
            targetState = routineToToggle!!.second,
            onConfirmToggle = {
                viewModel.confirmToggleRoutine()
            },
            onDismiss = {
                viewModel.cancelToggleRoutine()
            }
        )
    }

    val showOneDriveAuthDialog by viewModel.showOneDriveAuthDialog.collectAsState()
    val oneDriveAuthStatus by viewModel.oneDriveAuthStatus.collectAsState()
    val showOneDriveDisconnectDialog by viewModel.showOneDriveDisconnectDialog.collectAsState()
    val showOneDriveSuccessDialog by viewModel.showOneDriveSuccessDialog.collectAsState()

    if (showOneDriveAuthDialog) {
        de.joelneumann.lojinha.ui.components.admin.OneDriveAuthDialog(
            statusMessage = oneDriveAuthStatus,
            onDismiss = { viewModel.cancelOneDriveAuth() }
        )
    }

    if (showOneDriveDisconnectDialog) {
        de.joelneumann.lojinha.ui.components.admin.OneDriveDisconnectDialog(
            accountEmail = settings.oneDriveAccountEmail,
            onConfirm = { viewModel.confirmDisconnectOneDrive() },
            onDismiss = { viewModel.cancelDisconnectOneDrive() }
        )
    }

    if (showOneDriveSuccessDialog != null) {
        de.joelneumann.lojinha.ui.components.admin.OneDriveSuccessDialog(
            accountEmail = showOneDriveSuccessDialog!!,
            onDismiss = { viewModel.dismissOneDriveSuccessDialog() }
        )
    }
}
}
