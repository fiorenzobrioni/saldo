package com.callbackdev.saldo.feature.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
 * is made by looking at it rather than by placing the widget and going back.
 *
 * It shows the widget's own palette, not the screen's: on a light phone with a
 * dark widget the preview is dark, which is the whole point of the control it
 * sits under.
 */
@Composable
fun QuickAddWidgetPreview(
    theme: QuickAddWidgetTheme,
    categories: List<Category>,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.widget_config_preview_a11y)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(PreviewHeight)
            .clip(RoundedCornerShape(PreviewCorner))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .semantics { contentDescription = description },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(PreviewInset)
                .clip(RoundedCornerShape(WidgetCorner))
                .background(theme.background)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
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
                    PreviewTile(CategoryVisuals.color(category.color), CategoryVisuals.icon(category.icon))
                }
                PreviewTile(theme.scheme.primary, Icons.Outlined.MoreHoriz)
            }
        }
    }
}

@Composable
private fun PreviewPill(label: String, theme: QuickAddWidgetTheme, selected: Boolean) {
    Box(
        modifier = Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) theme.scheme.primary else theme.scheme.surfaceVariant)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) theme.scheme.onPrimary else theme.scheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun PreviewTile(color: Color, icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(AvatarShape)
            .background(color.copy(alpha = TileTintAlpha)),
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
private const val TileTintAlpha = 0.16f
private val PreviewHeight = 140.dp
private val PreviewCorner = 16.dp
private val PreviewInset = 12.dp
private val WidgetCorner = 24.dp
