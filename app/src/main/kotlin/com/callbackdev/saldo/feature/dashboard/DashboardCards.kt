@file:Suppress("TooManyFunctions") // A collection of dashboard card composables.

package com.callbackdev.saldo.feature.dashboard

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.MoneyOff
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.common.date.withLocaleDateCasing
import com.callbackdev.saldo.core.common.money.MoneyFormatter
import com.callbackdev.saldo.core.designsystem.component.SaldoCard
import com.callbackdev.saldo.core.designsystem.component.rememberMotionEnabled
import com.callbackdev.saldo.core.designsystem.theme.AvatarShape
import com.callbackdev.saldo.core.designsystem.theme.SaldoDimens
import com.callbackdev.saldo.core.designsystem.theme.moneyColors
import com.callbackdev.saldo.core.designsystem.theme.tabularNumbers
import com.callbackdev.saldo.core.designsystem.visuals.AccountVisuals
import com.callbackdev.saldo.core.domain.model.AccountWithBalance
import com.callbackdev.saldo.core.domain.model.DailyBalance
import com.callbackdev.saldo.core.domain.model.PeriodTotals
import com.callbackdev.saldo.core.domain.money.MoneyMapper
import com.callbackdev.saldo.feature.recap.recapMonthTitle
import com.callbackdev.saldo.feature.transactions.TransactionListItem
import com.callbackdev.saldo.feature.transactions.TransactionRowContent
import com.callbackdev.saldo.feature.transactions.compactDayLabel
import com.callbackdev.saldo.feature.transactions.localDate
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Currency

/**
 * A warm, time-of-day greeting as the screen's title. The [band] and [roll] are
 * fixed once per app-open in the ViewModel, so the message is stable across
 * recomposition and rotation and only changes on a fresh open. Messages are
 * written to fit one line at the default font scale; a second line is allowed
 * so larger accessibility font sizes never truncate the text.
 */
