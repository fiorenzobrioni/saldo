package com.callbackdev.saldo.core.designsystem.component

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Drag-to-reorder support for a [androidx.compose.foundation.lazy.LazyColumn].
 *
 * The backing list is reordered live while dragging (via [onMove]), so at drop
 * the dragged item already sits in its final slot and no snap animation is
 * needed; [onSettle] then persists the new order. All coordinates come from the
 * list layout on every read, so the offset self-corrects across relayouts and
 * auto-scrolls.
 */
class ReorderableListState internal constructor(
    val listState: LazyListState,
    private val onMove: (from: Int, to: Int) -> Unit,
    private val onSettle: () -> Unit,
    private val onPickUp: () -> Unit,
) {

    /** Index of the item under the finger, or null when idle. */
    var draggingItemIndex by mutableStateOf<Int?>(null)
        private set

    private var draggingItemInitialOffset = 0
    private var draggingDistance by mutableFloatStateOf(0f)
    private var autoScrollSpeed by mutableFloatStateOf(0f)

    val isDragging: Boolean get() = draggingItemIndex != null

    private val draggingItemLayoutInfo: LazyListItemInfo?
        get() = listState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.index == draggingItemIndex }

    /** Vertical translation (px) to apply to the item currently being dragged. */
    val draggingItemOffset: Float
        get() = draggingItemLayoutInfo?.let { item ->
            draggingItemInitialOffset + draggingDistance - item.offset
        } ?: 0f

    fun onDragStart(index: Int) {
        val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return
        draggingItemIndex = index
        draggingItemInitialOffset = info.offset
        draggingDistance = 0f
        onPickUp()
    }

    fun onDrag(delta: Float) {
        draggingDistance += delta
        val dragging = draggingItemLayoutInfo ?: return
        val fromIndex = draggingItemIndex ?: return
        val startOffset = dragging.offset + draggingItemOffset
        val endOffset = startOffset + dragging.size
        val middle = startOffset + dragging.size / 2f

        val target = listState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
            item.index != fromIndex && middle.toInt() in item.offset..(item.offset + item.size)
        }
        if (target != null) {
            onMove(fromIndex, target.index)
            draggingItemIndex = target.index
        }
        autoScrollSpeed = edgeScrollSpeed(startOffset, endOffset)
    }

    fun onDragEnd() {
        val moved = draggingItemIndex != null
        reset()
        if (moved) onSettle()
    }

    fun onDragCancel() = reset()

    /** Runs the continuous edge auto-scroll while an item is held near a border. */
    internal suspend fun autoScrollLoop() {
        while (isDragging) {
            val speed = autoScrollSpeed
            if (speed != 0f) listState.scrollBy(speed)
            withFrameNanos { }
        }
    }

    private fun edgeScrollSpeed(startOffset: Float, endOffset: Float): Float {
        val info = listState.layoutInfo
        val viewportStart = info.viewportStartOffset.toFloat()
        val viewportEnd = info.viewportEndOffset.toFloat()
        return when {
            endOffset > viewportEnd - EDGE_THRESHOLD ->
                (endOffset - (viewportEnd - EDGE_THRESHOLD)).coerceAtMost(MAX_STEP)

            startOffset < viewportStart + EDGE_THRESHOLD ->
                (startOffset - (viewportStart + EDGE_THRESHOLD)).coerceAtLeast(-MAX_STEP)

            else -> 0f
        }
    }

    private fun reset() {
        draggingItemIndex = null
        draggingDistance = 0f
        draggingItemInitialOffset = 0
        autoScrollSpeed = 0f
    }

    private companion object {
        const val EDGE_THRESHOLD = 96f
        const val MAX_STEP = 18f
    }
}

/**
 * Remembers a [ReorderableListState]. [onMove] reorders the caller's backing
 * list (a move from one index to another); [onSettle] fires once at drop to
 * persist the final order.
 */
@Composable
fun rememberReorderableListState(
    listState: LazyListState,
    onMove: (from: Int, to: Int) -> Unit,
    onSettle: () -> Unit,
): ReorderableListState {
    val latestOnMove by rememberUpdatedState(onMove)
    val latestOnSettle by rememberUpdatedState(onSettle)
    val haptics = LocalHapticFeedback.current
    val state = remember(listState) {
        ReorderableListState(
            listState = listState,
            onMove = { from, to -> latestOnMove(from, to) },
            onSettle = {
                haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
                latestOnSettle()
            },
            onPickUp = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
        )
    }
    LaunchedEffect(state.isDragging) {
        if (state.isDragging) state.autoScrollLoop()
    }
    return state
}

/**
 * Attaches this drag handle to the reorderable [state] for the item at [index].
 * Dragging starts immediately (no long press) since the handle is a dedicated
 * affordance, and the gesture is consumed so the list does not scroll under it.
 */
fun Modifier.reorderableHandle(
    state: ReorderableListState,
    index: Int,
): Modifier = pointerInput(index) {
    detectDragGestures(
        onDragStart = { state.onDragStart(index) },
        onDragEnd = { state.onDragEnd() },
        onDragCancel = { state.onDragCancel() },
        onDrag = { change, dragAmount ->
            change.consume()
            state.onDrag(dragAmount.y)
        },
    )
}
