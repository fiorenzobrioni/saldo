package com.callbackdev.saldo.feature.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.text.LineBreaker
import android.net.Uri
import android.text.StaticLayout
import android.text.TextPaint
import android.text.format.DateFormat
import android.util.SizeF
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.RemoteViews
import com.callbackdev.saldo.MainActivity
import com.callbackdev.saldo.R
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Currency
import java.util.Locale
import kotlin.math.ceil
import com.callbackdev.saldo.core.common.money.MoneyFormatter
import com.callbackdev.saldo.core.domain.model.BudgetLevel

/** The two month widgets (ADR 54). */
enum class MonthWidgetKind { WORDS, RING }

/**
 * Builds the `RemoteViews` of the month widgets (ADR 54): «In words», a
 * sentence about the month and the facts that fit, and «The month», the ring of
 * the overall budget. Same card, inks and type scale as the quick-add widget
 * (ADR 52), through the same [WidgetPalette].
 *
 * Every figure goes through [MoneyFormatter] and wears its `≈` when it includes
 * a converted amount (ADR 40). With [MonthWidgetData.amountsAllowed] false (the
 * app lock is on) or the card's own switch off, the cards say the same things
 * without an amount: a share, a day count, a charge's name and day.
 */
internal object MonthWidgetRenderer {

    fun sizeMap(
        context: Context,
        kind: MonthWidgetKind,
        data: MonthWidgetData,
        config: MonthWidgetConfig,
        palette: WidgetPalette,
        sizes: List<WidgetSize>,
    ): RemoteViews = RemoteViews(
        sizes.associate { size ->
            SizeF(size.width, size.height) to render(context, kind, data, config, palette, size)
        },
    )

    fun render(
        context: Context,
        kind: MonthWidgetKind,
        data: MonthWidgetData,
        config: MonthWidgetConfig,
        palette: WidgetPalette,
        size: WidgetSize,
    ): RemoteViews = when {
        !data.hasAccounts -> setup(context, palette, size, R.string.widget_quick_add_setup, openApp(context, OPEN_APP))
        kind == MonthWidgetKind.RING && data.budget == null ->
            setup(context, palette, size, R.string.widget_month_ring_setup, openBudgets(context))
        kind == MonthWidgetKind.RING -> ring(context, data, config, palette, size, checkNotNull(data.budget))
        else -> words(context, data, config, palette, size)
    }

    // ---- «In words» ----

    private fun words(
        context: Context,
        data: MonthWidgetData,
        config: MonthWidgetConfig,
        palette: WidgetPalette,
        size: WidgetSize,
    ): RemoteViews = RemoteViews(context.packageName, R.layout.widget_month_words).apply {
        val form = wordsForm(size)
        val text = MonthText(context, data, config.showAmounts)
        val measure = MonthTextMeasure(context)
        val tall = form == WordsForm.PANEL
        paintBackground(palette)
        setInt(R.id.widget_words_column, "setGravity", if (tall) Gravity.TOP else Gravity.CENTER_VERTICAL)
        if (!tall) setRowPadding(context, R.id.widget_words_column)
        val width = size.width - 2 * MonthInset
        val words = monthWords(data, config.showAmounts)
        // In reading order, each line kept only if it fits whole after the
        // ones before it: what does not fit is what is read last. The facts
        // and the next charge only on a tall card.
        val month = CardLine(text.monthName().takeIf { tall }, WidgetType.LABEL, after = MonthGap)
        val sentence = CardLine(
            text = pickSentence(text, words, form, width, size, tall, measure),
            sp = WidgetType.BODY,
            medium = true,
        )
        val facts = if (tall) text.facts(words, wide = size.width >= MonthWideMin) else emptyList()
        val tail = (facts + listOfNotNull(text.next(words, config.showNextCharge)?.takeIf { tall }))
            .mapIndexed { index, fact ->
                CardLine(fact, WidgetType.LABEL, maxLines = MaxFactLines, before = if (index == 0) FactGap else 0f)
            }
        val room = size.height - 2 * if (tall) MonthInset else MonthRowInset
        val all = listOf(month, sentence.copy(maxLines = Int.MAX_VALUE)) + tail
        val kept = fitInOrder(all, width, room, measure, keepSecond = true)

        bind(R.id.widget_words_month, month.text, kept[0], palette.onSurfaceVariant)
        bind(R.id.widget_words_sentence, sentence.text, kept[1], palette.onSurface)
        FactIds.forEachIndexed { index, id ->
            val line = tail.getOrNull(index)?.takeIf { index < facts.size }
            bind(id, line?.text, kept.getOrNull(2 + index), palette.onSurfaceVariant, context.px(line?.before ?: 0f))
        }
        val nextLine = tail.getOrNull(facts.size)
        // The one line allowed to end in an ellipsis all the same: it carries
        // a name the reader typed, which may be longer than any card.
        bind(
            R.id.widget_words_next,
            nextLine?.text,
            kept.getOrNull(2 + facts.size),
            palette.onSurfaceVariant,
            context.px(nextLine?.before ?: 0f),
        )
        setOnClickPendingIntent(R.id.widget_root, openApp(context, OPEN_APP))
    }