@Composable
internal fun DashboardHeader(band: GreetingBand, roll: Float, modifier: Modifier = Modifier) {
    val greetings = stringArrayResource(band.greetingsArrayRes())
    val greeting = greetings.getOrElse((roll * greetings.size).toInt()) { greetings.firstOrNull().orEmpty() }
    Text(
        text = greeting,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * The uniform card header: leading icon in the primary tint, [title] in
 * titleMedium and an optional trailing slot (the balance card's date). Every
 * dashboard card opens its detail on tap, so no chevron: the convention is
 * carried by the cards themselves, not per-card affordances.
 */
@Composable
internal fun DashboardCardHeader(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        trailingContent?.invoke()
    }
}

@androidx.annotation.ArrayRes
private fun GreetingBand.greetingsArrayRes(): Int = when (this) {
    GreetingBand.NIGHT -> R.array.dashboard_greetings_night
    GreetingBand.MORNING -> R.array.dashboard_greetings_morning
    GreetingBand.AFTERNOON -> R.array.dashboard_greetings_afternoon
    GreetingBand.EVENING -> R.array.dashboard_greetings_evening
}

/**
 * Full localized weekday date in the locale's own casing: Italian dates are
 * lowercase ("venerdì 10 luglio"), English weekday/month names are proper
 * nouns ("Friday, July 10"); see [withLocaleDateCasing].
 */
@Composable
private fun fullWeekdayDate(date: LocalDate): String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(date, locale) {
        val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM")
        date.format(DateTimeFormatter.ofPattern(pattern, locale))
            .withLocaleDateCasing(locale)
    }
}

/**
 * Hero card: the total balance as the screen's dominant figure, a balance
 * sparkline (30 days of history plus a dashed end-of-month forecast tail) and,
 * under a hairline divider, the per-account breakdown. The breakdown shows the
 * first [ACCOUNT_PREVIEW_COUNT] accounts and, when more exist, an expand chevron
 * in the header (mirroring the safe-to-spend card) reveals the rest in place up
 * to [ACCOUNT_EXPANDED_MAX]; beyond that an overflow row points to the full
 * accounts list. Expansion is transient (plain [remember]): it collapses again
 * when the app is reopened. A soft top-down tonal gradient and the larger shape
 * single the card out as the primary one while keeping the dashboard flat.
 *
 * Tap targets are layered: the header chevron toggles the breakdown, each
 * account row opens that account's detail ([onAccountClick]), and every other
 * part of the card opens account management ([onManageAccounts], the spoken
 * affordance of the card itself).
 */
@Composable
internal fun BalanceCard(
    totalBalance: BigDecimal,
    balanceAsOfToday: BigDecimal?,
    currency: Currency,
    accounts: List<AccountWithBalance>,
    history: List<DailyBalance>,
    forecast: List<DailyBalance>,
    date: LocalDate,
    onManageAccounts: () -> Unit,
    onAccountClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val manageAccountsLabel = stringResource(R.string.dashboard_manage_accounts)
    var accountsExpanded by remember { mutableStateOf(false) }
    SaldoCard(
        onClick = onManageAccounts,
        modifier = modifier
            .fillMaxWidth()
            .semantics { onClick(label = manageAccountsLabel, action = null) },
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = HERO_GRADIENT_ALPHA),
                            Color.Transparent,
                        ),
                    ),
                )
                .padding(
                    horizontal = SaldoDimens.cardPaddingLarge,
                    vertical = SaldoDimens.cardPaddingVertical,
                ),
        ) {
            DashboardCardHeader(
                icon = Icons.Outlined.AccountBalanceWallet,
                title = stringResource(R.string.dashboard_balance_total),
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = fullWeekdayDate(date),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                        if (accounts.size > ACCOUNT_PREVIEW_COUNT) {
                            Spacer(Modifier.width(4.dp))
                            AccountsExpandChevron(
                                expanded = accountsExpanded,
                                onToggle = { accountsExpanded = !accountsExpanded },
                            )
                        }
                    }
                },
            )
            Spacer(Modifier.height(BALANCE_AMOUNT_TOP_GAP))
            Text(
                text = animatedBalanceText(totalBalance, currency),
                style = MaterialTheme.typography.headlineMedium.tabularNumbers(),
                fontWeight = FontWeight.SemiBold,
                color = if (totalBalance.signum() < 0) {
                    MaterialTheme.moneyColors.negative
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = BALANCE_MONEY_MIN, maxFontSize = BALANCE_MONEY_MAX),
                modifier = Modifier.fillMaxWidth(),
            )
            if (balanceAsOfToday != null) {
                Spacer(Modifier.height(BALANCE_AMOUNT_TOP_GAP))
                BalanceAsOfTodayLabel(amount = balanceAsOfToday, currency = currency)
            }
            if (history.size > 1) {
                Spacer(Modifier.height(BALANCE_SECTION_GAP))
                BalanceSparkline(
                    history = history,
                    forecast = forecast,
                    currency = currency,
                    lineColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(SPARKLINE_HEIGHT.dp),
                )
                SparklineCaption(hasForecast = forecast.isNotEmpty())
            }
            if (accounts.isNotEmpty()) {
                Spacer(Modifier.height(BALANCE_SECTION_GAP))
                AccountsBreakdownSection(
                    accounts = accounts,
                    primaryCurrency = currency,
                    expanded = accountsExpanded,
                    onAccountClick = onAccountClick,
                    onShowAll = onManageAccounts,
                )
            }
        }
    }
}

/**
 * Secondary line under the hero figure naming the balance as of today. Shown
 * only when future-dated confirmed movements make [totalBalance][BalanceCard]
 * run ahead of what is actually available today (the sparkline's today point),
 * so the two figures the card shows never silently disagree. A muted line with
 * a calendar glyph, one altitude below the headline, no extra emphasis.
 */
