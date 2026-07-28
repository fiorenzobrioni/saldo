@file:Suppress("TooManyFunctions") // One small composable per row/pill/sheet piece.

package com.callbackdev.saldo.feature.upcoming

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.common.money.MoneyFormatter
import com.callbackdev.saldo.core.common.money.MoneyInput
import com.callbackdev.saldo.core.designsystem.component.AmountKeypadHost
import com.callbackdev.saldo.core.designsystem.component.AmountTarget
import com.callbackdev.saldo.core.designsystem.component.HeroAmountField
import com.callbackdev.saldo.core.designsystem.theme.AvatarShape
import com.callbackdev.saldo.core.designsystem.theme.moneyColors
import com.callbackdev.saldo.core.designsystem.theme.tabularNumbers
import com.callbackdev.saldo.core.designsystem.visuals.CategoryVisuals
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.core.domain.model.UpcomingOrigin
import com.callbackdev.saldo.core.domain.money.MoneyMapper
import com.callbackdev.saldo.feature.transactions.dayLabel
import com.callbackdev.saldo.feature.transactions.shortDayLabel
import java.math.BigDecimal
import java.time.LocalDate

/**
 * One upcoming movement: avatar, what it is, where it lands, and the amount.
 * A movement still to confirm carries its own pill and an amount that reads as
 * a figure to type when the rule has no fixed one, so the two states of the
 * list never look alike.
 */
