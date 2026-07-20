package com.callbackdev.saldo.feature.recap

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.designsystem.component.EmptyState
import com.callbackdev.saldo.core.designsystem.component.LoadingState
import com.callbackdev.saldo.core.designsystem.component.rememberMotionEnabled
import com.callbackdev.saldo.core.domain.model.MonthlyRecap
import com.callbackdev.saldo.navigation.MonthlyRecapRoute
import kotlinx.coroutines.launch
import androidx.compose.material.icons.outlined.AutoAwesome

/**
 * Full-screen story recap of one completed month, in the app's current theme
 * (the user's light/dark and palette choice from Settings applies here like
 * everywhere else; the shared image inherits the same look, so what is shared
 * is what was seen). Pages advance by swipe or tap (right two thirds forward,
 * left third back); the top row holds the per-page progress pills, share and
 * close.
 */
@Composable
fun MonthlyRecapScreen(
    route: MonthlyRecapRoute,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MonthlyRecapViewModel =
        hiltViewModel<MonthlyRecapViewModel, MonthlyRecapViewModel.Factory>(
            creationCallback = { factory -> factory.create(route) },
        ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel, context, resources) {
        viewModel.events.collect { event ->
            when (event) {
                is MonthlyRecapEvent.ShareReady -> {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, event.uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(
                        Intent.createChooser(send, resources.getString(R.string.recap_share_title)),
                    )
                }

                MonthlyRecapEvent.ShareFailed -> snackbarHostState.showSnackbar(
                    message = resources.getString(R.string.recap_share_failed),
                    duration = SnackbarDuration.Short,
                )
            }
        }
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            when {
                uiState.isLoading -> LoadingState(Modifier.align(Alignment.Center))
                uiState.isEmpty -> RecapEmptyContent(
                    month = recapMonthTitle(uiState.month),
                    onNavigateBack = onNavigateBack,
                )
                else -> uiState.recap?.let { recap ->
                    RecapContent(
                        recap = recap,
                        uiState = uiState,
                        onNavigateBack = onNavigateBack,
                        onShare = viewModel::onShareRequested,
                    )
                }
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(WindowInsets.navigationBars.asPaddingValues()),
            )
        }
    }
}

