package de.joelneumann.lojinha.ui.components.admin.users

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import de.joelneumann.lojinha.domain.model.Language
import de.joelneumann.lojinha.domain.model.Transaction
import de.joelneumann.lojinha.domain.model.User
import de.joelneumann.lojinha.ui.components.admin.AdminBadgeType
import de.joelneumann.lojinha.ui.components.admin.AdminStatusBadge
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.i18n.LanguageManager
import de.joelneumann.lojinha.ui.theme.*
import de.joelneumann.lojinha.ui.utils.Formatting
import de.joelneumann.lojinha.ui.utils.confirmationDialogKeys
import de.joelneumann.lojinha.ui.utils.pickFolder
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

enum class DateRangePreset {
    LAST_30_DAYS,
    LAST_60_DAYS,
    LAST_90_DAYS,
    CURRENT_MONTH,
    ALL_TIME,
    CUSTOM
}

@Composable
fun UserTransactionsPdfExportDialog(
    user: User,
    userTransactions: List<Transaction>,
    defaultSaveDirectory: String,
    isExporting: Boolean,
    exportResultPath: String? = null,
    errorMessage: String? = null,
    onExportPdf: (destinationFolder: String, fromTimestamp: Long?, toTimestamp: Long?, language: Language) -> Unit,
    onOpenExportedPdf: ((String) -> Unit)? = null,
    onRevealExportedFolder: ((String) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val strings = I18n.current
    var selectedLanguage by remember { mutableStateOf(user.language) }
    var selectedPreset by remember { mutableStateOf(DateRangePreset.LAST_30_DAYS) }
    var destinationFolder by remember { mutableStateOf(defaultSaveDirectory) }

    var customFromText by remember { mutableStateOf("") }
    var customToText by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }

    // Calculate timestamps based on active preset
    val nowMs = remember { Clock.System.now().toEpochMilliseconds() }
    val timeZone = remember { TimeZone.currentSystemDefault() }

    val (fromTimestamp, toTimestamp) = remember(selectedPreset, customFromText, customToText) {
        when (selectedPreset) {
            DateRangePreset.LAST_30_DAYS -> (nowMs - 30L * 24 * 60 * 60 * 1000L) to nowMs
            DateRangePreset.LAST_60_DAYS -> (nowMs - 60L * 24 * 60 * 60 * 1000L) to nowMs
            DateRangePreset.LAST_90_DAYS -> (nowMs - 90L * 24 * 60 * 60 * 1000L) to nowMs
            DateRangePreset.CURRENT_MONTH -> {
                val nowInstant = Clock.System.now()
                val ldt = nowInstant.toLocalDateTime(timeZone)
                val startOfMonth = LocalDate(ldt.year, ldt.month, 1).atStartOfDayIn(timeZone).toEpochMilliseconds()
                startOfMonth to nowMs
            }
            DateRangePreset.ALL_TIME -> null to null
            DateRangePreset.CUSTOM -> {
                val parsedFrom = parseDateStringToMillis(customFromText, timeZone, isStartOfDay = true)
                val parsedTo = parseDateStringToMillis(customToText, timeZone, isStartOfDay = false)
                parsedFrom to parsedTo
            }
        }
    }

    // Filter transactions for preview
    val filteredTransactions = remember(userTransactions, fromTimestamp, toTimestamp) {
        userTransactions
            .filter { tx ->
                (fromTimestamp == null || tx.timestamp >= fromTimestamp) &&
                (toTimestamp == null || tx.timestamp <= toTimestamp)
            }
            .sortedBy { it.timestamp }
    }

    val initialBalance = remember(filteredTransactions, user.balance) {
        if (filteredTransactions.isNotEmpty()) {
            filteredTransactions.first().userBalanceBefore ?: user.balance
        } else {
            user.balance
        }
    }

    val finalBalance = remember(filteredTransactions, user.balance) {
        if (filteredTransactions.isNotEmpty()) {
            filteredTransactions.last().userBalanceAfter ?: user.balance
        } else {
            user.balance
        }
    }

    var totalDeposits = 0L
    var totalPurchases = 0L
    for (tx in filteredTransactions) {
        if (tx.totalAmount > 0) totalDeposits += tx.totalAmount
        else if (tx.totalAmount < 0) totalPurchases += kotlin.math.abs(tx.totalAmount)
    }
    val netChange = totalDeposits - totalPurchases

    val handleExportSubmit = {
        if (selectedPreset == DateRangePreset.CUSTOM) {
            if (fromTimestamp != null && toTimestamp != null && fromTimestamp > toTimestamp) {
                validationError = strings.errInvalidDateRange
            } else if (destinationFolder.isBlank()) {
                validationError = strings.selectDestinationFolder
            } else {
                validationError = null
                onExportPdf(destinationFolder.trim(), fromTimestamp, toTimestamp, selectedLanguage)
            }
        } else {
            if (destinationFolder.isBlank()) {
                validationError = strings.selectDestinationFolder
            } else {
                validationError = null
                onExportPdf(destinationFolder.trim(), fromTimestamp, toTimestamp, selectedLanguage)
            }
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (!isExporting) onDismiss()
        },
        properties = DialogProperties(
            dismissOnBackPress = !isExporting,
            dismissOnClickOutside = !isExporting
        ),
        containerColor = SurfaceWhite,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .widthIn(min = 520.dp, max = 640.dp)
            .confirmationDialogKeys(
                onCancel = { if (!isExporting) onDismiss() },
                onConfirm = { if (!isExporting && exportResultPath == null) handleExportSubmit() }
            ),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Print,
                    contentDescription = null,
                    tint = PrimaryNavy,
                    modifier = Modifier.size(24.dp)
                )
                Column {
                    Text(
                        text = strings.exportTransactionsTitle,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = PrimaryNavy
                    )
                    Text(
                        text = "${user.name} • ${strings.balance}: ${Formatting.formatBrl(user.balance)}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondaryMuted
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (exportResultPath != null) {
                    // Success View
                    Surface(
                        color = ColorSuccessEmerald.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, ColorSuccessEmerald.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = ColorSuccessEmerald,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = strings.pdfExportSuccess,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = ColorSuccessEmerald
                                )
                            }
                            Text(
                                text = exportResultPath,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Normal,
                                color = PrimaryNavy
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (onOpenExportedPdf != null) {
                            Button(
                                onClick = { onOpenExportedPdf(exportResultPath) },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentNavy),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f).height(40.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PictureAsPdf,
                                        contentDescription = null,
                                        tint = SurfaceWhite,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(strings.openPdf, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SurfaceWhite)
                                }
                            }
                        }

                        if (onRevealExportedFolder != null) {
                            OutlinedButton(
                                onClick = { onRevealExportedFolder(exportResultPath) },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f).height(40.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FolderOpen,
                                        contentDescription = null,
                                        tint = PrimaryNavy,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(strings.revealInFolder, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PrimaryNavy)
                                }
                            }
                        }
                    }
                } else {
                    // Configuration Form

                    // 1. Language Selection Row
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = strings.statementLanguage,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryNavy
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val languages = listOf(
                                Language.BR to "🇧🇷 Português",
                                Language.DE to "🇩🇪 Deutsch",
                                Language.EN to "🇬🇧 English"
                            )
                            languages.forEach { (lang, label) ->
                                val isSelected = selectedLanguage == lang
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedLanguage = lang },
                                    label = { Text(label, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = AccentNavy,
                                        selectedLabelColor = SurfaceWhite
                                    )
                                )
                            }
                        }
                    }

                    // 2. Period Presets
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = strings.filterPeriod,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryNavy
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val presets = listOf(
                                DateRangePreset.LAST_30_DAYS to strings.periodLast30Days,
                                DateRangePreset.LAST_60_DAYS to strings.periodLast60Days,
                                DateRangePreset.LAST_90_DAYS to strings.periodLast90Days,
                                DateRangePreset.CURRENT_MONTH to strings.periodCurrentMonth,
                                DateRangePreset.ALL_TIME to strings.periodAllTime,
                                DateRangePreset.CUSTOM to strings.periodCustom
                            )
                            presets.forEach { (preset, label) ->
                                val isSelected = selectedPreset == preset
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        selectedPreset = preset
                                        validationError = null
                                    },
                                    label = { Text(label, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = AccentNavy,
                                        selectedLabelColor = SurfaceWhite
                                    )
                                )
                            }
                        }
                    }

                    // Custom Date Range Inputs (if CUSTOM selected)
                    if (selectedPreset == DateRangePreset.CUSTOM) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = customFromText,
                                onValueChange = { customFromText = it },
                                label = { Text("${strings.dateFrom} (DD/MM/YYYY)", fontSize = 11.sp) },
                                placeholder = { Text("01/01/2026", fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = customToText,
                                onValueChange = { customToText = it },
                                label = { Text("${strings.dateTo} (DD/MM/YYYY)", fontSize = 11.sp) },
                                placeholder = { Text("31/01/2026", fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // 3. Destination Folder Picker
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = strings.destinationFolder,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryNavy
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = destinationFolder,
                                onValueChange = { destinationFolder = it },
                                placeholder = { Text(strings.selectDestinationFolder) },
                                singleLine = true,
                                enabled = !isExporting,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    pickFolder { path ->
                                        destinationFolder = path
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                enabled = !isExporting,
                                colors = ButtonDefaults.buttonColors(containerColor = AccentNavy),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FolderOpen,
                                        contentDescription = null,
                                        tint = SurfaceWhite,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(strings.browseBtn)
                                }
                            }
                        }
                    }

                    // 4. Live Summary Preview Card
                    Surface(
                        color = SurfaceContainerHighLight,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = strings.transactionsCountPreview(filteredTransactions.size),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryNavy
                                )
                                AdminStatusBadge(
                                    text = "${strings.finalBalance}: ${Formatting.formatBrl(finalBalance)}",
                                    type = if (finalBalance >= 0) AdminBadgeType.SUCCESS else AdminBadgeType.DANGER
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${strings.initialBalance}: ${Formatting.formatBrl(initialBalance)}",
                                    fontSize = 11.sp,
                                    color = TextSecondaryMuted
                                )
                                val netPrefix = if (netChange > 0) "+" else ""
                                Text(
                                    text = "${strings.periodNetLabel}: $netPrefix${Formatting.formatBrl(netChange)}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (netChange >= 0) ColorSuccessEmerald else ColorDangerCrimson
                                )
                            }
                        }
                    }

                    // Error Message
                    val activeError = validationError ?: errorMessage
                    if (activeError != null) {
                        Surface(
                            color = ColorDangerCrimson.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = ColorDangerCrimson,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = activeError,
                                    fontSize = 12.sp,
                                    color = ColorDangerCrimson,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (exportResultPath == null) {
                Button(
                    onClick = handleExportSubmit,
                    enabled = !isExporting,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentNavy),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (isExporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = SurfaceWhite,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = null,
                                tint = SurfaceWhite,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = strings.exportPdf,
                                fontWeight = FontWeight.Bold,
                                color = SurfaceWhite
                            )
                        }
                    }
                }
            } else {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentNavy),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(strings.confirm, fontWeight = FontWeight.Bold, color = SurfaceWhite)
                }
            }
        },
        dismissButton = {
            if (exportResultPath == null) {
                OutlinedButton(
                    onClick = onDismiss,
                    enabled = !isExporting,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(strings.cancel)
                }
            }
        }
    )
}

private fun parseDateStringToMillis(input: String, timeZone: TimeZone, isStartOfDay: Boolean): Long? {
    val trimmed = input.trim()
    if (trimmed.isBlank()) return null
    val parts = trimmed.split("/", "-", ".")
    if (parts.size != 3) return null

    val day = parts[0].toIntOrNull() ?: return null
    val month = parts[1].toIntOrNull() ?: return null
    val year = parts[2].toIntOrNull() ?: return null

    return try {
        val date = LocalDate(year, month, day)
        val startOfDayMs = date.atStartOfDayIn(timeZone).toEpochMilliseconds()
        if (isStartOfDay) {
            startOfDayMs
        } else {
            startOfDayMs + (24L * 60 * 60 * 1000L - 1L)
        }
    } catch (_: Exception) {
        null
    }
}
