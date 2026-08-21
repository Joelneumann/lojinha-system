package de.joelneumann.lojinha.ui.components.userselection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.joelneumann.lojinha.domain.model.User
import de.joelneumann.lojinha.ui.theme.TextSecondaryMuted

@Composable
fun UserGrid(
    users: List<User>,
    onUserClick: (User) -> Unit,
    modifier: Modifier = Modifier,
    emptyText: String = "No users found."
) {
    if (users.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = emptyText,
                color = TextSecondaryMuted
            )
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = modifier.fillMaxSize()
        ) {
            items(users, key = { it.id }) { user ->
                UserCard(
                    user = user,
                    onClick = { onUserClick(user) }
                )
            }
        }
    }
}
