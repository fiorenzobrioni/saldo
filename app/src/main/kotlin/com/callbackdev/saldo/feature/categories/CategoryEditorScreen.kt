package com.callbackdev.saldo.feature.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.designsystem.component.EditorBottomBar
import com.callbackdev.saldo.core.designsystem.component.EditorSaveButton
import com.callbackdev.saldo.core.designsystem.visuals.labelRes
import com.callbackdev.saldo.core.domain.model.Category
import com.callbackdev.saldo.core.domain.model.CategoryType
import com.callbackdev.saldo.navigation.CategoryEditorRoute

/**
 * Create/edit form for a category: a live preview avatar plus name, type,
 * color and icon. In edit mode it also hosts the delete flow, which reassigns
 * any movements before removing the category.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryEditorScreen(
    route: CategoryEditorRoute,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CategoryEditorViewModel =
        hiltViewModel<CategoryEditorViewModel, CategoryEditorViewModel.Factory>(
            creationCallback = { factory -> factory.create(route) },
        ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val writeFailedMessage = stringResource(R.string.editor_write_failed)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                CategoryEditorEvent.Saved,
                CategoryEditorEvent.Deleted,
                CategoryEditorEvent.CategoryMissing,
                -> onNavigateBack()

                CategoryEditorEvent.WriteFailed -> snackbarHostState.showSnackbar(writeFailedMessage)
            }
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (uiState.isNew) {
                                R.string.category_editor_title_new
                            } else {
                                R.string.category_editor_title_edit
                            },
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.action_close),
                        )
                    }
                },
                actions = {
                    if (!uiState.isNew) {
                        IconButton(onClick = viewModel::requestDelete) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = stringResource(R.string.category_editor_delete),
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (!uiState.isLoading) {
                EditorBottomBar {
                    EditorSaveButton(
                        text = stringResource(R.string.category_editor_save),
                        onClick = viewModel::save,
                        enabled = !uiState.isLoading,
                    )
                }
            }
        },
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            EditorForm(
                uiState = uiState,
                onNameChanged = viewModel::onNameChanged,
                onTypeChanged = viewModel::onTypeChanged,
                onColorSelected = viewModel::onColorSelected,
                onIconSelected = viewModel::onIconSelected,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            )
        }
    }

    CategoryDeleteDialogHost(
        dialog = uiState.deleteDialog,
        categoryName = uiState.name,
        alsoRemovesBudget = uiState.deleteAlsoRemovesBudget,
        onTargetSelected = viewModel::onReassignTargetSelected,
        onConfirm = viewModel::confirmDelete,
        onDismiss = viewModel::dismissDeleteDialog,
    )
}

@Composable
private fun EditorForm(
    uiState: CategoryEditorUiState,
    onNameChanged: (String) -> Unit,
    onTypeChanged: (CategoryType) -> Unit,
    onColorSelected: (Int) -> Unit,
    onIconSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Spacer(Modifier.height(16.dp))
        Preview(uiState = uiState)
        Spacer(Modifier.height(24.dp))
        NameField(
            name = uiState.name,
            showError = uiState.showValidation && !uiState.isNameValid,
            onNameChanged = onNameChanged,
        )
        SectionLabel(stringResource(R.string.category_editor_section_type))
        TypeChips(selected = uiState.type, onTypeChanged = onTypeChanged)
        SectionLabel(stringResource(R.string.category_editor_section_color))
        CategoryColorPicker(selected = uiState.color, onColorSelected = onColorSelected)
        SectionLabel(stringResource(R.string.category_editor_section_icon))
        CategoryIconPicker(
            selectedIcon = uiState.icon,
            selectedColor = uiState.color,
            onIconSelected = onIconSelected,
        )
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun Preview(uiState: CategoryEditorUiState, modifier: Modifier = Modifier) {
    val previewCategory = Category(
        name = uiState.name,
        type = uiState.type,
        color = uiState.color,
        icon = uiState.icon,
    )
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CategoryAvatar(category = previewCategory, size = 72.dp)
        if (uiState.name.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = uiState.name.trim(),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 24.dp, bottom = 12.dp),
    )
}

@Composable
private fun NameField(
    name: String,
    showError: Boolean,
    onNameChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = name,
        onValueChange = onNameChanged,
        label = { Text(stringResource(R.string.category_editor_name)) },
        singleLine = true,
        isError = showError,
        supportingText = if (showError) {
            { Text(stringResource(R.string.category_editor_name_error)) }
        } else {
            null
        },
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun TypeChips(
    selected: CategoryType,
    onTypeChanged: (CategoryType) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        CategoryType.entries.forEach { type ->
            val isSelected = type == selected
            FilterChip(
                selected = isSelected,
                onClick = { onTypeChanged(type) },
                label = { Text(stringResource(type.labelRes())) },
                leadingIcon = if (isSelected) {
                    {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                } else {
                    null
                },
            )
        }
    }
}
