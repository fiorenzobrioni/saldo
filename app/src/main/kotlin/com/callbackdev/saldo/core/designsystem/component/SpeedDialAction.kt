package com.callbackdev.saldo.core.designsystem.component

import androidx.compose.ui.graphics.vector.ImageVector

/** One quick action of a [SpeedDialFab]: a labelled pill stacked above the toggle. */
data class SpeedDialAction(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
)
