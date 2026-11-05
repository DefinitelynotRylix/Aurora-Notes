package com.aurora.notes.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Smoothly morphs notes between a 2-column Masonry Staggered Grid (`isGridView = true`)
 * and a Full-Width Single Column layout (`isGridView = false`), with interactive
 * Hold-to-Elevate, subtle wobbly physics, neighbor hover interaction, and Drag-to-Reorder
 * that works in both Masonry and Single Column modes.
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
    val expandProgress by animateFloatAsState(
        targetValue = if (isGridView) 0f else 1f,
        animationSpec = tween(
            durationMillis = 320,
            easing = FastOutSlowInEasing
        ),
        label = "morphing_notes_grid_progress"
    )

    val scrollState = rememberScrollState()
    val smoothedHeights = remember { HashMap<String, Float>() }
    val coroutineScope = rememberCoroutineScope()
    var previousCount by remember { mutableIntStateOf(-1) }
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

    // Animated positions and bounds per note ID so cards smoothly glide into new slots
    val animXMap = remember { HashMap<String, Float>() }
    val animYMap = remember { HashMap<String, Float>() }
    val slotBoundsMap = remember { HashMap<Int, Rect>() }
    val cardBoundsMap = remember { HashMap<String, Rect>() }

    var isAnyCardGliding by remember { mutableStateOf(false) }
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
            smoothedHeights.keys.retainAll(validSet)
        }
    }

    val dragElevation by animateFloatAsState(
        targetValue = if (draggedNoteId != null) 1f else 0f,
        animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
        label = "drag_elevation_progress"
    )

    val isFrameLoopActive = draggedNoteId != null || droppingNoteId != null || isAnyCardGliding
    LaunchedEffect(isFrameLoopActive) {
        if (isFrameLoopActive) {
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
                            if (expandedRect.contains(androidx.compose.ui.geometry.Offset(centerX, centerY))) {
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
                                isAnyCardGliding = true
                            }
                        } else if (bestSlotIdx >= 0 && bestSlotIdx != currentSlotIdx) {
                            liveOrderIds.removeAt(currentSlotIdx)
                            liveOrderIds.add(bestSlotIdx, activeId)
                            isAnyCardGliding = true
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
                            isAnyCardGliding = false
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            currentOnReorderNotes?.invoke(finalOrder)
                        } else {
                            // Returned to original slot (or was just a hold without reorder)
                            animXMap[finishedId] = dragStartX + dragDeltaX
                            animYMap[finishedId] = dragStartY + dragDeltaY
                            droppingNoteId = finishedId
                            isAnyCardGliding = true
                            if (!dragMoved) {
                                currentOnReleaseWithoutDrag?.invoke(finishedId)
                            }
                        }
                    }
                    dragMoved = false
                    dragDeltaX = 0f
                    dragDeltaY = 0f
                },
                onDragCancel = {
                    val cancelledId = draggedNoteId
                    if (cancelledId != null) {
                        if (initialOrderOnDragStart.isNotEmpty() && liveOrderIds.toList() != initialOrderOnDragStart.toList()) {
                            liveOrderIds.clear()
                            liveOrderIds.addAll(initialOrderOnDragStart)
                        }
                        animXMap[cancelledId] = dragStartX + dragDeltaX
                        animYMap[cancelledId] = dragStartY + dragDeltaY
                        droppingNoteId = cancelledId
                        isAnyCardGliding = true
                    }
                    draggedNoteId = null
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
            // Read wobbleTimeMs to re-layout/re-place smoothly during active drag or settle
            val currentWobbleMs = wobbleTimeMs

            if (measurables.isEmpty()) {
                smoothedHeights.clear()
                previousCount = 0
                return@Layout layout(constraints.maxWidth, 0) {}
            }

            if (previousCount != measurables.size) {
                previousCount = measurables.size
                smoothedHeights.clear()
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
            val colItemWidth = totalWidth

            val p = expandProgress.coerceIn(0f, 1f)
            val isMorphAnimating = p > 0.001f && p < 0.999f

            val currentItemWidth = (gridItemWidth + (colItemWidth - gridItemWidth) * p)
                .roundToInt()
                .coerceIn(gridItemWidth, colItemWidth)

            val itemConstraints = Constraints.fixedWidth(currentItemWidth)
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

            val effectiveHeightsByOrigIdx = IntArray(placeables.size)
            for (i in placeables.indices) {
                val id = ids[i]
                val targetH = placeables[i].height.toFloat()
                val prevH = smoothedHeights[id]
                val nextH = if (!isMorphAnimating || prevH == null || abs(targetH - prevH) < 1f) {
                    targetH
                } else {
                    prevH + (targetH - prevH) * 0.35f
                }
                smoothedHeights[id] = nextH
                effectiveHeightsByOrigIdx[i] = nextH.roundToInt()
            }

            // 1. Compute 2-column Masonry Grid positions for each slot in effectiveOrderIds
            val colHeights = IntArray(2) { 0 }
            val slotGridX = IntArray(effectiveOrderIds.size)
            val slotGridY = IntArray(effectiveOrderIds.size)
            for (slotIdx in effectiveOrderIds.indices) {
                val origIdx = idToOrigIndex[effectiveOrderIds[slotIdx]] ?: slotIdx
                val itemH = effectiveHeightsByOrigIdx[origIdx]
                val lane = if (colHeights[0] <= colHeights[1]) 0 else 1
                slotGridX[slotIdx] = if (lane == 0) 0 else (totalWidth - gridItemWidth)
                slotGridY[slotIdx] = colHeights[lane]
                colHeights[lane] += itemH + vGap
            }
            val gridTotalHeight = max(0, max(colHeights[0], colHeights[1]) - vGap)

            // 2. Compute Single-Column vertical stack positions for each slot in effectiveOrderIds
            var runningY = 0
            val slotColX = IntArray(effectiveOrderIds.size)
            val slotColY = IntArray(effectiveOrderIds.size)
            for (slotIdx in effectiveOrderIds.indices) {
                val origIdx = idToOrigIndex[effectiveOrderIds[slotIdx]] ?: slotIdx
                val itemH = effectiveHeightsByOrigIdx[origIdx]
                slotColX[slotIdx] = 0
                slotColY[slotIdx] = runningY
                runningY += itemH + vGap
            }
            val colTotalHeight = max(0, runningY - vGap)

            // 3. Interpolated target (slotX, slotY) for each slot
            val slotTargetX = IntArray(effectiveOrderIds.size)
            val slotTargetY = IntArray(effectiveOrderIds.size)
            slotBoundsMap.clear()

            for (slotIdx in effectiveOrderIds.indices) {
                val origIdx = idToOrigIndex[effectiveOrderIds[slotIdx]] ?: slotIdx
                val itemH = effectiveHeightsByOrigIdx[origIdx]
                val x = (slotGridX[slotIdx] + (slotColX[slotIdx] - slotGridX[slotIdx]) * p).roundToInt()
                val rawY = (slotGridY[slotIdx] + (slotColY[slotIdx] - slotGridY[slotIdx]) * p).roundToInt()
                val y = if (slotIdx == 0) 0 else if (slotIdx == 1 && p <= 0.001f) 0 else rawY
                slotTargetX[slotIdx] = x
                slotTargetY[slotIdx] = y
                slotBoundsMap[slotIdx] = Rect(
                    left = x.toFloat(),
                    top = y.toFloat(),
                    right = (x + currentItemWidth).toFloat(),
                    bottom = (y + itemH).toFloat()
                )
            }

            val interpolatedTotalHeight = (gridTotalHeight + (colTotalHeight - gridTotalHeight) * p)
                .roundToInt()
                .coerceAtLeast(0)

            val layoutHeight = max(interpolatedTotalHeight, constraints.minHeight)

            // Update animated positions per note ID
            var stillGliding = false
            val activeDragId = draggedNoteId
            val activeDropId = droppingNoteId

            for (slotIdx in effectiveOrderIds.indices) {
                val id = effectiveOrderIds[slotIdx]
                val tx = slotTargetX[slotIdx].toFloat()
                val ty = slotTargetY[slotIdx].toFloat()

                if (id == activeDragId) {
                    animXMap[id] = dragStartX + dragDeltaX
                    animYMap[id] = dragStartY + dragDeltaY
                } else {
                    val prevX = animXMap[id]
                    val prevY = animYMap[id]
                    if (prevX == null || prevY == null || (activeDragId == null && activeDropId == null && !isAnyCardGliding)) {
                        animXMap[id] = tx
                        animYMap[id] = ty
                    } else {
                        val dx = tx - prevX
                        val dy = ty - prevY
                        if (hypot(dx, dy) > 0.75f) {
                            stillGliding = true
                            animXMap[id] = prevX + dx * 0.28f
                            animYMap[id] = prevY + dy * 0.28f
                        } else {
                            animXMap[id] = tx
                            animYMap[id] = ty
                        }
                    }
                }
            }

            if (!stillGliding && activeDragId == null) {
                if (isAnyCardGliding) isAnyCardGliding = false
                if (droppingNoteId != null) droppingNoteId = null
            }

            // Compute dragged card center for neighbor hover interaction
            val dragCenterX: Float
            val dragCenterY: Float
            if (activeDragId != null) {
                val dOrigIdx = idToOrigIndex[activeDragId] ?: 0
                val dH = effectiveHeightsByOrigIdx[dOrigIdx]
                dragCenterX = (dragStartX + dragDeltaX) + currentItemWidth * 0.5f
                dragCenterY = (dragStartY + dragDeltaY) + dH * 0.5f
            } else {
                dragCenterX = -10000f
                dragCenterY = -10000f
            }

            val phaseRad = (currentWobbleMs.toDouble() / 1000.0 * 16.5).toFloat()

            layout(totalWidth, layoutHeight) {
                for (slotIdx in effectiveOrderIds.indices) {
                    val id = effectiveOrderIds[slotIdx]
                    val origIdx = idToOrigIndex[id] ?: slotIdx
                    val placeable = placeables[origIdx]
                    val itemH = effectiveHeightsByOrigIdx[origIdx]

                    val drawX = (animXMap[id] ?: slotTargetX[slotIdx].toFloat()).roundToInt()
                    val drawY = (animYMap[id] ?: slotTargetY[slotIdx].toFloat()).roundToInt()

                    cardBoundsMap[id] = Rect(
                        left = drawX.toFloat(),
                        top = drawY.toFloat(),
                        right = (drawX + currentItemWidth).toFloat(),
                        bottom = (drawY + itemH).toFloat()
                    )

                    if (id == activeDragId) {
                        // Held / dragged card: elevates from its place with a gentle wobbly oscillation
                        val elev = dragElevation.coerceIn(0f, 1f)
                        val scale = 1f + 0.045f * elev
                        val wobbleDeg = sin(phaseRad) * 1.15f * elev
                        placeable.placeRelativeWithLayer(
                            x = drawX,
                            y = drawY,
                            zIndex = 30f
                        ) {
                            scaleX = scale
                            scaleY = scale
                            rotationZ = wobbleDeg
                            shadowElevation = 22f * elev
                        }
                    } else if (id == activeDropId) {
                        // Dropping card locking smoothly into its target slot
                        val distRemaining = hypot(
                            slotTargetX[slotIdx].toFloat() - drawX.toFloat(),
                            slotTargetY[slotIdx].toFloat() - drawY.toFloat()
                        )
                        val settleFactor = (distRemaining / 42f).coerceIn(0f, 1f)
                        val scale = 1f + 0.03f * settleFactor
                        val wobbleDeg = sin(phaseRad * 1.2f) * 0.7f * settleFactor
                        placeable.placeRelativeWithLayer(
                            x = drawX,
                            y = drawY,
                            zIndex = 15f
                        ) {
                            scaleX = scale
                            scaleY = scale
                            rotationZ = wobbleDeg
                        }
                    } else if (activeDragId != null || stillGliding) {
                        // Neighbor notes in the same group react with a subtle sympathetic wobble when hovered near or gliding to a new slot
                        val isSameGroupAsDragged = activeDragId != null &&
                            ((id in currentLockedItemIds) == (activeDragId in currentLockedItemIds))
                        val cardCenterX = drawX + currentItemWidth * 0.5f
                        val cardCenterY = drawY + itemH * 0.5f
                        val distToDragged = hypot(dragCenterX - cardCenterX, dragCenterY - cardCenterY)
                        val hoverProximity = if (isSameGroupAsDragged) {
                            (1f - (distToDragged / (currentItemWidth * 1.15f))).coerceIn(0f, 1f)
                        } else {
                            0f
                        }

                        val glideDist = hypot(
                            slotTargetX[slotIdx].toFloat() - drawX.toFloat(),
                            slotTargetY[slotIdx].toFloat() - drawY.toFloat()
                        )
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
                    } else {
                        placeable.placeRelative(drawX, drawY)
                    }
                }
            }
        }
    }
}
