package com.aurora.notes.ui.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Computes the organic metaball / fluid-droplet geometry for Aurora Notes' bottom navigation bar.
 *
 * Visual behavior:
 * 1. When a user selects a menu tab, a small purple fluid droplet ([dropPos]) detaches ahead of
 *    the main pill body ([bodyPos]) and glides to the selected menu tab (overshooting slightly
 *    into the neighboring button before settling back into place) while remaining connected via
 *    a smooth cubic-Bezier liquid bridge.
 * 2. The rest of the main pill ([bodyPos]) follows along the liquid bridge and attaches itself
 *    into the droplet, strictly contained within `[0f, containerWidthPx]` so it never clips
 *    outside the navbar.
 */
object FluidDropPathBuilder {

    data class FluidDropPaths(
        val mainMetaballPath: Path,
        val boundingLeftPx: Float,
        val boundingRightPx: Float
    )

    fun buildFluidDropPaths(
        dropPos: Float,
        bodyPos: Float,
        tabCount: Int,
        containerWidthPx: Float,
        containerHeightPx: Float
    ): FluidDropPaths {
        val safeTabCount = max(1, tabCount)
        val tabWidthPx = containerWidthPx / safeTabCount.toFloat()
        val h = containerHeightPx
        val centerY = h / 2f

        // Clamp within the navbar track [0 .. tabCount - 1] so the pill can overshoot into
        // neighboring buttons inside the bar but never clip past the outer ends of the navbar.
        val maxTabIndex = (safeTabCount - 1).toFloat()
        val clampedDrop = dropPos.coerceIn(0f, maxTabIndex)
        val clampedBody = bodyPos.coerceIn(0f, maxTabIndex)
        val separation = abs(clampedDrop - clampedBody)

        // Normalized separation factor [0f .. 1f]:
        // Rises quickly as the little purple drop pulls away from the main pill,
        // and decays smoothly to 0f as the main pill attaches into the drop upon arrival.
        val dropShrink = (separation / (separation + 0.28f)).coerceIn(0f, 1f)

        // At rest (or once the main pill has attached into the little drop and settles back into place)
        if (separation < 0.005f) {
            val left = (clampedDrop * tabWidthPx).coerceIn(0f, (containerWidthPx - tabWidthPx).coerceAtLeast(0f))
            val right = (left + tabWidthPx).coerceIn(left + h, containerWidthPx)
            val capsule = buildSymmetricCapsule(left, 0f, right, h)
            return FluidDropPaths(
                mainMetaballPath = capsule,
                boundingLeftPx = left,
                boundingRightPx = right
            )
        }

        val isMovingRight = clampedDrop >= clampedBody

        // Main pill body dimensions (gently compacts while feeding fluid into the leading drop)
        val bodyCenterX = (clampedBody + 0.5f) * tabWidthPx
        val bodyH = (h * (1f - 0.03f * dropShrink)).coerceAtLeast(h * 0.88f)
        val bodyW = (tabWidthPx * (1f - 0.08f * dropShrink)).coerceAtLeast(bodyH)

        // Leading fluid drop dimensions:
        // Forms a rounded purple fluid drop ahead of the main pill, then expands back
        // to full capsule dimensions as the main pill attaches into it.
        val dropCenterX = (clampedDrop + 0.5f) * tabWidthPx
        val dropH = (h * (1f - 0.14f * dropShrink)).coerceAtLeast(h * 0.80f)
        val dropW = (tabWidthPx * (1f - 0.24f * dropShrink)).coerceAtLeast(dropH)

        // Assign left and right lobes based on travel direction
        val leftCX: Float
        val leftW: Float
        val leftH: Float
        val rightCX: Float
        val rightW: Float
        val rightH: Float

        if (isMovingRight) {
            leftCX = bodyCenterX
            leftW = bodyW
            leftH = bodyH
            rightCX = dropCenterX
            rightW = dropW
            rightH = dropH
        } else {
            leftCX = dropCenterX
            leftW = dropW
            leftH = dropH
            rightCX = bodyCenterX
            rightW = bodyW
            rightH = bodyH
        }

        // Strictly clamp leftOuter and rightOuter inside [0f, containerWidthPx] so the pill never clips out
        val leftOuter = (leftCX - leftW / 2f).coerceIn(0f, (containerWidthPx - leftH).coerceAtLeast(0f))
        val rightOuter = (rightCX + rightW / 2f).coerceIn(
            (leftOuter + rightH).coerceAtMost(containerWidthPx),
            containerWidthPx
        )

        val leftTop = centerY - leftH / 2f
        val leftBottom = centerY + leftH / 2f
        val rightTop = centerY - rightH / 2f
        val rightBottom = centerY + rightH / 2f

        val leftCapCenterX = leftOuter + leftH / 2f
        val rightCapCenterX = rightOuter - rightH / 2f

        // When lobes closely overlap at the final attachment phase, blend smoothly as a single capsule
        if (rightCapCenterX <= leftCapCenterX + 2f) {
            val blendedH = max(leftH, rightH)
            val blendedTop = centerY - blendedH / 2f
            val blendedBottom = centerY + blendedH / 2f
            val merged = buildSymmetricCapsule(leftOuter, blendedTop, rightOuter, blendedBottom)
            return FluidDropPaths(
                mainMetaballPath = merged,
                boundingLeftPx = leftOuter,
                boundingRightPx = rightOuter
            )
        }

        // Position the gentle waist contour between the main pill and the leading drop
        val neckBias = if (isMovingRight) 0.56f else 0.44f
        val neckX = leftCapCenterX + (rightCapCenterX - leftCapCenterX) * neckBias

        // Keep the middle connecting bridge substantial (~68%-74% of full height at max stretch)
        // so it looks like a cohesive, thick liquid bridge rather than a thin thread.
        val minLobeHalfH = min(leftH, rightH) * 0.5f
        val neckHalfH = (minLobeHalfH * (1f - 0.26f * dropShrink)).coerceIn(h * 0.34f, minLobeHalfH)

        val neckTopY = centerY - neckHalfH
        val neckBottomY = centerY + neckHalfH

        val leftSpan = (neckX - leftCapCenterX).coerceAtLeast(1f)
        val rightSpan = (rightCapCenterX - neckX).coerceAtLeast(1f)

        val metaballPath = Path().apply {
            // 1. Left lobe semicircular end cap (bottom-left to top-left)
            // Use forceMoveTo = true on the first contour command so no stray line connects from (0,0).
            arcTo(
                rect = Rect(leftOuter, leftTop, leftOuter + leftH, leftBottom),
                startAngleDegrees = 90f,
                sweepAngleDegrees = 180f,
                forceMoveTo = true
            )

            // 2. Top contour from left lobe crown down into the liquid neck
            cubicTo(
                x1 = leftCapCenterX + leftSpan * 0.54f,
                y1 = leftTop,
                x2 = neckX - leftSpan * 0.36f,
                y2 = neckTopY,
                x3 = neckX,
                y3 = neckTopY
            )

            // 3. Top contour from the liquid neck up onto the right lobe crown
            cubicTo(
                x1 = neckX + rightSpan * 0.36f,
                y1 = neckTopY,
                x2 = rightCapCenterX - rightSpan * 0.54f,
                y2 = rightTop,
                x3 = rightCapCenterX,
                y3 = rightTop
            )

            // 4. Right lobe semicircular end cap (top-right to bottom-right)
            arcTo(
                rect = Rect(rightOuter - rightH, rightTop, rightOuter, rightBottom),
                startAngleDegrees = 270f,
                sweepAngleDegrees = 180f,
                forceMoveTo = false
            )

            // 5. Bottom contour from right lobe base up into the liquid neck
            cubicTo(
                x1 = rightCapCenterX - rightSpan * 0.54f,
                y1 = rightBottom,
                x2 = neckX + rightSpan * 0.36f,
                y2 = neckBottomY,
                x3 = neckX,
                y3 = neckBottomY
            )

            // 6. Bottom contour from the liquid neck back to the left lobe base
            cubicTo(
                x1 = neckX - leftSpan * 0.36f,
                y1 = neckBottomY,
                x2 = leftCapCenterX + leftSpan * 0.54f,
                y2 = leftBottom,
                x3 = leftCapCenterX,
                y3 = leftBottom
            )

            close()
        }

        return FluidDropPaths(
            mainMetaballPath = metaballPath,
            boundingLeftPx = leftOuter,
            boundingRightPx = rightOuter
        )
    }

    private fun buildSymmetricCapsule(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float
    ): Path {
        val h = (bottom - top).coerceAtLeast(1f)
        val safeRight = max(right, left + h)
        val radius = h / 2f
        return Path().apply {
            addRoundRect(
                RoundRect(
                    left = left,
                    top = top,
                    right = safeRight,
                    bottom = bottom,
                    cornerRadius = CornerRadius(radius, radius)
                )
            )
        }
    }
}
