package com.callbackdev.saldo.feature.recap

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.common.money.MoneyFormatter
import com.callbackdev.saldo.core.designsystem.theme.moneyColors
import com.callbackdev.saldo.core.designsystem.theme.tabularNumbers
import com.callbackdev.saldo.core.designsystem.visuals.CategoryVisuals
import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.MonthlyRecap

/**
 * The single shareable summary of the recap month (ADR 28): a fixed-layout
 * 360x640dp portrait card rendered off-screen at 3x density into a
 * 1080x1920px image. It inherits the screen's ambient theme, so the exported
 * image matches exactly what the user saw when sharing (share what you see).
 */
@Composable
internal fun RecapShareCard(
    recap: MonthlyRecap,
    categoryById: Map<Long, Category>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                        MaterialTheme.colorScheme.background,
                    ),
                ),
            )
            .padding(28.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.padding(start = 8.dp))
            Text(
                text = stringResource(R.string.recap_title, recapMonthTitle(recap.month)),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Spacer(Modifier.height(28.dp))
        ShareFigure(
            label = stringResource(R.string.recap_net_label),
            value = MoneyFormatter.formatSigned(recap.net, recap.currency),
            valueColor = if (recap.net.signum() < 0) {
                MaterialTheme.moneyColors.expense
            } else {
                MaterialTheme.moneyColors.income
            },
        )
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            ShareFigure(
                label = stringResource(R.string.recap_expense_label),
                value = MoneyFormatter.formatSigned(recap.expenseTotal.negate(), recap.currency),
                valueColor = MaterialTheme.moneyColors.expense,
                modifier = Modifier.weight(1f),
                compact = true,
            )
            ShareFigure(
                label = stringResource(R.string.recap_income_label),
                value = MoneyFormatter.formatSigned(recap.incomeTotal, recap.currency),
                valueColor = MaterialTheme.moneyColors.income,
                modifier = Modifier.weight(1f),
                compact = true,
            )
        }
        if (recap.topCategories.isNotEmpty()) {
            Spacer(Modifier.height(28.dp))
            Text(
                text = stringResource(R.string.recap_top_categories_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            recap.topCategories.take(SHARE_TOP_CATEGORIES).forEach { share ->
                val category = share.categoryId?.let { categoryById[it] }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(CategoryVisuals.color(category?.color)),
                    )
                    Text(
                        text = category?.name ?: stringResource(R.string.transaction_uncategorized),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 10.dp, end = 8.dp),
                    )
                    Text(
                        text = stringResource(
                            R.string.recap_category_amount_percent,
                            MoneyFormatter.format(share.amount, recap.currency),
                            share.percent,
                        ),
                        style = MaterialTheme.typography.labelMedium.tabularNumbers(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.recap_closing_privacy),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ShareFigure(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Column(modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = (
                if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.displaySmall
                ).tabularNumbers(),
            fontWeight = FontWeight.SemiBold,
            color = valueColor,
            maxLines = 1,
        )
    }
}

private const val SHARE_TOP_CATEGORIES = 3
