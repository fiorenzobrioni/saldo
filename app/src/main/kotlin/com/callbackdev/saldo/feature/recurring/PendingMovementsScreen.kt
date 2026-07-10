package com.callbackdev.saldo.feature.recurring

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.common.money.MoneyFormatter
import com.callbackdev.saldo.core.common.money.MoneyInput
import com.callbackdev.saldo.core.designsystem.component.EmptyState
import com.callbackdev.saldo.core.designsystem.component.LoadingState
import com.callbackdev.saldo.core.designsystem.theme.AvatarShape
import com.callbackdev.saldo.core.designsystem.theme.SaldoDimens
import com.callbackdev.saldo.core.designsystem.visuals.CategoryVisuals
import com.callbackdev.saldo.core.domain.money.MoneyMapper
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Confirmation of pending recurring movements: each row shows the subscription
 * and the due date, and tapping it opens a sheet to confirm the amount or skip
 * the charge. Reached from the dashboard "to confirm" card.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingMovementsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PendingMovementsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmTarget by remember { mutableStateOf<PendingItem?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.pending_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        when {
            uiState.isLoading -> LoadingState(Modifier.padding(innerPadding))

            uiState.isEmpty -> PendingEmptyState(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
            ) {
                item {
                    PendingCard(
                        items = uiState.items,
                        onItemClick = { confirmTarget = it },
                    )
                }
            }
        }
    }

    confirmTarget?.let { target ->
        ConfirmSheet(
            item = target,
            onConfirm = { magnitude ->
                viewModel.confirm(target.transaction, magnitude)
                confirmTarget = null
            },
            onSkip = {
                viewModel.skip(target.transaction)
                confirmTarget = null
            },
            onDismiss = { confirmTarget = null },
        )
    }
}

@Composable
private fun PendingCard(
    items: List<PendingItem>,
    onItemClick: (PendingItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column {
            items.forEachIndexed { index, item ->
                if (index > 0) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(horizontal = SaldoDimens.rowPaddingHorizontal),
                    )
                }
                Surface(onClick = { onItemClick(item) }, color = Color.Transparent) {
                    PendingRow(
                        item = item,
                        modifier = Modifier.padding(
                            horizontal = SaldoDimens.rowPaddingHorizontal,
                            vertical = SaldoDimens.rowPaddingVertical,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun PendingRow(item: PendingItem, modifier: Modifier = Modifier) {
    val color = CategoryVisuals.color(item.rule?.color)
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(AvatarShape)
                .background(color.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = CategoryVisuals.icon(item.rule?.icon),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.subscriptions_charged_on, shortDate(item.date))
                    .replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = if (item.isVariable) {
                stringResource(R.string.pending_amount_to_enter)
            } else {
                MoneyFormatter.format(item.magnitude, item.transaction.currency)
            },
            style = MaterialTheme.typography.titleMedium,
            color = if (item.isVariable) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfirmSheet(
    item: PendingItem,
    onConfirm: (java.math.BigDecimal) -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit,
) {
    val currency = item.transaction.currency
    val digits = MoneyMapper.fractionDigits(currency)
    var amountInput by remember(item.id) {
        mutableStateOf(if (item.isVariable) "" else item.magnitude.stripTrailingZeros().toPlainString())
    }
    val magnitude = MoneyInput.parse(amountInput)?.takeIf { it.signum() > 0 }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(text = item.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.subscriptions_charged_on, shortDate(item.date))
                    .replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                value = amountInput,
                onValueChange = { amountInput = MoneyInput.sanitize(it, digits, allowNegative = false) },
                label = { Text(stringResource(R.string.subscription_editor_amount)) },
                placeholder = { Text(stringResource(R.string.editor_amount_placeholder)) },
                prefix = { Text(currency.symbol) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onSkip, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.pending_skip))
                }
                Button(
                    onClick = { magnitude?.let(onConfirm) },
                    enabled = magnitude != null,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.pending_confirm))
                }
            }
        }
    }
}

@Composable
private fun PendingEmptyState(modifier: Modifier = Modifier) {
    EmptyState(
        icon = Icons.Outlined.TaskAlt,
        title = stringResource(R.string.pending_empty_title),
        body = stringResource(R.string.pending_empty_body),
        modifier = modifier,
    )
}

@Composable
private fun shortDate(date: LocalDate): String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(date, locale) {
        val pattern = DateFormat.getBestDateTimePattern(locale, "dMMM")
        date.format(DateTimeFormatter.ofPattern(pattern, locale))
    }
}
