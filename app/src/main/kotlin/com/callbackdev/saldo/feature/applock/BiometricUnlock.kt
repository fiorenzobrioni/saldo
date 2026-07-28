package com.callbackdev.saldo.feature.applock

import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether Class 3 (strong) biometrics are enrolled and usable right now.
 * Injectable wrapper so ViewModels can gate the biometric switch without
 * touching a system service in unit tests.
 */
@Singleton
class BiometricAvailability @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun canUseBiometrics(): Boolean = runCatching {
        val manager = context.getSystemService(BiometricManager::class.java)
        manager?.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }.getOrDefault(false)
}

/**
 * Launches the system biometric prompt and reports success. Framework
 * `BiometricPrompt`, not the androidx library (ADR 39): complete since API
 * 28, so minSdk 33 covers it with one code path and zero new dependencies,
 * and it does not force `MainActivity` onto `FragmentActivity`.
 *
 * `BIOMETRIC_STRONG` only, no device credential: the fallback is the app's
 * own PIN, so the negative button simply closes the prompt and leaves the
 * keypad. No `CryptoObject` either: the biometric is a shortcut for the PIN
 * gate, it does not custody keys (the data is not encrypted with the PIN).
 */
class BiometricUnlockController internal constructor(
    private val context: Context,
    private val title: String,
    private val negativeText: String,
    private val onSuccess: () -> Unit,
) {

    private var activeSignal: CancellationSignal? = null

    /** Shows the prompt, replacing any prompt already on screen. */
    fun launch() {
        cancel()
        val executor = ContextCompat.getMainExecutor(context)
        val prompt = BiometricPrompt.Builder(context)
            .setTitle(title)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setNegativeButton(negativeText, executor) { _, _ ->
                // "Use PIN": nothing to do, the keypad is already behind the prompt.
            }
            .build()
        val signal = CancellationSignal()
        activeSignal = signal
        prompt.authenticate(
            signal,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    activeSignal = null
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // Dismissals, lockouts and hardware errors all land here:
                    // the PIN keypad remains the way in, nothing to surface.
                    activeSignal = null
                }
            },
        )
    }

    /** Dismisses a showing prompt; safe to call when none is. */
    fun cancel() {
        activeSignal?.cancel()
        activeSignal = null
    }
}

/**
 * Remembers a [BiometricUnlockController] bound to this composition: the
 * prompt is cancelled when the caller leaves the tree, so it can never
 * outlive the screen that launched it. [onSuccess] is read through
 * [rememberUpdatedState], so the latest lambda always runs.
 */
@Composable
fun rememberBiometricUnlock(
    title: String,
    negativeText: String,
    onSuccess: () -> Unit,
): BiometricUnlockController {
    val context = LocalContext.current
    val currentOnSuccess by rememberUpdatedState(onSuccess)
    val controller = remember(context, title, negativeText) {
        BiometricUnlockController(
            context = context,
            title = title,
            negativeText = negativeText,
            onSuccess = { currentOnSuccess() },
        )
    }
    DisposableEffect(controller) {
        onDispose { controller.cancel() }
    }
    return controller
}
