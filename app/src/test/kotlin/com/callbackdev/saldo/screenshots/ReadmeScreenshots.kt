package com.callbackdev.saldo.screenshots

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.printToString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.common.prefs.ThemePreferences
import com.callbackdev.saldo.core.designsystem.theme.SaldoTheme
import com.callbackdev.saldo.core.designsystem.theme.WidgetCardColor
import com.callbackdev.saldo.core.domain.model.CategoryType
import com.callbackdev.saldo.core.domain.model.TransactionType
import com.callbackdev.saldo.core.domain.repository.AccountRepository
import com.callbackdev.saldo.core.domain.repository.CategoryRepository
import com.callbackdev.saldo.feature.guide.GuideScreen
import com.callbackdev.saldo.feature.rates.ExchangeRatesScreen
import com.callbackdev.saldo.feature.recurring.RecurrencesScreen
import com.callbackdev.saldo.feature.widget.ActionSizes
import com.callbackdev.saldo.feature.widget.QuickAddWidgetConfig
import com.callbackdev.saldo.feature.widget.QuickAddWidgetConfigScreen
import com.callbackdev.saldo.feature.widget.QuickAddWidgetConfigUiState
import com.callbackdev.saldo.feature.widget.QuickAddWidgetDataLoader
import com.callbackdev.saldo.feature.widget.QuickEntryRoute
import com.callbackdev.saldo.feature.widget.QuickEntrySheet
import com.callbackdev.saldo.feature.widget.QuickEntryViewModel
import com.callbackdev.saldo.feature.widget.WidgetRenderer
import com.callbackdev.saldo.feature.widget.WidgetSize
import com.callbackdev.saldo.feature.widget.resolveWidgetTheme
import com.callbackdev.saldo.feature.widget.wideGridHeight
import com.callbackdev.saldo.navigation.SaldoApp
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

