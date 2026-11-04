package com.aurora.notes.ui.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Computes single stretching capsule geometry for Aurora Notes' floating pill navigation bar.
 *
 * Visual behavior:
 * - Exactly ONE solid continuous pill capsule (no waist thinning, no middle pinching, no detached droplets).
 * - During travel, the pill stretches slightly along the motion axis (clamped to max ~28% expansion)
 *   and contracts back to normal resting width at the destination button.
 * - Strictly clamped within [0f, containerWidthPx] so it never clips outside the navbar.
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

        val maxTabIndex = (safeTabCount - 1).toFloat()
        val leadPos = dropPos.coerceIn(0f, maxTabIndex)
        val trailPos = bodyPos.coerceIn(0f, maxTabIndex)

        val minSlot = min(leadPos, trailPos)
        val maxSlot = max(leadPos, trailPos)

        // Single continuous pill stretches slightly along the travel axis
        // Max span is 1.28 slots (28% stretch)
        val naturalSpan = (maxSlot - minSlot) + 1f
        val clampedSpan = naturalSpan.coerceAtMost(1.28f)

        val leftSlot: Float
        val rightSlot: Float
        if (leadPos >= trailPos) {
            // Moving right: lead edge leads, trail edge follows
            val right = (minSlot + clampedSpan).coerceAtMost(maxTabIndex + 1f)
            val left = (right - clampedSpan).coerceAtLeast(0f)
            leftSlot = left
            rightSlot = right
        } else {
            // Moving left: lead edge leads to the left
            val left = (maxSlot + 1f - clampedSpan).coerceAtLeast(0f)
            val right = (left + clampedSpan).coerceAtMost(maxTabIndex + 1f)
            leftSlot = left
            rightSlot = right
        }

        val leftPx = (leftSlot * tabWidthPx).coerceIn(0f, (containerWidthPx - h).coerceAtLeast(0f))
        val rightPx = (rightSlot * tabWidthPx).coerceIn(leftPx + h, containerWidthPx)

        val capsulePath = buildSymmetricCapsule(leftPx, 0f, rightPx, h)

        return FluidDropPaths(
            mainMetaballPath = capsulePath,
            boundingLeftPx = leftPx,
            boundingRightPx = rightPx
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