@Composable
private fun RecapEmptyContent(month: String, onNavigateBack: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        IconButton(
            onClick = onNavigateBack,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(WindowInsets.statusBars.asPaddingValues())
                .padding(8.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = stringResource(R.string.recap_close),
            )
        }
        EmptyState(
            icon = Icons.Outlined.AutoAwesome,
            title = stringResource(R.string.recap_empty_title, month),
            body = stringResource(R.string.recap_empty_body),
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
private fun RecapContent(
    recap: MonthlyRecap,
    uiState: MonthlyRecapUiState,
    onNavigateBack: () -> Unit,
    onShare: (androidx.compose.ui.graphics.ImageBitmap) -> Unit,
) {
    val pages = remember(recap) { recapPages(recap) }
    val pagerState = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val motionEnabled = rememberMotionEnabled()

    // The share image is recorded from an off-screen composition of the fixed
    // summary card, drawn behind the opaque page background at 3x density so
    // the exported PNG is 1080x1920 regardless of the device (ADR 28).
    val shareLayer = rememberGraphicsLayer()

    Box(Modifier.fillMaxSize()) {
        // Painted first and fully covered by the opaque page background: never
        // visible, but still laid out and drawn, so the layer records real
        // frames (an alpha-0 modifier would skip drawing entirely).
        Box(Modifier.zIndex(-1f)) {
            androidx.compose.runtime.CompositionLocalProvider(
                LocalDensity provides Density(SHARE_DENSITY),
            ) {
                Box(
                    modifier = Modifier
                        .requiredSize(SHARE_WIDTH.dp, SHARE_HEIGHT.dp)
                        .drawWithContent {
                            shareLayer.record { this@drawWithContent.drawContent() }
                            drawLayer(shareLayer)
                        },
                ) {
                    RecapShareCard(
                        recap = recap,
                        categoryById = uiState.categoryById,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                            MaterialTheme.colorScheme.background,
                        ),
                    ),
                ),
        ) {
            Column(Modifier.fillMaxSize()) {
                RecapTopBar(
                    pageCount = pages.size,
                    currentPage = pagerState.settledPage,
                    onShare = { scope.launch { onShare(shareLayer.toImageBitmap()) } },
                    onClose = onNavigateBack,
                )
                Box(Modifier.weight(1f)) {
                    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { index ->
                        RecapPageReveal(
                            visible = !motionEnabled || pagerState.settledPage == index,
                            motionEnabled = motionEnabled,
                        ) {
                            pages[index](uiState)
                        }
                    }
                    RecapTapZones(
                        onPrevious = {
                            scope.launch {
                                if (pagerState.currentPage > 0) {
                                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                }
                            }
                        },
                        onNext = {
                            scope.launch {
                                if (pagerState.currentPage < pages.size - 1) {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            }
                        },
                    )
                }
                AnimatedVisibility(visible = pagerState.settledPage == pages.size - 1) {
                    FilledTonalButton(
                        onClick = { scope.launch { onShare(shareLayer.toImageBitmap()) } },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp)
                            .padding(bottom = 8.dp)
                            .height(52.dp),
                    ) {
                        Icon(Icons.Outlined.Share, contentDescription = null)
                        Text(
                            text = stringResource(R.string.recap_share),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
                Box(Modifier.padding(WindowInsets.navigationBars.asPaddingValues()))
            }
        }
    }
}

/** The ordered page list with the skip rules applied. */
private fun recapPages(recap: MonthlyRecap): List<@Composable (MonthlyRecapUiState) -> Unit> =
    buildList {
        add { RecapHeroPage(recap) }
        add { RecapSpendingPage(recap) }
        if (recap.topCategories.isNotEmpty()) {
            add { state -> RecapTopCategoriesPage(recap, state.categoryById) }
        }
        if (recap.biggestExpense != null || recap.busiestDay != null) {
            add { state -> RecapRecordsPage(recap, state.categoryById) }
        }
        add { RecapIncomeExpensePage(recap) }
        if (recap.recurringSpend.signum() > 0) {
            add { RecapRecurringPage(recap) }
        }
        add { RecapClosingPage(recap) }
    }

@Composable
private fun RecapTopBar(
    pageCount: Int,
    currentPage: Int,
    onShare: () -> Unit,
    onClose: () -> Unit,
) {
    Column(Modifier.padding(WindowInsets.statusBars.asPaddingValues())) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val progressDescription = stringResource(
                R.string.recap_page_progress_a11y,
                currentPage + 1,
                pageCount,
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = progressDescription },
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                repeat(pageCount) { index ->
                    val active = index <= currentPage
                    val pillColor by animateColorAsState(
                        targetValue = if (active) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        label = "recapPill",
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(pillColor),
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            IconButton(onClick = onShare) {
                Icon(
                    imageVector = Icons.Outlined.Share,
                    contentDescription = stringResource(R.string.recap_share),
                )
            }
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.recap_close),
                )
            }
        }
    }
}

/** Story-style tap zones: left third back, the rest forward. */
@Composable
private fun RecapTapZones(onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = stringResource(R.string.recap_page_previous),
                    onClick = onPrevious,
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(2f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = stringResource(R.string.recap_page_next),
                    onClick = onNext,
                ),
        )
    }
}

/** Per-page entrance: fade + rise on settle, or plain content with motion off. */
@Composable
private fun RecapPageReveal(
    visible: Boolean,
    motionEnabled: Boolean,
    content: @Composable () -> Unit,
) {
    if (!motionEnabled) {
        content()
        return
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / REVEAL_OFFSET_DIVISOR }),
        exit = fadeOut(),
    ) {
        content()
    }
}

private const val SHARE_DENSITY = 3f
private const val SHARE_WIDTH = 360
private const val SHARE_HEIGHT = 640
private const val REVEAL_OFFSET_DIVISOR = 12
