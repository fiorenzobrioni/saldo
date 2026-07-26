package com.callbackdev.saldo.core.designsystem.component

import android.content.ClipboardManager
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Exposure
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.common.money.AmountInputEditor
import com.callbackdev.saldo.core.common.money.KeypadKey
import com.callbackdev.saldo.core.common.money.MoneyInput
import com.callbackdev.saldo.core.designsystem.theme.tabularNumbers

/**
 * The editors' hero amount input: borderless and centered, with the currency
 * symbol beside the digits in a large tabular-figures style, so the amount is
 * the focal point of its screen. Digits are typed on the in-app keypad
 * (ADR 31), not on the system IME, so this is a display and not a text field:
 * [onActivate] asks the hosting screen to point [AmountKeypadHost] at this
 * [target], and [isActive] draws the caret while it does.
 *
 * The two affordances a text field gave for free are kept explicitly: a
 * hardware keyboard types into the focused field, and a long-press pastes.
 * When [isError] the amount turns error-colored and [errorText] appears below;
 * [showSignToggle] adds a sign-toggle next to the digits (balance
 * adjustments). [compact] renders the smaller variant used for secondary
 * amounts (e.g. the second leg of a cross-currency transfer, dialogs).
 */
@Suppress("LongParameterList", "LongMethod")
@Composable
fun HeroAmountField(
    target: AmountTarget,
    currencySymbol: String?,
    isError: Boolean,
    isActive: Boolean,
    onActivate: () -> Unit,
    modifier: Modifier = Modifier,
    showSignToggle: Boolean = false,
    label: String? = null,
    errorText: String? = null,
    compact: Boolean = false,
) {
    val symbols = rememberAmountSymbols()
    val amountStyle = if (compact) {
        MaterialTheme.typography.headlineSmall.tabularNumbers()
    } else {
        MaterialTheme.typography.displayMedium.tabularNumbers()
    }
    val symbolStyle = if (compact) {
        MaterialTheme.typography.titleMedium
    } else {
        MaterialTheme.typography.headlineSmall
    }
    val amountColor = if (isError) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val symbolColor = if (isError) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val focusRequester = remember { FocusRequester() }
    var showPasteMenu by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val display = MoneyInput.grouped(target.value, symbols.grouping)
    // A borderless field has no visible label in the standard case, so the
    // role is stated for TalkBack instead.
    val fieldLabel = label ?: stringResource(R.string.editor_amount)
    val fieldDescription = if (display.isEmpty()) fieldLabel else "$fieldLabel, $display"

    LaunchedEffect(display) {
        // The caret lives at the end: keep the tail of a long amount in view.
        scrollState.scrollTo(scrollState.maxValue)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth(),
    ) {
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
        }
        Box {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .semantics {
                        contentDescription = fieldDescription
                        role = Role.Button
                    }
                    .focusRequester(focusRequester)
                    .focusable()
                    .onKeyEvent { event ->
                        val key = hardwareKey(event.type, event.key, event.utf16CodePoint)
                        if (key == null) {
                            false
                        } else {
                            target.applyKey(key, symbols.decimal)
                            true
                        }
                    }
                    .combinedClickable(
                        onClick = {
                            focusRequester.requestFocus()
                            onActivate()
                        },
                        onLongClick = { showPasteMenu = true },
                        onLongClickLabel = stringResource(R.string.action_paste),
                    )
                    .padding(vertical = if (compact) 4.dp else 8.dp),
            ) {
                if (currencySymbol != null) {
                    Text(text = currencySymbol, style = symbolStyle, color = symbolColor)
                    Spacer(Modifier.width(8.dp))
                }
                Box(
                    contentAlignment = Alignment.Center,
                    // Wraps the digits but never pushes the symbol off-screen:
                    // past the cap the amount scrolls horizontally instead.
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .horizontalScroll(scrollState),
                ) {
                    Text(
                        text = display.ifEmpty {
                            stringResource(R.string.editor_amount_placeholder)
                        },
                        style = amountStyle,
                        color = if (display.isEmpty()) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                                .copy(alpha = PLACEHOLDER_ALPHA)
                        } else {
                            amountColor
                        },
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                    )
                }
                Caret(visible = isActive, compact = compact)
                if (showSignToggle) {
                    Spacer(Modifier.width(4.dp))
                    IconButton(
                        onClick = { target.applyKey(KeypadKey.ToggleSign, symbols.decimal) },
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Exposure,
                            contentDescription = stringResource(R.string.action_toggle_sign),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            PasteMenu(
                expanded = showPasteMenu,
                onDismiss = { showPasteMenu = false },
                onPaste = { pasted ->
                    showPasteMenu = false
                    target.onValueChange(
                        MoneyInput.sanitize(pasted, target.fractionDigits, target.allowNegative),
                    )
                },
            )
        }
        if (isError && errorText != null) {
            Text(
                text = errorText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** Runs a keypad key against the target and publishes the result. */
private fun AmountTarget.applyKey(key: KeypadKey, decimalSeparator: Char) {
    onValueChange(
        AmountInputEditor.apply(
            current = value,
            key = key,
            fractionDigits = fractionDigits,
            allowNegative = allowNegative,
            decimalSeparator = decimalSeparator,
        ),
    )
}

/** Maps a physical key press onto a keypad key, or null when it is none of ours. */
private fun hardwareKey(type: KeyEventType, key: Key, codePoint: Int): KeypadKey? {
    if (type != KeyEventType.KeyDown) return null
    val typed = codePoint.toChar()
    return when {
        key == Key.Backspace || key == Key.Delete -> KeypadKey.Backspace
        typed.isDigit() -> KeypadKey.Digit(typed.digitToInt())
        typed == '.' || typed == ',' -> KeypadKey.DecimalSeparator
        typed == '-' -> KeypadKey.ToggleSign
        else -> null
    }
}

/**
 * The typing caret. The blinking variant exists only while the field is the
 * keypad's target, so no animation runs on the amounts nobody is typing into.
 */
@Composable
private fun Caret(visible: Boolean, compact: Boolean) {
    val motionEnabled = rememberMotionEnabled()
    when {
        !visible -> Spacer(Modifier.width(CaretWidth).padding(start = 2.dp))
        !motionEnabled -> CaretBar(alpha = 1f, compact = compact)
        else -> {
            val transition = rememberInfiniteTransition(label = "caret")
            val alpha by transition.animateFloat(
                initialValue = 1f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = CARET_BLINK_MILLIS),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "caretAlpha",
            )
            CaretBar(alpha = alpha, compact = compact)
        }
    }
}

@Composable
private fun CaretBar(alpha: Float, compact: Boolean) {
    Spacer(
        Modifier
            .padding(start = 2.dp)
            .width(CaretWidth)
            .height(if (compact) 24.dp else 40.dp)
            .background(
                color = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                shape = MaterialTheme.shapes.extraSmall,
            ),
    )
}

/** "Paste", the one text-field affordance an amount still needs. */
@Composable
private fun PasteMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onPaste: (String) -> Unit,
) {
    val context = LocalContext.current
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_paste)) },
            leadingIcon = {
                Icon(imageVector = Icons.Outlined.ContentPaste, contentDescription = null)
            },
            onClick = {
                val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip
                val text = clip?.takeIf { it.itemCount > 0 }
                    ?.getItemAt(0)
                    ?.coerceToText(context)
                    ?.toString()
                if (text.isNullOrBlank()) onDismiss() else onPaste(text)
            },
        )
    }
}

private val CaretWidth = 2.dp
private const val PLACEHOLDER_ALPHA = 0.4f
private const val CARET_BLINK_MILLIS = 600
