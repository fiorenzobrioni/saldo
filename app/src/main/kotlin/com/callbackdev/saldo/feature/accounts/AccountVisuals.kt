package com.callbackdev.saldo.feature.accounts

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.CurrencyExchange
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Wallet
import androidx.compose.material.icons.outlined.Work
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.domain.model.AccountType

/**
 * Curated colors and icons for accounts. Icons are identified by their
 * Material Symbols name, stored as a plain string on the account so the
 * palette can grow without schema changes.
 */
object AccountVisuals {

    /** Curated palette (0xRRGGBB) that reads well on light and dark surfaces. */
    @Suppress("MagicNumber")
    val colors: List<Int> = listOf(
        0xEF5350, // red
        0xEC407A, // pink
        0xAB47BC, // purple
        0x7E57C2, // deep purple
        0x5C6BC0, // indigo
        0x1E88E5, // blue
        0x00ACC1, // cyan
        0x26A69A, // teal
        0x43A047, // green
        0x9E9D24, // olive
        0xF9A825, // amber
        0xF4511E, // deep orange
        0x8D6E63, // brown
        0x546E7A, // blue grey
    )

    private val icons: Map<String, ImageVector> = linkedMapOf(
        "account_balance" to Icons.Outlined.AccountBalance,
        "credit_card" to Icons.Outlined.CreditCard,
        "payments" to Icons.Outlined.Payments,
        "wallet" to Icons.Outlined.Wallet,
        "account_balance_wallet" to Icons.Outlined.AccountBalanceWallet,
        "savings" to Icons.Outlined.Savings,
        "smartphone" to Icons.Outlined.Smartphone,
        "trending_up" to Icons.AutoMirrored.Outlined.TrendingUp,
        "currency_exchange" to Icons.Outlined.CurrencyExchange,
        "shopping_bag" to Icons.Outlined.ShoppingBag,
        "home" to Icons.Outlined.Home,
        "work" to Icons.Outlined.Work,
        "school" to Icons.Outlined.School,
        "flight" to Icons.Outlined.Flight,
        "directions_car" to Icons.Outlined.DirectionsCar,
        "card_giftcard" to Icons.Outlined.CardGiftcard,
    )

    /** Icon keys in display order for the editor grid. */
    val iconKeys: List<String> = icons.keys.toList()

    /** Resolves a stored icon key, falling back to a generic wallet. */
    fun icon(key: String?): ImageVector = icons[key] ?: Icons.Outlined.Wallet

    /** Default icon for a freshly selected account type. */
    fun defaultIconFor(type: AccountType): String = when (type) {
        AccountType.CHECKING -> "account_balance"
        AccountType.CARD -> "credit_card"
        AccountType.CASH -> "payments"
        AccountType.DIGITAL_WALLET -> "wallet"
        AccountType.OTHER -> "account_balance_wallet"
    }

    /** Opaque [Color] for a stored 0xRRGGBB value, defaulting to the first palette entry. */
    @Suppress("MagicNumber")
    fun color(rgb: Int?): Color = Color(0xFF000000L or (rgb ?: colors.first()).toLong())
}

/** User-facing label for an [AccountType]. */
@StringRes
fun AccountType.labelRes(): Int = when (this) {
    AccountType.CHECKING -> R.string.account_type_checking
    AccountType.CARD -> R.string.account_type_card
    AccountType.CASH -> R.string.account_type_cash
    AccountType.DIGITAL_WALLET -> R.string.account_type_digital_wallet
    AccountType.OTHER -> R.string.account_type_other
}