@Composable
internal fun UpcomingRowContent(
    item: UpcomingItem,
    modifier: Modifier = Modifier,
    dateLabel: String? = null,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        UpcomingAvatar(item = item)
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                text = item.title.ifBlank { stringResource(R.string.transaction_uncategorized) },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OriginBadge(item = item)
                Text(
                    text = subtitle(item),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = amountText(item),
                style = MaterialTheme.typography.titleMedium.tabularNumbers(),
                color = amountColor(item),
                maxLines = 1,
            )
            // In the list the day is the group heading, so only the preview
            // card - where the rows stand alone - repeats it under the amount.
            when {
                item.isPending -> {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.upcoming_tap_to_confirm),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                dateLabel != null -> {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = dateLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * The avatar follows the movement's category, like everywhere else, and falls
 * back to the generating rule's own colour when the occurrence carries no
 * category of its own.
 */
@Composable
private fun UpcomingAvatar(item: UpcomingItem, modifier: Modifier = Modifier) {
    val type = item.transaction.type
    val categoryColor = item.category?.color ?: item.rule?.color
    val hasVisual = (type == TransactionType.EXPENSE || type == TransactionType.INCOME) &&
        (item.category != null || item.rule != null)
    val background: Color
    val tint: Color
    val icon: ImageVector
    if (hasVisual) {
        tint = CategoryVisuals.color(categoryColor)
        background = tint.copy(alpha = 0.16f)
        icon = CategoryVisuals.icon(item.category?.icon ?: item.rule?.icon)
    } else {
        background = MaterialTheme.colorScheme.surfaceContainerHighest
        tint = MaterialTheme.colorScheme.onSurfaceVariant
        icon = when (type) {
            TransactionType.TRANSFER -> Icons.Outlined.SwapHoriz
            TransactionType.ADJUSTMENT -> Icons.Outlined.Tune
            else -> Icons.Outlined.Category
        }
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(44.dp).clip(AvatarShape).background(background),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

/**
 * Where the movement comes from, as a small leading glyph rather than a word:
 * the origin is context for the amount, not the headline, and three text labels
 * repeated down a list would drown the names they qualify. A manual movement
 * gets none - "I typed this in" is the default and needs no mark - unless it
 * carries a reminder, which is worth seeing at a glance.
 */
@Composable
private fun OriginBadge(item: UpcomingItem, modifier: Modifier = Modifier) {
    val icon = when {
        item.movement.origin == UpcomingOrigin.PENDING -> null
        item.movement.origin == UpcomingOrigin.RECURRING -> Icons.Outlined.Repeat
        item.transaction.hasReminder -> Icons.Outlined.NotificationsActive
        else -> null
    } ?: return
    val description = stringResource(
        if (item.movement.origin == UpcomingOrigin.RECURRING) {
            R.string.transaction_recurring_badge
        } else {
            R.string.upcoming_reminder_badge
        },
    )
    Icon(
        imageVector = icon,
        contentDescription = description,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(end = 4.dp).size(14.dp),
    )
}

@Composable
private fun subtitle(item: UpcomingItem): String {
    if (item.isTransfer) {
        return stringResource(
            R.string.transaction_transfer_route,
            item.account?.name.orEmpty(),
            item.transferAccount?.name.orEmpty(),
        )
    }
    return item.account?.name.orEmpty()
}

@Composable
private fun amountText(item: UpcomingItem): String = when {
    item.needsAmountEntry -> stringResource(R.string.pending_amount_to_enter)
    item.isTransfer -> MoneyFormatter.format(item.magnitude, item.transaction.currency)
    else -> MoneyFormatter.formatSigned(item.transaction.amount, item.transaction.currency)
}

@Composable
private fun amountColor(item: UpcomingItem): Color = when {
    item.needsAmountEntry -> MaterialTheme.colorScheme.primary
    else -> when (item.transaction.type) {
        TransactionType.INCOME -> MaterialTheme.moneyColors.income
        TransactionType.EXPENSE -> MaterialTheme.moneyColors.expense
        TransactionType.TRANSFER, TransactionType.ADJUSTMENT -> MaterialTheme.moneyColors.neutral
    }
}

/**
 * Day heading of a group. Reuses the ledger's own labels so the two lists read
 * the same, and adds the one word a forward-looking list needs and a backward
 * one never does.
 */
@Composable
internal fun upcomingDayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today.plusDays(1) -> stringResource(R.string.date_tomorrow)
    else -> dayLabel(date, today)
}

/**
 * Confirmation of a pending occurrence: the amount gets the hero treatment with
 * the keypad already up, because typing it is the only reason this sheet
 * exists. Skipping discards the occurrence, which is a legitimate answer - the
 * charge did not happen - and not an error.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConfirmSheet(
    item: UpcomingItem,
    today: LocalDate,
    onConfirm: (BigDecimal) -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit,
) {
    val currency = item.entryCurrency
    var amountInput by remember(item.id) {
        mutableStateOf(
            if (item.needsAmountEntry) "" else item.magnitude.stripTrailingZeros().toPlainString(),
        )
    }
    val magnitude = MoneyInput.parse(amountInput)?.takeIf { it.signum() > 0 }
    val amountTarget = AmountTarget(
        value = amountInput,
        fractionDigits = MoneyMapper.fractionDigits(currency),
        allowNegative = false,
        onValueChange = { amountInput = it },
    )

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = item.title.ifBlank { stringResource(R.string.transaction_uncategorized) },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = confirmSubtitle(item, today),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            HeroAmountField(
                target = amountTarget,
                currencySymbol = currency.symbol,
                isError = false,
                isActive = true,
                onActivate = {},
                compact = true,
                label = stringResource(
                    if (item.isCrossCurrencyTransfer) {
                        R.string.transfer_received_amount
                    } else {
                        R.string.subscription_editor_amount
                    },
                ),
            )
            Spacer(Modifier.height(8.dp))
            AmountKeypadHost(target = amountTarget, compact = true)
            Spacer(Modifier.height(16.dp))
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
private fun confirmSubtitle(item: UpcomingItem, today: LocalDate): String =
    if (item.isCrossCurrencyTransfer && item.transferAccount != null) {
        // Remind the user what they are sending, so they enter what arrived.
        stringResource(
            R.string.transfer_pending_sending,
            MoneyFormatter.format(item.magnitude, item.transaction.currency),
            item.transferAccount.name,
        )
    } else {
        stringResource(R.string.subscriptions_charged_on, shortDayLabel(item.date, today))
            .replaceFirstChar { it.uppercase() }
    }
