package de.joelneumann.lojinha.ui.components.admin.users

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.joelneumann.lojinha.domain.model.User
import de.joelneumann.lojinha.ui.components.admin.AdminBadgeType
import de.joelneumann.lojinha.ui.components.admin.AdminStatusBadge
import de.joelneumann.lojinha.ui.theme.*
import de.joelneumann.lojinha.ui.utils.Formatting

@Composable
fun DeletedUserCard(
    user: User,
    onRestoreUser: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showRestoreConfirm by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = SurfaceWhite,
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(DividerBorder)
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(ColorDangerCrimson.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = user.initials,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorDangerCrimson
                    )
                }

                Column {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = user.name,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryNavy
                        )
                        AdminStatusBadge(
                            text = "Deleted",
                            type = AdminBadgeType.DANGER
                        )
                    }
                    Text(
                        text = "Balance: ${Formatting.formatBrl(user.balance)}" +
                                if (user.userBarcodeNumber != null) " • ID: ${user.userBarcodeNumber}" else "",
                        fontSize = 13.sp,
                        color = TextSecondaryMuted
                    )
                }
            }

            Button(
                onClick = { showRestoreConfirm = true },
                colors = ButtonDefaults.buttonColors(containerColor = ColorSuccessEmerald),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(38.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Restore,
                        contentDescription = null,
                        tint = SurfaceWhite,
                        modifier = Modifier.size(16.dp)
                    )
                    Text("Restore User", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SurfaceWhite)
                }
            }
        }
    }

    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            title = { Text("Confirm Account Restoration", fontWeight = FontWeight.Bold, color = ColorSuccessEmerald) },
            text = { Text("Are you sure you want to restore user account '${user.name}'?\n\nThis will reactivate the account and move it back into the active users list.") },
            confirmButton = {
                Button(
                    onClick = {
                        onRestoreUser()
                        showRestoreConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorSuccessEmerald)
                ) {
                    Text("Yes, Restore User", color = SurfaceWhite, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showRestoreConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
