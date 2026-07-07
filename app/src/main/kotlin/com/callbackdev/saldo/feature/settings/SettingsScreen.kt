package com.callbackdev.saldo.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.designsystem.component.PlaceholderScreen

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = stringResource(R.string.nav_settings),
        modifier = modifier,
    )
}
