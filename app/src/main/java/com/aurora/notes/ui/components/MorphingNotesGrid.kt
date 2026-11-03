package com.aurora.notes.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Smoothly morphs notes between a 2-column Masonry Staggered Grid (`isGridView = true`)
 * and a Full-Width Single Column layout (`isGridView = false`) without 1-frame width snaps,
 * font-distorting scaleX transforms, or right-edge overflow clipping.
 *
 * At every animation frame `p` in [0f, 1f]:
 * - Each card's real layout width is interpolated: `w(p) = gridWidth + p * (fullWidth - gridWidth)`
 * - Each card's `(x, y)` coordinates are interpolated between its 2-column masonry position
 *   and its 1-column vertical stack position.
 * - Right-column cards satisfy `x(p) + w(p) == fullWidth` for all `p`, keeping their right edge
 *   perfectly aligned with the container margin while stretching smoothly across.
 */
@Composable
fun MorphingNotesGrid(
    isGridView: Boolean,
    horizontalSpacing: Dp = 10.dp,
    verticalSpacing: Dp = 10.dp,
    contentPadding: PaddingValues = PaddingValues(bottom = 96.dp),
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
    val smoothedHeights = remember { HashMap<Int, Float>() }

    Layout(
        content = content,
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(contentPadding)
    ) { measurables, constraints ->
        if (measurables.isEmpty()) {
            smoothedHeights.clear()
            return@Layout layout(constraints.maxWidth, 0) {}
        }

        val totalWidth = constraints.maxWidth.coerceAtLeast(1)
        val hGap = horizontalSpacing.roundToPx()
        val vGap = verticalSpacing.roundToPx()

        val gridItemWidth = ((totalWidth - hGap) / 2).coerceAtLeast(1)
        val colItemWidth = totalWidth

        val p = expandProgress.coerceIn(0f, 1f)
        val isAnimating = p > 0.001f && p < 0.999f

        val currentItemWidth = (gridItemWidth + (colItemWidth - gridItemWidth) * p)
            .roundToInt()
            .coerceIn(gridItemWidth, colItemWidth)

        val itemConstraints = Constraints.fixedWidth(currentItemWidth)
        val placeables = measurables.map { measurable ->
            measurable.measure(itemConstraints)
        }

        val effectiveHeights = IntArray(placeables.size)
        for (i in placeables.indices) {
            val targetH = placeables[i].height.toFloat()
            val prevH = smoothedHeights[i]
            val nextH = if (!isAnimating || prevH == null || abs(targetH - prevH) < 1f) {
                targetH
            } else {
                prevH + (targetH - prevH) * 0.35f
            }
            smoothedHeights[i] = nextH
            effectiveHeights[i] = nextH.roundToInt()
        }

        // 1. Compute 2-column Masonry Grid positions
        val colHeights = IntArray(2) { 0 }
        val gridX = IntArray(placeables.size)
        val gridY = IntArray(placeables.size)
        for (i in placeables.indices) {
            val lane = if (colHeights[0] <= colHeights[1]) 0 else 1
            gridX[i] = if (lane == 0) 0 else (totalWidth - gridItemWidth)
            gridY[i] = colHeights[lane]
            colHeights[lane] += effectiveHeights[i] + vGap
        }
        val gridTotalHeight = max(0, max(colHeights[0], colHeights[1]) - vGap)

        // 2. Compute Single-Column vertical stack positions
        var runningY = 0
        val colX = IntArray(placeables.size)
        val colY = IntArray(placeables.size)
        for (i in placeables.indices) {
            colX[i] = 0
            colY[i] = runningY
            runningY += effectiveHeights[i] + vGap
        }
        val colTotalHeight = max(0, runningY - vGap)

        val interpolatedTotalHeight = (gridTotalHeight + (colTotalHeight - gridTotalHeight) * p)
            .roundToInt()
            .coerceAtLeast(0)

        layout(totalWidth, interpolatedTotalHeight) {
            for (i in placeables.indices) {
                val x = (gridX[i] + (colX[i] - gridX[i]) * p).roundToInt()
                val y = (gridY[i] + (colY[i] - gridY[i]) * p).roundToInt()
                placeables[i].placeRelative(x, y)
            }
        }
    }
}
