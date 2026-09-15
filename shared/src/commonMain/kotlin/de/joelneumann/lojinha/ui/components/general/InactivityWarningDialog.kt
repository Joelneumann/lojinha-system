package de.joelneumann.lojinha.ui.components.general

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.joelneumann.lojinha.ui.i18n.I18n
import de.joelneumann.lojinha.ui.theme.*

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import de.joelneumann.lojinha.ui.utils.safeRequestFocus

@Composable
fun InactivityWarningDialog(
    secondsRemaining: Int,
    onStayLoggedIn: () -> Unit
) {
    val strings = I18n.current
    val mins = secondsRemaining / 60
    val secs = secondsRemaining % 60
    val formattedTime = "${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}"
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.safeRequestFocus()
    }

    Dialog(
        onDismissRequest = onStayLoggedIn,
        properties = DialogProperties(
            dismissOnClickOutside = true,
            dismissOnBackPress = true
        )
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = SurfaceWhite,
            shadowElevation = 10.dp,
            modifier = Modifier
                .width(420.dp)
                .wrapContentHeight()
                .focusRequester(focusRequester)
                .focusable()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    onStayLoggedIn()
                }
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        onStayLoggedIn()
                        true
                    } else false
                }
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = strings.inactivityWarningTitle,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorWarningAmber
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = strings.inactivityWarningMsg,
                    fontSize = 14.sp,
                    color = TextSecondarySubtle
                )

                Spacer(modifier = Modifier.height(20.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceContainerHighLight, RoundedCornerShape(12.dp))
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = formattedTime,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorWarningAmber
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onStayLoggedIn,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ColorSuccessEmerald)
                ) {
                    Text(text = strings.stayLoggedIn, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}