@Composable
private fun BalanceAsOfTodayLabel(
    amount: BigDecimal,
    currency: Currency,
    modifier: Modifier = Modifier,
) {
    val amountText = MoneyFormatter.format(amount, currency)
    val label = stringResource(R.string.dashboard_balance_as_of_today, amountText)
    // Red only when today is in the red; otherwise muted, so the line (icon and
    // text together) stays a quiet reference under the headline figure.
    val contentColor = if (amount.signum() < 0) {
        MaterialTheme.moneyColors.negative
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = label },
    ) {
        Icon(
            imageVector = Icons.Outlined.Today,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(BALANCE_TODAY_ICON),
        )
        Spacer(Modifier.width(BALANCE_TODAY_ICON_GAP))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The per-account breakdown under the hero figure: a hairline divider that
 * separates it from the balance and sparkline, then the account rows. When
 * collapsed only the first [ACCOUNT_PREVIEW_COUNT] rows show; when [expanded]
 * the list grows in place (animated) up to [ACCOUNT_EXPANDED_MAX] rows, and if
 * still more accounts remain an overflow row points to the full accounts list.
 */
@Composable
private fun AccountsBreakdownSection(
    accounts: List<AccountWithBalance>,
    primaryCurrency: Currency,
    expanded: Boolean,
    onAccountClick: (Long) -> Unit,
    onShowAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().animateContentSize()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(BALANCE_BREAKDOWN_TOP_GAP))
        val limit = if (expanded) ACCOUNT_EXPANDED_MAX else ACCOUNT_PREVIEW_COUNT
        // Reserve the second amount line for the whole list only when at least
        // one account diverges, so every row stays the same height (no ragged
        // list, no jump) while the common case keeps its compact rows.
        val reserveTodayLine = accounts.any { it.balanceAsOfToday != null }
        accounts.take(limit).forEach { item ->
            AccountBreakdownRow(
                item = item,
                primaryCurrency = primaryCurrency,
                reserveTodayLine = reserveTodayLine,
                onClick = { onAccountClick(item.account.id) },
            )
        }
        val overflow = accounts.size - ACCOUNT_EXPANDED_MAX
        if (expanded && overflow > 0) {
            OverflowAccountsRow(count = overflow, onClick = onShowAll)
        }
    }
}

/**
 * Self-expiring teaser for last month's recap, shown only in the first days
 * of a new month: tap opens the story, the trailing close dismisses it for
 * good (persisted per month). Deliberately not in the dashboard card
 * preferences: it removes itself.
 */
@Composable
internal fun RecapTeaserCard(
    month: YearMonth,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val monthTitle = recapMonthTitle(month)
    SaldoCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(
                start = SaldoDimens.cardPaddingLarge,
                top = 6.dp,
                bottom = 6.dp,
                end = 4.dp,
            ),
        ) {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
            ) {
                Text(
                    text = stringResource(R.string.recap_teaser_title, monthTitle),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.recap_teaser_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.recap_teaser_dismiss),
                )
            }
        }
    }
}

/**
 * The sparkline's legend line: the window label, naming the end-of-month
 * estimate when the dashed tail is shown. The dashed tail carries its own
 * "≈ amount" pill, so no figure is repeated here.
 */
@Composable
private fun SparklineCaption(hasForecast: Boolean, modifier: Modifier = Modifier) {
    Text(
        text = if (hasForecast) {
            stringResource(R.string.dashboard_sparkline_caption_forecast)
        } else {
            stringResource(R.string.dashboard_sparkline_caption)
        },
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * The hero amount with a presentational count-up: the displayed figure sweeps
 * from the previously shown value to the exact target in Long minor units,
 * formatted by [MoneyFormatter] on every frame. Only the in-flight frames are
 * interpolated (Float on the Long delta, display-only); the final frame snaps
 * to the exact stored value, so the money rules are untouched. With system
 * animations off the exact value renders immediately.
 *
 * The displayed value is kept in [rememberSaveable] so it survives the card
 * scrolling out of and back into the LazyColumn: the sweep plays once on the
 * first open (from zero) and then only when the balance actually changes, never
 * replaying from zero on every scroll back into view.
 */
@Composable
private fun animatedBalanceText(totalBalance: BigDecimal, currency: Currency): String {
    if (!rememberMotionEnabled()) return MoneyFormatter.format(totalBalance, currency)
    val target = remember(totalBalance, currency) { MoneyMapper.toMinorUnits(totalBalance, currency) }
    var displayedMinor by rememberSaveable { mutableStateOf(0L) }
    LaunchedEffect(target) {
        val start = displayedMinor
        if (start != target) {
            Animatable(0f).animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = COUNT_UP_MILLIS, easing = FastOutSlowInEasing),
            ) {
                displayedMinor = start + ((target - start) * value).toLong()
            }
            displayedMinor = target
        }
    }
    return MoneyFormatter.format(MoneyMapper.toAmount(displayedMinor, currency), currency)
}

