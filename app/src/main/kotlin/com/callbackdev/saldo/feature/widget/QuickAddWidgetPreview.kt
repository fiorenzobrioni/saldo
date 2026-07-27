package com.callbackdev.saldo.feature.widget

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.designsystem.theme.AvatarShape
import com.callbackdev.saldo.core.designsystem.visuals.CategoryVisuals
import com.callbackdev.saldo.core.domain.model.Category

/**
 * A live preview of the widget above its own settings, so the light/dark choice
 * and the opacity are judged by looking rather than by placing the widget and
 * going back.
 *
 * It shows the widget's own palette, not the screen's: on a light phone with a
 * dark widget the preview is dark, which is the whole point of the control it
 * sits under. The card behind it stands in for the wallpaper, which is what a
 * translucent background lets through.
 */
@Composable
fun QuickAddWidgetPreview(
    theme: QuickAddWidgetTheme,
    categories: List<Category>,
    showAppShortcut: Boolean,
    bar: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.widget_config_preview_a11y)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(if (bar) PreviewBarHeight else PreviewHeight)
            .clip(RoundedCornerShape(PreviewCorner))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .semantics { contentDescription = description },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(PreviewInset)
                // The same rounding the launcher gives the real widget.
                .clip(RoundedCornerShape(dimensionResource(android.R.dimen.system_app_widget_background_radius)))
                .background(theme.previewBackground)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (bar) {
                PreviewBarRow(theme, showAppShortcut)
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PreviewPill(stringResource(R.string.widget_quick_add_expense), theme, selected = true)
                    PreviewPill(stringResource(R.string.widget_quick_add_income), theme, selected = false)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    categories.take(PreviewTiles).forEach { category ->
                        PreviewTile(theme, CategoryVisuals.color(category.color), CategoryVisuals.icon(category.icon))
                    }
                    PreviewTile(theme, theme.previewScheme.primary, Icons.Outlined.MoreHoriz)
                }
            }
        }
    }
}

/** The bar's shape: the two accent buttons and, when asked, the app square. */
@Composable
private fun PreviewBarRow(theme: QuickAddWidgetTheme, showAppShortcut: Boolean) {
    val money = if (theme.previewDark) theme.darkMoney else theme.lightMoney
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PreviewActionButton(
            label = stringResource(R.string.widget_quick_add_expense),
            accent = theme.previewScheme.error,
            washAlpha = theme.washAlpha,
            modifier = Modifier.weight(1f),
        )
        PreviewActionButton(
            label = stringResource(R.string.widget_quick_add_income),
            accent = money.income,
            washAlpha = theme.washAlpha,
            modifier = Modifier.weight(1f),
        )
        if (showAppShortcut) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(theme.previewScheme.onSurfaceVariant.copy(alpha = theme.washAlpha)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(AppShortcutIcon),
                    contentDescription = null,
                    modifier = Modifier.size(34.dp),
                )
            }
        }
    }
}

@Composable
private fun PreviewActionButton(
    label: String,
    accent: Color,
    washAlpha: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(14.dp))
            .background(accent.copy(alpha = washAlpha)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = accent,
            maxLines = 1,
        )
    }
}

@Composable
private fun PreviewPill(label: String, theme: QuickAddWidgetTheme, selected: Boolean) {
    Box(
        modifier = Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) theme.previewScheme.primary else theme.previewScheme.surfaceVariant)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) theme.previewScheme.onPrimary else theme.previewScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun PreviewTile(theme: QuickAddWidgetTheme, color: Color, icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(AvatarShape)
            // The same wash the widget wears, densifying as the opacity drops.
            .background(color.copy(alpha = theme.washAlpha)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(25.dp),
        )
    }
}

private const val PreviewTiles = 3
private val PreviewHeight = 140.dp

/** The bar preview: one launcher row, not a shrunken grid. */
private val PreviewBarHeight = 96.dp
private val PreviewCorner = 16.dp
private val PreviewInset = 12.dp
