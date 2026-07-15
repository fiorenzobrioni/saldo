package com.callbackdev.saldo.core.designsystem.component

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.callbackdev.saldo.R

/**
 * Holds the "unsaved changes" state of an editor screen. Both exit routes - the
 * top-bar close button and the system back - funnel through [requestNavigateBack]:
 * when there are pending edits it opens a confirmation dialog, otherwise it
 * leaves at once. Created via [rememberUnsavedChangesGuard]; render the paired
 * [DiscardChangesDialog] to show the confirmation.
 */
@Stable
class UnsavedChangesGuardState internal constructor(dialogVisibleInitially: Boolean) {

    /** True while the confirmation dialog is shown. */
    var isConfirmDialogVisible by mutableStateOf(dialogVisibleInitially)
        private set

    // Plain fields (not snapshot state): kept in sync by
    // [rememberUnsavedChangesGuard] on every recomposition and read only at
    // click/back time, so assigning them during composition is safe.
    internal var hasUnsavedChanges: Boolean = false
    internal var onLeave: () -> Unit = {}

    /** The close button and the intercepted back both call this. */
    fun requestNavigateBack() {
        if (hasUnsavedChanges) isConfirmDialogVisible = true else onLeave()
    }

    /** Confirms discarding the edits and leaves the screen. */
    fun confirmDiscard() {
        isConfirmDialogVisible = false
        onLeave()
    }

    /** Dismisses the dialog and stays on the screen. */
    fun dismiss() {
        isConfirmDialogVisible = false
    }

    internal companion object {
        val Saver: Saver<UnsavedChangesGuardState, Boolean> = Saver(
            save = { it.isConfirmDialogVisible },
            restore = { UnsavedChangesGuardState(dialogVisibleInitially = it) },
        )
    }
}

/**
 * Remembers an [UnsavedChangesGuardState] and wires the system back to it.
 *
 * The [BackHandler] is enabled only while [hasUnsavedChanges] is true, so the
 * common case - leaving an untouched form - keeps the platform's predictive
 * back animation; the handler engages only when there is something to lose.
 *
 * @param hasUnsavedChanges whether the form differs from its saved/initial state.
 * @param onNavigateBack the actual navigation to run once leaving is allowed.
 */
@Composable
fun rememberUnsavedChangesGuard(
    hasUnsavedChanges: Boolean,
    onNavigateBack: () -> Unit,
): UnsavedChangesGuardState {
    val state = rememberSaveable(saver = UnsavedChangesGuardState.Saver) {
        UnsavedChangesGuardState(dialogVisibleInitially = false)
    }
    state.hasUnsavedChanges = hasUnsavedChanges
    state.onLeave = onNavigateBack
    BackHandler(enabled = hasUnsavedChanges) { state.requestNavigateBack() }
    return state
}

/**
 * The confirmation shown when the user tries to leave an editor with unsaved
 * edits. Two actions only: discard (destructive, error-colored) or keep
 * editing. Renders nothing while [UnsavedChangesGuardState.isConfirmDialogVisible]
 * is false.
 */
@Composable
fun DiscardChangesDialog(state: UnsavedChangesGuardState) {
    if (!state.isConfirmDialogVisible) return
    AlertDialog(
        onDismissRequest = state::dismiss,
        icon = {
            Icon(
                imageVector = Icons.Outlined.WarningAmber,
                contentDescription = null,
            )
        },
        title = { Text(stringResource(R.string.discard_changes_title)) },
        text = { Text(stringResource(R.string.discard_changes_message)) },
        confirmButton = {
            TextButton(
                onClick = state::confirmDiscard,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text(stringResource(R.string.discard_changes_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = state::dismiss) {
                Text(stringResource(R.string.discard_changes_keep_editing))
            }
        },
    )
}
