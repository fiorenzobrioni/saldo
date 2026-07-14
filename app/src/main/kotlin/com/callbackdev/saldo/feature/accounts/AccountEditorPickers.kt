package com.callbackdev.saldo.feature.accounts

import com.callbackdev.saldo.core.designsystem.visuals.AccountVisuals
import com.callbackdev.saldo.core.designsystem.visuals.contentColorOn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.callbackdev.saldo.R

/** Circular swatch grid for the curated account color palette. */
@Composable
internal fun ColorPicker(
    selected: Int,
    onColorSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        AccountVisuals.colors.forEachIndexed { index, color ->
            val isSelected = color == selected
            val label = stringResource(R.string.account_editor_color_option, index + 1)
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(AccountVisuals.color(color))
                    .selectable(
                        selected = isSelected,
                        role = Role.RadioButton,
                        onClick = { onColorSelected(color) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = label,
                        tint = contentColorOn(AccountVisuals.color(color)),
                    )
                }
            }
        }
    }
}

/** Circular grid of the curated account icons; selection uses the account color. */
@Composable
internal fun IconPicker(
    selectedIcon: String,
    selectedColor: Int,
    onIconSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        AccountVisuals.iconKeys.forEachIndexed { index, key ->
            val isSelected = key == selectedIcon
            val label = stringResource(R.string.account_editor_icon_option, index + 1)
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) {
                            AccountVisuals.color(selectedColor)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                    )
                    .selectable(
                        selected = isSelected,
                        role = Role.RadioButton,
                        onClick = { onIconSelected(key) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = AccountVisuals.icon(key),
                    contentDescription = label,
                    tint = if (isSelected) {
                        contentColorOn(AccountVisuals.color(selectedColor))
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}
