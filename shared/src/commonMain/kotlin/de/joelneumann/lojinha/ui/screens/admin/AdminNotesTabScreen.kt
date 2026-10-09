package de.joelneumann.lojinha.ui.screens.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.joelneumann.lojinha.domain.model.AdminNote
import de.joelneumann.lojinha.ui.components.admin.AdminTopBar
import de.joelneumann.lojinha.ui.components.general.AppVerticalScrollbar
import de.joelneumann.lojinha.ui.components.general.ConfirmationDialog
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.theme.*
import de.joelneumann.lojinha.ui.utils.Formatting
import de.joelneumann.lojinha.ui.utils.containsIgnoreAccents
import de.joelneumann.lojinha.ui.utils.pageDown
import de.joelneumann.lojinha.ui.utils.pageUp
import de.joelneumann.lojinha.ui.viewmodel.admin.AdminNotesViewModel
import kotlinx.coroutines.launch

@Composable
fun AdminNotesTabScreen(
    viewModel: AdminNotesViewModel,
    onRegisterPageScroller: (((Boolean) -> Boolean) -> Unit)? = null
) {
    val notes by viewModel.notes.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val strings = I18n.current

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var noteToDelete by remember { mutableStateOf<AdminNote?>(null) }

    LaunchedEffect(onRegisterPageScroller, noteToDelete) {
        onRegisterPageScroller?.invoke { isDown ->
            if (noteToDelete == null) {
                coroutineScope.launch {
                    if (isDown) listState.pageDown() else listState.pageUp()
                }
                true
            } else false
        }
    }

    val filteredNotes = remember(notes, searchQuery) {
        if (searchQuery.isBlank()) notes
        else notes.filter { n ->
            n.message.containsIgnoreAccents(searchQuery) ||
                    n.userNameSnapshot.containsIgnoreAccents(searchQuery)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && noteToDelete == null) {
                    when (event.key) {
                        Key.PageDown -> {
                            coroutineScope.launch { listState.pageDown() }
                            true
                        }
                        Key.PageUp -> {
                            coroutineScope.launch { listState.pageUp() }
                            true
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        AdminTopBar(
            searchQuery = searchQuery,
            onQueryChange = viewModel::updateSearchQuery,
            placeholder = strings.searchNotesPlaceholder,
            countText = strings.notesCountText(filteredNotes.size),
            onSearchSubmitted = {}
        )

        Spacer(modifier = Modifier.height(14.dp))

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (filteredNotes.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChatBubbleOutline,
                            contentDescription = null,
                            tint = TextSecondaryMuted,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = if (searchQuery.isBlank()) strings.noNotesTitle else strings.noNotesMsg,
                            color = TextSecondaryMuted,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 32.dp, end = 14.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredNotes, key = { it.id }) { note ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                            border = BorderStroke(1.dp, DividerBorder),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp, vertical = 14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = null,
                                            tint = AccentNavy,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = note.userNameSnapshot,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = PrimaryNavy
                                        )
                                        Text(
                                            text = "•",
                                            fontSize = 14.sp,
                                            color = TextSecondaryMuted
                                        )
                                        Text(
                                            text = Formatting.formatTimestamp(note.timestamp),
                                            fontSize = 12.sp,
                                            color = TextSecondaryMuted
                                        )
                                    }

                                    IconButton(
                                        onClick = { noteToDelete = note },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = strings.deleteNote,
                                            tint = ColorDangerCrimson,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = note.message,
                                    fontSize = 14.sp,
                                    color = PrimaryNavy,
                                    lineHeight = 20.sp
                                )
                            }
                        }
                    }
                }

                AppVerticalScrollbar(
                    scrollState = listState,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .padding(vertical = 4.dp)
                )
            }
        }
    }

    noteToDelete?.let { note ->
        ConfirmationDialog(
            title = strings.confirmDeleteNoteTitle,
            message = strings.confirmDeleteNoteMsg,
            confirmText = strings.deleteNote,
            cancelText = strings.cancel,
            onDismiss = { noteToDelete = null },
            onConfirm = {
                val id = note.id
                noteToDelete = null
                viewModel.deleteNote(id)
            },
            confirmButtonColor = ColorDangerCrimson
        )
    }
}
