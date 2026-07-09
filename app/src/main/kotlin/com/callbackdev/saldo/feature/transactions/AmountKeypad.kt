package com.callbackdev.saldo.feature.transactions

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Exposure
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.callbackdev.saldo.R

/**
 * In-app numeric keypad for amounts: a flat 3-column grid (1-9, decimal
 * separator, 0, backspace). Always available (no IME latency), locale-aware
 * separator, backspace with long-press to clear, and a sign toggle above the
 * grid when editing an adjustment. The save action lives below the keypad, in
 * the editor's bottom bar, not here.
 */
@Composable
fun AmountKeypad(
    onKey: (KeypadKey) -> Unit,
    decimalSeparator: Char,
    showSignToggle: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (showSignToggle) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                SignToggleKey(onClick = { onKey(KeypadKey.ToggleSign) })
            }
        }
        listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9)).forEach { rowDigits ->
            KeypadRow {
                rowDigits.forEach { digit ->
                    TextKey(
                        text = digit.toString(),
                        onClick = { onKey(KeypadKey.Digit(digit)) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        KeypadRow {
            TextKey(
                text = decimalSeparator.toString(),
                onClick = { onKey(KeypadKey.DecimalSeparator) },
                modifier = Modifier.weight(1f),
            )
            TextKey(
                text = "0",
                onClick = { onKey(KeypadKey.Digit(0)) },
                modifier = Modifier.weight(1f),
            )
            BackspaceKey(
                onClick = { onKey(KeypadKey.Backspace) },
                onLongClick = { onKey(KeypadKey.Clear) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun KeypadRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}

@Composable
private fun TextKey(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KeyBox(onClick = onClick, modifier = modifier) {
        Text(
            text = text,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun BackspaceKey(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KeyBox(onClick = onClick, onLongClick = onLongClick, modifier = modifier) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.Backspace,
            contentDescription = stringResource(R.string.keypad_backspace),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SignToggleKey(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier,
    ) {
        Icon(
            imageVector = Icons.Outlined.Exposure,
            contentDescription = stringResource(R.string.action_toggle_sign),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .size(20.dp),
        )
    }
}

@Composable
private fun KeyBox(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(KeyHeight)
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        content()
    }
}

private val KeyHeight = 56.dp
