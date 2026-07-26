package com.callbackdev.saldo.core.designsystem.component

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.common.money.KeypadKey
import com.callbackdev.saldo.core.designsystem.theme.tabularNumbers

/**
 * The app's in-app numeric keypad for amounts (ADR 31): a flat 3-column grid
 * (1-9, decimal separator, 0, backspace) whose height belongs to the app, not
 * to whichever keyboard the user has installed. Keys are drawn with theme
 * tokens only, so the panel reads correctly in light, dark, brand palette and
 * dynamic color.
 *
 * Backspace wipes the amount on long-press. [decimalSeparator] comes from the
 * current locale and its key is dropped for currencies without decimals
 * ([showDecimalSeparator]). [onHide] adds the handle that closes the panel; it
 * is null where the keypad is always on screen (dialogs, sheets), and [compact]
 * shrinks the keys for those same tighter surfaces.
 */
@Composable
fun AmountKeypad(
    onKey: (KeypadKey) -> Unit,
    decimalSeparator: Char,
    modifier: Modifier = Modifier,
    showDecimalSeparator: Boolean = true,
    onHide: (() -> Unit)? = null,
    compact: Boolean = false,
) {
    val keyHeight = if (compact) CompactKeyHeight else KeyHeight
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(KeySpacing),
    ) {
        if (onHide != null) {
            HideHandle(onClick = onHide)
        }
        DigitRows.forEach { rowDigits ->
            KeypadRow {
                rowDigits.forEach { digit ->
                    TextKey(
                        text = digit.toString(),
                        height = keyHeight,
                        onClick = { onKey(KeypadKey.Digit(digit)) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        KeypadRow {
            if (showDecimalSeparator) {
                TextKey(
                    text = decimalSeparator.toString(),
                    height = keyHeight,
                    label = stringResource(R.string.keypad_decimal_separator),
                    onClick = { onKey(KeypadKey.DecimalSeparator) },
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            TextKey(
                text = "0",
                height = keyHeight,
                onClick = { onKey(KeypadKey.Digit(0)) },
                modifier = Modifier.weight(1f),
            )
            BackspaceKey(
                height = keyHeight,
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
        horizontalArrangement = Arrangement.spacedBy(KeySpacing),
        content = content,
    )
}

/** Full-width area that closes the panel, marked by a chevron. */
@Composable
private fun HideHandle(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = HandleHeight)
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(role = Role.Button, onClick = onClick),
    ) {
        Icon(
            imageVector = Icons.Outlined.KeyboardArrowDown,
            contentDescription = stringResource(R.string.keypad_hide),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
    }
}

/**
 * A digit or separator key. [label] overrides what TalkBack reads: a digit
 * announces itself, a bare separator glyph would not.
 */
@Composable
private fun TextKey(
    text: String,
    height: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    KeyBox(height = height, onClick = onClick, modifier = modifier) {
        Text(
            text = text,
            style = MaterialTheme.typography.headlineSmall.tabularNumbers(),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = if (label != null) {
                Modifier.semantics { contentDescription = label }
            } else {
                Modifier
            },
        )
    }
}

@Composable
private fun BackspaceKey(
    height: Dp,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KeyBox(
        height = height,
        onClick = onClick,
        onLongClick = onLongClick,
        onLongClickLabel = stringResource(R.string.keypad_clear),
        modifier = modifier,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.Backspace,
            contentDescription = stringResource(R.string.keypad_backspace),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * A key: no filled container, just a large touch target with a ripple, so the
 * panel stays quiet next to the amount it feeds. The height is a minimum, never
 * fixed, so nothing clips at large font scales.
 */
@Composable
private fun KeyBox(
    height: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    content: @Composable () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .heightIn(min = height)
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(
                role = Role.Button,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    onClick()
                },
                onLongClickLabel = onLongClickLabel,
                onLongClick = onLongClick?.let { action ->
                    {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        action()
                    }
                },
            )
            .padding(vertical = 4.dp),
    ) {
        content()
    }
}

private val DigitRows = listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9))

private val KeyHeight = 52.dp
private val CompactKeyHeight = 44.dp
private val KeySpacing = 4.dp
private val HandleHeight = 36.dp
