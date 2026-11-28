package com.aurora.notes.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private val SmoothDecelEasing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
private val AuroraGlowCore = Color(0xFFD8B4FE)
private val AuroraGlowMid = Color(0xFF8B5CF6)

private data class CornerGlowSample(
    val intensity: Float,
    val waveOffsetPx: Float,
    val scaleBoost: Float,
    val tl: Float,
    val tr: Float,
    val bl: Float,
    val br: Float
)

private fun computeCornerGlowSample(
    progress: Float,
    isDroppedCard: Boolean,
    dxFromDropped: Float,
    dyFromDropped: Float,
    maxRadiusPx: Float
): CornerGlowSample {
    if (progress <= 0f || progress >= 1f) {
        return CornerGlowSample(0f, 0f, 0f, 0f, 0f, 0f, 0f)
    }

    if (isDroppedCard) {
        val rise = (progress / 0.22f).coerceIn(0f, 1f)
        val fall = if (progress <= 0.22f) 1f else (1f - (progress - 0.22f) / 0.78f).coerceIn(0f, 1f).pow(1.75f)
        val intensity = (rise * fall).coerceIn(0f, 1f)
        return CornerGlowSample(intensity, 0f, 0f, 1f, 1f, 1f, 1f)
    }

    val safeRadius = max(1f, maxRadiusPx)
    val dist = hypot(dxFromDropped, dyFromDropped)
    if (dist >= safeRadius) {
        return CornerGlowSample(0f, 0f, 0f, 0f, 0f, 0f, 0f)
    }

    val proximity = (1f - dist / safeRadius).pow(1.2f)
    val waveDelay = (dist / safeRadius) * 0.22f
    if (progress <= waveDelay) {
        return CornerGlowSample(0f, 0f, 0f, 0f, 0f, 0f, 0f)
    }

    val localT = ((progress - waveDelay) / max(0.01f, 1f - waveDelay)).coerceIn(0f, 1f)
    val wave = (sin(localT * PI).toFloat() * (1f - localT).pow(0.45f)).coerceAtLeast(0f)
    val intensity = (wave * proximity * 0.92f).coerceIn(0f, 1f)

    val ux = -dxFromDropped / max(1f, dist)
    val uy = -dyFromDropped / max(1f, dist)
    val invSqrt2 = 1f / sqrt(2f)
    fun weightCorner(cxSign: Float, cySign: Float): Float {
        val dot = (cxSign * ux + cySign * uy) * invSqrt2
        return (0.56f + dot * 0.48f).coerceIn(0.22f, 1f)
    }

    return CornerGlowSample(
        intensity = intensity,
        waveOffsetPx = wave * proximity * 4.5f,
        scaleBoost = wave * proximity * 0.016f,
        tl = weightCorner(-1f, -1f),
        tr = weightCorner(1f, -1f),
        bl = weightCorner(-1f, 1f),
        br = weightCorner(1f, 1f)
    )
}

