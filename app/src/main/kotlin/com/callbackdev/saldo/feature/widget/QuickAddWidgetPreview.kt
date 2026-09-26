package com.callbackdev.saldo.feature.widget

import android.appwidget.AppWidgetManager
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.callbackdev.saldo.R

/**
 * The widget above its own settings, drawn by the same [WidgetRenderer] the
 * launcher receives - the `RemoteViews` applied to a view in this process - so
 * what the settings show cannot drift from what gets placed (Chiaro and Passo
 * draw their settings previews the same way, through the real composition).
 *
 * The ground behind it stands in for the wallpaper: a soft two-tone wash, so a
 * see-through card shows that it is one. Taps are swallowed: the real widget's
 * intents are bound on these views, and a preview that opened the quick-entry
 * sheet would be a surprise.
 */
@Composable
internal fun QuickAddWidgetPreview(
    data: QuickAddWidgetData,
    theme: QuickAddWidgetTheme,
    bar: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val description = stringResource(R.string.widget_config_preview_a11y)
    val wallpaper = Brush.linearGradient(
        listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.tertiaryContainer),
    )
    Column(modifier = modifier.fillMaxWidth()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(WallpaperCorner))
                .background(wallpaper)
                .padding(WallpaperInset)
                .semantics { contentDescription = description },
        ) {
            val size = previewSize(maxWidth.value, data, bar)
            val palette = theme.palette
            Box(modifier = Modifier.fillMaxWidth().height(size.height.dp)) {
                AndroidView(
                    factory = { FrameLayout(it) },
                    update = { host ->
                        val views = WidgetRenderer.render(
                            context = context,
                            appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID,
                            data = data,
                            palette = palette,
                            size = size,
                        )
                        host.removeAllViews()
                        host.addView(views.apply(context, host))
                    },
                    modifier = Modifier.matchParentSize(),
                )
                // Above the widget, so the hit test ends here and no bound
                // intent fires.
                Box(
                    modifier = Modifier
                        .matchParentSize()
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
        Text(
            text = stringResource(R.string.widget_config_preview_caption),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, start = 4.dp),
        )
    }
}

/**
 * The size the preview is rendered for: the width it has, and the height of
 * the layout the placed widget opens at - one row for the bar, the wide grid at
 * the rows its categories fill, up to its default three.
 */
private fun previewSize(widthDp: Float, data: QuickAddWidgetData, bar: Boolean): WidgetSize {
    if (bar) return WidgetSize(widthDp, BarPreviewHeight)
    val rows = ((data.categories.size + 1 + WideColumns - 1) / WideColumns).coerceIn(1, PreviewGridRows)
    return WidgetSize(maxOf(widthDp, WideMinWidth), wideGridHeight(rows))
}

private const val BarPreviewHeight = 72f
private const val PreviewGridRows = 3
private val WallpaperCorner = 28.dp
private val WallpaperInset = 16.dp

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
