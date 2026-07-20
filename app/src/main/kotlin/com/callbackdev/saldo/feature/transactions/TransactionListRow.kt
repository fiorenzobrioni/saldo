package com.callbackdev.saldo.feature.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.common.money.MoneyFormatter
import com.callbackdev.saldo.core.designsystem.component.SaldoCardDefaults
import com.callbackdev.saldo.core.designsystem.theme.AvatarShape
import com.callbackdev.saldo.core.designsystem.theme.SaldoDimens
import com.callbackdev.saldo.core.designsystem.theme.moneyColors
import com.callbackdev.saldo.core.designsystem.theme.tabularNumbers
import com.callbackdev.saldo.core.designsystem.visuals.CategoryVisuals
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.core.domain.model.isRecurring

/**
 * A movement row that can be swiped away (end to start) to delete it. Flat, so
 * it sits inside a day's grouped card: the opaque foreground hides the delete
 * background until swiped, and the parent card clips the rounded corners.
 */
@Composable
internal fun SwipeableTransactionRow(
    item: TransactionListItem,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnDelete by rememberUpdatedState(onDelete)
    val haptics = LocalHapticFeedback.current
    val dismissState = rememberSwipeToDismissBoxState()
    // Trigger the delete when the row settles in the dismissed state, observing
    // the state instead of vetoing it from a callback (confirmValueChange is
    // deprecated). Only the end-to-start direction is enabled below, and the box
    // reaches EndToStart only past the swipe threshold, so a short drag that
    // snaps back never deletes.
    LaunchedEffect(dismissState) {
        snapshotFlow { dismissState.currentValue }
            .collect { value ->
                if (value == SwipeToDismissBoxValue.EndToStart) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    currentOnDelete()
                }
            }
    }
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                contentAlignment = Alignment.CenterEnd,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer),
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = stringResource(R.string.transaction_editor_delete),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(end = 24.dp),
                )
            }
        },
        modifier = modifier,
    ) {
        Surface(
            onClick = onClick,
            color = SaldoCardDefaults.containerColor,
            modifier = Modifier.fillMaxWidth(),
        ) {
            TransactionRowContent(
                item = item,
                modifier = Modifier.padding(
                    horizontal = SaldoDimens.rowPaddingHorizontal,
                    vertical = SaldoDimens.rowPaddingVertical,
                ),
            )
        }
    }
}

/**
 * The inner layout of a movement row (avatar, title/subtitle, signed amount)
 * without any surface of its own, so it can sit inside a filled row card or a
 * flat grouped list (e.g. the dashboard's recent movements).
 */
@Composable
internal fun TransactionRowContent(
    item: TransactionListItem,
    modifier: Modifier = Modifier,
    dateLabel: String? = null,
) {
    val transaction = item.transaction
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TransactionAvatar(item = item)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(
                text = itemTitle(item),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = itemSubtitle(item),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (transaction.isRecurring) {
            Icon(
                imageVector = Icons.Outlined.Repeat,
                contentDescription = stringResource(R.string.transaction_recurring_badge),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(16.dp),
            )
        }
        if (transaction.isExcludedFromStats) {
            Icon(
                imageVector = Icons.Outlined.VisibilityOff,
                contentDescription =
                stringResource(R.string.transaction_editor_exclude_stats),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(16.dp),
            )
        }
        if (dateLabel != null) {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = itemAmountText(item),
                    style = MaterialTheme.typography.titleMedium.tabularNumbers(),
                    color = itemAmountColor(item),
                )
                Text(
                    text = dateLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Text(
                text = itemAmountText(item),
                style = MaterialTheme.typography.titleMedium.tabularNumbers(),
                color = itemAmountColor(item),
            )
        }
    }
}

@Composable
private fun TransactionAvatar(
    item: TransactionListItem,
    modifier: Modifier = Modifier,
) {
    val type = item.transaction.type
    val isCategorized = type == TransactionType.EXPENSE || type == TransactionType.INCOME
    val background: Color
    val tint: Color
    val icon: ImageVector
    if (isCategorized && item.category != null) {
        background = CategoryVisuals.color(item.category.color).copy(alpha = 0.16f)
        tint = CategoryVisuals.color(item.category.color)
        icon = CategoryVisuals.icon(item.category.icon)
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
        modifier = modifier
            .size(44.dp)
            .clip(AvatarShape)
            .background(background),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun itemTitle(item: TransactionListItem): String {
    val transaction = item.transaction
    return transaction.description
        ?: item.category?.name
        ?: when (transaction.type) {
            TransactionType.TRANSFER,
            TransactionType.ADJUSTMENT,
            -> stringResource(transaction.type.labelRes())

            else -> stringResource(R.string.transaction_uncategorized)
        }
}

@Composable
private fun itemSubtitle(item: TransactionListItem): String {
    val transaction = item.transaction
    if (transaction.type == TransactionType.TRANSFER) {
        return stringResource(
            R.string.transaction_transfer_route,
            item.account?.name.orEmpty(),
            item.toAccount?.name.orEmpty(),
        )
    }
    return buildList {
        item.account?.let { add(it.name) }
        if (transaction.isRefund) add(stringResource(R.string.transaction_refund_label))
    }.joinToString(separator = " · ")
}

@Composable
private fun itemAmountText(item: TransactionListItem): String {
    val transaction = item.transaction
    return when (transaction.type) {
        TransactionType.TRANSFER ->
            MoneyFormatter.format(transaction.amount.abs(), transaction.currency)

        TransactionType.INCOME, TransactionType.ADJUSTMENT ->
            MoneyFormatter.formatSigned(transaction.amount, transaction.currency)

        TransactionType.EXPENSE ->
            MoneyFormatter.format(transaction.amount, transaction.currency)
    }
}

@Composable
private fun itemAmountColor(item: TransactionListItem): Color = when (item.transaction.type) {
    TransactionType.INCOME -> MaterialTheme.moneyColors.income
    TransactionType.TRANSFER, TransactionType.ADJUSTMENT -> MaterialTheme.moneyColors.neutral
    TransactionType.EXPENSE -> MaterialTheme.moneyColors.expense
}
