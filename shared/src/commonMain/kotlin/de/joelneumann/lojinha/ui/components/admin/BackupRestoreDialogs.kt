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
import de.joelneumann.lojinha.ui.theme.*
import de.joelneumann.lojinha.ui.utils.PlatformFile

@Composable
fun DbRestoreMultiApprovalDialog(
    file: PlatformFile,
    adminPasswordHash: String,
    onConfirmRestore: () -> Unit,
    onDismiss: () -> Unit
) {
    var step by remember { mutableStateOf(1) }
    var inputPassword by remember { mutableStateOf("") }
    var inputPhrase by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    imageVector = if (step == 1) Icons.Default.Warning else Icons.Default.Lock,
                    contentDescription = null,
                    tint = ColorDangerCrimson,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = if (step == 1) "Step 1 of 2: Confirm Database Restore" else "Step 2 of 2: Security Authorization",
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
                                Text("CRITICAL WARNING:", fontWeight = FontWeight.Bold, color = ColorDangerCrimson, fontSize = 14.sp)
                            }
                            Text(
                                "Restoring this .db file will COMPLETE OVERWRITE all current data in the application database.",
                                fontSize = 13.sp,
                                color = PrimaryNavy
                            )
                            Text(
                                "All existing products, users, balances, transactions, and settings will be PERMANENTLY ERASED and replaced with the backup file.",
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
                            Text("Backup File Details:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = PrimaryNavy)
                            Text("Filename: ${file.name}", fontSize = 12.sp, color = AccentNavy)
                            Text("File Path: ${file.absolutePath}", fontSize = 11.sp, color = PrimaryNavy.copy(alpha = 0.7f))
                        }
                    }
                } else {
                    Text(
                        "To complete the database restore, enter the Admin Master Password and type the keyword 'RESTORE':",
                        fontSize = 13.sp,
                        color = PrimaryNavy
                    )

                    OutlinedTextField(
                        value = inputPassword,
                        onValueChange = { inputPassword = it; errorMessage = null },
                        label = { Text("Admin Master Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = inputPhrase,
                        onValueChange = { inputPhrase = it; errorMessage = null },
                        label = { Text("Type 'RESTORE' to confirm") },
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
                    Text("Proceed to Authorization ➔", color = SurfaceWhite, fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = {
                        val isPasswordCorrect = inputPassword == adminPasswordHash || (adminPasswordHash == "admin" && inputPassword == "admin")
                        val isPhraseCorrect = inputPhrase.trim() == "RESTORE"

                        if (!isPasswordCorrect) {
                            errorMessage = "Incorrect Admin Password."
                        } else if (!isPhraseCorrect) {
                            errorMessage = "Please type 'RESTORE' exactly to confirm."
                        } else {
                            onConfirmRestore()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorDangerCrimson),
                    shape = RoundedCornerShape(8.dp)
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
                        Text("RESTORE DATABASE NOW", color = SurfaceWhite, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = {
                    if (step == 2) step = 1 else onDismiss()
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(if (step == 2) "⬅ Back" else "Cancel")
            }
        }
    )
}

@Composable
fun WipeDataMultiApprovalDialog(
    adminPasswordHash: String,
    onConfirmWipe: () -> Unit,
    onDismiss: () -> Unit
) {
    var step by remember { mutableStateOf(1) }
    var inputPassword by remember { mutableStateOf("") }
    var inputPhrase by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    imageVector = if (step == 1) Icons.Default.DeleteForever else Icons.Default.Lock,
                    contentDescription = null,
                    tint = ColorDangerCrimson,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = if (step == 1) "Step 1 of 2: Factory Reset / Wipe All Data" else "Step 2 of 2: Security Authorization",
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
                                Text("PERMANENT DATA WIPE WARNING:", fontWeight = FontWeight.Bold, color = ColorDangerCrimson, fontSize = 14.sp)
                            }
                            Text(
                                "This action will completely wipe all database tables:",
                                fontSize = 13.sp,
                                color = PrimaryNavy
                            )
                            Text("• All User accounts & balances will be erased.\n• All Products & stock quantities will be erased.\n• All Transaction history will be erased.\n• System settings will reset to default.", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = ColorDangerCrimson)
                            Text("This action CANNOT BE UNDONE!", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ColorDangerCrimson)
                        }
                    }
                } else {
                    Text(
                        "To complete the Factory Reset, enter the Admin Master Password and type 'WIPE':",
                        fontSize = 13.sp,
                        color = PrimaryNavy
                    )

                    OutlinedTextField(
                        value = inputPassword,
                        onValueChange = { inputPassword = it; errorMessage = null },
                        label = { Text("Admin Master Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = inputPhrase,
                        onValueChange = { inputPhrase = it; errorMessage = null },
                        label = { Text("Type 'WIPE' to confirm") },
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
                    Text("Proceed to Authorization ➔", color = SurfaceWhite, fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = {
                        val isPasswordCorrect = inputPassword == adminPasswordHash || (adminPasswordHash == "admin" && inputPassword == "admin")
                        val isPhraseCorrect = inputPhrase.trim() == "WIPE"

                        if (!isPasswordCorrect) {
                            errorMessage = "Incorrect Admin Password."
                        } else if (!isPhraseCorrect) {
                            errorMessage = "Please type 'WIPE' exactly to confirm."
                        } else {
                            onConfirmWipe()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorDangerCrimson),
                    shape = RoundedCornerShape(8.dp)
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
                        Text("WIPE ALL SYSTEM DATA", color = SurfaceWhite, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = {
                    if (step == 2) step = 1 else onDismiss()
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(if (step == 2) "⬅ Back" else "Cancel")
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
    onConfirmImport: () -> Unit,
    onDismiss: () -> Unit
) {
    var step by remember { mutableStateOf(1) }
    var inputPassword by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    imageVector = if (step == 1) Icons.Default.FileUpload else Icons.Default.Lock,
                    contentDescription = null,
                    tint = PrimaryNavy,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = if (step == 1) "Step 1 of 2: CSV Import Preview ($importType)" else "Step 2 of 2: Admin Approval",
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
                            Text("CSV File: ${file.name}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = PrimaryNavy)
                            Text("Total Rows Found: ${importResultPreview.totalProcessed}", fontSize = 12.sp, color = AccentNavy)
                            Text("New Records to Add: ${importResultPreview.addedCount}", fontSize = 12.sp, color = ColorSuccessEmerald)
                            Text("Existing Records to Update: ${importResultPreview.updatedCount}", fontSize = 12.sp, color = PrimaryNavy)
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
                                    text = "Security Note: Any user barcodes in the CSV will be stripped/cleared to prevent barcode collisions in the kiosk.",
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
                                    text = "Notice: ${importResultPreview.strippedBarcodesCount} product barcode(s) were stripped because they are already assigned to other existing products.",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = ColorDangerCrimson
                                )
                            }
                        }
                    }

                    if (importResultPreview.warnings.isNotEmpty()) {
                        Text("Warnings/Notes:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = PrimaryNavy)
                        LazyColumn(modifier = Modifier.heightIn(max = 120.dp).fillMaxWidth()) {
                            items(importResultPreview.warnings.size) { idx ->
                                Text("• ${importResultPreview.warnings[idx]}", fontSize = 11.sp, color = AccentNavy)
                            }
                        }
                    }
                } else {
                    Text(
                        "Please enter the Admin Master Password to execute the CSV import:",
                        fontSize = 13.sp,
                        color = PrimaryNavy
                    )

                    OutlinedTextField(
                        value = inputPassword,
                        onValueChange = { inputPassword = it; errorMessage = null },
                        label = { Text("Admin Master Password") },
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
                    onClick = { step = 2 },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentNavy),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Proceed to Approval ➔", color = SurfaceWhite, fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = {
                        val isPasswordCorrect = inputPassword == adminPasswordHash || (adminPasswordHash == "admin" && inputPassword == "admin")
                        if (!isPasswordCorrect) {
                            errorMessage = "Incorrect Admin Password."
                        } else {
                            onConfirmImport()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentNavy),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileUpload,
                            contentDescription = null,
                            tint = SurfaceWhite,
                            modifier = Modifier.size(16.dp)
                        )
                        Text("Execute CSV Import", color = SurfaceWhite, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = {
                    if (step == 2) step = 1 else onDismiss()
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(if (step == 2) "⬅ Back" else "Cancel")
            }
        }
    )
}
