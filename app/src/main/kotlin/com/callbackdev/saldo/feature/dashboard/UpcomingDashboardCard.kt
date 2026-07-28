package com.callbackdev.saldo.feature.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Upcoming
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.designsystem.component.SaldoCard
import com.callbackdev.saldo.core.designsystem.theme.SaldoDimens
import com.callbackdev.saldo.feature.upcoming.UpcomingRowContent
import com.callbackdev.saldo.feature.upcoming.upcomingDayLabel
import java.time.LocalDate

/**
 * Dashboard preview of what is coming (ADR 36): the soonest few movements with
 * the day they land on, and a line for the rest. Like the credits card it has
 * no empty invitation - the caller hides it when nothing is ahead - because a
 * card about the future is noise for someone whose ledger only holds the past.
 *
 * The rows are the same component the full list uses, so the card is a genuine
 * preview of the screen it opens rather than a second rendering of the same
 * data that can drift away from it.
 */
@Composable
internal fun UpcomingCard(
    preview: UpcomingPreview,
    today: LocalDate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (preview.isEmpty) return
    SaldoCard(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(
                horizontal = SaldoDimens.cardPadding,
                vertical = SaldoDimens.cardPaddingVertical,
            ),
        ) {
            DashboardCardHeader(
                icon = Icons.Outlined.Upcoming,
                title = stringResource(R.string.dashboard_upcoming_title),
                trailingContent = {
                    Text(
                        text = pluralStringResource(
                            R.plurals.dashboard_upcoming_count,
                            preview.totalCount,
                            preview.totalCount,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
            preview.items.forEachIndexed { index, item ->
                if (index > 0) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                UpcomingRowContent(
                    item = item,
                    dateLabel = upcomingDayLabel(item.date, today),
                    modifier = Modifier.padding(vertical = SaldoDimens.rowPaddingVertical),
                )
            }
            if (preview.hiddenCount > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = pluralStringResource(
                        R.plurals.dashboard_upcoming_more,
                        preview.hiddenCount,
                        preview.hiddenCount,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
