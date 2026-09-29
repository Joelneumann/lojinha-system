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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOn
import de.joelneumann.lojinha.domain.model.User
import de.joelneumann.lojinha.ui.components.admin.AdminBadgeType
import de.joelneumann.lojinha.ui.components.admin.AdminStatusBadge
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.theme.*
import de.joelneumann.lojinha.ui.components.userselection.UserAvatar
import de.joelneumann.lojinha.ui.utils.Formatting
import androidx.compose.ui.window.DialogProperties
import de.joelneumann.lojinha.ui.utils.confirmationDialogKeys

@Composable
fun DeactivatedUserCard(
    user: User,
    onActivateUser: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = I18n.current
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
                            text = strings.deactivated,
                            type = AdminBadgeType.WARNING
                        )
                    }
                    Text(
                        text = "${strings.balance}: ${Formatting.formatBrl(user.balance)}" +
                                if (user.userBarcodeNumber != null) strings.userBarcodeIdBadge(user.userBarcodeNumber) else "",
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FlashOn,
                        contentDescription = null,
                        tint = SurfaceWhite,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(strings.activateUser, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SurfaceWhite)
                }
            }
        }
    }

    if (showActivateConfirm) {
        val dismissDialog = { showActivateConfirm = false }
        val confirmActivate = {
            onActivateUser()
            showActivateConfirm = false
        }

        AlertDialog(
            onDismissRequest = dismissDialog,
            containerColor = SurfaceWhite,
            shape = RoundedCornerShape(16.dp),
            properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true),
            modifier = Modifier.confirmationDialogKeys(onCancel = dismissDialog, onConfirm = confirmActivate),
            title = { Text(strings.confirmUserActivationTitle, fontWeight = FontWeight.Bold, color = ColorSuccessEmerald) },
            text = { Text(strings.confirmUserActivationMsg(user.name)) },
            confirmButton = {
                Button(
                    onClick = confirmActivate,
                    colors = ButtonDefaults.buttonColors(containerColor = ColorSuccessEmerald)
                ) {
                    Text(strings.yesActivateUser, color = SurfaceWhite, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = dismissDialog) {
                    Text(strings.cancel)
                }
            }
        )
    }
}
