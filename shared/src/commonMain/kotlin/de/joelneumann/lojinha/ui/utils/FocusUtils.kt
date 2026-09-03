package de.joelneumann.lojinha.ui.utils

import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester

/**
 * Requests focus safely after the layout pass has attached the node to the window hierarchy.
 */
suspend fun FocusRequester.safeRequestFocus() {
    runCatching {
        withFrameNanos { }
        requestFocus()
    }
}
