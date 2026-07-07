package com.callbackdev.saldo.feature.transactions

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.designsystem.component.PlaceholderScreen

@Composable
fun TransactionsScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = stringResource(R.string.nav_transactions),
        modifier = modifier,
    )
}
