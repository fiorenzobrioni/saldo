package com.callbackdev.saldo.feature.widget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callbackdev.saldo.core.designsystem.theme.WidgetCardColor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MonthWidgetConfigUiState(
    val isLoading: Boolean = true,
    val config: MonthWidgetConfig = MonthWidgetConfig(),
    /** The month as the widget will draw it, for the preview; null until read. */
    val data: MonthWidgetData? = null,
)

/**
 * Backs a month widget's settings (ADR 54): the card's look and its two
 * switches. Every choice is kept as it is made, like the quick-add widget's:
 * [savedConfig] is what the activity writes and repaints, and "Done" only
 * closes the screen.
 */
@HiltViewModel
class MonthWidgetConfigViewModel @Inject constructor(
    private val loader: MonthWidgetDataLoader,
) : ViewModel() {

    private val config = MutableStateFlow(MonthWidgetConfig())
    private val seeded = MutableStateFlow(false)
    private val opacityDrag = MutableStateFlow<Int?>(null)
    private val data = MutableStateFlow<MonthWidgetData?>(null)

    init {
        // The preview's month, read once: the screen is open for a few seconds
        // and draws the month as it stands when it opened.
        viewModelScope.launch { data.value = runCatching { loader.load() }.getOrNull() }
    }

    val uiState: StateFlow<MonthWidgetConfigUiState> = combine(
        config,
        seeded,
        opacityDrag,
        data,
    ) { stored, ready, drag, month ->
        MonthWidgetConfigUiState(
            isLoading = !ready,
            config = if (drag != null) stored.copy(opacityPct = drag) else stored,
            data = month,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = MonthWidgetConfigUiState(),
    )

    /** What the store should hold: every choice once made, never a slider mid-drag. */
    val savedConfig: Flow<MonthWidgetConfig> = combine(config, seeded) { current, ready ->
        current.takeIf { ready }
    }.filterNotNull().distinctUntilChanged()

    fun initialize(stored: MonthWidgetConfig) {
        config.value = stored
        seeded.value = true
    }

    fun onLookChanged(background: WidgetBackground, cardColor: WidgetCardColor) {
        config.update { it.copy(background = background, cardColor = cardColor) }
    }

    fun onOpacityDragged(pct: Int) {
        opacityDrag.value = pct.coerceIn(0, FullOpacity)
    }

    fun onOpacityDone() {
        val pct = opacityDrag.value ?: return
        config.update { it.copy(opacityPct = pct) }
        opacityDrag.value = null
    }

    fun onShowAmountsChanged(show: Boolean) {
        config.update { it.copy(showAmounts = show) }
    }

    fun onShowNextChargeChanged(show: Boolean) {
        config.update { it.copy(showNextCharge = show) }
    }

    /** Back to a card nobody configured: a month widget has no data source to keep. */
    fun onReset() {
        opacityDrag.value = null
        config.value = MonthWidgetConfig()
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
