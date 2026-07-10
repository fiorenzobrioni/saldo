package com.callbackdev.saldo.feature.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.callbackdev.saldo.R

/**
 * Speed-dial FAB: the primary button expands into three quick actions (expense,
 * income, transfer). The caller owns [expanded] so it can also dim the content
 * behind the dial.
 */
@Composable
internal fun DashboardSpeedDial(
    expanded: Boolean,
    onToggle: () -> Unit,
    onAddExpense: () -> Unit,
    onAddIncome: () -> Unit,
    onAddTransfer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 45f else 0f,
        label = "speed-dial-rotation",
    )
    val haptics = LocalHapticFeedback.current
    Column(horizontalAlignment = Alignment.End, modifier = modifier) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + scaleIn(transformOrigin = TransformOrigin(1f, 1f)),
            exit = fadeOut() + scaleOut(transformOrigin = TransformOrigin(1f, 1f)),
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(bottom = 16.dp),
            ) {
                SpeedDialAction(
                    label = stringResource(R.string.dashboard_fab_transfer),
                    icon = Icons.Outlined.SwapHoriz,
                    onClick = onAddTransfer,
                )
                SpeedDialAction(
                    label = stringResource(R.string.dashboard_fab_income),
                    icon = Icons.AutoMirrored.Outlined.TrendingUp,
                    onClick = onAddIncome,
                )
                SpeedDialAction(
                    label = stringResource(R.string.dashboard_fab_expense),
                    icon = Icons.AutoMirrored.Outlined.TrendingDown,
                    onClick = onAddExpense,
                )
            }
        }
        FloatingActionButton(
            onClick = {
                haptics.performHapticFeedback(
                    if (expanded) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn,
                )
                onToggle()
            },
        ) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = stringResource(R.string.dashboard_fab_add),
                modifier = Modifier.graphicsLayer { rotationZ = rotation },
            )
        }
    }
}

@Composable
private fun SpeedDialAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            onClick = onClick,
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 3.dp,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        SmallFloatingActionButton(
            onClick = onClick,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Icon(imageVector = icon, contentDescription = label)
        }
    }
}