    /** The long sentence where it fits whole, else the short one: never a sentence cut in the middle. */
    private fun pickSentence(
        text: MonthText,
        words: MonthWords,
        form: WordsForm,
        width: Float,
        size: WidgetSize,
        tall: Boolean,
        measure: MonthTextMeasure,
    ): String {
        val room = size.height - 2 * (if (tall) MonthInset else MonthRowInset) -
            if (tall) measure.height(text.monthName(), WidgetType.LABEL, width) + MonthGap else 0f
        val candidates = listOfNotNull(text.sentence(words).takeIf { form != WordsForm.SHORT }, text.short(words))
        return candidates.firstOrNull { measure.height(it, WidgetType.BODY, width, medium = true) <= room }
            ?: candidates.last()
    }

    // ---- «The month» ----

    private fun ring(
        context: Context,
        data: MonthWidgetData,
        config: MonthWidgetConfig,
        palette: WidgetPalette,
        size: WidgetSize,
        budget: MonthBudget,
    ): RemoteViews {
        val text = MonthText(context, data, config.showAmounts)
        val measure = MonthTextMeasure(context)
        val lines = text.ringLines(budget, ringLayout(size).form)
        // The square keeps under the ring what its two lines really take at
        // this text size, and the ring has the rest.
        val caption = SquareGap + lines.take(SquareLines).sumOf {
            measure.height(it, WidgetType.LABEL, size.width - 2 * MonthInset, medium = it == lines.first()).toDouble()
        }.toFloat()
        val layout = ringLayout(size, captionDp = ceil(caption).toInt())
        val square = layout.form == RingForm.SQUARE
        val resource = if (square) R.layout.widget_month_ring_square else R.layout.widget_month_ring
        return RemoteViews(context.packageName, resource)
            .apply {
                paintBackground(palette)
                if (layout.form == RingForm.ROW) setRowPadding(context, R.id.widget_month_content)
                // A row with no room for words is the ring alone, in the middle.
                if (layout.form == RingForm.ROW && !layout.showText) {
                    setInt(R.id.widget_month_content, "setGravity", Gravity.CENTER)
                }
                paintRing(data, budget, layout, text.percent(budget.percent), palette, measure)
                bindRingText(lines, layout, size, caption, palette, measure)
                setContentDescription(R.id.widget_root, text.ringDescription(budget))
                setOnClickPendingIntent(R.id.widget_root, openApp(context, OPEN_APP))
            }
    }

