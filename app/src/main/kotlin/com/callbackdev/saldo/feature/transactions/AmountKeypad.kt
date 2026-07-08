package com.callbackdev.saldo.feature.transactions

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Exposure
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.callbackdev.saldo.R

/**
 * In-app numeric keypad for amounts: always available (no IME latency), with
 * locale-aware decimal separator, backspace (long press clears), a `00` key
 * (sign toggle instead when editing an adjustment) and a prominent save key.
 */
@Composable
fun AmountKeypad(
    onKey: (KeypadKey) -> Unit,
    onSave: () -> Unit,
    decimalSeparator: Char,
    showSignToggle: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(12.dp)
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DigitPad(
                onKey = onKey,
                decimalSeparator = decimalSeparator,
                showSignToggle = showSignToggle,
                modifier = Modifier.weight(3f),
            )
            ActionColumn(
                onKey = onKey,
                onSave = onSave,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun DigitPad(
    onKey: (KeypadKey) -> Unit,
    decimalSeparator: Char,
    showSignToggle: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9)).forEach { rowDigits ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowDigits.forEach { digit ->
                    TextKey(
                        text = digit.toString(),
                        onClick = { onKey(KeypadKey.Digit(digit)) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            if (showSignToggle) {
                IconKey(
                    icon = Icons.Outlined.Exposure,
                    contentDescription = stringResource(R.string.action_toggle_sign),
                    onClick = { onKey(KeypadKey.ToggleSign) },
                    modifier = Modifier.weight(1f),
                )
            } else {
                TextKey(
                    text = "00",
                    onClick = { onKey(KeypadKey.DoubleZero) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ActionColumn(
    onKey: (KeypadKey) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Key(
            color = MaterialTheme.colorScheme.secondaryContainer,
            onClick = { onKey(KeypadKey.Backspace) },
            onLongClick = { onKey(KeypadKey.Clear) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.Backspace,
                contentDescription = stringResource(R.string.keypad_backspace),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Surface(
            onClick = onSave,
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = stringResource(R.string.action_save),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

@Composable
private fun TextKey(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Key(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        onClick = onClick,
        modifier = modifier,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun IconKey(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Key(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        onClick = onClick,
        modifier = modifier,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun Key(
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = color,
        modifier = modifier.height(KeyHeight),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        ) {
            content()
        }
    }
}

private val KeyHeight = 56.dp
