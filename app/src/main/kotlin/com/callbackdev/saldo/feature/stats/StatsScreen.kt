package com.callbackdev.saldo.feature.stats

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.designsystem.component.PlaceholderScreen

@Composable
fun StatsScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = stringResource(R.string.nav_stats),
        modifier = modifier,
    )
}
