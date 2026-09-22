package de.joelneumann.lojinha.ui.components.userselection

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.joelneumann.lojinha.domain.model.User
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.theme.*

@Composable
fun UserGrid(
    users: List<User>,
    onUserClick: (User) -> Unit,
    modifier: Modifier = Modifier,
    emptyText: String = "No users found.",
    isSearchActive: Boolean = false,
    onOpenAdminSetup: (() -> Unit)? = null
) {
    if (users.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (!isSearchActive && onOpenAdminSetup != null) {
                val strings = I18n.current
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = SurfaceWhite,
                    shadowElevation = 2.dp,
                    border = BorderStroke(1.dp, DividerBorder),
                    modifier = Modifier.padding(24.dp).widthIn(max = 520.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = AccentNavy.copy(alpha = 0.1f),
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Storefront,
                                    contentDescription = null,
                                    tint = AccentNavy,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        Text(
                            text = strings.firstRunTitle,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryNavy,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = strings.firstRunMessage,
                            fontSize = 14.sp,
                            color = TextSecondaryMuted,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = onOpenAdminSetup,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentNavy),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = SurfaceWhite,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = strings.firstRunAdminLoginBtn,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SurfaceWhite
                                )
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = emptyText,
                    color = TextSecondaryMuted
                )
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = ScreenPadding),
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
