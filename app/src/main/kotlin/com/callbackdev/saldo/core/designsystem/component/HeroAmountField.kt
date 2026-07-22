package com.callbackdev.saldo.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Exposure
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.designsystem.theme.tabularNumbers

/**
 * The editors' hero amount input: a borderless, centered [BasicTextField] with
 * the currency symbol beside the digits, in a large tabular-figures style so
 * the amount is the focal point of its screen. Input goes through the system
 * decimal keyboard (ADR 16) and the raw text is expected to be sanitized by
 * the caller's ViewModel, so both `.` and `,` are accepted while typing. When
 * [isError] the amount turns error-colored and [errorText] appears below;
 * [showSignToggle] adds a sign-toggle next to the digits (balance
 * adjustments). [compact] renders the smaller variant used for secondary
 * amounts (e.g. the second leg of a cross-currency transfer, dialogs).
 */
@Suppress("LongParameterList")
@Composable
fun HeroAmountField(
    input: String,
    currencySymbol: String?,
    isError: Boolean,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    showSignToggle: Boolean = false,
    label: String? = null,
    errorText: String? = null,
    compact: Boolean = false,
    focusRequester: FocusRequester? = null,
) {
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
        // A borderless field has no visible label in the standard case, so the
        // role is stated for TalkBack instead.
        val fieldDescription = label ?: stringResource(R.string.editor_amount)
        BasicTextField(
            value = input,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = amountStyle.copy(color = amountColor),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = (focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
                .fillMaxWidth()
                .semantics { contentDescription = fieldDescription },
            decorationBox = { innerTextField ->
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = if (compact) 4.dp else 8.dp),
                ) {
                    if (currencySymbol != null) {
                        Text(
                            text = currencySymbol,
                            style = symbolStyle,
                            color = symbolColor,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Box(
                        contentAlignment = Alignment.Center,
                        // Wraps the digits but never pushes the symbol off-screen:
                        // past the cap the field scrolls horizontally instead.
                        modifier = Modifier.weight(1f, fill = false),
                    ) {
                        if (input.isEmpty()) {
                            Text(
                                text = stringResource(R.string.editor_amount_placeholder),
                                style = amountStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                    .copy(alpha = PLACEHOLDER_ALPHA),
                            )
                        }
                        innerTextField()
                    }
                    if (showSignToggle) {
                        Spacer(Modifier.width(4.dp))
                        IconButton(onClick = { onValueChange(toggleSign(input)) }) {
                            Icon(
                                imageVector = Icons.Outlined.Exposure,
                                contentDescription = stringResource(R.string.action_toggle_sign),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            },
        )
        if (isError && errorText != null) {
            Text(
                text = errorText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

private fun toggleSign(input: String): String =
    if (input.startsWith("-")) input.removePrefix("-") else "-$input"

private const val PLACEHOLDER_ALPHA = 0.4f
