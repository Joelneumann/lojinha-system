package de.joelneumann.lojinha.ui.components.general

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.theme.ColorDangerCrimson
import de.joelneumann.lojinha.ui.theme.SurfaceWhite

@Composable
fun LogoutButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = I18n.current

    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = ColorDangerCrimson),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Logout,
                contentDescription = null,
                tint = SurfaceWhite,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = strings.logout,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = SurfaceWhite,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}
