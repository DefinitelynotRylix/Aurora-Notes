package com.aurora.notes.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Smoothly morphs notes between a 2-column Masonry Staggered Grid (`isGridView = true`)
 * and a Full-Width Single Column layout (`isGridView = false`), with interactive
 * Hold-to-Elevate, subtle wobbly physics, neighbor hover interaction, and Drag-to-Reorder
 * that works in both Masonry and Single Column modes at 60/120fps.
 *
 * Performance architecture:
 * - Grid <-> Column morph measures cards ONCE at target width and animates coordinates + subtle
 *   GPU scale strictly inside placement (`layout {}`) and `placeRelativeWithLayer {}` (0 per-frame
 *   child re-measurements, 0 recompositions).
 * - Drag wobble, elevation, and drop-back settle read animation state strictly in placement /
 *   graphicsLayer and NEVER mutate Compose snapshot state inside the measure/layout block.
 */
@Composable
fun MorphingNotesGrid(
    isGridView: Boolean,
    horizontalSpacing: Dp = 10.dp,
    verticalSpacing: Dp = 10.dp,
    contentPadding: PaddingValues = PaddingValues(bottom = 96.dp),
    itemIds: List<String> = emptyList(),
    lockedItemIds: Set<String> = emptySet(),
    onHoldNote: ((String) -> Unit)? = null,
    onReleaseHoldWithoutDrag: ((String) -> Unit)? = null,
    onReorderNotes: ((List<String>) -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    val currentItemIds by rememberUpdatedState(itemIds)
    val currentLockedItemIds by rememberUpdatedState(lockedItemIds)
    val currentOnHoldNote by rememberUpdatedState(onHoldNote)
    val currentOnReleaseWithoutDrag by rememberUpdatedState(onReleaseHoldWithoutDrag)
    val currentOnReorderNotes by rememberUpdatedState(onReorderNotes)

    // Live slot permutation order during drag-to-reorder
    val liveOrderIds = remember { mutableStateListOf<String>() }
    val initialOrderOnDragStart = remember { mutableStateListOf<String>() }
    var draggedNoteId by remember { mutableStateOf<String?>(null) }
    var droppingNoteId by remember { mutableStateOf<String?>(null) }
    var dragMoved by remember { mutableStateOf(false) }

    var dragStartX by remember { mutableFloatStateOf(0f) }
    var dragStartY by remember { mutableFloatStateOf(0f) }
    var dragDeltaX by remember { mutableFloatStateOf(0f) }
    var dragDeltaY by remember { mutableFloatStateOf(0f) }

    var dropStartX by remember { mutableFloatStateOf(0f) }
    var dropStartY by remember { mutableFloatStateOf(0f) }

    // Placement-only refs (never cause recomposition or child re-measure)
    val animXMap = remember { HashMap<String, Float>() }
    val animYMap = remember { HashMap<String, Float>() }
    val slotBoundsMap = remember { HashMap<Int, Rect>() }
    val cardBoundsMap = remember { HashMap<String, Rect>() }

    // GPU-only Grid <-> Column FLIP morph state
    val morphAnim = remember { Animatable(1f) }
    val morphStartPosMap = remember { HashMap<String, Offset>() }
    var lastGridViewMode by remember { mutableStateOf(isGridView) }
    var morphActive by remember { mutableStateOf(false) }
    var previousLayoutHeight by remember { mutableIntStateOf(0) }
    var morphStartLayoutHeight by remember { mutableIntStateOf(0) }
    var previousCount by remember { mutableIntStateOf(-1) }

    // Smooth GPU elevation & drop-back settle animatables (read strictly in placement / graphicsLayer)
    val dragElevationAnim = remember { Animatable(0f) }
    val dropSettleAnim = remember { Animatable(1f) }
    var dropSettleJob by remember { mutableStateOf<Job?>(null) }
    var wobbleTimeMs by remember { mutableLongStateOf(0L) }

    // Sync liveOrderIds ONLY when itemIds changes from upstream (never overwrite with stale itemIds on drag end!)
    LaunchedEffect(itemIds) {
        if (draggedNoteId == null) {
            if (liveOrderIds.toList() != itemIds) {
                liveOrderIds.clear()
                liveOrderIds.addAll(itemIds)
            }
            val validSet = itemIds.toSet()
            animXMap.keys.retainAll(validSet)
            animYMap.keys.retainAll(validSet)
            cardBoundsMap.keys.retainAll(validSet)
            morphStartPosMap.keys.retainAll(validSet)
        }
    }

    // Trigger zero-reflow GPU morph when switching between Masonry Grid and Single Column
    LaunchedEffect(isGridView) {
        if (isGridView != lastGridViewMode) {
            morphStartPosMap.clear()
            for ((id, rect) in cardBoundsMap) {
                morphStartPosMap[id] = Offset(rect.left, rect.top)
            }
            morphStartLayoutHeight = previousLayoutHeight
            lastGridViewMode = isGridView
            morphActive = true
            morphAnim.snapTo(0f)
            morphAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 240,
                    easing = FastOutSlowInEasing
                )
            )
            morphStartPosMap.clear()
            morphActive = false
        }
    }

    // Smooth GPU elevation on hold / release without recomposing children
    LaunchedEffect(draggedNoteId != null) {
        val target = if (draggedNoteId != null) 1f else 0f
        dragElevationAnim.animateTo(
            targetValue = target,
            animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing)
        )
    }

    // Frame clock active ONLY while actively dragging a card (stopped immediately on drop)
    val isDragClockActive = draggedNoteId != null
    LaunchedEffect(isDragClockActive) {
        if (isDragClockActive) {
            val startNs = System.nanoTime()
            while (true) {
                withFrameNanos { nowNs ->
                    wobbleTimeMs = (nowNs - startNs) / 1_000_000L
                }
            }
        } else {
            wobbleTimeMs = 0L
        }
    }

    val dragGestureModifier = if (itemIds.isNotEmpty() && (onHoldNote != null || onReorderNotes != null)) {
        Modifier.pointerInput(itemIds.size, isGridView) {
            detectDragGesturesAfterLongPress(
                onDragStart = { downOffset ->
                    val hitEntry = cardBoundsMap.entries.firstOrNull { (_, rect) ->
                        rect.contains(downOffset)
                    }
                    val hitId = hitEntry?.key ?: return@detectDragGesturesAfterLongPress
                    val hitRect = hitEntry.value

                    dropSettleJob?.cancel()
                    dropSettleJob = null

                    if (liveOrderIds.size != currentItemIds.size || liveOrderIds.toSet() != currentItemIds.toSet()) {
                        liveOrderIds.clear()
                        liveOrderIds.addAll(currentItemIds)
                    }
                    initialOrderOnDragStart.clear()
                    initialOrderOnDragStart.addAll(liveOrderIds)

                    draggedNoteId = hitId
                    droppingNoteId = null
                    dragMoved = false
                    dragStartX = animXMap[hitId] ?: hitRect.left
                    dragStartY = animYMap[hitId] ?: hitRect.top
                    dragDeltaX = 0f
                    dragDeltaY = 0f

                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    currentOnHoldNote?.invoke(hitId)
                },
                onDrag = { change, dragAmount ->
                    val activeId = draggedNoteId ?: return@detectDragGesturesAfterLongPress
                    change.consume()
                    dragDeltaX += dragAmount.x
                    dragDeltaY += dragAmount.y

                    if (!dragMoved && hypot(dragDeltaX, dragDeltaY) > 10f) {
                        dragMoved = true
                    }

                    val activeRect = cardBoundsMap[activeId]
                    val cardW = activeRect?.width ?: 1f
                    val cardH = activeRect?.height ?: 1f
                    val centerX = dragStartX + dragDeltaX + cardW * 0.5f
                    val centerY = dragStartY + dragDeltaY + cardH * 0.5f

                    val isDraggedPinned = activeId in currentLockedItemIds
                    val currentSlotIdx = liveOrderIds.indexOf(activeId)
                    if (currentSlotIdx >= 0) {
                        var bestSlotIdx = -1
                        var bestDist = Float.MAX_VALUE
                        for (slotIdx in 0 until liveOrderIds.size) {
                            val rect = slotBoundsMap[slotIdx] ?: continue
                            val expandedRect = Rect(
                                left = rect.left - rect.width * 0.08f,
                                top = rect.top - rect.height * 0.08f,
                                right = rect.right + rect.width * 0.08f,
                                bottom = rect.bottom + rect.height * 0.08f
                            )
                            if (expandedRect.contains(Offset(centerX, centerY))) {
                                val dist = hypot(centerX - rect.center.x, centerY - rect.center.y)
                                if (dist < bestDist) {
                                    bestDist = dist
                                    bestSlotIdx = slotIdx
                                }
                            }
                        }

                        // Check if dragged into the opposite group's zone (pinned vs unpinned)
                        var crossedIntoOppositeGroup = false
                        if (bestSlotIdx >= 0) {
                            val occupantId = liveOrderIds[bestSlotIdx]
                            val isOccupantPinned = occupantId in currentLockedItemIds
                            if (isOccupantPinned != isDraggedPinned) {
                                crossedIntoOppositeGroup = true
                            }
                        } else if (currentLockedItemIds.isNotEmpty() && currentLockedItemIds.size < liveOrderIds.size) {
                            var maxPinnedBottom = Float.MIN_VALUE
                            var minUnpinnedTop = Float.MAX_VALUE
                            for (slotIdx in 0 until liveOrderIds.size) {
                                val r = slotBoundsMap[slotIdx] ?: continue
                                val occPin = liveOrderIds[slotIdx] in currentLockedItemIds
                                if (occPin) {
                                    if (r.bottom > maxPinnedBottom) maxPinnedBottom = r.bottom
                                } else {
                                    if (r.top < minUnpinnedTop) minUnpinnedTop = r.top
                                }
                            }
                            if (isDraggedPinned && minUnpinnedTop < Float.MAX_VALUE && centerY > minUnpinnedTop) {
                                crossedIntoOppositeGroup = true
                            } else if (!isDraggedPinned && maxPinnedBottom > Float.MIN_VALUE && centerY < maxPinnedBottom) {
                                crossedIntoOppositeGroup = true
                            }
                        }

                        if (crossedIntoOppositeGroup) {
                            // Trying to move pinned into unpinned or unpinned into pinned -> revert to original order
                            if (liveOrderIds.toList() != initialOrderOnDragStart.toList()) {
                                liveOrderIds.clear()
                                liveOrderIds.addAll(initialOrderOnDragStart)
                            }
                        } else if (bestSlotIdx >= 0 && bestSlotIdx != currentSlotIdx) {
                            liveOrderIds.removeAt(currentSlotIdx)
                            liveOrderIds.add(bestSlotIdx, activeId)
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    }
                },
                onDragEnd = {
                    val finishedId = draggedNoteId
                    if (finishedId != null) {
                        val finalOrder = liveOrderIds.toList()
                        val initialOrder = initialOrderOnDragStart.toList()
                        val didReorder = dragMoved && finalOrder.size == initialOrder.size && finalOrder != initialOrder
                        val releaseX = dragStartX + dragDeltaX
                        val releaseY = dragStartY + dragDeltaY

                        draggedNoteId = null
                        if (didReorder) {
                            // Instant move into the new locked slot without jumping back to the original slot
                            for (slotIdx in finalOrder.indices) {
                                val id = finalOrder[slotIdx]
                                val targetRect = slotBoundsMap[slotIdx]
                                if (targetRect != null) {
                                    animXMap[id] = targetRect.left
                                    animYMap[id] = targetRect.top
                                }
                            }
                            droppingNoteId = null
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            currentOnReorderNotes?.invoke(finalOrder)
                        } else if (dragMoved) {
                            // Smooth GPU-driven drop-back into original slot (no layout invalidation or measure spike)
                            dropStartX = releaseX
                            dropStartY = releaseY
                            droppingNoteId = finishedId
                            dropSettleJob?.cancel()
                            dropSettleJob = coroutineScope.launch {
                                dropSettleAnim.snapTo(0f)
                                dropSettleAnim.animateTo(
                                    targetValue = 1f,
                                    animationSpec = tween(
                                        durationMillis = 175,
                                        easing = FastOutSlowInEasing
                                    )
                                )
                                droppingNoteId = null
                            }
                        } else {
                            // Released in place without dragging: zero glide needed, just settle elevation cleanly
                            droppingNoteId = null
                            currentOnReleaseWithoutDrag?.invoke(finishedId)
                        }
                    }
                    dragMoved = false
                    dragDeltaX = 0f
                    dragDeltaY = 0f
                },
                onDragCancel = {
                    val cancelledId = draggedNoteId
                    if (cancelledId != null) {
                        val releaseX = dragStartX + dragDeltaX
                        val releaseY = dragStartY + dragDeltaY
                        if (initialOrderOnDragStart.isNotEmpty() && liveOrderIds.toList() != initialOrderOnDragStart.toList()) {
                            liveOrderIds.clear()
                            liveOrderIds.addAll(initialOrderOnDragStart)
                        }
                        draggedNoteId = null
                        if (dragMoved) {
                            dropStartX = releaseX
                            dropStartY = releaseY
                            droppingNoteId = cancelledId
                            dropSettleJob?.cancel()
                            dropSettleJob = coroutineScope.launch {
                                dropSettleAnim.snapTo(0f)
                                dropSettleAnim.animateTo(
                                    targetValue = 1f,
                                    animationSpec = tween(
                                        durationMillis = 175,
                                        easing = FastOutSlowInEasing
                                    )
                                )
                                droppingNoteId = null
                            }
                        } else {
                            droppingNoteId = null
                        }
                    }
                    dragMoved = false
                    dragDeltaX = 0f
                    dragDeltaY = 0f
                }
            )
        }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState, enabled = draggedNoteId == null)
            .padding(contentPadding),
        contentAlignment = Alignment.TopStart
    ) {
        Layout(
            content = content,
            modifier = Modifier
                .fillMaxWidth()
                .then(dragGestureModifier)
        ) { measurables, constraints ->
            // MEASURE PHASE: Runs ONLY when data, grid mode, or slot order changes (NEVER per animation frame!)
            if (measurables.isEmpty()) {
                previousCount = 0
                previousLayoutHeight = 0
                return@Layout layout(constraints.maxWidth, 0) {}
            }

            if (previousCount != measurables.size) {
                previousCount = measurables.size
                if (measurables.size <= 2 && scrollState.value > 0) {
                    coroutineScope.launch {
                        scrollState.scrollTo(0)
                    }
                }
            }

            val totalWidth = constraints.maxWidth.coerceAtLeast(1)
            val hGap = horizontalSpacing.roundToPx()
            val vGap = verticalSpacing.roundToPx()

            val gridItemWidth = ((totalWidth - hGap) / 2).coerceAtLeast(1)
            val targetItemWidth = if (isGridView) gridItemWidth else totalWidth

            val itemConstraints = Constraints.fixedWidth(targetItemWidth)
            val placeables = measurables.map { measurable ->
                measurable.measure(itemConstraints)
            }

            val ids = if (itemIds.size == placeables.size) {
                itemIds
            } else {
                List(placeables.size) { idx -> idx.toString() }
            }

            val idToOrigIndex = HashMap<String, Int>(ids.size)
            for (i in ids.indices) {
                idToOrigIndex[ids[i]] = i
            }

            val effectiveOrderIds = if (liveOrderIds.size == ids.size && liveOrderIds.all { idToOrigIndex.containsKey(it) }) {
                liveOrderIds.toList()
            } else {
                ids
            }

            // Compute target coordinates for the active layout mode (2-column Masonry or Single Column)
            val slotTargetX = IntArray(effectiveOrderIds.size)
            val slotTargetY = IntArray(effectiveOrderIds.size)
            slotBoundsMap.clear()

            val targetTotalHeight: Int = if (isGridView) {
                val colHeights = IntArray(2) { 0 }
                for (slotIdx in effectiveOrderIds.indices) {
                    val origIdx = idToOrigIndex[effectiveOrderIds[slotIdx]] ?: slotIdx
                    val itemH = placeables[origIdx].height
                    val lane = if (colHeights[0] <= colHeights[1]) 0 else 1
                    val x = if (lane == 0) 0 else (totalWidth - gridItemWidth)
                    val y = if (slotIdx == 0 || slotIdx == 1) 0 else colHeights[lane]
                    slotTargetX[slotIdx] = x
                    slotTargetY[slotIdx] = y
                    slotBoundsMap[slotIdx] = Rect(
                        left = x.toFloat(),
                        top = y.toFloat(),
                        right = (x + targetItemWidth).toFloat(),
                        bottom = (y + itemH).toFloat()
                    )
                    colHeights[lane] = y + itemH + vGap
                }
                max(0, max(colHeights[0], colHeights[1]) - vGap)
            } else {
                var runningY = 0
                for (slotIdx in effectiveOrderIds.indices) {
                    val origIdx = idToOrigIndex[effectiveOrderIds[slotIdx]] ?: slotIdx
                    val itemH = placeables[origIdx].height
                    slotTargetX[slotIdx] = 0
                    slotTargetY[slotIdx] = runningY
                    slotBoundsMap[slotIdx] = Rect(
                        left = 0f,
                        top = runningY.toFloat(),
                        right = targetItemWidth.toFloat(),
                        bottom = (runningY + itemH).toFloat()
                    )
                    runningY += itemH + vGap
                }
                max(0, runningY - vGap)
            }

            previousLayoutHeight = targetTotalHeight
            val effectiveHeight = if (morphActive) {
                max(targetTotalHeight, morphStartLayoutHeight)
            } else {
                targetTotalHeight
            }
            val layoutHeight = max(effectiveHeight, constraints.minHeight)

            // PLACEMENT PHASE: Reads animation values here so Compose skips re-measure & recomposition at 120fps
            layout(totalWidth, layoutHeight) {
                val activeDragId = draggedNoteId
                val activeDropId = droppingNoteId
                val morphP = if (morphActive) morphAnim.value.coerceIn(0f, 1f) else 1f
                val isMorphRunning = morphActive && morphP < 0.999f
                val dropP = if (activeDropId != null) dropSettleAnim.value.coerceIn(0f, 1f) else 1f
                val currentWobbleMs = if (activeDragId != null) wobbleTimeMs else 0L
                val phaseRad = (currentWobbleMs.toDouble() / 1000.0 * 16.5).toFloat()

                val dragCenterX: Float
                val dragCenterY: Float
                if (activeDragId != null) {
                    val dOrigIdx = idToOrigIndex[activeDragId] ?: 0
                    val dH = placeables[dOrigIdx].height
                    dragCenterX = (dragStartX + dragDeltaX) + targetItemWidth * 0.5f
                    dragCenterY = (dragStartY + dragDeltaY) + dH * 0.5f
                } else {
                    dragCenterX = -10000f
                    dragCenterY = -10000f
                }

                for (slotIdx in effectiveOrderIds.indices) {
                    val id = effectiveOrderIds[slotIdx]
                    val origIdx = idToOrigIndex[id] ?: slotIdx
                    val placeable = placeables[origIdx]
                    val itemH = placeable.height
                    val tx = slotTargetX[slotIdx].toFloat()
                    val ty = slotTargetY[slotIdx].toFloat()

                    val drawXf: Float
                    val drawYf: Float

                    when {
                        id == activeDragId -> {
                            drawXf = dragStartX + dragDeltaX
                            drawYf = dragStartY + dragDeltaY
                        }
                        id == activeDropId -> {
                            drawXf = dropStartX + (tx - dropStartX) * dropP
                            drawYf = dropStartY + (ty - dropStartY) * dropP
                        }
                        isMorphRunning -> {
                            val startPos = morphStartPosMap[id]
                            if (startPos != null) {
                                drawXf = startPos.x + (tx - startPos.x) * morphP
                                drawYf = startPos.y + (ty - startPos.y) * morphP
                            } else {
                                drawXf = tx
                                drawYf = ty
                            }
                        }
                        activeDragId != null -> {
                            val prevX = animXMap[id] ?: tx
                            val prevY = animYMap[id] ?: ty
                            val dx = tx - prevX
                            val dy = ty - prevY
                            if (hypot(dx, dy) > 0.75f) {
                                drawXf = prevX + dx * 0.30f
                                drawYf = prevY + dy * 0.30f
                            } else {
                                drawXf = tx
                                drawYf = ty
                            }
                        }
                        else -> {
                            drawXf = tx
                            drawYf = ty
                        }
                    }

                    animXMap[id] = drawXf
                    animYMap[id] = drawYf
                    val drawX = drawXf.roundToInt()
                    val drawY = drawYf.roundToInt()

                    cardBoundsMap[id] = Rect(
                        left = drawXf,
                        top = drawYf,
                        right = drawXf + targetItemWidth.toFloat(),
                        bottom = drawYf + itemH.toFloat()
                    )

                    when {
                        id == activeDragId -> {
                            placeable.placeRelativeWithLayer(
                                x = drawX,
                                y = drawY,
                                zIndex = 30f
                            ) {
                                val elev = dragElevationAnim.value.coerceIn(0f, 1f)
                                val scale = 1f + 0.045f * elev
                                scaleX = scale
                                scaleY = scale
                                rotationZ = sin(phaseRad) * 1.15f * elev
                                shadowElevation = 22f * elev
                            }
                        }
                        id == activeDropId -> {
                            placeable.placeRelativeWithLayer(
                                x = drawX,
                                y = drawY,
                                zIndex = 15f
                            ) {
                                val settle = 1f - dropP
                                val scale = 1f + 0.03f * settle
                                scaleX = scale
                                scaleY = scale
                                shadowElevation = 14f * settle
                            }
                        }
                        isMorphRunning -> {
                            placeable.placeRelativeWithLayer(
                                x = drawX,
                                y = drawY,
                                zIndex = (effectiveOrderIds.size - slotIdx).toFloat() * 0.01f
                            ) {
                                val scale = 0.94f + 0.06f * morphP
                                scaleX = scale
                                scaleY = scale
                                alpha = 0.88f + 0.12f * morphP
                            }
                        }
                        activeDragId != null -> {
                            val isSameGroupAsDragged =
                                (id in currentLockedItemIds) == (activeDragId in currentLockedItemIds)
                            val cardCenterX = drawXf + targetItemWidth * 0.5f
                            val cardCenterY = drawYf + itemH * 0.5f
                            val distToDragged = hypot(dragCenterX - cardCenterX, dragCenterY - cardCenterY)
                            val hoverProximity = if (isSameGroupAsDragged) {
                                (1f - (distToDragged / (targetItemWidth * 1.15f))).coerceIn(0f, 1f)
                            } else {
                                0f
                            }
                            val glideDist = hypot(tx - drawXf, ty - drawYf)
                            val glideFactor = (glideDist / 40f).coerceIn(0f, 1f)
                            val wobbleIntensity = max(hoverProximity * 0.85f, glideFactor * 1.0f)
                            val neighborWobbleDeg = sin(phaseRad * 1.25f + origIdx * 1.7f) * wobbleIntensity
                            val neighborScale = 1f - (hoverProximity * 0.015f)

                            placeable.placeRelativeWithLayer(
                                x = drawX,
                                y = drawY,
                                zIndex = if (glideFactor > 0.05f) 5f else 0f
                            ) {
                                scaleX = neighborScale
                                scaleY = neighborScale
                                rotationZ = neighborWobbleDeg
                            }
                        }
                        else -> {
                            placeable.placeRelative(drawX, drawY)
                        }
                    }
                }
            }
        }
    }
}
