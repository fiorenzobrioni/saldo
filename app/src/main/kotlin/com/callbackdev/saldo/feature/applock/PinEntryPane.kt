package com.callbackdev.saldo.feature.applock

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.callbackdev.saldo.core.common.applock.APP_LOCK_PIN_LENGTH

/**
 * The one PIN-entry surface of the whole app (ADR 39): title, optional
 * subtitle, the six-dot indicator, a reserved error line (so a rejection
 * never shifts the layout) and the [PinKeypad]. The lock screen, the
 * Security flows (create, confirm, verify) and the widget sheet all compose
 * this same pane, so PIN entry looks and behaves identically everywhere.
 *
 * With [fillHeight] the pane stretches and anchors the keypad to its bottom
 * edge (the full-screen contexts); without it everything stacks compactly
 * (the widget sheet).
 */
@Composable
fun PinEntryPane(
    title: String?,
    subtitle: String?,
    filledDigits: Int,
    error: String?,
    shakeTick: Int,
    onDigit: (Int) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier,
    onBiometric: (() -> Unit)? = null,
    keypadEnabled: Boolean = true,
    compact: Boolean = false,
    fillHeight: Boolean = false,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
        }
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = if (title != null) 4.dp else 0.dp),
            )
        }
        PinIndicator(
            filled = filledDigits,
            total = APP_LOCK_PIN_LENGTH,
            isError = error != null,
            shakeTick = shakeTick,
            modifier = Modifier.padding(top = 24.dp),
        )
        // Reserved line: an appearing error must not push the keypad around.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(top = 12.dp)
                .heightIn(min = ErrorLineMinHeight),
        ) {
            if (error != null) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }
        }
        if (fillHeight) {
            Spacer(Modifier.weight(1f))
        } else {
            Spacer(Modifier.height(16.dp))
        }
        PinKeypad(
            onDigit = onDigit,
            onBackspace = onBackspace,
            onBiometric = onBiometric,
            enabled = keypadEnabled,
            compact = compact,
            // Order matters: the cap must sit outside the fill, so the pad
            // takes the full width up to the cap and is centered beyond it.
            modifier = Modifier
                .widthIn(max = KeypadMaxWidth)
                .fillMaxWidth(),
        )
    }
}

private val ErrorLineMinHeight = 20.dp

/** Keeps the keys reachable on tablets and in landscape instead of edge-to-edge stretching. */
private val KeypadMaxWidth = 360.dp