/**
 * The README's pictures (docs/screenshots), drawn from the real app on the
 * [SampleLedger]. Run with `./gradlew testDebugUnitTest -PupdateScreenshots`;
 * without the property Gradle leaves this class out and CI never runs it.
 *
 * The top-level tabs are reached the way a user reaches them, through the
 * bottom bar of [SaldoApp]; screens pushed on top of a tab are shown on their
 * own, as they appear with their back arrow.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// SDK 35: Robolectric's SDK 36 image reaches into JDK internals that Java 21 keeps
// closed (same pin as Passo). The screens draw the same on both.
@Config(
    application = HiltTestApplication::class,
    sdk = [35],
    qualifiers = "it-rIT-w393dp-h852dp-xhdpi",
)
class ReadmeScreenshots {

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val registerActivity = RegisterHiltTestActivityRule()

    @get:Rule(order = 2)
    val compose = createAndroidComposeRule<HiltTestActivity>()

    @Inject
    lateinit var ledger: SampleLedger

    @Inject
    lateinit var widgetData: QuickAddWidgetDataLoader

    @Inject
    lateinit var categories: CategoryRepository

    @Inject
    lateinit var accounts: AccountRepository

    @Inject
    @ApplicationContext
    lateinit var context: Context

    private val output = System.getProperty("saldo.readmeScreenshots")

    @Before
    fun setUp() {
        assumeTrue("Run with -PupdateScreenshots", output != null)
        Locale.setDefault(Locale.ITALY)
        TimeZone.setDefault(TimeZone.getTimeZone(SampleLedger.ZONE))
        hilt.inject()
        runBlocking { ledger.seed() }
    }

    @Test
    fun dashboard() {
        showApp()
        save("dashboard")
    }

    @Test
    fun dashboardCards() {
        showApp()
        // Below the balance and the period totals: the month comparison, budgets,
        // savings goals, credits and debts.
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(DASHBOARD_CARDS_INDEX)
        quiet()
        save("dashboard-cards")
    }

    @Test
    fun dashboardDark() {
        showApp(dark = true)
        save("dashboard-dark")
    }

    @Test
    fun transactions() {
        showApp()
        openTab(R.string.nav_transactions)
        waitFor("Esselunga")
        save("transactions")
    }

    @Test
    fun stats() {
        showApp()
        openTab(R.string.nav_stats)
        quiet()
        save("stats")
    }

    @Test
    fun recurrences() {
        show {
            RecurrencesScreen(
                onNavigateBack = {},
                onNavigateToNewRule = {},
                onNavigateToEditRule = {},
                onNavigateToUpcoming = {},
                onNavigateToSuggestedRule = {},
            )
        }
        waitFor("Affitto")
        save("recurrences")
    }

    @Test
    fun exchangeRates() {
        show { ExchangeRatesScreen(onNavigateBack = {}) }
        waitFor("Sterlina britannica")
        // A price seen abroad, typed on the converter's keypad, then the keypad
        // put away so the board shows.
        compose.onNode(hasContentDescription(context.getString(R.string.editor_amount))).performClick()
        quiet()
        pressKeys("5", "5")
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        quiet()
        save("exchange-rates")
    }

    @Test
    fun widgets() {
        val home = homeScreenWidgets()
        show(applyBackground = false) { HomeScreen(home) }
        quiet()
        save("widgets")
    }

    /** The guide, from the card at the top of Settings: its opening and the map of the four screens. */
    @Test
    fun guide() {
        show { GuideScreen(onNavigateBack = {}) }
        quiet()
        save("guide")
    }

    /**
     * The grid widget's own settings, as the launcher's reconfigure flow opens
     * them: the real widget at the top, then its background, colour and opacity.
     */
    @Test
    fun widgetSettings() {
        val config = QuickAddWidgetConfig()
        val state = runBlocking {
            QuickAddWidgetConfigUiState(
                isLoading = false,
                config = config,
                accounts = accounts.observeAccounts().first().filter { !it.isArchived },
                categories = categories.observeCategories(CategoryType.EXPENSE).first(),
            )
        }
        val theme = resolveWidgetTheme(context, ThemePreferences(), config)
        show {
            QuickAddWidgetConfigScreen(
                state = state,
                isBar = false,
                theme = theme,
                onAccountSelected = {},
                onTypeSelected = {},
                onShowAppShortcutChanged = {},
                onButtonsSelected = {},
                onCustomCategoriesChanged = {},
                onCategoryToggled = {},
                onPinnedReordered = {},
                onLookChanged = {},
                onConfirm = {},
                onCancel = {},
            )
        }
        quiet()
        save("widget-settings")
    }

    /**
     * What a tap on the widget's "Ristoranti & Bar" tile opens: the quick-entry
     * sheet over the home screen, with an amount half typed on its keypad.
     */
    @Test
    fun quickEntry() {
        val home = homeScreenWidgets()
        val diningName = context.getString(R.string.seed_category_dining)
        val dining = runBlocking { categories.observeCategories().first().first { it.name == diningName } }
        val route = QuickEntryRoute(type = TransactionType.EXPENSE, categoryId = dining.id, accountId = null)
        show(applyBackground = false) {
            HomeScreen(home)
            QuickEntrySheet(
                viewModel = hiltViewModel<QuickEntryViewModel, QuickEntryViewModel.Factory>(
                    creationCallback = { factory -> factory.create(route) },
                ),
                onDismiss = {},
            )
        }
        waitFor(diningName)
        pressKeys("1", "2", ",", "5", "0")
        quiet()
        save("quick-entry")
    }

    /** Taps the app's amount keypad (the amount field above it shows the same digits). */
    private fun pressKeys(vararg keys: String) {
        val amountField = hasContentDescription(context.getString(R.string.editor_amount), substring = true)
        keys.forEach { key -> compose.onNode(hasText(key) and hasClickAction() and !amountField).performClick() }
    }

    private fun showApp(dark: Boolean = false) {
        show(dark = dark) { SaldoApp() }
        waitFor("Conto corrente")
    }

    private fun show(dark: Boolean = false, applyBackground: Boolean = true, content: @Composable () -> Unit) {
        compose.setContent {
            SaldoTheme(darkTheme = dark, applyBackground = applyBackground) { content() }
        }
    }

    private fun openTab(label: Int) {
        compose.onNode(hasText(context.getString(label)) and hasClickAction()).performClick()
        quiet()
    }

    /**
     * Waits for the screen to show the ledger: Room answers on its own
     * threads, so idle Compose is not yet a loaded screen. First [text], then
     * a few quiet rounds for the parts that load after it.
     */
    private fun waitFor(text: String) {
        try {
            compose.waitUntil(timeoutMillis = LOAD_TIMEOUT_MS) {
                compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
            }
        } catch (e: ComposeTimeoutException) {
            // What the screen shows instead, in the test report.
            println(compose.onRoot(useUnmergedTree = true).printToString())
            throw e
        }
        quiet()
    }

    /** A few idle rounds, for what Room delivers after the first frame. */
    private fun quiet() {
        repeat(QUIET_ROUNDS) {
            Thread.sleep(QUIET_ROUND_MS)
            compose.waitForIdle()
        }
    }

    /** The two widget shapes as the launcher gets them: the real `RemoteViews`. */
    private fun homeScreenWidgets(): HomeWidgets {
        fun render(config: QuickAddWidgetConfig, size: WidgetSize): RemoteViews {
            val snapshot = runBlocking { widgetData.loadShared(config) }
            return WidgetRenderer.render(
                context = context,
                appWidgetId = 1,
                data = snapshot.data,
                palette = snapshot.theme.palette,
                size = size,
            )
        }
        val grid = WidgetSize(WIDE_WIDGET_DP, wideGridHeight(GRID_ROWS))
        return HomeWidgets(
            // The bar in another of the six colours, the grid on the default
            // blue: the picture shows the choice as well as the widgets.
            bar = render(QuickAddWidgetConfig(cardColor = WidgetCardColor.PLUM), ActionSizes[1]),
            barHeightDp = ActionSizes[1].height,
            grid = render(QuickAddWidgetConfig(), grid),
            gridHeightDp = grid.height,
        )
    }

    /**
     * Draws every window on screen, in stacking order: the activity, and any
     * sheet or dialog above it (a modal bottom sheet is a window of its own).
     * Drawn by hand rather than with `captureToImage()`, which waits for a
     * hardware frame that Robolectric never delivers to this activity.
     */
    private fun save(name: String) {
        compose.waitForIdle()
        val decor = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        windowRoots().forEach { root ->
            val location = IntArray(2).also(root::getLocationOnScreen)
            canvas.save()
            canvas.translate(location[0].toFloat(), location[1].toFloat())
            root.draw(canvas)
            canvas.restore()
        }
        val dir = File(checkNotNull(output)).apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** The root views of the process's windows, bottom first (framework internals, test only). */
    private fun windowRoots(): List<View> {
        val global = Class.forName("android.view.WindowManagerGlobal")
        val instance = global.getMethod("getInstance").invoke(null)
        val views = global.getDeclaredField("mViews").apply { isAccessible = true }.get(instance)
        return (views as List<*>).filterIsInstance<View>().filter { it.isShown }
    }

    private class HomeWidgets(
        val bar: RemoteViews,
        val barHeightDp: Float,
        val grid: RemoteViews,
        val gridHeightDp: Float,
    )

    /** A plain wallpaper with the two widgets placed on it, four cells wide. */
    @Composable
    private fun HomeScreen(widgets: HomeWidgets) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(WallpaperTop, WallpaperBottom)))
                .padding(horizontal = 16.dp, vertical = 56.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            PlacedWidget(widgets.bar, heightDp = widgets.barHeightDp)
            PlacedWidget(widgets.grid, heightDp = widgets.gridHeightDp)
        }
    }

    @Composable
    private fun PlacedWidget(views: RemoteViews, heightDp: Float) {
        Box(modifier = Modifier.fillMaxWidth().height(heightDp.dp)) {
            AndroidView(
                factory = { context -> FrameLayout(context).apply { addView(views.apply(context, this)) } },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    private companion object {
        const val DASHBOARD_CARDS_INDEX = 3
        const val LOAD_TIMEOUT_MS = 10_000L
        const val QUIET_ROUNDS = 5
        const val QUIET_ROUND_MS = 200L
        const val WIDE_WIDGET_DP = 250f
        const val GRID_ROWS = 4
        val WallpaperTop = Color(0xFF1F4E5A)
        val WallpaperBottom = Color(0xFFD9895B)
    }
}
