package com.callbackdev.saldo.core.designsystem.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Exposure
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.common.money.AmountInputEditor
import com.callbackdev.saldo.core.common.money.KeypadKey
import com.callbackdev.saldo.core.common.money.MoneyInput
import com.callbackdev.saldo.core.designsystem.theme.tabularNumbers

/**
 * An amount in an ordinary labelled field, for the forms where the figure is
 * one input among many and not the screen's hero (an account's initial
 * balance, a card limit, a filter bound). It looks like the text fields
 * around it but is read-only: tapping it points the keypad at this [target]
 * via [onActivate] instead of raising the system IME (ADR 31).
 */
@Suppress("LongParameterList")
@Composable
fun AmountTextField(
    target: AmountTarget,
    label: String,
    onActivate: () -> Unit,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    supportingText: String? = null,
    showSignToggle: Boolean = false,
    isError: Boolean = false,
) {
    val symbols = rememberAmountSymbols()
    // A read-only field swallows taps for its own cursor, so the press is read
    // from the interaction source: the same wiring an exposed dropdown uses.
    val interactionSource = remember { MutableInteractionSource() }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            if (interaction is PressInteraction.Release) onActivate()
        }
    }
    OutlinedTextField(
        value = MoneyInput.grouped(target.value, symbols.grouping),
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        placeholder = { Text(stringResource(R.string.editor_amount_placeholder)) },
        suffix = suffix?.let { text -> { Text(text) } },
        singleLine = true,
        isError = isError,
        textStyle = LocalTextStyle.current.tabularNumbers(),
        supportingText = supportingText?.let { text -> { Text(text) } },
        trailingIcon = if (showSignToggle) {
            {
                IconButton(
                    onClick = {
                        target.onValueChange(
                            AmountInputEditor.apply(
                                current = target.value,
                                key = KeypadKey.ToggleSign,
                                fractionDigits = target.fractionDigits,
                                allowNegative = target.allowNegative,
                                decimalSeparator = symbols.decimal,
                            ),
                        )
                    },
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Exposure,
                        contentDescription = stringResource(R.string.action_toggle_sign),
                    )
                }
            }
        } else {
            null
        },
        interactionSource = interactionSource,
        modifier = modifier.fillMaxWidth(),
    )
}
