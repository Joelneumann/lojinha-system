package de.joelneumann.lojinha.ui.components.general

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.joelneumann.lojinha.domain.scale.ScaleConnectionStatus
import de.joelneumann.lojinha.domain.scale.ScaleReadingStatus
import de.joelneumann.lojinha.domain.scale.ScaleService
import de.joelneumann.lojinha.domain.scale.ScaleUiState
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.i18n.LanguageManager
import de.joelneumann.lojinha.ui.theme.*
import de.joelneumann.lojinha.ui.utils.Formatting

@Composable
fun ScalePollingEffect(
    scaleService: ScaleService?,
    owner: String,
    enabled: Boolean
) {
    DisposableEffect(scaleService, owner, enabled) {
        if (enabled && scaleService != null) {
            scaleService.acquirePollingToken(owner)
        }
        onDispose {
            if (enabled && scaleService != null) {
                scaleService.releasePollingToken(owner)
            }
        }
    }
}

@Composable
fun ScaleStatusBar(
    scaleState: ScaleUiState?,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    actionEnabled: Boolean = false,
    onActionClick: (() -> Unit)? = null,
    hintText: String? = null,
    inputActiveLabel: String? = null,
    isInputActive: Boolean = false,
    compact: Boolean = false
) {
    if (scaleState == null || !scaleState.config.enabled || scaleState.connectionStatus == ScaleConnectionStatus.DISABLED) return
    val strings = I18n.current
    val reading = scaleState.reading

    val (dotColor, statusText) = when (scaleState.connectionStatus) {
        ScaleConnectionStatus.DISABLED -> TextSecondaryMuted to strings.scaleStatusDisabled
        ScaleConnectionStatus.DISCONNECTED -> TextSecondaryMuted to strings.scaleStatusDisconnected
        ScaleConnectionStatus.ERROR -> ColorDangerCrimson to
            (scaleState.lastError?.takeIf { it.isNotBlank() } ?: strings.scaleStatusDisconnected)
        ScaleConnectionStatus.CONNECTING -> ColorWarningAmber to strings.scaleStatusConnecting
        ScaleConnectionStatus.CONNECTED -> {
            when (reading?.status) {
                null -> ColorWarningAmber to strings.scaleStatusConnecting
                ScaleReadingStatus.NEGATIVE -> ColorDangerCrimson to strings.scaleNegativeLabel
                ScaleReadingStatus.OVERLOAD -> ColorDangerCrimson to strings.scaleOverloadLabel
                ScaleReadingStatus.UNSTABLE -> ColorWarningAmber to strings.scaleUnstableLabel
                ScaleReadingStatus.ZERO -> {
                    val sep = Formatting.decimalSeparator(LanguageManager.currentLanguage)
                    TextSecondarySubtle to "0${sep}000 kg"
                }
                ScaleReadingStatus.OK -> {
                    val formatted = "${Formatting.formatWeightForInput(reading.grams)} kg"
                    if (reading.isStable) {
                        ColorSuccessEmerald to "$formatted · ${strings.scaleStableLabel}"
                    } else {
                        ColorWarningAmber to "$formatted · ${strings.scaleUnstableLabel}"
                    }
                }
            }
        }
    }

    val isConnected = scaleState.connectionStatus == ScaleConnectionStatus.CONNECTED
    val borderColor = when {
        isConnected && isInputActive -> ColorSuccessEmerald.copy(alpha = 0.55f)
        else -> DividerBorder
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = SurfaceContainerHighLight,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .then(if (compact) Modifier else Modifier.fillMaxWidth())
                .padding(
                    horizontal = if (compact) 8.dp else 10.dp,
                    vertical = if (compact) 4.dp else 6.dp
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Scale,
                contentDescription = null,
                tint = PrimaryNavy,
                modifier = Modifier.size(16.dp)
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(dotColor, CircleShape)
            )
            Text(
                text = statusText,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = PrimaryNavy,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (hintText != null) {
                Text(
                    text = hintText,
                    fontSize = 11.sp,
                    color = TextSecondaryMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!compact) {
                Spacer(modifier = Modifier.weight(1f))
            }
            if (inputActiveLabel != null) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isInputActive) ColorSuccessEmerald.copy(alpha = 0.12f) else SurfaceContainerLight
                ) {
                    Text(
                        text = inputActiveLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isInputActive) ColorSuccessEmerald else TextSecondaryMuted,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            if (actionLabel != null && onActionClick != null) {
                Button(
                    onClick = onActionClick,
                    enabled = actionEnabled,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentNavy,
                        contentColor = SurfaceWhite,
                        disabledContainerColor = DividerBorder,
                        disabledContentColor = TextSecondaryMuted
                    ),
                    contentPadding = PaddingValues(
                        horizontal = if (compact) 10.dp else 12.dp,
                        vertical = 2.dp
                    ),
                    modifier = Modifier.height(if (compact) 26.dp else 30.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = actionLabel,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
