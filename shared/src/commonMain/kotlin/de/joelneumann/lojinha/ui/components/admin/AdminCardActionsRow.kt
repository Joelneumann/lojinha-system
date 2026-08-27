package de.joelneumann.lojinha.ui.components.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.joelneumann.lojinha.ui.theme.*

@Composable
fun AdminCardActionsRow(
    hasUnsaved: Boolean,
    onSave: () -> Unit,
    onRevert: () -> Unit,
    saveEnabled: Boolean,
    toggleStatusText: String,
    onToggleStatus: () -> Unit,
    isStatusActive: Boolean,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onSave,
                enabled = saveEnabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentNavy,
                    disabledContainerColor = SurfaceContainerHighLight
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(42.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (hasUnsaved) Icons.Default.Save else Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (hasUnsaved) "Save Changes" else "Saved",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (hasUnsaved) {
                OutlinedButton(
                    onClick = onRevert,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(42.dp)
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
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onToggleStatus,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(42.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (isStatusActive) Icons.Default.Warning else Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (isStatusActive) ColorWarningAmber else ColorSuccessEmerald,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = toggleStatusText.trim(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isStatusActive) ColorWarningAmber else ColorSuccessEmerald
                    )
                }
            }

            Button(
                onClick = onDelete,
                colors = ButtonDefaults.buttonColors(containerColor = ColorDangerCrimson),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(42.dp)
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
                    Text(
                        text = "Delete",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = SurfaceWhite
                    )
                }
            }
        }
    }
}
