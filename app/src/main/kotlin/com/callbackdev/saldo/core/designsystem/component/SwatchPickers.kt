package com.callbackdev.saldo.core.designsystem.component

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.callbackdev.saldo.core.designsystem.theme.AvatarShape
import com.callbackdev.saldo.core.designsystem.visuals.contentColorOn

/**
 * Squircle swatch grid for a curated color palette: the single implementation
 * behind the category, account and subscription color pickers. Swatches use
 * [AvatarShape] because each one previews the avatar background it will
 * become. [colors] are the raw palette values as stored in the domain;
 * [resolveColor] maps one to its display color, and [optionLabelRes] (with a
 * positional placeholder) labels each swatch for TalkBack.
 */
@Composable
fun ColorSwatchPicker(
    colors: List<Int>,
    selected: Int,
    onColorSelected: (Int) -> Unit,
    resolveColor: (Int) -> Color,
    @StringRes optionLabelRes: Int,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        colors.forEachIndexed { index, color ->
            val isSelected = color == selected
            val label = stringResource(optionLabelRes, index + 1)
            Box(
                modifier = Modifier
                    .size(COLOR_SWATCH_SIZE)
                    .clip(AvatarShape)
                    .background(resolveColor(color))
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
                        tint = contentColorOn(resolveColor(color)),
                    )
                }
            }
        }
    }
}

/**
 * Squircle grid of a curated icon set; the selected cell fills with
 * [selectedColor]. The counterpart of [ColorSwatchPicker] for the icon half
 * of the same editors; cells use [AvatarShape] like every entity avatar in
 * the app. [resolveIcon] maps a stored icon key to its vector.
 */
@Composable
fun IconSwatchPicker(
    iconKeys: List<String>,
    selectedIcon: String,
    selectedColor: Color,
    onIconSelected: (String) -> Unit,
    resolveIcon: (String) -> ImageVector,
    @StringRes optionLabelRes: Int,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        iconKeys.forEachIndexed { index, key ->
            val isSelected = key == selectedIcon
            Box(
                modifier = Modifier
                    .size(ICON_SWATCH_SIZE)
                    .clip(AvatarShape)
                    .background(
                        if (isSelected) {
                            selectedColor
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
                    imageVector = resolveIcon(key),
                    contentDescription = stringResource(optionLabelRes, index + 1),
                    tint = if (isSelected) {
                        contentColorOn(selectedColor)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(ICON_SWATCH_GLYPH_SIZE),
                )
            }
        }
    }
}

private val COLOR_SWATCH_SIZE = 40.dp
private val ICON_SWATCH_SIZE = 44.dp
private val ICON_SWATCH_GLYPH_SIZE = 22.dp