    /** The ring at its side: the spend, the month gone by, the track, and the share in the hole. */
    private fun RemoteViews.paintRing(
        data: MonthWidgetData,
        budget: MonthBudget,
        layout: RingLayout,
        percent: String,
        palette: WidgetPalette,
        measure: MonthTextMeasure,
    ) {
        val over = budget.level == BudgetLevel.OVER
        setViewLayoutWidth(R.id.widget_ring_box, layout.ringSide.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
        setViewLayoutHeight(R.id.widget_ring_box, layout.ringSide.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
        setProgressBar(R.id.widget_ring, RingMax, (budget.fraction.coerceIn(0f, 1f) * RingMax).toInt(), false)
        setInt(R.id.widget_ring, "setSecondaryProgress", (data.monthElapsed * RingMax).toInt())
        setTint(R.id.widget_ring, "setProgressTintList", if (over) palette.expenseInk else palette.moreInk)
        setTint(R.id.widget_ring, "setSecondaryProgressTintList", palette.onSurfaceVariant)
        setTint(R.id.widget_ring, "setProgressBackgroundTintList", palette.onSurfaceVariant.withAlpha(TrackAlpha))
        setTextViewText(R.id.widget_ring_percent, percent)
        setColor(R.id.widget_ring_percent, "setTextColor", if (over) palette.expenseInk else palette.onSurface)
        setTextViewTextSize(R.id.widget_ring_percent, TypedValue.COMPLEX_UNIT_SP, percentSize(layout, percent, measure))
    }

    /**
     * The ring's words, each whole on its lines and kept while they fit, in
     * reading order: beside the ring in a row or a panel, under it in a square.
     */
    private fun RemoteViews.bindRingText(
        lines: List<String>,
        layout: RingLayout,
        size: WidgetSize,
        caption: Float,
        palette: WidgetPalette,
        measure: MonthTextMeasure,
    ) {
        val panel = layout.form == RingForm.PANEL
        val titleSize = when (layout.form) {
            RingForm.PANEL -> WidgetType.TITLE
            RingForm.ROW -> WidgetType.BODY
            RingForm.SQUARE -> WidgetType.LABEL
        }
        if (panel) setTextViewTextSize(R.id.widget_ring_title, TypedValue.COMPLEX_UNIT_SP, titleSize)
        val vertical = if (layout.form == RingForm.ROW) MonthRowInset else MonthInset
        val (width, room) = if (layout.form == RingForm.SQUARE) {
            (size.width - 2 * MonthInset) to caption
        } else {
            (size.width - 2 * MonthInset - layout.ringSide - RingTextGap) to (size.height - 2 * vertical)
        }
        val cardLines = lines.mapIndexed { index, line ->
            CardLine(
                text = line.takeIf { layout.showText },
                sp = if (index == 0) titleSize else WidgetType.LABEL,
                medium = index == 0,
                maxLines = if (panel) PanelLineLines else 1,
            )
        }
        val kept = fitInOrder(cardLines, width, room, measure)
        setViewVisibility(R.id.widget_ring_text, layout.showText.asVisibility())
        RingTextIds.forEachIndexed { index, id ->
            bind(
                id,
                cardLines.getOrNull(index)?.text,
                kept.getOrNull(index),
                if (index == 0) palette.onSurface else palette.onSurfaceVariant,
            )
        }
    }

    /** Shows [text] on its [lines] in [color], or hides the view when it was not kept. */
    private fun RemoteViews.bind(id: Int, text: String?, lines: Int?, color: WidgetColor, top: Int = 0) {
        val shown = text != null && lines != null
        setViewVisibility(id, shown.asVisibility())
        if (shown) {
            setTextViewText(id, text)
            setInt(id, "setMaxLines", checkNotNull(lines))
            setColor(id, "setTextColor", color)
            setViewPadding(id, 0, top, 0, 0)
        }
    }

    private fun RemoteViews.setRowPadding(context: Context, id: Int) {
        val side = context.px(MonthInset)
        val row = context.px(MonthRowInset)
        setViewPadding(id, side, row, side, row)
    }

    /**
     * The share inside the ring, as large as the ring's hole holds: the
     * largest step of the scale (up to the form's) whose width stays inside
     * the thin month band, so «103%» never touches it.
     */
    private fun percentSize(layout: RingLayout, percent: String, measure: MonthTextMeasure): Float {
        val largest = when {
            layout.form == RingForm.SQUARE && layout.ringSide >= DisplayRingMin -> WidgetType.DISPLAY
            layout.form != RingForm.ROW -> WidgetType.TITLE
            else -> WidgetType.BODY
        }
        val hole = layout.ringSide * HoleShare
        return listOf(WidgetType.DISPLAY, WidgetType.TITLE, WidgetType.BODY, WidgetType.LABEL)
            .filter { it <= largest }
            .firstOrNull { measure.width(percent, it, medium = true) <= hole }
            ?: WidgetType.CAPTION
    }

    // ---- The card with nothing to show yet ----

    private fun setup(
        context: Context,
        palette: WidgetPalette,
        size: WidgetSize,
        message: Int,
        tap: PendingIntent,
    ): RemoteViews = RemoteViews(context.packageName, R.layout.widget_quick_setup).apply {
        paintBackground(palette)
        val compact = size.height < MonthTallMin
        setInt(
            R.id.widget_setup_column,
            "setGravity",
            if (compact) Gravity.CENTER else Gravity.TOP or Gravity.CENTER_HORIZONTAL,
        )
        setViewVisibility(R.id.widget_setup_mark, if (compact) View.GONE else View.VISIBLE)
        if (!compact) {
            setImageViewBitmap(
                R.id.widget_setup_mark,
                CategoryIconBitmaps.appMark(context, AppShortcutIcon, context.px(SetupMarkSize)),
            )
        }
        setTextViewText(R.id.widget_setup_text, context.getString(message))
        setColor(R.id.widget_setup_text, "setTextColor", palette.onSurfaceVariant)
        setOnClickPendingIntent(R.id.widget_root, tap)
    }

    // ---- Shared ----

    private fun RemoteViews.paintBackground(palette: WidgetPalette) {
        setColor(R.id.widget_background, "setColorFilter", palette.background)
        setInt(R.id.widget_background, "setImageAlpha", palette.backgroundAlpha)
    }

    private fun RemoteViews.setColor(viewId: Int, method: String, color: WidgetColor) {
        setColorInt(viewId, method, color.light, color.dark)
    }

    private fun RemoteViews.setTint(viewId: Int, method: String, color: WidgetColor) {
        setColorStateList(viewId, method, ColorStateList.valueOf(color.light), ColorStateList.valueOf(color.dark))
    }

    private fun WidgetColor.withAlpha(alpha: Int): WidgetColor = WidgetColor(
        light = (light and RgbMask) or (alpha shl AlphaShift),
        dark = (dark and RgbMask) or (alpha shl AlphaShift),
    )

    private fun openApp(context: Context, requestKey: Int): PendingIntent = PendingIntent.getActivity(
        context,
        requestKey,
        Intent(context, MainActivity::class.java)
            .setData(Uri.parse("saldo://widget/month/$requestKey"))
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP,
            ),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** The ring's invitation opens the budgets, where the monthly one is set. */
    private fun openBudgets(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        OPEN_BUDGETS,
        Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN_BUDGETS)
            .setData(Uri.parse("saldo://widget/month/budgets"))
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP,
            ),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun Boolean.asVisibility(): Int = if (this) View.VISIBLE else View.GONE

    private fun Context.px(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    private fun Context.px(dp: Float): Int = (dp * resources.displayMetrics.density).toInt()

    private val FactIds = listOf(R.id.widget_words_fact1, R.id.widget_words_fact2)
    private val RingTextIds = listOf(R.id.widget_ring_title, R.id.widget_ring_line1, R.id.widget_ring_line2)

    private const val RingMax = 1000

    /** The track: the quiet ink at a quarter, present but under the arc. */
    private const val TrackAlpha = 0x40
    private const val RgbMask = 0x00FFFFFF
    private const val AlphaShift = 24

    private const val DisplayRingMin = 110

    /** The text's room inside the ring: the thin band's inner edge is at 0.59 of the side, less a margin. */
    private const val HoleShare = 0.48f
    private const val SquareLines = 2

    /** The gap between a square's ring and its lines (the layout's marginTop). */
    private const val SquareGap = 6f

    /** The ring's words start this far from the ring (the layout's marginStart). */
    private const val RingTextGap = 12

    /** A panel's line may take two lines; a row's and a square's one. */
    private const val PanelLineLines = 2

    /** Under the month's name (the layout's marginBottom). */
    private const val MonthGap = 2f

    /** Over the first fact. */
    private const val FactGap = 6f

    /** A narrow card's fact (a figure pair) may wrap once; more would be a paragraph. */
    private const val MaxFactLines = 2
    private const val SetupMarkSize = 36

    private const val OPEN_APP = 40
    private const val OPEN_BUDGETS = 41
}

/**
 * The words of the month cards, in the reader's language: the sentence for a
 * [MonthWords], the facts under it, the ring's lines. Kept apart from the views
 * so the renderer only places what this says.
 */
private class MonthText(
    private val context: Context,
    private val data: MonthWidgetData,
    showAmounts: Boolean,
) {
    private val locale: Locale = context.resources.configuration.locales[0]
    private val amounts = showAmounts && data.amountsAllowed

    private fun money(amount: BigDecimal, currency: Currency = data.currency): String =
        if (data.includesEstimates && currency == data.currency) {
            MoneyFormatter.formatApprox(amount, currency, locale)
        } else {
            MoneyFormatter.format(amount, currency, locale)
        }

    private fun string(id: Int, vararg args: Any): String = context.getString(id, *args)

    /** «Ottobre»: the month's standalone name, capitalised as a heading. */
    fun monthName(): String = data.month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale)
        .replaceFirstChar { it.titlecase(locale) }

    /** The month's name as it reads inside a sentence («ottobre» in Italian, «October» in English). */
    private fun monthInSentence(): String = data.month.month.getDisplayName(TextStyle.FULL, locale)

    fun percent(value: Int): String = string(R.string.widget_month_percent, value)

    fun sentence(words: MonthWords): String = when (words) {
        is MonthWords.CanSpend -> when {
            data.daysLeft == 1 -> string(R.string.widget_words_can_spend_last_day, money(words.amount))
            words.perDay == null -> string(R.string.widget_words_can_spend_plain, money(words.amount))
            else -> string(R.string.widget_words_can_spend, money(words.amount), money(words.perDay))
        }

        is MonthWords.Over -> string(R.string.widget_words_over, money(words.amount))
        is MonthWords.UsedPercent -> string(R.string.widget_words_used, words.percent)
        MonthWords.OverHidden -> string(R.string.widget_words_over_hidden)
        is MonthWords.Spent -> string(R.string.widget_words_spent, money(words.amount))
        is MonthWords.NextOnly -> string(R.string.widget_words_next_only, words.next.name, whenOf(words.next.date))
        MonthWords.Quiet -> string(R.string.widget_words_quiet)
        MonthWords.Setup -> string(R.string.widget_quick_add_setup)
    }

    /** The sentence a narrow row has room for: the figure that matters, in a few words. */
    fun short(words: MonthWords): String = when (words) {
        is MonthWords.CanSpend -> string(R.string.widget_words_short_can_spend, money(words.amount))
        is MonthWords.Over -> string(R.string.widget_words_short_over, money(words.amount))
        is MonthWords.UsedPercent -> string(R.string.widget_words_short_used, words.percent)
        MonthWords.OverHidden -> string(R.string.widget_words_short_over_hidden)
        is MonthWords.Spent -> string(R.string.widget_words_short_spent, money(words.amount))
        is MonthWords.NextOnly -> string(R.string.widget_words_short_next, words.next.name, whenOf(words.next.date))
        MonthWords.Quiet -> string(R.string.widget_words_short_quiet)
        MonthWords.Setup -> string(R.string.widget_quick_add_setup)
    }

    /**
     * The facts under the sentence, in the order they are read: the budget's
     * two figures, then the days left (in their short form on a narrow card).
     */
    fun facts(words: MonthWords, wide: Boolean): List<String> = buildList {
        if (words is MonthWords.Setup) return@buildList
        val budget = data.budget
        if (budget != null && amounts) {
            add(string(R.string.widget_month_spent_of, money(budget.spent), money(budget.limit)))
        }
        add(if (wide) daysLeft() else shortDaysLeft())
    }

    /** The next charge as the last fact, unless the sentence already is it or the card hides it. */
    fun next(words: MonthWords, show: Boolean): String? {
        val next = data.next ?: return null
        if (!show || words is MonthWords.NextOnly || words is MonthWords.Setup) return null
        return nextFact(next)
    }

    private fun daysLeft(): String =
        context.resources.getQuantityString(R.plurals.widget_month_days_left, data.daysLeft, data.daysLeft)

    private fun nextFact(next: NextCharge): String {
        val amount = next.amount?.takeIf { amounts }
        return if (amount != null) {
            string(R.string.widget_words_fact_next_amount, next.name, whenOf(next.date), money(amount, next.currency))
        } else {
            string(R.string.widget_words_fact_next, next.name, whenOf(next.date))
        }
    }

    /** «oggi», «domani», a weekday within the week, else the day and the month, short. */
    private fun whenOf(date: LocalDate): String {
        val days = ChronoUnit.DAYS.between(data.today, date)
        return when {
            days <= 0L -> string(R.string.widget_month_today)
            days == 1L -> string(R.string.widget_month_tomorrow)
            days < WEEK -> date.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
            else -> date.format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "dMMM"), locale))
        }
    }

    /**
     * The ring's lines by form: a heading, then what the ring measures, then
     * the days. A row and a square have a narrow column, so their heading is
     * the figure alone («960 €» over «su 1.500 €»), where a panel says it in
     * words («Spesi 960 €» over «su un budget di 1.500 €»).
     */
    fun ringLines(budget: MonthBudget, form: RingForm): List<String> {
        val over = budget.level == BudgetLevel.OVER
        val limit = money(budget.limit)
        val panel = form == RingForm.PANEL
        return if (amounts) {
            listOf(
                if (panel) string(R.string.widget_ring_spent, money(budget.spent)) else money(budget.spent),
                when {
                    panel && over -> string(R.string.widget_ring_over_budget, limit)
                    panel -> string(R.string.widget_ring_of_budget, limit)
                    over -> string(R.string.widget_ring_over_short, limit)
                    else -> string(R.string.widget_ring_of_short, limit)
                },
                shortDaysLeft(),
            )
        } else {
            val heading = when {
                over -> string(R.string.widget_ring_over)
                panel -> string(R.string.widget_ring_month_budget, monthInSentence())
                else -> monthName()
            }
            listOf(heading, shortDaysLeft())
        }
    }

    private fun shortDaysLeft(): String =
        context.resources.getQuantityString(R.plurals.widget_month_days_left_short, data.daysLeft, data.daysLeft)

    /** What a screen reader says for the ring: the drawing has no words of its own. */
    fun ringDescription(budget: MonthBudget): String = string(
        R.string.widget_ring_description,
        monthInSentence(),
        budget.percent,
        daysLeft(),
    )

    private companion object {
        const val WEEK = 7L
    }
}

