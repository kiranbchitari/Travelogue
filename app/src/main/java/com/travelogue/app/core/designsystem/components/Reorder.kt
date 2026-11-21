package com.travelogue.app.core.designsystem.components

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Long-press-and-drag reordering for items of a LazyColumn (no external library).
 *
 * Only items whose key passes [canReorder] take part, so headers and other rows in the same list
 * stay fixed. [onMove] is called with the dragged key and the key it should take the place of; the
 * caller updates its local list. [onDrop] is called once when the finger lifts, to persist the order.
 */
@Stable
class ReorderState internal constructor(
    val listState: LazyListState,
    private val scope: CoroutineScope,
    private val edgePx: Float,
    private val canReorder: (Any) -> Boolean,
    private val onMoveState: androidx.compose.runtime.State<(from: Any, to: Any) -> Unit>,
    private val onDropState: androidx.compose.runtime.State<() -> Unit>,
) {
    var draggingKey by mutableStateOf<Any?>(null)
        private set

    private var dragDelta by mutableFloatStateOf(0f)
    private var startOffset = 0

    private val draggingItem: LazyListItemInfo?
        get() = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == draggingKey }

    /** How far the dragged item should be drawn from its laid-out slot so it stays under the finger. */
    val translation: Float
        get() = draggingItem?.let { startOffset + dragDelta - it.offset } ?: 0f

    internal fun start(key: Any) {
        val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return
        draggingKey = key
        startOffset = info.offset
        dragDelta = 0f
    }

    internal fun drag(dy: Float) {
        dragDelta += dy
        val item = draggingItem ?: return
        val top = startOffset + dragDelta
        val bottom = top + item.size
        val middle = (top + item.size / 2f).toInt()
        listState.layoutInfo.visibleItemsInfo
            .firstOrNull { other ->
                other.key != item.key && canReorder(other.key) && middle in other.offset..(other.offset + other.size)
            }
            ?.let { onMoveState.value(item.key, it.key) }

        // Auto-scroll when the dragged item nears the top or bottom of the viewport.
        val info = listState.layoutInfo
        val overshoot = when {
            bottom > info.viewportEndOffset - edgePx -> bottom - (info.viewportEndOffset - edgePx)
            top < info.viewportStartOffset + edgePx -> top - (info.viewportStartOffset + edgePx)
            else -> 0f
        }
        if (overshoot != 0f) scope.launch { listState.scrollBy(overshoot * 0.25f) }
    }

    internal fun end() {
        if (draggingKey != null) onDropState.value()
        draggingKey = null
        dragDelta = 0f
    }
}

@Composable
fun rememberReorderState(
    listState: LazyListState,
    canReorder: (Any) -> Boolean,
    onMove: (from: Any, to: Any) -> Unit,
    onDrop: () -> Unit,
): ReorderState {
    val scope = rememberCoroutineScope()
    val edge = with(LocalDensity.current) { 72.dp.toPx() }
    val move = rememberUpdatedState(onMove)
    val drop = rememberUpdatedState(onDrop)
    val can = rememberUpdatedState(canReorder)
    return remember(listState) { ReorderState(listState, scope, edge, { can.value(it) }, move, drop) }
}

/**
 * Makes a list item draggable after a long press. Apply to the item's root; the dragged item lifts
 * slightly and follows the finger while the others slide out of the way.
 */
fun Modifier.reorderable(state: ReorderState, key: Any): Modifier = composed {
    val haptics = LocalHapticFeedback.current
    val dragging = state.draggingKey == key
    this
        .zIndex(if (dragging) 1f else 0f)
        .graphicsLayer {
            translationY = if (dragging) state.translation else 0f
            val s = if (dragging) 1.03f else 1f
            scaleX = s
            scaleY = s
            alpha = if (dragging) 0.96f else 1f
        }
        .pointerInput(key) {
            detectDragGesturesAfterLongPress(
                onDragStart = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    state.start(key)
                },
                onDrag = { change, amount ->
                    change.consume()
                    state.drag(amount.y)
                },
                onDragEnd = { state.end() },
                onDragCancel = { state.end() },
            )
        }
}
