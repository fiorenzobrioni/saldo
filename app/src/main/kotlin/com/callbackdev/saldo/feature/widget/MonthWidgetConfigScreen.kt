package com.callbackdev.saldo.feature.widget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.designsystem.theme.WidgetCardColor

/**
 * A month widget's settings (ADR 54), in the family's order: the card at the
 * sizes it can be given, what it is painted on and how solid, then what it
 * carries. No data source to choose: a month card reads the overall monthly
 * budget, which spans every account counted in it. Every choice is saved as it
 * is made; "Done" only closes the door.
 *
 * The preview is the real card over the real month, so with the app lock on it
 * shows no amount either: this screen is reached from the home screen, outside
 * the lock, and must not print what the card itself would not.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthWidgetConfigScreen(
    state: MonthWidgetConfigUiState,
    kind: MonthWidgetKind,
    theme: QuickAddWidgetTheme,
    placed: DpSize?,
    onLookChanged: (WidgetBackground, WidgetCardColor) -> Unit,
    onOpacityDrag: (Int) -> Unit,
    onOpacityDone: () -> Unit,
    onShowAmountsChanged: (Boolean) -> Unit,
    onShowNextChargeChanged: (Boolean) -> Unit,
    onReset: () -> Unit,
    onDone: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.widget_config_title)) }) },
    ) { padding ->
        // Until the stored configuration is read, nothing: a screen of defaults
        // about to change would show the reader choices in the wrong place.
        if (state.isLoading) return@Scaffold
        val config = state.config
        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = PageMaxWidth)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp),
            ) {
                val widgetKind = if (kind == MonthWidgetKind.RING) SaldoWidgetKind.RING else SaldoWidgetKind.WORDS
                state.data?.let { data ->
                    WidgetPreviewSection(
                        kind = widgetKind,
                        placed = placed,
                        note = { size -> monthFormNote(kind, size) },
                        render = { context, size ->
                            MonthWidgetRenderer.render(context, kind, data, config, theme.palette, size)
                        },
                    )
                } ?: Box(Modifier.padding(top = PreviewPlaceholder))

                ConfigHeader(stringResource(R.string.widget_config_background))
                ConfigGroup {
                    BackgroundChoices(config.background, config.cardColor, theme.dress, onLookChanged)
                    ConfigDivider()
                    OpacityRow(config.opacityPct, onOpacityDrag, onOpacityDone)
                }

                ConfigHeader(stringResource(R.string.widget_config_content))
                ConfigGroup {
                    ConfigSwitchRow(
                        label = stringResource(R.string.widget_config_amounts),
                        note = stringResource(R.string.widget_config_amounts_caption),
                        checked = config.showAmounts,
                        onToggle = onShowAmountsChanged,
                    )
                    if (kind == MonthWidgetKind.WORDS) {
                        ConfigDivider()
                        ConfigSwitchRow(
                            label = stringResource(R.string.widget_config_next_charge),
                            note = stringResource(R.string.widget_config_next_charge_caption),
                            checked = config.showNextCharge,
                            onToggle = onShowNextChargeChanged,
                        )
                    }
                }

                ConfigResetButton(onReset)
                ConfigDoneButton(onDone)
            }
        }
    }
}

/** The widest the page's column grows on an open foldable or a phone on its side. */
private val PageMaxWidth = 640.dp

/** Room kept for the preview while the month is read, so the page does not jump twice. */
private val PreviewPlaceholder = 8.dp
