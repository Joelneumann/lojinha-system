package de.joelneumann.lojinha.ui.components.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.joelneumann.lojinha.ui.theme.AccentNavy
import de.joelneumann.lojinha.ui.theme.DividerBorder
import de.joelneumann.lojinha.ui.theme.PrimaryNavy
import de.joelneumann.lojinha.ui.theme.SurfaceWhite
import de.joelneumann.lojinha.ui.theme.TextSecondaryMuted
import de.joelneumann.lojinha.ui.utils.SelectAllOnFocusEffect
import de.joelneumann.lojinha.ui.utils.rememberSelectAllOnFocusState

@Composable
fun AdminLabeledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String? = null,
    singleLine: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    selectAllOnFocus: Boolean = false,
    focusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier
) {
    val fieldModifier = Modifier
        .fillMaxWidth()
        .height(56.dp)
        .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }

    if (!selectAllOnFocus) {
        Column(modifier = modifier) {
            Text(text = label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PrimaryNavy, maxLines = 1)
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = placeholder?.let { { Text(it, fontSize = 13.sp, color = TextSecondaryMuted) } },
                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                visualTransformation = visualTransformation,
                trailingIcon = trailingIcon,
                singleLine = singleLine,
                enabled = enabled,
                modifier = fieldModifier,
                shape = RoundedCornerShape(8.dp)
            )
        }
    } else {
        var textFieldValue by remember {
            mutableStateOf(TextFieldValue(text = value, selection = TextRange(value.length)))
        }
        val selectAllState = rememberSelectAllOnFocusState()

        LaunchedEffect(value) {
            if (value != textFieldValue.text) {
                textFieldValue = textFieldValue.copy(
                    text = value,
                    selection = TextRange(value.length)
                )
            }
        }

        SelectAllOnFocusEffect(
            state = selectAllState,
            currentValue = { textFieldValue },
            onUpdateValue = { textFieldValue = it }
        )

        Column(modifier = modifier) {
            Text(text = label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PrimaryNavy, maxLines = 1)
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = textFieldValue,
                onValueChange = { newValue ->
                    if (selectAllState.shouldForceSelectAllOnChange(newValue.text, textFieldValue.text)) {
                        textFieldValue = newValue.copy(
                            selection = TextRange(0, newValue.text.length)
                        )
                    } else {
                        textFieldValue = newValue
                        if (newValue.text != value) {
                            onValueChange(newValue.text)
                        }
                    }
                },
                placeholder = placeholder?.let { { Text(it, fontSize = 13.sp, color = TextSecondaryMuted) } },
                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                visualTransformation = visualTransformation,
                trailingIcon = trailingIcon,
                singleLine = singleLine,
                enabled = enabled,
                modifier = fieldModifier
                    .onFocusChanged { focusState ->
                        val wasFocused = selectAllState.isFocused
                        if (focusState.isFocused) {
                            if (!wasFocused) {
                                selectAllState.onFocusGained(textFieldValue.text)
                                textFieldValue = textFieldValue.copy(
                                    selection = TextRange(0, textFieldValue.text.length)
                                )
                            }
                        } else if (wasFocused) {
                            selectAllState.onFocusLost()
                            textFieldValue = textFieldValue.copy(
                                selection = TextRange(textFieldValue.text.length)
                            )
                        }
                    },
                shape = RoundedCornerShape(8.dp)
            )
        }
    }
}

@Composable
fun <T> AdminSegmentedOptionsRow(
    label: String,
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    optionLabel: (T) -> String,
    modifier: Modifier = Modifier
) {
    AdminSegmentedOptionsRow(
        label = label,
        options = options,
        selected = selected,
        onSelect = onSelect,
        modifier = modifier,
        optionContent = { option ->
            Text(optionLabel(option), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    )
}

@Composable
fun <T> AdminSegmentedOptionsRow(
    label: String,
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    optionContent: @Composable (T) -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(text = label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PrimaryNavy)
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { option ->
                val isSel = selected == option
                OutlinedButton(
                    onClick = { onSelect(option) },
                    modifier = Modifier.weight(1f).height(38.dp).pointerHoverIcon(PointerIcon.Hand),
                    contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (isSel) AccentNavy else SurfaceWhite,
                        contentColor = if (isSel) SurfaceWhite else PrimaryNavy
                    ),
                    border = BorderStroke(1.dp, if (isSel) AccentNavy else DividerBorder),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    optionContent(option)
                }
            }
        }
    }
}
