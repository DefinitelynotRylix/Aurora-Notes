package com.aurora.notes.ui.components

import android.content.Context
import android.view.View
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aurora.notes.ui.theme.AuroraAmethyst
import com.aurora.notes.ui.theme.AuroraRoyalViolet
import com.aurora.notes.util.HapticHelper
import kotlin.math.abs
import kotlin.math.roundToInt

data class NavTabItem(
    val index: Int,
    val label: String,
    val icon: ImageVector,
    val badge: Int = 0
)

fun triggerNavbarHaptic(view: View, context: Context, enabled: Boolean = true) {
    HapticHelper.triggerHaptic(view = view, context = context, enabled = enabled, durationMs = 30L)
}

@Composable
fun FloatingPillNavigationBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    archivedCount: Int = 0,
    trashCount: Int = 0,
    isHapticsEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val context = LocalContext.current
    val currentOnTabSelected by rememberUpdatedState(onTabSelected)
    val currentHapticsEnabled by rememberUpdatedState(isHapticsEnabled)

    val items = listOf(
        NavTabItem(0, "Notes", Icons.Default.Description),
        NavTabItem(1, "Archive", Icons.Default.Archive, badge = archivedCount),
        NavTabItem(2, "Trash", Icons.Default.Delete, badge = trashCount),
        NavTabItem(3, "Settings", Icons.Default.Settings)
    )

    // Hold-and-slide state for dragging the purple pill across the menus
    var isHoldingPill by remember { mutableStateOf(false) }
    var dragTabPos by remember { mutableFloatStateOf(selectedTab.toFloat()) }

    LaunchedEffect(selectedTab) {
        if (!isHoldingPill) {
            dragTabPos = selectedTab.toFloat()
        }
    }

    val targetPillPos = if (isHoldingPill) dragTabPos else selectedTab.toFloat()

    // Dual fluid-drop springs with horizontal overshoot:
    // 1. dropTabPos: Glides smoothly to the selected button, goes past it a little bit toward the next button,
    //    and settles back into place without blocking or delaying menu transitions.
    // 2. bodyTabPos: Follows along the connected liquid bridge and settles into the leading drop.
    val dropTabPos by animateFloatAsState(
        targetValue = targetPillPos,
        animationSpec = spring(
            dampingRatio = if (isHoldingPill) Spring.DampingRatioNoBouncy else 0.62f,
            stiffness = if (isHoldingPill) 540f else 220f
        ),
        label = "fluid_drop_lead_pos"
    )

    val bodyTabPos by animateFloatAsState(
        targetValue = targetPillPos,
        animationSpec = spring(
            dampingRatio = if (isHoldingPill) Spring.DampingRatioNoBouncy else 0.70f,
            stiffness = if (isHoldingPill) 210f else 110f
        ),
        label = "fluid_pill_body_pos"
    )

    // Highlight the hovered or selected menu immediately
    val visualSelectedTab = if (isHoldingPill) {
        dragTabPos.roundToInt().coerceIn(0, items.size - 1)
    } else {
        selectedTab
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(100.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            tonalElevation = 12.dp,
            shadowElevation = 20.dp,
            border = BorderStroke(
                width = 1.5.dp,
                brush = Brush.horizontalGradient(
                    listOf(
                        AuroraRoyalViolet.copy(alpha = 0.55f),
                        AuroraAmethyst.copy(alpha = 0.75f),
                        AuroraRoyalViolet.copy(alpha = 0.55f)
                    )
                )
            ),
            modifier = Modifier
                .widthIn(max = 380.dp)
                .fillMaxWidth()
                .height(62.dp)
                .shadow(
                    elevation = 24.dp,
                    shape = RoundedCornerShape(100.dp),
                    ambientColor = AuroraRoyalViolet,
                    spotColor = AuroraAmethyst
                )
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(5.dp)
                    .clip(RoundedCornerShape(100.dp))
                    .pointerInput(items.size) {
                        val touchSlopPx = viewConfiguration.touchSlop
                        val holdTimeoutMs = 165L

                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val trackWidthPx = size.width.toFloat().coerceAtLeast(1f)
                            val tabWidthPx = trackWidthPx / items.size.toFloat()
                            val startX = down.position.x
                            var currentX = startX
                            val initialTappedTab = (startX / tabWidthPx).toInt().coerceIn(0, items.size - 1)

                            // Haptic feedback on push (initial touch down)
                            triggerNavbarHaptic(view, context, currentHapticsEnabled)

                            var holdModeActive = false
                            var pointerReleased = false

                            // Phase 1: Wait up to holdTimeoutMs to see if the user releases (tap),
                            // slides horizontally, or holds still on the pill/button.
                            val preHoldResult = withTimeoutOrNull(holdTimeoutMs) {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Main)
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                        ?: event.changes.firstOrNull()
                                    if (change == null || !change.pressed) {
                                        pointerReleased = true
                                        break
                                    }
                                    currentX = change.position.x
                                    if (abs(currentX - startX) > touchSlopPx * 0.45f) {
                                        holdModeActive = true
                                        change.consume()
                                        break
                                    }
                                }
                            }

                            // If the timer expired while the finger is still pressed down, enter hold mode
                            if (preHoldResult == null && !pointerReleased) {
                                holdModeActive = true
                            }

                            if (holdModeActive && !pointerReleased) {
                                isHoldingPill = true
                                dragTabPos = ((currentX / tabWidthPx) - 0.5f)
                                    .coerceIn(0f, (items.size - 1).toFloat())

                                // Phase 2: Slide the purple pill through the menus with NO haptic feedback
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Main)
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                        ?: event.changes.firstOrNull()
                                    if (change == null || !change.pressed) {
                                        break
                                    }
                                    currentX = change.position.x
                                    change.consume()
                                    dragTabPos = ((currentX / tabWidthPx) - 0.5f)
                                        .coerceIn(0f, (items.size - 1).toFloat())
                                }

                                // User let go of the hold phase: land on the selected page immediately and trigger haptic
                                val landedTab = dragTabPos.roundToInt().coerceIn(0, items.size - 1)
                                isHoldingPill = false
                                currentOnTabSelected(landedTab)
                                triggerNavbarHaptic(view, context, currentHapticsEnabled)
                            } else {
                                // Normal quick tap: switch to the tapped page immediately
                                isHoldingPill = false
                                currentOnTabSelected(initialTappedTab)
                            }
                        }
                    }
            ) {
                val density = LocalDensity.current
                val containerWidthPx = with(density) { maxWidth.toPx() }
                val containerHeightPx = with(density) { maxHeight.toPx() }

                Spacer(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawBehind {
                            val fluidPaths = FluidDropPathBuilder.buildFluidDropPaths(
                                dropPos = dropTabPos,
                                bodyPos = bodyTabPos,
                                tabCount = items.size,
                                containerWidthPx = size.width.coerceAtLeast(containerWidthPx),
                                containerHeightPx = size.height.coerceAtLeast(containerHeightPx)
                            )

                            val left = fluidPaths.boundingLeftPx
                            val right = (fluidPaths.boundingRightPx).coerceAtLeast(left + 1f)
                            val h = size.height

                            // Outer soft neon purple glow around the main pill and connected droplet
                            drawPath(
                                path = fluidPaths.mainMetaballPath,
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        AuroraRoyalViolet.copy(alpha = 0.36f),
                                        AuroraAmethyst.copy(alpha = 0.48f),
                                        AuroraRoyalViolet.copy(alpha = 0.36f)
                                    ),
                                    startX = left,
                                    endX = right
                                )
                            )

                            // Main Neon Purple Fluid Drop + Connected Pill Fill (clean gradient without inner streak line)
                            drawPath(
                                path = fluidPaths.mainMetaballPath,
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        AuroraRoyalViolet,
                                        Color(0xFF7B35E8),
                                        AuroraAmethyst
                                    ),
                                    startX = left,
                                    endX = right
                                )
                            )

                            // Subtle outer rim highlight along the capsule contour
                            drawPath(
                                path = fluidPaths.mainMetaballPath,
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.38f),
                                        Color.White.copy(alpha = 0.08f)
                                    ),
                                    startY = 0f,
                                    endY = h
                                ),
                                style = Stroke(width = with(density) { 1.dp.toPx() })
                            )
                        }
                )

                // Symmetric 4-Column Tab Items
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items.forEach { item ->
                        val isSelected = visualSelectedTab == item.index

                        val contentColor by animateColorAsState(
                            targetValue = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                            animationSpec = tween(durationMillis = 220),
                            label = "tab_content_color"
                        )

                        val iconScale by animateFloatAsState(
                            targetValue = if (isSelected) 1.08f else 1.0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessLow
                            ),
                            label = "tab_icon_scale"
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.label,
                                    tint = contentColor,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .scale(iconScale)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = item.label,
                                    color = contentColor,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1
                                )
                                if (item.badge > 0) {
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Surface(
                                        shape = CircleShape,
                                        color = if (isSelected) Color.White.copy(alpha = 0.28f) else AuroraRoyalViolet.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = item.badge.toString(),
                                            color = if (isSelected) Color.White else AuroraRoyalViolet,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
