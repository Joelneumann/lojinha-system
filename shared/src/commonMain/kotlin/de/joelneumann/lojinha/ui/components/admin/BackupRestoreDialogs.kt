package de.joelneumann.lojinha.ui.components.admin

import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.joelneumann.lojinha.domain.model.CsvImportResult
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.theme.*
import de.joelneumann.lojinha.ui.utils.PlatformFile
import androidx.compose.ui.window.DialogProperties
import de.joelneumann.lojinha.ui.utils.confirmationDialogKeys

@Composable
fun DbRestoreMultiApprovalDialog(
    file: PlatformFile,
    adminPasswordHash: String,
    isExecuting: Boolean = false,
    onConfirmRestore: () -> Unit,
    onDismiss: () -> Unit
) {
    val strings = I18n.current
    var step by remember { mutableStateOf(1) }
    var inputPassword by remember { mutableStateOf("") }
    var inputPhrase by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val handleConfirm = {
        if (!isExecuting) {
            if (step == 1) {
                step = 2
            } else {
                val isPasswordCorrect = de.joelneumann.lojinha.security.PasswordHasher.verifyAdminBypass(inputPassword.trim(), adminPasswordHash)
                val isPhraseCorrect = inputPhrase.trim() == "RESTORE"

                if (!isPasswordCorrect) {
                    errorMessage = strings.incorrectAdminPassword
                } else if (!isPhraseCorrect) {
                    errorMessage = strings.typeRestoreExactly
                } else {
                    onConfirmRestore()
                }
            }
        }
    }
    val handleDismiss = {
        if (!isExecuting) {
            if (step == 2) step = 1 else onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = handleDismiss,
        properties = DialogProperties(dismissOnBackPress = !isExecuting, dismissOnClickOutside = !isExecuting),
        containerColor = SurfaceWhite,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.confirmationDialogKeys(onCancel = handleDismiss, onConfirm = handleConfirm),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    imageVector = if (step == 1) Icons.Default.Warning else Icons.Default.Lock,
                    contentDescription = null,
                    tint = ColorDangerCrimson,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = if (step == 1) strings.step1DbRestoreTitle else strings.step2SecurityTitle,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = ColorDangerCrimson
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (step == 1) {
                    Surface(
                        color = ColorDangerCrimson.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = ColorDangerCrimson,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(strings.criticalWarningHeader, fontWeight = FontWeight.Bold, color = ColorDangerCrimson, fontSize = 14.sp)
                            }
                            Text(
                                strings.dbRestoreWarning1,
                                fontSize = 13.sp,
                                color = PrimaryNavy
                            )
                            Text(
                                strings.dbRestoreWarning2,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorDangerCrimson
                            )
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceContainerHighLight),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(strings.backupFileDetailsHeader, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = PrimaryNavy)
                            Text(strings.filenameLabel(file.name), fontSize = 12.sp, color = AccentNavy)
                            Text(strings.filePathLabel(file.absolutePath), fontSize = 11.sp, color = PrimaryNavy.copy(alpha = 0.7f))
                        }
                    }
                } else {
                    Text(
                        strings.dbRestoreAuthMsg,
                        fontSize = 13.sp,
                        color = PrimaryNavy
                    )

                    OutlinedTextField(
                        value = inputPassword,
                        onValueChange = { inputPassword = it; errorMessage = null },
                        label = { Text(strings.adminMasterPasswordLabel) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = inputPhrase,
                        onValueChange = { inputPhrase = it; errorMessage = null },
                        label = { Text(strings.typeRestoreConfirmLabel) },
                        placeholder = { Text("RESTORE") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (errorMessage != null) {
                        Text(errorMessage!!, color = ColorDangerCrimson, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            if (step == 1) {
                Button(
                    onClick = { step = 2 },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorDangerCrimson),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(strings.proceedToAuthBtn, color = SurfaceWhite, fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = handleConfirm,
                    enabled = !isExecuting,
                    colors = ButtonDefaults.buttonColors(containerColor = ColorDangerCrimson),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (isExecuting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = SurfaceWhite,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.RestoreFromTrash,
                                contentDescription = null,
                                tint = SurfaceWhite,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(strings.restoreDatabaseNowBtn, color = SurfaceWhite, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = handleDismiss,
                enabled = !isExecuting,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(if (step == 2) strings.backBtn else strings.cancel)
            }
        }
    )
}

@Composable
fun WipeDataMultiApprovalDialog(
    adminPasswordHash: String,
    isExecuting: Boolean = false,
    onConfirmWipe: () -> Unit,
    onDismiss: () -> Unit
) {
    val strings = I18n.current
    var step by remember { mutableStateOf(1) }
    var inputPassword by remember { mutableStateOf("") }
    var inputPhrase by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val handleConfirm = {
        if (!isExecuting) {
            if (step == 1) {
                step = 2
            } else {
                val isPasswordCorrect = de.joelneumann.lojinha.security.PasswordHasher.verifyAdminBypass(inputPassword.trim(), adminPasswordHash)
                val isPhraseCorrect = inputPhrase.trim() == "WIPE"

                if (!isPasswordCorrect) {
                    errorMessage = strings.incorrectAdminPassword
                } else if (!isPhraseCorrect) {
                    errorMessage = strings.typeWipeExactly
                } else {
                    onConfirmWipe()
                }
            }
        }
    }
    val handleDismiss = {
        if (!isExecuting) {
            if (step == 2) step = 1 else onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = handleDismiss,
        properties = DialogProperties(dismissOnBackPress = !isExecuting, dismissOnClickOutside = !isExecuting),
        containerColor = SurfaceWhite,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.confirmationDialogKeys(onCancel = handleDismiss, onConfirm = handleConfirm),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    imageVector = if (step == 1) Icons.Default.DeleteForever else Icons.Default.Lock,
                    contentDescription = null,
                    tint = ColorDangerCrimson,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = if (step == 1) strings.step1WipeDataTitle else strings.step2SecurityTitle,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = ColorDangerCrimson
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (step == 1) {
                    Surface(
                        color = ColorDangerCrimson.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = ColorDangerCrimson,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(strings.permanentWipeWarningHeader, fontWeight = FontWeight.Bold, color = ColorDangerCrimson, fontSize = 14.sp)
                            }
                            Text(
                                strings.wipeWarning1,
                                fontSize = 13.sp,
                                color = PrimaryNavy
                            )
                            Text(strings.wipeWarningDetails, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = ColorDangerCrimson)
                            Text(strings.actionCannotBeUndone, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ColorDangerCrimson)
                        }
                    }
                } else {
                    Text(
                        strings.wipeAuthMsg,
                        fontSize = 13.sp,
                        color = PrimaryNavy
                    )

                    OutlinedTextField(
                        value = inputPassword,
                        onValueChange = { inputPassword = it; errorMessage = null },
                        label = { Text(strings.adminMasterPasswordLabel) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = inputPhrase,
                        onValueChange = { inputPhrase = it; errorMessage = null },
                        label = { Text(strings.typeWipeConfirmLabel) },
                        placeholder = { Text("WIPE") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (errorMessage != null) {
                        Text(errorMessage!!, color = ColorDangerCrimson, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            if (step == 1) {
                Button(
                    onClick = { step = 2 },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorDangerCrimson),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(strings.proceedToAuthBtn, color = SurfaceWhite, fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = handleConfirm,
                    enabled = !isExecuting,
                    colors = ButtonDefaults.buttonColors(containerColor = ColorDangerCrimson),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (isExecuting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = SurfaceWhite,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.DeleteForever,
                                contentDescription = null,
                                tint = SurfaceWhite,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(strings.wipeAllSystemDataBtn, color = SurfaceWhite, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = handleDismiss,
                enabled = !isExecuting,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(if (step == 2) strings.backBtn else strings.cancel)
            }
        }
    )
}

@Composable
fun CsvImportMultiApprovalDialog(
    file: PlatformFile,
    importResultPreview: CsvImportResult,
    importType: String, // "Products" or "Users"
    adminPasswordHash: String,
    isExecuting: Boolean = false,
    onConfirmImport: () -> Unit,
    onDismiss: () -> Unit
) {
    val strings = I18n.current
    var step by remember { mutableStateOf(1) }
    var inputPassword by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val handleConfirm = {
        if (!isExecuting) {
            if (step == 1) {
                if (importResultPreview.errors.isEmpty()) {
                    step = 2
                }
            } else {
                val isPasswordCorrect = de.joelneumann.lojinha.security.PasswordHasher.verifyAdminBypass(inputPassword.trim(), adminPasswordHash)
                if (!isPasswordCorrect) {
                    errorMessage = strings.incorrectAdminPassword
                } else {
                    onConfirmImport()
                }
            }
        }
    }
    val handleDismiss = {
        if (!isExecuting) {
            if (step == 2) step = 1 else onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = handleDismiss,
        properties = DialogProperties(dismissOnBackPress = !isExecuting, dismissOnClickOutside = !isExecuting),
        containerColor = SurfaceWhite,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.confirmationDialogKeys(onCancel = handleDismiss, onConfirm = handleConfirm),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    imageVector = if (step == 1) Icons.Default.FileUpload else Icons.Default.Lock,
                    contentDescription = null,
                    tint = PrimaryNavy,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = if (step == 1) strings.step1CsvImportTitle(if (importType == "Products") strings.products else strings.tabUsers) else strings.step2AdminApprovalTitle,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = PrimaryNavy
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (step == 1) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceContainerHighLight),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(strings.csvFileLabel(file.name), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = PrimaryNavy)
                            Text(strings.totalRowsFoundLabel(importResultPreview.totalProcessed), fontSize = 12.sp, color = AccentNavy)
                            Text(strings.newRecordsToAddLabel(importResultPreview.addedCount), fontSize = 12.sp, color = ColorSuccessEmerald)
                            Text(strings.existingRecordsToUpdateLabel(importResultPreview.updatedCount), fontSize = 12.sp, color = PrimaryNavy)
                        }
                    }

                    if (importType == "Users") {
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
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = PrimaryNavy,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = strings.csvUserSecurityNote,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = PrimaryNavy
                                )
                            }
                        }
                    }

                    if (importResultPreview.strippedBarcodesCount > 0) {
                        Surface(
                            color = ColorDangerCrimson.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = ColorDangerCrimson,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = strings.strippedBarcodesNotice(importResultPreview.strippedBarcodesCount),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = ColorDangerCrimson
                                )
                            }
                        }
                    }

                    if (importResultPreview.errors.isNotEmpty()) {
                        Surface(
                            color = ColorDangerCrimson.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                                    Text(
                                        text = strings.fatalImportErrorsHeader,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ColorDangerCrimson
                                    )
                                }
                                LazyColumn(modifier = Modifier.heightIn(max = 120.dp).fillMaxWidth()) {
                                    items(importResultPreview.errors.size) { idx ->
                                        Text("• ${importResultPreview.errors[idx]}", fontSize = 11.sp, color = ColorDangerCrimson)
                                    }
                                }
                            }
                        }
                    }

                    if (importResultPreview.warnings.isNotEmpty()) {
                        Text(strings.warningsNotesHeader, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = PrimaryNavy)
                        LazyColumn(modifier = Modifier.heightIn(max = 120.dp).fillMaxWidth()) {
                            items(importResultPreview.warnings.size) { idx ->
                                Text("• ${importResultPreview.warnings[idx]}", fontSize = 11.sp, color = AccentNavy)
                            }
                        }
                    }
                } else {
                    Text(
                        strings.csvImportAuthMsg,
                        fontSize = 13.sp,
                        color = PrimaryNavy
                    )

                    OutlinedTextField(
                        value = inputPassword,
                        onValueChange = { inputPassword = it; errorMessage = null },
                        label = { Text(strings.adminMasterPasswordLabel) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (errorMessage != null) {
                        Text(errorMessage!!, color = ColorDangerCrimson, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            if (step == 1) {
                Button(
                    onClick = {
                        if (importResultPreview.errors.isEmpty()) {
                            step = 2
                        }
                    },
                    enabled = importResultPreview.errors.isEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentNavy,
                        disabledContainerColor = AccentNavy.copy(alpha = 0.38f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(strings.proceedToApprovalBtn, color = SurfaceWhite, fontWeight = FontWeight.Bold)
                }
            } else {
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
                                modifier = Modifier.size(16.dp),
                                color = SurfaceWhite,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.FileUpload,
                                contentDescription = null,
                                tint = SurfaceWhite,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(strings.executeCsvImportBtn, color = SurfaceWhite, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = handleDismiss,
                enabled = !isExecuting,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(if (step == 2) strings.backBtn else strings.cancel)
            }
        }
    )
}
