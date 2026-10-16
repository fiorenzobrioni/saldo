package com.callbackdev.saldo.feature.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.callbackdev.saldo.R

/**
 * The sizes each widget can really be given: the bar never grows past one row
 * (`maxResizeHeight`), the grid goes from the two buttons it becomes at one row
 * to the tall grid. Every form each widget has appears at least once, and the
 * default placement is in both lists.
 */
internal fun previewSizes(bar: Boolean): List<PreviewSize> = if (bar) {
    listOf(PreviewSize.TWO_BY_ONE, PreviewSize.THREE_BY_ONE, PreviewSize.FOUR_BY_ONE)
} else {
    listOf(
        PreviewSize.TWO_BY_ONE,
        PreviewSize.FOUR_BY_ONE,
        PreviewSize.TWO_BY_TWO,
        PreviewSize.TWO_BY_THREE,
        PreviewSize.THREE_BY_TWO,
        PreviewSize.FOUR_BY_TWO,
        PreviewSize.FOUR_BY_THREE,
    )
}

/** Where a widget lands when it is first placed: its provider's `targetCell*`. */
internal fun defaultPreviewSize(bar: Boolean): PreviewSize =
    if (bar) PreviewSize.FOUR_BY_ONE else PreviewSize.FOUR_BY_THREE

/**
 * The size this widget really has on the home screen, in portrait (the
 * launcher's `minWidth × maxHeight`, the pair a portrait grid grants), or null
 * when the host has not said yet.
 */
internal fun placedWidgetSize(context: Context, appWidgetId: Int): DpSize? {
    val options = runCatching {
        AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId)
    }.getOrNull() ?: return null
    val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
    val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
    return if (width > 0 && height > 0) DpSize(width.dp, height.dp) else null
}

/**
 * One sentence under the preview saying what the widget carries at [size],
 * read off [layoutFor], the function the renderer lays itself out with, so the
 * sentence and the drawing can never describe two different forms.
 */
internal fun formNote(size: DpSize, categories: Int, fontScale: Float): Int {
    val layout = layoutFor(WidgetSize(size.width.value, size.height.value), categories, fontScale)
    return when {
        layout.style == WidgetStyle.ACTIONS && layout.showLabels -> R.string.widget_form_buttons_words
        layout.style == WidgetStyle.ACTIONS -> R.string.widget_form_buttons_arrows
        layout.showHeader -> R.string.widget_form_grid_wide
        else -> R.string.widget_form_grid_narrow
    }
}

/**
 * The widget as it will look, on a ground that stands in for a wallpaper, with
 * the sizes it can be seen at underneath and one line saying what that size
 * carries. It opens on the size the widget really has on the home screen when
 * the launcher has said it, so the first thing the reader sees is their own;
 * the other chips are the forms it turns into when resized, which the home
 * screen only ever shows one at a time.
 */
@Composable
internal fun QuickAddWidgetPreviewSection(
    data: QuickAddWidgetData,
    theme: QuickAddWidgetTheme,
    bar: Boolean,
    placed: DpSize?,
) {
    var size by remember { mutableStateOf(placed ?: defaultPreviewSize(bar).size) }
    val colors = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(WallpaperCorner))
            .background(Brush.linearGradient(listOf(colors.primaryContainer, colors.tertiaryContainer)))
            .padding(horizontal = 16.dp, vertical = 24.dp),
    ) {
        QuickAddWidgetPreview(data = data, theme = theme, size = size)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        placed?.let { here ->
            FilterChip(
                selected = size == here,
                onClick = { size = here },
                label = { Text(stringResource(R.string.widget_config_size_placed)) },
            )
        }
        previewSizes(bar).forEach { option ->
            FilterChip(
                selected = size == option.size && size != placed,
                onClick = { size = option.size },
                label = { Text(option.label) },
            )
        }
    }
    val fontScale = LocalDensity.current.fontScale
    Text(
        text = stringResource(formNote(size, data.categories.size, fontScale)),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp),
    )
    Text(
        text = stringResource(R.string.widget_config_resize_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp),
    )
}

/**
 * The widget itself, not a lookalike: the same [WidgetRenderer] the launcher
 * receives, its `RemoteViews` applied to a view in this process at the size
 * the reader picked and scaled down to the width there is. Taps are swallowed:
 * the real widget's intents are bound on these views, and a preview that
 * opened the quick-entry sheet would throw the reader out of their settings.
 * It is one picture to a screen reader, named as the preview it is.
 */
@Composable
internal fun QuickAddWidgetPreview(
    data: QuickAddWidgetData,
    theme: QuickAddWidgetTheme,
    size: DpSize,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val description = stringResource(R.string.widget_config_preview_a11y)
    val palette = theme.palette
    BoxWithConstraints(modifier = modifier.fillMaxWidth().semantics { contentDescription = description }) {
        val scale = minOf(1f, maxWidth / size.width)
        Box(
            modifier = Modifier.fillMaxWidth().height(size.height * scale),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .requiredSize(size.width, size.height)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
            ) {
                AndroidView(
                    factory = { FrameLayout(it) },
                    update = { host ->
                        val views = WidgetRenderer.render(
                            context = context,
                            appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID,
                            data = data,
                            palette = palette,
                            size = WidgetSize(size.width.value, size.height.value),
                        )
                        host.removeAllViews()
                        host.addView(views.apply(context, host))
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                // Above the widget, so the hit test ends here and no bound
                // intent fires.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                                }
                            }
                        },
                )
            }
        }
    }
}

private val WallpaperCorner = 28.dp

/** Remembers the preview's data for the settings as they stand. */
@Composable
internal fun rememberPreviewData(state: QuickAddWidgetConfigUiState): QuickAddWidgetData =
    remember(state.config, state.categories, state.accounts) {
        val config = state.config
        val pinned = state.selectedAccount
        val categories = if (config.usesCustomCategories) {
            config.pinnedCategoryIds.mapNotNull { id -> state.categories.firstOrNull { it.id == id } }
        } else {
            state.categories
        }
        QuickAddWidgetData(
            type = config.type,
            categories = categories,
            hasAccounts = state.accounts.isNotEmpty(),
            pinnedAccountId = pinned?.id,
            pinnedAccountName = pinned?.name,
            buttons = config.buttons,
            showAppShortcut = config.showAppShortcut,
        )
    }
