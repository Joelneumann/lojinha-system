package de.joelneumann.lojinha.ui.components.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.joelneumann.lojinha.domain.model.*
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.theme.*
import de.joelneumann.lojinha.ui.utils.PlatformFile
import de.joelneumann.lojinha.ui.utils.currentTimeMillis
import de.joelneumann.lojinha.ui.utils.generateUuid
import de.joelneumann.lojinha.ui.utils.pickFolder
import androidx.compose.ui.window.DialogProperties
import de.joelneumann.lojinha.ui.utils.confirmationDialogKeys

private enum class ScheduleMode { TIMED, INTERVAL, ON_DATA_CHANGE }

@Composable
fun BackupRoutineDialog(
    initialRoutine: BackupRoutine?,
    onSaveRoutine: (BackupRoutine) -> Unit,
    onDismiss: () -> Unit
) {
    val strings = I18n.current
    var name by remember { mutableStateOf(initialRoutine?.name ?: "") }
    var locationPath by remember { mutableStateOf(initialRoutine?.backupLocationPath ?: "") }
    var destinationType by remember { mutableStateOf(initialRoutine?.type ?: BackupType.LOCAL) }
    var fileType by remember { mutableStateOf(initialRoutine?.fileType ?: BackupFileType.DB) }
    var writeMode by remember { mutableStateOf(initialRoutine?.writeMode ?: BackupWriteMode.CREATE_NEW_FILE) }

    val initialScheduleMode = when (initialRoutine?.scheduleConfig) {
        is BackupScheduleConfig.Timed -> ScheduleMode.TIMED
        is BackupScheduleConfig.Interval -> ScheduleMode.INTERVAL
        is BackupScheduleConfig.OnDataChange -> ScheduleMode.ON_DATA_CHANGE
        null -> ScheduleMode.TIMED
    }

    var scheduleMode by remember { mutableStateOf(initialScheduleMode) }

    val initialTimedTime = (initialRoutine?.scheduleConfig as? BackupScheduleConfig.Timed)?.timeOfDay ?: "02:00"
    var timedTime by remember { mutableStateOf(initialTimedTime) }

    val initialIntervalHours = (initialRoutine?.scheduleConfig as? BackupScheduleConfig.Interval)?.intervalHours?.toString() ?: "1"
    var intervalHours by remember { mutableStateOf(initialIntervalHours) }

    val initialIntervalMinutes = (initialRoutine?.scheduleConfig as? BackupScheduleConfig.Interval)?.intervalMinutes?.toString() ?: "0"
    var intervalMinutes by remember { mutableStateOf(initialIntervalMinutes) }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true),
        containerColor = SurfaceWhite,
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                text = if (initialRoutine == null) strings.createNewBackupRoutineTitle else strings.editRoutineTitle(initialRoutine.name),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = PrimaryNavy
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (initialRoutine != null) {
                    Surface(
                        color = PrimaryNavy.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                tint = PrimaryNavy,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = strings.editingRoutineBannerText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = PrimaryNavy
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; errorMessage = null },
                    label = { Text(strings.routineNameLabel) },
                    placeholder = { Text(strings.routineNamePlaceholder) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Destination Target FilterChips
                Column {
                    Text(strings.destinationTargetLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PrimaryNavy)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = destinationType == BackupType.LOCAL,
                            onClick = { destinationType = BackupType.LOCAL },
                            label = { Text(strings.localFolderOption) }
                        )
                        FilterChip(
                            selected = destinationType == BackupType.ONEDRIVE,
                            onClick = {
                                destinationType = BackupType.ONEDRIVE
                                if (locationPath.isBlank() || !locationPath.startsWith("/")) {
                                    locationPath = "/LojinhaBackups"
                                }
                            },
                            label = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Cloud,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(strings.oneDriveOption)
                                }
                            }
                        )
                    }
                }

                // Location Picker
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = locationPath,
                        onValueChange = { locationPath = it; errorMessage = null },
                        label = { Text(if (destinationType == BackupType.ONEDRIVE) strings.oneDriveRemotePathLabel else strings.hostSaveLocationLabel) },
                        placeholder = { Text(if (destinationType == BackupType.ONEDRIVE) "/LojinhaBackups" else strings.specifySaveLocation) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    if (destinationType == BackupType.LOCAL) {
                        Button(
                            onClick = { pickFolder { path -> locationPath = path } },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentNavy),
                            modifier = Modifier.padding(top = 8.dp)
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

                // File Type FilterChips
                Column {
                    Text(strings.backupFileFormatLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PrimaryNavy)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = fileType == BackupFileType.DB,
                            onClick = { fileType = BackupFileType.DB },
                            label = { Text(strings.dbFileOption) }
                        )
                        FilterChip(
                            selected = fileType == BackupFileType.CSV,
                            onClick = { fileType = BackupFileType.CSV },
                            label = { Text(strings.csvFilesOption) }
                        )
                        FilterChip(
                            selected = fileType == BackupFileType.BOTH,
                            onClick = { fileType = BackupFileType.BOTH },
                            label = { Text(strings.bothOption) }
                        )
                    }
                }

                // Write Mode FilterChips
                Column {
                    Text(strings.fileOverwriteStrategyLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PrimaryNavy)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = writeMode == BackupWriteMode.CREATE_NEW_FILE,
                            onClick = { writeMode = BackupWriteMode.CREATE_NEW_FILE },
                            label = { Text(strings.timestampedNewFileOption) }
                        )
                        FilterChip(
                            selected = writeMode == BackupWriteMode.OVERWRITE_LATEST,
                            onClick = { writeMode = BackupWriteMode.OVERWRITE_LATEST },
                            label = { Text(strings.overwriteSingleFileOption) }
                        )
                    }
                }

                // Schedule Type FilterChips
                Column {
                    Text(strings.scheduleTypeLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PrimaryNavy)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = scheduleMode == ScheduleMode.TIMED,
                            onClick = { scheduleMode = ScheduleMode.TIMED },
                            label = { Text(strings.fixedTimeDailyOption) }
                        )
                        FilterChip(
                            selected = scheduleMode == ScheduleMode.INTERVAL,
                            onClick = { scheduleMode = ScheduleMode.INTERVAL },
                            label = { Text(strings.recurringIntervalOption) }
                        )
                        FilterChip(
                            selected = scheduleMode == ScheduleMode.ON_DATA_CHANGE,
                            onClick = { scheduleMode = ScheduleMode.ON_DATA_CHANGE },
                            label = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Bolt,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(strings.onRealtimeChangeOption)
                                }
                            }
                        )
                    }
                }

                if (scheduleMode == ScheduleMode.TIMED) {
                    OutlinedTextField(
                        value = timedTime,
                        onValueChange = { timedTime = it; errorMessage = null },
                        label = { Text(strings.dailyFixedTimeLabel) },
                        placeholder = { Text("02:00") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else if (scheduleMode == ScheduleMode.INTERVAL) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = intervalHours,
                            onValueChange = { intervalHours = it; errorMessage = null },
                            label = { Text(strings.hoursLabel) },
                            placeholder = { Text("1") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = intervalMinutes,
                            onValueChange = { intervalMinutes = it; errorMessage = null },
                            label = { Text(strings.minutesLabel) },
                            placeholder = { Text("30") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    Surface(
                        color = ColorSuccessEmerald.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = ColorSuccessEmerald,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = strings.realtimeBackupDesc,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = PrimaryNavy
                            )
                        }
                    }
                }

                if (errorMessage != null) {
                    Text(errorMessage!!, color = ColorDangerCrimson, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) {
                        errorMessage = strings.routineNameCannotBeEmpty
                        return@Button
                    }
                    if (locationPath.isBlank()) {
                        errorMessage = strings.specifySaveLocation
                        return@Button
                    }

                    val timeRegex = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")
                    val scheduleConfig: BackupScheduleConfig = when (scheduleMode) {
                        ScheduleMode.TIMED -> {
                            val trimmedTime = timedTime.trim()
                            if (!timeRegex.matches(trimmedTime)) {
                                errorMessage = strings.invalidTimeFormat
                                return@Button
                            }
                            BackupScheduleConfig.Timed(trimmedTime)
                        }
                        ScheduleMode.INTERVAL -> {
                            val h = intervalHours.trim().toIntOrNull() ?: 0
                            val m = intervalMinutes.trim().toIntOrNull() ?: 0
                            if (h <= 0 && m <= 0) {
                                errorMessage = strings.invalidIntervalFormat
                                return@Button
                            }
                            val existingAnchor = (initialRoutine?.scheduleConfig as? BackupScheduleConfig.Interval)?.anchorStartTimestamp
                            BackupScheduleConfig.Interval(
                                intervalHours = h,
                                intervalMinutes = m,
                                anchorStartTimestamp = existingAnchor ?: currentTimeMillis()
                            )
                        }
                        ScheduleMode.ON_DATA_CHANGE -> BackupScheduleConfig.OnDataChange()
                    }

                    val routine = BackupRoutine(
                        id = initialRoutine?.id?.ifBlank { generateUuid() } ?: generateUuid(),
                        name = name,
                        isEnabled = initialRoutine?.isEnabled ?: true,
                        type = destinationType,
                        fileType = fileType,
                        writeMode = writeMode,
                        scheduleConfig = scheduleConfig,
                        backupLocationPath = locationPath,
                        lastBackupTimestamp = initialRoutine?.lastBackupTimestamp ?: currentTimeMillis()
                    )

                    onSaveRoutine(routine)
                },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentNavy)
            ) {
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
                    Text(strings.saveRoutineBtn, color = SurfaceWhite, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(strings.cancel)
            }
        }
    )
}

