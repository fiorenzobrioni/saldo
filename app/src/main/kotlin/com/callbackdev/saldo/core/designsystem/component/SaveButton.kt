package com.callbackdev.saldo.core.designsystem.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.callbackdev.saldo.R

/**
 * The primary "Save" action shown in the top app bar of the editor screens.
 *
 * A filled (high-emphasis) button with a comfortable touch target: the tonal,
 * default-height variant used before read as too small for the screen's main
 * action.
 */
@Composable
fun SaveButton(
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 24.dp),
        modifier = modifier
            .padding(end = 8.dp)
            .heightIn(min = 44.dp),
    ) {
        Text(stringResource(R.string.action_save))
    }
}
