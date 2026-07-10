package com.callbackdev.saldo.core.designsystem.visuals

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Commute
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.CurrencyExchange
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.HomeWork
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.LocalBar
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Redeem
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.Work
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.annotation.StringRes
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.domain.model.CategoryType

/**
 * Colors and icons for categories. Icons are identified by their Material
 * Symbols name, stored as a plain string on the category (same convention as
 * [AccountVisuals]), so the set can grow without schema changes. The keys
 * cover every icon used by the default category seed.
 */
object CategoryVisuals {

    /** Curated palette (0xRRGGBB) shared with the default seed, used by the editor. */
    @Suppress("MagicNumber")
    val colors: List<Int> = listOf(
        0xEF5350, // red
        0xEC407A, // pink
        0xF06292, // light pink
        0xAB47BC, // purple
        0x7E57C2, // deep purple
        0x5C6BC0, // indigo
        0x42A5F5, // blue
        0x29B6F6, // light blue
        0x26C6DA, // cyan
        0x26A69A, // teal
        0x66BB6A, // green
        0x9CCC65, // light green
        0xD4E157, // lime
        0xFFA726, // orange
        0xFF7043, // deep orange
        0x8D6E63, // brown
        0x78909C, // blue grey
        0x90A4AE, // grey
    )

    private val icons: Map<String, ImageVector> = linkedMapOf(
        "home" to Icons.Outlined.Home,
        "home_work" to Icons.Outlined.HomeWork,
        "shopping_cart" to Icons.Outlined.ShoppingCart,
        "restaurant" to Icons.Outlined.Restaurant,
        "commute" to Icons.Outlined.Commute,
        "local_gas_station" to Icons.Outlined.LocalGasStation,
        "health_and_safety" to Icons.Outlined.HealthAndSafety,
        "shopping_bag" to Icons.Outlined.ShoppingBag,
        "flight" to Icons.Outlined.Flight,
        "movie" to Icons.Outlined.Movie,
        "subscriptions" to Icons.Outlined.Subscriptions,
        "receipt_long" to Icons.AutoMirrored.Outlined.ReceiptLong,
        "school" to Icons.Outlined.School,
        "card_giftcard" to Icons.Outlined.CardGiftcard,
        "account_balance" to Icons.Outlined.AccountBalance,
        "payments" to Icons.Outlined.Payments,
        "work" to Icons.Outlined.Work,
        "redeem" to Icons.Outlined.Redeem,
        "currency_exchange" to Icons.Outlined.CurrencyExchange,
        "savings" to Icons.Outlined.Savings,
        "local_cafe" to Icons.Outlined.LocalCafe,
        "local_bar" to Icons.Outlined.LocalBar,
        "cake" to Icons.Outlined.Cake,
        "fitness_center" to Icons.Outlined.FitnessCenter,
        "spa" to Icons.Outlined.Spa,
        "sports_esports" to Icons.Outlined.SportsEsports,
        "music_note" to Icons.Outlined.MusicNote,
        "live_tv" to Icons.Outlined.LiveTv,
        "checkroom" to Icons.Outlined.Checkroom,
        "pets" to Icons.Outlined.Pets,
        "child_care" to Icons.Outlined.ChildCare,
        "celebration" to Icons.Outlined.Celebration,
        "volunteer_activism" to Icons.Outlined.VolunteerActivism,
        "computer" to Icons.Outlined.Computer,
        "phone" to Icons.Outlined.Phone,
        "wifi" to Icons.Outlined.Wifi,
        "cloud" to Icons.Outlined.Cloud,
        "directions_car" to Icons.Outlined.DirectionsCar,
        "category" to Icons.Outlined.Category,
    )

    /** Icon keys in display order for the category editor grid (Phase 4). */
    val iconKeys: List<String> = icons.keys.toList()

    /** Icon key used for a freshly created category, before the user picks one. */
    const val defaultIconKey: String = "category"

    /** Default color for a freshly created category. */
    val defaultColor: Int = colors.first()

    /** Resolves a stored icon key, falling back to a generic category glyph. */
    fun icon(key: String?): ImageVector = icons[key] ?: Icons.Outlined.Category

    /** Opaque [Color] for a stored 0xRRGGBB value, defaulting to a neutral grey. */
    @Suppress("MagicNumber")
    fun color(rgb: Int?): Color = Color(0xFF000000L or (rgb ?: 0x90A4AE).toLong())
}

/** User-facing label for a [CategoryType]. */
@StringRes
fun CategoryType.labelRes(): Int = when (this) {
    CategoryType.EXPENSE -> R.string.category_type_expense
    CategoryType.INCOME -> R.string.category_type_income
    CategoryType.BOTH -> R.string.category_type_both
}
