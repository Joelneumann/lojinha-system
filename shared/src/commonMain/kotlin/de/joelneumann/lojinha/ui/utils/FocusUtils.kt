package de.joelneumann.lojinha.ui.utils

import androidx.compose.runtime.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.delay

/**
 * Requests focus safely after the layout pass has attached the node to the window hierarchy.
 */
suspend fun FocusRequester.safeRequestFocus(maxAttempts: Int = 10, delayMillis: Long = 25L) {
    for (attempt in 0 until maxAttempts) {
        try {
            withFrameNanos { }
            requestFocus()
            break
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            delay(delayMillis)
        }
    }
}

/**
 * Tracks focus-gain timing so initial pointer-up / tap cursor placement does not override
 * select-all-on-focus on Web and Mobile browsers.
 */
@Stable
class SelectAllOnFocusState {
    var isFocused by mutableStateOf(false)
        private set
    private var focusGainedTime by mutableStateOf(0L)
    private var textOnFocusGain by mutableStateOf("")
    private var userMovedCursor by mutableStateOf(false)

    fun onFocusGained(currentText: String) {
        isFocused = true
        focusGainedTime = currentTimeMillis()
        textOnFocusGain = currentText
        userMovedCursor = false
    }

    fun onFocusLost() {
        isFocused = false
        focusGainedTime = 0L
        textOnFocusGain = ""
        userMovedCursor = false
    }

    fun onCursorNavigationKey() {
        userMovedCursor = true
    }

    fun shouldForceSelectAllOnChange(newText: String, currentText: String): Boolean {
        return isFocused &&
            !userMovedCursor &&
            newText == currentText &&
            newText.isNotEmpty() &&
            (currentTimeMillis() - focusGainedTime < SELECT_ALL_FOCUS_WINDOW_MS)
    }

    fun shouldReapplySelectAll(currentValue: TextFieldValue): Boolean {
        return isFocused &&
            !userMovedCursor &&
            currentValue.text == textOnFocusGain &&
            currentValue.text.isNotEmpty() &&
            currentValue.selection != TextRange(0, currentValue.text.length)
    }

    companion object {
        const val SELECT_ALL_FOCUS_WINDOW_MS = 400L
    }
}

@Composable
fun rememberSelectAllOnFocusState(): SelectAllOnFocusState = remember { SelectAllOnFocusState() }

/**
 * Re-applies full selection shortly after focus gain if a pointer/tap event collapsed the cursor
 * before any text edit or explicit cursor navigation occurred.
 */
@Composable
fun SelectAllOnFocusEffect(
    state: SelectAllOnFocusState,
    currentValue: () -> TextFieldValue,
    onUpdateValue: (TextFieldValue) -> Unit
) {
    LaunchedEffect(state.isFocused) {
        if (state.isFocused) {
            for (waitMs in longArrayOf(60L, 140L, 160L)) {
                delay(waitMs)
                val value = currentValue()
                if (state.shouldReapplySelectAll(value)) {
                    onUpdateValue(value.copy(selection = TextRange(0, value.text.length)))
                }
            }
        }
    }
}
