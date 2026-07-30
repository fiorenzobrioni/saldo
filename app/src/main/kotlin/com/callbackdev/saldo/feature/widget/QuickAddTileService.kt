package com.callbackdev.saldo.feature.widget

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.callbackdev.saldo.core.domain.model.TransactionType

/**
 * The "Add expense" Quick Settings tile: one tap in the shade opens the quick
 * entry sheet from any screen of the phone (ADR 41). Always an expense - the
 * most frequent operation - with account and category resolved by the sheet's
 * own default chain, exactly as for the single-row widget.
 *
 * Deliberately stateless (ADR 37): the tile shows no balances or totals, so
 * the lifecycle callbacks never touch the database and there is nothing to
 * refresh. The user adds the tile from the shade's editor; the app never
 * requests it.
 */
class QuickAddTileService : TileService() {

    override fun onStartListening() {
        // An action tile is always ready: ACTIVE renders it bright instead of
        // the grayed "off" look of INACTIVE. A state constant is the only thing
        // ever written here - never a database read.
        qsTile?.let { tile ->
            tile.state = Tile.STATE_ACTIVE
            tile.updateTile()
        }
    }

    override fun onClick() {
        // The shade is reachable from the keyguard: ask for the unlock first,
        // the same stance as the app lock (ADR 39) - financial entry never
        // happens over a locked screen, and the sheet would sit under the
        // keyguard anyway.
        if (isLocked) {
            unlockAndRun(::openSheet)
        } else {
            openSheet()
        }
    }

    // The lint check flags the deprecated Intent overload regardless of the
    // SDK_INT guard around it; the call is deliberate, see the branch comment.
    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openSheet() {
        val intent = QuickEntryActivity.intent(
            context = this,
            type = TransactionType.EXPENSE,
            categoryId = null,
            accountId = null,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE),
            )
        } else {
            // The Intent overload throws on API 34+ for apps targeting 34+,
            // but this branch only ever runs on API 33 (minSdk), where the
            // PendingIntent overload does not exist yet.
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
