package com.callbackdev.saldo.feature.applock

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.designsystem.theme.tabularNumbers

/**
 * The PIN pad of the app lock (ADR 39): the same flat, quiet key language as
 * the amount keypad, but a separate composable on purpose. A security surface
 * must not depend on the amount editor's `KeypadKey` model (decimal separator
 * and sign toggle mean nothing here), and its bottom-left slot belongs to the
 * biometric shortcut instead of the decimal separator. No long-press clear
 * either: six digits are cheap to backspace, and gestures on a secret field
 * are noise.
 *
 * [onBiometric] fills the bottom-left slot with the fingerprint key; null
 * leaves it empty. [enabled] greys the pad out during the failed-attempt
 * cooldown. [compact] shrinks the keys for the widget sheet.
 */
@Composable
fun PinKeypad(
    onDigit: (Int) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier,
    onBiometric: (() -> Unit)? = null,
    enabled: Boolean = true,
    compact: Boolean = false,
) {
    val keyHeight = if (compact) CompactKeyHeight else KeyHeight
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(KeySpacing),
    ) {
        DigitRows.forEach { rowDigits ->
            PinKeypadRow {
                rowDigits.forEach { digit ->
                    DigitKey(
                        digit = digit,
                        height = keyHeight,
                        enabled = enabled,
                        onClick = { onDigit(digit) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        PinKeypadRow {
            if (onBiometric != null) {
                IconKey(
                    icon = Icons.Outlined.Fingerprint,
                    contentDescription = stringResource(R.string.pin_keypad_biometric),
                    height = keyHeight,
                    // The biometric path stays open during the PIN cooldown:
                    // the throttle exists to slow down guessing, and there is
                    // nothing to guess on a fingerprint.
                    enabled = true,
                    onClick = onBiometric,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            DigitKey(
                digit = 0,
                height = keyHeight,
                enabled = enabled,
                onClick = { onDigit(0) },
                modifier = Modifier.weight(1f),
            )
            IconKey(
                icon = Icons.AutoMirrored.Outlined.Backspace,
                contentDescription = stringResource(R.string.keypad_backspace),
                height = keyHeight,
                enabled = enabled,
                onClick = onBackspace,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun PinKeypadRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(KeySpacing),
        content = content,
    )
}

@Composable
private fun DigitKey(
    digit: Int,
    height: Dp,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PinKeyBox(height = height, enabled = enabled, onClick = onClick, modifier = modifier) {
        Text(
            text = digit.toString(),
            style = MaterialTheme.typography.headlineSmall.tabularNumbers(),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun IconKey(
    icon: ImageVector,
    contentDescription: String,
    height: Dp,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PinKeyBox(height = height, enabled = enabled, onClick = onClick, modifier = modifier) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * A key: no filled container, just a large touch target with a ripple, same
 * language as the amount keypad's keys. The height is a minimum, never fixed,
 * so nothing clips at large font scales.
 */
@Composable
private fun PinKeyBox(
    height: Dp,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .heightIn(min = height)
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(
                enabled = enabled,
                role = Role.Button,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    onClick()
                },
            )
            .alpha(if (enabled) 1f else DisabledKeyAlpha),
    ) {
        content()
    }
}

private val DigitRows = listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9))

// Taller than the amount keypad's keys: the lock screen has the whole height
// to itself, and a bigger target is one less thing between the user and the
// app.
private val KeyHeight = 56.dp
private val CompactKeyHeight = 46.dp
private val KeySpacing = 4.dp

/** Material disabled-content opacity. */
private const val DisabledKeyAlpha = 0.38f
