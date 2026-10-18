package com.callbackdev.saldo.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.designsystem.component.AmountTarget
import com.callbackdev.saldo.core.designsystem.component.AmountTextField
import com.callbackdev.saldo.core.domain.model.CurrencyCatalog
import java.util.Currency

/**
 * The onboarding page bodies (ADR 55): words only, centered, no picture. The frame around them
 * (centered in the room above the buttons, scrolling when taller, a phone's width on an open
 * foldable) and the buttons live in the screen. [where] is the step, said to a screen reader on
 * each page's heading in place of a page indicator.
 */

/** The name and its sentence, then the three things Saldo promises, the privacy page's among them. */
@Composable
internal fun WelcomePage(where: String) {
    Text(
        text = stringResource(R.string.onboarding_app_name),
        style = MaterialTheme.typography.displaySmall,
        textAlign = TextAlign.Center,
        modifier = Modifier.stepHeading(where),
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.onboarding_welcome_tagline),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(36.dp))
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Promise(R.string.onboarding_promise_taps_title, R.string.onboarding_promise_taps_body)
        Promise(R.string.onboarding_promise_phone_title, R.string.onboarding_promise_phone_body)
        Promise(R.string.onboarding_promise_backup_title, R.string.onboarding_promise_backup_body)
    }
}

/** A promise in words only: a title and its line, no badge. */
@Composable
private fun Promise(title: Int, body: Int) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun NotificationsPage(where: String) {
    PageHeader(
        title = stringResource(R.string.onboarding_notifications_title),
        body = stringResource(R.string.onboarding_notifications_body),
        where = where,
    )
}

@Composable
internal fun CurrencyPage(
    selected: Currency,
    onSelected: (Currency) -> Unit,
    where: String,
) {
    PageHeader(
        title = stringResource(R.string.onboarding_currency_title),
        body = stringResource(R.string.onboarding_currency_body),
        where = where,
    )
    Spacer(Modifier.height(24.dp))
    val locale = LocalConfiguration.current.locales[0]
    // A plain column, not a lazy list: the page scrolls as a whole, as every page does, and
    // the catalog is a few dozen rows.
    Column(modifier = Modifier.fillMaxWidth().selectableGroup()) {
        CurrencyCatalog.supportedCurrencies.forEach { currency ->
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

@Composable
internal fun AccountPage(
    uiState: OnboardingUiState,
    onNameChanged: (String) -> Unit,
    balanceTarget: AmountTarget,
    onActivateBalance: () -> Unit,
    onCloseKeypad: () -> Unit,
    where: String,
) {
    PageHeader(
        title = stringResource(R.string.onboarding_account_title),
        body = stringResource(R.string.onboarding_account_body),
        where = where,
    )
    Spacer(Modifier.height(24.dp))
    Column(
        modifier = Modifier.fillMaxWidth(),
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
}

/** A page's title and its words, centered. */
@Composable
private fun PageHeader(
    title: String,
    body: String,
    where: String,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.stepHeading(where),
    )
    Spacer(Modifier.height(12.dp))
    Text(
        text = body,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

/** A page's heading, announced with where the run stands. */
private fun Modifier.stepHeading(where: String): Modifier = semantics {
    heading()
    stateDescription = where
}