/**
 * One line of the balance breakdown: avatar, name, then (only when relevant)
 * small markers explaining why the row does not feed the headline total, and
 * the balance. An account that does not contribute to the total (its flag is
 * off or it is in a non-primary currency) has its balance muted, so the eye
 * sees at a glance which rows are not part of the big number. The whole row is
 * tappable and opens the account's own detail via [onClick].
 */
@Composable
private fun AccountBreakdownRow(
    item: AccountWithBalance,
    primaryCurrency: Currency,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    reserveTodayLine: Boolean = false,
) {
    val account = item.account
    val color = AccountVisuals.color(account.color)
    val nonPrimaryCurrency = account.currency != primaryCurrency
    val contributesToTotal = account.isIncludedInTotal && !nonPrimaryCurrency
    val openLabel = stringResource(R.string.dashboard_account_open, account.name)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .semantics { onClick(label = openLabel, action = null) }
            // When any account shows the "as of today" line, pin every row to the
            // two-line height so a diverging row never grows taller than its peers.
            .then(if (reserveTodayLine) Modifier.heightIn(min = BALANCE_ROW_TWO_LINE_HEIGHT) else Modifier)
            .padding(vertical = BALANCE_ROW_PADDING_VERTICAL),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(AvatarShape)
                .background(color.copy(alpha = AVATAR_TINT_ALPHA)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = AccountVisuals.icon(account.icon),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text = account.name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        )
        AccountBreakdownMarkers(
            currency = account.currency,
            showCurrencyCode = nonPrimaryCurrency,
            excludedFromTotal = !account.isIncludedInTotal && !nonPrimaryCurrency,
            excludedFromBudget = !account.isIncludedInBudget,
        )
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = MoneyFormatter.format(item.balance, account.currency),
                style = MaterialTheme.typography.bodyLarge.tabularNumbers(),
                color = when {
                    !contributesToTotal -> MaterialTheme.colorScheme.onSurfaceVariant
                    item.balance.signum() < 0 -> MaterialTheme.moneyColors.negative
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
            item.balanceAsOfToday?.let { today ->
                Text(
                    text = stringResource(
                        R.string.dashboard_balance_as_of_today,
                        MoneyFormatter.format(today, account.currency),
                    ),
                    style = MaterialTheme.typography.labelSmall.tabularNumbers(),
                    // Red only when negative (today is in the red); otherwise it
                    // stays muted, so the secondary line keeps its low profile.
                    color = if (today.signum() < 0) {
                        MaterialTheme.moneyColors.negative
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * The header expand/collapse affordance: the same 24dp [Icons.Outlined.ExpandMore]
 * chevron as the safe-to-spend card, columnar in the header's trailing slot and
 * rotating on toggle. It carries its own click so the tap toggles the breakdown
 * instead of falling through to the card's "manage accounts" navigation.
 */
@Composable
private fun AccountsExpandChevron(
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) CHEVRON_EXPANDED_DEGREES else 0f,
        label = "balanceAccountsChevron",
    )
    Icon(
        imageVector = Icons.Outlined.ExpandMore,
        contentDescription = stringResource(
            if (expanded) R.string.dashboard_accounts_collapse else R.string.dashboard_accounts_expand,
        ),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .clip(CircleShape)
            .clickable(onClick = onToggle)
            .rotate(rotation),
    )
}

/**
 * The row closing an expanded breakdown that still hides accounts past
 * [ACCOUNT_EXPANDED_MAX]: a "more" glyph aligned with the account avatars and a
 * muted label, tappable to open the full accounts list. It exists only to keep
 * the hero card bounded when a user holds an unusually large number of accounts.
 */
@Composable
private fun OverflowAccountsRow(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(vertical = BALANCE_ROW_PADDING_VERTICAL),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(36.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.MoreHoriz,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text = pluralStringResource(R.plurals.dashboard_accounts_overflow, count, count),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        )
    }
}

/**
 * The negation-only markers shown between an account's name and its balance.
 * A non-primary currency shows its ISO code (which also explains why the row is
 * out of the total, so the total-excluded icon is suppressed in that case); a
 * flag-excluded account shows a "not in total" icon; a budget-excluded account
 * shows a distinct "not in budget" icon. Nothing is drawn when the account is
 * fully included.
 */
@Composable
private fun AccountBreakdownMarkers(
    currency: Currency,
    showCurrencyCode: Boolean,
    excludedFromTotal: Boolean,
    excludedFromBudget: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!showCurrencyCode && !excludedFromTotal && !excludedFromBudget) return
    Row(
        modifier = modifier.padding(end = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            showCurrencyCode -> CurrencyMarker(currency)
            excludedFromTotal -> MarkerIcon(
                icon = Icons.Outlined.RemoveCircleOutline,
                contentDescription = stringResource(R.string.accounts_excluded_from_total),
            )
        }
        if (excludedFromBudget) {
            MarkerIcon(
                icon = Icons.Outlined.MoneyOff,
                contentDescription = stringResource(R.string.accounts_excluded_from_budget),
            )
        }
    }
}

@Composable
private fun MarkerIcon(icon: ImageVector, contentDescription: String) {
    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(16.dp),
    )
}

