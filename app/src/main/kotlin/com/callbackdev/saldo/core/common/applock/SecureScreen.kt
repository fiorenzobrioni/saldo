package com.callbackdev.saldo.core.common.applock

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * Keeps the window's `FLAG_SECURE` in sync with the screen-privacy
 * preference: content hidden in the recents thumbnail and screenshots
 * blocked (the flag does not distinguish the two). Called from `onCreate`
 * before `setContent`, earlier than any composition-based effect could run,
 * to shrink the first-frame window; the collection then follows the
 * preference live for the activity's whole lifetime, so toggling it in
 * Settings applies without a restart.
 */
fun ComponentActivity.bindSecureScreen(appLockRepository: AppLockRepository) {
    lifecycleScope.launch {
        appLockRepository.secureScreenEnabled.collect { enabled ->
            if (enabled) {
                window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }
}
