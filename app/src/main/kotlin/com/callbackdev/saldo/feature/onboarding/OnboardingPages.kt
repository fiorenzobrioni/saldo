package com.callbackdev.saldo.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.designsystem.component.AmountTarget
import com.callbackdev.saldo.core.designsystem.component.AmountTextField
import com.callbackdev.saldo.core.domain.model.CurrencyCatalog
import java.util.Currency

/**
 * The onboarding page bodies. Each page is a column with the shared visual
 * language (app-icon hero, centered headline and body) followed by its own
 * content; static pages scroll so nothing clips at 200% font scale. The CTAs
 * live in the screen-level action block, not here.
 */

@Composable
internal fun WelcomePage(modifier: Modifier = Modifier) {
    StaticPage(
        hero = { WelcomeAppIcon() },
        title = stringResource(R.string.onboarding_welcome_title),
        body = stringResource(R.string.onboarding_welcome_body),
        modifier = modifier,
    )
}

@Composable
internal fun PrivacyPage(modifier: Modifier = Modifier) {
    StaticPage(
        hero = { AppIconWithSecurityBadge(badge = Icons.Outlined.VerifiedUser) },
        title = stringResource(R.string.onboarding_privacy_title),
        body = stringResource(R.string.onboarding_privacy_body),
        modifier = modifier,
    )
}

@Composable
internal fun NotificationsPage(modifier: Modifier = Modifier) {
    StaticPage(
        hero = { AppIconWithNotificationBadge(badge = Icons.Outlined.NotificationsActive) },
        title = stringResource(R.string.onboarding_notifications_title),
        body = stringResource(R.string.onboarding_notifications_body),
        modifier = modifier,
    )
}

@Composable
internal fun CurrencyPage(
    selected: Currency,
    onSelected: (Currency) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        PageHeader(
            title = stringResource(R.string.onboarding_currency_title),
            body = stringResource(R.string.onboarding_currency_body),
        )
        Spacer(Modifier.height(16.dp))
        val locale = LocalConfiguration.current.locales[0]
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 24.dp),
        ) {
            items(CurrencyCatalog.supportedCurrencies, key = { it.currencyCode }) { currency ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = currency == selected,
                            role = Role.RadioButton,
                            onClick = { onSelected(currency) },
                        )
                        .padding(vertical = 10.dp),
                ) {
                    RadioButton(selected = currency == selected, onClick = null)
                    Text(
                        text = "${currency.currencyCode} - ${currency.getDisplayName(locale)}",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun AccountPage(
    uiState: OnboardingUiState,
    onNameChanged: (String) -> Unit,
    balanceTarget: AmountTarget,
    onActivateBalance: () -> Unit,
    onCloseKeypad: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        PageHeader(
            title = stringResource(R.string.onboarding_account_title),
            body = stringResource(R.string.onboarding_account_body),
        )
        Spacer(Modifier.height(24.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = uiState.accountName,
                onValueChange = onNameChanged,
                label = { Text(stringResource(R.string.onboarding_account_name_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                // The name field brings the system IME up: the keypad steps aside.
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { if (it.isFocused) onCloseKeypad() },
            )
            AmountTextField(
                target = balanceTarget,
                label = stringResource(R.string.onboarding_account_balance_label),
                onActivate = onActivateBalance,
                suffix = uiState.selectedCurrency.currencyCode,
                supportingText = stringResource(R.string.onboarding_account_balance_hint),
                showSignToggle = true,
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** A page with no inputs: hero, headline, body, all centered and scrollable. */
@Composable
private fun StaticPage(
    hero: @Composable () -> Unit,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        hero()
        Spacer(Modifier.height(32.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
    }
}

/** Compact top header for the pages that carry inputs below. */
@Composable
private fun PageHeader(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

