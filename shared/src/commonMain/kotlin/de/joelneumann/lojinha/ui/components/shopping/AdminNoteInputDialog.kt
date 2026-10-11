package de.joelneumann.lojinha.ui.components.shopping

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import de.joelneumann.lojinha.domain.model.User
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.theme.*
import de.joelneumann.lojinha.ui.utils.safeRequestFocus
import de.joelneumann.lojinha.ui.utils.trackUserInteractions

private const val MAX_NOTE_LENGTH = 1000

@Composable
fun AdminNoteInputDialog(
    user: User,
    isSubmitting: Boolean = false,
    errorMessage: String? = null,
    onDismiss: () -> Unit,
    onSubmit: (message: String) -> Unit,
    onUserInteracted: (force: Boolean) -> Unit = {}
) {
    val strings = I18n.current
    var text by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.safeRequestFocus()
    }

    val submitAction = {
        if (!isSubmitting) {
            onUserInteracted(true)
            val clean = text.trim()
            if (clean.isBlank()) {
                localError = strings.noteDialogEmptyError
            } else {
                localError = null
                onSubmit(clean)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite,
        shape = RoundedCornerShape(16.dp),
        properties = DialogProperties(dismissOnBackPress = !isSubmitting, dismissOnClickOutside = !isSubmitting),
        modifier = Modifier
            .widthIn(min = 400.dp, max = 520.dp)
            .trackUserInteractions(onUserInteracted)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    onUserInteracted(true)
                    if (event.key == Key.Escape && !isSubmitting) {
                        onDismiss()
                        true
                    } else if (event.key == Key.Enter && (event.isCtrlPressed || event.isMetaPressed) && !isSubmitting) {
                        submitAction()
                        true
                    } else false
                } else false
            },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lightbulb,
                    contentDescription = null,
                    tint = AccentNavy,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = strings.noteDialogTitle,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryNavy
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = strings.noteDialogSubmittingAs(user.name),
                    fontSize = 13.sp,
                    color = TextSecondarySubtle
                )

                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        if (it.length <= MAX_NOTE_LENGTH) {
                            text = it
                        }
                        if (localError != null) localError = null
                        onUserInteracted(true)
                    },
                    placeholder = {
                        Text(
                            text = strings.noteDialogPlaceholder,
                            fontSize = 13.sp,
                            color = TextSecondaryMuted
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    minLines = 4,
                    maxLines = 6,
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentNavy,
                        unfocusedBorderColor = DividerBorder,
                        focusedTextColor = PrimaryNavy,
                        unfocusedTextColor = PrimaryNavy
                    )
                )

                val displayError = localError ?: errorMessage
                if (displayError != null) {
                    Text(
                        text = displayError,
                        fontSize = 12.sp,
                        color = ColorDangerCrimson,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = submitAction,
                enabled = !isSubmitting,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentNavy)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = SurfaceWhite,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = strings.noteDialogSubmit,
                        color = SurfaceWhite,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = {
                    onUserInteracted(true)
                    onDismiss()
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(text = strings.cancel)
            }
        }
    )
}
