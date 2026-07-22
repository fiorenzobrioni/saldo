package com.callbackdev.saldo.feature.categories

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.designsystem.component.ColorSwatchPicker
import com.callbackdev.saldo.core.designsystem.component.IconSwatchPicker
import com.callbackdev.saldo.core.designsystem.visuals.CategoryVisuals

/** Circular swatch grid for the shared category color palette. */
@Composable
internal fun CategoryColorPicker(
    selected: Int,
    onColorSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    ColorSwatchPicker(
        colors = CategoryVisuals.colors,
        selected = selected,
        onColorSelected = onColorSelected,
        resolveColor = CategoryVisuals::color,
        optionLabelRes = R.string.category_editor_color_option,
        modifier = modifier,
    )
}

/** Circular grid of the curated category icons; selection uses the category color. */
@Composable
internal fun CategoryIconPicker(
    selectedIcon: String,
    selectedColor: Int,
    onIconSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    IconSwatchPicker(
        iconKeys = CategoryVisuals.iconKeys,
        selectedIcon = selectedIcon,
        selectedColor = CategoryVisuals.color(selectedColor),
        onIconSelected = onIconSelected,
        resolveIcon = CategoryVisuals::icon,
        optionLabelRes = R.string.category_editor_icon_option,
        modifier = modifier,
    )
}
