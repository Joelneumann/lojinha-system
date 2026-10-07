package de.joelneumann.lojinha.ui.components.general

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.defaultScrollbarStyle
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.joelneumann.lojinha.ui.theme.AccentNavy
import de.joelneumann.lojinha.ui.theme.PrimaryNavy

@Composable
actual fun AppVerticalScrollbar(
    scrollState: LazyListState,
    modifier: Modifier
) {
    VerticalScrollbar(
        adapter = rememberScrollbarAdapter(scrollState),
        modifier = modifier,
        style = defaultScrollbarStyle().copy(
            unhoverColor = PrimaryNavy.copy(alpha = 0.25f),
            hoverColor = AccentNavy.copy(alpha = 0.85f),
            thickness = 8.dp,
            shape = RoundedCornerShape(4.dp),
            minimalHeight = 36.dp
        )
    )
}

@Composable
actual fun AppVerticalScrollbar(
    scrollState: ScrollState,
    modifier: Modifier
) {
    VerticalScrollbar(
        adapter = rememberScrollbarAdapter(scrollState),
        modifier = modifier,
        style = defaultScrollbarStyle().copy(
            unhoverColor = PrimaryNavy.copy(alpha = 0.25f),
            hoverColor = AccentNavy.copy(alpha = 0.85f),
            thickness = 8.dp,
            shape = RoundedCornerShape(4.dp),
            minimalHeight = 36.dp
        )
    )
}
