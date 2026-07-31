package com.callbackdev.saldo.feature.backup

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.callbackdev.saldo.R

/** Shortest passphrase the export accepts; a length, never a character-class rule. */
private const val MIN_PASSPHRASE_LENGTH = 8

/** Diameter of the in-button spinner shown while the key is being derived. */
private val ButtonSpinnerSize = 18.dp

/**
 * Asks for a new passphrase before an encrypted export (Fase 22).
 *
 * Typed twice, because a typo in the only copy of a passphrase that cannot be
 * recovered would be found out on the day the backup is needed. The dialog says
 * plainly that there is no recovery, and it says it *before* the file exists.
 *
 * Nothing here is kept: the confirmed value goes to the ViewModel, which holds
 * it only for the round trip through the file picker. The fields are
 * deliberately not `rememberSaveable`: a passphrase does not belong in saved
 * instance state.
 */
@Composable
internal fun ExportPassphraseDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var passphrase by remember { mutableStateOf("") }
    var repeated by remember { mutableStateOf("") }
    var revealed by rememberSaveable { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val tooShort = passphrase.length < MIN_PASSPHRASE_LENGTH
    val mismatch = repeated.isNotEmpty() && repeated != passphrase
    val canConfirm = !tooShort && repeated == passphrase

    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        icon = { Icon(imageVector = Icons.Outlined.Lock, contentDescription = null) },
        title = { Text(stringResource(R.string.backup_passphrase_dialog_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.backup_passphrase_dialog_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text(stringResource(R.string.backup_passphrase_label)) },
                    supportingText = {
                        Text(
                            stringResource(
                                R.string.backup_passphrase_min_length,
                                MIN_PASSPHRASE_LENGTH,
                            ),
                        )
                    },
                    singleLine = true,
                    visualTransformation = passphraseTransformation(revealed),
                    keyboardOptions = passphraseKeyboardOptions(ImeAction.Next),
                    trailingIcon = {
                        RevealToggle(revealed = revealed, onToggle = { revealed = !revealed })
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = repeated,
                    onValueChange = { repeated = it },
                    label = { Text(stringResource(R.string.backup_passphrase_confirm_label)) },
                    isError = mismatch,
                    supportingText = if (mismatch) {
                        { Text(stringResource(R.string.backup_passphrase_mismatch)) }
                    } else {
                        null
                    },
                    singleLine = true,
                    visualTransformation = passphraseTransformation(revealed),
                    keyboardOptions = passphraseKeyboardOptions(ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(passphrase) }, enabled = canConfirm) {
                Text(stringResource(R.string.backup_passphrase_confirm_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

/**
 * Asks for the passphrase of a picked container, before anything is inspected.
 *
 * A rejected attempt is reported in place, with the typed text kept: getting one
 * character wrong should not cost the whole passphrase. The dialog cannot be
 * dismissed while the key is being derived, which is the only moment when it is
 * actually busy.
 */
@Composable
internal fun UnlockPassphraseDialog(
    request: UnlockRequest,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var passphrase by remember { mutableStateOf("") }
    var revealed by rememberSaveable { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    AlertDialog(
        modifier = modifier,
        onDismissRequest = { if (!request.isUnlocking) onDismiss() },
        icon = { Icon(imageVector = Icons.Outlined.LockOpen, contentDescription = null) },
        title = { Text(stringResource(R.string.backup_unlock_dialog_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.backup_unlock_dialog_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text(stringResource(R.string.backup_passphrase_label)) },
                    isError = request.failed,
                    supportingText = if (request.failed) {
                        { Text(stringResource(R.string.backup_unlock_error)) }
                    } else {
                        null
                    },
                    enabled = !request.isUnlocking,
                    singleLine = true,
                    visualTransformation = passphraseTransformation(revealed),
                    keyboardOptions = passphraseKeyboardOptions(ImeAction.Done),
                    trailingIcon = {
                        RevealToggle(revealed = revealed, onToggle = { revealed = !revealed })
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(passphrase) },
                enabled = passphrase.isNotEmpty() && !request.isUnlocking,
            ) {
                if (request.isUnlocking) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(ButtonSpinnerSize),
                    )
                    Spacer(Modifier.size(8.dp))
                }
                Text(stringResource(R.string.backup_unlock_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !request.isUnlocking) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun RevealToggle(revealed: Boolean, onToggle: () -> Unit) {
    IconButton(onClick = onToggle) {
        Icon(
            imageVector = if (revealed) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
            contentDescription = stringResource(
                if (revealed) R.string.backup_passphrase_hide else R.string.backup_passphrase_show,
            ),
        )
    }
}

private fun passphraseTransformation(revealed: Boolean): VisualTransformation =
    if (revealed) VisualTransformation.None else PasswordVisualTransformation()

/**
 * A password field, with the autocorrect and suggestion behaviour that implies:
 * an IME that "fixes" a passphrase would produce a file nobody can open.
 */
private fun passphraseKeyboardOptions(imeAction: ImeAction) =
    KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction)
