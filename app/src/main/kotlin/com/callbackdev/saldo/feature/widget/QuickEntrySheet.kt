package com.callbackdev.saldo.feature.widget

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.designsystem.component.AmountKeypadHost
import com.callbackdev.saldo.core.designsystem.component.AmountTarget
import com.callbackdev.saldo.core.designsystem.component.EditorSaveButton
import com.callbackdev.saldo.core.designsystem.component.HeroAmountField
import com.callbackdev.saldo.core.designsystem.theme.AvatarShape
import com.callbackdev.saldo.core.designsystem.visuals.CategoryVisuals
import com.callbackdev.saldo.core.designsystem.visuals.contentColorOn
import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.feature.transactions.AccountPickerSheet
import com.callbackdev.saldo.feature.transactions.CategoryPickerSheet
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The sheet the widget opens: the chosen category at the top, the amount in the
 * hero field with the keypad already up, and Save. The two things a quick entry
 * could still get wrong - the category and the account - are correctable in
 * place, so a mistap does not mean starting again in the full editor.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickEntrySheet(viewModel: QuickEntryViewModel, onDismiss: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var showAccountPicker by remember { mutableStateOf(false) }
    var showCategoryPicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                QuickEntryEvent.Saved -> {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    // The pause is the point: a widget cannot animate, so this
                    // is the only closure the user gets that the movement landed.
                    delay(ConfirmationMillis)
                    sheetState.hide()
                    onDismiss()
                }
                QuickEntryEvent.WriteFailed -> haptics.performHapticFeedback(HapticFeedbackType.Reject)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() } },
        sheetState = sheetState,
    ) {
        AnimatedContent(
            targetState = state.isSaved,
            transitionSpec = { (fadeIn() + scaleIn(initialScale = ConfirmationScaleIn)) togetherWith fadeOut() },
            label = "quick-entry-state",
        ) { saved ->
            if (saved) {
                SavedConfirmation(state.savedAmount.orEmpty())
            } else {
                QuickEntryForm(
                    state = state,
                    onAmountChanged = viewModel::onAmountChanged,
                    onSave = viewModel::save,
                    onPickAccount = { showAccountPicker = true },
                    onPickCategory = { showCategoryPicker = true },
                )
            }
        }
    }

    if (showAccountPicker) {
        AccountPickerSheet(
            title = stringResource(R.string.transaction_editor_account),
            accounts = state.accounts,
            selectedAccountId = state.account?.account?.id,
            disabledAccountId = null,
            onSelect = {
                viewModel.onAccountSelected(it.account.id)
                showAccountPicker = false
            },
            onDismiss = { showAccountPicker = false },
        )
    }
    if (showCategoryPicker) {
        CategoryPickerSheet(
            categories = state.categories,
            selectedId = state.category?.id,
            onSelect = {
                viewModel.onCategorySelected(it)
                showCategoryPicker = false
            },
            onDismiss = { showCategoryPicker = false },
        )
    }
}

@Composable
private fun QuickEntryForm(
    state: QuickEntryUiState,
    onAmountChanged: (String) -> Unit,
    onSave: () -> Unit,
    onPickAccount: () -> Unit,
    onPickCategory: () -> Unit,
) {
    val target = AmountTarget(
        value = state.amountInput,
        fractionDigits = state.fractionDigits,
        allowNegative = false,
        onValueChange = onAmountChanged,
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        QuickEntryHeader(
            category = state.category,
            accountName = state.account?.account?.name,
            onPickCategory = onPickCategory,
            onPickAccount = onPickAccount,
        )
        HeroAmountField(
            target = target,
            currencySymbol = state.currencySymbol,
            isError = false,
            // Always active: there is nothing else on this sheet to type into,
            // so an inactive state would only cost the user a tap.
            isActive = true,
            onActivate = {},
        )
        AmountKeypadHost(target = target, compact = true)
        EditorSaveButton(
            text = stringResource(R.string.action_save),
            onClick = onSave,
            enabled = state.canSave,
        )
    }
}

@Composable
private fun QuickEntryHeader(
    category: Category?,
    accountName: String?,
    onPickCategory: () -> Unit,
    onPickAccount: () -> Unit,
) {
    val color = CategoryVisuals.color(category?.color)
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(AvatarSize).clip(AvatarShape).background(color),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = CategoryVisuals.icon(category?.icon),
                contentDescription = null,
                tint = contentColorOn(color),
                modifier = Modifier.size(AvatarGlyphSize),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = category?.name.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .clickable(
                        onClick = onPickCategory,
                        onClickLabel = stringResource(R.string.widget_quick_entry_change_category),
                    )
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
            if (accountName != null) {
                Text(
                    text = accountName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .clickable(
                            onClick = onPickAccount,
                            onClickLabel = stringResource(R.string.widget_quick_entry_change_account),
                        )
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
        }
    }
}

/** The saved state: a checkmark and the amount, held just long enough to read. */
@Composable
private fun SavedConfirmation(savedAmount: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(top = 24.dp, bottom = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(percent = 50),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(16.dp).size(32.dp),
            )
        }
        Text(text = savedAmount, style = MaterialTheme.typography.headlineMedium)
        Text(
            text = stringResource(R.string.widget_quick_entry_saved),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val AvatarSize = 44.dp
private val AvatarGlyphSize = 24.dp
private const val ConfirmationScaleIn = 0.92f
private const val ConfirmationMillis = 700L
