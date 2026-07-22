package com.callbackdev.saldo.feature.accounts

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.designsystem.component.ColorSwatchPicker
import com.callbackdev.saldo.core.designsystem.component.IconSwatchPicker
import com.callbackdev.saldo.core.designsystem.visuals.AccountVisuals

/** Circular swatch grid for the curated account color palette. */
@Composable
internal fun ColorPicker(
    selected: Int,
    onColorSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    ColorSwatchPicker(
        colors = AccountVisuals.colors,
        selected = selected,
        onColorSelected = onColorSelected,
        resolveColor = AccountVisuals::color,
        optionLabelRes = R.string.account_editor_color_option,
        modifier = modifier,
    )
}

/** Circular grid of the curated account icons; selection uses the account color. */
@Composable
internal fun IconPicker(
    selectedIcon: String,
    selectedColor: Int,
    onIconSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    IconSwatchPicker(
        iconKeys = AccountVisuals.iconKeys,
        selectedIcon = selectedIcon,
        selectedColor = AccountVisuals.color(selectedColor),
        onIconSelected = onIconSelected,
        resolveIcon = AccountVisuals::icon,
        optionLabelRes = R.string.account_editor_icon_option,
        modifier = modifier,
    )
}