/**
 * Measures the month cards' words the way the launcher will lay them out: a
 * [StaticLayout] at the card's text size, the reader's font scale, the same
 * typeface and break strategy as a `TextView`, on [FitMargin] of the width so a
 * launcher's font a little wider than this process's still fits. The renderer
 * keeps a line only where it fits whole: a `RemoteViews` has no measuring pass
 * of its own, and a text the host cuts is cut without a word.
 */
internal class MonthTextMeasure(context: Context) {
    private val density = context.resources.displayMetrics.density
    private val scaled = density * context.resources.configuration.fontScale
    private val regular = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT }
    private val medium = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    private fun layout(text: String, sp: Float, widthDp: Float, medium: Boolean): StaticLayout {
        val paint = if (medium) this.medium else regular
        paint.textSize = sp * scaled
        val width = (widthDp * FitMargin * density).toInt().coerceAtLeast(1)
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setIncludePad(true)
            .setBreakStrategy(LineBreaker.BREAK_STRATEGY_HIGH_QUALITY)
            .build()
    }

    fun lines(text: String, sp: Float, widthDp: Float, medium: Boolean = false): Int =
        layout(text, sp, widthDp, medium).lineCount

    /** The height of [text]'s first [maxLines] lines, in dp. */
    fun height(text: String, sp: Float, widthDp: Float, medium: Boolean = false, maxLines: Int = Int.MAX_VALUE): Float {
        val layout = layout(text, sp, widthDp, medium)
        val shown = minOf(layout.lineCount, maxLines).coerceAtLeast(1)
        return layout.getLineBottom(shown - 1) / density
    }

    /** How many of [text]'s lines fit in [roomDp], at least one. */
    fun linesWithin(text: String, sp: Float, widthDp: Float, roomDp: Float, medium: Boolean = false): Int {
        val layout = layout(text, sp, widthDp, medium)
        return (1..layout.lineCount).lastOrNull { layout.getLineBottom(it - 1) / density <= roomDp } ?: 1
    }

    /** [text] on one line, in dp, with the same margin as the line breaks. */
    fun width(text: String, sp: Float, medium: Boolean = false): Float {
        val paint = if (medium) this.medium else regular
        paint.textSize = sp * scaled
        return paint.measureText(text) / density / FitMargin
    }

    private companion object {
        /** A launcher's font may run a few percent wider than this process's: measure on less. */
        const val FitMargin = 0.9f
    }
}

