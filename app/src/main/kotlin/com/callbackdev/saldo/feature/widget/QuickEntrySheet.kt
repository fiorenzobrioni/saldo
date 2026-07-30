package com.callbackdev.saldo.feature.widget

import android.content.Intent
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.callbackdev.saldo.MainActivity
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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

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

    val context = LocalContext.current
    ModalBottomSheet(
        onDismissRequest = { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() } },
        sheetState = sheetState,
    ) {
        AnimatedContent(
            targetState = state.step,
            transitionSpec = { (fadeIn() + scaleIn(initialScale = ConfirmationScaleIn)) togetherWith fadeOut() },
            label = "quick-entry-state",
        ) { step ->
            when (step) {
                QuickEntryStep.Saved -> SavedConfirmation(state.savedAmount.orEmpty())
                QuickEntryStep.Setup -> SetupRedirect(
                    onOpenApp = {
                        // The sheet lives in its own empty-affinity task: the
                        // flag sends MainActivity to the app's own task instead
                        // of stacking the whole app over the launcher's sheet.
                        context.startActivity(
                            Intent(context, MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                        onDismiss()
                    },
                )
                QuickEntryStep.Form -> QuickEntryForm(
                    state = state,
                    onQuickTextChanged = viewModel::onQuickTextChanged,
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
    onQuickTextChanged: (String) -> Unit,
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
    var quickFieldFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        QuickTextField(
            value = state.quickText,
            onValueChange = onQuickTextChanged,
            onFocusChanged = { quickFieldFocused = it },
            onDone = { focusManager.clearFocus() },
        )
        QuickEntryHeader(
            category = state.category,
            accountName = state.account?.account?.name,
            parsedDate = state.parsedDate,
            isCategorySuggested = state.isCategorySuggested,
            onPickCategory = onPickCategory,
            onPickAccount = onPickAccount,
        )
        HeroAmountField(
            target = target,
            currencySymbol = state.currencySymbol,
            isError = false,
            // The app keypad and the system keyboard never overlap (ADR 31):
            // while the text line has focus the IME types, and tapping the
            // amount takes the keypad back.
            isActive = !quickFieldFocused,
            onActivate = { focusManager.clearFocus() },
        )
        AmountKeypadHost(target = if (quickFieldFocused) null else target, compact = true)
        EditorSaveButton(
            text = stringResource(R.string.action_save),
            onClick = onSave,
            enabled = state.canSave,
        )
    }
}

/**
 * The one-line quick entry (ADR 42): "12,50 pizza ieri" typed on the system
 * keyboard, parsed live into the fields below, everything still correctable
 * by hand before Save.
 */
@Composable
private fun QuickTextField(
    value: String,
    onValueChange: (String) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    onDone: () -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { onFocusChanged(it.isFocused) },
        placeholder = { Text(text = stringResource(R.string.widget_quick_entry_text_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        trailingIcon = if (value.isEmpty()) {
            null
        } else {
            {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.widget_quick_entry_text_clear),
                    )
                }
            }
        },
    )
}

@Composable
private fun QuickEntryHeader(
    category: Category?,
    accountName: String?,
    parsedDate: LocalDate?,
    isCategorySuggested: Boolean,
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
                    // The deduced field is highlighted, not just filled (ADR
                    // 42): the container marks "read from your text" while it
                    // stays one tap from being changed.
                    .background(
                        if (isCategorySuggested) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            Color.Transparent
                        },
                    )
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
            if (parsedDate != null) {
                val formatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
                Text(
                    text = stringResource(
                        R.string.widget_quick_entry_parsed_date,
                        formatter.format(parsedDate),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
        }
    }
}

/**
 * First launch from the tile, nothing to save onto yet: a short explanation
 * and a single way forward, into the app - the sheet's own version of the
 * widget's NotReady face.
 */
@Composable
private fun SetupRedirect(onOpenApp: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(top = 8.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.widget_quick_entry_setup_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onOpenApp) {
            Text(text = stringResource(R.string.widget_quick_add_open))
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

/** The three faces of the sheet, in priority order. */
private enum class QuickEntryStep { Saved, Setup, Form }

private val QuickEntryUiState.step: QuickEntryStep
    get() = when {
        isSaved -> QuickEntryStep.Saved
        needsSetup -> QuickEntryStep.Setup
        else -> QuickEntryStep.Form
    }

private val AvatarSize = 44.dp
private val AvatarGlyphSize = 24.dp
private const val ConfirmationScaleIn = 0.92f
private const val ConfirmationMillis = 700L
