package de.joelneumann.lojinha.ui.components.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.focus.FocusRequester
import de.joelneumann.lojinha.ui.components.general.SearchInputField
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.theme.AccentNavy
import de.joelneumann.lojinha.ui.theme.DividerBorder
import de.joelneumann.lojinha.ui.theme.MOBILE_BREAKPOINT
import de.joelneumann.lojinha.ui.theme.PrimaryNavy
import de.joelneumann.lojinha.ui.theme.SurfaceWhite

@Composable
fun CompactCsvExportButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = I18n.current
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, DividerBorder),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = SurfaceWhite,
            contentColor = PrimaryNavy
        ),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
        modifier = modifier.pointerHoverIcon(PointerIcon.Hand)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = strings.exportCsv,
                tint = PrimaryNavy,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = strings.exportCsv,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = PrimaryNavy,
                maxLines = 1
            )
        }
    }
}

@Composable
fun AdminTopBar(
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    countText: String,
    onSearchSubmitted: () -> Unit,
    actionButtonText: String? = null,
    onActionButtonClick: (() -> Unit)? = null,
    onExportCsv: (() -> Unit)? = null,
    sortContent: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val isMobile = maxWidth < MOBILE_BREAKPOINT

        if (isMobile) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SearchInputField(
                    query = searchQuery,
                    onQueryChange = onQueryChange,
                    placeholder = placeholder,
                    onSearchSubmitted = onSearchSubmitted,
                    focusRequester = focusRequester,
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth().height(38.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.fillMaxHeight(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AdminCountPill(
                            text = countText,
                            modifier = Modifier.fillMaxHeight()
                        )
                        if (sortContent != null) {
                            Box(modifier = Modifier.fillMaxHeight()) {
                                sortContent()
                            }
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        if (onExportCsv != null) {
                            CompactCsvExportButton(
                                onClick = onExportCsv,
                                modifier = Modifier.fillMaxHeight()
                            )
                        }
                        if (onExportCsv != null) {
                    CompactCsvExportButton(
                        onClick = onExportCsv,
                        modifier = Modifier.fillMaxHeight()
                    )
                }

                if (actionButtonText != null && onActionButtonClick != null) {
                            Button(
                                onClick = onActionButtonClick,
                                colors = ButtonDefaults.buttonColors(containerColor = AccentNavy),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.fillMaxHeight()
                            ) {
                                Text(actionButtonText, color = SurfaceWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().height(56.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    SearchInputField(
                        query = searchQuery,
                        onQueryChange = onQueryChange,
                        placeholder = placeholder,
                        onSearchSubmitted = onSearchSubmitted,
                        focusRequester = focusRequester,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                AdminCountPill(
                    text = countText,
                    modifier = Modifier.fillMaxHeight()
                )

                if (sortContent != null) {
                    Box(modifier = Modifier.fillMaxHeight()) {
                        sortContent()
                    }
                }

                if (onExportCsv != null) {
                    CompactCsvExportButton(
                        onClick = onExportCsv,
                        modifier = Modifier.fillMaxHeight()
                    )
                }

                if (actionButtonText != null && onActionButtonClick != null) {
                    Button(
                        onClick = onActionButtonClick,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentNavy),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Text(actionButtonText, color = SurfaceWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}
