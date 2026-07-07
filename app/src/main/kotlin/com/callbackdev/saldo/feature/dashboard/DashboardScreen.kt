package com.callbackdev.saldo.feature.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.designsystem.component.PlaceholderScreen

@Composable
fun DashboardScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = stringResource(R.string.nav_dashboard),
        modifier = modifier,
    )
}
