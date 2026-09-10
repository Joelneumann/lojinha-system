package de.joelneumann.lojinha.ui.utils

import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.delay

/**
 * Requests focus safely after the layout pass has attached the node to the window hierarchy.
 */
suspend fun FocusRequester.safeRequestFocus() {
    for (attempt in 0..4) {
        try {
            withFrameNanos { }
            requestFocus()
            break
        } catch (_: Throwable) {
            delay(25)
        }
    }
}
