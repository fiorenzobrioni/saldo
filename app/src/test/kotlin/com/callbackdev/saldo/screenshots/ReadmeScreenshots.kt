package com.callbackdev.saldo.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.printToString
import com.callbackdev.saldo.core.designsystem.theme.SaldoTheme
import com.callbackdev.saldo.navigation.SaldoApp
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
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

    private fun showApp(dark: Boolean = false) {
        compose.setContent {
            SaldoTheme(darkTheme = dark) { SaldoApp() }
        }
        settle()
    }

    /**
     * Waits for the screen to show the ledger: Room answers on its own
     * threads, so idle Compose is not yet a loaded screen. First the accounts,
     * then a few quiet rounds for the cards that load after them.
     */
    private fun settle() {
        try {
            compose.waitUntil(timeoutMillis = LOAD_TIMEOUT_MS) {
                compose.onAllNodesWithText("Conto corrente").fetchSemanticsNodes().isNotEmpty()
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

    private fun save(name: String) {
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val dir = File(checkNotNull(output)).apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private companion object {
        const val DASHBOARD_CARDS_INDEX = 3
        const val LOAD_TIMEOUT_MS = 10_000L
        const val QUIET_ROUNDS = 5
        const val QUIET_ROUND_MS = 200L
    }
}
