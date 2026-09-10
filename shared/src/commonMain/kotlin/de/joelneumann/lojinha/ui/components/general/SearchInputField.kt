package de.joelneumann.lojinha.ui.components.general

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.joelneumann.lojinha.ui.theme.AccentNavy
import de.joelneumann.lojinha.ui.theme.DividerBorder
import de.joelneumann.lojinha.ui.theme.SurfaceWhite
import de.joelneumann.lojinha.ui.theme.TextSecondaryMuted

@Composable
fun SearchInputField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    onSearchSubmitted: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onFocusChanged: ((FocusState) -> Unit)? = null,
    onEscape: (() -> Unit)? = null,
    onKeyDown: ((KeyEvent) -> Boolean)? = null
) {
    var baseModifier = modifier
        .fillMaxWidth()
        .height(56.dp)

    if (focusRequester != null) {
        baseModifier = baseModifier.focusRequester(focusRequester)
    }

    if (onFocusChanged != null) {
        baseModifier = baseModifier.onFocusChanged(onFocusChanged)
    }

    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = TextSecondaryMuted
            )
        },
        placeholder = {
            Text(
                text = placeholder,
                color = TextSecondaryMuted,
                fontSize = 15.sp
            )
        },
        textStyle = LocalTextStyle.current.copy(fontSize = 15.sp),
        modifier = baseModifier
            .onPreviewKeyEvent { keyEvent ->
                if (onKeyDown != null && onKeyDown(keyEvent)) {
                    true
                } else if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Escape) {
                    if (query.isNotBlank()) {
                        onQueryChange("")
                        true
                    } else if (onEscape != null) {
                        onEscape()
                        true
                    } else {
                        false
                    }
                } else false
            }
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyUp && (keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter)) {
                    onSearchSubmitted()
                    true
                } else false
            },
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = SurfaceWhite,
            unfocusedContainerColor = SurfaceWhite,
            focusedBorderColor = AccentNavy,
            unfocusedBorderColor = DividerBorder
        ),
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {
            onSearchSubmitted()
        })
    )
}