@Composable
fun DeleteRoutineConfirmationDialog(
    routine: BackupRoutine,
    onConfirmDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val strings = I18n.current
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true),
        containerColor = SurfaceWhite,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.confirmationDialogKeys(onCancel = onDismiss, onConfirm = onConfirmDelete),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = ColorDangerCrimson,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = strings.deleteBackupRoutineConfirmTitle,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = ColorDangerCrimson
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = strings.deleteBackupRoutineConfirmMsg,
                    fontSize = 13.sp,
                    color = PrimaryNavy
                )
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerHighLight),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Routine: ${routine.name}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = PrimaryNavy)
                        Text("Format: .${routine.fileType.name.lowercase()}", fontSize = 12.sp, color = AccentNavy)
                        Text("Save Location: ${routine.backupLocationPath}", fontSize = 11.sp, color = AccentNavy)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmDelete,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ColorDangerCrimson)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = SurfaceWhite,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(strings.deleteRoutineBtn, color = SurfaceWhite, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(strings.cancel)
            }
        }
    )
}

@Composable
fun ToggleRoutineConfirmationDialog(
    routine: BackupRoutine,
    targetState: Boolean,
    onConfirmToggle: () -> Unit,
    onDismiss: () -> Unit
) {
    val strings = I18n.current
    val actionText = if (targetState) "Activate" else "Deactivate"

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true),
        containerColor = SurfaceWhite,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.confirmationDialogKeys(onCancel = onDismiss, onConfirm = onConfirmToggle),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (targetState) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = null,
                    tint = PrimaryNavy,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = if (targetState) strings.activateBackupRoutineTitle else strings.deactivateBackupRoutineTitle,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = PrimaryNavy
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (targetState) {
                        strings.activateBackupRoutineMsg(routine.name)
                    } else {
                        strings.deactivateBackupRoutineMsg(routine.name)
                    },
                    fontSize = 13.sp,
                    color = PrimaryNavy
                )
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerHighLight),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Routine: ${routine.name}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = PrimaryNavy)
                        Text("Target Path: ${routine.backupLocationPath}", fontSize = 11.sp, color = AccentNavy)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmToggle,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (targetState) ColorSuccessEmerald else AccentNavy
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (targetState) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = null,
                        tint = SurfaceWhite,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(strings.yesAction(actionText), color = SurfaceWhite, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(strings.cancel)
            }
        }
    )
}