/** One line of a card, before it is measured: its words, its size, the room around it. */
private data class CardLine(
    val text: String?,
    val sp: Float,
    val medium: Boolean = false,
    val maxLines: Int = 1,
    /** Space over the line. */
    val before: Float = 0f,
    /** Space under the line. */
    val after: Float = 0f,
)

/**
 * Which of [lines] fit in [room], in reading order: each whole, on at most its
 * own lines; once one does not fit, none after it, so what is dropped is what
 * is read last. The line count of each kept line, null for the rest. With
 * [keepSecond] the second line (a card's sentence) is kept on the lines that
 * fit even when it would not fit whole, since its caller has already chosen
 * the version that does.
 */
private fun fitInOrder(
    lines: List<CardLine>,
    width: Float,
    room: Float,
    measure: MonthTextMeasure,
    keepSecond: Boolean = false,
): List<Int?> {
    var left = room
    var open = true
    return lines.mapIndexed { index, line ->
        val text = line.text ?: return@mapIndexed null
        val whole = measure.lines(text, line.sp, width, line.medium)
        val count = if (keepSecond && index == 1) {
            measure.linesWithin(text, line.sp, width, left - line.before, line.medium)
        } else {
            whole
        }
        val cost = line.before + line.after + measure.height(text, line.sp, width, line.medium, maxLines = count)
        if (open && count <= line.maxLines && cost <= left) {
            left -= cost
            count
        } else {
            open = false
            null
        }
    }
}
