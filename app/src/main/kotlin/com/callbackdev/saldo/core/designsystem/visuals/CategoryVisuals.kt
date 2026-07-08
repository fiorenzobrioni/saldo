package com.callbackdev.saldo.core.designsystem.visuals

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Commute
import androidx.compose.material.icons.outlined.CurrencyExchange
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.HomeWork
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Redeem
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.Work
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Colors and icons for categories. Icons are identified by their Material
 * Symbols name, stored as a plain string on the category (same convention as
 * [AccountVisuals]), so the set can grow without schema changes. The keys
 * cover every icon used by the default category seed.
 */
object CategoryVisuals {

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
        "category" to Icons.Outlined.Category,
        "payments" to Icons.Outlined.Payments,
        "work" to Icons.Outlined.Work,
        "redeem" to Icons.Outlined.Redeem,
        "currency_exchange" to Icons.Outlined.CurrencyExchange,
    )

    /** Icon keys in display order for the category editor grid (Phase 4). */
    val iconKeys: List<String> = icons.keys.toList()

    /** Resolves a stored icon key, falling back to a generic category glyph. */
    fun icon(key: String?): ImageVector = icons[key] ?: Icons.Outlined.Category

    /** Opaque [Color] for a stored 0xRRGGBB value, defaulting to a neutral grey. */
    @Suppress("MagicNumber")
    fun color(rgb: Int?): Color = Color(0xFF000000L or (rgb ?: 0x90A4AE).toLong())
}