/**
 * Smoothly morphs notes between a 2-column Masonry Staggered Grid (`isGridView = true`)
 * and a Full-Width Single Column layout (`isGridView = false`), with reliable Hold-to-Select,
 * Hold-to-Elevate, subtle wobbly physics, smooth Drag-to-Reorder, and a luminous 4-corner
 * drop glow wave that radiates across nearby notes at 60/120fps.
 *
 * Performance architecture:
 * - Grid <-> Column morph captures prior full bounds synchronously before layout and runs a
 *   true top-left FLIP scaleX/scaleY + translation GPU transition inside placement (0 width snap,
 *   0 per-frame re-measurements).
 * - Custom `awaitEachGesture` long-press handling explicitly consumes the release UP pointer event
 *   so `NoteCard.clickable` never cancels or toggles off a long-press selection.
 * - Smooth drop settle and radiant corner glow run entirely in placement/graphicsLayer + drawWithContent.
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
    selectedTabFlow: StateFlow<Int>? = null,
    tabIndex: Int = -1,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val viewportSpanPx = max(with(density) { configuration.screenHeightDp.dp.toPx() }, 1200f)

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
    var dropStartRotation by remember { mutableFloatStateOf(0f) }

    // Placement-only refs (never cause recomposition or child re-measure)
    val animXMap = remember { HashMap<String, Float>() }
    val animYMap = remember { HashMap<String, Float>() }
    val dropStartPosMap = remember { HashMap<String, Offset>() }
    val slotBoundsMap = remember { HashMap<Int, Rect>() }
    val cardBoundsMap = remember { HashMap<String, Rect>() }

    // True GPU FLIP Grid <-> Column morph state (captured synchronously before Layout measures)
    val morphAnim = remember { Animatable(1f) }
    val morphStartRectMap = remember { HashMap<String, Rect>() }
    var lastGridViewMode by remember { mutableStateOf(isGridView) }
    var morphActive by remember { mutableStateOf(false) }
    var morphNeedsSnap by remember { mutableStateOf(false) }
    var previousLayoutHeight by remember { mutableIntStateOf(0) }
    var morphStartLayoutHeight by remember { mutableIntStateOf(0) }
    var previousCount by remember { mutableIntStateOf(-1) }

    // Capture old full card bounds BEFORE Layout measures at the new mode so Frame 0 has ZERO snap.
    // Skip morph animations on hidden preloaded tabs so toggling Grid <-> Column never animates invisible screens.
    if (isGridView != lastGridViewMode) {
        val isTabCurrentlyVisible = selectedTabFlow == null || tabIndex < 0 || selectedTabFlow.value == tabIndex
        morphStartRectMap.clear()
        if (isTabCurrentlyVisible) {
            for ((id, rect) in cardBoundsMap) {
                if (rect.width > 0f && rect.height > 0f) {
                    morphStartRectMap[id] = rect
                }
            }
        }
        morphStartLayoutHeight = previousLayoutHeight
        lastGridViewMode = isGridView
        if (isTabCurrentlyVisible && morphStartRectMap.isNotEmpty()) {
            morphActive = true
            morphNeedsSnap = true
        } else {
            morphActive = false
            morphNeedsSnap = false
        }
    }

    // Smooth GPU elevation, drop settle, and luminous corner glow wave animatables
    val dragElevationAnim = remember { Animatable(0f) }
    val dropSettleAnim = remember { Animatable(1f) }
    val dropGlowAnim = remember { Animatable(1f) }
    var glowOriginId by remember { mutableStateOf<String?>(null) }
    var dropSettleJob by remember { mutableStateOf<Job?>(null) }
    var dropGlowJob by remember { mutableStateOf<Job?>(null) }
    var wobbleTimeMs by remember { mutableLongStateOf(0L) }
    var currentDragRotDeg by remember { mutableFloatStateOf(0f) }

    // Sync liveOrderIds ONLY when itemIds changes outside active drag or drop settle
    LaunchedEffect(itemIds) {
        if (draggedNoteId == null && droppingNoteId == null) {
            if (liveOrderIds.toList() != itemIds) {
                liveOrderIds.clear()
                liveOrderIds.addAll(itemIds)
            }
            val validSet = itemIds.toSet()
            animXMap.keys.retainAll(validSet)
            animYMap.keys.retainAll(validSet)
            cardBoundsMap.keys.retainAll(validSet)
            morphStartRectMap.keys.retainAll(validSet)
        }
    }

    // Run true FLIP morph animation whenever Grid <-> Column toggles (cancels cleanly on rapid taps)
    LaunchedEffect(isGridView) {
        if (morphActive) {
            morphAnim.snapTo(0f)
            morphNeedsSnap = false
            morphAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 280,
                    easing = SmoothDecelEasing
                )
            )
            morphStartRectMap.clear()
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

    fun launchSmoothDrop(
        droppedId: String,
        targetOrder: List<String>,
        releaseX: Float,
        releaseY: Float,
        releaseRot: Float,
        withCornerGlow: Boolean
    ) {
        dropStartPosMap.clear()
        for (id in targetOrder) {
            if (id == droppedId) {
                dropStartPosMap[id] = Offset(releaseX, releaseY)
            } else {
                val curX = animXMap[id] ?: cardBoundsMap[id]?.left ?: 0f
                val curY = animYMap[id] ?: cardBoundsMap[id]?.top ?: 0f
                dropStartPosMap[id] = Offset(curX, curY)
            }
        }
        dropStartRotation = releaseRot
        droppingNoteId = droppedId

        dropSettleJob?.cancel()
        dropSettleJob = coroutineScope.launch {
            dropSettleAnim.snapTo(0f)
            dropSettleAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = if (withCornerGlow) 280 else 160,
                    easing = SmoothDecelEasing
                )
            )
            droppingNoteId = null
            dropStartPosMap.clear()
            // Sync any upstream list updates once drop settle finishes
            if (draggedNoteId == null && liveOrderIds.toList() != currentItemIds) {
                liveOrderIds.clear()
                liveOrderIds.addAll(currentItemIds)
            }
        }

        dropGlowJob?.cancel()
        if (withCornerGlow) {
            glowOriginId = droppedId
            dropGlowJob = coroutineScope.launch {
                dropGlowAnim.snapTo(0f)
                dropGlowAnim.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = 740,
                        easing = LinearEasing
                    )
                )
                glowOriginId = null
            }
        } else {
            glowOriginId = null
        }
    }

    val dragGestureModifier = if (itemIds.isNotEmpty() && (onHoldNote != null || onReorderNotes != null)) {
        Modifier.pointerInput(itemIds.size) {
            val hitPadPx = 6.dp.toPx()
            val dragMoveSlopPx = 16.dp.toPx()

            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val hitEntry = cardBoundsMap.entries.firstOrNull { (_, rect) ->
                    rect.contains(down.position)
                } ?: cardBoundsMap.entries.firstOrNull { (_, rect) ->
                    Rect(
                        left = rect.left - hitPadPx,
                        top = rect.top - hitPadPx,
                        right = rect.right + hitPadPx,
                        bottom = rect.bottom + hitPadPx
                    ).contains(down.position)
                } ?: return@awaitEachGesture

                val longPress = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                // Consume the long-press change immediately so child clickables cancel their press state
                longPress.consume()

                val hitId = hitEntry.key
                val hitRect = cardBoundsMap[hitId] ?: hitEntry.value

                dropSettleJob?.cancel()
                dropSettleJob = null
                dropGlowJob?.cancel()
                dropGlowJob = null
                glowOriginId = null

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
                currentDragRotDeg = 0f

                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                currentOnHoldNote?.invoke(hitId)

                var lastPosition = longPress.position
                var wasCancelled = false

                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change == null) {
                        wasCancelled = true
                        break
                    }
                    if (!change.pressed) {
                        // CRITICAL: Consume pointer UP so NoteCard.clickable NEVER toggles off the held selection!
                        change.consume()
                        break
                    }

                    val delta = change.position - lastPosition
                    lastPosition = change.position
                    change.consume()

                    dragDeltaX += delta.x
                    dragDeltaY += delta.y

                    if (!dragMoved && hypot(dragDeltaX, dragDeltaY) > dragMoveSlopPx) {
                        dragMoved = true
                    }

                    val activeRect = cardBoundsMap[hitId]
                    val cardW = activeRect?.width ?: 1f
                    val cardH = activeRect?.height ?: 1f
                    val centerX = dragStartX + dragDeltaX + cardW * 0.5f
                    val centerY = dragStartY + dragDeltaY + cardH * 0.5f

                    val isDraggedPinned = hitId in currentLockedItemIds
                    val currentSlotIdx = liveOrderIds.indexOf(hitId)
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
                            if (liveOrderIds.toList() != initialOrderOnDragStart.toList()) {
                                liveOrderIds.clear()
                                liveOrderIds.addAll(initialOrderOnDragStart)
                            }
                        } else if (bestSlotIdx >= 0 && bestSlotIdx != currentSlotIdx) {
                            liveOrderIds.removeAt(currentSlotIdx)
                            liveOrderIds.add(bestSlotIdx, hitId)
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    }
                }

                // Finish gesture cleanly with smooth drop settle + radiant corner glow
                val finalOrder = liveOrderIds.toList()
                val initialOrder = initialOrderOnDragStart.toList()
                val didReorder = !wasCancelled && dragMoved && finalOrder.size == initialOrder.size && finalOrder != initialOrder
                val releaseX = dragStartX + dragDeltaX
                val releaseY = dragStartY + dragDeltaY
                val releaseRot = currentDragRotDeg

                draggedNoteId = null

                when {
                    didReorder -> {
                        launchSmoothDrop(
                            droppedId = hitId,
                            targetOrder = finalOrder,
                            releaseX = releaseX,
                            releaseY = releaseY,
                            releaseRot = releaseRot,
                            withCornerGlow = true
                        )
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        currentOnReorderNotes?.invoke(finalOrder)
                    }
                    dragMoved -> {
                        if (initialOrder.isNotEmpty() && liveOrderIds.toList() != initialOrder) {
                            liveOrderIds.clear()
                            liveOrderIds.addAll(initialOrder)
                        }
                        launchSmoothDrop(
                            droppedId = hitId,
                            targetOrder = initialOrder.ifEmpty { finalOrder },
                            releaseX = releaseX,
                            releaseY = releaseY,
                            releaseRot = releaseRot,
                            withCornerGlow = true
                        )
                    }
                    else -> {
                        // Released without dragging: keep note selected, ease elevation down, and ripple outline glow
                        launchSmoothDrop(
                            droppedId = hitId,
                            targetOrder = liveOrderIds.toList(),
                            releaseX = releaseX,
                            releaseY = releaseY,
                            releaseRot = releaseRot,
                            withCornerGlow = !wasCancelled
                        )
                        if (!wasCancelled) {
                            currentOnReleaseWithoutDrag?.invoke(hitId)
                        }
                    }
                }

                dragMoved = false
                dragDeltaX = 0f
                dragDeltaY = 0f
                currentDragRotDeg = 0f
            }
        }
    } else {
        Modifier
    }

    val cornerRadiusPx = with(density) { 16.dp.toPx() }
    val coreOutlineStrokePx = with(density) { 2.dp.toPx() }
    val midOutlineStrokePx = with(density) { 4.5.dp.toPx() }
    val outerOutlineStrokePx = with(density) { 8.dp.toPx() }
    val minGlowRadiusPx = with(density) { 340.dp.toPx() }
    val minOutlineSpreadPx = with(density) { 210.dp.toPx() }

    val cornerGlowDrawModifier = Modifier.drawWithContent {
        drawContent()
        val originId = glowOriginId ?: return@drawWithContent
        val glowP = dropGlowAnim.value.coerceIn(0f, 1f)
        if (glowP <= 0.001f || glowP >= 0.999f) return@drawWithContent

        val originRect = cardBoundsMap[originId] ?: return@drawWithContent
        val originCx = originRect.center.x
        val originCy = originRect.center.y
        val originCenter = Offset(originCx, originCy)
        val maxRadius = max(size.width * 1.15f, minGlowRadiusPx)
        val cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)

        for ((id, rect) in cardBoundsMap) {
            if (rect.width <= 0f || rect.height <= 0f) continue
            val isDropped = id == originId
            val sample = computeCornerGlowSample(
                progress = glowP,
                isDroppedCard = isDropped,
                dxFromDropped = rect.center.x - originCx,
                dyFromDropped = rect.center.y - originCy,
                maxRadiusPx = maxRadius
            )
            if (sample.intensity <= 0.01f) continue

            val a = sample.intensity
            val topLeft = Offset(rect.left, rect.top)
            val cardSize = Size(rect.width, rect.height)

            if (isDropped) {
                // Soft outer luminous outline bloom + crisp glowing perimeter stroke (no interior circles)
                drawRoundRect(
                    color = AuroraGlowMid.copy(alpha = (a * 0.28f).coerceIn(0f, 1f)),
                    topLeft = topLeft,
                    size = cardSize,
                    cornerRadius = cornerRadius,
                    style = Stroke(width = outerOutlineStrokePx)
                )
                drawRoundRect(
                    color = AuroraGlowMid.copy(alpha = (a * 0.54f).coerceIn(0f, 1f)),
                    topLeft = topLeft,
                    size = cardSize,
                    cornerRadius = cornerRadius,
                    style = Stroke(width = midOutlineStrokePx)
                )
                drawRoundRect(
                    color = AuroraGlowCore.copy(alpha = (a * 0.95f).coerceIn(0f, 1f)),
                    topLeft = topLeft,
                    size = cardSize,
                    cornerRadius = cornerRadius,
                    style = Stroke(width = coreOutlineStrokePx)
                )
            } else {
                // Directional outline illumination: radial light field centered at the released note
                // lights up only the facing outline edges & corners of nearby notes
                val nearX = originCx.coerceIn(rect.left, rect.right)
                val nearY = originCy.coerceIn(rect.top, rect.bottom)
                val distToEdge = hypot(originCx - nearX, originCy - nearY)
                val spread = max(minOutlineSpreadPx, min(rect.width, rect.height) * 1.65f)
                val totalR = max(1f, distToEdge + spread)
                val s0 = ((distToEdge - 10f) / totalR).coerceIn(0f, 0.95f)
                val s1 = ((distToEdge + spread * 0.42f) / totalR).coerceIn(s0 + 0.01f, 0.97f)
                val s2 = ((distToEdge + spread * 0.78f) / totalR).coerceIn(s1 + 0.01f, 0.99f)

                val haloBrush = Brush.radialGradient(
                    s0 to AuroraGlowMid.copy(alpha = (a * 0.46f).coerceIn(0f, 1f)),
                    s1 to AuroraGlowMid.copy(alpha = (a * 0.26f).coerceIn(0f, 1f)),
                    1.0f to Color.Transparent,
                    center = originCenter,
                    radius = totalR
                )
                drawRoundRect(
                    brush = haloBrush,
                    topLeft = topLeft,
                    size = cardSize,
                    cornerRadius = cornerRadius,
                    style = Stroke(width = midOutlineStrokePx)
                )

                val coreBrush = Brush.radialGradient(
                    s0 to AuroraGlowCore.copy(alpha = (a * 0.96f).coerceIn(0f, 1f)),
                    s1 to AuroraGlowMid.copy(alpha = (a * 0.78f).coerceIn(0f, 1f)),
                    s2 to AuroraGlowMid.copy(alpha = (a * 0.26f).coerceIn(0f, 1f)),
                    1.0f to Color.Transparent,
                    center = originCenter,
                    radius = totalR
                )
                drawRoundRect(
                    brush = coreBrush,
                    topLeft = topLeft,
                    size = cardSize,
                    cornerRadius = cornerRadius,
                    style = Stroke(width = coreOutlineStrokePx)
                )
            }
        }
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
                .then(cornerGlowDrawModifier)
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
                val activeGlowId = glowOriginId
                val morphP = if (morphActive) {
                    if (morphNeedsSnap) 0f else morphAnim.value.coerceIn(0f, 1f)
                } else {
                    1f
                }
                val isMorphRunning = morphActive && morphP < 0.999f
                val dropP = if (activeDropId != null) dropSettleAnim.value.coerceIn(0f, 1f) else 1f
                val glowP = if (activeGlowId != null) dropGlowAnim.value.coerceIn(0f, 1f) else 1f
                val isGlowRunning = activeGlowId != null && glowP > 0.001f && glowP < 0.999f
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

                // Target center of the dropped card for outward nearby-note wave motion
                var glowOriginCx = 0f
                var glowOriginCy = 0f
                if (isGlowRunning && activeGlowId != null) {
                    val gSlot = effectiveOrderIds.indexOf(activeGlowId)
                    if (gSlot >= 0) {
                        val gRect = slotBoundsMap[gSlot]
                        if (gRect != null) {
                            glowOriginCx = gRect.center.x
                            glowOriginCy = gRect.center.y
                        }
                    }
                }
                val maxWaveRadius = max(totalWidth * 1.15f, minGlowRadiusPx)

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
                        activeDropId != null -> {
                            val startPos = dropStartPosMap[id]
                            if (startPos != null) {
                                drawXf = startPos.x + (tx - startPos.x) * dropP
                                drawYf = startPos.y + (ty - startPos.y) * dropP
                            } else {
                                drawXf = tx
                                drawYf = ty
                            }
                        }
                        isMorphRunning -> {
                            val startRect = morphStartRectMap[id]
                            if (startRect != null) {
                                drawXf = startRect.left + (tx - startRect.left) * morphP
                                drawYf = startRect.top + (ty - startRect.top) * morphP
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

                    // Sympathetic micro-nudge & scale boost on nearby cards while the drop glow wave passes
                    var wavePushX = 0f
                    var wavePushY = 0f
                    var waveScaleBoost = 0f
                    if (isGlowRunning && id != activeGlowId) {
                        val dxFromDropped = (tx + targetItemWidth * 0.5f) - glowOriginCx
                        val dyFromDropped = (ty + itemH * 0.5f) - glowOriginCy
                        val sample = computeCornerGlowSample(
                            progress = glowP,
                            isDroppedCard = false,
                            dxFromDropped = dxFromDropped,
                            dyFromDropped = dyFromDropped,
                            maxRadiusPx = maxWaveRadius
                        )
                        if (sample.waveOffsetPx > 0.01f) {
                            val d = max(1f, hypot(dxFromDropped, dyFromDropped))
                            wavePushX = (dxFromDropped / d) * sample.waveOffsetPx
                            wavePushY = (dyFromDropped / d) * sample.waveOffsetPx
                            waveScaleBoost = sample.scaleBoost
                        }
                    }

                    val finalDrawX = (drawXf + wavePushX).roundToInt()
                    val finalDrawY = (drawYf + wavePushY).roundToInt()

                    cardBoundsMap[id] = Rect(
                        left = drawXf + wavePushX,
                        top = drawYf + wavePushY,
                        right = drawXf + wavePushX + targetItemWidth.toFloat(),
                        bottom = drawYf + wavePushY + itemH.toFloat()
                    )

                    when {
                        id == activeDragId -> {
                            placeable.placeRelativeWithLayer(
                                x = finalDrawX,
                                y = finalDrawY,
                                zIndex = 30f
                            ) {
                                val elev = dragElevationAnim.value.coerceIn(0f, 1f)
                                val scale = 1f + 0.045f * elev
                                val rot = sin(phaseRad) * 1.15f * elev
                                currentDragRotDeg = rot
                                scaleX = scale
                                scaleY = scale
                                rotationZ = rot
                                shadowElevation = 22f * elev
                            }
                        }
                        id == activeDropId -> {
                            placeable.placeRelativeWithLayer(
                                x = finalDrawX,
                                y = finalDrawY,
                                zIndex = 18f
                            ) {
                                val settle = 1f - dropP
                                val cushion = sin(dropP * PI).toFloat() * 0.008f
                                val scale = 1f + 0.045f * settle - cushion
                                scaleX = scale
                                scaleY = scale
                                rotationZ = dropStartRotation * settle
                                shadowElevation = 18f * settle
                            }
                        }
                        isMorphRunning -> {
                            val startRect = morphStartRectMap[id]
                            val scrollTop = scrollState.value.toFloat()
                            val visMinY = scrollTop - viewportSpanPx * 0.75f
                            val visMaxY = scrollTop + viewportSpanPx * 1.75f
                            val intersectsStart = startRect != null &&
                                startRect.bottom >= visMinY && startRect.top <= visMaxY
                            val intersectsTarget = (ty + itemH) >= visMinY && ty <= visMaxY

                            if (intersectsStart || intersectsTarget) {
                                val startScaleX = if (startRect != null && targetItemWidth > 0) {
                                    (startRect.width / targetItemWidth.toFloat()).coerceIn(0.35f, 2.4f)
                                } else {
                                    1f
                                }
                                val startScaleY = if (startRect != null && itemH > 0) {
                                    (startRect.height / itemH.toFloat()).coerceIn(0.72f, 1.38f)
                                } else {
                                    1f
                                }
                                val curScaleX = startScaleX + (1f - startScaleX) * morphP
                                val curScaleY = startScaleY + (1f - startScaleY) * morphP
                                cardBoundsMap[id] = Rect(
                                    left = drawXf,
                                    top = drawYf,
                                    right = drawXf + targetItemWidth * curScaleX,
                                    bottom = drawYf + itemH * curScaleY
                                )
                                placeable.placeRelativeWithLayer(
                                    x = finalDrawX,
                                    y = finalDrawY,
                                    zIndex = (effectiveOrderIds.size - slotIdx).toFloat() * 0.01f
                                ) {
                                    transformOrigin = TransformOrigin(0f, 0f)
                                    scaleX = curScaleX
                                    scaleY = curScaleY
                                }
                            } else {
                                // Far offscreen card: place directly at target slot with zero GPU layer overhead
                                cardBoundsMap[id] = Rect(
                                    left = tx,
                                    top = ty,
                                    right = tx + targetItemWidth.toFloat(),
                                    bottom = ty + itemH.toFloat()
                                )
                                placeable.placeRelative(tx.roundToInt(), ty.roundToInt())
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
                                x = finalDrawX,
                                y = finalDrawY,
                                zIndex = if (glideFactor > 0.05f) 5f else 0f
                            ) {
                                scaleX = neighborScale
                                scaleY = neighborScale
                                rotationZ = neighborWobbleDeg
                            }
                        }
                        waveScaleBoost > 0.0005f -> {
                            placeable.placeRelativeWithLayer(
                                x = finalDrawX,
                                y = finalDrawY,
                                zIndex = 2f
                            ) {
                                val s = 1f + waveScaleBoost
                                scaleX = s
                                scaleY = s
                            }
                        }
                        else -> {
                            placeable.placeRelative(finalDrawX, finalDrawY)
                        }
                    }
                }
            }
        }
    }
}
