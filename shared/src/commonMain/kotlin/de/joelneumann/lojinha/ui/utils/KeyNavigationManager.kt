package de.joelneumann.lojinha.ui.utils

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.*

/**
 * Attaches keyboard shortcuts for confirmation popups (ESC = Cancel / Left, Enter = Confirm / Right).
 * Intercepts in the preview phase so button focus does not override the standard modal shortcuts.
 * Automatically gains focus and requests focus so that key events like Enter and Escape are captured immediately.
 */
fun Modifier.confirmationDialogKeys(
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean = true
): Modifier = this.composed {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.safeRequestFocus()
    }

    this
        .focusRequester(focusRequester)
        .focusable()
        .onPreviewKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown) {
                when (event.key) {
                    Key.Escape -> {
                        onCancel()
                        true
                    }
                    Key.Enter, Key.NumPadEnter -> {
                        if (confirmEnabled) {
                            onConfirm()
                            true
                        } else {
                            false
                        }
                    }
                    else -> false
                }
            } else false
        }
}

/**
 * Attaches keyboard shortcuts for form modals that may contain text fields or child controls.
 * Intercepts ESC in the preview phase to reliably dismiss or trigger the unsaved guard.
 * Enter is intercepted in onPreviewKeyEvent and only fires if confirmEnabled is true
 * (allowing callers to provide an interceptEnter callback for special cases like barcode addition).
 */
fun Modifier.formModalKeys(
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean = true,
    interceptEnter: ((KeyEvent) -> Boolean)? = null
): Modifier = this.onPreviewKeyEvent { event ->
    if (event.type == KeyEventType.KeyDown) {
        when (event.key) {
            Key.Escape -> {
                onCancel()
                true
            }
            Key.Enter, Key.NumPadEnter -> {
                if (interceptEnter != null && interceptEnter(event)) {
                    true
                } else if (confirmEnabled) {
                    onConfirm()
                    true
                } else false
            }
            else -> false
        }
    } else false
}
