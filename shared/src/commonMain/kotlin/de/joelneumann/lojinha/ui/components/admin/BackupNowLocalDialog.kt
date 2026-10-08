package de.joelneumann.lojinha.ui.components.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import de.joelneumann.lojinha.domain.model.BackupFileType
import de.joelneumann.lojinha.domain.model.BackupWriteMode
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.theme.*
import de.joelneumann.lojinha.ui.utils.confirmationDialogKeys
import de.joelneumann.lojinha.ui.utils.pickFolder

@Composable
fun BackupNowLocalDialog(
    initialPath: String,
    isExecuting: Boolean,
    errorMessage: String? = null,
    onExecuteBackup: (destinationPath: String, fileType: BackupFileType, writeMode: BackupWriteMode) -> Unit,
    onDismiss: () -> Unit
) {
    val strings = I18n.current
    var locationPath by remember { mutableStateOf(initialPath) }
    var fileType by remember { mutableStateOf(BackupFileType.DB) }
    var writeMode by remember { mutableStateOf(BackupWriteMode.CREATE_NEW_FILE) }
    var localError by remember { mutableStateOf<String?>(null) }

    val handleConfirm = {
        val trimmed = locationPath.trim()
        if (trimmed.isBlank()) {
            localError = strings.pleaseSelectDestinationFolder
        } else {
            localError = null
            onExecuteBackup(trimmed, fileType, writeMode)
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (!isExecuting) onDismiss()
        },
        properties = DialogProperties(
            dismissOnBackPress = !isExecuting,
            dismissOnClickOutside = !isExecuting
        ),
        containerColor = SurfaceWhite,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.confirmationDialogKeys(
            onCancel = { if (!isExecuting) onDismiss() },
            onConfirm = { if (!isExecuting) handleConfirm() }
        ),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Save,
                    contentDescription = null,
                    tint = PrimaryNavy,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = strings.backupNowLocalTitle,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = PrimaryNavy
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Info banner explaining immediate backup
                Surface(
                    color = PrimaryNavy.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = PrimaryNavy,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = strings.backupNowLocalDescription,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = PrimaryNavy
                        )
                    }
                }

                // Location Picker
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = strings.hostSaveLocationLabel,
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
                            value = locationPath,
                            onValueChange = {
                                locationPath = it
                                localError = null
                            },
                            placeholder = { Text(strings.specifySaveLocation) },
                            singleLine = true,
                            enabled = !isExecuting,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                pickFolder { path ->
                                    locationPath = path
                                    localError = null
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            enabled = !isExecuting,
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

                // Backup Format (File Type) Chips
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = strings.backupFileFormatLabel,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryNavy
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = fileType == BackupFileType.DB,
                            onClick = { if (!isExecuting) fileType = BackupFileType.DB },
                            enabled = !isExecuting,
                            label = { Text(strings.dbFileOption) }
                        )
                        FilterChip(
                            selected = fileType == BackupFileType.CSV,
                            onClick = { if (!isExecuting) fileType = BackupFileType.CSV },
                            enabled = !isExecuting,
                            label = { Text(strings.csvFilesOption) }
                        )
                        FilterChip(
                            selected = fileType == BackupFileType.BOTH,
                            onClick = { if (!isExecuting) fileType = BackupFileType.BOTH },
                            enabled = !isExecuting,
                            label = { Text(strings.bothOption) }
                        )
                    }
                }

                // File Overwrite Strategy Chips
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = strings.fileOverwriteStrategyLabel,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryNavy
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = writeMode == BackupWriteMode.CREATE_NEW_FILE,
                            onClick = { if (!isExecuting) writeMode = BackupWriteMode.CREATE_NEW_FILE },
                            enabled = !isExecuting,
                            label = { Text(strings.timestampedNewFileOption) }
                        )
                        FilterChip(
                            selected = writeMode == BackupWriteMode.OVERWRITE_LATEST,
                            onClick = { if (!isExecuting) writeMode = BackupWriteMode.OVERWRITE_LATEST },
                            enabled = !isExecuting,
                            label = { Text(strings.overwriteSingleFileOption) }
                        )
                    }
                }

                // Error message if validation or execution failed
                val effectiveError = localError ?: errorMessage
                if (effectiveError != null) {
                    Text(
                        text = effectiveError,
                        color = ColorDangerCrimson,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = handleConfirm,
                enabled = !isExecuting,
                colors = ButtonDefaults.buttonColors(containerColor = AccentNavy),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isExecuting) {
                        CircularProgressIndicator(
                            color = SurfaceWhite,
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = strings.backingUpInProgress,
                            color = SurfaceWhite,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = null,
                            tint = SurfaceWhite,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = strings.backupNowActionBtn,
                            color = SurfaceWhite,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                enabled = !isExecuting,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(strings.cancel)
            }
        }
    )
}
