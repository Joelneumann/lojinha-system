package de.joelneumann.lojinha.ui.screens.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import de.joelneumann.lojinha.ui.viewmodel.admin.AdminSettingsViewModel

@Composable
fun AdminSettingsTabScreen(
    viewModel: AdminSettingsViewModel,
    onUnsavedStateChanged: (Boolean) -> Unit
) {
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
    var autoBackupEnabled by remember(settings) { mutableStateOf(settings.autoBackupEnabled) }
    var autoBackupFormat by remember(settings) { mutableStateOf(settings.autoBackupFormat) }
    var autoBackupScheduleType by remember(settings) { mutableStateOf(settings.autoBackupScheduleType) }
    var autoBackupTime by remember(settings) { mutableStateOf(settings.autoBackupTime) }
    var autoBackupIntervalHours by remember(settings) { mutableStateOf(settings.autoBackupIntervalHours.toString()) }

    LaunchedEffect(settings) {
        newPassword = ""
        confirmPassword = ""
        globalMarkup = settings.globalMarkupPercent.toString()
        usdRate = settings.usdExchangeRate.toString()
        eurRate = settings.eurExchangeRate.toString()
        inactivityTimeout = settings.inactivityTimeoutMinutes.toString()
        backupLocation = settings.backupLocationPath
        autoBackupEnabled = settings.autoBackupEnabled
        autoBackupFormat = settings.autoBackupFormat
        autoBackupScheduleType = settings.autoBackupScheduleType
        autoBackupTime = settings.autoBackupTime
        autoBackupIntervalHours = settings.autoBackupIntervalHours.toString()
    }

    val isPasswordEntered = newPassword.isNotEmpty() || confirmPassword.isNotEmpty()
    val doPasswordsMatch = newPassword == confirmPassword
    val isPasswordValid = !isPasswordEntered || (newPassword.isNotBlank() && doPasswordsMatch)

    val hasFieldChanges = remember(
        settings, newPassword, confirmPassword, globalMarkup, usdRate, eurRate, inactivityTimeout,
        backupLocation, autoBackupEnabled, autoBackupFormat, autoBackupScheduleType, autoBackupTime, autoBackupIntervalHours
    ) {
        newPassword.isNotEmpty() ||
                globalMarkup != settings.globalMarkupPercent.toString() ||
                usdRate != settings.usdExchangeRate.toString() ||
                eurRate != settings.eurExchangeRate.toString() ||
                inactivityTimeout != settings.inactivityTimeoutMinutes.toString() ||
                backupLocation != settings.backupLocationPath ||
                autoBackupEnabled != settings.autoBackupEnabled ||
                autoBackupFormat != settings.autoBackupFormat ||
                autoBackupScheduleType != settings.autoBackupScheduleType ||
                autoBackupTime != settings.autoBackupTime ||
                autoBackupIntervalHours != settings.autoBackupIntervalHours.toString()
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
                    text = "System & Admin Settings",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryNavy
                )

                if (hasFieldChanges) {
                    AdminStatusBadge(
                        text = "● Unsaved Edits",
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
                            autoBackupEnabled = settings.autoBackupEnabled
                            autoBackupFormat = settings.autoBackupFormat
                            autoBackupScheduleType = settings.autoBackupScheduleType
                            autoBackupTime = settings.autoBackupTime
                            autoBackupIntervalHours = settings.autoBackupIntervalHours.toString()
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
                                imageVector = Icons.Default.Undo,
                                contentDescription = null,
                                tint = PrimaryNavy,
                                modifier = Modifier.size(16.dp)
                            )
                            Text("Revert Changes", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PrimaryNavy)
                        }
                    }
                }

                Button(
                    onClick = {
                        if (hasFieldChanges && isPasswordValid) {
                            val updatedSettings = settings.copy(
                                adminPasswordHash = if (newPassword.isNotBlank()) newPassword else settings.adminPasswordHash,
                                globalMarkupPercent = globalMarkup.toDoubleOrNull() ?: settings.globalMarkupPercent,
                                usdExchangeRate = usdRate.toDoubleOrNull() ?: settings.usdExchangeRate,
                                eurExchangeRate = eurRate.toDoubleOrNull() ?: settings.eurExchangeRate,
                                inactivityTimeoutMinutes = inactivityTimeout.toIntOrNull() ?: settings.inactivityTimeoutMinutes,
                                backupLocationPath = backupLocation,
                                autoBackupEnabled = autoBackupEnabled,
                                autoBackupFormat = autoBackupFormat,
                                autoBackupScheduleType = autoBackupScheduleType,
                                autoBackupTime = autoBackupTime,
                                autoBackupIntervalHours = autoBackupIntervalHours.toIntOrNull() ?: settings.autoBackupIntervalHours
                            )
                            viewModel.updateSystemSettings(updatedSettings)
                            newPassword = ""
                            confirmPassword = ""
                            onUnsavedStateChanged(false)
                        }
                    },
                    enabled = hasFieldChanges && isPasswordValid,
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
                            text = if (hasFieldChanges) "Save Settings" else "Saved",
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
                        Text("✕", fontSize = 12.sp, color = ColorSuccessEmerald)
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
                        Text("✕", fontSize = 12.sp, color = ColorDangerCrimson)
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
                            Text("Admin Master Password", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = PrimaryNavy)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            AdminLabeledField(
                                label = "New Password:",
                                value = newPassword,
                                onValueChange = { newPassword = it },
                                placeholder = "Enter new password",
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
                                label = "Confirm New Password:",
                                value = confirmPassword,
                                onValueChange = { confirmPassword = it },
                                placeholder = "Confirm new password",
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
                                    Text("Passwords do not match", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ColorDangerCrimson)
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
                                    Text("Passwords match", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ColorSuccessEmerald)
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
                            Text("Product Pricing Rules", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = PrimaryNavy)
                        }
                        AdminLabeledField(
                            label = "Global Product Markup (%):",
                            value = globalMarkup,
                            onValueChange = { globalMarkup = it },
                            placeholder = "e.g. 10.0",
                            modifier = Modifier.fillMaxWidth()
                        )
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
                            Text("Currency Exchange Rates", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = PrimaryNavy)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            AdminLabeledField(
                                label = "USD Rate (1 BRL = X USD):",
                                value = usdRate,
                                onValueChange = { usdRate = it },
                                placeholder = "e.g. 0.18",
                                modifier = Modifier.weight(1f)
                            )

                            AdminLabeledField(
                                label = "EUR Rate (1 BRL = X EUR):",
                                value = eurRate,
                                onValueChange = { eurRate = it },
                                placeholder = "e.g. 0.16",
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
                            Text("Kiosk System Timers", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = PrimaryNavy)
                        }
                        AdminLabeledField(
                            label = "Inactivity Timeout (Minutes):",
                            value = inactivityTimeout,
                            onValueChange = { inactivityTimeout = it },
                            placeholder = "e.g. 3",
                            modifier = Modifier.fillMaxWidth()
                        )
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
                                Text("Configured Backup Routines", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = PrimaryNavy)
                                Surface(
                                    color = PrimaryNavy.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("${routines.size} Routines", modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PrimaryNavy)
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
                                    Text("Create Routine", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                                    Text("No backup routines created yet.", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PrimaryNavy)
                                    Text("Click '+ Create Routine' above to add automated or manual backup schedules.", fontSize = 12.sp, color = AccentNavy)
                                }
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                routines.forEach { routine ->
                                    val lastBackupStr = routine.lastBackupTimestamp?.let {
                                        Formatting.formatTimestamp(it)
                                    } ?: "Never"

                                    val nextDueStr = if (routine.scheduleConfig is de.joelneumann.lojinha.domain.model.BackupScheduleConfig.OnDataChange) {
                                        "On Real-time Event"
                                    } else {
                                        Formatting.formatTimestamp(routine.calculateNextDueTimestamp())
                                    }

                                    val scheduleBadgeText = when (val cfg = routine.scheduleConfig) {
                                        is de.joelneumann.lojinha.domain.model.BackupScheduleConfig.Timed -> "TIMED ${cfg.timeOfDay}"
                                        is de.joelneumann.lojinha.domain.model.BackupScheduleConfig.Interval -> "EVERY ${cfg.intervalHours}h ${cfg.intervalMinutes}m"
                                        is de.joelneumann.lojinha.domain.model.BackupScheduleConfig.OnDataChange -> "⚡ REALTIME CHANGE"
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
                                                         val writeModeText = if (routine.writeMode == de.joelneumann.lojinha.domain.model.BackupWriteMode.OVERWRITE_LATEST) "OVERWRITE" else "NEW FILE"
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

                                            Text("Target Path: ${routine.backupLocationPath}", fontSize = 11.sp, color = AccentNavy)
                                            Text("Last Backup: $lastBackupStr  •  Next Due: $nextDueStr", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = PrimaryNavy)

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
                                                        Text("Run Now", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                                                        Text("Edit", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PrimaryNavy)
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
                                                        Text("Delete", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                            Text("Database Restore (.db) & Reset", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = PrimaryNavy)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    pickFile("Select .db Database Backup File", ".db") { selectedFile ->
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
                                    Text("Restore Database (.db)...", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SurfaceWhite)
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
                                    Text("Wipe All Data (Factory Reset)...", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SurfaceWhite)
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
                            Text("Import Data from CSV Files", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = PrimaryNavy)
                        }
                        Text("Import Products or Users into the database (Add / Update Mode). Existing matching IDs will be updated; user barcodes will be cleared to prevent collisions.", fontSize = 12.sp, color = AccentNavy)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    pickFile("Select Products CSV File", ".csv") { selectedFile ->
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
                                    Text("Import Products CSV...", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PrimaryNavy)
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    pickFile("Select Users CSV File", ".csv") { selectedFile ->
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
                                    Text("Import Users CSV...", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PrimaryNavy)
                                }
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
}