/** The ISO code of a non-primary currency, as a subtle pill. */
@Composable
private fun CurrencyMarker(currency: Currency) {
    val description = stringResource(R.string.dashboard_account_other_currency, currency.currencyCode)
    Text(
        text = currency.currencyCode,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .clearAndSetSemantics { contentDescription = description },
    )
}

/**
 * The "Today" and current-month cards, side by side and equal height. Each
 * opens the filtered-transactions drill-down for its own window: the natural
 * question behind the aggregate is "which movements make it up".
 */
@Composable
internal fun PeriodCardsRow(
    date: LocalDate,
    today: PeriodTotals,
    month: PeriodTotals,
    currency: Currency,
    onTodayClick: () -> Unit,
    onMonthClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val monthTitle = remember(date, locale) {
        date.format(DateTimeFormatter.ofPattern("LLLL", locale))
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(SaldoDimens.cardSpacing),
    ) {
        PeriodCompactCard(
            title = stringResource(R.string.dashboard_today),
            icon = Icons.Outlined.Today,
            flow = today,
            currency = currency,
            onClick = onTodayClick,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )
        PeriodCompactCard(
            title = monthTitle,
            icon = Icons.Outlined.CalendarMonth,
            flow = month,
            currency = currency,
            onClick = onMonthClick,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )
    }
}

@Composable
private fun PeriodCompactCard(
    title: String,
    icon: ImageVector,
    flow: PeriodTotals,
    currency: Currency,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SaldoCard(
        onClick = onClick,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = SaldoDimens.cardPadding,
                    vertical = SaldoDimens.cardPaddingVertical,
                ),
        ) {
            DashboardCardHeader(icon = icon, title = title)
            Spacer(Modifier.height(4.dp))
            Text(
                text = MoneyFormatter.formatSigned(flow.net, currency),
                style = MaterialTheme.typography.titleLarge.tabularNumbers(),
                fontWeight = FontWeight.SemiBold,
                color = netColor(flow.net),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                autoSize = TextAutoSize.StepBased(minFontSize = COMPACT_MONEY_MIN, maxFontSize = COMPACT_MONEY_MAX),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            StatLine(
                label = stringResource(R.string.dashboard_stat_expenses),
                value = MoneyFormatter.formatSigned(flow.spend, currency),
            )
            Spacer(Modifier.height(2.dp))
            StatLine(
                label = stringResource(R.string.dashboard_stat_incomes),
                value = MoneyFormatter.formatSigned(flow.income, currency),
            )
        }
    }
}

