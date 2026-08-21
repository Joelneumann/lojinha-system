package de.joelneumann.lojinha.ui.components.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.joelneumann.lojinha.ui.components.general.SearchInputField
import de.joelneumann.lojinha.ui.theme.AccentNavy
import de.joelneumann.lojinha.ui.theme.SurfaceWhite

@Composable
fun AdminTopBar(
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    countText: String,
    onSearchSubmitted: () -> Unit,
    actionButtonText: String? = null,
    onActionButtonClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().height(56.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            SearchInputField(
                query = searchQuery,
                onQueryChange = onQueryChange,
                placeholder = placeholder,
                onSearchSubmitted = onSearchSubmitted,
                modifier = Modifier.fillMaxSize()
            )
        }

        AdminCountPill(
            text = countText
        )

        if (actionButtonText != null && onActionButtonClick != null) {
            Button(
                onClick = onActionButtonClick,
                colors = ButtonDefaults.buttonColors(containerColor = AccentNavy),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxHeight()
            ) {
                Text(actionButtonText, color = SurfaceWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}
