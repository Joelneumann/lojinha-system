package de.joelneumann.lojinha.ui.components.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.joelneumann.lojinha.domain.model.*
import de.joelneumann.lojinha.ui.theme.*
import de.joelneumann.lojinha.ui.utils.PlatformFile
import de.joelneumann.lojinha.ui.utils.currentTimeMillis

private enum class ScheduleMode { TIMED, INTERVAL }

@Composable
fun BackupRoutineDialog(
    initialRoutine: BackupRoutine?,
    onSaveRoutine: (BackupRoutine) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialRoutine?.name ?: "") }
    var locationPath by remember { mutableStateOf(initialRoutine?.backupLocationPath ?: "") }
    var fileType by remember { mutableStateOf(initialRoutine?.fileType ?: BackupFileType.DB) }
    
    var scheduleMode by remember {
        val mode = if (initialRoutine?.scheduleConfig is BackupScheduleConfig.Interval) ScheduleMode.INTERVAL else ScheduleMode.TIMED
        mutableStateOf(mode)
    }

    var timedTime by remember {
        val time = (initialRoutine?.scheduleConfig as? BackupScheduleConfig.Timed)?.timeOfDay ?: "02:00"
        mutableStateOf(time)
    }

    var intervalHours by remember {
        val h = (initialRoutine?.scheduleConfig as? BackupScheduleConfig.Interval)?.intervalHours ?: 1
        mutableStateOf(h.toString())
    }

    var intervalMinutes by remember {
        val m = (initialRoutine?.scheduleConfig as? BackupScheduleConfig.Interval)?.intervalMinutes ?: 0
        mutableStateOf(m.toString())
    }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun pickFolder(onSelect: (String) -> Unit) {
        // Desktop file picker fallback
        onSelect(locationPath.ifBlank { "/backups" })
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialRoutine == null) "➕ Create New Backup Routine" else "✏️ Edit Routine: '${initialRoutine.name}'",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = PrimaryNavy
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (initialRoutine != null) {
                    Surface(
                        color = AccentNavy.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "✏️ You are editing an existing backup routine. Changes will update the active routine schedule.",
                            modifier = Modifier.padding(10.dp),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PrimaryNavy
                        )
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; errorMessage = null },
                    label = { Text("Routine Name") },
                    placeholder = { Text("e.g. Nightly DB Backup") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Location Picker
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = locationPath,
                        onValueChange = { locationPath = it; errorMessage = null },
                        label = { Text("Host Save Location") },
                        placeholder = { Text("Select folder path...") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = { pickFolder { path -> locationPath = path } },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentNavy),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text("📁 Browse...")
                    }
                }

                // File Type FilterChips
                Column {
                    Text("Backup File Format:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PrimaryNavy)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = fileType == BackupFileType.DB,
                            onClick = { fileType = BackupFileType.DB },
                            label = { Text(".db File") }
                        )
                        FilterChip(
                            selected = fileType == BackupFileType.CSV,
                            onClick = { fileType = BackupFileType.CSV },
                            label = { Text(".csv Files") }
                        )
                        FilterChip(
                            selected = fileType == BackupFileType.BOTH,
                            onClick = { fileType = BackupFileType.BOTH },
                            label = { Text("Both") }
                        )
                    }
                }

                // Schedule Type FilterChips
                Column {
                    Text("Schedule Type:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PrimaryNavy)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = scheduleMode == ScheduleMode.TIMED,
                            onClick = { scheduleMode = ScheduleMode.TIMED },
                            label = { Text("Fixed Time (Daily)") }
                        )
                        FilterChip(
                            selected = scheduleMode == ScheduleMode.INTERVAL,
                            onClick = { scheduleMode = ScheduleMode.INTERVAL },
                            label = { Text("Recurring Interval") }
                        )
                    }
                }

                if (scheduleMode == ScheduleMode.TIMED) {
                    OutlinedTextField(
                        value = timedTime,
                        onValueChange = { timedTime = it; errorMessage = null },
                        label = { Text("Daily Fixed Time (HH:mm 24h)") },
                        placeholder = { Text("02:00") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = intervalHours,
                            onValueChange = { intervalHours = it; errorMessage = null },
                            label = { Text("Hours") },
                            placeholder = { Text("1") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = intervalMinutes,
                            onValueChange = { intervalMinutes = it; errorMessage = null },
                            label = { Text("Minutes") },
                            placeholder = { Text("30") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
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
                        errorMessage = "Routine name cannot be empty."
                        return@Button
                    }
                    if (locationPath.isBlank()) {
                        errorMessage = "Please specify a save location path."
                        return@Button
                    }

                    val scheduleConfig: BackupScheduleConfig = if (scheduleMode == ScheduleMode.TIMED) {
                        BackupScheduleConfig.Timed(timedTime.ifBlank { "02:00" })
                    } else {
                        val h = intervalHours.toIntOrNull() ?: 1
                        val m = intervalMinutes.toIntOrNull() ?: 0
                        val existingAnchor = (initialRoutine?.scheduleConfig as? BackupScheduleConfig.Interval)?.anchorStartTimestamp
                        BackupScheduleConfig.Interval(
                            intervalHours = h,
                            intervalMinutes = m,
                            anchorStartTimestamp = existingAnchor ?: currentTimeMillis()
                        )
                    }

                    val routine = BackupRoutine(
                        id = initialRoutine?.id?.ifBlank { "rt-${currentTimeMillis()}" } ?: "rt-${currentTimeMillis()}",
                        name = name,
                        isEnabled = initialRoutine?.isEnabled ?: true,
                        type = BackupType.LOCAL,
                        fileType = fileType,
                        scheduleConfig = scheduleConfig,
                        backupLocationPath = locationPath,
                        lastBackupTimestamp = initialRoutine?.lastBackupTimestamp
                    )

                    onSaveRoutine(routine)
                },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentNavy)
            ) {
                Text("💾 Save Routine", color = SurfaceWhite, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Cancel")
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "🗑️ Delete Backup Routine?",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = ColorDangerCrimson
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Are you sure you want to delete this backup routine? Automated backups for this schedule will stop permanently.",
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
                Text("🗑️ Delete Routine", color = SurfaceWhite, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Cancel")
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
    val actionText = if (targetState) "Activate" else "Deactivate"
    val icon = if (targetState) "▶️" else "⏸️"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "$icon $actionText Backup Routine?",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = PrimaryNavy
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (targetState) {
                        "Are you sure you want to activate automated backups for '${routine.name}'?"
                    } else {
                        "Are you sure you want to deactivate automated backups for '${routine.name}'? Automated background runs will be paused."
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
                Text("$icon Yes, $actionText", color = SurfaceWhite, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Cancel")
            }
        }
    )
}