@Composable
private fun StatLine(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // Takes the leftover width so the amount is pushed to the row's end:
            // the two stat rows share a right edge and their tabular figures
            // stay columnar.
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.size(6.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.tabularNumbers(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Standalone reference line: how much had been spent by this day last month. */
@Composable
internal fun MonthComparisonRow(
    previousSpend: BigDecimal,
    spentMore: Boolean,
    currency: Currency,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (spentMore) {
                Icons.AutoMirrored.Outlined.TrendingUp
            } else {
                Icons.AutoMirrored.Outlined.TrendingDown
            },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = stringResource(
                R.string.dashboard_month_comparison,
                MoneyFormatter.format(previousSpend, currency),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Attention card shown when recurring movements await confirmation. */
@Composable
internal fun PendingConfirmationCard(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SaldoCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = SaldoDimens.cardPadding,
                vertical = SaldoDimens.cardPaddingVertical,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(AvatarShape)
                    .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.NotificationsActive,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp),
            ) {
                Text(
                    text = pluralStringResource(R.plurals.dashboard_pending_title, count, count),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    text = stringResource(R.string.dashboard_pending_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
    }
}

/**
 * Dashboard card for credit card statements waiting to be paid (confirm mode):
 * the amount owed and a tap-through to the accounts screen, where the statement
 * is settled. Auto-post cards never appear here (they are charged on their own).
 */
@Composable
internal fun StatementDueCard(
    statements: List<com.callbackdev.saldo.core.domain.usecase.DueStatement>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (statements.isEmpty()) return
    val single = statements.singleOrNull()
    val total = statements.fold(java.math.BigDecimal.ZERO) { acc, statement -> acc.add(statement.amount) }
    val currency = statements.first().currency
    SaldoCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = SaldoDimens.cardPadding,
                vertical = SaldoDimens.cardPaddingVertical,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(AvatarShape)
                    .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.CreditCard,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp),
            ) {
                Text(
                    text = if (single != null) {
                        stringResource(R.string.dashboard_statement_title_single, single.cardName)
                    } else {
                        pluralStringResource(R.plurals.dashboard_statement_title, statements.size, statements.size)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    text = stringResource(
                        R.string.dashboard_statement_subtitle,
                        MoneyFormatter.format(total, currency),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
    }
}

/**
 * Dashboard card for recurring transactions: monthly expense and income totals
 * side by side, plus the next upcoming charge or credit across both types.
 */
@Composable
internal fun RecurringCard(
    summary: RecurringSummary,
    currency: Currency,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SaldoCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = SaldoDimens.cardPadding,
                vertical = SaldoDimens.cardPaddingVertical,
            ),
        ) {
            DashboardCardHeader(
                icon = Icons.Outlined.EventRepeat,
                title = stringResource(R.string.dashboard_recurring_title),
            )
            if (summary.hasRules) {
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    RecurringMetric(
                        label = stringResource(R.string.dashboard_recurring_expenses_label),
                        value = MoneyFormatter.formatSigned(summary.monthlyExpenses.negate(), currency),
                        color = if (summary.monthlyExpenses.signum() > 0) {
                            MaterialTheme.moneyColors.negative
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.weight(1f),
                    )
                    RecurringMetric(
                        label = stringResource(R.string.dashboard_recurring_incomes_label),
                        value = MoneyFormatter.formatSigned(summary.monthlyIncomes, currency),
                        color = if (summary.monthlyIncomes.signum() > 0) {
                            MaterialTheme.moneyColors.income
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (summary.monthlyTransfersToSavings.signum() > 0) {
                    Spacer(Modifier.height(8.dp))
                    RecurringSavingsLine(amount = summary.monthlyTransfersToSavings, currency = currency)
                }
                summary.next?.let { next ->
                    Spacer(Modifier.height(8.dp))
                    NextRecurringEventLine(next = next)
                }
            } else {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.dashboard_recurring_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RecurringMetric(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.tabularNumbers(),
            fontWeight = FontWeight.SemiBold,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Planned-savings line: the monthly-equivalent of recurring transfers into savings. */
@Composable
private fun RecurringSavingsLine(amount: BigDecimal, currency: Currency, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.dashboard_recurring_savings_label),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = MoneyFormatter.format(amount, currency),
            style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.moneyColors.income,
        )
    }
}

@Composable
private fun NextRecurringEventLine(next: NextRecurringEvent, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val dateText = remember(next.date, locale) {
        val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, "dMMM")
        next.date.format(DateTimeFormatter.ofPattern(pattern, locale))
            .withLocaleDateCasing(locale)
    }
    Text(
        text = stringResource(
            R.string.dashboard_recurring_next,
            next.name,
            MoneyFormatter.formatSigned(next.amount, next.currency),
            dateText,
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** Recent movements as a single grouped card with flat, tappable rows. */
@Composable
internal fun RecentMovementsCard(
    items: List<TransactionListItem>,
    onItemClick: (Long) -> Unit,
    today: LocalDate,
    modifier: Modifier = Modifier,
) {
    SaldoCard(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            items.forEachIndexed { index, item ->
                if (index > 0) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                Surface(
                    onClick = { onItemClick(item.id) },
                    color = Color.Transparent,
                ) {
                    TransactionRowContent(
                        item = item,
                        modifier = Modifier.padding(
                            horizontal = SaldoDimens.rowPaddingHorizontal,
                            vertical = SaldoDimens.rowPaddingVertical,
                        ),
                        dateLabel = compactDayLabel(item.transaction.localDate, today),
                    )
                }
            }
        }
    }
}

@Composable
private fun netColor(value: BigDecimal): Color = when {
    value.signum() > 0 -> MaterialTheme.moneyColors.income
    else -> MaterialTheme.colorScheme.onSurface
}

private const val AVATAR_TINT_ALPHA = 0.16f

// Money figures auto-size within these bounds so large amounts shrink to fit
// instead of truncating, while typical values keep the target size. Hero: the
// safe-to-spend figure on its full-width card; compact: the half-width
// Today/month and budget figures. Shared by BudgetDashboardCards.
internal val HERO_MONEY_MIN = 20.sp
internal val HERO_MONEY_MAX = 28.sp

// The total-balance figure is the screen's primary number and outranks even the
// safe-to-spend hero, so it auto-sizes within a larger band of its own.
private val BALANCE_MONEY_MIN = 24.sp
private val BALANCE_MONEY_MAX = 34.sp

// Vertical rhythm inside the balance card: a tight gap under the header before
// the figure, a wider gap between its sections (figure / sparkline / accounts),
// and a smaller one under the breakdown divider.
private val BALANCE_AMOUNT_TOP_GAP = 4.dp
private val BALANCE_SECTION_GAP = 12.dp
private val BALANCE_BREAKDOWN_TOP_GAP = 8.dp

/** Size of the calendar glyph and its gap on the "as of today" secondary line. */
private val BALANCE_TODAY_ICON = 16.dp
private val BALANCE_TODAY_ICON_GAP = 4.dp

/** Vertical padding of a tappable account (or overflow) row in the breakdown. */
private val BALANCE_ROW_PADDING_VERTICAL = 6.dp

// Height a breakdown row is pinned to while the "as of today" line can appear,
// so a diverging (two-line) row never grows past its single-line peers: the
// bodyLarge amount and the labelSmall today line plus the row's vertical padding.
private val BALANCE_ROW_TWO_LINE_HEIGHT = 52.dp

/** How many accounts the collapsed breakdown shows before the expand chevron. */
private const val ACCOUNT_PREVIEW_COUNT = 2

/** How many accounts the expanded breakdown shows before the overflow row. */
private const val ACCOUNT_EXPANDED_MAX = 10

/** Rotation of the expand chevron when the breakdown is open. */
private const val CHEVRON_EXPANDED_DEGREES = 180f

/** Height of the hero card's balance sparkline, in dp. */
private const val SPARKLINE_HEIGHT = 56

/** Opacity of the hero card's decorative tonal gradient. */
private const val HERO_GRADIENT_ALPHA = 0.35f

/** Duration of the hero balance count-up. */
private const val COUNT_UP_MILLIS = 700
internal val COMPACT_MONEY_MIN = 14.sp
internal val COMPACT_MONEY_MAX = 22.sp
