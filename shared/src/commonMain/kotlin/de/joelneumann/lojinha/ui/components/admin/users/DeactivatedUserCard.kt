package de.joelneumann.lojinha.ui.components.admin.users

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import de.joelneumann.lojinha.ui.components.userselection.UserAvatar
import de.joelneumann.lojinha.ui.utils.Formatting

@Composable
fun DeactivatedUserCard(
    user: User,
    onActivateUser: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showActivateConfirm by remember { mutableStateOf(false) }

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
                UserAvatar(
                    user = user,
                    modifier = Modifier.size(40.dp),
                    fontSize = 15.sp
                )

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
                            text = "Deactivated",
                            type = AdminBadgeType.WARNING
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
                onClick = { showActivateConfirm = true },
                colors = ButtonDefaults.buttonColors(containerColor = ColorSuccessEmerald),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(38.dp)
            ) {
                Text("⚡ Activate Account", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SurfaceWhite)
            }
        }
    }

    if (showActivateConfirm) {
        AlertDialog(
            onDismissRequest = { showActivateConfirm = false },
            title = { Text("Confirm Account Activation", fontWeight = FontWeight.Bold, color = ColorSuccessEmerald) },
            text = { Text("Are you sure you want to activate user account '${user.name}'?\n\nThis will move the account back into the active users list.") },
            confirmButton = {
                Button(
                    onClick = {
                        onActivateUser()
                        showActivateConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorSuccessEmerald)
                ) {
                    Text("Yes, Activate User", color = SurfaceWhite, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showActivateConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
